<template>
  <div class="skills-view">
    <div v-if="loading" class="loading">Loading skills...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else>
      <div class="search-bar">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search skills..."
          class="search-input"
          @input="handleSearch"
        />
      </div>
      <div v-if="skills.length === 0" class="empty">No skills found</div>
      <div v-else class="skills-list">
        <div v-for="skill in skills" :key="skill.name" class="skill-card">
          <div class="skill-header">
            <div class="skill-title">
              <span v-if="skill.metadata?.emoji" class="skill-emoji">{{ skill.metadata.emoji }}</span>
              <h4 class="skill-name">{{ skill.name }}</h4>
            </div>
          </div>
          <p class="skill-description">{{ skill.description }}</p>
          <div class="skill-meta">
            <span v-if="skill.metadata?.homepage" class="skill-link">
              <a :href="skill.metadata.homepage" target="_blank" rel="noopener">Homepage</a>
            </span>
            <span class="skill-directory">{{ skill.directory }}</span>
          </div>
          <div class="actions">
            <button
              class="btn btn-reload"
              @click="handleReload"
            >
              Reload All
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';

interface Skill {
  name: string;
  description: string;
  directory: string;
  metadata?: {
    emoji?: string;
    homepage?: string;
    always?: boolean;
  };
}

interface Props {
  apiBaseUrl?: string;
}

const props = withDefaults(defineProps<Props>(), {
  apiBaseUrl: '/api',
});

const loading = ref(true);
const error = ref<string | null>(null);
const skills = ref<Skill[]>([]);
const searchQuery = ref('');

const fetchSkills = async () => {
  try {
    loading.value = true;
    error.value = null;
    const url = searchQuery.value
      ? `${props.apiBaseUrl}/skills/search?q=${encodeURIComponent(searchQuery.value)}`
      : `${props.apiBaseUrl}/skills`;
    const response = await fetch(url);
    if (!response.ok) throw new Error('Failed to fetch skills');
    const data = await response.json();
    skills.value = data.skills || [];
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Unknown error';
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  fetchSkills();
};

const handleReload = async () => {
  try {
    await fetch(`${props.apiBaseUrl}/skills/reload`, {
      method: 'POST',
    });
    await fetchSkills();
  } catch (e) {
    error.value = 'Failed to reload skills';
  }
};

onMounted(() => {
  fetchSkills();
});
</script>

<style scoped>
.skills-view {
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

.search-bar {
  margin-bottom: 1rem;
}

.search-input {
  width: 100%;
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.skills-list {
  display: grid;
  gap: 1rem;
}

.skill-card {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1rem;
  background: #fff;
}

.skill-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.skill-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.skill-emoji {
  font-size: 1.5rem;
}

.skill-name {
  margin: 0;
  font-size: 1.1rem;
}

.skill-description {
  color: #444;
  font-size: 0.9rem;
  margin: 0.5rem 0;
  line-height: 1.5;
}

.skill-meta {
  display: flex;
  gap: 1rem;
  font-size: 0.8rem;
  color: #666;
}

.skill-link a {
  color: #007bff;
  text-decoration: none;
}

.skill-link a:hover {
  text-decoration: underline;
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

.btn-reload {
  background: #007bff;
  color: white;
}

.btn:hover {
  opacity: 0.9;
}
</style>
