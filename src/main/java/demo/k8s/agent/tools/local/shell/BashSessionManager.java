package demo.k8s.agent.tools.local.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 后台 Shell 会话管理器。
 * <p>
 * 支持：
 * - 后台执行长时间运行的命令
 * - 流式输出读取
 * - 会话管理（启动/停止/查询）
 */
public class BashSessionManager {

    private static final ConcurrentMap<String, BashSession> sessions = new ConcurrentHashMap<>();
    private static final int MAX_SESSIONS = 10;
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 分钟

    /**
     * Bash 会话信息
     */
    public static class BashSession {
        public final String sessionId;
        public final String command;
        public final Process process;
        public final long startTime;
        public volatile StringBuilder output = new StringBuilder();
        public volatile boolean completed = false;
        public volatile Integer exitCode = null;
        public volatile String error = null;
        public volatile long lastAccessTime;

        public BashSession(String sessionId, String command, Process process) {
            this.sessionId = sessionId;
            this.command = command;
            this.process = process;
            this.startTime = System.currentTimeMillis();
            this.lastAccessTime = this.startTime;
        }

        public boolean isRunning() {
            return !completed && process.isAlive();
        }

        public void updateLastAccess() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    /**
     * 启动后台会话
     */
    public static String startSession(String command, String workdir, int timeoutMs) throws IOException {
        // 清理过期会话
        cleanupExpiredSessions();

        if (sessions.size() >= MAX_SESSIONS) {
            throw new IOException("Maximum number of sessions reached");
        }

        String sessionId = UUID.randomUUID().toString().substring(0, 8);

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectErrorStream(true);
        if (workdir != null && !workdir.isEmpty()) {
            pb.directory(new java.io.File(workdir));
        }

        Map<String, String> env = pb.environment();
        env.put("LC_ALL", "en_US.UTF-8");

        Process process = pb.start();

        BashSession session = new BashSession(sessionId, command, process);
        sessions.put(sessionId, session);

        // 启动输出读取线程
        CompletableFuture.runAsync(() -> readOutput(session, timeoutMs));

        return sessionId;
    }

    /**
     * 获取会话状态
     */
    public static BashSession getSession(String sessionId) {
        BashSession session = sessions.get(sessionId);
        if (session != null) {
            session.updateLastAccess();
        }
        return session;
    }

    /**
     * 获取会话输出
     */
    public static String getSessionOutput(String sessionId) {
        BashSession session = getSession(sessionId);
        if (session == null) {
            return null;
        }
        return session.output.toString();
    }

    /**
     * 停止会话
     */
    public static boolean stopSession(String sessionId) {
        BashSession session = sessions.remove(sessionId);
        if (session != null) {
            session.process.destroyForcibly();
            session.completed = true;
            return true;
        }
        return false;
    }

    /**
     * 等待会话完成
     */
    public static boolean waitForSession(String sessionId, long timeoutMs) {
        BashSession session = getSession(sessionId);
        if (session == null) {
            return false;
        }

        try {
            boolean completed = session.process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (completed) {
                session.completed = true;
                session.exitCode = session.process.exitValue();
            }
            return completed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 清理过期会话
     */
    private static void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            BashSession session = entry.getValue();
            return (now - session.lastAccessTime) > SESSION_TIMEOUT_MS;
        });
    }

    /**
     * 读取输出
     */
    private static void readOutput(BashSession session, int timeoutMs) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(session.process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            long elapsed = 0;
            long checkInterval = 100;

            while ((line = reader.readLine()) != null) {
                session.output.append(line).append("\n");

                // 检查超时
                elapsed += checkInterval;
                if (elapsed >= timeoutMs) {
                    session.error = "Output reading timed out";
                    session.process.destroyForcibly();
                    break;
                }
            }

            // 等待进程结束
            if (!session.process.waitFor(timeoutMs - elapsed, TimeUnit.MILLISECONDS)) {
                session.process.destroyForcibly();
                session.error = "Process timed out";
            }

            session.completed = true;
            session.exitCode = session.process.exitValue();

        } catch (IOException e) {
            session.error = "IO error: " + e.getMessage();
            session.completed = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            session.error = "Interrupted";
            session.completed = true;
        } catch (Exception e) {
            session.error = "Error: " + e.getMessage();
            session.completed = true;
        }
    }

    /**
     * 获取活跃会话数
     */
    public static int getActiveSessionCount() {
        cleanupExpiredSessions();
        return sessions.size();
    }

    /**
     * 获取所有会话 ID
     */
    public static java.util.Set<String> getAllSessionIds() {
        return sessions.keySet();
    }
}
