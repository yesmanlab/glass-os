# Builds GlassHome.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Usage: powershell -ExecutionPolicy Bypass -File build.ps1 [-Install]   (-Install pushes it to the TV)
param([switch]$Install, [string]$Tv = '192.168.8.155:5555')
$ErrorActionPreference = 'Continue'  # PS 5.1 treats tool stderr as fatal under Stop; each step checks $LASTEXITCODE instead
Set-Location $PSScriptRoot

$sdk = "$env:LOCALAPPDATA\Android\Sdk"; $bt = "$sdk\build-tools\34.0.0"; $jar = "$sdk\platforms\android-34\android.jar"
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = (Get-ChildItem 'C:\Program Files\Microsoft\jdk-17*' -Directory | Select-Object -First 1).FullName }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Remove-Item -Recurse -Force out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out\classes | Out-Null
$assets = @()   # optional keys: Stremio (library + Continue tile), TMDB (more backdrops on Open)
foreach ($k in 'stremio.key', 'tmdb.key') { if (Test-Path $k) { New-Item -ItemType Directory -Force out\assets | Out-Null; Copy-Item $k out\assets\; $assets = @('-A', 'out\assets') } }
& "$bt\aapt2.exe" link -o out\base.apk -I $jar --manifest AndroidManifest.xml --min-sdk-version 28 --target-sdk-version 28 @assets
if ($LASTEXITCODE) { throw 'aapt2 failed' }
& javac --release 8 -encoding UTF-8 -classpath $jar -d out\classes (Get-ChildItem src -Recurse -Filter *.java).FullName
if ($LASTEXITCODE) { throw 'javac failed' }
& "$bt\d8.bat" --release --min-api 28 --lib $jar --output out (Get-ChildItem out\classes -Recurse -Filter *.class).FullName
if ($LASTEXITCODE) { throw 'd8 failed' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$PWD\out\base.apk", 'Update')
[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$PWD\out\classes.dex", 'classes.dex') | Out-Null
$zip.Dispose()

if (-not (Test-Path debug.keystore)) {  # keep this file: reinstalling over an app signed with another key fails
    & keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias glasshome -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Glass Home' 2>&1 | Out-Null
}
& "$bt\zipalign.exe" -f -p 4 out\base.apk out\aligned.apk
if ($LASTEXITCODE) { throw 'zipalign failed' }
& "$bt\apksigner.bat" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out GlassHome.apk out\aligned.apk
if ($LASTEXITCODE) { throw 'apksigner failed' }
"Built $PSScriptRoot\GlassHome.apk ($([math]::Round((Get-Item GlassHome.apk).Length / 1KB)) KB)"

if ($Install) {
    $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
    if (-not $adb) { $adb = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools*\platform-tools\adb.exe" | Select-Object -First 1 -ExpandProperty FullName }
    & $adb connect $Tv | Out-Null
    & $adb -s $Tv install -r GlassHome.apk
    & $adb -s $Tv shell cmd package compile -m speed -f app.glasshome   # ahead-of-time compile: fastest start and key response
    # An update leaves the Home service bound with an empty config until it's toggled; re-apply if it was on.
    if ("$(& $adb -s $Tv shell settings get secure glasshome_home)" -eq '1' -or "$(& $adb -s $Tv shell settings get secure enabled_accessibility_services)" -match 'glasshome') {
        & "$PSScriptRoot\set-home.ps1" -Undo -Tv $Tv | Out-Null
        & "$PSScriptRoot\set-home.ps1" -Tv $Tv
    }
}
