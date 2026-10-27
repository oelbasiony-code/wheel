# Wheel

A dot-matrix app wheel home screen for Fire TV Stick 4K Plus. It shows your apps
(YouTube TV, Apple TV, Disney+, Netflix, Hulu, Prime Video, Peacock, Apple Music),
each in its brand color, with a greeting, clock, date and weather on the right.

## Controls

| Button | Does |
|---|---|
| Up / Down | Spin the wheel |
| OK | Open the app |
| Hold OK | Move, hide or unhide the app |
| Menu (☰) | Switch between your apps and all apps |
| Back | Close a menu or go back to your apps |
| Home | Opens Wheel once the setup script has been run (see below) |

Apps that aren't one of your 8 start out hidden. Press Menu to see them all,
then hold OK on one and pick UNHIDE to add it to your wheel.

## Extras

- **Dinosaur:** a dot brontosaurus walks back and forth above the greeting.
- **Live weather icon:** sun, clouds, rain, snow, fog or storms, animated, next to the forecast. At night it shows tonight's real moon phase.
- **Idle mode:** after 3 minutes with no button presses, the wheel fades and a big clock
  appears in the middle of the screen. Any button wakes it; that first press
  doesn't do anything else. Change or turn off with `IDLE_MINUTES` in `Config.java` (0 = off).

- **Holiday themes:** the dinosaur dresses up all month (October witch hat, November scarf,
  December Santa hat, Ramadan lantern). On the day itself there's also an icon next to the date,
  a screen-wide effect and a greeting: Halloween pumpkin and bats, Thanksgiving turkey and leaves,
  Christmas tree and snow, Eid crescent and gold sparkles, birthday (Apr 30) cake and confetti.
  Ramadan shows its lantern and greeting all month. In `Config.java`: `HIJRI_ADJUST_DAYS` shifts
  Ramadan/Eid by a day if your mosque differs, and `PREVIEW_DATE` (e.g. `"2026-10-31"`) lets you
  see a holiday early.

## Building it with GitHub (no installs)

1. Make a GitHub account and create a new repository called `wheel`.
   Set it to **Public** so the Fire Stick can download the app without signing in.
2. On the empty repo page, click **uploading an existing file**. Drag in
   everything from this folder **except** the hidden `.github` folder: the `app`
   folder, `build.gradle`, `settings.gradle`, `gradle.properties` and `README.md`.
   Click **Commit changes**.
3. Click **Add file → Create new file**. For the name, type exactly
   `.github/workflows/build.yml`, paste in the contents of that file from this
   folder, and click **Commit changes**.
4. Open the **Actions** tab. A build starts by itself and takes about
   3–5 minutes. Wait for the green check.
5. Your download link is:
   `https://github.com/YOUR-USERNAME/wheel/releases/latest/download/wheel.apk`

## Installing on the Fire Stick

1. Open Downloader, type the link from step 5, and press Go.
2. Choose Install. When it's done, choose Delete to remove the installer file.
3. Wheel shows up in Your Apps & Channels.

## Making Wheel your home screen

Test Wheel as a normal app first. Once everything opens and looks right:

1. On the Fire Stick: Settings → My Fire TV → Developer options → turn on
   **ADB debugging**. Note the IP under Settings → My Fire TV → About → Network.
2. On the Mac: download **SDK Platform-Tools for Mac** from
   developer.android.com/tools/releases/platform-tools and unzip it in Downloads.
3. In Terminal run:
   `bash ~/Downloads/wheel-launcher/setup/firestick-setup.command`
   Type the IP, accept "Allow USB debugging?" on the TV, and answer the questions.

The stick restarts and the Home button then lands on Wheel. The script also
turns off Amazon's ad, tracking, shopping and tutorial services. Prime Video
and the Appstore are left alone.

- **Fire TV Settings / Amazon's home screen:** press Menu (☰) in Wheel. Both
  are at the top of the all-apps list. Holding Home also opens Fire TV's quick menu.
- **Undo everything:** `bash ~/Downloads/wheel-launcher/setup/firestick-setup.command undo`
- Amazon's launcher still runs underneath (it can't be removed without root),
  you just never see it. A Fire OS update may undo the changes; run the script again.

## Changing things

Everything personal is in `app/src/main/java/com/omar/wheel/Config.java`:
your name, the weather location, and the app list with each app's colors.
Edit it on GitHub (click the file, then the pencil icon), commit, and a new
build starts. Install the new one with the same Downloader link. It updates
over the old one and keeps your order and hidden apps.

## Notes

- If one of your apps shows as NOT INSTALLED but it is installed, its name on
  the Fire Stick doesn't match. Add that name to its aliases in `Config.java`
  (lowercase, letters and numbers only, `+` written as `plus`).
- Weather comes from Open-Meteo, which is free and needs no account.
- The signing key in `app/wheel.keystore` only identifies your own builds so
  updates install over each other. It isn't used for anything else.
