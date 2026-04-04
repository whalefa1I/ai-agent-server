package demo.k8s.agent.tools.local.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 仓库操作辅助工具类。
 * <p>
 * 提供高级 Git 操作封装，用于：
 * - 检测未提交变更
 * - 生成标准 diff 格式
 * - 解析 git status 输出
 * - 获取提交历史
 */
public class GitUtil {

    private static final int DEFAULT_TIMEOUT = 10;

    /**
     * Git 状态信息
     */
    public static class GitStatus {
        public final List<String> modifiedFiles = new ArrayList<>();
        public final List<String> addedFiles = new ArrayList<>();
        public final List<String> deletedFiles = new ArrayList<>();
        public final List<String> untrackedFiles = new ArrayList<>();
        public final List<String> stagedFiles = new ArrayList<>();
        public boolean isClean = true;
        public String currentBranch = "";
        public String error = null;
    }

    /**
     * Diff 块信息
     */
    public static class DiffHunk {
        public int oldStart;
        public int oldLines;
        public int newStart;
        public int newLines;
        public String header;
        public List<String> lines = new ArrayList<>();
    }

    /**
     * 检查目录是否是 Git 仓库
     */
    public static boolean isGitRepository(Path dir) {
        Path gitDir = dir.resolve(".git");
        if (Files.exists(gitDir)) {
            return true;
        }
        Path parent = dir.getParent();
        if (parent != null && !parent.equals(dir)) {
            return isGitRepository(parent);
        }
        return false;
    }

    /**
     * 获取 Git 仓库根目录
     */
    public static Path getGitRoot(Path dir) {
        Path gitDir = dir.resolve(".git");
        if (Files.exists(gitDir)) {
            return dir.toAbsolutePath().normalize();
        }
        Path parent = dir.getParent();
        if (parent != null && !parent.equals(dir)) {
            return getGitRoot(parent);
        }
        return null;
    }

    /**
     * 获取仓库状态
     */
    public static GitStatus getStatus(Path repoDir) {
        GitStatus status = new GitStatus();

        try {
            // 获取当前分支
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
            pb.directory(repoDir.toFile());
            String branch = executeCommand(pb);
            if (branch != null) {
                status.currentBranch = branch.trim();
            }

            // 获取详细状态
            pb = new ProcessBuilder("git", "status", "--porcelain");
            pb.directory(repoDir.toFile());
            String output = executeCommand(pb);

            if (output != null && !output.isEmpty()) {
                status.isClean = false;
                parseStatusOutput(output, status);
            }

            // 检查未追踪文件
            pb = new ProcessBuilder("git", "ls-files", "--others", "--exclude-standard");
            pb.directory(repoDir.toFile());
            String untracked = executeCommand(pb);
            if (untracked != null && !untracked.isEmpty()) {
                status.isClean = false;
                for (String line : untracked.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        status.untrackedFiles.add(line.trim());
                    }
                }
            }

        } catch (Exception e) {
            status.error = e.getMessage();
        }

        return status;
    }

    /**
     * 生成文件的 diff
     */
    public static String getDiff(Path repoDir, String filePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "diff", "--no-color", "--", filePath);
            pb.directory(repoDir.toFile());
            return executeCommand(pb);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 生成暂存区的 diff
     */
    public static String getStagedDiff(Path repoDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "diff", "--cached", "--no-color");
            pb.directory(repoDir.toFile());
            return executeCommand(pb);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 解析 unified diff 格式
     */
    public static List<DiffHunk> parseDiff(String diffOutput) {
        List<DiffHunk> hunks = new ArrayList<>();
        if (diffOutput == null || diffOutput.isEmpty()) {
            return hunks;
        }

        Pattern hunkPattern = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$");
        String[] lines = diffOutput.split("\n");

        DiffHunk currentHunk = null;

        for (String line : lines) {
            if (line.startsWith("@@")) {
                Matcher matcher = hunkPattern.matcher(line);
                if (matcher.matches()) {
                    currentHunk = new DiffHunk();
                    currentHunk.header = line;
                    currentHunk.oldStart = Integer.parseInt(matcher.group(1));
                    currentHunk.oldLines = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
                    currentHunk.newStart = Integer.parseInt(matcher.group(3));
                    currentHunk.newLines = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 1;
                    hunks.add(currentHunk);
                }
            } else if (currentHunk != null) {
                currentHunk.lines.add(line);
            }
        }

        return hunks;
    }

    /**
     * 获取提交历史
     */
    public static List<String> getLog(Path repoDir, int maxCommits) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "log", "--oneline", "-n", String.valueOf(maxCommits));
            pb.directory(repoDir.toFile());
            String output = executeCommand(pb);
            if (output != null) {
                List<String> commits = new ArrayList<>();
                for (String line : output.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        commits.add(line.trim());
                    }
                }
                return commits;
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return new ArrayList<>();
    }

    /**
     * 获取文件的 blame 信息
     */
    public static String getBlame(Path repoDir, String filePath, int lineNumber) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            cmd.add("blame");
            cmd.add("-L");
            cmd.add(lineNumber + "," + lineNumber);
            cmd.add("--");
            cmd.add(filePath);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(repoDir.toFile());
            return executeCommand(pb);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 检查文件是否有未提交的变更
     */
    public static boolean hasUncommittedChanges(Path repoDir, String filePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "diff", "--quiet", "--", filePath);
            pb.directory(repoDir.toFile());
            Process process = pb.start();
            process.waitFor(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            return process.exitValue() != 0; // exit code 1 表示有变更
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 执行命令并返回输出
     */
    private static String executeCommand(ProcessBuilder pb) throws IOException, InterruptedException {
        Process process = pb.start();
        boolean completed = process.waitFor(DEFAULT_TIMEOUT, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new IOException("Command timed out");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
    }

    /**
     * 解析 git status --porcelain 输出
     */
    private static void parseStatusOutput(String output, GitStatus status) {
        for (String line : output.split("\n")) {
            if (line.trim().isEmpty()) continue;

            if (line.length() < 3) continue;

            char staged = line.charAt(0);
            char unstaged = line.charAt(1);
            String filePath = line.substring(3).trim();

            // 处理重命名文件
            if (line.contains(" -> ")) {
                String[] parts = filePath.split(" -> ");
                filePath = parts[1].trim();
            }

            if (staged == 'A') {
                status.addedFiles.add(filePath);
                status.stagedFiles.add(filePath);
            } else if (staged == 'M' || staged == 'D') {
                status.modifiedFiles.add(filePath);
                status.stagedFiles.add(filePath);
            }

            if (unstaged == 'M') {
                if (!status.modifiedFiles.contains(filePath)) {
                    status.modifiedFiles.add(filePath);
                }
            } else if (unstaged == 'D') {
                status.deletedFiles.add(filePath);
            }
        }
    }
}
