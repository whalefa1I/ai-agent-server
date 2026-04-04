$ProgressPreference = 'SilentlyContinue'
$url = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10+9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.10_9.msi"
$outFile = "$env:TEMP\jdk-21.msi"
Write-Host "Downloading JDK 21..."
Invoke-WebRequest -Uri $url -OutFile $outFile -UseBasicParsing
Write-Host "Downloaded to $outFile"
Write-Host "Installing JDK 21..."
Start-Process msiexec.exe -Wait -ArgumentList "/i `"$outFile`" /quiet /norestart"
Write-Host "Installation complete!"
