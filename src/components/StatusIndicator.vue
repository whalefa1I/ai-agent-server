<template>
  <div class="status-indicator" :class="stateClass" :title="tooltip">
    <span class="indicator-dot"></span>
    <span v-if="showLabel" class="indicator-label">{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ToolCall } from '@/types/happy-protocol';

interface Props {
  state: ToolCall['state'];
  showLabel?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showLabel: false
});

const stateClass = computed(() => {
  switch (props.state) {
    case 'running':
      return 'state-running';
    case 'completed':
      return 'state-completed';
    case 'error':
      return 'state-error';
    default:
      return 'state-running';
  }
});

const label = computed(() => {
  switch (props.state) {
    case 'running':
      return 'Running';
    case 'completed':
      return 'Done';
    case 'error':
      return 'Error';
    default:
      return 'Running';
  }
});

const tooltip = computed(() => {
  switch (props.state) {
    case 'running':
      return 'Tool is running';
    case 'completed':
      return 'Tool completed successfully';
    case 'error':
      return 'Tool encountered an error';
    default:
      return '';
  }
});
</script>

<style scoped>
.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
}

.indicator-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.state-running .indicator-dot {
  background: #f59e0b;
  animation: pulse 1.5s ease-in-out infinite;
}

.state-completed .indicator-dot {
  background: #22c55e;
}

.state-error .indicator-dot {
  background: #ef4444;
}

.indicator-label {
  font-size: 0.75rem;
  color: #6b7280;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>
