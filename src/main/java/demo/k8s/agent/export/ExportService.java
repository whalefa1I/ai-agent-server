package demo.k8s.agent.export;

import demo.k8s.agent.export.exporters.AlpacaExporter;
import demo.k8s.agent.export.exporters.ChatMLExporter;
import demo.k8s.agent.export.exporters.ShareGPTExporter;
import demo.k8s.agent.export.privacy.DataAnonymizer;
import demo.k8s.agent.observability.events.Event;
import demo.k8s.agent.observability.events.EventBus;
import demo.k8s.agent.state.ChatMessage;
import demo.k8s.agent.state.ConversationRepository;
import demo.k8s.agent.state.ConversationSession;
import demo.k8s.agent.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据导出服务
 * <p>
 * 支持将对话数据导出为多种训练数据格式（JSONL、ShareGPT、ChatML、Alpaca）
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final EventBus eventBus;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final DataAnonymizer dataAnonymizer;

    private final Map<String, ExportJob> runningJobs = new ConcurrentHashMap<>();

    public ExportService(EventBus eventBus,
                         ConversationRepository conversationRepository,
                         UserRepository userRepository,
                         DataAnonymizer dataAnonymizer) {
        this.eventBus = eventBus;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.dataAnonymizer = dataAnonymizer;
    }

    /**
     * 创建导出任务
     */
    public ExportJob createExportJob(ExportRequest request) {
        String jobId = "export_" + UUID.randomUUID().toString().substring(0, 8);

        ExportJob job = ExportJob.builder()
                .id(jobId)
                .request(request)
                .status(ExportStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        runningJobs.put(jobId, job);
        log.info("创建导出任务：{}", jobId);

        // 异步执行导出
        CompletableFuture.runAsync(() -> executeExport(job))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        job.setStatus(ExportStatus.FAILED);
                        job.setError(error.getMessage());
                        log.error("导出任务失败：{}", jobId, error);
                    }
                });

        return job;
    }

    /**
     * 获取导出任务状态
     */
    public Optional<ExportJob> getExportJob(String jobId) {
        return Optional.ofNullable(runningJobs.get(jobId));
    }

    /**
     * 列出所有导出任务
     */
    public List<ExportJob> listExportJobs() {
        return new ArrayList<>(runningJobs.values());
    }

    /**
     * 取消导出任务
     */
    public boolean cancelExportJob(String jobId) {
        ExportJob job = runningJobs.get(jobId);
        if (job != null && job.getStatus() == ExportStatus.RUNNING) {
            job.setStatus(ExportStatus.CANCELLED);
            log.info("取消导出任务：{}", jobId);
            return true;
        }
        return false;
    }

    /**
     * 删除导出任务
     */
    public boolean deleteExportJob(String jobId) {
        ExportJob job = runningJobs.remove(jobId);
        if (job != null) {
            log.info("删除导出任务：{}", jobId);
            return true;
        }
        return false;
    }

    /**
     * 执行导出任务
     */
    private void executeExport(ExportJob job) {
        job.setStatus(ExportStatus.RUNNING);
        job.setStartedAt(Instant.now());

        try {
            ExportRequest request = job.getRequest();
            ExportFormat format = request.format();

            // 收集对话数据
            List<ConversationData> conversations = collectConversations(request);
            log.info("收集到 {} 个对话用于导出", conversations.size());

            // 数据脱敏
            if (request.anonymize()) {
                conversations = anonymizeConversations(conversations);
            }

            // 创建输出目录
            Path outputDir = createOutputDirectory(request.outputDir(), format);

            // 根据格式导出
            Path outputFile = switch (format) {
                case ALPACA -> new AlpacaExporter().export(conversations, outputDir);
                case SHAREGPT -> new ShareGPTExporter().export(conversations, outputDir);
                case CHATML -> new ChatMLExporter().export(conversations, outputDir);
                case JSONL -> new AlpacaExporter().exportJsonl(conversations, outputDir);
            };

            // 更新任务状态
            job.setStatus(ExportStatus.COMPLETED);
            job.setOutputFile(outputFile.toString());
            job.setCompletedAt(Instant.now());
            job.setRecordCount(conversations.size());

            log.info("导出任务完成：{}, 输出文件：{}, 记录数：{}",
                    job.getId(), outputFile, conversations.size());

        } catch (Exception e) {
            job.setStatus(ExportStatus.FAILED);
            job.setError(e.getMessage());
            log.error("导出任务失败：{}", job.getId(), e);
            // 不重新抛出异常，因为 job 已经在异步线程中执行
        }
    }

    /**
     * 收集对话数据
     */
    private List<ConversationData> collectConversations(ExportRequest request) {
        List<ConversationData> conversations = new ArrayList<>();

        // 根据条件查询
        if (request.sessionId() != null) {
            // 按 Session ID 导出
            ConversationSession session = conversationRepository.loadSession(request.sessionId())
                    .orElse(null);
            if (session != null) {
                conversations.add(convertToConversationData(session));
            }
        } else {
            // 按时间范围导出（简化版：只导出当前 session）
            // TODO: 实现完整的用户/时间范围查询
            List<String> sessionIds = conversationRepository.listSessions();
            for (String sessionId : sessionIds) {
                ConversationSession session = conversationRepository.loadSession(sessionId).orElse(null);
                if (session != null && isWithinTimeRange(session, request.startTime(), request.endTime())) {
                    conversations.add(convertToConversationData(session));
                }
            }
        }

        return conversations;
    }

    /**
     * 检查会话是否在时间范围内
     */
    private boolean isWithinTimeRange(ConversationSession session, Instant startTime, Instant endTime) {
        Instant createdAt = session.getMetadata("createdAt");
        if (createdAt == null) {
            createdAt = session.getCreatedAt();
        }

        if (startTime != null && createdAt.isBefore(startTime)) {
            return false;
        }
        if (endTime != null && createdAt.isAfter(endTime)) {
            return false;
        }
        return true;
    }

    /**
     * 转换 ConversationSession 为 ConversationData
     */
    private ConversationData convertToConversationData(ConversationSession session) {
        List<MessageData> messages = new ArrayList<>();

        for (ChatMessage msg : session.getMessages()) {
            MessageData.MessageDataBuilder builder = MessageData.builder()
                    .role(msg.type().name().toLowerCase())
                    .content(msg.content() != null ? msg.content() : "")
                    .timestamp(Instant.now()); // TODO: 从消息元数据获取

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                builder.toolCalls(msg.toolCalls());
            }

            messages.add(builder.build());
        }

        return ConversationData.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .messages(messages)
                .createdAt(session.getCreatedAt())
                .metadata(session.getAllMetadata())
                .build();
    }

    /**
     * 数据脱敏
     */
    private List<ConversationData> anonymizeConversations(List<ConversationData> conversations) {
        return conversations.stream()
                .map(dataAnonymizer::anonymize)
                .toList();
    }

    /**
     * 创建输出目录
     */
    private Path createOutputDirectory(String customDir, ExportFormat format) throws IOException {
        Path baseDir;
        if (customDir != null && !customDir.isBlank()) {
            baseDir = Path.of(customDir);
        } else {
            baseDir = Path.of("exports");
        }

        Files.createDirectories(baseDir);
        return baseDir;
    }
}
