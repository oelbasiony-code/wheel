package com.omar.wheel;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Makes Wheel the home screen on Fire TV. Amazon's launcher can't be removed
 * without root, so this sends you to Wheel whenever:
 *  - the Home button is pressed. Fire OS writes a line to the system log for every
 *    Home press ("START ... category.HOME ... HomeActivity_vNext ... from uid 0"),
 *    even when Amazon's home screen is already showing, so Wheel watches for it.
 *    Needs READ_LOGS, which the setup script grants over ADB.
 *  - Amazon's home screen (HomeActivity_vNext) comes to the front. This is the
 *    fallback if the log can't be read.
 * Fire TV Settings is a different screen of Amazon's launcher, so it's left alone.
 * After you pick AMAZON HOME in Wheel, Amazon's home screen stays until you press Home.
 * Turned on once over ADB by setup/firestick-setup.command.
 *
 * Diagnostics: adb logcat -d -s Wheel
 */
public final class HomeWatcher extends AccessibilityService {

    static final String TAG = "Wheel";
    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME_SCREEN = "HomeActivity_vNext";
    private static final long HOLD_MAX_MS = 60 * 1000L;

    /** Set when you choose AMAZON HOME in Wheel. */
    private static volatile long holdUntil;
    /** During a hold: has Amazon's home screen already appeared once? */
    private static volatile boolean holdArrived;
    /** When it appeared. Fire OS reports the opening more than once in quick succession. */
    private static volatile long holdArrivedAt;
    private static final long OPENING_MS = 2000;

    /**
     * Opening Fire TV Settings briefly passes through Amazon's home screen.
     * While this is set, that pass-through is allowed; it ends as soon as the
     * Settings screen itself appears (or after a few seconds).
     */
    private static volatile long settingsGraceUntil;

    private final Handler h = new Handler(Looper.getMainLooper());
    private long lastJump;

    // ---------------------------------------------------------------- log watcher

    /** A Home press as Fire OS logs it. Wheel's own AMAZON HOME launch comes from Wheel's uid, not 0. */
    private static final String HOME_PRESS_A = "cat=[android.intent.category.HOME]";
    private static final String HOME_PRESS_B = AMAZON_LAUNCHER;
    private static final String HOME_PRESS_C = "from uid 0";
    /** Lines that arrive this soon after starting are old ones from the buffer. */
    private static final long LOG_WARMUP_MS = 700;

    private volatile boolean watching;
    private Thread logThread;
    private Process logProc;

    private final Runnable homePressed = new Runnable() {
        @Override
        public void run() {
            if (SystemClock.uptimeMillis() < settingsGraceUntil) return;
            releaseHold();
            openWheel("home button");
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        startLogWatch();
        // Messages to the TV: listen all the time, so they arrive during shows too.
        Messages.addListener(onMessage);
        Messages.start(this);
    }

    @Override
    public void onDestroy() {
        stopLogWatch();
        Messages.removeListener(onMessage);
        removeOverlay();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    // ---------------------------------------------------------------- messages over other apps

    private static volatile HomeWatcher instance;
    private MessageOverlay overlay;
    /** Like a phone notification: the banner shows this long, then goes away. */
    private static final long BANNER_MS = 10 * 1000L;

    private final Messages.Listener onMessage = new Messages.Listener() {
        @Override
        public void onMessage(String text) {
            // On Wheel it pops up big there instead. Do Not Disturb: it waits for Wheel.
            if (MainActivity.onScreen || Messages.dnd(HomeWatcher.this)) return;
            showOverlay(text);
        }
    };

    private final Runnable iconTimeout = new Runnable() {
        @Override
        public void run() {
            removeOverlay();   // it stays on Wheel's card as MSG for a while
        }
    };

    private void showOverlay(String text) {
        try {
            if (overlay == null) {
                overlay = new MessageOverlay(this);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        android.graphics.PixelFormat.TRANSLUCENT);
                ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(overlay, lp);
            }
            overlay.show(text);
            // You've seen it, so Wheel won't pop it up again (still on the card).
            Messages.markRead(this);
            h.removeCallbacks(iconTimeout);
            h.postDelayed(iconTimeout, BANNER_MS);
        } catch (Exception e) {
            Log.d(TAG, "can't show message overlay: " + e);
            overlay = null;
        }
    }

    private void removeOverlay() {
        h.removeCallbacks(iconTimeout);
        if (overlay == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(overlay);
        } catch (Exception ignored) {
        }
        overlay = null;
    }

    /** Wheel came to the front: it shows the message itself. */
    static void hideOverlay() {
        final HomeWatcher w = instance;
        if (w == null) return;
        w.h.post(new Runnable() {
            @Override
            public void run() {
                w.removeOverlay();
            }
        });
    }

    // ---------------------------------------------------------------- holds

    static void holdForAmazonHome() {
        holdArrived = false;
        holdStartedAt = SystemClock.uptimeMillis();
        // Lasts until the Home button is pressed or you're back on Wheel.
        holdUntil = Long.MAX_VALUE;
    }

    private static volatile long holdStartedAt;

    /** True when Home presses can be read from the log (READ_LOGS granted by the setup script). */
    private boolean canSeeHomePresses() {
        return checkCallingOrSelfPermission("android.permission.READ_LOGS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
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

    // ---------------------------------------------------------------- screen changes (fallback)

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        CharSequence cls = e.getClassName();
        if (pkg == null || cls == null || !AMAZON_LAUNCHER.contentEquals(pkg)) return;
        long now = SystemClock.uptimeMillis();
        // Only Amazon's actual home screen. Settings, the quick menu, etc. are left alone.
        if (!cls.toString().endsWith(AMAZON_HOME_SCREEN)) {
            settingsGraceUntil = 0; // Settings (or another Amazon screen) has arrived
            return;
        }
        if (now < settingsGraceUntil) return; // passing through on the way to Settings
        if (held()) {
            // On AMAZON HOME, only the Home button brings you back. Amazon's screen
            // reports changes on its own while it loads, so those are ignored.
            if (canSeeHomePresses()) return;
            // Fallback when Home presses can't be seen: let it open, then the next
            // report counts as Home. Gives up holding after a minute.
            if (now - holdStartedAt <= HOLD_MAX_MS) {
                if (!holdArrived) {
                    holdArrived = true; // the AMAZON HOME you asked for; let it show
                    holdArrivedAt = now;
                    return;
                }
                if (now - holdArrivedAt < OPENING_MS) return; // repeat reports of the same opening
            }
            releaseHold(); // back to Wheel
        }
        if (now - lastJump < 800) return;
        lastJump = now;
        openWheel("amazon home screen");
    }

    private void openWheel(String why) {
        Log.d(TAG, "opening Wheel (" + why + ")");
        lastJump = SystemClock.uptimeMillis();
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
