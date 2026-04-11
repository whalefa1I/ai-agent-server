/**
 * WebSocket Client for Agent Interaction
 *
 * 连接到后端的 /ws/agent 端点，处理用户消息和流式响应
 * 支持 REASONING_DELTA 流式推理内容
 */

import type { BackendArtifact, Message, HappyEvent } from '@/types/happy-protocol';
import { toolStateManager } from './use-tool-state';

export interface AgentWebSocketClientOptions {
    /** 后端 WebSocket URL */
    url?: string;
    /** 自动重连间隔 (ms) */
    reconnectInterval?: number;
    /** 最大重连次数 */
    maxReconnects?: number;
}

export interface AgentWebSocketClientState {
    connected: boolean;
    connecting: boolean;
    error: string | null;
    reconnectCount: number;
    currentRequestId: string | null;
    isStreaming: boolean;
}

export interface StreamingCallbacks {
    /** 响应开始 */
    onResponseStart?: (requestId: string, turnId: string) => void;
    /** reasoning 内容增量 */
    onReasoningDelta?: (delta: string) => void;
    /** 文本内容增量 */
    onTextDelta?: (delta: string) => void;
    /** 工具调用开始 */
    onToolCallStart?: (toolCallId: string, toolName: string, input?: Record<string, unknown>) => void;
    /** 工具调用完成 */
    onToolCallEnd?: (toolCallId: string, toolName: string, output?: string, error?: string) => void;
    /** 响应完成 */
    onResponseComplete?: (content: string, inputTokens?: number, outputTokens?: number) => void;
}

export class AgentWebSocketClient {
    private ws: WebSocket | null = null;
    private url: string;
    private reconnectInterval: number;
    private maxReconnects: number;
    private reconnectCount: number = 0;
    private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    private stateListeners: Set<(state: AgentWebSocketClientState) => void> = new Set();
    private messageListeners: Set<(message: Message) => void> = new Set();
    private callbacks: StreamingCallbacks = {};

    private state: AgentWebSocketClientState = {
        connected: false,
        connecting: false,
        error: null,
        reconnectCount: 0,
        currentRequestId: null,
        isStreaming: false
    };

    // 当前正在构建的响应内容
    private currentContentBuilder: string = '';
    private currentReasoningBuilder: string = '';
    private currentRequestId: string | null = null;
    private currentTurnId: string | null = null;

    constructor(options: AgentWebSocketClientOptions = {}) {
        this.url = options.url || this.getDefaultUrl();
        this.reconnectInterval = options.reconnectInterval || 3000;
        this.maxReconnects = options.maxReconnects || 5;
    }

    /**
     * 获取默认 WebSocket URL
     */
    private getDefaultUrl(): string {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${protocol}//${window.location.host}/ws/agent`;
    }

    /**
     * 更新状态并通知监听器
     */
    private setState(updates: Partial<AgentWebSocketClientState>): void {
        this.state = { ...this.state, ...updates };
        this.stateListeners.forEach(listener => listener(this.state));
    }

    /**
     * 订阅状态变化
     */
    subscribeState(listener: (state: AgentWebSocketClientState) => void): () => void {
        this.stateListeners.add(listener);
        return () => this.stateListeners.delete(listener);
    }

    /**
     * 订阅消息
     */
    subscribeMessage(listener: (message: Message) => void): () => void {
        this.messageListeners.add(listener);
        return () => this.messageListeners.delete(listener);
    }

    /**
     * 设置流式回调
     */
    setCallbacks(callbacks: StreamingCallbacks): void {
        this.callbacks = callbacks;
    }

    /**
     * 连接 WebSocket
     */
    connect(token?: string): void {
        if (this.ws || this.state.connecting) {
            console.log('[AgentWS] 已存在连接或正在连接');
            return;
        }

        this.setState({ connecting: true, error: null });

        const connectUrl = token ? `${this.url}?token=${token}` : this.url;
        console.log('[AgentWS] 正在连接:', connectUrl);
        this.ws = new WebSocket(connectUrl);

        this.ws.onopen = () => {
            console.log('[AgentWS] 连接成功');
            this.reconnectCount = 0;
            this.setState({ connected: true, connecting: false, reconnectCount: 0 });
        };

        this.ws.onclose = (event) => {
            console.log('[AgentWS] 连接关闭:', event.code, event.reason);
            this.ws = null;
            this.setState({
                connected: false,
                connecting: false,
                isStreaming: false
            });

            // 尝试重连
            this.scheduleReconnect(token);
        };

        this.ws.onerror = (error) => {
            console.error('[AgentWS] 连接错误:', error);
            this.setState({ error: 'WebSocket connection error' });
        };

        this.ws.onmessage = (event) => {
            this.handleMessage(event.data);
        };
    }

    /**
     * 处理接收到的消息
     */
    private handleMessage(data: string): void {
        try {
            const message = JSON.parse(data);
            console.log('[AgentWS] 收到消息:', message.type);

            switch (message.type) {
                case 'RESPONSE_START':
                    this.handleResponseStart(message);
                    break;

                case 'REASONING_DELTA':
                    this.handleReasoningDelta(message);
                    break;

                case 'TEXT_DELTA':
                    this.handleTextDelta(message);
                    break;

                case 'TOOL_CALL':
                    this.handleToolCall(message);
                    break;

                case 'RESPONSE_COMPLETE':
                    this.handleResponseComplete(message);
                    break;

                case 'PING':
                    this.handlePing(message);
                    break;

                default:
                    console.log('[AgentWS] 未知消息类型:', message.type);
            }
        } catch (error) {
            console.error('[AgentWS] 解析消息失败:', error);
        }
    }

    /**
     * 处理 RESPONSE_START
     */
    private handleResponseStart(message: { requestId: string; turnId: string }): void {
        console.log('[AgentWS] 响应开始:', message.requestId);
        this.currentRequestId = message.requestId;
        this.currentTurnId = message.turnId;
        this.currentContentBuilder = '';
        this.currentReasoningBuilder = '';
        this.setState({
            isStreaming: true,
            currentRequestId: message.requestId
        });

        if (this.callbacks.onResponseStart) {
            this.callbacks.onResponseStart(message.requestId, message.turnId);
        }
    }

    /**
     * 处理 REASONING_DELTA
     */
    private handleReasoningDelta(message: { delta: string }): void {
        const delta = message.delta;
        if (!delta) return;

        console.log('[AgentWS] reasoning 增量:', delta.length, 'chars');

        // 累积 reasoning 内容
        this.currentReasoningBuilder += delta;

        // 创建 reasoning message
        const reasoningMessage: Message = {
            kind: 'reasoning',
            id: `reasoning_${Date.now()}`,
            time: Date.now(),
            role: 'agent',
            turn: this.currentTurnId || undefined,
            text: delta,
            thinking: true
        };

        // 通知消息监听器
        this.messageListeners.forEach(listener => listener(reasoningMessage));

        // 调用回调
        if (this.callbacks.onReasoningDelta) {
            this.callbacks.onReasoningDelta(delta);
        }
    }

    /**
     * 处理 TEXT_DELTA
     */
    private handleTextDelta(message: { delta: string }): void {
        const delta = message.delta;
        if (!delta) return;

        console.log('[AgentWS] 文本增量:', delta.length, 'chars');

        // 累积文本内容
        this.currentContentBuilder += delta;

        // 创建 text message
        const textMessage: Message = {
            kind: 'text',
            id: `text_${Date.now()}`,
            time: Date.now(),
            role: 'agent',
            turn: this.currentTurnId || undefined,
            text: delta
        };

        // 通知消息监听器
        this.messageListeners.forEach(listener => listener(textMessage));

        // 调用回调
        if (this.callbacks.onTextDelta) {
            this.callbacks.onTextDelta(delta);
        }
    }

    /**
     * 处理 TOOL_CALL
     */
    private handleToolCall(message: {
        toolName: string;
        status: string;
        toolCallId?: string;
        input?: Record<string, unknown>;
        output?: string;
        error?: string;
    }): void {
        console.log('[AgentWS] 工具调用:', message.toolName, message.status);

        if (message.status === 'started') {
            if (this.callbacks.onToolCallStart) {
                this.callbacks.onToolCallStart(
                    message.toolCallId || `tool_${Date.now()}`,
                    message.toolName,
                    message.input
                );
            }
        } else if (message.status === 'completed' || message.status === 'failed') {
            if (this.callbacks.onToolCallEnd) {
                this.callbacks.onToolCallEnd(
                    message.toolCallId || `tool_${Date.now()}`,
                    message.toolName,
                    message.output,
                    message.error
                );
            }
        }
    }

    /**
     * 处理 RESPONSE_COMPLETE
     */
    private handleResponseComplete(message: {
        content?: string;
        inputTokens?: number;
        outputTokens?: number;
    }): void {
        console.log('[AgentWS] 响应完成:', message.content?.length, 'chars');

        this.setState({ isStreaming: false, currentRequestId: null });
        this.currentRequestId = null;
        this.currentTurnId = null;

        if (this.callbacks.onResponseComplete) {
            this.callbacks.onResponseComplete(
                message.content || this.currentContentBuilder,
                message.inputTokens,
                message.outputTokens
            );
        }
    }

    /**
     * 处理 PING
     */
    private handlePing(message: { timestamp: number }): void {
        // 回复 PONG
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({
                type: 'PONG',
                timestamp: Date.now()
            }));
        }
    }

    /**
     * 发送用户消息
     */
    sendMessage(content: string, requestId?: string): boolean {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            console.warn('[AgentWS] 无法发送消息，连接未打开');
            return false;
        }

        try {
            const msg = {
                type: 'USER_MESSAGE',
                content,
                requestId: requestId || `req_${Date.now()}_${Math.random().toString(36).slice(2)}`
            };
            this.ws.send(JSON.stringify(msg));
            console.log('[AgentWS] 已发送消息:', msg.requestId);
            return true;
        } catch (error) {
            console.error('[AgentWS] 发送消息失败:', error);
            return false;
        }
    }

    /**
     * 调度重连
     */
    private scheduleReconnect(token?: string): void {
        if (this.reconnectCount >= this.maxReconnects) {
            console.log('[AgentWS] 达到最大重连次数，停止重连');
            this.setState({ error: 'Max reconnects reached' });
            return;
        }

        this.reconnectCount++;
        this.setState({ reconnectCount: this.reconnectCount });

        const delay = this.reconnectInterval * Math.pow(2, this.reconnectCount - 1);
        console.log(`[AgentWS] 将在 ${delay}ms 后重连 (${this.reconnectCount}/${this.maxReconnects})`);

        this.reconnectTimer = setTimeout(() => {
            this.reconnectTimer = null;
            this.connect(token);
        }, delay);
    }

    /**
     * 断开连接
     */
    disconnect(): void {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }

        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }

        this.setState({
            connected: false,
            connecting: false,
            isStreaming: false,
            currentRequestId: null
        });
    }

    /**
     * 获取当前状态
     */
    getState(): AgentWebSocketClientState {
        return this.state;
    }
}

// 创建全局单例
export const agentWebSocketClient = new AgentWebSocketClient();
