<template>
  <div class="turn-history">
    <!-- Turn 列表头部 -->
    <div class="turn-header" v-if="turns.length > 0">
      <div class="turn-title">
        <span class="turn-icon">📋</span>
        <span class="turn-label">Conversation Turns</span>
        <span class="turn-count">{{ turns.length }} total</span>
      </div>
      <button
        class="refresh-btn"
        @click="fetchTurns"
        :disabled="loading"
      >
        <span :class="{ 'spinning': loading }">🔄</span>
      </button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading && turns.length === 0" class="loading-state">
      <span class="loading-icon">⏳</span>
      <span class="loading-text">Loading turns...</span>
    </div>

    <!-- 空状态 -->
    <div v-else-if="turns.length === 0 && !loading" class="empty-state">
      <span class="empty-icon">📭</span>
      <span class="empty-text">No turns yet</span>
    </div>

    <!-- Turn 列表 -->
    <div v-else class="turn-list">
      <div
        v-for="turn in turns"
        :key="turn.turnId"
        class="turn-card"
        :class="turn.status"
        @click="toggleExpand(turn.turnId)"
      >
        <!-- Turn 头部 -->
        <div class="turn-card-header">
          <div class="turn-status-indicator">
            <span v-if="turn.status === 'completed'" class="status-icon completed">✓</span>
            <span v-else-if="turn.status === 'in_progress'" class="status-icon in-progress">⟳</span>
            <span v-else-if="turn.status === 'failed'" class="status-icon failed">!</span>
            <span v-else class="status-icon pending">◻</span>
          </div>

          <div class="turn-summary">
            <div class="turn-user-input">{{ truncateInput(turn.userInput, 80) }}</div>
            <div class="turn-meta">
              <span class="turn-id">ID: {{ formatTurnId(turn.turnId) }}</span>
              <span class="turn-tools" v-if="turn.toolCallCount > 0">
                🔧 {{ turn.toolCallCount }} tool{{ turn.toolCallCount > 1 ? 's' : '' }}
              </span>
              <span class="turn-duration" v-if="turn.durationMs">
                ⏱️ {{ formatDuration(turn.durationMs) }}
              </span>
            </div>
          </div>

          <div class="turn-expand-icon">
            <span :class="{ 'expanded': expandedTurns.has(turn.turnId) }">▼</span>
          </div>
        </div>

        <!-- Turn 详情（展开后显示） -->
        <div v-if="expandedTurns.has(turn.turnId)" class="turn-details">
          <div class="messages-section">
            <div
              v-for="msg in turn.messages"
              :key="msg.id"
              class="message-row"
              :class="msg.type?.toLowerCase()"
            >
              <div class="message-type-badge">
                {{ msg.type }}
              </div>
              <div class="message-content">
                {{ msg.content || '(no content)' }}
              </div>
            </div>
          </div>

          <!-- Turn 指标 -->
          <div class="turn-metrics" v-if="turn.metrics">
            <div class="metric">
              <span class="metric-label">Input tokens:</span>
              <span class="metric-value">{{ turn.metrics.inputTokens || 0 }}</span>
            </div>
            <div class="metric">
              <span class="metric-label">Output tokens:</span>
              <span class="metric-value">{{ turn.metrics.outputTokens || 0 }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';

interface ChatMessage {
  id: string;
  type: string;
  content: string;
  timestamp?: string;
  inputTokens?: number;
  outputTokens?: number;
}

interface TurnMetrics {
  inputTokens: number;
  outputTokens: number;
  latencyMs?: number;
  toolMetrics?: Record<string, unknown>;
}

interface TurnInfo {
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

interface TurnListResponse {
  turns: TurnInfo[];
  total: number;
  hasMore: boolean;
  error?: string;
}

const props = defineProps<{
  sessionId?: string;
}>();

const turns = ref<TurnInfo[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const expandedTurns = ref<Set<string>>(new Set());

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

/**
 * 获取 Turn 历史
 */
async function fetchTurns() {
  loading.value = true;
  error.value = null;

  try {
    const url = props.sessionId
      ? `${apiBaseUrl}/api/v1/turns?sessionId=${props.sessionId}&limit=20`
      : `${apiBaseUrl}/api/v1/turns?limit=20`;

    const response = await fetch(url);

    if (!response.ok) {
      if (response.status === 404) {
        turns.value = [];
        return;
      }
      throw new Error(`HTTP ${response.status}`);
    }

    const data: TurnListResponse = await response.json();

    if (data.error) {
      error.value = data.error;
    } else {
      turns.value = data.turns;
    }
  } catch (e) {
    console.error('Failed to fetch turns:', e);
    error.value = `Failed to load turns: ${e}`;
  } finally {
    loading.value = false;
  }
}

/**
 * 切换 Turn 展开/折叠
 */
function toggleExpand(turnId: string) {
  if (expandedTurns.value.has(turnId)) {
    expandedTurns.value.delete(turnId);
  } else {
    expandedTurns.value.add(turnId);
  }
}

/**
 * 截断用户输入
 */
function truncateInput(input: string, maxLength: number): string {
  if (!input) return '';
  if (input.length <= maxLength) return input;
  return input.substring(0, maxLength) + '...';
}

/**
 * 格式化 Turn ID（缩短显示）
 */
function formatTurnId(turnId: string): string {
  if (!turnId) return '';
  // 提取最后 8 位
  const parts = turnId.split('_');
  const lastPart = parts[parts.length - 1];
  return lastPart.length > 8 ? lastPart.substring(0, 8) : lastPart;
}

/**
 * 格式化持续时间
 */
function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  const seconds = (ms / 1000).toFixed(1);
  return `${seconds}s`;
}

// 组件挂载时自动加载
onMounted(() => {
  fetchTurns();
});

// 暴露方法供父组件调用
defineExpose({
  fetchTurns
});
</script>

<style scoped>
.turn-history {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

/* ==================== Header ==================== */
.turn-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  background: #f9fafb;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.turn-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: #374151;
}

.turn-icon {
  font-size: 1rem;
}

.turn-count {
  font-size: 0.75rem;
  color: #9ca3af;
  font-weight: 400;
}

.refresh-btn {
  background: none;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 0.25rem 0.5rem;
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.15s;
}

.refresh-btn:hover:not(:disabled) {
  background: #f3f4f6;
}

.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.spinning {
  display: inline-block;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ==================== Loading / Empty States ==================== */
.loading-state,
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 2rem 1rem;
  color: #9ca3af;
  font-size: 0.875rem;
}

.loading-icon,
.empty-icon {
  font-size: 1.25rem;
}

/* ==================== Turn List ==================== */
.turn-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* ==================== Turn Card ==================== */
.turn-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  transition: all 0.2s;
  cursor: pointer;
}

.turn-card:hover {
  border-color: #d1d5db;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.turn-card.completed {
  border-left: 3px solid #22c55e;
}

.turn-card.in-progress {
  border-left: 3px solid #3b82f6;
}

.turn-card.failed {
  border-left: 3px solid #ef4444;
}

.turn-card.pending {
  border-left: 3px solid #9ca3af;
}

/* ==================== Turn Card Header ==================== */
.turn-card-header {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.75rem;
  background: #f9fafb;
}

.turn-status-indicator {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-icon {
  font-size: 0.875rem;
  font-weight: bold;
}

.status-icon.completed {
  color: #22c55e;
}

.status-icon.in-progress {
  color: #3b82f6;
  animation: spin 2s linear infinite;
}

.status-icon.failed {
  color: #ef4444;
}

.status-icon.pending {
  color: #9ca3af;
}

.turn-summary {
  flex: 1;
  min-width: 0;
}

.turn-user-input {
  font-size: 0.875rem;
  font-weight: 500;
  color: #111827;
  margin-bottom: 0.25rem;
  word-break: break-word;
}

.turn-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: #6b7280;
}

.turn-id,
.turn-tools,
.turn-duration {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.turn-expand-icon {
  flex-shrink: 0;
  font-size: 0.625rem;
  color: #9ca3af;
  transition: transform 0.2s;
}

.turn-expand-icon.expanded {
  transform: rotate(180deg);
}

/* ==================== Turn Details ==================== */
.turn-details {
  border-top: 1px solid #e5e7eb;
  padding: 0.75rem;
  background: #fff;
}

.messages-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.message-row {
  display: flex;
  gap: 0.5rem;
  padding: 0.5rem;
  border-radius: 4px;
  font-size: 0.8125rem;
}

.message-row.user {
  background: #eff6ff;
  border-left: 2px solid #3b82f6;
}

.message-row.assistant {
  background: #f0fdf4;
  border-left: 2px solid #22c55e;
}

.message-row.tool {
  background: #fef3c7;
  border-left: 2px solid #f59e0b;
}

.message-row.system {
  background: #f3f4f6;
  border-left: 2px solid #6b7280;
}

.message-type-badge {
  flex-shrink: 0;
  padding: 0.125rem 0.375rem;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 3px;
  font-size: 0.625rem;
  font-weight: 600;
  text-transform: uppercase;
  color: #6b7280;
  min-width: 50px;
  text-align: center;
}

.message-content {
  flex: 1;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ==================== Turn Metrics ==================== */
.turn-metrics {
  display: flex;
  gap: 1rem;
  padding-top: 0.5rem;
  border-top: 1px solid #f3f4f6;
  font-size: 0.75rem;
}

.metric {
  display: flex;
  gap: 0.25rem;
  color: #6b7280;
}

.metric-label {
  color: #9ca3af;
}

.metric-value {
  font-weight: 500;
  color: #374151;
}
</style>
