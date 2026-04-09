package demo.k8s.agent.config;

import org.springframework.ai.tool.annotation.ToolParam;

public record K8sSandboxInput(
        @ToolParam(description = "在沙盒容器中执行的一段 shell（单行，勿换行）") String command) {}
