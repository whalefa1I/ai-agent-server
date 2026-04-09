package demo.k8s.agent.logquery;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Minimal Loki push appender for structured JSON log lines.
 */
public class LokiPushAppender extends AppenderBase<ILoggingEvent> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private String url;
    private String username;
    private String password;
    private String job = "ai-agent-server";
    private String serviceName = "ai-agent-server";
    private int timeoutSeconds = 5;
    private String authHeader;

    @Override
    public void start() {
        if (isBlank(url) || isBlank(username) || isBlank(password)) {
            addInfo("LokiPushAppender disabled: missing url/username/password");
            return;
        }
        String token = username + ":" + password;
        authHeader = "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        super.start();
        addInfo("LokiPushAppender started");
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted()) {
            return;
        }
        try {
            long timestampNs = eventObject.getTimeStamp() * 1_000_000L;
            String line = eventObject.getFormattedMessage();
            if (isBlank(line)) {
                return;
            }

            Map<String, Object> payload = Map.of(
                    "streams", List.of(
                            Map.of(
                                    "stream", Map.of(
                                            "job", job,
                                            "service_name", serviceName
                                    ),
                                    "values", List.of(
                                            List.of(String.valueOf(timestampNs), line)
                                    )
                            )
                    )
            );

            String body = OBJECT_MAPPER.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                addWarn("Loki push failed, status=" + code);
            }
        } catch (Exception e) {
            addWarn("Loki push exception: " + e.getMessage());
        }
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
