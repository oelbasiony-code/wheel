package com.omar.wheel;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
        startLogWatch();
    }

    @Override
    public void onDestroy() {
        stopLogWatch();
        super.onDestroy();
    }

    private void startLogWatch() {
        if (watching) return;
        watching = true;
        logThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (watching) {
                    readLogOnce();
                    if (!watching) break;
                    try {
                        Thread.sleep(3000); // logcat ended (or isn't allowed); try again later
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }, "wheel-homelog");
        logThread.start();
    }

    private void readLogOnce() {
        BufferedReader in = null;
        try {
            logProc = Runtime.getRuntime().exec(new String[]{
                    "logcat", "-v", "brief", "-T", "1", "ActivityTaskManager:I", "*:S"});
            in = new BufferedReader(new InputStreamReader(logProc.getInputStream()));
            long started = SystemClock.uptimeMillis();
            String line;
            while (watching && (line = in.readLine()) != null) {
                if (SystemClock.uptimeMillis() - started < LOG_WARMUP_MS) continue;
                if (line.contains(HOME_PRESS_A) && line.contains(HOME_PRESS_B) && line.contains(HOME_PRESS_C)) {
                    h.removeCallbacks(homePressed);
                    // Let Fire OS finish handling the press, then put Wheel on top.
                    h.postDelayed(homePressed, 150);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "log watch stopped: " + e);
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
            if (logProc != null) logProc.destroy();
            logProc = null;
        }
    }

    private void stopLogWatch() {
        watching = false;
        Process p = logProc;
        if (p != null) p.destroy();
        if (logThread != null) logThread.interrupt();
        h.removeCallbacks(homePressed);
    }

    // ---------------------------------------------------------------- holds

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
            if (!holdArrived) {
                holdArrived = true; // the AMAZON HOME you asked for; let it show
                holdArrivedAt = now;
                return;
            }
            if (now - holdArrivedAt < OPENING_MS) return; // repeat reports of the same opening
            releaseHold(); // Amazon's home screen reappeared: back to Wheel
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
