<template>
  <div class="tool-calls">
    <div v-for="(call, index) in toolCalls" :key="call.id || index" class="tool-call">
      <!-- MCP Connect -->
      <McpServerView
        v-if="call.toolName === 'mcp_connect'"
        :server-name="call.args?.serverName"
      />

      <!-- MCP Disconnect -->
      <div v-else-if="call.toolName === 'mcp_disconnect'" class="tool-result">
        <span class="icon">🔌</span>
        <span>Disconnecting from MCP server: {{ call.args?.serverName }}</span>
      </div>

      <!-- Skill Install -->
      <div v-else-if="call.toolName === 'skill_install'" class="tool-result">
        <span class="icon">📦</span>
        <span>Installing skill: {{ call.args?.skillId }}</span>
        <span v-if="call.args?.version" class="version">v{{ call.args.version }}</span>
      </div>

      <!-- Skill Uninstall -->
      <div v-else-if="call.toolName === 'skill_uninstall'" class="tool-result">
        <span class="icon">🗑️</span>
        <span>Uninstalling skill: {{ call.args?.skillId }}</span>
      </div>

      <!-- Skill Search -->
      <div v-else-if="call.toolName === 'skill_search'" class="tool-result">
        <span class="icon">🔍</span>
        <span>Searching for skills: {{ call.args?.query }}</span>
      </div>

      <!-- Default fallback -->
      <div v-else class="tool-result default">
        <span class="tool-name">{{ call.toolName }}</span>
        <pre v-if="call.args" class="args">{{ JSON.stringify(call.args, null, 2) }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineProps } from 'vue';
import McpServerView from './views/McpServerView.vue';

interface ToolCall {
  id?: string;
  toolName: string;
  args?: Record<string, unknown>;
  result?: unknown;
  status?: 'pending' | 'success' | 'error';
}

interface Props {
  toolCalls: ToolCall[];
}

defineProps<Props>();
</script>

<style scoped>
.tool-calls {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.tool-call {
  border: 1px solid #ddd;
  border-radius: 6px;
  padding: 0.75rem;
  background: #f8f9fa;
}

.tool-result {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
}

.tool-result .icon {
  font-size: 1.2rem;
}

.tool-result .version {
  background: #e9ecef;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  font-size: 0.8rem;
  color: #666;
}

.tool-result.default {
  flex-direction: column;
  align-items: flex-start;
}

.tool-name {
  font-weight: 600;
  color: #333;
}

.args {
  margin: 0.5rem 0 0 0;
  padding: 0.5rem;
  background: #fff;
  border-radius: 4px;
  font-size: 0.8rem;
  max-height: 200px;
  overflow: auto;
}
</style>
