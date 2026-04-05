package demo.k8s.agent.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件整理技能执行器
 *
 * 按文件类型自动分类整理文件
 * 基于 SKILL.md 中定义的文件类型映射
 */
public class FileOrganizerSkillExecutor implements SkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(FileOrganizerSkillExecutor.class);

    // 文件扩展名映射
    private static final Map<String, String> FILE_TYPE_MAP = Map.ofEntries(
        // 图片
        Map.entry("jpg", "images"),
        Map.entry("jpeg", "images"),
        Map.entry("png", "images"),
        Map.entry("gif", "images"),
        Map.entry("bmp", "images"),
        Map.entry("webp", "images"),
        Map.entry("svg", "images"),
        // 文档
        Map.entry("doc", "documents"),
        Map.entry("docx", "documents"),
        Map.entry("pdf", "documents"),
        Map.entry("txt", "documents"),
        Map.entry("md", "documents"),
        Map.entry("xls", "documents"),
        Map.entry("xlsx", "documents"),
        Map.entry("ppt", "documents"),
        Map.entry("pptx", "documents"),
        // 代码
        Map.entry("js", "code"),
        Map.entry("ts", "code"),
        Map.entry("py", "code"),
        Map.entry("java", "code"),
        Map.entry("cpp", "code"),
        Map.entry("c", "code"),
        Map.entry("html", "code"),
        Map.entry("css", "code"),
        Map.entry("json", "code"),
        // 视频
        Map.entry("mp4", "videos"),
        Map.entry("avi", "videos"),
        Map.entry("mov", "videos"),
        Map.entry("wmv", "videos"),
        Map.entry("flv", "videos"),
        // 音频
        Map.entry("mp3", "audio"),
        Map.entry("wav", "audio"),
        Map.entry("flac", "audio"),
        Map.entry("aac", "audio"),
        Map.entry("ogg", "audio"),
        // 压缩包
        Map.entry("zip", "archives"),
        Map.entry("rar", "archives"),
        Map.entry("7z", "archives"),
        Map.entry("tar", "archives"),
        Map.entry("gz", "archives")
    );

    @Override
    public String getSkillName() {
        return "file-organizer-zh";
    }

    @Override
    public String execute(Map<String, Object> args) {
        String directory = (String) args.get("directory");
        String action = (String) args.get("action");
        Boolean dryRun = (Boolean) args.get("dryRun");

        if (directory == null) {
            return "错误：缺少 directory 参数";
        }

        if (action == null) {
            action = "organize"; // 默认动作
        }

        if (dryRun == null) {
            dryRun = true; // 默认干运行，不实际移动
        }

        try {
            return switch (action) {
                case "organize" -> organizeFiles(directory, dryRun);
                case "scan" -> scanDirectory(directory);
                case "classify" -> classifyFile(directory);
                default -> "错误：未知的 action: " + action;
            };
        } catch (Exception e) {
            log.error("文件整理失败", e);
            return "执行失败：" + e.getMessage();
        }
    }

    /**
     * 整理文件
     */
    private String organizeFiles(String directory, boolean dryRun) throws IOException {
        Path dir = Paths.get(directory);
        if (!Files.exists(dir)) {
            return "错误：目录不存在：" + directory;
        }

        StringBuilder result = new StringBuilder();
        result.append(dryRun ? "=== 预扫描模式（不会实际移动文件）===\n\n" : "=== 开始整理文件 ===\n\n");

        Map<String, Integer> stats = new HashMap<>();

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String ext = getFileExtension(file);
                String type = FILE_TYPE_MAP.get(ext.toLowerCase());

                if (type != null) {
                    Path targetDir = dir.resolve(type);
                    Path targetFile = targetDir.resolve(file.getFileName());

                    // 跳过已经在目标目录的文件
                    if (file.getParent().equals(targetDir)) {
                        return FileVisitResult.CONTINUE;
                    }

                    stats.put(type, stats.getOrDefault(type, 0) + 1);

                    if (!dryRun) {
                        try {
                            if (!Files.exists(targetDir)) {
                                Files.createDirectories(targetDir);
                            }
                            Files.move(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                            result.append("移动：").append(file.getFileName())
                                  .append(" -> ").append(type).append("/\n");
                        } catch (IOException e) {
                            result.append("失败：").append(file.getFileName())
                                  .append(" - ").append(e.getMessage()).append("\n");
                        }
                    } else {
                        result.append("计划移动：").append(file.getFileName())
                              .append(" -> ").append(type).append("/\n");
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });

        result.append("\n=== 统计 ===\n");
        stats.forEach((type, count) ->
            result.append(type).append(": ").append(count).append(" 个文件\n")
        );

        if (dryRun && !stats.isEmpty()) {
            result.append("\n提示：设置 dryRun=false 以实际移动文件");
        }

        return result.toString();
    }

    /**
     * 扫描目录
     */
    private String scanDirectory(String directory) throws IOException {
        Path dir = Paths.get(directory);
        if (!Files.exists(dir)) {
            return "错误：目录不存在：" + directory;
        }

        Map<String, Integer> typeCount = new HashMap<>();
        int[] totalFiles = {0}; // 使用数组以便在 lambda 中修改

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String ext = getFileExtension(file);
                String type = FILE_TYPE_MAP.getOrDefault(ext.toLowerCase(), "other");
                typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
                totalFiles[0]++;
                return FileVisitResult.CONTINUE;
            }
        });

        StringBuilder result = new StringBuilder();
        result.append("目录扫描结果：").append(directory).append("\n\n");
        result.append("总文件数：").append(totalFiles[0]).append("\n\n");
        result.append("按类型分布:\n");

        typeCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry ->
                result.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n")
            );

        return result.toString();
    }

    /**
     * 分类单个文件
     */
    private String classifyFile(String filePath) {
        Path file = Paths.get(filePath);
        String ext = getFileExtension(file);
        String type = FILE_TYPE_MAP.get(ext.toLowerCase());

        if (type == null) {
            return "无法分类：." + ext + " 扩展名";
        }

        return "文件 " + file.getFileName() + " 属于类型：" + type;
    }

    private String getFileExtension(Path file) {
        String fileName = file.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }
}
