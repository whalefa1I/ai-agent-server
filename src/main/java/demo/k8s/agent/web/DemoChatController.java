package demo.k8s.agent.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简单对话控制器 - 已禁用，由 StatefulChatController 替代
 */
@RestController
@RequestMapping(path = "/api/internal", produces = MediaType.APPLICATION_JSON_VALUE)
@org.springframework.context.annotation.Profile("dev") // 仅在 dev  profile 启用
public class DemoChatController {

    private final ChatClient demoChatClient;

    public DemoChatController(ChatClient demoChatClient) {
        this.demoChatClient = demoChatClient;
    }

    @PostMapping("/chat")
    public DemoChatResponse chat(@RequestBody ChatRequest request) {
        String user = request.message() == null ? "" : request.message();
        String reply = demoChatClient.prompt().user(user).call().content();
        return new DemoChatResponse(reply);
    }

    public record DemoChatResponse(String reply) {}
}
