package com.omar.wheel;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes Wheel the home screen on Fire TV. Amazon's launcher can't be removed
 * without root, so this sends you to Wheel whenever:
 *  - Amazon's home screen (HomeActivity_vNext) comes to the front, or
 *  - the Home button is tapped (if Fire OS passes it on; holding Home still opens the quick menu).
 * Fire TV Settings is a different screen of Amazon's launcher, so it's left alone.
 * After you pick AMAZON HOME in Wheel, Amazon's home screen may stay until you
 * press Home (or for at most a minute).
 * Turned on once over ADB by setup/firestick-setup.command.
 *
 * Diagnostics: adb logcat -d -s Wheel
 */
public final class HomeWatcher extends AccessibilityService {

    static final String TAG = "Wheel";
    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME_SCREEN = "HomeActivity_vNext";
    private static final long HOLD_MAX_MS = 60 * 1000L;
    private static final long SHORT_PRESS_MS = 500;

    /** Set when you choose AMAZON HOME in Wheel; covers only the moment it opens. */
    private static volatile long holdUntil;
    /** During a hold: has Amazon's home screen already appeared once? */
    private static volatile boolean holdArrived;

    /**
     * Opening Fire TV Settings briefly passes through Amazon's home screen.
     * While this is set, that pass-through is allowed; it ends as soon as the
     * Settings screen itself appears (or after a few seconds).
     */
    private static volatile long settingsGraceUntil;

    private final Handler h = new Handler(Looper.getMainLooper());
    private long homeDownAt;
    private long lastJump;

    private final Runnable jumpLater = new Runnable() {
        @Override
        public void run() {
            openWheel("home key");
        }
    };

    static void holdForAmazonHome() {
        holdArrived = false;
        holdUntil = SystemClock.uptimeMillis() + HOLD_MAX_MS;
    }

    static void releaseHold() {
        holdUntil = 0;
        holdArrived = false;
        settingsGraceUntil = 0;
    }

    static void expectSettings() {
        settingsGraceUntil = SystemClock.uptimeMillis() + 5000;
    }

    private static boolean held() {
        return SystemClock.uptimeMillis() < holdUntil;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        CharSequence pkg = e.getPackageName();
        CharSequence cls = e.getClassName();
        Log.d(TAG, "event " + AccessibilityEvent.eventTypeToString(e.getEventType())
                + " " + pkg + " " + cls + (held() ? " [held]" : ""));
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (pkg == null || cls == null || !AMAZON_LAUNCHER.contentEquals(pkg)) return;
        long now = SystemClock.uptimeMillis();
        // Only Amazon's actual home screen. Settings, the quick menu, etc. are left alone.
        if (!cls.toString().endsWith(AMAZON_HOME_SCREEN)) {
            settingsGraceUntil = 0; // Settings (or another Amazon screen) has arrived
            return;
        }
        if (now < settingsGraceUntil) return; // passing through on the way to Settings
        if (held()) {
            if (!holdArrived) {
                holdArrived = true; // the AMAZON HOME you asked for; let it show
                return;
            }
            releaseHold(); // Amazon's home screen reappeared (e.g. Home pressed): back to Wheel
        }
        if (now - lastJump < 800) return;
        lastJump = now;
        openWheel("amazon home screen");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        Log.d(TAG, "key " + KeyEvent.keyCodeToString(e.getKeyCode())
                + (e.getAction() == KeyEvent.ACTION_DOWN ? " down" : " up"));
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) homeDownAt = e.getEventTime();
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            if (e.getEventTime() - homeDownAt < SHORT_PRESS_MS) {
                // A tap of Home always means "go to Wheel". Let Fire OS handle the press
                // first, then bring Wheel up on top in case we were already on Amazon's screen.
                releaseHold();
                h.removeCallbacks(jumpLater);
                h.postDelayed(jumpLater, 400);
            }
        }
        return false;
    }

    private void openWheel(String why) {
        Log.d(TAG, "opening Wheel (" + why + ")");
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }
}
