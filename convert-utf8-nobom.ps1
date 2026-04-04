# Convert all Java files to UTF-8 without BOM
$javaFiles = Get-ChildItem -Recurse -Filter "*.java" -Path "src\main\java"

foreach ($file in $javaFiles) {
    # Read content as raw bytes
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    # Check for BOM and skip it
    $bom = [System.Text.Encoding]::UTF8.GetPreamble()
    if ($bytes.Length -ge 3 -and $bytes[0] -eq $bom[0] -and $bytes[1] -eq $bom[1] -and $bytes[2] -eq $bom[2]) {
        $bytes = $bytes[3..($bytes.Length-1)]
    }
    $content = [System.Text.Encoding]::UTF8.GetString($bytes)
    # Write back without BOM
    [System.IO.File]::WriteAllText($file.FullName, $content, (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Done. Converted $($javaFiles.Count) files to UTF-8 without BOM."
