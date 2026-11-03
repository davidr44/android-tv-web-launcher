# Operating the launcher

Everything here runs from a PC with `adb` connected to the TV over the network: turn on ADB debugging in the TV's Developer options, then `adb connect <TV address>` and accept the prompt on the TV. Every command that changes the TV has its undo next to it.

## Where to change things

| What | File |
| --- | --- |
| Dock apps (order, names, the PS5's HDMI port), which installed app shows as each entry, what counts as watching | `web/apps.js` (personal overrides in `web/apps.local.js`, kept out of git) |
| Apps hidden from All apps, colour icons, TV inputs listed with the apps | `web/device.js` (`HIDDEN`, `ICONS`, `INPUTS`) |
| Look | `web/style.css`; backgrounds in `web/assets/wallpapers` and `web/assets/materials` |
| Dock marks | `web/assets/icons/<id>.svg` (the file name is the entry's `id`) |
| Opening apps, HDMI inputs, live TV, HDMI signal detection | `app/src/pt/inicio/launcher/Bridge.java` |
| Verse of the day | YouVersion: `VerseOfTheDay.java`; offline list: `web/assets/verses.js` (made by `tools/verses.py`) |
| Watch time | `WatchTime.java` (usage log, HDMI log) and `OnScreen.java` (spans, tested in `app/test`) |
| Screensaver | `web/dream.html`, `dream.css`, `dream.js` |
| Frosted glass at 960×540 (less GPU memory) | `tools/shrink_materials.py`, after generating new materials |

## Build and test

Needs a JDK 17+ and Python 3. The first build downloads three jars from Maven Central into `tools/cache` and checks their SHA-256.

```
python3 tools/test.py                               # plain-Java tests, no Android needed
INICIO_KEY_PASSWORD=... python3 tools/build.py      # makes Inicio.apk
```

The signing key (`inicio.keystore`) is not in the repository. Without one, the build makes a new key with that password. Keep the keystore and its password: Android only installs an update signed with the same key, so a new key means uninstalling first (which loses the app's settings).

## Install, make it the home screen, undo

```
adb install -r Inicio.apk          # install or update (one app, changes nothing else)
adb uninstall pt.inicio.launcher   # remove
```

The Google TV launcher on this Android 10 TV has system priority and beats any preferred home screen, so it has to be disabled (not uninstalled), together with its recommendations service:

```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell pm disable-user --user 0 com.google.android.tvrecommendations
adb shell cmd package set-home-activity pt.inicio.launcher/.HomeActivity
```

Undo:

```
adb shell pm enable com.google.android.tvlauncher
adb shell pm enable com.google.android.tvrecommendations
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
```

Without a PC: *Settings → Apps → See all apps → Show system apps → Android TV Home → Enable*.

Inside the launcher, *Settings → TV → Original Home* opens the factory home screen while it is still enabled.

Screensaver: on the TV, *Settings → Device Preferences → Screen saver → Wallpapers & verse*. To go back, choose *Backdrop* (Ambient Mode) there.

## Watch time

The first of the top-right controls ("3h 12m today"; OK opens the split per app). It reads Android's own usage log, which needs a one-time permission:

```
adb shell appops set pt.inicio.launcher GET_USAGE_STATS allow     # on
adb shell appops set pt.inicio.launcher GET_USAGE_STATS default   # off (the control disappears)
```

Sony's TV app shows both the antenna channels and the HDMI inputs, so its time is split into TV and PS5 using a log of when the PS5's HDMI port had a signal. The home screen itself never counts.

## Verse of the day (YouVersion)

The same verse of the day as the YouVersion Bible app and bible.com, in the Berean Standard Bible, from YouVersion's official Platform API, fetched once a day in the background. It needs a free app key from platform.youversion.com, which is stored only on the TV:

```
adb shell am start -n pt.inicio.launcher/.HomeActivity --es yvp_key YOUR_KEY   # save
adb shell am start -n pt.inicio.launcher/.HomeActivity --es yvp_key ""         # remove
```

Without a key or without internet, it uses the offline list in `web/assets/verses.js`. The screensaver shows the same verse.

If the first fetch after an update fails with `EPERM` in logcat although the INTERNET permission shows as granted, restart the TV. On this TV, the INTERNET permission that an update added only reached the app's network sockets after a reboot.

## Debloat

14 promotion, demo, telemetry and unused extra apps, disabled with `pm disable-user` (nothing uninstalled). The commands and their undo are in `tools/debloat.txt`.

Not touched: live TV, recordings and HbbTV, HDMI and HDMI-CEC, system updates, Google services and the Play Store, the voice assistant, and Sony's web runtime, which other parts of the TV may use.

## Factory state of the TV (read on 21 October 2026)

- Home screen: `com.google.android.tvlauncher/.MainActivity`
- Screensaver: `com.google.android.backdrop/.Backdrop`
- Already disabled before this project (leave alone): `com.sony.dtv.seeds.iot`, `com.sony.dtv.b2b.vendorprotocol`, `com.youview.poa`, `com.sony.dtv.smarthomesettings`, `com.sony.dtv.b2b.softap`, `screnmirroring.com`
- HDMI 1–4 are `com.sony.dtv.tvinput.external/.ExternalTvInputService/HW2` to `HW5`. The PS5 is on HDMI 3 (side panel).
