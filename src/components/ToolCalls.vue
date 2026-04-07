<template>
  <div class="tool-calls">
    <!-- 使用 Happy Protocol 兼容的工具调用显示 -->
    <div v-if="useHappyProtocol" class="happy-tool-calls">
      <MessageView
        v-for="message in messages"
        :key="message.id"
        :message="message"
        :metadata="metadata"
        :messages="messages"
        :default-expanded="defaultExpanded"
      />
    </div>

    <!-- 传统模式：向后兼容 -->
    <div v-else class="legacy-tool-calls">
      <div v-for="(call, index) in toolCalls" :key="call.id || index" class="tool-call">
        <!-- MCP Connect -->
        <ToolCallView
          v-if="call.toolName === 'mcp_connect'"
          :tool="toToolCall(call, 'MCP Connect')"
          :metadata="metadata"
        />

        <!-- MCP Disconnect -->
        <ToolCallView
          v-else-if="call.toolName === 'mcp_disconnect'"
          :tool="toToolCall(call, 'MCP Disconnect')"
          :metadata="metadata"
        />

        <!-- Skill Install -->
        <ToolCallView
          v-else-if="call.toolName === 'skill_install'"
          :tool="toToolCall(call, 'Skill Install')"
          :metadata="metadata"
        />

        <!-- Skill Uninstall -->
        <ToolCallView
          v-else-if="call.toolName === 'skill_uninstall'"
          :tool="toToolCall(call, 'Skill Uninstall')"
          :metadata="metadata"
        />

        <!-- Skill Search -->
        <ToolCallView
          v-else-if="call.toolName === 'skill_search'"
          :tool="toToolCall(call, 'Skill Search')"
          :metadata="metadata"
        />

        <!-- Bash -->
        <ToolCallView
          v-else-if="call.toolName === 'bash'"
          :tool="toToolCall(call, 'Terminal Command')"
          :metadata="metadata"
        />

        <!-- Glob -->
        <ToolCallView
          v-else-if="call.toolName === 'glob'"
          :tool="toToolCall(call, 'Search Files')"
          :metadata="metadata"
        />

        <!-- Grep -->
        <ToolCallView
          v-else-if="call.toolName === 'grep'"
          :tool="toToolCall(call, 'Search Content')"
          :metadata="metadata"
        />

        <!-- File Read -->
        <ToolCallView
          v-else-if="call.toolName === 'file_read'"
          :tool="toToolCall(call, 'Read File')"
          :metadata="metadata"
        />

        <!-- File Write -->
        <ToolCallView
          v-else-if="call.toolName === 'file_write'"
          :tool="toToolCall(call, 'Write File')"
          :metadata="metadata"
        />

        <!-- File Edit -->
        <ToolCallView
          v-else-if="call.toolName === 'file_edit'"
          :tool="toToolCall(call, 'Edit File')"
          :metadata="metadata"
        />

        <!-- Todo Write -->
        <TodoListView
          v-else-if="call.toolName === 'todo_write'"
          :todos="extractTodos(call)"
        />

        <!-- Default fallback -->
        <ToolCallView
          v-else
          :tool="toToolCall(call, call.toolName)"
          :metadata="metadata"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ToolCall as HappyToolCall, Message, Metadata } from '@/types/happy-protocol';
import ToolCallView from './ToolCallView.vue';
import MessageView from './MessageView.vue';
import TodoListView from './TodoListView.vue';

interface LegacyToolCall {
  id?: string;
  toolName: string;
  args?: Record<string, unknown>;
  result?: unknown;
  status?: 'pending' | 'success' | 'error';
}

interface Props {
  toolCalls?: LegacyToolCall[];
  messages?: Message[];
  useHappyProtocol?: boolean;
  metadata?: Metadata | null;
  defaultExpanded?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  toolCalls: undefined,
  messages: undefined,
  useHappyProtocol: false,
  metadata: null,
  defaultExpanded: false
});

// 将传统 tool calls 转换为 Happy Protocol 格式
function toToolCall(legacy: LegacyToolCall, title: string): HappyToolCall {
  const stateMap: Record<string, HappyToolCall['state']> = {
    'pending': 'running',
    'success': 'completed',
    'error': 'error'
  };

  return {
    id: legacy.id || `tool-${Date.now()}`,
    name: legacy.toolName,
    title,
    description: '',
    args: legacy.args || {},
    result: legacy.result,
    state: stateMap[legacy.status || 'pending'] || 'running',
    startTime: Date.now(),
    endTime: legacy.status === 'success' || legacy.status === 'error' ? Date.now() : undefined,
    durationMs: undefined
  };
}

// 从 tool call 中提取 todos
function extractTodos(call: LegacyToolCall) {
  const todos = call.args?.todos;
  if (Array.isArray(todos)) {
    return todos;
  }
  return [];
}

// 使用消息列表（Happy Protocol）或传统 tool calls
const messages = computed(() => {
  if (props.useHappyProtocol && props.messages) {
    return props.messages;
  }

  // 从传统 tool calls 生成消息
  if (props.toolCalls) {
    return props.toolCalls.map((call, index): Message => ({
      kind: 'tool-call',
      id: call.id || `tool-${index}`,
      time: Date.now(),
      role: 'agent',
      tool: toToolCall(call, call.toolName)
    }));
  }

  return [];
});
</script>

<style scoped>
.tool-calls {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.happy-tool-calls,
.legacy-tool-calls {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.tool-call {
  /* ToolCallView handles its own styling */
}
</style>
