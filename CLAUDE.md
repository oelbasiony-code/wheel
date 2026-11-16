# Wheel: Omar's custom Fire TV home screen

A replacement launcher for a **Fire TV Stick 4K Plus** on a **Roku TV**: a dot-matrix app wheel
(left), clock / date / weather panel (right), animated brontosaurus, seasonal themes, idle mode.
Goal: it should feel like Omar's own JARVIS-style home screen, ads and Amazon bloat gone.

## Layout

- `wheel-launcher/` is the Android project (Java only, no Kotlin, no AndroidX).
  minSdk 22, targetSdk 34, AGP 8.7.3, Gradle 8.10.2.
- `.github/workflows/build.yml` builds on every push to `main` and publishes `wheel.apk`
  to the `latest` release: https://github.com/oelbasiony-code/wheel/releases/download/latest/wheel.apk
- `wheel-launcher/app/wheel.keystore` signs the app. Never change or remove it, or updates
  won't install over the existing app.

Key files (`wheel-launcher/app/src/main/java/com/omar/wheel/`):
- `Config.java`: everything personal (name, location, pinned apps and their dot icons,
  console HDMI inputs, train stop).
- `WheelView.java`: the wheel and the right panel; redraws once a second.
- `AmbientView.java`: transparent overlay at ~25 fps for everything animated (dino, weather
  icon, seasonal effects, the rotating info line, idle mode).
- `DotFont.java` (5x7 font), `Doodles.java` (dino, weather icons), `Season.java` (holidays).
- `Agenda.java`: Google Calendar via the private iCal link. The link is NOT in the repo (the
  repo is public); it is read from `/sdcard/Android/data/com.omar.wheel/files/calendar.txt`
  on the stick (one link per line). Fire OS 8 blocks adb from that folder, so send it through
  the app instead: `./adb shell "am start -n com.omar.wheel/.MainActivity --es calendar_link 'LINK'"`
  (several links: separate with spaces). Without a link the card says CAL NO LINK YET.
- `TrainDoodle.java`: the dot-matrix commuter rail train along the bottom.
- `News.java`: NYT headlines from the public RSS feed (no key) for idle mode's ticker.
- `Train.java`: MBTA v3 API (no key), Andover `place-WR-0228`, `CR-Haverhill`, direction 1 = to Boston.
- `Weather.java`: Open-Meteo (no key).
- `RokuTv.java`: PLAYSTATION / XBOX tiles switch the TV input over Roku ECP (port 8060).
  PS5 = InputHDMI1, Xbox = InputHDMI2. TV must have Control by mobile apps = Enabled.
- `HomeWatcher.java`: accessibility service + logcat watcher that sends the Home button to Wheel.

## Omar's setup (his Mac)

- adb is at `~/Desktop/platform-tools/adb` (run as `./adb` from that folder).
- Fire Stick IP: `192.168.86.52` (port 5555). Connect: `./adb connect 192.168.86.52`.
- If adb says "No route to host" or "unauthorized": `./adb kill-server`, connect again,
  and accept the prompt on the TV.

## Ship a change (do the whole loop, Omar doesn't want manual steps)

1. Edit, commit, push to `main`.
2. Wait for the Actions run for that commit to finish successfully (`gh run watch`, or poll
   `https://api.github.com/repos/oelbasiony-code/wheel/actions/runs?per_page=1`).
   Downloading before it finishes gets the OLD apk.
3. `curl -L -o wheel.apk https://github.com/oelbasiony-code/wheel/releases/download/latest/wheel.apk`
4. `./adb connect 192.168.86.52 && ./adb install -r wheel.apk`
5. Reopen Wheel: `./adb shell am start -n com.omar.wheel/.MainActivity`

Try-it-now hooks (for screenshots): add `--ez train_demo true` (a train pass) or
`--ez idle_demo true` (idle mode) to the `am start` line in step 5.

GitHub: `gh` is at `/opt/homebrew/bin/gh` and signed in (it also handles `git push`).

`install -r` keeps the Home takeover and permissions; the setup script never needs re-running
for an update. Take a screenshot to check work: `./adb exec-out screencap -p > screen.png`.

## Style rules

- Everything on screen is dots: DotFont text, sprite grids, no system fonts or widgets.
- Calm and restrained over busy. Animate only things that mean something.
- Colorblind-friendly contrast (Omar asked for it on the weather icons).
- Keep the stick fast: only `AmbientView` animates; `WheelView` redraws once a second.
- Comments in plain language, like the existing code.

## Done so far

Wheel + pinned apps (YouTube TV, Apple TV, Disney+, Netflix, Hulu, Prime Video, Peacock,
HBO Max, PlayStation, Xbox), Home button takeover, weather with animated icons and real moon
phase, idle mode, holidays (Halloween, Thanksgiving, Christmas, Ramadan, Eid, birthday Apr 30),
PS5/Xbox input switching, Google Calendar, MBTA trains, and a rotating info line above the
greeting (calendar, train, rain warning; left/right on the remote flips it), now in a
Smart Stack card (faint dotted rounded box, messages slide up, position dots on the right).
When today is empty it shows tomorrow's first event and the next train.
Commuter rail train: rolls along the bottom with its departure time above it, every 5 minutes
in the 30 minutes before the next train.

## Next up (Omar's picks, in order)

1. **NYT headlines ticker** (in progress): public RSS feed
   (https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml, no key), a smooth scrolling
   dot-matrix ticker along the bottom of **idle mode only**, "NYT" as the label, a dot between
   headlines, refreshed every 15 minutes. Lives in `AmbientView` so it stays fast.
2. **The bottom of the screen is shared**: in normal mode it's the train's track; in idle mode
   the NYT ticker runs there, and if a train is due during idle it rides on top of the ticker
   line like rails. Never put the train at the top.
Items 3-6 are queued: don't start them until Omar says so.

3. **Startup sequence**: when Wheel first appears after the TV turns on, a ~2 s dot-matrix
   boot (dots fly in, "WELCOME BACK, OMAR", wheel builds itself). JARVIS feel.
4. **Morning briefing**: first time on before 11 am, a full-screen moment with weather, first
   calendar event, next train; then settles into the wheel.
5. **Messages to the TV**: a phone Shortcut sends text that pops up in dots (free push service
   such as ntfy.sh, topic name kept off the public repo like the calendar link).
6. **Govee lights turn on with the TV**: Omar has Govee lights. When the TV turns on (Wheel
   comes up after the stick wakes), switch the Govee lights on too. No dimming for movies.
   Prefer Govee's LAN API (UDP, port 4003, needs "LAN Control" switched on per device in the
   Govee Home app); fall back to the cloud API key.

Not wanted: sunrise/sunset, prayer times, countdowns, sports scores. Reminders: later.
