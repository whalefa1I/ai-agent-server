package demo.k8s.agent.toolstate;

import java.util.Map;

/**
 * 工具状态更新事件
 *
 * 对应 happy-server 的 UpdateEvent 中 update-artifact 事件
 */
public class ToolStateUpdateEvent {

    private final String type;
    private final String artifactId;
    private final ToolArtifactHeader header;
    private final ToolArtifactBody body;
    private final long timestamp;

    public ToolStateUpdateEvent(ToolArtifact artifact) {
        this.type = "update-tool-artifact";
        this.artifactId = artifact.getId();
        this.header = parseHeader(artifact.getHeader(), artifact.getHeaderVersion());
        this.body = parseBody(artifact.getBody(), artifact.getBodyVersion());
        this.timestamp = System.currentTimeMillis();
    }

    public ToolStateUpdateEvent(String artifactId, ToolArtifactHeader header, ToolArtifactBody body) {
        this.type = "update-tool-artifact";
        this.artifactId = artifactId;
        this.header = header;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
    }

    private ToolArtifactHeader parseHeader(String headerJson, int version) {
        // TODO: 使用 Jackson 解析 JSON
        return new ToolArtifactHeader();
    }

    private ToolArtifactBody parseBody(String bodyJson, int version) {
        // TODO: 使用 Jackson 解析 JSON
        return new ToolArtifactBody();
    }

    // Getters
    public String getType() { return type; }
    public String getArtifactId() { return artifactId; }
    public ToolArtifactHeader getHeader() { return header; }
    public ToolArtifactBody getBody() { return body; }
    public long getTimestamp() { return timestamp; }
}
