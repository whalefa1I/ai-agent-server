package demo.k8s.agent.web;

import demo.k8s.agent.query.AgenticQueryLoop;
import demo.k8s.agent.query.AgenticTurnResult;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 使用显式 {@link AgenticQueryLoop}（QueryEngine / query 内层循环的 Java 投影），与 {@link DemoChatController} 的「单次 ChatClient」路径并存。
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class AgenticChatController {

    private final AgenticQueryLoop agenticQueryLoop;

    public AgenticChatController(AgenticQueryLoop agenticQueryLoop) {
        this.agenticQueryLoop = agenticQueryLoop;
    }

    @PostMapping("/chat/agentic")
    public AgenticTurnResult chatAgentic(@RequestBody ChatRequest request) {
        String user = request.message() == null ? "" : request.message();
        return agenticQueryLoop.run(user);
    }
}
