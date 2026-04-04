# Convert all Java files to UTF-8 with BOM
$javaFiles = Get-ChildItem -Recurse -Filter "*.java" -Path "src\main\java"

foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    [System.IO.File]::WriteAllText($file.FullName, $content, [System.Text.Encoding]::UTF8)
    Write-Host "Converted: $($file.Name)"
}

Write-Host "Done. Converted $($javaFiles.Count) files."
