package demo.k8s.agent.k8s;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 未启用 K8s 时返回说明文本，便于本地启动联调 LLM，无需集群。
 */
@Service
@ConditionalOnProperty(name = "demo.k8s.enabled", havingValue = "false", matchIfMissing = true)
public class NoopK8sJobSandboxService implements K8sSandboxFacade {

    @Override
    public String runShellSnippet(String command) {
        return "[demo.k8s.enabled=false] 未连接 Kubernetes。请设置 demo.k8s.enabled=true 并配置 kubeconfig。"
                + " 收到的命令预览: "
                + truncate(command, 200);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
