# Makes Glass Home the home screen and lets its brightness slider work. -Undo reverses both.
# Fire OS hard-wires Home to Amazon's launcher and refuses to disable it ("protected package"), so this
# turns on Glass Home's HomeWatcher accessibility service, which opens Glass Home whenever Amazon's
# home screen appears. It can also be switched off on the TV under Settings > Accessibility.
# Fire OS sometimes wipes accessibility settings on its own; glasshome_home=1 plus WRITE_SECURE_SETTINGS lets Glass Home
# switch its service straight back on (Keeper.java). -Undo clears the flag and revokes the permission.
# Usage: powershell -ExecutionPolicy Bypass -File set-home.ps1 [-Undo] [-Tv ip:5555]
param([switch]$Undo, [string]$Tv = '192.168.8.155:5555')

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools*\platform-tools\adb.exe" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName }
if (-not $adb) { throw 'adb not found. Install it with: winget install Google.PlatformTools' }
& $adb connect $Tv | Out-Null
if ((& $adb -s $Tv get-state 2>$null) -ne 'device') { throw "Can't reach $Tv. Check ADB debugging is on and this PC is approved on the TV." }

$svc = 'app.glasshome/app.glasshome.HomeWatcher'
# Keep any other accessibility services (e.g. VoiceView) that are already on.
$others = @("$(& $adb -s $Tv shell settings get secure enabled_accessibility_services)".Trim() -split ':' | Where-Object { $_ -and $_ -ne 'null' -and $_ -ne $svc })

if ($Undo) {
    & $adb -s $Tv shell settings put secure glasshome_home 0
    & $adb -s $Tv shell pm revoke app.glasshome android.permission.WRITE_SECURE_SETTINGS 2>$null
    if ($others) { & $adb -s $Tv shell settings put secure enabled_accessibility_services ($others -join ':') }
    else { & $adb -s $Tv shell settings delete secure enabled_accessibility_services | Out-Null }
    & $adb -s $Tv shell appops set app.glasshome WRITE_SETTINGS default
    'Home goes to Amazon''s launcher again.'
} else {
    if (-not (& $adb -s $Tv shell pm list packages app.glasshome)) { throw 'Glass Home is not installed. Run build.ps1 -Install first.' }
    & $adb -s $Tv shell settings put secure enabled_accessibility_services (($others + $svc) -join ':')
    & $adb -s $Tv shell settings put secure accessibility_enabled 1
    & $adb -s $Tv shell pm grant app.glasshome android.permission.WRITE_SECURE_SETTINGS   # lets it restore itself if Fire OS wipes it
    & $adb -s $Tv shell settings put secure glasshome_home 1
    & $adb -s $Tv shell appops set app.glasshome WRITE_SETTINGS allow   # brightness slider
    & $adb -s $Tv shell cmd package set-home-activity app.glasshome/.Main | Out-Null
    $on = "$(& $adb -s $Tv shell settings get secure enabled_accessibility_services)"
    if ($on -notmatch [regex]::Escape($svc)) { throw "Fire OS didn't keep the setting: $on" }
    'Glass Home is the home screen. Undo with: set-home.ps1 -Undo'
}
