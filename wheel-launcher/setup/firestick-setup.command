#!/bin/bash
# Wheel: make Wheel your Fire TV home screen and turn off Amazon's ads and tracking.
#
# Run it from Terminal:
#   bash ~/Downloads/wheel-launcher/setup/firestick-setup.command
# Undo everything:
#   bash ~/Downloads/wheel-launcher/setup/firestick-setup.command undo

PKG="com.omar.wheel"
SERVICE="com.omar.wheel/com.omar.wheel.HomeWatcher"

# Ads, tracking, shopping and tutorial screens. None of these are needed
# to watch anything.
ADS="com.amazon.tv.acr
com.amazon.hybridadidservice
com.amazon.device.telemetry.emitter
com.amazon.wirelessmetrics.service
com.amazon.perfc
com.amazon.perfcollection
com.amazon.logan
com.amazon.shoptv.client
com.amazon.shoptv.firetv.client
com.amazon.sneakpeek
com.amazon.tv.ftvambient
com.amazon.storm.lightning.tutorial
com.amazon.tmm.tutorial
com.amazon.tv.releasenotes"

# Amazon apps that aren't on your wheel. Asked about separately.
EXTRAS="com.amazon.bueller.photos
com.amazon.bueller.music
com.amazon.imdb.tv.android.app
com.amazon.gamehub
com.amazon.cloud9
com.amazon.tv.livetv"

SCREENSAVER="com.amazon.ftv.screensaver"

say() { printf '\n%s\n' "$*"; }
fail() { printf '\n[!!] %s\n\n' "$*"; exit 1; }

# ---- find adb -------------------------------------------------------------
ADB=""
for c in "$(command -v adb 2>/dev/null)" \
         "$HOME/Downloads/platform-tools/adb" \
         "$(dirname "$0")/../../platform-tools/adb" \
         "$(dirname "$0")/platform-tools/adb"; do
    if [ -n "$c" ] && [ -x "$c" ]; then ADB="$c"; break; fi
done
[ -z "$ADB" ] && fail "Can't find adb. Download 'SDK Platform-Tools for Mac' from
     developer.android.com/tools/releases/platform-tools and unzip it in Downloads."

# ---- connect --------------------------------------------------------------
say "Fire Stick IP address? (Settings > My Fire TV > About > Network)"
read -r -p "> " IP
IP="${IP%%:*}"
[ -z "$IP" ] && fail "No IP address entered."
T="$IP:5555"

"$ADB" connect "$T" >/dev/null 2>&1
say "If the TV shows 'Allow USB debugging?', check 'Always allow' and press OK."
read -r -p "Press Enter here when you've done that (or if nothing popped up)... " _
"$ADB" connect "$T" >/dev/null 2>&1
state=$("$ADB" -s "$T" get-state 2>&1)
[ "$state" != "device" ] && fail "Couldn't connect to $T ($state).
     Check ADB debugging is on and the Mac and Fire Stick are on the same Wi-Fi."
echo "[OK] Connected to $T"

sh() { "$ADB" -s "$T" shell "$@" 2>&1; }

# ---- undo -----------------------------------------------------------------
if [ "$1" = "undo" ]; then
    say "Putting Amazon's home screen back and turning everything back on..."
    sh settings put secure enabled_accessibility_services '""' >/dev/null
    sh settings put secure accessibility_enabled 0 >/dev/null
    sh cmd role remove-role-holder android.app.role.HOME "$PKG" >/dev/null
    for p in $ADS $EXTRAS $SCREENSAVER; do
        out=$(sh pm enable "$p")
        case "$out" in *enabled*) echo "  [OK] On: $p" ;; esac
    done
    say "Done. Restarting the Fire Stick..."
    "$ADB" -s "$T" reboot >/dev/null 2>&1
    exit 0
fi

# ---- install --------------------------------------------------------------
sh pm list packages "$PKG" | grep -q "package:$PKG\$" \
    || fail "Wheel isn't installed on this Fire Stick yet. Install it with Downloader first."

say "Making Wheel your home screen..."
sh settings put secure enabled_accessibility_services "$SERVICE" >/dev/null
sh settings put secure accessibility_enabled 1 >/dev/null
sh cmd role add-role-holder android.app.role.HOME "$PKG" >/dev/null
if sh settings get secure enabled_accessibility_services | grep -q "$PKG"; then
    echo "  [OK] Home button now opens Wheel"
else
    echo "  [!!] Couldn't turn on the Home takeover. Send Claude a screenshot of this."
fi

disable_list() {
    for p in $1; do
        out=$(sh pm disable-user --user 0 "$p")
        case "$out" in
            *disabled*) echo "  [OK] Off: $p" ;;
            *SecurityException*) echo "  [--] Amazon won't allow turning off: $p" ;;
            *) echo "  [--] Not on this stick: $p" ;;
        esac
    done
}

say "Turning off Amazon ads, tracking and shopping..."
disable_list "$ADS"

say "Also turn off Amazon Photos, Amazon Music, Freevee, Game Hub, Silk browser and Live TV?"
read -r -p "(y/n) > " yn
case "$yn" in [Yy]*) disable_list "$EXTRAS" ;; esac

say "Turn off Amazon's screensaver too? It can show sponsored content."
read -r -p "(y/n) > " yn
case "$yn" in [Yy]*) disable_list "$SCREENSAVER" ;; esac

say "Restarting the Fire Stick so it starts on Wheel..."
"$ADB" -s "$T" reboot >/dev/null 2>&1

say "All set. After it restarts, press Home on the remote and you should land on Wheel.
To undo everything later:
  bash \"$0\" undo"
