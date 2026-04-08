# Dot-source at the top of other .ps1 scripts (after param block if any).
# Makes Windows PowerShell 5.1 show Chinese/UTF-8 correctly; safe on pwsh (Windows/Linux).

if ($PSVersionTable.PSVersion.Major -le 5) {
    try { chcp 65001 | Out-Null } catch {}
}
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {}
