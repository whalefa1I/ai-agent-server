package demo.k8s.agent.k8s;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 在指定 Namespace 中创建短生命周期 Job，在 busybox 容器内执行 shell 片段并收集日志。
 * 生产环境请配合 RBAC、NetworkPolicy、镜像白名单与命令审计使用。
 */
@Service
@ConditionalOnProperty(name = "demo.k8s.enabled", havingValue = "true")
public class K8sJobSandboxService implements K8sSandboxFacade {

    private static final Logger log = LoggerFactory.getLogger(K8sJobSandboxService.class);

    private final KubernetesClient client;
    private final DemoK8sProperties props;

    public K8sJobSandboxService(KubernetesClient client, DemoK8sProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public String runShellSnippet(String command) {
        validateCommand(command);
        String jobName = buildJobName();

        Container container = new ContainerBuilder()
                .withName("sandbox")
                .withImage(props.getRunnerImage())
                .withCommand("sh", "-c", command)
                .build();

        Job job = new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .withLabels(Map.of("app", "minimal-k8s-agent-demo", "demo.k8s.agent/job", jobName))
                .endMetadata()
                .withSpec(new JobSpecBuilder()
                        .withNewTemplate()
                        .withNewMetadata()
                        .withLabels(Map.of("job-name", jobName))
                        .endMetadata()
                        .withNewSpec()
                        .withRestartPolicy("Never")
                        .addToContainers(container)
                        .endSpec()
                        .endTemplate()
                        .withBackoffLimit(0)
                        .withTtlSecondsAfterFinished(props.getTtlSecondsAfterFinished())
                        .build())
                .build();

        client.batch().v1().jobs().inNamespace(props.getNamespace()).resource(job).create();

        try {
            waitForCompletion(jobName, props.getTimeout());
            return readPodLogs(jobName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Job execution was interrupted", e);
        } finally {
            try {
                client.batch().v1().jobs().inNamespace(props.getNamespace()).withName(jobName).delete();
            } catch (Exception e) {
                log.warn("删除 Job 失败: {}", jobName, e);
            }
        }
    }

    private void waitForCompletion(String jobName, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Job j = client.batch().v1().jobs().inNamespace(props.getNamespace()).withName(jobName).get();
            if (j != null && j.getStatus() != null) {
                Integer succeeded = j.getStatus().getSucceeded();
                Integer failed = j.getStatus().getFailed();
                if (succeeded != null && succeeded > 0) {
                    return;
                }
                if (failed != null && failed > 0) {
                    throw new IllegalStateException("Job 失败: " + jobName);
                }
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("等待 Job 超时: " + jobName);
    }

    private String readPodLogs(String jobName) {
        Pod pod = client.pods()
                .inNamespace(props.getNamespace())
                .withLabel("job-name", jobName)
                .list()
                .getItems()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到 Job 对应 Pod: " + jobName));

        String podName = pod.getMetadata().getName();
        try {
            return client.pods().inNamespace(props.getNamespace()).withName(podName).getLog();
        } catch (Exception e) {
            throw new IllegalStateException("读取 Pod 日志失败: " + podName, e);
        }
    }

    private static void validateCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command 不能为空");
        }
        String t = command.toLowerCase(Locale.ROOT);
        if (command.contains("\n") || command.contains("\r")) {
            throw new IllegalArgumentException("不允许换行");
        }
        for (String deny : new String[] {"kubectl", "curl", "wget", "nc ", "bash -i"}) {
            if (t.contains(deny)) {
                throw new IllegalArgumentException("命令包含禁止片段: " + deny);
            }
        }
    }

    private static String buildJobName() {
        String u = UUID.randomUUID().toString().replace("-", "");
        String name = "dj" + u;
        return name.length() <= 63 ? name : name.substring(0, 63);
    }
}
