# TV Web

A remote-first web browser for Android TV. One screen of shortcuts, a toolbar, tabs, an ad blocker
that is on by default, and video that plays without hunting for a button.

The TV browser category is full of ports of phone browsers: touch targets, address bars, popups and
ads. This one assumes a D-pad and nothing else.

## What a person notices

- The start page is five tiles and four buttons. Everything is reachable with the arrow keys.
- Arrow keys scroll the page. The toolbar is one press of `MENU` away and `BACK` returns to it.
- `OK` taps the middle of the screen, or submits the field you are typing in, or clicks the element
  the page has focused.
- A video in the page turns on a `Watch` button that hides the toolbar and fills the screen.
- Ads and trackers are dropped before they load, so pages are lighter than in Chrome on the same box.
- Streaming services that need DRM say so in plain words and offer to open the installed TV app
  instead of showing a black rectangle.

## Build

Requires JDK 17 and an Android SDK with platform 37 and build-tools 36.

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
```

This repo was built with Gradle 9.6.0 and AGP 9.4.0. AGP 9 brings its own Kotlin plugin, so there is
no `org.jetbrains.kotlin.android` plugin in the build files and adding one fails the build.

Release build, with R8 shrinking the 3.2 MB debug APK to a 631 KB release APK:

```bash
./gradlew :app:assembleRelease
```

The release APK is unsigned. Sign it with your own key before installing on a TV.

## Run it on an Android TV emulator

```bash
sdkmanager "system-images;android-34;android-tv;x86"
avdmanager create avd -n tv34 -k "system-images;android-34;android-tv;x86" -d "tv_1080p"

# The device profile sometimes lands at 320x640. Force 1080p in config.ini.
sed -i 's/^hw.lcd.width.*/hw.lcd.width = 1920/; s/^hw.lcd.height.*/hw.lcd.height = 1080/; s/^hw.lcd.density.*/hw.lcd.density = 320/' \
  ~/.config/.android/avd/tv34.avd/config.ini

emulator -avd tv34 -no-window -no-audio -gpu swiftshader_indirect &
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.sxch.tvweb/.MainActivity
```

`tools/drive.sh` wraps the adb calls used to drive the UI, and `tools/verify.sh` runs the checks below.
Both take `SERIAL`, defaulting to `emulator-5554`.

```bash
source tools/drive.sh
start_app
open_url "https://en.wikipedia.org/wiki/Television"
shot wikipedia
```

## Verified on the emulator

`tools/verify.sh` runs these against a running Android TV 14 emulator and reads state off the device
rather than trusting the UI. Last run: 9 passed, 0 failed.

| Check | How it is proven |
| --- | --- |
| App is foreground | `dumpsys activity activities` names `dev.sxch.tvweb/.MainActivity` |
| Page loads and is recorded | page URL appears in `history` in the app database |
| Ad block toggle persists | `shared_prefs/settings.xml` contains `block_ads=false`, before and after a restart |
| Bookmarks persist | a row appears in `bookmarks`, before and after a restart |
| Download works | `hosts-master.zip` lands in `/sdcard/Download` at the reported size |
| No crashes | the logcat crash buffer has no lines for the package |

Checked by screenshot, so not in the script: D-pad focus on every surface, page scrolling by arrow key,
the tabs panel with a live page title, cinema mode entering and leaving on `BACK`, and the DRM notice on
netflix.com.

Also run on real hardware, not just the emulator. Installed the release APK on a Motorola TV running
Android 14 with a 1080p output and WebView 156, then loaded Wikipedia and scrolled it, played a YouTube
video, and switched cinema mode on and off, with an empty crash buffer throughout. The TV is 32-bit ARM,
which is fine because the app ships no native libraries.

`tools/testpage/` holds single-control pages used to check the input model by hand. Serve them and open
them from the emulator with `http://10.0.2.2:8099/<page>`:

```bash
cd tools/testpage && python3 -m http.server 8099
```

| Page | Proves |
| --- | --- |
| `checkbox.html` | `OK` at screen centre toggles a checkbox instead of submitting the form |
| `select.html` | `OK` focuses a dropdown and a second `OK` opens it natively |
| `innerframe.html` inside `frame.html` | a same-origin iframe button receives the centre tap |
| `inner.html` | arrows scroll an inner `overflow:auto` container, not just the window |

## Layout

```
app/src/main/java/dev/sxch/tvweb/
  MainActivity.kt          surfaces (home, browser, panel), key routing, panel contents
  browser/BrowserSession.kt  tab ownership, one WebView per tab, ad block interception
  browser/TvWebView.kt     key model: arrows scroll, OK taps, MENU and BACK escape
  browser/PageAgent.kt     injected JS: page probe, tap, video fullscreen, cosmetic filters
  browser/Blocklist.kt     sorted host list, binary search on the host and its parent domains
  browser/Downloader.kt    DownloadManager wiring and filename parsing
  browser/DrmSites.kt      host to TV app mapping for the DRM handoff
  browser/UrlParser.kt     typed address or search decision
  data/Store.kt            SQLite for history, bookmarks, shortcuts
  data/Prefs.kt            typed settings over SharedPreferences
  ui/                      custom views, all focusable, no XML layouts
```

The data shape that keeps this small: one `Surface` state machine (`Home`, `Browser`, `Panel(kind)`)
rather than visibility booleans, `BrowserSession` as the single owner of every WebView, and a
`Settings` data class read through one provider so a change applies everywhere.

The ad block list ships in `app/src/main/assets/blocklist.txt`, about 74k hosts from StevenBlack's
hosts file, matched by suffix walk so subdomains are covered.

## Known limits

- DRM video does not play in a system WebView. The app detects the big streaming hosts and hands off.
  Anything not in `DrmSites` map fails silently with a blank player.
- Taps land at the centre of the screen. A page whose only control is in a corner needs a scroll first.
- Text entry uses the system on-screen keyboard. Voice search needs a recogniser the TV may not ship.
- Installed as a sideload with a debug key, so it cannot be updated by the Play Store and will not
  update in place once signed with a real key.
- Page scrolling is injected JS. A page that traps arrow keys in its own UI keeps them only while an
  input or iframe has focus.
- No tab restore after the app is killed, no history search, no reader mode, no extensions.
