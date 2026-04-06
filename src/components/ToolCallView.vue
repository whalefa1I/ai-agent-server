<template>
  <div class="tool-call-view" :class="{ 'minimal': isMinimal, 'expanded': !isMinimal }">
    <!-- 工具头部 -->
    <div class="tool-header" @click="toggleExpand">
      <div class="tool-icon">
        <component v-if="iconComponent" :is="iconComponent" :size="20" :color="iconColor" />
        <span v-else class="icon-placeholder">🔧</span>
      </div>
      <div class="tool-info">
        <div class="tool-title">{{ title }}</div>
        <div v-if="subtitle" class="tool-subtitle">{{ subtitle }}</div>
      </div>
      <div class="tool-status">
        <StatusIndicator v-if="!noStatus" :state="tool.state" />
        <button v-if="expandable" class="expand-btn">
          <span :class="{ 'rotated': isExpanded }">›</span>
        </button>
      </div>
    </div>

    <!-- 工具内容（展开时显示） -->
    <div v-if="isExpanded" class="tool-content">
      <!-- 输入参数 -->
      <div v-if="showArgs" class="tool-section">
        <div class="section-label">Input</div>
        <pre class="section-content">{{ formattedArgs }}</pre>
      </div>

      <!-- 输出结果 -->
      <div v-if="showResult" class="tool-section">
        <div class="section-label">Output</div>
        <pre class="section-content" :class="{ 'has-error': hasError }">{{ formattedResult }}</pre>
      </div>

      <!-- 错误信息 -->
      <div v-if="showError" class="tool-section error-section">
        <div class="section-label">Error</div>
        <pre class="section-content has-error">{{ errorText }}</pre>
      </div>

      <!-- 自定义内容插槽 -->
      <slot name="content"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { ToolCall, ToolDefinition, Message } from '@/types/happy-protocol';
import { getToolDefinition } from '@/tools/registry/known-tools';
import StatusIndicator from './StatusIndicator.vue';

interface Props {
  tool: ToolCall;
  metadata?: Metadata | null;
  messages?: Message[];
  defaultExpanded?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  metadata: null,
  messages: undefined,
  defaultExpanded: false
});

const isExpanded = ref(props.defaultExpanded);

// 获取工具定义
const toolDef = computed(() => getToolDefinition(props.tool.name));

// 是否无状态工具
const noStatus = computed(() => toolDef.value?.noStatus === true);

// 是否隐藏默认错误
const hideDefaultError = computed(() => toolDef.value?.hideDefaultError === true);

// 标题
const title = computed(() => {
  const def = toolDef.value;
  if (def?.title) {
    if (typeof def.title === 'function') {
      return def.title({ metadata: props.metadata, tool: props.tool });
    }
    return def.title;
  }
  return props.tool.title || props.tool.name;
});

// 副标题
const subtitle = computed(() => {
  const def = toolDef.value;
  if (def?.extractSubtitle) {
    return def.extractSubtitle({ metadata: props.metadata, tool: props.tool });
  }
  return props.tool.description || null;
});

// 描述
const description = computed(() => {
  const def = toolDef.value;
  if (def?.extractDescription) {
    return def.extractDescription({ metadata: props.metadata, tool: props.tool });
  }
  return props.tool.description;
});

// 状态
const statusText = computed(() => {
  const def = toolDef.value;
  if (def?.extractStatus) {
    return def.extractStatus({ metadata: props.metadata, tool: props.tool });
  }
  return null;
});

// 是否最小化显示
const isMinimal = computed(() => {
  const def = toolDef.value;
  if (def?.minimal === undefined) return false;
  if (typeof def.minimal === 'boolean') return def.minimal;
  return def.minimal({
    metadata: props.metadata,
    tool: props.tool,
    messages: props.messages
  });
});

// 是否可展开
const expandable = computed(() => !isMinimal.value || props.tool.args || props.tool.result);

// 图标
const iconComponent = computed(() => {
  const def = toolDef.value;
  if (def?.icon) {
    return def.icon(20, '#666');
  }
  return null;
});

const iconColor = computed(() => {
  if (props.tool.state === 'error') return '#ef4444';
  if (props.tool.state === 'completed') return '#22c55e';
  if (props.tool.state === 'running') return '#f59e0b';
  return '#666';
});

// 是否显示参数
const showArgs = computed(() => {
  return props.tool.args && Object.keys(props.tool.args).length > 0;
});

// 是否显示结果
const showResult = computed(() => {
  return props.tool.result !== undefined && props.tool.result !== null;
});

// 是否显示错误
const showError = computed(() => {
  if (hideDefaultError.value) return false;
  return props.tool.state === 'error';
});

// 是否有错误
const hasError = computed(() => {
  return props.tool.state === 'error';
});

// 错误文本
const errorText = computed(() => {
  if (props.tool.result && typeof props.tool.result === 'object') {
    const result = props.tool.result as Record<string, unknown>;
    return result.error as string || JSON.stringify(props.tool.result, null, 2);
  }
  return String(props.tool.result);
});

// 格式化的参数
const formattedArgs = computed(() => {
  return JSON.stringify(props.tool.args, null, 2);
});

// 格式化的结果
const formattedResult = computed(() => {
  if (typeof props.tool.result === 'object') {
    return JSON.stringify(props.tool.result, null, 2);
  }
  return String(props.tool.result);
});

// 切换展开/收起
function toggleExpand() {
  if (expandable.value) {
    isExpanded.value = !isExpanded.value;
  }
}
</script>

<style scoped>
.tool-call-view {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.tool-call-view.minimal {
  padding: 0.5rem;
}

.tool-call-view.expanded {
  padding: 0.75rem;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: default;
}

.expandable .tool-header {
  cursor: pointer;
}

.tool-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #f3f4f6;
  flex-shrink: 0;
}

.icon-placeholder {
  font-size: 1.25rem;
}

.tool-info {
  flex: 1;
  min-width: 0;
}

.tool-title {
  font-weight: 600;
  font-size: 0.875rem;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tool-subtitle {
  font-size: 0.75rem;
  color: #6b7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 0.125rem;
}

.tool-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.expand-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.25rem;
  color: #9ca3af;
  transition: transform 0.2s;
}

.expand-btn span {
  display: block;
  font-size: 1.25rem;
  line-height: 1;
}

.expand-btn span.rotated {
  transform: rotate(90deg);
}

.tool-content {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid #e5e7eb;
}

.tool-section {
  margin-bottom: 0.75rem;
}

.tool-section:last-child {
  margin-bottom: 0;
}

.section-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.375rem;
}

.section-content {
  background: #f9fafb;
  border-radius: 4px;
  padding: 0.5rem;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 0.75rem;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  max-height: 300px;
  overflow: auto;
}

.section-content.has-error {
  background: #fef2f2;
  color: #dc2626;
}

.error-section {
  margin-top: 0.5rem;
}
</style>
