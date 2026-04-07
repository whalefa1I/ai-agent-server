package demo.k8s.agent.skills;

import demo.k8s.agent.config.SkillsWatchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * 技能文件监听服务
 *
 * 使用 Java NIO WatchService 监听技能目录中的 SKILL.md 文件变化
 * 参考 openclaw 的 chokidar 监听机制实现
 */
@Service
@EnableConfigurationProperties(SkillsWatchProperties.class)
public class SkillsWatchService {

    private static final Logger log = LoggerFactory.getLogger(SkillsWatchService.class);

    private final SkillsWatchProperties watchProperties;
    private final SkillsSnapshotService snapshotService;
    private final SkillService skillService;

    private WatchService watchService;
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean watching = new AtomicBoolean(false);

    /**
     * 防抖任务 Map - 每个路径一个独立的防抖定时器
     */
    private final Map<String, ScheduledFuture<?>> debounceTasks = new ConcurrentHashMap<>();

    /**
     * 技能搜索目录
     */
    private static final List<String> SKILL_SEARCH_PATHS = List.of(
            System.getProperty("user.dir") + "/skills",
            System.getProperty("user.home") + "/.agents/skills",
            System.getProperty("user.home") + "/.openclaw/skills"
    );

    public SkillsWatchService(
            SkillsWatchProperties watchProperties,
            SkillsSnapshotService snapshotService,
            SkillService skillService) {
        this.watchProperties = watchProperties;
        this.snapshotService = snapshotService;
        this.skillService = skillService;
    }

    /**
     * 启动监听服务
     */
    public void startWatching() {
        if (!watchProperties.isEnabled()) {
            log.info("技能热加载监听已禁用");
            return;
        }

        if (watching.getAndSet(true)) {
            log.warn("技能监听服务已在运行");
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            log.info("技能监听服务启动，防抖延迟：{}ms", watchProperties.getDebounceMs());

            // 注册所有技能目录
            for (String basePath : SKILL_SEARCH_PATHS) {
                registerDirectory(Paths.get(basePath));
            }

            // 启动监听线程
            startWatchLoop();

        } catch (IOException e) {
            log.error("启动技能监听服务失败：{}", e.getMessage(), e);
            watching.set(false);
        }
    }

    /**
     * 注册目录监听
     */
    private void registerDirectory(Path dir) {
        // 检查目录是否存在
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try {
            // 注册 CREATE, DELETE, MODIFY 事件
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            watchKeys.put(key, dir);
            log.debug("注册技能目录监听：{}", dir);

            // 递归注册子目录（只注册一层，避免过深）
            try (Stream<Path> paths = Files.walk(dir, 2)) {
                paths.filter(Files::isDirectory)
                     .filter(path -> !path.equals(dir))
                     .filter(this::isNotIgnored)
                     .forEach(path -> {
                         try {
                             WatchKey subKey = path.register(watchService,
                                     StandardWatchEventKinds.ENTRY_CREATE,
                                     StandardWatchEventKinds.ENTRY_DELETE,
                                     StandardWatchEventKinds.ENTRY_MODIFY);
                             watchKeys.put(subKey, path);
                             log.trace("注册子目录监听：{}", path);
                         } catch (IOException e) {
                             log.trace("注册子目录失败：{} - {}", path, e.getMessage());
                         }
                     });
            }
        } catch (IOException e) {
            log.warn("注册目录监听失败：{} - {}", dir, e.getMessage());
        }
    }

    /**
     * 检查目录是否应该被忽略
     */
    private boolean isNotIgnored(Path path) {
        String name = path.getFileName().toString();

        if (watchProperties.isIgnoreGit() && name.equals(".git")) {
            return false;
        }
        if (watchProperties.isIgnoreNodeModules() && name.equals("node_modules")) {
            return false;
        }
        // 忽略隐藏目录
        if (name.startsWith(".") && !name.equals(".agents") && !name.equals(".openclaw")) {
            return false;
        }

        return true;
    }

    /**
     * 启动监听循环
     */
    private void startWatchLoop() {
        Thread watchThread = new Thread(() -> {
            try {
                while (watching.get()) {
                    WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                    if (key != null) {
                        handleWatchEvent(key);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("技能监听线程被中断");
            }
        }, "skills-watch-thread");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /**
     * 处理文件变化事件
     */
    private void handleWatchEvent(WatchKey key) {
        Path dir = watchKeys.get(key);
        if (dir == null) {
            return;
        }

        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            Path fileName = (Path) event.context();

            if (fileName == null) {
                continue;
            }

            Path fullPath = dir.resolve(fileName);

            // 检查是否是 SKILL.md 文件变化
            if (isSkillFile(fullPath, fileName.toString())) {
                scheduleReload(fullPath.toString());
            }

            // 如果是新目录，注册监听
            if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
                registerDirectory(fullPath);
            }
        }

        key.reset();
    }

    /**
     * 检查是否是技能文件
     */
    private boolean isSkillFile(Path path, String fileName) {
        if (!fileName.equals("SKILL.md")) {
            return false;
        }

        // 忽略忽略目录中的文件
        String pathStr = path.toString();
        if (watchProperties.isIgnoreGit() && pathStr.contains(".git")) {
            return false;
        }
        if (watchProperties.isIgnoreNodeModules() && pathStr.contains("node_modules")) {
            return false;
        }

        return true;
    }

    /**
     * 调度技能重载（带防抖）
     */
    private void scheduleReload(String changedPath) {
        // 取消之前的防抖任务
        ScheduledFuture<?> existingTask = debounceTasks.get(changedPath);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
        }

        // 调度新任务
        ScheduledFuture<?> newTask = scheduler.schedule(
                () -> reloadSkills(changedPath),
                watchProperties.getDebounceMs(),
                TimeUnit.MILLISECONDS
        );
        debounceTasks.put(changedPath, newTask);
    }

    /**
     * 重载技能
     */
    private void reloadSkills(String changedPath) {
        log.info("检测到技能文件变化，开始重载：{}", changedPath);

        try {
            // 清除防抖任务
            debounceTasks.remove(changedPath);

            // 更新快照版本
            snapshotService.bumpVersion(changedPath);

            // 重新加载所有技能
            skillService.loadAllSkills();

        } catch (Exception e) {
            log.error("重载技能失败：{}", changedPath, e);
        }
    }

    /**
     * 停止监听服务
     */
    public void stopWatching() {
        if (!watching.getAndSet(false)) {
            return;
        }

        // 取消所有防抖任务
        for (ScheduledFuture<?> task : debounceTasks.values()) {
            task.cancel(false);
        }
        debounceTasks.clear();

        // 关闭 WatchService
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("关闭 WatchService 失败：{}", e.getMessage());
            }
        }
        watchKeys.clear();

        // 关闭调度器
        scheduler.shutdownNow();

        log.info("技能监听服务已停止");
    }

    /**
     * 手动触发技能重载（用于 API 端点）
     */
    public void triggerReload() {
        log.info("手动触发技能重载");
        skillService.loadAllSkills();
        snapshotService.bumpVersion("manual-trigger");
    }

    /**
     * 检查监听服务是否正在运行
     */
    public boolean isWatching() {
        return watching.get();
    }
}
