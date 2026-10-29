# Back up the app's database from a connected device BEFORE any destructive test.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\backup-device-db.ps1
#   powershell -ExecutionPolicy Bypass -File tools\backup-device-db.ps1 -Serial emulator-5554
#
# Notes:
# - Only works while a **debug** build is installed (release builds are not
#   debuggable, so run-as is rejected). Back up first, then switch builds.
# - db / -wal / -shm must be copied together, otherwise SQLite cannot replay
#   the WAL and you would read a stale snapshot.
# - This script is intentionally kept ASCII-only: PowerShell 5.1 reads .ps1
#   files using the system ANSI codepage, which corrupts non-ASCII source.
param(
    [string]$Serial = "",
    [string]$Package = "com.example.collocationmemory",
    [string]$OutRoot = "",
    # Optional: python used for post-backup verification.
    # Do NOT hardcode this path in the file as literal non-ASCII text:
    # PowerShell 5.1 reads .ps1 using the system ANSI codepage, which corrupts
    # any non-ASCII source text. Building it at runtime from environment
    # variables keeps the script file itself pure ASCII.
    [string]$PythonExe = ""
)

$ErrorActionPreference = "Stop"
$adb = "D:\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found: $adb" }

if ([string]::IsNullOrWhiteSpace($OutRoot)) {
    $OutRoot = Join-Path $PSScriptRoot "..\backup"
}

$adbArgs = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) { $adbArgs += @("-s", $Serial) }

# Device must be online
$probe = (& $adb @adbArgs shell echo ok 2>&1) -join ''
if ($probe -notmatch 'ok') {
    throw "Device not available (Serial='$Serial'). Check: adb devices"
}

# Package must be debuggable or run-as will fail
$dbg = (& $adb @adbArgs shell "run-as $Package echo ok" 2>&1) -join ''
if ($dbg -notmatch 'ok') {
    throw "run-as denied: $Package is not a debuggable build. Install the debug build before backing up."
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $OutRoot "device-db-$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$dbDir = "/data/data/$Package/databases"
$files = @(
    "collocation_memory_db",
    "collocation_memory_db-wal",
    "collocation_memory_db-shm"
)

$ok = 0
foreach ($f in $files) {
    $dst = Join-Path $outDir $f
    # Must use exec-out with a cmd redirect to capture raw bytes.
    # PowerShell's own ">" would re-encode the stream as UTF-16 and destroy the file.
    $adbPrefix = ($adbArgs -join ' ')
    cmd /c "`"$adb`" $adbPrefix exec-out run-as $Package cat $dbDir/$f > `"$dst`""

    if (-not (Test-Path $dst) -or (Get-Item $dst).Length -eq 0) {
        Write-Warning "  $f : empty or missing (may not exist on device)"
        Remove-Item $dst -ErrorAction SilentlyContinue
        continue
    }
    $ok++
    Write-Host ("  {0,-28} {1,8} bytes" -f $f, (Get-Item $dst).Length)
}

if ($ok -eq 0) { throw "Nothing was copied; backup failed" }

# Verify the copy is actually a readable database with rows in it.
# If verification cannot be completed, we FAIL the backup: an unverified
# backup is worse than none, because you would trust it during a restore.
if ([string]::IsNullOrWhiteSpace($PythonExe)) {
    $PythonExe = Join-Path $env:USERPROFILE ".dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe"
}
if (-not (Test-Path $PythonExe)) {
    throw "Cannot verify backup: python not found at $PythonExe (pass -PythonExe to override)"
}
$dbPath = Join-Path $outDir "collocation_memory_db"
$verifyScript = @"
import sqlite3, sys
try:
    con = sqlite3.connect(r'$dbPath')
    n = con.execute('select count(*) from collocations').fetchone()[0]
    l = con.execute('select count(*) from collocations where isLearned=1').fetchone()[0]
    con.close()
except Exception as e:
    print('  verify FAILED: %s' % e)
    sys.exit(1)
if n <= 0:
    print('  verify FAILED: table is empty')
    sys.exit(1)
print('  verified: %d words, %d learned' % (n, l))
"@
& $PythonExe -c $verifyScript
if ($LASTEXITCODE -ne 0) { throw "Backup file is not a readable database; do not trust it" }

Write-Host ""
Write-Host "Backup OK -> $outDir" -ForegroundColor Green
Write-Host "Restore with: tools\restore-device-db.ps1 -BackupDir `"$outDir`"" -ForegroundColor Yellow
Write-Host "Important: the restore must delete stale -wal/-shm first, otherwise" -ForegroundColor Yellow
Write-Host "SQLite replays the old WAL and silently reverts your restore." -ForegroundColor Yellow
