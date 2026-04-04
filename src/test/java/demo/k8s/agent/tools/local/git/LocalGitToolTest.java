package demo.k8s.agent.tools.local.git;

import demo.k8s.agent.tools.local.LocalToolResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalGitTool 单元测试
 */
@DisplayName("LocalGitTool 测试")
class LocalGitToolTest {

    @TempDir
    Path tempDir;

    Path gitRepo;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        // 创建一个临时的 git 仓库
        gitRepo = tempDir.resolve("test-repo");
        Files.createDirectory(gitRepo);

        // 初始化 git 仓库
        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(gitRepo.toFile());
        Process process = pb.start();
        process.waitFor();

        // 配置 git 用户（如果没有全局配置）
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
    @DisplayName("执行 git status 命令")
    void testGitStatus() {
        Map<String, Object> input = Map.of(
                "command", "status",
                "cwd", gitRepo.toString()
        );

        LocalToolResult result = LocalGitTool.execute(input);

        assertTrue(result.isSuccess(), "Should succeed: " + result.getError());
        assertNotNull(result.getContent());
    }

    @Test
    @DisplayName("执行 git diff 命令")
    void testGitDiff() throws IOException, InterruptedException {
        // 创建并提交一个文件
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
        Files.writeString(testFile, "Line 1\nModified Line 2\nLine 3\n");

        Map<String, Object> input = Map.of(
                "command", "diff",
                "cwd", gitRepo.toString()
        );

        LocalToolResult result = LocalGitTool.execute(input);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertTrue(content.contains("-Line 2"));
        assertTrue(content.contains("+Modified Line 2"));
    }

    @Test
    @DisplayName("执行 git log 命令")
    void testGitLog() throws IOException, InterruptedException {
        // 创建并提交一个文件
        Path testFile = gitRepo.resolve("test.txt");
        Files.writeString(testFile, "Content");

        ProcessBuilder pb = new ProcessBuilder("git", "add", "test.txt");
        pb.directory(gitRepo.toFile());
        Process process = pb.start();
        process.waitFor();

        pb = new ProcessBuilder("git", "commit", "-m", "Initial commit");
        pb.directory(gitRepo.toFile());
        Process commitProcess = pb.start();
        commitProcess.waitFor();

        Map<String, Object> input = Map.of(
                "command", "log",
                "args", List.of("-n", "5"),
                "cwd", gitRepo.toString()
        );

        LocalToolResult result = LocalGitTool.execute(input);

        assertTrue(result.isSuccess());
        String content = result.getContent();
        assertTrue(content.contains("Initial commit"));
    }

    @Test
    @DisplayName("不允许的命令")
    void testDisallowedCommand() {
        Map<String, Object> input = Map.of(
                "command", "rm -rf /",
                "cwd", gitRepo.toString()
        );

        LocalToolResult result = LocalGitTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not allowed"));
    }

    @Test
    @DisplayName("非 git 目录失败")
    void testNonGitDirectory() {
        Path nonGitDir = tempDir.resolve("not-a-repo");
        try {
            Files.createDirectory(nonGitDir);
        } catch (IOException e) {
            fail("Failed to create test directory");
        }

        Map<String, Object> input = Map.of(
                "command", "status",
                "cwd", nonGitDir.toString()
        );

        LocalToolResult result = LocalGitTool.execute(input);

        // 在非 git 目录中，git status 会返回错误，但不一定是 "not a git repository"
        // 可能是其他错误信息，所以我们只要验证命令失败了即可
        assertFalse(result.isSuccess(), "Should fail in non-git directory: " + result.getContent());
    }

    @Test
    @DisplayName("缺少命令参数")
    void testMissingCommand() {
        Map<String, Object> input = Map.of(
                "cwd", gitRepo.toString()
        );

        LocalToolResult result = LocalGitTool.execute(input);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("command is required"));
    }
}
