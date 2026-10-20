# Re-enables everything debloat.ps1 hid (hidden.log) plus every package named in bloat.txt, commented or not.
# Usage: powershell -ExecutionPolicy Bypass -File restore.ps1 [-Tv ip:5555]
param([string]$Tv = '192.168.8.155:5555')
$ErrorActionPreference = 'Stop'

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools*\platform-tools\adb.exe" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName }
if (-not $adb) { throw 'adb not found. Install it with: winget install Google.PlatformTools' }
& $adb connect $Tv | Out-Null
if ((& $adb -s $Tv get-state 2>$null) -ne 'device') { throw "Can't reach $Tv. Check ADB debugging is on and this PC is approved on the TV." }

$log = Join-Path $PSScriptRoot 'hidden.log'
$fromList = Get-Content (Join-Path $PSScriptRoot 'bloat.txt') | ForEach-Object { if ($_ -match '^\s*#?\s*([a-z]\w*(\.\w+)+)') { $Matches[1] } }
$fromLog = if (Test-Path $log) { Get-Content $log } else { @() }
$disabled = & $adb -s $Tv shell pm list packages -d | ForEach-Object { ($_ -replace '^package:').Trim() }

@($fromLog) + @($fromList) | Sort-Object -Unique | Where-Object { $_ -in $disabled } | ForEach-Object {
    "$_ : $(& $adb -s $Tv shell "pm enable $_ 2>&1")"   # 2>&1 runs on the TV (see debloat.ps1)
}
if (Test-Path $log) { Remove-Item $log }
"Done. Reboot the TV (adb -s $Tv reboot) if the home screen doesn't refresh."
