/**
 * Happy Session Protocol 兼容类型定义
 *
 * 将后端的 tool-state/artifact 数据转换为 Happy 协议格式
 */

// ==================== Happy 协议事件类型 ====================

export type HappyRole = 'user' | 'agent';

export type HappyEventType =
  | 'text'
  | 'service'
  | 'tool-call-start'
  | 'tool-call-end'
  | 'file'
  | 'turn-start'
  | 'turn-end'
  | 'start'
  | 'stop'
  | 'reasoning';

export interface HappyEventBase {
  t: HappyEventType;
}

export interface TextEvent extends HappyEventBase {
  t: 'text';
  text: string;
  thinking?: boolean;
}

export interface ServiceEvent extends HappyEventBase {
  t: 'service';
  text: string;
}

export interface ReasoningEvent extends HappyEventBase {
  t: 'reasoning';
  delta: string;
}

export interface ToolCallStartEvent extends HappyEventBase {
  t: 'tool-call-start';
  call: string;
  name: string;
  title: string;
  description: string;
  args: Record<string, unknown>;
}

export interface ToolCallEndEvent extends HappyEventBase {
  t: 'tool-call-end';
  call: string;
}

export interface FileEvent extends HappyEventBase {
  t: 'file';
  ref: string;
  name: string;
  size: number;
  image?: {
    width: number;
    height: number;
    thumbhash: string;
  };
}

export interface TurnStartEvent extends HappyEventBase {
  t: 'turn-start';
}

export interface TurnEndEvent extends HappyEventBase {
  t: 'turn-end';
  status: 'completed' | 'failed' | 'cancelled';
}

export interface StartEvent extends HappyEventBase {
  t: 'start';
  title?: string;
}

export interface StopEvent extends HappyEventBase {
  t: 'stop';
}

export type HappyEvent =
  | TextEvent
  | ServiceEvent
  | ReasoningEvent
  | ToolCallStartEvent
  | ToolCallEndEvent
  | FileEvent
  | TurnStartEvent
  | TurnEndEvent
  | StartEvent
  | StopEvent;

// ==================== Happy 协议消息信封 ====================

export interface HappyMessage {
  id: string;           // cuid2 格式
  time: number;         // Unix 时间戳 (毫秒)
  role: HappyRole;
  turn?: string;        // turn id (cuid2)
  subagent?: string;    // 子代理 id (cuid2)
  ev: HappyEvent;
}

// ==================== 后端 Artifact 类型 ====================

export interface BackendArtifact {
  id: string;
  sessionId: string;
  accountId: string;
  header: string;       // Base64 编码的 JSON
  body: string | null;  // Base64 编码的 JSON
  headerVersion: number;
  bodyVersion: number;
  seq: number;
  createdAt: string;
  updatedAt: string;
}

// ==================== 解码后的 Artifact Header/Body ====================

export interface DecryptedHeader {
  type: string;
  subtype?: string;
  title?: string;
  toolName?: string;
  toolDisplayName?: string;
  icon?: string;
  status?: string;
  timestamp?: number;
  [key: string]: unknown;
}

export interface DecryptedBody {
  type?: string;
  content?: string;
  input?: Record<string, unknown>;
  output?: unknown;
  status?: 'started' | 'running' | 'completed' | 'failed' | 'error';
  timestamp?: number;
  durationMs?: number;
  [key: string]: unknown;
}

// ==================== 转换后的 Tool Call 类型 ====================

export interface ToolCall {
  id: string;
  name: string;
  title: string;
  description: string;
  args: Record<string, unknown>;
  result?: unknown;
  state: 'running' | 'completed' | 'error';
  startTime: number;
  endTime?: number;
  durationMs?: number;
}

// ==================== 消息类型 (兼容 happy-app) ====================

export interface Message {
  kind: 'tool-call' | 'text' | 'service' | 'file' | 'reasoning';
  id: string;
  time: number;
  role: HappyRole;
  turn?: string;
  subagent?: string;
  tool?: ToolCall;
  text?: string;
  thinking?: boolean;
  file?: {
    ref: string;
    name: string;
    size: number;
    image?: {
      width: number;
      height: number;
      thumbhash: string;
    };
  };
}

// ==================== Metadata 类型 (用于路径解析) ====================

export interface Metadata {
  cwd?: string;
  home?: string;
  [key: string]: unknown;
}

// ==================== 工具定义类型 ====================

export interface ToolDefinition {
  title?: string | ((opts: { metadata: Metadata | null; tool: ToolCall }) => string);
  icon: (size: number, color: string) => React.ReactNode;
  noStatus?: boolean;
  hideDefaultError?: boolean;
  hidden?: boolean;
  isMutable?: boolean;
  input?: Record<string, unknown>;
  result?: Record<string, unknown>;
  minimal?: boolean | ((opts: { metadata: Metadata | null; tool: ToolCall; messages?: Message[] }) => boolean);
  extractDescription?: (opts: { metadata: Metadata | null; tool: ToolCall }) => string;
  extractSubtitle?: (opts: { metadata: Metadata | null; tool: ToolCall }) => string | null;
  extractStatus?: (opts: { metadata: Metadata | null; tool: ToolCall }) => string | null;
}

// ==================== 工具注册表类型 ====================

export interface KnownTools {
  [name: string]: ToolDefinition;
}
