package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多 Agent 子系统配置（v1 M3 实现）。
 * <p>
 * 控制多 Agent 编排的开关、模式和门控参数。
 */
@ConfigurationProperties(prefix = "demo.multi-agent")
public class DemoMultiAgentProperties {

    /**
     * 多 Agent 子系统总开关；关闭时不装配子系统 Bean、不注册 spawn 类工具。
     */
    private boolean enabled = false;

    /**
     * 运行模式：
     * - off: 完全禁用，所有请求走 legacy 单 Agent 路径
     * - shadow: 影子模式，执行子 Agent 但不污染主对话，仅用于评估和统计
     * - on: 正式启用，子 Agent 结果注入主对话
     */
    private Mode mode = Mode.off;

    /**
     * 最大派生深度（防止无限递归派生）
     */
    private int maxSpawnDepth = 3;

    /**
     * 每个 Agent 允许的最大并发子 Agent 数量
     */
    private int maxConcurrentSpawns = 5;

    /**
     * Wall-Clock TTL（秒），子任务超时时间
     */
    private int wallclockTtlSeconds = 180;

    /**
     * 默认租户 ID（开发环境兜底）
     */
    private String defaultTenantId = "default";

    /**
     * 子 Agent Worker 是否暴露 TaskCreate/TaskList 等「会话级 Task」工具。
     * <p>
     * 默认 {@code false}：Worker 仅使用文件/Shell 等工具，避免子 Agent 内再嵌套 Task 与主会话 Task 混淆；
     * 若需实验「子 Agent 也建 Task」可设为 {@code true}（仍受门控深度/并发约束）。
     */
    private boolean workerExposeTaskTools = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public int getMaxSpawnDepth() {
        return maxSpawnDepth;
    }

    public void setMaxSpawnDepth(int maxSpawnDepth) {
        this.maxSpawnDepth = maxSpawnDepth;
    }

    public int getMaxConcurrentSpawns() {
        return maxConcurrentSpawns;
    }

    public void setMaxConcurrentSpawns(int maxConcurrentSpawns) {
        this.maxConcurrentSpawns = maxConcurrentSpawns;
    }

    public int getWallclockTtlSeconds() {
        return wallclockTtlSeconds;
    }

    public void setWallclockTtlSeconds(int wallclockTtlSeconds) {
        this.wallclockTtlSeconds = wallclockTtlSeconds;
    }

    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    public boolean isWorkerExposeTaskTools() {
        return workerExposeTaskTools;
    }

    public void setWorkerExposeTaskTools(boolean workerExposeTaskTools) {
        this.workerExposeTaskTools = workerExposeTaskTools;
    }

    public enum Mode {
        off,
        shadow,
        on
    }
}
