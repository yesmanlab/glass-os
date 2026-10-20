# Project notes: Fire TV debloat + Glass OS launcher

Everything needed to pick this project up again. The target is an **Amazon Fire TV 4-Series (AFTTI43)** running Fire OS 7 (Android 9) at `192.168.8.155`, with an LCD panel, controlled over ADB from this Windows laptop.

To put Glass OS on a different TV, see [PORTING.md](PORTING.md).

Status as of 2026-10-19: debloated (19 packages hidden). **Glass OS** is the home screen; the Home button and boot both go to it.

---

## What's here

| Path | What it is |
|---|---|
| `debloat.ps1` | Hides (disables) every uncommented package in `bloat.txt`. Nothing is uninstalled. Refuses core TV packages. |
| `restore.ps1` | Re-enables everything `debloat.ps1` hid, plus everything named in `bloat.txt`. |
| `bloat.txt` | The hide list, in tiers. Tier 1 (ads, shopping) and Tier 2 (telemetry) are active; Tier 3 (Alexa extras) is commented out. |
| `hidden.log` | What `debloat.ps1` actually hid. `restore.ps1` reads it. |
| `docs/PORTING.md` | How to run Glass OS on other TVs (Android TV / Google TV, other Fire TVs, AOSP boxes) and why Tizen, webOS, Roku and tvOS can't host it. |
| `FireTV-apps.md` | All 266 packages on the TV, grouped and marked Keep / Bloat / Never remove / Leave. |
| `launcher/` | **Glass OS**, the custom home screen (see below). |
| `launcher/src/app/glasshome/Main.java` | The whole UI, about 1,300 lines. Plain Android views: no WebView, no libraries. |
| `launcher/src/app/glasshome/HomeWatcher.java` | Accessibility service that sends the Home button to Glass OS. |
| `launcher/src/app/glasshome/Keeper.java` | Switches HomeWatcher back on if Fire OS wipes the accessibility settings. Does nothing until it's granted `WRITE_SECURE_SETTINGS` (see Known limits). |
| `launcher/AndroidManifest.xml` | App manifest (package `app.glasshome`). |
| `launcher/build.ps1` | Builds `GlassHome.apk` without Gradle. `-Install` also pushes it to the TV. |
| `launcher/set-home.ps1` | Makes Glass OS the home screen; `-Undo` gives Home back to Amazon. |
| `launcher/stremio.key`, `launcher/tmdb.key` | API keys, bundled into the APK at build time. **Secrets: don't share or commit them.** |
| `launcher/debug.keystore` | Signing key. **Keep it.** Without it, a new build can't update the installed app. |
| `launcher/screenshots/` | Captures from the TV. |

## Tools on this laptop

- **ADB**: `%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_*\platform-tools\adb.exe` (installed with `winget install Google.PlatformTools`). Every script finds it automatically.
- **JDK 17**: `C:\Program Files\Microsoft\jdk-17.*` (winget `Microsoft.OpenJDK.17`).
- **Android SDK**: `%LOCALAPPDATA%\Android\Sdk`, with `platforms;android-34` and `build-tools;34.0.0`.
- If the TV refuses ADB after a factory reset, re-enable Developer options → ADB debugging, then accept the "Allow USB debugging" prompt on the TV.

## Everyday commands

Run these from the repository root:

```bash
powershell -ExecutionPolicy Bypass -File launcher\build.ps1 -Install
```
Rebuilds and installs Glass OS. It also compiles the app ahead of time for speed, and re-arms the Home button service if it was on.

```bash
powershell -ExecutionPolicy Bypass -File launcher\set-home.ps1 -Undo
```
Gives the Home button back to Amazon's launcher. Run it without `-Undo` to switch back to Glass OS.

```bash
powershell -ExecutionPolicy Bypass -File debloat.ps1
```
Re-hides the bloat. Run this after a Fire OS update. Add `-WhatIf` for a dry run.

```bash
powershell -ExecutionPolicy Bypass -File restore.ps1
```
Un-hides everything.

All scripts accept `-Tv <ip>:5555` if the TV's IP address changes.

---

## Glass OS: how it works

### Layout (1920×1080 design grid, scaled by `px()`)
- **Top row:** Explore / Movies / Shows tabs on the left (moving onto a tab switches it); greeting and 24-hour clock, Search (Stremio search), Bluetooth (Remotes & Bluetooth settings), Brightness (slider) and Settings (side panel) on the right.
- **Top Shelf:** title logo (trimmed HD PNG), then a liquid-glass **IMDb** badge, runtime, year, genre and Movie/Show, a three-line blurb, and three actions: **Open in Stremio**, **Watch Trailer** (opens the trailer in the YouTube app), and **+** to add to or remove from your Stremio library.
  - While an action button is focused, the carousel hides and the backdrop cycles through more HD backdrops of the title, from TMDB.
- **Carousel:** seven thumbnails exactly fill the width between the margins, each with its title on the image. Scrolling moves one thumb at a time so both edges stay aligned, and moving along it changes the shelf. It wraps around: ◀ on the first title jumps to the last, ▶ on the last jumps to the first. Nothing rotates on its own.
- **Dock (bottom):** **Continue**, Stremio, YouTube, Plex, Prime Video, Spotify.
  - **Continue** is your most recently played Stremio title, with a progress bar along the bottom and "S1 · E5 · 12m left" or "Up next".
  - Moving onto the dock scales it up slightly (4%) and dims the backdrop behind it by 41% (`shadeLevel()`).
  - The dock sits 44 px above the bottom edge, and only 18 px of the grid peeks out below the screen (`DOCK_TOP`, `GRID_TOP`).
  - Tile corners are concentric with the dock: the dock radius is `DOCK_R` = 48, every side is inset by `DPAD` = 20, so tiles use a 28 px radius.
  - **Rearrange:** hold OK on a dock app to pick it up, move it with ◀ ▶, then press OK or Back to drop it. The order is saved in the `dock` SharedPreferences and survives restarts and rebuilds. Continue always stays first. Because of this, OK now acts on release instead of on press.
- **Grid (below the dock, a sliver visible):** Netflix, Appstore, Silk, Downloader, AirPlay (shows a hint, because Fire OS won't let apps open it), and HDMI 1–4. Moving down from the dock lifts everything so the dock sits at the top of the screen (`DOCK_AT_TOP` = 64) with the grid below it. The shelf, top row and carousel move out of view, and lower rows scroll further only if they would fall off the bottom (`gridLift()`).

To change which apps appear in the dock, edit the `DOCK` and `MORE` arrays at the top of `Main.java`. The order is set on the TV by holding OK. `"a|b"` means the first one of those packages that's installed.

### Data
- **Discover:** `https://v3-cinemeta.strem.io/catalog/{movie|series}/top.json`, the same source as Stremio's Discover tab. Cached for 6 hours in SharedPreferences.
- **Your library:** `POST https://api.strem.io/api/datastoreGet` with `stremio.key`. It feeds unwatched titles into Explore and the Continue tile. **+** writes changes with `datastorePut`. Removing sets `removed: true`, so your watch history is kept.
- **Images:** backdrops are metahub "large" (3000 px), downloaded once, scaled to screen width and re-saved, so later loads are a quick decode. Logos are trimmed to their visible pixels.
- **Extra backdrops:** TMDB `find` (by IMDb id) then `images`, up to 5 textless backdrops per title, cached. Uses `tmdb.key`.
- **Trailer:** YouTube id from Cinemeta's `trailerStreams`, opened as `https://www.youtube.com/watch?v=<id>` in `com.amazon.firetv.youtube`.
- If you change the shape of a shelf item's JSON, bump `CACHE_VERSION`; that clears old caches on next launch.

### Home button and boot
Fire OS hard-wires Home to Amazon's launcher, and won't let it be disabled, force-stopped, or replaced with Android's default-home setting. `HomeWatcher` is an accessibility service that watches **only** window changes from `com.amazon.tv.launcher`. When Amazon's home appears, it opens Glass OS through `AlarmManager`; a system-performed launch avoids Android's 5-second app-launch freeze after Home. The result is that Home lands on Glass OS in about 130–300 ms, after a brief flash of Amazon's home. It's turned on by `set-home.ps1` (a secure setting) and survives reboots.

### Speed and memory, measured on the TV

| | Stock Amazon launcher | Glass OS |
|---|---|---|
| APK size | 27.3 MB | **45 KB** |
| Home pressed from an app | — | **128–163 ms** warm |
| Janky frames, scrolling carousel and dock | — | **0.1%** warm (about 5% in the first run after boot, while artwork downloads) |
| Memory, behind another app | about 113 MB | **about 100 MB** (all big images released while hidden) |

What keeps it fast, so don't undo these casually:
- No WebView and no libraries.
- `cmd package compile -m speed` (AOT) after every install.
- Focus animations are under 200 ms with shared interpolators.
- No looping animations, no shadows (elevation), no auto-rotation.
- The HD backdrop waits 200 ms after you stop moving, so fast scrolling skips the in-between images.
- Neighbouring backdrops are prefetched to disk only, never decoded, on a low-priority thread.
- `prepareToDraw()` uploads each bitmap to the GPU off the critical path.
- Tiles fully covered by artwork skip the glass fill underneath.
- Glass is a single 160×90 blurred bitmap that every glass surface samples. There's no live blur.
- `onTrimMemory(UI_HIDDEN)` drops every big bitmap while another app is in front.

### Burn-in care
The whole UI drifts a few pixels every 4 minutes, and the screen dims to near-black after 3 minutes idle (the first key press only wakes it). The panel is LCD, so this is just cheap insurance.

---

## Debloat details

- **Method:** `pm disable-user --user 0`. It's fully reversible with `pm enable`, and app data is kept.
- **Hidden (19):** Amazon Shopping (×2), Amazon Music, Amazon Photos, Amazon Kids, Games hub, miniTV, advertising ID, Special Offers ads (`kso.blackbird`), content recognition (`tv.acr`), Ambient Experience, demo content, two tutorials, and five telemetry/logging services.
- **Protected by Fire OS, so they can't be hidden without root:** `ftvads.deeplinking`, `device.metrics`, `device.crashmanager`, `device.logmanager`, and Amazon's launcher itself.
- **Never hide:** anything in section 7 of `FireTV-apps.md`, which covers Live TV and inputs, remote and Bluetooth, picture and sound, settings, account sign-in, and core Android. `debloat.ps1` refuses those even if they're added to `bloat.txt`.
- **Home-screen ads:** the remaining ads come from Amazon's launcher, which Glass OS now covers. For extra privacy, go to Settings → Preferences → Privacy and turn off Device Usage Data, App Usage Data and Interest-based Ads.

## Known limits

- **Fire OS can switch the Home service off.** At 20:58 on 2026-10-19, Fire OS reset the accessibility settings by itself, while its own `AccessibilityFSFProvider` was restarting, so Home went to Amazon's launcher.
  - Fixed by `Keeper.java`. Glass OS watches those settings and switches HomeWatcher back on immediately. It also checks at boot, after updates, and every 15 minutes. Tested by deleting the setting with Netflix in front: it was restored within a fraction of a second.
  - This uses `WRITE_SECURE_SETTINGS`, which `set-home.ps1` grants, and the flag `settings secure glasshome_home=1`. `set-home.ps1 -Undo` clears the flag and revokes the permission, so undo isn't fought.
  - If Home ever goes back to Amazon anyway, re-run `launcher\set-home.ps1`.
- **Picture and Sound menus:** Fire OS only lets its own launcher open them. Use the remote's ⚙ button, or hold Home.
- **Alexa voice button:** Glass OS doesn't intercept voice keys, and Alexa (`com.amazon.vizzini`) stays enabled. Hand-testing with the real remote is still to do.
- **AirPlay:** can't be opened by apps, but it's always listening. Cast from an iPhone or Mac.
- **Trailer video in the Top Shelf:** trailers are YouTube streams, so they open in the YouTube app instead of playing inline.
- **"Unable to Detect Your Remote" dialog:** Amazon shows this sometimes after reboots or after many ADB key presses. Press OK; it's harmless.
- **Before automating remote presses:** check the screen first. Every test script here refuses to send keys unless Glass OS is in front.

## Ideas for next time

- Weather in the top-right pill (Open-Meteo, no key needed).
- An "Up next" row of more in-progress Stremio titles, not just the latest one.
- Per-profile Stremio keys, if more people use the TV.
- Moving apps between the dock and the grid by holding OK.
