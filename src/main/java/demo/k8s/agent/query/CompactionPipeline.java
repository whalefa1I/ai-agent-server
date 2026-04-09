package demo.k8s.agent.query;

import java.util.List;

import org.springframework.ai.chat.messages.Message;

/**
 * 对齐 {@code query.ts} 每次模型调用前的「分层压缩」管线入口（工具结果预算 → snip → microcompact → collapse → autocompact）。
 * 实现类可拆为多阶段 Bean。
 */
@FunctionalInterface
public interface CompactionPipeline {

    /** 每次模型调用前对消息列表做压缩准备（对齐 query.ts 中 pre-iteration pipeline）。 */
    List<Message> compactBeforeModelCall(List<Message> messages);
}
