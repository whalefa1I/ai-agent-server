/**
 * WebSocket Client for Tool State
 *
 * 连接到后端的 /ws/tool-state 端点，接收实时工具状态推送
 */

import type { BackendArtifact } from '@/types/happy-protocol';
import { toolStateManager } from './use-tool-state';

export interface WebSocketClientOptions {
    /** 后端 WebSocket URL */
    url?: string;
    /** 自动重连间隔 (ms) */
    reconnectInterval?: number;
    /** 最大重连次数 */
    maxReconnects?: number;
    /** 心跳间隔 (ms) */
    heartbeatInterval?: number;
}

export interface WebSocketClientState {
    connected: boolean;
    connecting: boolean;
    error: string | null;
    reconnectCount: number;
}

export class ToolStateWebSocketClient {
    private ws: WebSocket | null = null;
    private url: string;
    private reconnectInterval: number;
    private maxReconnects: number;
    private heartbeatInterval: number;
    private reconnectCount: number = 0;
    private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    private heartbeatTimer: ReturnType<typeof setTimeout> | null = null;
    private stateListeners: Set<(state: WebSocketClientState) => void> = new Set();
    private messageListeners: Set<(artifact: BackendArtifact) => void> = new Set();

    private state: WebSocketClientState = {
        connected: false,
        connecting: false,
        error: null,
        reconnectCount: 0
    };

    constructor(options: WebSocketClientOptions = {}) {
        this.url = options.url || this.getDefaultUrl();
        this.reconnectInterval = options.reconnectInterval || 3000;
        this.maxReconnects = options.maxReconnects || 5;
        this.heartbeatInterval = options.heartbeatInterval || 30000;
    }

    /**
     * 获取默认 WebSocket URL
     */
    private getDefaultUrl(): string {
        // 根据当前页面 URL 构建 WebSocket URL
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${protocol}//${window.location.host}/ws/tool-state`;
    }

    /**
     * 更新状态并通知监听器
     */
    private setState(updates: Partial<WebSocketClientState>): void {
        this.state = { ...this.state, ...updates };
        this.stateListeners.forEach(listener => listener(this.state));
    }

    /**
     * 订阅状态变化
     */
    subscribeState(listener: (state: WebSocketClientState) => void): () => void {
        this.stateListeners.add(listener);
        return () => this.stateListeners.delete(listener);
    }

    /**
     * 订阅消息
     */
    subscribeMessage(listener: (artifact: BackendArtifact) => void): () => void {
        this.messageListeners.add(listener);
        return () => this.messageListeners.delete(listener);
    }

    /**
     * 连接 WebSocket
     */
    connect(token?: string): void {
        if (this.ws || this.state.connecting) {
            console.log('[ToolStateWS] 已存在连接或正在连接');
            return;
        }

        this.setState({ connecting: true, error: null });

        const connectUrl = token ? `${this.url}?token=${token}` : this.url;
        this.ws = new WebSocket(connectUrl);

        this.ws.onopen = () => {
            console.log('[ToolStateWS] 连接成功');
            this.reconnectCount = 0;
            this.setState({ connected: true, connecting: false, reconnectCount: 0 });
            toolStateManager.setWsConnected(true);
            this.startHeartbeat();
        };

        this.ws.onclose = (event) => {
            console.log('[ToolStateWS] 连接关闭:', event.code, event.reason);
            this.ws = null;
            this.setState({ connected: false, connecting: false });
            toolStateManager.setWsConnected(false);
            this.stopHeartbeat();

            // 尝试重连
            this.scheduleReconnect(token);
        };

        this.ws.onerror = (error) => {
            console.error('[ToolStateWS] 连接错误:', error);
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
            const artifact: BackendArtifact = JSON.parse(data);
            console.log('[ToolStateWS] 收到 artifact:', artifact.id);

            // 通知消息监听器
            this.messageListeners.forEach(listener => listener(artifact));

            // 更新工具状态管理器
            // 这里可以根据需要更新 toolStateManager 中的状态
        } catch (error) {
            console.error('[ToolStateWS] 解析消息失败:', error);
        }
    }

    /**
     * 调度重连
     */
    private scheduleReconnect(token?: string): void {
        if (this.reconnectCount >= this.maxReconnects) {
            console.log('[ToolStateWS] 达到最大重连次数，停止重连');
            this.setState({ error: 'Max reconnects reached' });
            return;
        }

        this.reconnectCount++;
        this.setState({ reconnectCount: this.reconnectCount });

        const delay = this.reconnectInterval * Math.pow(2, this.reconnectCount - 1);
        console.log(`[ToolStateWS] 将在 ${delay}ms 后重连 (${this.reconnectCount}/${this.maxReconnects})`);

        this.reconnectTimer = setTimeout(() => {
            this.reconnectTimer = null;
            this.connect(token);
        }, delay);
    }

    /**
     * 停止重连
     */
    stopReconnect(): void {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
    }

    /**
     * 开始心跳
     */
    private startHeartbeat(): void {
        this.stopHeartbeat();
        this.heartbeatTimer = setInterval(() => {
            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                // 发送心跳消息（如果需要）
                // this.ws.send(JSON.stringify({ type: 'heartbeat' }));
            }
        }, this.heartbeatInterval);
    }

    /**
     * 停止心跳
     */
    private stopHeartbeat(): void {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }

    /**
     * 断开连接
     */
    disconnect(): void {
        this.stopReconnect();
        this.stopHeartbeat();

        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }

        this.setState({ connected: false, connecting: false });
    }

    /**
     * 获取当前状态
     */
    getState(): WebSocketClientState {
        return this.state;
    }

    /**
     * 发送消息（如果需要）
     */
    send(data: unknown): boolean {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            console.warn('[ToolStateWS] 无法发送消息，连接未打开');
            return false;
        }

        try {
            this.ws.send(JSON.stringify(data));
            return true;
        } catch (error) {
            console.error('[ToolStateWS] 发送消息失败:', error);
            return false;
        }
    }
}

// 创建全局单例
export const toolStateWebSocketClient = new ToolStateWebSocketClient();
