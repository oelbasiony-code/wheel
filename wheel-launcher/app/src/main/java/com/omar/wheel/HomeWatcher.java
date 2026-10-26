package com.omar.wheel;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes Wheel the home screen on Fire TV. Amazon's launcher can't be removed
 * without root, so whenever it comes to the front (Home button, boot, leaving
 * an app), this jumps straight to Wheel instead. Turned on once over ADB by
 * setup/firestick-setup.command; it does nothing until then.
 */
public final class HomeWatcher extends AccessibilityService {

    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";

    private static volatile long pausedUntil;
    private long lastJump;

    /** Lets Amazon's screen stay up for a while (used for Settings / Amazon Home). */
    static void pause(long ms) {
        pausedUntil = SystemClock.uptimeMillis() + ms;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null || !AMAZON_LAUNCHER.contentEquals(pkg)) return;
        long now = SystemClock.uptimeMillis();
        if (now < pausedUntil || now - lastJump < 800) return;
        if (!isFullScreen(e)) return;
        lastJump = now;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    /**
     * Only real screens of Amazon's launcher count. Pop-up panels (like the
     * quick menu you get by holding Home) aren't activities, so they're left alone.
     */
    private boolean isFullScreen(AccessibilityEvent e) {
        CharSequence cls = e.getClassName();
        if (cls == null) return false;
        try {
            getPackageManager().getActivityInfo(new ComponentName(AMAZON_LAUNCHER, cls.toString()), 0);
            return true;
        } catch (Exception notAnActivity) {
            return false;
        }
    }

    @Override
    public void onInterrupt() {
    }
}
