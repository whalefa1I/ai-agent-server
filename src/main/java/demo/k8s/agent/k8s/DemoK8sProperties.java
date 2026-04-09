package demo.k8s.agent.k8s;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.k8s")
public class DemoK8sProperties {

    /**
     * 是否真正访问集群；为 false 时使用 {@link NoopK8sJobSandboxService}。
     */
    private boolean enabled = false;

    private String namespace = "default";

    /**
     * 执行用户命令的镜像（需含 sh，默认 busybox）。
     */
    private String runnerImage = "busybox:1.36";

    private int ttlSecondsAfterFinished = 120;

    /**
     * 等待 Job 完成的最长时间。
     */
    private Duration timeout = Duration.ofMinutes(3);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getRunnerImage() {
        return runnerImage;
    }

    public void setRunnerImage(String runnerImage) {
        this.runnerImage = runnerImage;
    }

    public int getTtlSecondsAfterFinished() {
        return ttlSecondsAfterFinished;
    }

    public void setTtlSecondsAfterFinished(int ttlSecondsAfterFinished) {
        this.ttlSecondsAfterFinished = ttlSecondsAfterFinished;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
