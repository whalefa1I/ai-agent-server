/**
 * Known Tools Registry
 *
 * 定义所有已知工具的显示属性，参考 happy-project 的 knownTools
 */

import type { ToolDefinition, ToolCall, Message, Metadata } from '@/types/happy-protocol';

/**
 * 工具图标工厂
 */
function createIcon(svgPath: string) {
    return (size: number = 24, color: string = '#666') => ({
        type: 'svg',
        size,
        color,
        path: svgPath
    });
}

// ==================== 图标定义 ====================

const ICONS = {
    terminal: createIcon('M4 4h16v16H4z M6 8h4 M6 12h4 M12 8h4 M12 12h4'),
    search: createIcon('M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z'),
    file: createIcon('M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z M13 2v7h7'),
    edit: createIcon('M11 4H7a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-4 M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z'),
    list: createIcon('M4 6h16M4 10h16M4 14h16M4 18h16'),
    check: createIcon('M5 13l4 4L19 7'),
    globe: createIcon('M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9'),
    bulb: createIcon('M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z'),
    question: createIcon('M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z')
};

// ==================== 工具定义 ====================

export const knownTools: Record<string, ToolDefinition> = {
    // ==================== Bash 工具 ====================
    'bash': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.command === 'string') {
                const cmd = opts.tool.args.command;
                const firstWord = cmd.split(' ')[0];
                const cmdNames: Record<string, string> = {
                    'cd': 'Change Directory',
                    'ls': 'List Files',
                    'pwd': 'Print Working Directory',
                    'mkdir': 'Create Directory',
                    'rm': 'Remove',
                    'cp': 'Copy',
                    'mv': 'Move',
                    'npm': 'NPM',
                    'yarn': 'Yarn',
                    'git': 'Git',
                    'python': 'Python',
                    'node': 'Node'
                };
                return cmdNames[firstWord] || `Command: ${firstWord}`;
            }
            return 'Terminal Command';
        },
        icon: ICONS.terminal,
        minimal: true,
        hideDefaultError: true,
        isMutable: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.command === 'string') {
                const cmd = opts.tool.args.command;
                if (cmd.length > 40) {
                    return cmd.substring(0, 40) + '...';
                }
                return cmd;
            }
            return 'Execute shell command';
        },
        extractSubtitle: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.command === 'string') {
                return opts.tool.args.command;
            }
            return null;
        },
        extractStatus: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (opts.tool.state === 'completed' && opts.tool.result) {
                const result = opts.tool.result as Record<string, unknown>;
                if (result.stderr) {
                    return 'Completed with errors';
                }
                return 'Completed successfully';
            }
            return null;
        }
    },

    // ==================== Glob 工具 ====================
    'glob': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.pattern === 'string') {
                return opts.tool.args.pattern;
            }
            return 'Search Files';
        },
        icon: ICONS.search,
        minimal: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.pattern === 'string') {
                return `Search pattern: ${opts.tool.args.pattern}`;
            }
            return 'Search for files matching a pattern';
        },
        extractSubtitle: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.pattern === 'string') {
                return `Pattern: ${opts.tool.args.pattern}`;
            }
            return null;
        }
    },

    // ==================== Grep 工具 ====================
    'grep': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.pattern === 'string') {
                const pattern = opts.tool.args.pattern;
                if (pattern.length > 30) {
                    return `grep: ${pattern.substring(0, 30)}...`;
                }
                return `grep: ${pattern}`;
            }
            return 'Search Content';
        },
        icon: ICONS.search,
        minimal: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.pattern === 'string') {
                return `Search for pattern: ${opts.tool.args.pattern}`;
            }
            return 'Search for a pattern in files';
        },
        extractSubtitle: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.pattern === 'string') {
                return `Pattern: ${opts.tool.args.pattern}`;
            }
            return null;
        }
    },

    // ==================== Read 工具 ====================
    'file_read': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.file_path === 'string') {
                const path = opts.tool.args.file_path;
                const parts = path.split('/');
                return parts[parts.length - 1] || path;
            }
            return 'Read File';
        },
        icon: ICONS.file,
        minimal: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.file_path === 'string') {
                return `Reading: ${opts.tool.args.file_path}`;
            }
            return 'Read file content';
        },
        extractSubtitle: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.file_path === 'string') {
                return opts.tool.args.file_path;
            }
            return null;
        }
    },

    // ==================== Write 工具 ====================
    'file_write': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.file_path === 'string') {
                const path = opts.tool.args.file_path;
                const parts = path.split('/');
                return parts[parts.length - 1] || path;
            }
            return 'Write File';
        },
        icon: ICONS.edit,
        isMutable: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.file_path === 'string') {
                return `Writing: ${opts.tool.args.file_path}`;
            }
            return 'Write content to file';
        },
        extractSubtitle: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.file_path === 'string') {
                return opts.tool.args.file_path;
            }
            return null;
        }
    },

    // ==================== Edit 工具 ====================
    'file_edit': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.file_path === 'string') {
                const path = opts.tool.args.file_path;
                const parts = path.split('/');
                return parts[parts.length - 1] || path;
            }
            return 'Edit File';
        },
        icon: ICONS.edit,
        isMutable: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.file_path === 'string') {
                return `Editing: ${opts.tool.args.file_path}`;
            }
            return 'Edit file content';
        },
        extractSubtitle: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.file_path === 'string') {
                return opts.tool.args.file_path;
            }
            return null;
        }
    },

    // ==================== TodoWrite 工具 ====================
    'todo_write': {
        title: 'Task List',
        icon: ICONS.bulb,
        noStatus: true,
        minimal: (opts: { metadata: Metadata | null; tool: ToolCall; messages?: Message[] }) => {
            const todos = opts.tool.args?.todos;
            if (Array.isArray(todos) && todos.length > 0) {
                return false;
            }
            return true;
        },
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            const todos = opts.tool.args?.todos;
            if (Array.isArray(todos)) {
                const completed = todos.filter((t: any) => t.status === 'completed').length;
                return `${completed}/${todos.length} tasks completed`;
            }
            return 'Manage task list';
        },
        extractSubtitle: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            const todos = opts.tool.args?.todos;
            if (Array.isArray(todos) && todos.length > 0) {
                const pending = todos.filter((t: any) => t.status !== 'completed');
                if (pending.length > 0) {
                    return `Next: ${pending[0].content || pending[0].description}`;
                }
            }
            return null;
        }
    },

    // ==================== MCP Connect 工具 ====================
    'mcp_connect': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.serverName === 'string') {
                return `Connect: ${opts.tool.args.serverName}`;
            }
            return 'Connect to MCP Server';
        },
        icon: ICONS.globe,
        noStatus: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.serverName === 'string') {
                return `Connecting to MCP server: ${opts.tool.args.serverName}`;
            }
            return 'Connect to an MCP server';
        }
    },

    // ==================== MCP Disconnect 工具 ====================
    'mcp_disconnect': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.serverName === 'string') {
                return `Disconnect: ${opts.tool.args.serverName}`;
            }
            return 'Disconnect from MCP Server';
        },
        icon: ICONS.globe,
        noStatus: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.serverName === 'string') {
                return `Disconnecting from MCP server: ${opts.tool.args.serverName}`;
            }
            return 'Disconnect from an MCP server';
        }
    },

    // ==================== Skill Install 工具 ====================
    'skill_install': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.skillId === 'string') {
                return `Install: ${opts.tool.args.skillId}`;
            }
            return 'Install Skill';
        },
        icon: ICONS.bulb,
        noStatus: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.skillId === 'string') {
                return `Installing skill: ${opts.tool.args.skillId}`;
            }
            return 'Install a new skill';
        }
    },

    // ==================== Skill Uninstall 工具 ====================
    'skill_uninstall': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.skillId === 'string') {
                return `Remove: ${opts.tool.args.skillId}`;
            }
            return 'Remove Skill';
        },
        icon: ICONS.bulb,
        noStatus: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.skillId === 'string') {
                return `Uninstalling skill: ${opts.tool.args.skillId}`;
            }
            return 'Uninstall a skill';
        }
    },

    // ==================== Skill Search 工具 ====================
    'skill_search': {
        title: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.query === 'string') {
                return `Search: ${opts.tool.args.query}`;
            }
            return 'Search Skills';
        },
        icon: ICONS.search,
        minimal: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.query === 'string') {
                return `Searching for skills: ${opts.tool.args.query}`;
            }
            return 'Search for skills';
        }
    },

    // ==================== Reasoning/Thinking 工具 ====================
    'thinking': {
        title: 'Reasoning',
        icon: ICONS.bulb,
        hidden: true,
        minimal: true,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (typeof opts.tool.args?.title === 'string') {
                return opts.tool.args.title;
            }
            return 'Thinking process';
        }
    },

    // ==================== Question 工具 ====================
    'ask_user': {
        title: 'Question',
        icon: ICONS.question,
        noStatus: true,
        minimal: false,
        extractDescription: (opts: { metadata: Metadata | null; tool: ToolCall }) => {
            if (opts.tool.args?.questions && Array.isArray(opts.tool.args.questions)) {
                const questions = opts.tool.args.questions;
                if (questions.length > 0 && questions[0].question) {
                    return questions[0].question;
                }
            }
            return 'Asking question';
        }
    }
};

/**
 * 获取工具定义
 */
export function getToolDefinition(toolName: string): ToolDefinition | undefined {
    return knownTools[toolName];
}

/**
 * 检查工具是否可变（可能修改文件）
 */
export function isMutableTool(toolName: string): boolean {
    const tool = knownTools[toolName];
    if (tool) {
        return tool.isMutable === true;
    }
    return true; // 未知工具默认假定为可变
}

/**
 * 检查工具是否隐藏
 */
export function isToolHidden(toolName: string): boolean {
    const tool = knownTools[toolName];
    if (tool) {
        return tool.hidden === true;
    }
    return false;
}

/**
 * 检查工具是否无状态
 */
export function isToolNoStatus(toolName: string): boolean {
    const tool = knownTools[toolName];
    if (tool) {
        return tool.noStatus === true;
    }
    return false;
}
