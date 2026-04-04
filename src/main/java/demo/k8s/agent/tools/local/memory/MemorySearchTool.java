package demo.k8s.agent.tools.local.memory;

import demo.k8s.agent.memory.model.MemoryEntry;
import demo.k8s.agent.memory.search.MemorySearchService;
import demo.k8s.agent.tools.local.LocalToolResult;
import demo.k8s.agent.toolsystem.ClaudeLikeTool;
import demo.k8s.agent.toolsystem.ClaudeToolFactory;
import demo.k8s.agent.toolsystem.ToolCategory;
import demo.k8s.agent.toolsystem.ToolDefPartial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记忆搜索工具 - 允许 Agent 搜索跨会话记忆
 */
public class MemorySearchTool {

    private static final Logger log = LoggerFactory.getLogger(MemorySearchTool.class);

    private static MemorySearchService searchService;

    /**
     * 设置搜索服务（由 Spring 初始化时调用）
     */
    public static void setSearchService(MemorySearchService service) {
        searchService = service;
    }

    private static final String INPUT_SCHEMA =
            "{" +
            "  \"type\": \"object\"," +
            "  \"properties\": {" +
            "    \"query\": {\"type\": \"string\", \"description\": \"Search query for semantic memory search\"}," +
            "    \"maxResults\": {\"type\": \"integer\", \"description\": \"Maximum number of results to return (default 10)\"}," +
            "    \"sessionId\": {\"type\": \"string\", \"description\": \"Filter by session ID (optional)\"}," +
            "    \"source\": {\"type\": \"string\", \"description\": \"Filter by memory source: CONVERSATION, FILE, USER_NOTE, MEMORY_FILE (optional)\"}" +
            "  }," +
            "  \"required\": [\"query\"]" +
            "}";

    /**
     * 创建工具定义
     */
    public static ClaudeLikeTool createTool() {
        return ClaudeToolFactory.buildTool(
                new ToolDefPartial(
                        "memory_search",
                        ToolCategory.MEMORY,
                        "Search across conversation history and memory files using semantic similarity",
                        INPUT_SCHEMA,
                        null,
                        true)); // 只读操作
    }

    /**
     * 执行记忆搜索
     *
     * @param input 输入参数
     *              - query: 搜索查询（必需）
     *              - maxResults: 最大结果数（可选，默认 10）
     *              - sessionId: 会话 ID 过滤器（可选）
     *              - source: 来源过滤器（可选）
     * @return 搜索结果
     */
    public static LocalToolResult execute(Map<String, Object> input) {
        if (searchService == null) {
            return LocalToolResult.error("Memory search service not initialized");
        }

        String query = (String) input.get("query");
        if (query == null || query.isBlank()) {
            return LocalToolResult.error("query is required");
        }

        Integer maxResultsObj = (Integer) input.getOrDefault("maxResults", 10);
        int maxResults = maxResultsObj != null ? maxResultsObj : 10;

        String sessionId = (String) input.get("sessionId");
        String sourceStr = (String) input.get("source");

        // 解析来源过滤器
        MemoryEntry.MemorySource sourceFilter = null;
        if (sourceStr != null && !sourceStr.isBlank()) {
            try {
                sourceFilter = MemoryEntry.MemorySource.valueOf(sourceStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return LocalToolResult.error("Invalid source: " + sourceStr + ". Valid values: CONVERSATION, FILE, USER_NOTE, MEMORY_FILE");
            }
        }

        // 执行搜索
        List<MemoryEntry> results = searchService.search(query, maxResults,
                new MemorySearchService.SearchFilter(sourceFilter, sessionId));

        if (results.isEmpty()) {
            return LocalToolResult.success("No memories found for query: " + query, Map.of(
                    "query", query,
                    "count", 0
            ));
        }

        // 构建结果输出
        List<Map<String, Object>> memoryResults = new ArrayList<>();
        for (MemoryEntry entry : results) {
            memoryResults.add(Map.of(
                    "id", entry.getId(),
                    "content", entry.getContent(),
                    "source", entry.getSource().name(),
                    "sessionId", entry.getSessionId() != null ? entry.getSessionId() : "null",
                    "createdAt", entry.getCreatedAt().toString(),
                    "summary", entry.getSummary()
            ));
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Found ").append(results.size()).append(" memories for query: ").append(query).append("\n\n");
        for (int i = 0; i < memoryResults.size(); i++) {
            Map<String, Object> m = memoryResults.get(i);
            summary.append("[").append(i + 1).append("] ").append(m.get("content")).append("\n");
            summary.append("    Source: ").append(m.get("source")).append(", Session: ").append(m.get("sessionId")).append("\n");
        }

        return LocalToolResult.success(summary.toString(), Map.of(
                "query", query,
                "count", results.size(),
                "memories", memoryResults
        ));
    }
}
