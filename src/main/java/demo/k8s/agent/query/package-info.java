/**
 * 与 Claude Code {@code src/query.ts} / {@code QueryEngine.ts} 对齐的<strong>可运行内核</strong>（非全量移植）：
 * <ul>
 *   <li>{@link demo.k8s.agent.query.AgenticQueryLoop}：compaction → {@link org.springframework.ai.chat.model.ChatModel#call} →
 *       {@link org.springframework.ai.model.tool.ToolCallingManager#executeToolCalls} 的多轮循环</li>
 *   <li>{@link demo.k8s.agent.query.DefaultCompactionPipeline}：Tier1 截断 tool 结果；可选 Tier3 摘要（autocompact 类比）</li>
 *   <li>{@link demo.k8s.agent.query.ModelCallRetryPolicy}：HTTP 可重试错误退避（与 {@code withRetry} 不同层）</li>
 *   <li>{@link demo.k8s.agent.query.QueryLoopState} / {@link demo.k8s.agent.query.ContinuationReason}：状态与审计标签</li>
 * </ul>
 * 参考：Zread
 * <a href="https://zread.ai/instructkr/claude-code/13-queryengine-core-loop">QueryEngine Core Loop</a>、
 * <a href="https://zread.ai/instructkr/claude-code/14-tool-call-loop-and-retry-logic">Tool-Call Loop and Retry</a>、
 * <a href="https://zread.ai/instructkr/claude-code/15-context-compression-and-compaction">Context Compression</a>。
 */
package demo.k8s.agent.query;
