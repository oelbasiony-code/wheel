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
  repo is public). It's saved in the app's private storage (SharedPreferences), set with
  `./adb shell "am start -n com.omar.wheel/.MainActivity --es calendar_url 'LINK'"`
  (several links: separate with spaces). Fire OS 8 denies adb any access to
  /sdcard/Android/data/com.omar.wheel, so files there can't be used. Without a link the card
  says CAL NO LINK YET. The link is secret: Omar runs that command himself; never ask him for it.
- `TrainDoodle.java`: the dot-matrix commuter rail train along the bottom.
- `News.java`: NYT headlines from the public RSS feed (no key) for the bottom ticker.
- `Words.java`: built-in word of the day (just the word, no definition).
- `Messages.java`: messages to the TV over ntfy.sh (no account). Wheel keeps a connection open
  to a private topic (random name, works like a password, NOT in the repo), set with
  `./adb shell "am start -n com.omar.wheel/.MainActivity --es message_topic 'TOPIC'"` and saved
  in SharedPreferences. The listener runs inside HomeWatcher so it works during shows.
  Anything posted to https://ntfy.sh/TOPIC shows up. It is NOT connected to iMessage, Gmail or
  iPhone notifications (Apple blocks reading notifications). Planned feeds into the topic:
  iPhone Shortcuts automations (Message / Email triggers) and maybe a Gmail Apps Script.
- Email: ntfy's email address for the topic (ntfy-TOPIC@ntfy.sh, NOT in the repo) drops the
  sender, so `scripts/gmail-to-tv.gs` (Apps Script in the BU account, every minute) emails it
  "Sender Name: Subject" with body "--\nwheel" (shows as MAIL, title only). Gmail forwarding
  to that address should be OFF once the script runs (else doubles). subject = title, body = message. Messages.tidy() keeps the first lines and drops the
  sign-off/signature. The iPhone Email trigger never fired, so email doesn't use the phone.
- Feeding the topic is done on Omar's iPhone with Shortcuts automations (Message trigger for
  chosen people; Email trigger on his Gmail account, which he adds to Apple's Mail app; both
  POST sender + text/subject to the topic). A Google Apps Script route was tried and dropped:
  Google's script servers get "Address unavailable" from ntfy.sh.
- `MessageOverlay.java`: the envelope icon (top-right, 10 s, dot teal = text, orange = mail),
  like a phone's notification icon. Over apps HomeWatcher shows it (icon only; ☰ can't be caught
  there, so it stays unread). On Wheel, AmbientView draws the same icon (no ☰ hint): ☰ opens it big in
  the middle; ☰, Back or any button closes. Play/Pause opens the newest any time. Texts stay on
  the card as MSG, emails as MAIL (separate, 2 hours each). An email = a body with line breaks.
  Do Not Disturb (tile in the all-apps list): no icon over shows; it shows when you're back on Wheel.
  An empty body arrives from ntfy as "triggered"; Wheel then shows just the title. The same text twice within 30 s shows once (two phone automations: chosen senders + "contains a space").
  Don't rely on catching remote keys in HomeWatcher: on this stick key filtering didn't reach
  the service reliably, and Back always reaches the app anyway.
- `Train.java`: MBTA v3 API (no key), Andover `place-WR-0228`, `CR-Haverhill`, direction 1 = to Boston.
- `Weather.java`: Open-Meteo (no key); MET Norway backup when it doesn't answer; last good reading saved and shown at start.
- `RokuTv.java`: PLAYSTATION / XBOX tiles switch the TV input over Roku ECP (port 8060).
  PS5 = InputHDMI1, Xbox = InputHDMI2. TV must have Control by mobile apps = Enabled.
- `HomeWatcher.java`: accessibility service + logcat watcher that sends the Home button to Wheel.
  Also knows when the TV turns on/off, for Govee and Startup: the stick does NOT sleep when the
  Roku TV turns off, so it polls the TV's power-mode over ECP every 5 s (RokuTv.screenOn).
  The startup sequence waits until the TV shows the Fire Stick (query/active-app =
  Config.FIRE_TV_APP_ID, tvinput.hdmi4) plus 2.5 s for the picture, so Omar actually sees it.
- `Govee.java`: the H615C strip. LAN API first (finds the strip by multicast scan, or
  `--es govee_ip 'IP'`); Govee cloud key as fallback (`--es govee_key 'KEY'`, secret, not in
  repo). Lights on/off with the TV only when the tile LIGHTS FOLLOW TV (all-apps list) is ON; off by default; opening an app
  from Wheel fades to its color (PS/Xbox tiles too), back on Wheel it goes to the home light: warm white 2700K at 60%, candlelight 2000K at 35% from 9 pm to 5 am (Config.HOME_LIGHT_*, NIGHT_*); it shifts by itself at 9 pm while you sit on Wheel.
  Needs "LAN Control" on in the Govee Home app (as of Nov 16 the scan got no reply).
- `Startup.java`: boot sequence when the TV comes on (dots fly into WELCOME BACK, OMAR, hold,
  fly back out the way they came, black fades to the wheel; no news line, Omar's call) and the daily briefing (first TV-on at 5 am or later, once a day; greeting matches the panel: weather, first
  event, next train, typed out). Any button skips. Try it: `--ez boot_demo true` (add
  `--ez briefing true`), or `--ez briefing_demo true`.

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
greeting, now a Smart Stack card (faint dotted rounded box, messages slide up, position dots
on the right, long text scrolls sideways). It rotates Calendar (tomorrow's first event when
today is empty) and WORD OF DAY (just the word). 10 minutes before a timed event the card
shows only "LAUNDRY IN 7 MIN" (pulsing), then "LAUNDRY NOW" for 5 minutes, with a rain warning when relevant. No train on the
card (Omar's call: the train animation covers it).
Commuter rail train: rolls along the bottom with "NEXT TRAIN TO BOSTON 4:58 AM" (small,
centered over it), every 5 minutes in the 30 minutes before the next train.
NYT headlines ticker: along the very bottom of the main screen (dimmer; NOT in idle mode, Omar's call:
idle), "NYT" label, a dot between headlines, refreshed every 15 minutes, all in `AmbientView`.
The bottom is shared: the train rides on top of the ticker like rails. Never put the train at
the top.

## Status (Nov 16)

All of Omar's picks are built and on the TV: messages (texts via iPhone Shortcut with contact
names; BU email via Gmail forwarding to ntfy's email address), the envelope icon + MSG/MAIL
cards, a calm sleep screen (dino, time, date, weather only; no news or card), Govee (cloud API key; app colors at 100%, home light warm white by day / candlelight
9 pm-5 am, LIGHTS FOLLOW TV tile, TV on/off from the Roku's power-mode), the startup
sequence, the daily briefing (first TV-on at 5 am or later), the calendar
countdown on the card, WORD OF DAY, centered idle mode.

Ideas Omar liked but hasn't picked yet: departure warning ("LEAVE IN 8 MIN" + amber strip),
focus when gaming (PS5/Xbox turns on Do Not Disturb), sleep timer for TV + strip, phone
quick actions through the topic ("movie mode"), weekly recap in Sunday's briefing.

Not wanted: sunrise/sunset, prayer times, days-until countdowns, sports scores. (The
10-minute calendar countdown on the card IS wanted.) Reminders: later.
