<template>
  <div class="todo-list-view">
    <!-- 工具头部 -->
    <div class="tool-header" @click="toggleExpand">
      <div class="tool-icon">
        <span class="icon">📋</span>
      </div>
      <div class="tool-info">
        <div class="tool-title">Task List</div>
        <div class="tool-subtitle">{{ subtitle }}</div>
      </div>
      <div class="tool-status">
        <button class="expand-btn">
          <span :class="{ 'rotated': isExpanded }">›</span>
        </button>
      </div>
    </div>

    <!-- Todo 列表内容 -->
    <div v-if="isExpanded" class="todo-content">
      <div class="todo-items">
        <div
          v-for="(todo, index) in todos"
          :key="todo.id || index"
          class="todo-item"
          :class="todo.status"
        >
          <div class="todo-checkbox">
            <span class="checkbox-mark">
              {{ getCheckboxSymbol(todo.status) }}
            </span>
          </div>
          <div class="todo-content-text">
            <div class="todo-text" :class="{ 'completed': todo.status === 'completed' }">
              {{ todo.content }}
            </div>
            <div v-if="todo.status === 'in_progress'" class="todo-active-form">
              {{ todo.activeForm || todo.content }}
            </div>
            <!-- Subagent 标识 -->
            <div v-if="todo.useSubagent" class="todo-subagent-badge">
              <span class="subagent-icon">🚀</span>
              <span>Subagent</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

interface TodoItem {
  content: string;
  status: 'pending' | 'in_progress' | 'completed';
  activeForm?: string;
  id?: string;
  metadata?: {
    useSubagent?: boolean;
    reason?: string;
    [key: string]: unknown;
  };
}

interface Props {
  todos: TodoItem[];
  defaultExpanded?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  defaultExpanded: true
});

const isExpanded = ref(props.defaultExpanded);

const subtitle = computed(() => {
  if (props.todos.length === 0) {
    return 'No tasks';
  }
  const completed = props.todos.filter(t => t.status === 'completed').length;
  const inProgress = props.todos.filter(t => t.status === 'in_progress').length;
  const pending = props.todos.filter(t => t.status === 'pending').length;

  if (inProgress > 0) {
    return `${completed}/${props.todos.length} completed · ${inProgress} in progress`;
  }
  return `${completed}/${props.todos.length} completed`;
});

function getCheckboxSymbol(status: string): string {
  switch (status) {
    case 'completed':
      return '✓';
    case 'in_progress':
      return '◻';
    case 'pending':
    default:
      return '◻';
  }
}

function toggleExpand() {
  isExpanded.value = !isExpanded.value;
}
</script>

<style scoped>
.todo-list-view {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
  padding: 0.75rem;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.tool-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #dbeafe;
  flex-shrink: 0;
}

.tool-icon .icon {
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
}

.tool-subtitle {
  font-size: 0.75rem;
  color: #6b7280;
  margin-top: 0.125rem;
}

.tool-status {
  display: flex;
  align-items: center;
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

.todo-content {
  padding: 0.75rem;
}

.todo-items {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.todo-item {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.5rem;
  border-radius: 4px;
  transition: background 0.15s;
}

.todo-item:hover {
  background: #f9fafb;
}

.todo-item.completed {
  opacity: 0.7;
}

.todo-checkbox {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.checkbox-mark {
  font-size: 1rem;
  line-height: 1;
  font-weight: bold;
}

.todo-item.pending .checkbox-mark {
  color: #9ca3af;
}

.todo-item.in_progress .checkbox-mark {
  color: #3b82f6;
}

.todo-item.completed .checkbox-mark {
  color: #22c55e;
}

.todo-content-text {
  flex: 1;
  min-width: 0;
}

.todo-text {
  font-size: 0.875rem;
  color: #111827;
  line-height: 1.5;
}

.todo-text.completed {
  text-decoration: line-through;
  color: #9ca3af;
}

.todo-active-form {
  font-size: 0.75rem;
  color: #3b82f6;
  margin-top: 0.25rem;
  font-style: italic;
}

.todo-subagent-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.7rem;
  color: #7c3aed;
  background: #f3e8ff;
  padding: 0.125rem 0.375rem;
  border-radius: 9999px;
  margin-top: 0.375rem;
  font-weight: 500;
}

.todo-subagent-badge .subagent-icon {
  font-size: 0.75rem;
}
</style>
