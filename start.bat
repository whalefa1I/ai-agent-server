@echo off
cd /d %~dp0
"D:\Program Files\Android\Android Studio\jbr\bin\java.exe" -Xms512m -Xmx2g -jar target\minimal-k8s-agent-demo-0.1.0-SNAPSHOT.jar > app.log 2>&1
