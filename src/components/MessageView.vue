<template>
  <div class="message-view" :class="`message-${message.kind}`">
    <!-- Text Message -->
    <div v-if="message.kind === 'text'" class="text-message">
      <div v-if="message.thinking" class="thinking-indicator">
        <span class="thinking-icon">💭</span>
        <span class="thinking-label">Thinking</span>
      </div>
      <div class="text-content" :class="{ 'is-thinking': message.thinking }">
        {{ message.text }}
      </div>
    </div>

    <!-- Reasoning Message -->
    <div v-else-if="message.kind === 'reasoning'" class="reasoning-message">
      <div class="thinking-indicator">
        <span class="thinking-icon">💭</span>
        <span class="thinking-label">Thinking</span>
      </div>
      <div class="reasoning-content">
        {{ message.text }}
      </div>
    </div>

    <!-- Service Message -->
    <div v-else-if="message.kind === 'service'" class="service-message">
      <span class="service-icon">⚙️</span>
      <span class="service-content">{{ message.text }}</span>
    </div>

    <!-- Tool Call Message -->
    <div v-else-if="message.kind === 'tool-call' && message.tool" class="tool-call-message">
      <ToolCallView
        :tool="message.tool"
        :metadata="metadata"
        :messages="messages"
        :default-expanded="defaultExpanded"
      />
    </div>

    <!-- File Message -->
    <div v-else-if="message.kind === 'file'" class="file-message">
      <span class="file-icon">📄</span>
      <span class="file-name">{{ message.file?.name }}</span>
      <span class="file-size">{{ formatFileSize(message.file?.size) }}</span>
    </div>

    <!-- Unknown Message -->
    <div v-else class="unknown-message">
      <span class="unknown-label">Unknown message type</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Message, Metadata } from '@/types/happy-protocol';
import ToolCallView from './ToolCallView.vue';

interface Props {
  message: Message;
  metadata?: Metadata | null;
  messages?: Message[];
  defaultExpanded?: boolean;
}

withDefaults(defineProps<Props>(), {
  metadata: null,
  messages: undefined,
  defaultExpanded: false
});

/**
 * 格式化文件大小
 */
function formatFileSize(size?: number): string {
  if (size === undefined) return '';

  const units = ['B', 'KB', 'MB', 'GB'];
  let unitIndex = 0;
  let formattedSize = size;

  while (formattedSize >= 1024 && unitIndex < units.length - 1) {
    formattedSize /= 1024;
    unitIndex++;
  }

  return `${formattedSize.toFixed(1)} ${units[unitIndex]}`;
}
</script>

<style scoped>
.message-view {
  display: block;
}

/* ==================== Text Message ==================== */
.text-message {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.thinking-indicator {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.5rem;
  background: #f3f4f6;
  border-radius: 4px;
  width: fit-content;
}

.thinking-icon {
  font-size: 0.875rem;
}

.thinking-label {
  font-size: 0.75rem;
  color: #6b7280;
  font-style: italic;
}

.text-content {
  font-size: 0.875rem;
  line-height: 1.6;
  color: #111827;
  white-space: pre-wrap;
  word-break: break-word;
}

.text-content.is-thinking {
  color: #6b7280;
  font-style: italic;
}

/* ==================== Reasoning Message ==================== */
.reasoning-message {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.reasoning-content {
  font-size: 0.875rem;
  line-height: 1.6;
  color: #9ca3af;
  white-space: pre-wrap;
  word-break: break-word;
  font-style: italic;
  background: #f9fafb;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  border-left: 3px solid #e5e7eb;
}

/* ==================== Service Message ==================== */
.service-message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  background: #f9fafb;
  border-radius: 6px;
  font-size: 0.875rem;
  color: #6b7280;
}

.service-icon {
  font-size: 1rem;
}

.service-content {
  font-style: italic;
}

/* ==================== Tool Call Message ==================== */
.tool-call-message {
  /* ToolCallView handles its own styling */
}

/* ==================== File Message ==================== */
.file-message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  background: #f9fafb;
  border-radius: 6px;
  font-size: 0.875rem;
}

.file-icon {
  font-size: 1.25rem;
}

.file-name {
  flex: 1;
  color: #111827;
  font-weight: 500;
}

.file-size {
  color: #9ca3af;
  font-size: 0.75rem;
}

/* ==================== Unknown Message ==================== */
.unknown-message {
  padding: 0.5rem 0.75rem;
  background: #fef3c7;
  border-radius: 6px;
  font-size: 0.75rem;
  color: #92400e;
}
</style>
