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
| Home | Still goes to Amazon's home screen (Fire OS doesn't let apps change that) |

Apps that aren't one of your 8 start out hidden. Press Menu to see them all,
then hold OK on one and pick UNHIDE to add it to your wheel.

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

To make the remote's Home button open Wheel, install a button remapper app
(for example Button Mapper) and set Home, or a long-press of Home, to open Wheel.

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
