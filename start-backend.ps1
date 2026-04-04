#!/usr/bin/env pwsh
# Start backend with proper Java and Maven paths

Set-Location "G:\project\ai-agent-server"

# Set Java 21
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"

# Use Maven wrapper
& .\mvnw.cmd "spring-boot:run"
