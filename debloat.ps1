# Hides (disables) every uncommented package in bloat.txt. Nothing is uninstalled; restore.ps1 undoes it.
# Usage: powershell -ExecutionPolicy Bypass -File debloat.ps1 [-Tv ip:5555] [-WhatIf]
param([string]$Tv = '192.168.8.155:5555', [switch]$WhatIf)
$ErrorActionPreference = 'Stop'

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools*\platform-tools\adb.exe" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName }
if (-not $adb) { throw 'adb not found. Install it with: winget install Google.PlatformTools' }
& $adb connect $Tv | Out-Null
if ((& $adb -s $Tv get-state 2>$null) -ne 'device') { throw "Can't reach $Tv. Check ADB debugging is on and this PC is approved on the TV." }

# Hiding any of these breaks Live TV, inputs, the remote, picture/sound, sign-in or the home screen.
$never = '^(com\.amazon\.tv\.(launcher|livetv|settings.*|ime|channelscan|inputpreference.*|routing|devicecontrol.*|keypolicymanager|oobe)|com\.amazon\.mediatek\..*|com\.mediatek\..*|com\.amazon\.franktvinput|com\.amazon\.tifobserver|com\.amazon\.(fireinputdevices|autopairservice|accessoryconfigservice|venezia|vizzini|firehomestarter|identity\..*|ftve\..*)|com\.amazon\.device\.(bluetooth.*|blepa)|com\.android\..*|android.*)$'

$installed = & $adb -s $Tv shell pm list packages | ForEach-Object { ($_ -replace '^package:').Trim() }
$log = Join-Path $PSScriptRoot 'hidden.log'

Get-Content (Join-Path $PSScriptRoot 'bloat.txt') | ForEach-Object { ($_ -split '#')[0].Trim() } | Where-Object { $_ } | ForEach-Object {
    if ($_ -match $never)        { "REFUSED $_ (protected system package)"; return }
    if ($_ -notin $installed)    { "skip    $_ (not installed)"; return }
    if ($WhatIf)                 { "would hide $_"; return }
    # 2>&1 runs on the TV: in PowerShell 5.1 a local 2>&1 turns adb's stderr into a terminating error.
    $out = "$(& $adb -s $Tv shell "pm disable-user --user 0 $_ 2>&1 | head -1")"
    if ($out -match 'disabled-user') { "hidden  $_"; Add-Content $log $_ } else { "FAILED  $_ : $out" }
}
