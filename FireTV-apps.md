# Fire TV app inventory

Amazon Fire TV 4-Series (AFTTI43) Â· Fire OS 7.0 (Android 9) Â· 192.168.8.155 Â· 266 packages Â· read 2026-10-19

**Status (2026-10-19):** Tier 1 + Tier 2 hidden: 19 packages, still hidden after a reboot. Fire OS blocks hiding 4 of them: com.amazon.ftvads.deeplinking, com.amazon.device.metrics, com.amazon.device.crashmanager, com.amazon.device.logmanager. Tier 3, Downloader and AirPlay were kept.

Legend: **KEEP** = your apps or useful Â· **BLOAT** = safe to hide Â· **NEVER** = must not be removed Â· **LEAVE** = unclear Amazon internals, not worth the risk

## 1. Your apps â€” KEEP
| Package | App |
|---|---|
| com.netflix.ninja | Netflix |
| com.amazon.firebat | Prime Video |
| com.plexapp.android | Plex |
| com.spotify.tv.android | Spotify |
| com.stremio.one | Stremio |
| com.amazon.cloud9 | Silk Browser |

YouTube is **not installed** (get it from the Appstore if you want it).

## 2. Your call
| Package | App | Note |
|---|---|---|
| com.esaba.downloader | Downloader | Not on your list; probably how Stremio got sideloaded. Keep if you sideload. |
| com.apple.airplay.amazon.airplay | AirPlay | Keep if you cast from an iPhone/Mac. |

## 3. BLOAT â€” Tier 1: ads, shopping, unused Amazon apps (recommended)
| Package | What it is |
|---|---|
| com.amazon.shoptv.client | Amazon Shopping |
| com.amazon.shoptv.firetv.client | Amazon Shopping home tile |
| com.amazon.bueller.music | Amazon Music |
| com.amazon.bueller.photos | Amazon Photos |
| com.amazon.tahoe | Amazon Kids (FreeTime) |
| com.amazon.gamehub | Games hub |
| com.amazon.minitv.android.app | Amazon miniTV stub |
| com.amazon.ftvads.deeplinking | Opens ads when you click a banner |
| com.amazon.hybridadidservice | Advertising ID |
| com.amazon.kso.blackbird | "Special Offers" ad service |
| com.amazon.tv.acr | Content recognition: logs what's on screen, including HDMI and Live TV |
| com.amazon.tv.ftvambient | Ambient Experience |
| com.amazon.tv.democontent.provider | Store demo content |
| com.amazon.tmm.tutorial, com.amazon.storm.lightning.tutorial | Tutorials |

## 4. BLOAT â€” Tier 2: telemetry (optional, privacy)
com.amazon.device.metrics, com.amazon.tv.fw.metrics, com.amazon.wirelessmetrics.service, com.amazon.device.crashmanager, com.amazon.device.logmanager, com.amazon.dp.logger, com.amazon.perfcollection, com.amazon.neo.minerva.logcat

## 5. BLOAT â€” Tier 3: Alexa/smart-home extras (only if you have no Echo, Alexa calls or Matter devices)
| Package | What it is |
|---|---|
| com.amazon.whad, com.amazon.whasettings, com.amazon.wha.mediabrowserservice | Multi-room music with Echo |
| com.amazon.audiohome | Alexa Home Theater (Echo as TV speakers) |
| com.amazon.tv.matter, com.amazon.tv.mattercohost | Matter smart home |
| com.amazon.rtcsessioncontroller | Alexa calls / video calls |
| com.amazon.tv.alexaalerts, com.amazon.tv.alexanotifications | Alexa reminders and notifications |

Already disabled from the factory: com.amazon.comms.starktachyon (Alexa calling), com.android.nfc.

## 6. KEEP â€” useful features
| Package | Why |
|---|---|
| com.amazon.venezia | Appstore: Netflix, Plex, Spotify etc. get their updates here |
| com.amazon.vizzini, com.amazon.vizzini.ftvcds, com.amazon.alexadirectivebrokerservice, com.amazon.alexamediaplayer.runtime.ftv, com.amazon.alexa.datastore.app, com.amazon.alexa.externalmediaplayer.fireos, amazon.speech.davs.davcservice, com.amazon.spotify.mediabrowserservice | Voice search on the remote (and "Alexa, play on Spotify") |
| com.amazon.cast.sink | Screen mirroring |
| com.amazon.awvflingreceiver, com.amazon.dialservice | Casting from phone apps (Netflix, YouTube) |
| com.amazon.ftv.screensaver, com.android.dreams.basic | Screensaver |
| com.amazon.ftv.profilepicker | Profiles |
| com.amazon.tv.notificationcenter, com.amazon.tv.parentalcontrols, com.amazon.tv.parentalcontrols.overlay.localisation.common, com.amazon.android.settings | Notifications, parental PIN |
| com.amazon.tv.localgallery | Photos from USB |
| com.amazon.firetv.troubleshooting | Troubleshooting tile |
| com.amazon.hearingaid, com.ivona.orchestrator, com.ivona.tts.oem, com.svox.pico | Accessibility: hearing aids, VoiceView text-to-speech |
| com.amazon.device.software.ota, com.amazon.device.software.ota.override, com.amazon.tv.forcedotaupdater.v2, com.amazon.dcp.contracts.library | System updates (security fixes) |
| com.amazon.webview.chromium | Web view that many apps need |
| com.amazon.cpl | Wi-Fi sign-in pages (hotels) |
| com.amazon.media.recommendations | Content rows on the home screen |

## 7. NEVER REMOVE
**Live TV, antenna and HDMI inputs:** com.amazon.tv.livetv, com.amazon.mediatek.livetv, com.mediatek.tvinput, com.mediatek.tv.agent, com.mediatek.tvinputservice.arbitratorservice, com.amazon.franktvinput, com.amazon.tv.channelscan, com.amazon.tifobserver, com.amazon.providers.tv, com.android.providers.tv, com.amazon.tv.inputpreference.service, com.amazon.tv.routing, com.amazon.tv.devicecontrol, com.amazon.tv.devicecontrolsettings (HDMI-CEC / soundbar control), com.fireos.sdk.ftve.addp

**Remote, Bluetooth, keyboard:** com.android.bluetooth, com.android.bluetoothmidiservice, com.amazon.fireinputdevices, com.amazon.device.bluetoothkeymaplib, com.amazon.device.bluetoothpa, com.amazon.device.blepa, com.amazon.autopairservice, com.amazon.accessoryconfigservice, com.amazon.tv.keypolicymanager, com.amazon.tv.ime, com.android.companiondevicemanager

**Picture and sound:** com.amazon.tv.settings.mediatek, com.amazon.ftve.audioprovider, com.amazon.ftve.audioeffectsprovider, com.amazon.audiolatencymeasurer, com.amazon.tv.resolutioncycler, com.amazon.firetvledservice

**Settings and home screen:** com.amazon.tv.launcher, com.amazon.firehomestarter, com.amazon.tv.settings.core, com.amazon.tv.settings.v2, com.amazon.tv.settings.v2.overlay.localisation.common, com.amazon.tv.settingsprovider.configuration, com.amazon.tv.quicksettings, com.amazon.tv.quicksettings.ui, com.amazon.tv.oobe, com.amazon.tv.ooberesource, com.amazon.uxcontrollerservice, com.amazon.uxnotification, com.android.systemui, com.android.systemui.theme.dark, com.amazon.tv.intentsupport, com.amazon.tv.sysappsinternal.resources

**Amazon account, sign-in, sync:** com.amazon.identity.auth.device.authorization, com.amazon.dcp, com.amazon.dcp.contracts.framework.library, com.amazon.tcomm, com.amazon.tcomm.client, com.amazon.tcomm.jackson, amazon.jackson19, com.amazon.device.messaging, com.amazon.device.messaging.sdk.library, com.amazon.device.messaging.sdk.internal.library, com.amazon.device.settings, com.amazon.device.settings.sdk.internal.library, com.amazon.device.sync, com.amazon.device.sync.sdk.internal, com.amazon.sync.service, com.amazon.sync.provider.ipc, com.amazon.appaccesskeyprovider, com.amazon.android.marketplace, com.amazon.application.compatibility.enforcer, com.amazon.application.compatibility.enforcer.sdk.library, com.amazon.client.metrics.api (shared library many apps load), com.amazon.csm.htmlruntime

**Network and storage:** com.amazon.connectivitycontroller, com.amazon.net.smartconnect, com.amazon.wifilocker, com.amazon.kindleautomatictimezone, com.amazon.storage.manager, com.amazon.device.lowstoragemanager, com.amazon.ssm, com.amazon.ssmsys

**Core Android and Fire OS:** android, amazon.fireos, android.amazon.perm, android.ext.services, android.ext.shared, com.amazon.android.devicemodelinfo.resources, com.android.backupconfirm, com.android.bips, com.android.certinstaller, com.android.cts.ctsshim, com.android.cts.priv.ctsshim, com.android.defcontainer, com.android.documentsui, com.android.externalstorage, com.android.htmlviewer, com.android.internal.display.cutout.emulation.corner, com.android.internal.display.cutout.emulation.double, com.android.internal.display.cutout.emulation.tall, com.android.keychain, com.android.location.fused, com.android.managedprovisioning, com.android.onetimeinitializer, com.android.packageinstaller, com.android.pacprocessor, com.android.printspooler, com.android.providers.calendar, com.android.providers.contacts, com.android.providers.downloads, com.android.providers.downloads.ui, com.android.providers.media, com.android.providers.settings, com.android.providers.userdictionary, com.android.proxyhandler, com.android.se, com.android.settings, com.android.settings.intelligence, com.android.sharedstoragebackup, com.android.shell, com.android.simappdialog, com.android.statementservice, com.android.traceur, com.android.vpndialogs, com.android.wallpaperbackup, com.android.wallpapercropper, com.google.android.boot.appsplashscreen, com.amazon.dummy.alarmclock, com.amazon.dummy.calendar, com.amazon.dummy.contacts, com.amazon.dummy.gallery, com.amazon.dummy.music, com.amazon.dummy.settings (placeholders that stop apps crashing)

## 8. LEAVE â€” unclear Amazon internals
No reliable public info on what these do on this firmware. Hiding the wrong one can break the home screen or cause a boot loop, so they stay.

com.amazon.a4b.mcc, com.amazon.aca, com.amazon.adep, com.amazon.ahelite, com.amazon.aiondec, com.amazon.ale, com.amazon.alta.h2clientservice, com.amazon.aria, com.amazon.avls.experience, com.amazon.cardinal, com.amazon.ceviche, com.amazon.communication.discovery, com.amazon.connectivitydiag, com.amazon.device.details, com.amazon.device.rdmapplication, com.amazon.device_remote_config_sync_service, com.amazon.diode, com.amazon.dpcclient, com.amazon.fireos.cirruscloud, com.amazon.fireos.rescuepartycloud, com.amazon.firetv.androidskillconnectioncapability, com.amazon.ftv.xpicker, com.amazon.hedwig, com.amazon.imp, com.amazon.katoch, com.amazon.livedeviceservice, com.amazon.logan, com.amazon.naatyam, com.amazon.neo.minerva, com.amazon.neodelegate, com.amazon.neopactservice, com.amazon.ods.kindleconnect, com.amazon.perfc, com.amazon.platform.fdrw, com.amazon.prism.android.service, com.amazon.privacypassservice, com.amazon.providers.contentsupport, com.amazon.recess, com.amazon.securitysyncclient, com.amazon.sharingservice.android.client.proxy, com.amazon.sneakpeek, com.amazon.spiderpork, com.amazon.systemnotices, com.amazon.tv.consents, com.amazon.tv.csapp, com.amazon.tv.developer.dataservice, com.amazon.tv.easyupgrade, com.amazon.tv.ffsprovisioneeclient, com.amazon.tv.fss, com.amazon.tv.legal.notices, com.amazon.tv.legal.notices.overlay.localisation.common, com.amazon.tv.legalresources, com.amazon.tv.ottssocompanionapp, com.amazon.tv.releasenotes, com.amazon.tv.support, com.amazon.tv.turnstile, com.amazon.tv.website_launcher, com.amazon.whisperjoin.middleware.np, com.amazon.whisperlink.core.android, com.amazon.whisperplay.contracts, com.amazon.whisperplay.service.install, com.fireos.arcus.proxy, com.fireos.usagestats.proxy, com.amazon.comms.starktachyon (already disabled), com.android.nfc (already disabled)

## What hiding can't do
The big banner ads and "sponsored" rows on the home screen come from the launcher itself (com.amazon.tv.launcher), which has to stay. To cut them back, go to:
- **Settings â†’ Preferences â†’ Featured Content:** turn off video and audio autoplay.
- **Settings â†’ Preferences â†’ Privacy Settings:** turn off Device Usage Data, Collect App Usage Data and Interest-based Ads.
