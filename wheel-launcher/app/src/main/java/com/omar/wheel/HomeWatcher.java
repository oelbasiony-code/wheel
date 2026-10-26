package com.omar.wheel;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes Wheel the home screen on Fire TV. Amazon's launcher can't be removed
 * without root, so this sends you to Wheel whenever:
 *  - Amazon's home screen (HomeActivity_vNext) comes to the front, or
 *  - the Home button is pressed (a short press; holding Home still opens the quick menu).
 * Fire TV Settings is a different screen of Amazon's launcher, so it's left alone.
 * The only time Amazon's home screen is allowed to stay is after you pick
 * AMAZON HOME in Wheel, until you press Home again.
 * Turned on once over ADB by setup/firestick-setup.command.
 */
public final class HomeWatcher extends AccessibilityService {

    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME_SCREEN = "HomeActivity_vNext";
    private static final long HOLD_MAX_MS = 10 * 60 * 1000L;
    private static final long SHORT_PRESS_MS = 500;

    /** Set when you choose AMAZON HOME in Wheel; cleared by Home or returning to Wheel. */
    private static volatile long holdUntil;

    private final Handler h = new Handler(Looper.getMainLooper());
    private long homeDownAt;
    private long lastJump;

    private final Runnable jumpLater = new Runnable() {
        @Override
        public void run() {
            openWheel();
        }
    };

    static void holdForAmazonHome() {
        holdUntil = SystemClock.uptimeMillis() + HOLD_MAX_MS;
    }

    static void releaseHold() {
        holdUntil = 0;
    }

    private static boolean held() {
        return SystemClock.uptimeMillis() < holdUntil;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        CharSequence cls = e.getClassName();
        if (pkg == null || cls == null || !AMAZON_LAUNCHER.contentEquals(pkg)) return;
        // Only Amazon's actual home screen. Settings, the quick menu, etc. are left alone.
        if (!cls.toString().endsWith(AMAZON_HOME_SCREEN)) return;
        if (held()) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastJump < 800) return;
        lastJump = now;
        openWheel();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
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

    private void openWheel() {
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
