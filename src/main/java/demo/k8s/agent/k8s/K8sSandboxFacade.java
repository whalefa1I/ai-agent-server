package demo.k8s.agent.k8s;

/**
 * 统一抽象，避免 {@link K8sJobSandboxService} 与 {@link NoopK8sJobSandboxService} 重复注入。
 */
public interface K8sSandboxFacade {

    String runShellSnippet(String command);
}
