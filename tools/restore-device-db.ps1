# Restore the app database from a backup made by backup-device-db.ps1
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\restore-device-db.ps1 -BackupDir backup\device-db-YYYYMMDD-HHMMSS
#   powershell -ExecutionPolicy Bypass -File tools\restore-device-db.ps1 -BackupDir <dir> -Serial emulator-5554
#
# CRITICAL: the app must be stopped and any leftover -wal / -shm files must be
# deleted BEFORE dropping the backup in place.
#
# Why: in WAL mode, committed data can live in the -wal file. If you only
# overwrite the main db file and leave a stale -wal behind, SQLite replays that
# WAL on the next open and silently reverts your restore. This was observed
# during testing: the restore "succeeded" but the old data came back.
#
# ASCII-only on purpose: PowerShell 5.1 reads .ps1 with the system ANSI
# codepage, which corrupts any non-ASCII source text.
param(
    [Parameter(Mandatory = $true)][string]$BackupDir,
    [string]$Serial = "",
    [string]$Package = "com.example.collocationmemory"
)

$ErrorActionPreference = "Stop"
$adb = "D:\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found: $adb" }

$src = Resolve-Path $BackupDir -ErrorAction Stop
$mainDb = Join-Path $src "collocation_memory_db"
if (-not (Test-Path $mainDb)) { throw "collocation_memory_db not found in $src" }

$adbArgs = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) { $adbArgs += @("-s", $Serial) }

$probe = (& $adb @adbArgs shell echo ok 2>&1) -join ''
if ($probe -notmatch 'ok') { throw "Device not available (Serial='$Serial')" }

$dbg = (& $adb @adbArgs shell "run-as $Package echo ok" 2>&1) -join ''
if ($dbg -notmatch 'ok') {
    throw "run-as denied: install the DEBUG build before restoring"
}

$remoteDbDir = "/data/data/$Package/databases"

Write-Host "1/4 stop the app"
& $adb @adbArgs shell am force-stop $Package | Out-Null
Start-Sleep -Seconds 2

Write-Host "2/4 push backup to a staging path"
& $adb @adbArgs push $mainDb /data/local/tmp/restore_db | Out-Null

Write-Host "3/4 drop stale -wal / -shm, then copy the db in place"
# Removing the stale WAL is the step that makes the restore actually stick
& $adb @adbArgs shell "run-as $Package rm -f $remoteDbDir/collocation_memory_db-wal $remoteDbDir/collocation_memory_db-shm" | Out-Null
& $adb @adbArgs shell "run-as $Package cp /data/local/tmp/restore_db $remoteDbDir/collocation_memory_db" | Out-Null
# Clean up the staging file with the shell user (run-as cannot delete it)
& $adb @adbArgs shell rm -f /data/local/tmp/restore_db | Out-Null

Write-Host "4/4 start the app so room opens the restored file"
& $adb @adbArgs shell monkey -p $Package -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 8

Write-Host ""
Write-Host "Restore done. Now verify with:" -ForegroundColor Green
Write-Host "  python tools\dev\check_sentinel.py $Serial"
