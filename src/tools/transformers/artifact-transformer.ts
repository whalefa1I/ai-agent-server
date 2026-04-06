/**
 * Artifact Transformer
 *
 * 将后端 ToolArtifact 转换为 Happy Protocol 的 Message 格式
 */

import type {
    BackendArtifact,
    DecryptedHeader,
    DecryptedBody,
    HappyMessage,
    HappyEvent,
    ToolCall,
    Message
} from '@/types/happy-protocol';
import { decodeHeader, decodeBody } from './api/artifact-api';

/**
 * 生成 cuid2 格式的 ID
 * 简化实现，使用随机字符串
 */
function generateId(): string {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    let result = '';
    for (let i = 0; i < 8; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
}

/**
 * 将 Artifact Header 转换为 ToolCall
 */
export function headerToToolCall(
    artifact: BackendArtifact,
    header: DecryptedHeader,
    body: DecryptedBody | null
): ToolCall | null {
    const toolName = header.toolName || header.type || 'unknown';

    // 根据 artifact 类型构建 ToolCall
    const toolCall: ToolCall = {
        id: artifact.id,
        name: toolName,
        title: header.title || toolName,
        description: header.subtitle || '',
        args: body?.input || {},
        state: mapStatusToState(header.status),
        startTime: header.timestamp || artifact.createdAt ? new Date(artifact.createdAt).getTime() : Date.now(),
        endTime: body?.timestamp,
        durationMs: body?.durationMs
    };

    // 如果有 output，添加到 result
    if (body?.output !== undefined) {
        toolCall.result = body.output;
    }

    return toolCall;
}

/**
 * 将状态字符串映射到 ToolCall state
 */
function mapStatusToState(status?: string): ToolCall['state'] {
    switch (status) {
        case 'running':
        case 'executing':
        case 'started':
            return 'running';
        case 'completed':
        case 'success':
            return 'completed';
        case 'failed':
        case 'error':
            return 'error';
        default:
            return 'running';
    }
}

/**
 * 将 BackendArtifact 转换为 Happy Message
 */
export function artifactToMessage(artifact: BackendArtifact): Message | null {
    const header = decodeHeader(artifact.header);
    if (!header) {
        console.warn('Failed to decode artifact header:', artifact.id);
        return null;
    }

    const body = decodeBody(artifact.body);
    const toolCall = headerToToolCall(artifact, header, body);

    if (!toolCall) {
        return null;
    }

    // 根据 header type 决定 message kind
    const kind = getArtifactKind(header);

    const message: Message = {
        kind,
        id: artifact.id,
        time: header.timestamp || new Date(artifact.createdAt).getTime(),
        role: 'agent',
        turn: undefined,  // 可选，根据需要设置
        subagent: undefined
    };

    if (kind === 'tool-call' && toolCall) {
        message.tool = toolCall;
    } else if (kind === 'text' && body?.content) {
        message.text = body.content;
        message.thinking = header.type === 'thinking' || header.type === 'reasoning';
    } else if (kind === 'service' && body?.content) {
        message.text = body.content;
    }

    return message;
}

/**
 * 根据 artifact header 决定 message kind
 */
function getArtifactKind(header: DecryptedHeader): Message['kind'] {
    const type = header.type?.toLowerCase() || '';

    if (header.toolName || type === 'tool') {
        return 'tool-call';
    }
    if (type === 'text' || type === 'message') {
        return 'text';
    }
    if (type === 'service' || type === 'system') {
        return 'service';
    }
    if (type === 'file') {
        return 'file';
    }

    // 默认当作 tool-call
    return 'tool-call';
}

/**
 * 将 ToolCall 转换为 Happy Event
 */
export function toolCallToEvent(
    toolCall: ToolCall,
    eventType: 'tool-call-start' | 'tool-call-end'
): HappyEvent {
    if (eventType === 'tool-call-start') {
        return {
            t: 'tool-call-start',
            call: toolCall.id,
            name: toolCall.name,
            title: toolCall.title,
            description: toolCall.description,
            args: toolCall.args
        };
    } else {
        return {
            t: 'tool-call-end',
            call: toolCall.id
        };
    }
}

/**
 * 创建 TurnStart 事件
 */
export function createTurnStartEvent(): HappyEvent {
    return { t: 'turn-start' };
}

/**
 * 创建 TurnEnd 事件
 */
export function createTurnEndEvent(status: 'completed' | 'failed' | 'cancelled'): HappyEvent {
    return { t: 'turn-end', status };
}

/**
 * 创建 Text 事件
 */
export function createTextEvent(text: string, thinking?: boolean): HappyEvent {
    return {
        t: 'text',
        text,
        thinking
    };
}

/**
 * 创建 Service 事件
 */
export function createServiceEvent(text: string): HappyEvent {
    return {
        t: 'service',
        text
    };
}

/**
 * 创建 Happy Message
 */
export function createHappyMessage(
    event: HappyEvent,
    options?: {
        role?: 'user' | 'agent';
        turn?: string;
        subagent?: string;
    }
): HappyMessage {
    return {
        id: generateId(),
        time: Date.now(),
        role: options?.role || 'agent',
        turn: options?.turn,
        subagent: options?.subagent,
        ev: event
    };
}

/**
 * 将 artifacts 列表转换为 messages 列表
 */
export function artifactsToMessages(artifacts: BackendArtifact[]): Message[] {
    return artifacts
        .map(artifact => artifactToMessage(artifact))
        .filter((msg): msg is Message => msg !== null);
}

/**
 * 按时间排序 messages
 */
export function sortMessagesByTime(messages: Message[]): Message[] {
    return messages.sort((a, b) => a.time - b.time);
}
