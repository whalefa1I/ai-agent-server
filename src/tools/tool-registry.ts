/**
 * Tool Registry - 注册所有工具元数据和视图组件映射
 *
 * 这个文件定义了所有可用工具及其对应的 Vue 视图组件
 */

import type { Component } from 'vue';

// 导入视图组件
import McpServerView from './views/McpServerView.vue';
import SkillsView from './views/SkillsView.vue';

/**
 * 工具元数据接口
 */
export interface ToolMetadata {
  name: string;
  description: string;
  category: 'mcp' | 'skill' | 'local' | 'external';
  viewComponent?: Component;
  requiresConfirmation?: boolean;
  enabled?: boolean;
}

/**
 * 已注册的工具列表
 */
export const TOOLS: Record<string, ToolMetadata> = {
  // MCP 工具
  mcp_connect: {
    name: 'mcp_connect',
    description: 'Connect to an MCP server',
    category: 'mcp',
    viewComponent: McpServerView,
    requiresConfirmation: false,
    enabled: true,
  },
  mcp_disconnect: {
    name: 'mcp_disconnect',
    description: 'Disconnect from an MCP server',
    category: 'mcp',
    viewComponent: McpServerView,
    requiresConfirmation: false,
    enabled: true,
  },

  // Skill 工具
  skill_install: {
    name: 'skill_install',
    description: 'Install a skill from ClawHub',
    category: 'skill',
    viewComponent: SkillsView,
    requiresConfirmation: true,
    enabled: true,
  },
  skill_uninstall: {
    name: 'skill_uninstall',
    description: 'Uninstall a skill',
    category: 'skill',
    viewComponent: SkillsView,
    requiresConfirmation: true,
    enabled: true,
  },
  skill_search: {
    name: 'skill_search',
    description: 'Search for skills on ClawHub',
    category: 'skill',
    viewComponent: SkillsView,
    requiresConfirmation: false,
    enabled: true,
  },
};

/**
 * 获取工具的视图组件
 */
export function getToolViewComponent(toolName: string): Component | undefined {
  return TOOLS[toolName]?.viewComponent;
}

/**
 * 获取所有已启用的工具
 */
export function getEnabledTools(): ToolMetadata[] {
  return Object.values(TOOLS).filter(tool => tool.enabled !== false);
}

/**
 * 按类别获取工具
 */
export function getToolsByCategory(category: ToolMetadata['category']): ToolMetadata[] {
  return Object.values(TOOLS).filter(tool => tool.category === category);
}

/**
 * 注册新工具
 */
export function registerTool(name: string, metadata: Omit<ToolMetadata, 'name'>): void {
  TOOLS[name] = { ...metadata, name };
}

/**
 * 注销工具
 */
export function unregisterTool(name: string): void {
  delete TOOLS[name];
}
