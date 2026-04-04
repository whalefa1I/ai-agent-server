package demo.k8s.agent.plugin.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 工作区 Hook 加载器 - 从 .openclaw/hooks/ 目录加载 JavaScript Hook 脚本
 * <p>
 * 支持的 Hook 脚本格式：
 * <pre>{@code
 * // .openclaw/hooks/before-tool-call.js
 * // @hook before-tool-call
 * // @name LogToolCalls
 * // @description Log all tool calls to console
 *
 * function execute(context) {
 *     const eventData = context.getEventData();
 *     console.log("Tool called: " + eventData.toolName);
 *     return true; // 继续执行
 * }
 * }</pre>
 */
@Service
public class WorkspaceHookLoader {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceHookLoader.class);

    private static final String HOOKS_DIR = ".openclaw/hooks";

    @Value("${demo.hooks.workspaceDir:#{null}}")
    private String customWorkspaceDir;

    private final HookRegistry hookRegistry;
    private final HookExecutor hookExecutor;

    public WorkspaceHookLoader(HookRegistry hookRegistry, HookExecutor hookExecutor) {
        this.hookRegistry = hookRegistry;
        this.hookExecutor = hookExecutor;
        log.info("WorkspaceHookLoader initialized");
    }

    /**
     * 应用启动时自动加载工作区 Hook
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onLoad() {
        loadAllHooks();
    }

    /**
     * 加载所有工作区 Hook
     *
     * @return 加载的 Hook 数量
     */
    public int loadAllHooks() {
        Path hooksDir = getHooksDirectory();

        if (!Files.exists(hooksDir)) {
            log.debug("Hooks directory not found: {}, skipping", hooksDir);
            return 0;
        }

        int loadedCount = 0;
        try (var stream = Files.walk(hooksDir)) {
            List<Path> hookFiles = stream
                    .filter(p -> p.toString().endsWith(".js"))
                    .toList();

            for (Path hookFile : hookFiles) {
                try {
                    loadHookFromFile(hookFile);
                    loadedCount++;
                } catch (Exception e) {
                    log.error("Failed to load hook from {}: {}", hookFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to walk hooks directory: {}", e.getMessage(), e);
        }

        log.info("Loaded {} workspace hooks from {}", loadedCount, hooksDir);
        return loadedCount;
    }

    /**
     * 从文件加载单个 Hook
     */
    private void loadHookFromFile(Path hookFile) throws IOException, ScriptException {
        String scriptContent = Files.readString(hookFile);

        // 解析元数据注释
        HookMetadata metadata = parseMetadata(scriptContent);
        if (metadata == null) {
            log.warn("No valid hook metadata found in {}, skipping", hookFile);
            return;
        }

        // 创建 JavaScript Hook 包装器
        JsHook hook = new JsHook(
                metadata.id != null ? metadata.id : metadata.name,
                metadata.name,
                metadata.description,
                metadata.type,
                metadata.phase,
                metadata.priority,
                scriptContent
        );

        // 注册 Hook
        hookRegistry.register(hook);
    }

    /**
     * 解析 Hook 脚本的元数据注释
     */
    private HookMetadata parseMetadata(String scriptContent) {
        HookMetadata metadata = new HookMetadata();

        // 解析注释中的元数据
        // 格式：// @key value
        String[] lines = scriptContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith("// @")) {
                break; // 元数据通常在文件开头
            }

            String metaLine = line.substring(4); // 移除 "// @"
            String[] parts = metaLine.split("\\s+", 2);
            if (parts.length < 2) {
                continue;
            }

            String key = parts[0].toLowerCase();
            String value = parts[1];

            switch (key) {
                case "hook":
                    metadata.type = parseHookType(value);
                    break;
                case "name":
                    metadata.name = value;
                    break;
                case "id":
                    metadata.id = value;
                    break;
                case "description":
                    metadata.description = value;
                    break;
                case "phase":
                    metadata.phase = parseHookPhase(value);
                    break;
                case "priority":
                    try {
                        metadata.priority = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid priority value: {}", value);
                    }
                    break;
            }
        }

        // 验证必需的元数据
        if (metadata.name == null || metadata.type == null) {
            return null;
        }

        // 默认 phase 为 BEFORE
        if (metadata.phase == null) {
            metadata.phase = HookPhase.BEFORE;
        }

        return metadata;
    }

    private HookType parseHookType(String value) {
        try {
            return HookType.valueOf(value.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown hook type: {}", value);
            return null;
        }
    }

    private HookPhase parseHookPhase(String value) {
        try {
            return HookPhase.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown hook phase: {}", value);
            return null;
        }
    }

    private Path getHooksDirectory() {
        if (customWorkspaceDir != null) {
            return Paths.get(customWorkspaceDir, HOOKS_DIR);
        }
        // 默认使用当前工作目录
        return Paths.get(System.getProperty("user.dir"), HOOKS_DIR);
    }

    /**
     * Hook 元数据
     */
    private static class HookMetadata {
        String id;
        String name;
        String description = "";
        HookType type;
        HookPhase phase;
        int priority = 100;
    }

    /**
     * JavaScript Hook 实现
     */
    private static class JsHook implements Hook {

        private final String id;
        private final String name;
        private final String description;
        private final HookType type;
        private final HookPhase phase;
        private final int priority;
        private final String scriptContent;

        private final javax.script.ScriptEngine engine;
        private javax.script.CompiledScript compiledScript;

        public JsHook(String id, String name, String description, HookType type, HookPhase phase, int priority, String scriptContent) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.type = type;
            this.phase = phase;
            this.priority = priority;
            this.scriptContent = scriptContent;

            // 初始化 GraalVM JavaScript 引擎
            ScriptEngineManager manager = new ScriptEngineManager();
            this.engine = manager.getEngineByName("JavaScript");

            if (this.engine == null) {
                throw new RuntimeException("JavaScript engine not available");
            }
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public HookType getType() {
            return type;
        }

        @Override
        public HookPhase getPhase() {
            return phase;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public boolean execute(HookContext context) {
            try {
                // 将 context 绑定到脚本引擎
                engine.put("context", context);
                engine.put("console", new JsConsole());

                // 编译并执行脚本
                if (compiledScript == null) {
                    // 移除元数据注释部分
                    String scriptBody = removeMetadataComments(scriptContent);
                    javax.script.CompiledScript cs = ((javax.script.Compilable) engine).compile(scriptBody);
                    compiledScript = cs;
                }

                Object result = compiledScript.eval();

                // 对于 BEFORE Hook，返回 true 表示继续，false 表示阻止
                if (phase == HookPhase.BEFORE) {
                    return result instanceof Boolean ? (Boolean) result : true;
                }

                return true;

            } catch (ScriptException e) {
                throw new RuntimeException("Script execution failed: " + e.getMessage(), e);
            }
        }

        @Override
        public void onError(HookContext context, Throwable error) {
            log.error("Hook {} error: {}", id, error.getMessage());
        }

        private String removeMetadataComments(String content) {
            // 移除开头的元数据注释行
            String[] lines = content.split("\n");
            StringBuilder sb = new StringBuilder();
            boolean inMetadata = true;

            for (String line : lines) {
                if (inMetadata && line.trim().startsWith("// @")) {
                    continue;
                }
                if (inMetadata && !line.trim().startsWith("//")) {
                    inMetadata = false;
                }
                sb.append(line).append("\n");
            }

            return sb.toString();
        }
    }

    /**
     * JavaScript console 对象
     */
    private static class JsConsole {
        public void log(String message) {
            log.info("[JS] {}", message);
        }

        public void error(String message) {
            log.error("[JS] {}", message);
        }

        public void warn(String message) {
            log.warn("[JS] {}", message);
        }

        public void debug(String message) {
            log.debug("[JS] {}", message);
        }
    }
}
