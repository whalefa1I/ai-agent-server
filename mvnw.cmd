@echo off
REM Maven wrapper for minimal-k8s-agent-demo
set MAVEN_HOME=C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
"%MAVEN_HOME%\bin\mvn.cmd" %*
