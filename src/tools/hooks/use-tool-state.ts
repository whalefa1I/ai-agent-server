/**
 * 工具状态管理 Hook
 *
 * 管理工具状态的订阅、更新和清理
 */

import type { ToolCall, Message, BackendArtifact } from '@/types/happy-protocol';
import { artifactsToMessages, sortMessagesByTime } from './transformers/artifact-transformer';
import { fetchSessionArtifacts, fetchArtifacts } from './api/artifact-api';

export interface ToolState {
    /** 当前会话 ID */
    sessionId: string | null;
    /** 当前账户 ID */
    accountId: string | null;
    /** 工具调用列表 */
    toolCalls: ToolCall[];
    /** 转换后的消息列表 */
    messages: Message[];
    /** 是否正在加载 */
    loading: boolean;
    /** 错误信息 */
    error: string | null;
    /** WebSocket 连接状态 */
    wsConnected: boolean;
}

export interface ToolStateActions {
    /** 设置当前会话 */
    setSession: (sessionId: string, accountId: string) => Promise<void>;
    /** 刷新工具状态 */
    refresh: () => Promise<void>;
    /** 添加工具调用 */
    addToolCall: (toolCall: ToolCall) => void;
    /** 更新工具调用状态 */
    updateToolCall: (toolCallId: string, updates: Partial<ToolCall>) => void;
    /** 清除工具状态 */
    clear: () => void;
}

/**
 * 工具状态管理 Class
 */
export class ToolStateManager {
    private sessionId: string | null = null;
    private accountId: string | null = null;
    private toolCalls: ToolCall[] = [];
    private messages: Message[] = [];
    private loading: boolean = false;
    private error: string | null = null;
    private wsConnected: boolean = false;
    private listeners: Set<() => void> = new Set();

    /**
     * 订阅状态变化
     */
    subscribe(listener: () => void): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /**
     * 通知所有监听器
     */
    private notify(): void {
        this.listeners.forEach(listener => listener());
    }

    /**
     * 获取当前状态
     */
    getState(): ToolState {
        return {
            sessionId: this.sessionId,
            accountId: this.accountId,
            toolCalls: [...this.toolCalls],
            messages: [...this.messages],
            loading: this.loading,
            error: this.error,
            wsConnected: this.wsConnected
        };
    }

    /**
     * 设置会话并加载工具状态
     */
    async setSession(sessionId: string, accountId: string): Promise<void> {
        this.sessionId = sessionId;
        this.accountId = accountId;
        this.loading = true;
        this.error = null;
        this.notify();

        try {
            // 从 API 获取会话的所有 artifacts
            const artifacts = await fetchSessionArtifacts(sessionId);

            // 转换为 messages
            this.messages = sortMessagesByTime(artifactsToMessages(artifacts));

            // 提取 tool calls
            this.toolCalls = this.messages
                .filter(m => m.kind === 'tool-call' && m.tool)
                .map(m => m.tool!);

            this.loading = false;
            this.notify();
        } catch (err) {
            this.error = err instanceof Error ? err.message : 'Failed to fetch tool state';
            this.loading = false;
            this.notify();
        }
    }

    /**
     * 刷新工具状态
     */
    async refresh(): Promise<void> {
        if (!this.sessionId || !this.accountId) return;
        await this.setSession(this.sessionId, this.accountId);
    }

    /**
     * 添加工具调用
     */
    addToolCall(toolCall: ToolCall): void {
        this.toolCalls.push(toolCall);

        const message: Message = {
            kind: 'tool-call',
            id: toolCall.id,
            time: toolCall.startTime,
            role: 'agent',
            tool: toolCall
        };
        this.messages.push(message);
        this.messages = sortMessagesByTime(this.messages);

        this.notify();
    }

    /**
     * 更新工具调用状态
     */
    updateToolCall(toolCallId: string, updates: Partial<ToolCall>): void {
        const index = this.toolCalls.findIndex(tc => tc.id === toolCallId);
        if (index !== -1) {
            this.toolCalls[index] = { ...this.toolCalls[index], ...updates };

            // 同步更新 message
            const msgIndex = this.messages.findIndex(m => m.id === toolCallId);
            if (msgIndex !== -1 && this.messages[msgIndex].tool) {
                this.messages[msgIndex].tool = { ...this.toolCalls[index] };
            }

            this.notify();
        }
    }

    /**
     * 清除工具状态
     */
    clear(): void {
        this.sessionId = null;
        this.accountId = null;
        this.toolCalls = [];
        this.messages = [];
        this.loading = false;
        this.error = null;
        this.wsConnected = false;
        this.notify();
    }

    /**
     * 设置 WebSocket 连接状态
     */
    setWsConnected(connected: boolean): void {
        this.wsConnected = connected;
        this.notify();
    }

    /**
     * 获取账户的所有 artifacts
     */
    async loadAccountArtifacts(accountId: string): Promise<BackendArtifact[]> {
        this.loading = true;
        this.error = null;
        this.notify();

        try {
            const artifacts = await fetchArtifacts(accountId);
            this.messages = sortMessagesByTime(artifactsToMessages(artifacts));
            this.toolCalls = this.messages
                .filter(m => m.kind === 'tool-call' && m.tool)
                .map(m => m.tool!);
            this.loading = false;
            this.notify();
            return artifacts;
        } catch (err) {
            this.error = err instanceof Error ? err.message : 'Failed to fetch artifacts';
            this.loading = false;
            this.notify();
            return [];
        }
    }
}

// 创建单例
export const toolStateManager = new ToolStateManager();

/**
 * React Hook 封装 (如果将来使用 React)
 */
export function createToolStateHook(useSyncExternalStore: any) {
    return function useToolState() {
        return useSyncExternalStore(
            toolStateManager.subscribe.bind(toolStateManager),
            toolStateManager.getState.bind(toolStateManager)
        );
    };
}
