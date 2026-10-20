# Porting Glass OS to other TVs

Glass OS was built for one TV: an Amazon Fire TV 4-Series (AFTTI43) on Fire OS 7, which is Android 9. This guide covers how to run it on other TVs and streaming boxes: what carries over unchanged, what's specific to Fire OS, and what to change on each platform.

The short answer:

| Platform | Can Glass OS be the home screen? | Effort |
|---|---|---|
| **Android TV / Google TV** (Sony, TCL, Hisense, Philips, Xiaomi, Nvidia Shield, Chromecast with Google TV, Google TV Streamer, onn) | **Yes, properly.** Android lets a third-party app be the real HOME app, so no accessibility workaround is needed. | Low |
| **Other Fire TV devices** (sticks, Cubes, Omni / 2-Series / 4-Series TVs) on Fire OS 6–7 | Yes, with the same accessibility workaround used here. | Very low |
| **Fire TV on Fire OS 8** (Android 11 base, newer sticks and TVs) | Probably, with the same workaround, plus Android 11 changes. Amazon has tightened launcher replacement on newer firmware. | Medium; test first |
| **Fire TV on Vega OS** (Amazon's Linux-based OS, not Android) | **No.** Android apps don't run there. | — |
| **Generic AOSP boxes** (Android 7–12 "TV boxes") | Yes, usually the easiest of all. | Low |
| **Samsung Tizen, LG webOS, Roku, Apple tvOS, Hisense VIDAA, Vizio SmartCast** | **No.** None let an app replace the home screen. The design could be rebuilt as a regular app, but you'd still land on their home screen. | High; not a launcher |

The rest of this guide assumes an **Android-based** TV, which covers almost every case where Glass OS can actually be the home screen.

---

## 1. How Glass OS is built

Glass OS is deliberately one plain Android app with no dependencies, so it ports by editing constants rather than rewriting.

```
launcher/
├── AndroidManifest.xml          package app.glasshome; HOME + LEANBACK_LAUNCHER intents; permissions
├── build.ps1                    aapt2 → javac → d8 → zipalign → apksigner (no Gradle), then AOT compile on the TV
├── set-home.ps1                 makes it the home screen (Fire OS workaround; see §4)
├── src/app/glasshome/
│   ├── Main.java                the entire UI (~1,700 lines, plain Android views)
│   ├── HomeWatcher.java         accessibility service: Amazon home appears → open Glass OS (Fire OS only)
│   └── Keeper.java              re-enables HomeWatcher if Fire OS wipes accessibility settings (Fire OS only)
├── stremio.key / tmdb.key       optional API keys, bundled into the APK at build time
└── debug.keystore               signing key; keep it, or updates won't install over the existing app
```

### What each part of `Main.java` does

| Section (search for the comment banner) | Portable? | Notes |
|---|---|---|
| Constants at the top (`DOCK`, `MORE`, `SETTINGS`, layout sizes) | **Edit per device** | Package names and settings actions differ by platform; see §3. |
| `// ---- build ----` (layout) | Yes | Designed on a 1920×1080 grid; `px()` scales to whatever the screen reports. |
| `// ---- data: Stremio Discover ...` | Yes | Plain HTTPS to Cinemeta, the Stremio API and TMDB. Works on any device with internet. |
| `// ---- Top Shelf ...`, carousel, gallery | Yes | |
| `// ---- focus ----` | Yes | Custom D-pad focus engine; doesn't rely on Android's focus search. |
| Dock rearranging (`startMove` / `swap` / `endMove`) | Yes | Needs the remote's OK key to auto-repeat while held. Every Android TV does. |
| Brightness (`stepBrightness`) | **Varies** | Uses Android system brightness, which drives the backlight on this TV. Many TVs ignore it (see §5). |
| HDMI inputs (`inputs()`) | **Varies** | Android TV Input Framework. Works on TVs, but streaming sticks and boxes have no HDMI inputs, so the tiles simply don't appear. |
| `launchIntent()`, `start()`, `open()` | Yes | Prefers the TV (leanback) launch intent, falling back to the normal one. |
| Images (`image()`, `load()`, `prefetch()`) | Yes | Memory and disk cache. Backdrops are scaled once to screen width. |
| `onTrimMemory` image release | Yes | Matters more on 1–1.5 GB devices. |
| `HomeWatcher` + `Keeper` | **Fire OS only** | Delete them, or leave them unregistered, on Android TV (§4). |

---

## 2. Before you start: put the target device on ADB

Every platform needs ADB first.

1. **Turn on Developer options.**
   - **Android TV / Google TV:** Settings → System → About → click **Build** (or **Android TV OS build**) 7 times.
   - **Fire TV:** Settings → My Fire TV → About → click the device name 7 times.
2. **Turn on debugging.** In Developer options, turn on **USB debugging** (Android TV) or **ADB debugging** (Fire TV). For Wi-Fi ADB on Android 11+ Google TV, use **Wireless debugging** and pair with `adb pair <ip>:<port>`.
3. **Connect from the laptop:**
   ```bash
   adb connect 192.168.x.x:5555
   ```
   Then accept the "Allow debugging?" prompt on the TV. Tick **Always allow from this computer**.
4. **Record the device facts**; you'll need them below:
   ```bash
   adb shell "getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; wm size; wm density"
   ```
   ```bash
   adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   ```
   The second command lists every app that can be the home screen. The stock launcher is in there.

Every script in this project takes `-Tv <ip>:5555`, so nothing needs hard-coding.

---

## 3. Port checklist (any Android TV)

Work through this in order. Each step is a small edit, mostly in the constants at the top of `Main.java`.

### 3.1 App IDs in the dock and grid

Package names differ between platforms. To find the ones installed on the new device:

```bash
adb shell pm list packages -3
```
Then check each one opens from the TV launcher:
```bash
adb shell cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER -a android.intent.action.MAIN <package>
```

Common equivalents:

| App | Fire TV | Android TV / Google TV |
|---|---|---|
| YouTube | `com.amazon.firetv.youtube` | `com.google.android.youtube.tv` |
| Prime Video | `com.amazon.firebat` | `com.amazon.amazonvideo.livingroom` |
| Netflix | `com.netflix.ninja` | `com.netflix.ninja` |
| Plex | `com.plexapp.android` | `com.plexapp.android` |
| Spotify | `com.spotify.tv.android` | `com.spotify.tv.android` |
| Stremio | `com.stremio.one` | `com.stremio.one` |
| App store | `com.amazon.venezia` | `com.android.vending` (Play Store) |
| Browser | `com.amazon.cloud9` (Silk) | none built in; use whatever you install |
| Disney+ | `com.disney.disneyplus` | `com.disney.disneyplus` |

`DOCK` entries accept `"a|b"` meaning "the first of these that's installed". That's how one build serves several devices, for example `"com.amazon.firetv.youtube|com.google.android.youtube.tv"`. Use the same trick for the others:

```java
static final String[] DOCK = {"com.stremio.one", "com.amazon.firetv.youtube|com.google.android.youtube.tv", "com.plexapp.android",
    "com.amazon.firebat|com.amazon.amazonvideo.livingroom", "com.spotify.tv.android"};
static final String[] MORE = {"com.netflix.ninja", "com.amazon.venezia|com.android.vending", ...};
static final String STREMIO = "com.stremio.one", YOUTUBE = "com.amazon.firetv.youtube|com.google.android.youtube.tv";
```
`YOUTUBE` is used by the **Watch Trailer** button. If you give it alternatives like this, also change `open(...)` to try each package, the same way `appTile()` does.

Apps that aren't installed are skipped automatically; nothing crashes.

**Quirks:**
- **AirPlay:** Fire OS doesn't let apps open AirPlay, so its tile shows a hint instead (search for `com.apple.airplay` in `appTile()`). On other platforms, delete that special case if the app opens normally.
- **Icon-only tiles:** `ICON_ONLY` lists apps whose TV banner looks poor, so they show a centred icon instead. Adjust it to taste.

### 3.2 Settings shortcuts

`SETTINGS`, and the Bluetooth button (`bBt`), use **Fire OS-only** intent actions (`com.amazon.device.settings.action.*`), because Fire OS blocks third-party apps from opening most of its settings screens directly.

On Android TV, use the standard Android actions:

```java
static final String[][] SETTINGS = {
    {"Network", android.provider.Settings.ACTION_WIFI_SETTINGS},
    {"Bluetooth & remotes", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS},
    {"Display & sound", android.provider.Settings.ACTION_DISPLAY_SETTINGS},
    {"Apps", android.provider.Settings.ACTION_APPLICATION_SETTINGS},
    {"Device", android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS},
    {"All settings", android.provider.Settings.ACTION_SETTINGS},
};
```
Point the Bluetooth button at `ACTION_BLUETOOTH_SETTINGS`.

Always check that each one actually opens on the device:
```bash
adb shell am start -a android.settings.BLUETOOTH_SETTINGS
```
A "Permission Denial" in `adb logcat` means that vendor protects the screen; drop it from the list. Also update the note at the bottom of the Settings panel, which mentions Fire TV's ⚙ button.

### 3.3 Manifest changes for Android 11 and newer (API 30+)

Newer Android hides other apps from yours unless you declare it. Without this, `getLeanbackLaunchIntentForPackage()` returns null and **the dock comes up empty**. Add to `AndroidManifest.xml`, inside `<manifest>`:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="https" />
    </intent>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="stremio" />
    </intent>
</queries>
```

Then, in `build.ps1`, raise the target to the device's API level, from `--target-sdk-version 28` to for example `30`, `31` or `34`.

Code changes when you raise the target:
- **API 31+:** `PendingIntent` needs `FLAG_IMMUTABLE`. In `HomeWatcher` and `Keeper`, use `PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE`.
- **API 31+:** every `<activity>`, `<service>` and `<receiver>` that has an `<intent-filter>` needs `android:exported` set explicitly. Use `exported="true"` on `Main` and `HomeWatcher`, and `exported="false"` on `Keeper`.
- **API 33+:** `onBackPressed` is deprecated. Glass OS already handles Back in `dispatchKeyEvent`, so nothing else is needed.
- **API 34+:** exact alarms need `SCHEDULE_EXACT_ALARM`. Only `HomeWatcher` uses one, and it isn't needed on Android TV (§4).

Raising `--min-sdk-version` isn't required. Keep it at 28 unless you need newer APIs.

### 3.4 Screen size and density

`px()` scales everything from a 1920-wide design, so layout carries over to any resolution. Check two things:
- **4K devices that report a 3840-wide UI** (rare; most report 1920): backdrops are scaled to `widthPixels`, so they'd take four times the memory. If `wm size` shows 3840, cap the backdrop width at 1920 in `load()`.
- **720p devices** (cheap sticks): `wm size` shows 1280×720. It still works; text is just smaller in physical terms.

### 3.5 Build and install

```bash
powershell -ExecutionPolicy Bypass -File launcher\build.ps1 -Install -Tv 192.168.x.x:5555
```
Then open it as a normal app first, to check it before it becomes the home screen:
```bash
adb shell am start -n app.glasshome/.Main
```

---

## 4. Making it the home screen, by platform

This is the part that differs most.

### 4.1 Android TV / Google TV: use the real HOME mechanism

Glass OS already declares `android.intent.category.HOME`, so Android treats it as a launcher. Delete or ignore `HomeWatcher` and `Keeper`; you don't need them here.

**Option A: the system picker** (some AOSP and Android TV builds).
Uninstalling or disabling nothing, press Home. If Android shows "Select a home app", pick **Glass Home → Always**. If no picker appears, use B.

**Option B: set it over ADB.**
```bash
adb shell cmd package set-home-activity app.glasshome/.Main
```
This works on AOSP-based boxes, the Nvidia Shield, and most Android TV 9–11 devices. Check it by pressing Home.

**Option C: disable the stock launcher** (Google TV, and stubborn Android TV builds). Google TV ignores other launchers while its own is enabled.

1. Find the stock launcher package with `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`. Common ones:
   - Google TV: `com.google.android.apps.tv.launcherx`
   - Android TV (older): `com.google.android.tvlauncher`
   - Google TV setup helper that can bring the stock launcher back: `com.google.android.tungsten.setupwraith`
2. Disable it, for example on Google TV:
   ```bash
   adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
   ```
   ```bash
   adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
   ```
   This never uninstalls, so it's fully reversible.
3. Press Home. Glass OS is now the only HOME app.
4. To undo:
   ```bash
   adb shell pm enable com.google.android.apps.tv.launcherx
   ```
   And the same for `setupwraith`.

**What you lose by disabling the Google TV launcher:**
- The Google TV recommendations and Live tab.
- On some models, the Assistant/voice button's on-screen results UI. Voice search may still work through the Google app; test it.
- Some TVs' input switcher, if the vendor built it into the launcher. Glass OS's HDMI tiles cover this on TVs with TIF inputs.

Write your version of `set-home.ps1` as these few `adb` lines with an `-Undo` switch, the same structure as the Fire OS one.

**Notes:**
- Android 12+ may kill the old launcher's process when it's disabled. That's expected.
- After a **system update**, check that Home still goes to Glass OS. Some vendors re-enable their launcher on update; re-running your set-home script fixes it.

### 4.2 Fire TV, Fire OS 6–7 (what this project does)

Amazon's launcher (`com.amazon.tv.launcher`) is a **protected package**: Fire OS refuses `pm disable-user`, `force-stop`, and `set-home-activity`. So Glass OS uses a workaround:

1. **`HomeWatcher`** (accessibility service) watches window changes from `com.amazon.tv.launcher` only. When Amazon's home appears, it starts Glass OS through `AlarmManager`. A system-performed launch skips Android's 5-second "no app launches after Home" freeze, so you get only a brief flash of Amazon's home.
2. **`Keeper`** puts `HomeWatcher` back if Fire OS wipes the accessibility settings. That has happened at least once on its own, while `AccessibilityFSFProvider` was restarting. It needs `WRITE_SECURE_SETTINGS`, granted once over ADB, plus a flag (`settings secure glasshome_home=1`) so that `-Undo` isn't fought.
3. **`set-home.ps1`** turns all of that on, and grants `WRITE_SETTINGS` for the brightness slider. `-Undo` reverses everything.

On another Fire TV running Fire OS 6–7, `set-home.ps1 -Tv <ip>:5555` should work as-is. Check two things:
- The launcher's home activity is still `.ui.HomeActivity_vNext`. `HomeWatcher` matches on that class name. Find it with:
  ```bash
  adb shell dumpsys activity activities | grep -m1 mResumedActivity
  ```
  Run that while on Amazon's home screen. If it differs, update the `endsWith(...)` in `HomeWatcher`.
- Fire TV sticks and Cubes have no HDMI inputs. The HDMI tiles just won't appear, which is fine.

### 4.3 Fire TV, Fire OS 8 (Android 11 base)

Apply everything in §3.3 (queries, exported, `FLAG_IMMUTABLE`), then try the Fire OS 7 approach. Amazon has been tightening launcher replacement on newer firmware, so before investing time:

1. Install, run `set-home.ps1`, then press Home from inside an app.
2. If Amazon's home stays put, look at `adb logcat | grep -iE "HomeWatcher|glasshome|BAL|background activity"`. "Background activity launch blocked" means the `AlarmManager` bounce is being refused. Accessibility services are normally exempt, but vendors can change that.
3. If it can't be made to work, Glass OS still runs fine as a regular app. Pin it to the front of Amazon's app row.

### 4.4 Generic AOSP TV boxes

Usually the simplest case. Install, press Home, and pick Glass Home in the picker, or use `set-home-activity`. Many boxes also ship a "Launcher" setting under Settings → Apps → Default apps.

### 4.5 Non-Android TV platforms

| Platform | Why it can't be a launcher | What's possible |
|---|---|---|
| Samsung Tizen | Apps are sandboxed web apps; the home bar belongs to the system. | Rebuild the UI as a Tizen web app (HTML/JS). It opens as an app, not as Home. |
| LG webOS | Same model. The home launcher is replaceable only on rooted ("Homebrew Channel") sets. | A webOS web app. On rooted sets, homebrew launcher replacements exist; that's out of scope here. |
| Roku | Channels are BrightScript/SceneGraph; Home is fixed. | A Roku channel with the same layout. |
| Apple tvOS | No launcher replacement. | A SwiftUI app; tvOS already has the look Glass OS imitates. |
| Hisense VIDAA, Vizio SmartCast | Closed platforms. | None practical. |

The cheapest way to get Glass OS on any of those TVs is often a **$30–50 Android TV / Google TV stick** on an HDMI input, with CEC turned on so the TV switches to it at power-on.

---

## 5. Device-specific features

### Brightness slider
Glass OS writes `Settings.System.SCREEN_BRIGHTNESS`. On this Fire TV that moves the panel backlight, which we verified via `dumpsys display`.

- **Android TV sets** often ignore it and use their own picture settings. Test: `adb shell settings put system screen_brightness 30`, then look at the screen and run `adb shell dumpsys display | grep -i brightness`.
- **Streaming sticks and boxes** have no backlight, so the slider does nothing.
- If it doesn't work, hide the button: remove `bBright` from the top row in `buildTop()` and `indexRows()`. Or point it at the vendor's picture settings intent if one opens.
- `set-home.ps1` grants the needed `WRITE_SETTINGS` with `appops set app.glasshome WRITE_SETTINGS allow`. Keep that line on any platform where the slider works.

### HDMI input tiles
These use Android's TV Input Framework (`TvInputManager`, `TYPE_HDMI`) and open an input with `TvContract.buildChannelUriForPassthroughInput(id)`. That works on Android TV **TVs**.

- Some vendors handle passthrough URIs with their own Live TV app, so test that each tile switches.
- Inputs named by HDMI-CEC (e.g. "PlayStation 5") show automatically.
- Sticks and boxes have no inputs, so the tiles are simply absent.

### Picture and Sound menus
On Fire OS, only Amazon's launcher may open them, so Glass OS points to the remote's ⚙ button. On Android TV, `Settings.ACTION_DISPLAY_SETTINGS` and `ACTION_SOUND_SETTINGS` usually open fine; add them to `SETTINGS`. Some vendors (Sony, TCL) have their own picture-menu intents; find them with `adb shell dumpsys package <vendor settings package> | grep -i action`.

### Voice / Assistant button
Glass OS never intercepts voice, media or volume keys (`dispatchKeyEvent` returns early for anything that isn't D-pad, OK or Back). Alexa on Fire TV and Google Assistant on Android TV keep working. On Google TV, disabling the stock launcher (§4.1 C) can affect the Assistant's results screen, so test it.

### Stremio, TMDB and Cinemeta data
These are device-independent; the same keys work on any TV. `stremio.key` is your Stremio account's auth key. Treat it like a password, and get a fresh one if the device will be shared:
1. Sign in at web.stremio.com.
2. In the browser console, run `JSON.parse(localStorage.getItem("profile")).auth.key`.

---

## 6. Debloating other TVs

The method in `debloat.ps1` and `restore.ps1` (`pm disable-user --user 0`, always reversible with `pm enable`) works on any Android TV. **The package list in `bloat.txt` is Fire TV-only.** Build a new one per device:

1. **Dump everything:**
   ```bash
   adb shell pm list packages -f > packages.txt
   ```
2. **Sort into Keep / Bloat / Never remove / Leave**, like `FireTV-apps.md`. Never disable anything that handles:
   - Live TV / tuner / TV inputs (`*.tvinput*`, `*.livetv*`, `*.tv.settings*`, vendor `*mediatek*` / `*realtek*` / `*mstar*` packages)
   - Bluetooth, the remote, keyboard / IME
   - System UI, settings, package installer, Google Play Services (`com.google.android.gms`, which many apps need)
   - Account sign-in
3. **Typical safe bloat on Google TV:** vendor ad and recommendation services, "Free TV" channel apps, shopping apps, demo/retail mode, games hubs, and vendor telemetry. Check names with care. For example, `com.google.android.tvrecommendations` feeds rows in the stock launcher, but nothing in Glass OS.
4. **Update `$never` in `debloat.ps1`** with the new device's critical package patterns before running it. It's a regex guard that refuses to disable matching packages even if they're in the list.
5. **Dry-run first:**
   ```bash
   powershell -ExecutionPolicy Bypass -File debloat.ps1 -WhatIf -Tv <ip>:5555
   ```
   Then run it for real, reboot, and check the TV still works: Live TV, every HDMI input, remote and Bluetooth, picture and sound.
6. **"Protected package" errors** mean the vendor blocks disabling that package. Comment it out in `bloat.txt` with a note, as was done for four Fire OS packages.

Never use `pm uninstall`. `disable-user` gets the same result and is reversible without a factory reset.

---

## 7. Performance rules (keep these on any device)

Glass OS beats the stock Fire TV launcher on size and speed because of these choices. Most target devices have 1.5–2 GB of RAM and slow GPUs, so keep them when porting.

1. **No WebView, no libraries.** Plain Android views keep the APK around 50 KB and avoid a second renderer process.
2. **Ahead-of-time compile after install:** `cmd package compile -m speed -f app.glasshome`. `build.ps1 -Install` does it.
3. **No shadows (`elevation`), no live blur, no looping animations.** Glass is one 160×90 blurred bitmap that every glass surface samples.
4. **Short focus animations** (≤ 220 ms) using shared interpolators.
5. **Debounce heavy work while scrolling.** The HD backdrop loads 200 ms after you stop on a title, and neighbours are prefetched to disk only, never decoded.
6. **`bitmap.prepareToDraw()`** after decoding, so the GPU upload happens off the frame that shows it.
7. **Release memory when hidden.** `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` drops every large bitmap, and returning shows the blurred placeholder at once.
8. **Skip invisible drawing.** Tiles fully covered by artwork skip the glass fill underneath (`opaque`).

To measure on a new device, using the same method as on the Fire TV:
```bash
adb shell dumpsys gfxinfo app.glasshome reset
```
Navigate for 10–15 seconds, then read the results:
```bash
adb shell dumpsys gfxinfo app.glasshome
```
Aim for under 2% janky frames once warm.

Memory, while another app is in front:
```bash
adb shell dumpsys meminfo app.glasshome
```
Look at the `TOTAL` line, and compare with the same command for the stock launcher's package.

---

## 8. Testing a port

Follow the same routine used here. It catches the platform differences quickly.

1. **Before sending any key presses, check what's on screen**, so you don't interrupt someone:
   ```bash
   adb shell "dumpsys window windows | grep mCurrentFocus"
   ```
   Only send keys when Glass OS, the stock launcher, or the screensaver is in front.
2. **Screenshot every screen, critique it like a designer, fix, repeat:**
   ```bash
   adb shell screencap -p /sdcard/s.png
   ```
   ```bash
   adb pull /sdcard/s.png
   ```
   Cover: home, carousel at both ends, Top Shelf buttons, each tab, the grid, brightness, settings, and a dock app being moved.
3. **Launch check.** Press OK on every tile and button, then confirm the expected app is in front with the same `mCurrentFocus` command. The Fire TV run passed 17 of 18; the one failure was AirPlay, which Fire OS blocks.
4. **Home check.** Open another app, press `adb shell input keyevent KEYCODE_HOME`, and check that Glass OS is in front within a second.
5. **Reboot check.** Reboot, and confirm Glass OS comes up on its own and the debloat survived.
6. **Real-remote check.** Things ADB can't test: voice button, long-press OK to rearrange, holding Home.

---

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Dock is empty | Android 11+ package visibility | Add the `<queries>` block (§3.3). |
| A tile does nothing | Package name differs on this platform | Check with `pm list packages`, then use `"a|b"` alternatives. |
| Settings item does nothing | Vendor blocks that intent | Look for "Permission Denial" in `adb logcat`; remove the item or use another action. |
| Home still goes to the stock launcher (Android TV) | Stock launcher still enabled | §4.1 option B or C. |
| Home goes to Amazon (Fire TV) | Accessibility settings wiped | Check `settings get secure enabled_accessibility_services`. `Keeper` should restore it; if not, re-run `set-home.ps1`. |
| "Can't install, signatures don't match" | Different `debug.keystore` | Use the original keystore, or uninstall the old app first (this loses its saved dock order). |
| Brightness slider does nothing | Panel ignores system brightness | Hide the button (§5). |
| Blank backdrops | No internet, or TMDB/metahub blocked | Glass OS shows cached art. Check DNS and ad-blocking on the network. |
| Stuttering on first launch | Artwork downloading and decoding | Normal for the first minute; it should settle under 2% jank once warm. |
| Continue tile missing | No `stremio.key` at build time | Add the key, then rebuild. |

---

## 10. Quick-start: porting to a Google TV stick in 15 minutes

1. Turn on Developer options and debugging, then `adb connect <ip>:5555` (§2).
2. In `Main.java`, set `DOCK` / `MORE` / `YOUTUBE` to the Google TV package names (§3.1), and replace `SETTINGS` and the Bluetooth action with standard Android ones (§3.2).
3. Add the `<queries>` block to the manifest, and set `android:exported` on `Main`, `HomeWatcher` and `Keeper` (§3.3). Change `--target-sdk-version` in `build.ps1` to the device's API level.
4. Remove the `HomeWatcher` `<service>` and the `Keeper` `<receiver>` from the manifest, and the `Keeper.ensure` / `Keeper.schedule` lines in `Main.onCreate`.
5. Build and install:
   ```bash
   powershell -ExecutionPolicy Bypass -File launcher\build.ps1 -Install -Tv <ip>:5555
   ```
6. Make it home:
   ```bash
   adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
   ```
   Then press Home.
7. Run the tests in §8.
