<template>
  <div class="mcp-server-view">
    <div v-if="loading" class="loading">Loading MCP servers...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="servers.length === 0" class="empty">No MCP servers configured</div>
    <div v-else class="servers-list">
      <div v-for="server in servers" :key="server.name" class="server-card">
        <div class="server-header">
          <h4 class="server-name">{{ server.name }}</h4>
          <span :class="['status-badge', server.connected ? 'connected' : 'disconnected']">
            {{ server.connected ? 'Connected' : 'Disconnected' }}
          </span>
        </div>
        <div class="server-info">
          <div class="info-row">
            <span class="label">Transport:</span>
            <span class="value">{{ server.transport || 'stdio' }}</span>
          </div>
          <div class="info-row">
            <span class="label">URL:</span>
            <span class="value">{{ server.url || 'N/A' }}</span>
          </div>
        </div>
        <div v-if="server.tools && server.tools.length > 0" class="tools-section">
          <h5>Tools ({{ server.tools.length }})</h5>
          <ul class="tools-list">
            <li v-for="tool in server.tools" :key="tool.name" class="tool-item">
              {{ tool.name }}
            </li>
          </ul>
        </div>
        <div v-if="server.error" class="error-section">
          <span class="error-text">{{ server.error }}</span>
        </div>
        <div class="actions">
          <button
            v-if="!server.connected"
            class="btn btn-connect"
            @click="handleConnect(server.name)"
          >
            Connect
          </button>
          <button
            v-else
            class="btn btn-disconnect"
            @click="handleDisconnect(server.name)"
          >
            Disconnect
          </button>
          <button
            class="btn btn-health"
            @click="handleHealthCheck(server.name)"
          >
            Health Check
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';

interface McpServer {
  name: string;
  transport?: string;
  url?: string;
  connected: boolean;
  tools?: Array<{ name: string; description?: string }>;
  error?: string;
}

interface Props {
  apiBaseUrl?: string;
}

const props = withDefaults(defineProps<Props>(), {
  apiBaseUrl: '/api',
});

const loading = ref(true);
const error = ref<string | null>(null);
const servers = ref<McpServer[]>([]);

const fetchServers = async () => {
  try {
    loading.value = true;
    error.value = null;
    const response = await fetch(`${props.apiBaseUrl}/mcp/servers`);
    if (!response.ok) throw new Error('Failed to fetch MCP servers');
    servers.value = await response.json();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Unknown error';
  } finally {
    loading.value = false;
  }
};

const handleConnect = async (serverName: string) => {
  try {
    await fetch(`${props.apiBaseUrl}/mcp/servers/${serverName}/connect`, {
      method: 'POST',
    });
    await fetchServers();
  } catch (e) {
    error.value = 'Failed to connect';
  }
};

const handleDisconnect = async (serverName: string) => {
  try {
    await fetch(`${props.apiBaseUrl}/mcp/servers/${serverName}/disconnect`, {
      method: 'POST',
    });
    await fetchServers();
  } catch (e) {
    error.value = 'Failed to disconnect';
  }
};

const handleHealthCheck = async (serverName: string) => {
  try {
    const response = await fetch(`${props.apiBaseUrl}/mcp/servers/${serverName}/health`, {
      method: 'POST',
    });
    const result = await response.json();
    if (!result.success) {
      error.value = result.error || 'Health check failed';
    }
  } catch (e) {
    error.value = 'Health check failed';
  }
};

onMounted(() => {
  fetchServers();
});
</script>

<style scoped>
.mcp-server-view {
  padding: 1rem;
}

.loading, .empty {
  text-align: center;
  color: #666;
  padding: 2rem;
}

.error {
  color: #dc3545;
  padding: 1rem;
  background: #f8d7da;
  border-radius: 4px;
}

.servers-list {
  display: grid;
  gap: 1rem;
}

.server-card {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1rem;
  background: #fff;
}

.server-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.server-name {
  margin: 0;
  font-size: 1.1rem;
}

.status-badge {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
}

.status-badge.connected {
  background: #d4edda;
  color: #155724;
}

.status-badge.disconnected {
  background: #f8d7da;
  color: #721c24;
}

.server-info {
  margin-bottom: 0.5rem;
}

.info-row {
  display: flex;
  gap: 0.5rem;
  font-size: 0.9rem;
}

.info-row .label {
  font-weight: 600;
  color: #666;
}

.tools-section {
  margin: 0.5rem 0;
}

.tools-section h5 {
  margin: 0 0 0.25rem 0;
  font-size: 0.9rem;
  color: #666;
}

.tools-list {
  margin: 0;
  padding-left: 1.5rem;
}

.tool-item {
  font-size: 0.85rem;
  color: #444;
}

.error-section {
  margin: 0.5rem 0;
}

.error-text {
  color: #dc3545;
  font-size: 0.85rem;
}

.actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.btn {
  padding: 0.4rem 0.8rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
}

.btn-connect {
  background: #28a745;
  color: white;
}

.btn-disconnect {
  background: #dc3545;
  color: white;
}

.btn-health {
  background: #007bff;
  color: white;
}

.btn:hover {
  opacity: 0.9;
}
</style>
