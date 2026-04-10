package demo.k8s.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 调试开关：模型请求内容仅在服务端可见，用于区分提示词问题与工程/工具定义问题。
 */
@ConfigurationProperties(prefix = "demo.debug")
public class DemoDebugProperties {

    /**
     * 为 true 时，在每次 {@link demo.k8s.agent.query.EnhancedAgenticQueryLoop} /
     * {@link demo.k8s.agent.query.AgenticQueryLoop} 调用模型前打 INFO 日志：系统提示长度与预览、工具名与 inputSchema 预览、消息列表预览。
     */
    private boolean logModelRequest = false;

    /** 系统提示与消息正文预览最大字符数 */
    private int modelRequestPreviewChars = 500;

    /** 每个工具 JSON Schema 预览最大字符数 */
    private int modelRequestToolSchemaMaxChars = 1200;

    /**
     * 为 true 时，应用启动后统一打印 {@link System#getenv()} 全部键值（用于核对 Railway 等环境变量是否注入）。
     * 键名命中常见敏感模式时仅打印是否为空与长度，不打印明文；非敏感键打印完整值（单条值过长会截断）。
     */
    private boolean logEnvironmentAtStartup = false;

    /** 非敏感环境变量值在日志中的最大字符数，避免单条撑爆日志后端 */
    private int logEnvironmentValueMaxChars = 2048;

    public boolean isLogEnvironmentAtStartup() {
        return logEnvironmentAtStartup;
    }

    public void setLogEnvironmentAtStartup(boolean logEnvironmentAtStartup) {
        this.logEnvironmentAtStartup = logEnvironmentAtStartup;
    }

    public int getLogEnvironmentValueMaxChars() {
        return logEnvironmentValueMaxChars;
    }

    public void setLogEnvironmentValueMaxChars(int logEnvironmentValueMaxChars) {
        this.logEnvironmentValueMaxChars = logEnvironmentValueMaxChars;
    }

    public boolean isLogModelRequest() {
        return logModelRequest;
    }

    public void setLogModelRequest(boolean logModelRequest) {
        this.logModelRequest = logModelRequest;
    }

    public int getModelRequestPreviewChars() {
        return modelRequestPreviewChars;
    }

    public void setModelRequestPreviewChars(int modelRequestPreviewChars) {
        this.modelRequestPreviewChars = modelRequestPreviewChars;
    }

    public int getModelRequestToolSchemaMaxChars() {
        return modelRequestToolSchemaMaxChars;
    }

    public void setModelRequestToolSchemaMaxChars(int modelRequestToolSchemaMaxChars) {
        this.modelRequestToolSchemaMaxChars = modelRequestToolSchemaMaxChars;
    }
}
