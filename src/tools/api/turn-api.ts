/**
 * Turn History API 客户端
 *
 * 用于获取和管理会话 Turn 历史
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export interface ChatMessage {
  id: string;
  type: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL';
  content: string;
  timestamp?: string;
  inputTokens?: number;
  outputTokens?: number;
}

export interface TurnMetrics {
  inputTokens: number;
  outputTokens: number;
  latencyMs?: number;
  toolMetrics?: Record<string, unknown>;
}

export interface TurnInfo {
  turnId: string;
  userInput: string;
  startedAt?: string;
  completedAt?: string;
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
  messages: ChatMessage[];
  toolCallCount: number;
  durationMs?: number;
  metrics?: TurnMetrics;
}

export interface TurnListResponse {
  turns: TurnInfo[];
  total: number;
  hasMore: boolean;
  error?: string;
}

export interface TurnDetailResponse {
  turn: TurnInfo;
  error?: string;
}

export interface CurrentTurnResponse {
  sessionId: string;
  totalTurns: number;
  sessionStartedAt?: string;
  durationSeconds: number;
  error?: string;
}

/**
 * 获取 Turn 历史列表
 */
export async function getTurnHistory(
  sessionId?: string,
  limit: number = 20,
  offset: number = 0
): Promise<TurnListResponse> {
  const params = new URLSearchParams({
    limit: limit.toString(),
    offset: offset.toString()
  });

  if (sessionId) {
    params.set('sessionId', sessionId);
  }

  const response = await fetch(`${API_BASE_URL}/api/v1/turns?${params}`);

  if (!response.ok) {
    if (response.status === 404) {
      return { turns: [], total: 0, hasMore: false };
    }
    throw new Error(`Failed to fetch turns: HTTP ${response.status}`);
  }

  return response.json();
}

/**
 * 获取单个 Turn 详情
 */
export async function getTurnDetail(turnId: string): Promise<TurnDetailResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/turns/${turnId}`);

  if (!response.ok) {
    if (response.status === 404) {
      return { turn: null as unknown as TurnInfo, error: 'Turn not found' };
    }
    throw new Error(`Failed to fetch turn: HTTP ${response.status}`);
  }

  return response.json();
}

/**
 * 获取当前 Turn 状态
 */
export async function getCurrentTurn(): Promise<CurrentTurnResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/turns/current`);

  if (!response.ok) {
    throw new Error(`Failed to fetch current turn: HTTP ${response.status}`);
  }

  return response.json();
}

/**
 * 格式化 Turn ID 用于显示
 */
export function formatTurnId(turnId: string): string {
  if (!turnId) return '';
  const parts = turnId.split('_');
  const lastPart = parts[parts.length - 1];
  return lastPart.length > 8 ? lastPart.substring(0, 8) : lastPart;
}

/**
 * 格式化持续时间
 */
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  const seconds = (ms / 1000).toFixed(1);
  return `${seconds}s`;
}

/**
 * 截断文本
 */
export function truncate(text: string, maxLength: number): string {
  if (!text) return '';
  if (text.length <= maxLength) return text;
  return text.substring(0, maxLength) + '...';
}
