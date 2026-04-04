package demo.k8s.agent.tools.local.git;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitUtil 工具类单元测试
 */
@DisplayName("GitUtil 测试")
class GitUtilTest {

    @TempDir
    Path tempDir;

    Path gitRepo;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        gitRepo = tempDir.resolve("test-repo");
        Files.createDirectory(gitRepo);

        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(gitRepo.toFile());
        Process process = pb.start();
        process.waitFor();

        pb = new ProcessBuilder("git", "config", "user.email", "test@example.com");
        pb.directory(gitRepo.toFile());
        process = pb.start();
        process.waitFor();

        pb = new ProcessBuilder("git", "config", "user.name", "Test User");
        pb.directory(gitRepo.toFile());
        process = pb.start();
        process.waitFor();
    }

    @Test
    @DisplayName("检测 Git 仓库")
    void testIsGitRepository() {
        assertTrue(GitUtil.isGitRepository(gitRepo));
        assertFalse(GitUtil.isGitRepository(tempDir));
    }

    @Test
    @DisplayName("获取 Git 根目录")
    void testGetGitRoot() {
        Path root = GitUtil.getGitRoot(gitRepo);
        assertNotNull(root);
        assertEquals(gitRepo.toAbsolutePath().normalize(), root);

        Path subdir = gitRepo.resolve("subdir");
        try {
            Files.createDirectory(subdir);
            Path rootFromSubdir = GitUtil.getGitRoot(subdir);
            assertNotNull(rootFromSubdir);
            assertEquals(gitRepo.toAbsolutePath().normalize(), rootFromSubdir);
        } catch (IOException e) {
            fail("Failed to create subdir");
        }
    }

    @Test
    @DisplayName("获取空仓库状态")
    void testGetStatusEmptyRepo() {
        GitUtil.GitStatus status = GitUtil.getStatus(gitRepo);
        assertNull(status.error, "Should not have error: " + status.error);
        assertTrue(status.isClean);
        assertTrue(status.modifiedFiles.isEmpty());
    }

    @Test
    @DisplayName("检测修改的文件")
    void testGetStatusWithModifiedFile() throws IOException, InterruptedException {
        // 创建并提交文件
        Path testFile = gitRepo.resolve("test.txt");
        Files.writeString(testFile, "Original content");

        ProcessBuilder pb = new ProcessBuilder("git", "add", "test.txt");
        pb.directory(gitRepo.toFile());
        Process process = pb.start();
        process.waitFor();

        pb = new ProcessBuilder("git", "commit", "-m", "Initial commit");
        pb.directory(gitRepo.toFile());
        process = pb.start();
        process.waitFor();

        // 修改文件
        Files.writeString(testFile, "Modified content");

        GitUtil.GitStatus status = GitUtil.getStatus(gitRepo);
        assertFalse(status.isClean);
        assertTrue(status.modifiedFiles.contains("test.txt"));
    }

    @Test
    @DisplayName("生成 Diff")
    void testGetDiff() throws IOException, InterruptedException {
        Path testFile = gitRepo.resolve("test.txt");
        Files.writeString(testFile, "Line 1\nLine 2\nLine 3\n");

        ProcessBuilder pb = new ProcessBuilder("git", "add", "test.txt");
        pb.directory(gitRepo.toFile());
        Process process = pb.start();
        process.waitFor();

        pb = new ProcessBuilder("git", "commit", "-m", "Initial commit");
        pb.directory(gitRepo.toFile());
        process = pb.start();
        process.waitFor();

        // 修改文件
        Files.writeString(testFile, "Line 1\nModified\nLine 3\n");

        String diff = GitUtil.getDiff(gitRepo, "test.txt");
        assertNotNull(diff);
        assertTrue(diff.contains("-Line 2"));
        assertTrue(diff.contains("+Modified"));
    }

    @Test
    @DisplayName("解析 Diff Hunk")
    void testParseDiff() {
        String diffOutput = """
            diff --git a/test.txt b/test.txt
            index abc123..def456 100644
            --- a/test.txt
            +++ b/test.txt
            @@ -1,4 +1,4 @@
             Line 1
            -Line 2
            +Modified Line 2
             Line 3
             Line 4
            """;

        List<GitUtil.DiffHunk> hunks = GitUtil.parseDiff(diffOutput);

        assertEquals(1, hunks.size());
        GitUtil.DiffHunk hunk = hunks.get(0);
        assertEquals(1, hunk.oldStart);
        assertEquals(4, hunk.oldLines);
        assertEquals(1, hunk.newStart);
        assertEquals(4, hunk.newLines);
        // 5 行：1 行 context + 1 removed + 1 added + 2 context
        assertEquals(5, hunk.lines.size());
    }

    @Test
    @DisplayName("获取提交历史")
    void testGetLog() throws IOException, InterruptedException {
        Path testFile = gitRepo.resolve("test.txt");
        Files.writeString(testFile, "Content");

        ProcessBuilder pb = new ProcessBuilder("git", "add", "test.txt");
        pb.directory(gitRepo.toFile());
        Process process = pb.start();
        process.waitFor();

        pb = new ProcessBuilder("git", "commit", "-m", "Initial commit");
        pb.directory(gitRepo.toFile());
        process = pb.start();
        process.waitFor();

        List<String> commits = GitUtil.getLog(gitRepo, 10);
        assertFalse(commits.isEmpty());
        assertTrue(commits.get(0).contains("Initial commit"));
    }

    @Test
    @DisplayName("检查未提交变更")
    void testHasUncommittedChanges() throws IOException, InterruptedException {
        Path testFile = gitRepo.resolve("test.txt");
        Files.writeString(testFile, "Original");

        ProcessBuilder pb = new ProcessBuilder("git", "add", "test.txt");
        pb.directory(gitRepo.toFile());
        Process process = pb.start();
        process.waitFor();

        pb = new ProcessBuilder("git", "commit", "-m", "Initial commit");
        pb.directory(gitRepo.toFile());
        process = pb.start();
        process.waitFor();

        // 初始无变更
        assertFalse(GitUtil.hasUncommittedChanges(gitRepo, "test.txt"));

        // 修改后有变更
        Files.writeString(testFile, "Modified");
        assertTrue(GitUtil.hasUncommittedChanges(gitRepo, "test.txt"));
    }
}
