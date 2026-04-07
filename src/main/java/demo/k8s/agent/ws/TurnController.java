package demo.k8s.agent.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import demo.k8s.agent.state.ChatMessage;
import demo.k8s.agent.state.ConversationManager;
import demo.k8s.agent.state.ConversationSession;
import demo.k8s.agent.state.MessageType;
import demo.k8s.agent.ws.protocol.WsProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Turn（回合）管理 HTTP API 控制器
 * <p>
 * 与 Happy Protocol 对齐，提供会话回合的创建、查询和历史 retrieval 功能。
 * <p>
 * 数据结构参考 Claude Code 的 Turn 管理：
 * - 每个用户消息发起一个新的 Turn
 * - Turn 包含该回合内所有的工具调用和响应
 * - Turn 有唯一 ID、开始时间、结束时间、状态等元数据
 */
@RestController
@RequestMapping("/api/v1/turns")
@CrossOrigin(origins = "*")
public class TurnController {

    private static final Logger log = LoggerFactory.getLogger(TurnController.class);

    private final ConversationManager conversationManager;

    public TurnController(ConversationManager conversationManager) {
        this.conversationManager = conversationManager;
    }

    /**
     * 获取会话的所有 Turn 历史
     * <p>
     * 返回按时间倒序排列的 Turn 列表，支持分页。
     *
     * @param sessionId 会话 ID（从 Header 或参数获取）
     * @param limit 每页数量，默认 20
     * @param offset 偏移量，默认 0
     */
    @GetMapping
    public ResponseEntity<TurnListResponse> getTurns(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {

        log.info("获取 Turn 历史：sessionId={}, limit={}, offset={}", sessionId, limit, offset);

        try {
            // 从 ConversationManager 获取会话快照
            ConversationSession.SessionSnapshot snapshot = conversationManager.getSessionSnapshot();

            if (snapshot == null) {
                return ResponseEntity.notFound().build();
            }

            // 获取历史消息
            List<ChatMessage> messages = conversationManager.getHistory(limit);

            // 将消息分组为 Turn（基于消息 ID 关联）
            List<TurnInfo> turns = messages.stream()
                .filter(msg -> msg.type() == MessageType.USER)  // 每个用户消息是一个 Turn 的开始
                .map(msg -> {
                    // 查找该 Turn 内的所有相关消息
                    Instant turnStartTime = msg.timestamp();
                    List<WsProtocol.ChatMessage> turnMessages = messages.stream()
                        .filter(m -> m.id().equals(msg.id()) ||
                                     (m.timestamp() != null && turnStartTime != null &&
                                      m.timestamp().isAfter(turnStartTime)))
                        .map(WsProtocol.ChatMessage::from)
                        .toList();

                    return TurnInfo.from(msg, turnMessages);
                })
                .toList();

            TurnListResponse response = new TurnListResponse();
            response.turns = turns;
            response.total = turns.size();
            response.hasMore = turns.size() >= limit;

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取 Turn 历史失败", e);
            return ResponseEntity.internalServerError()
                .body(new TurnListResponse(null, 0, false, "获取失败：" + e.getMessage()));
        }
    }

    /**
     * 获取单个 Turn 的详细信息
     *
     * @param turnId Turn ID
     */
    @GetMapping("/{turnId}")
    public ResponseEntity<TurnDetailResponse> getTurn(@PathVariable String turnId) {
        log.info("获取 Turn 详情：turnId={}", turnId);

        try {
            // 获取所有历史消息
            List<ChatMessage> messages = conversationManager.getFullHistory();

            // 查找匹配的 Turn
            for (ChatMessage msg : messages) {
                if (msg.id().equals(turnId) && msg.type() == MessageType.USER) {
                    // 找到该 Turn 的所有相关消息
                    Instant turnStartTime = msg.timestamp();
                    List<WsProtocol.ChatMessage> turnMessages = messages.stream()
                        .filter(m -> m.id().equals(turnId) ||
                                     (m.timestamp() != null && turnStartTime != null &&
                                      m.timestamp().isAfter(turnStartTime)))
                        .map(WsProtocol.ChatMessage::from)
                        .toList();

                    TurnInfo turnInfo = TurnInfo.from(msg, turnMessages);
                    return ResponseEntity.ok(new TurnDetailResponse(turnInfo, null));
                }
            }

            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("获取 Turn 详情失败：turnId={}", turnId, e);
            return ResponseEntity.internalServerError()
                .body(new TurnDetailResponse(null, "获取失败：" + e.getMessage()));
        }
    }

    /**
     * 获取当前活跃 Turn 的信息
     */
    @GetMapping("/current")
    public ResponseEntity<CurrentTurnResponse> getCurrentTurn() {
        log.info("获取当前 Turn 状态");

        try {
            ConversationSession.SessionSnapshot snapshot = conversationManager.getSessionSnapshot();

            if (snapshot == null) {
                return ResponseEntity.notFound().build();
            }

            CurrentTurnResponse response = new CurrentTurnResponse();
            response.sessionId = snapshot.sessionId();
            // 使用消息计数作为 turn 数的近似值
            response.totalTurns = snapshot.messageCount() / 2;  // 假设每轮有用户和助手各一条消息
            response.sessionStartedAt = snapshot.createdAt();
            response.durationSeconds = snapshot.sessionDuration().getSeconds();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取当前 Turn 状态失败", e);
            return ResponseEntity.internalServerError()
                .body(new CurrentTurnResponse(null, 0, null, 0, "获取失败：" + e.getMessage()));
        }
    }

    // ==================== 数据传输对象 ====================

    /**
     * Turn 列表响应
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TurnListResponse {
        public List<TurnInfo> turns;
        public int total;
        public boolean hasMore;
        public String error;

        public TurnListResponse() {}

        public TurnListResponse(List<TurnInfo> turns, int total, boolean hasMore, String error) {
            this.turns = turns;
            this.total = total;
            this.hasMore = hasMore;
            this.error = error;
        }
    }

    /**
     * Turn 详情响应
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TurnDetailResponse {
        public TurnInfo turn;
        public String error;

        public TurnDetailResponse() {}

        public TurnDetailResponse(TurnInfo turn, String error) {
            this.turn = turn;
            this.error = error;
        }
    }

    /**
     * 当前 Turn 响应
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrentTurnResponse {
        public String sessionId;
        public int totalTurns;
        public Instant sessionStartedAt;
        public long durationSeconds;
        public String error;

        public CurrentTurnResponse() {}

        public CurrentTurnResponse(String sessionId, int totalTurns, Instant sessionStartedAt,
                                   long durationSeconds, String error) {
            this.sessionId = sessionId;
            this.totalTurns = totalTurns;
            this.sessionStartedAt = sessionStartedAt;
            this.durationSeconds = durationSeconds;
            this.error = error;
        }
    }

    /**
     * Turn 信息
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TurnInfo {
        public String turnId;
        public String userInput;
        public Instant startedAt;
        public Instant completedAt;
        public String status;  // PENDING, IN_PROGRESS, COMPLETED, FAILED
        public List<WsProtocol.ChatMessage> messages;
        public int toolCallCount;
        public long durationMs;
        public TurnMetrics metrics;

        public TurnInfo() {}

        public static TurnInfo from(ChatMessage userMsg, List<WsProtocol.ChatMessage> relatedMessages) {
            TurnInfo info = new TurnInfo();
            info.turnId = userMsg.id();
            info.userInput = userMsg.content();
            info.startedAt = userMsg.timestamp();

            // 计算完成时间和状态
            Instant latestTime = relatedMessages.stream()
                .map(m -> m.timestamp)
                .filter(t -> t != null)
                .max(Instant::compareTo)
                .orElse(userMsg.timestamp());

            info.completedAt = latestTime;
            info.status = "COMPLETED";  // 简化处理，实际需要根据工具调用状态判断

            info.messages = relatedMessages;
            info.toolCallCount = (int) relatedMessages.stream()
                .filter(m -> "TOOL".equals(m.type))
                .count();

            if (info.startedAt != null && info.completedAt != null) {
                info.durationMs = Duration.between(info.startedAt, info.completedAt).toMillis();
            }

            // 计算指标
            info.metrics = new TurnMetrics();
            info.metrics.inputTokens = relatedMessages.stream()
                .mapToLong(m -> m.inputTokens)
                .sum();
            info.metrics.outputTokens = relatedMessages.stream()
                .mapToLong(m -> m.outputTokens)
                .sum();

            return info;
        }
    }

    /**
     * Turn 指标
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TurnMetrics {
        public long inputTokens;
        public long outputTokens;
        public long latencyMs;
        public Map<String, Object> toolMetrics;

        public TurnMetrics() {}
    }
}
