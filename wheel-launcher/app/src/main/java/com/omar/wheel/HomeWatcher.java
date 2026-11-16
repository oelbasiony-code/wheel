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
        // The Govee strip follows the TV: the stick sleeps when the TV turns off.
        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, f);
        screenWatch = true;
        // The stick usually stays awake when the TV turns off, so also ask the TV itself.
        startTvWatch();
    }

    // ---------------------------------------------------------------- TV on/off (asks the Roku)

    private static final long TV_POLL_MS = 5 * 1000L;
    /** After the TV turns on: how long to wait for it to show the Fire Stick, and then
     *  for the picture to actually appear, before the startup sequence plays. */
    private static final long WAIT_FOR_INPUT_MS = 5 * 60 * 1000L, PICTURE_MS = 2500;
    private long tvOnAt;   // when the TV came on and the startup hasn't played yet; 0 = none
    private volatile boolean tvWatching;
    private Thread tvThread;
    private Boolean tvWasOn;

    private void startTvWatch() {
        if (tvWatching) return;
        tvWatching = true;
        final android.content.Context c = getApplicationContext();
        tvThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (tvWatching) {
                    Boolean on = RokuTv.screenOn(c);
                    if (on != null) {
                        if (tvWasOn != null && !on.equals(tvWasOn)) {
                            Log.d(TAG, "tv " + (on ? "on" : "off"));
                            if (on) {
                                Govee.tvOn(c);
                                tvOnAt = System.currentTimeMillis();   // startup waits for the picture
                            } else {
                                Govee.tvOff(c);
                                tvOnAt = 0;
                            }
                        }
                        tvWasOn = on;
                        // Sitting on Wheel at 9 pm: the home light turns to candlelight.
                        if (on && MainActivity.onScreen) Govee.homeTick(c);
                    }
                    long wait = TV_POLL_MS;
                    if (tvOnAt > 0) {
                        if (System.currentTimeMillis() - tvOnAt > WAIT_FOR_INPUT_MS) {
                            tvOnAt = 0;   // never came to the Fire Stick; skip it this time
                        } else if (Config.FIRE_TV_APP_ID.equals(RokuTv.activeApp(c))) {
                            // On the Fire Stick: give the picture a moment, then play.
                            tvOnAt = 0;
                            try {
                                Thread.sleep(PICTURE_MS);
                            } catch (InterruptedException e) {
                                break;
                            }
                            Startup.tvOn();
                        } else {
                            wait = 1000;   // still switching: check every second
                        }
                    }
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, "wheel-tv");
        tvThread.start();
    }

    private boolean screenWatch;
    private final android.content.BroadcastReceiver screen = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                Govee.tvOn(c);
            } else if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                Govee.tvOff(c);
            }
        }
    };

    @Override
    public void onDestroy() {
        stopLogWatch();
        Messages.removeListener(onMessage);
        tvWatching = false;
        if (tvThread != null) tvThread.interrupt();
        if (screenWatch) {
            try {
                unregisterReceiver(screen);
            } catch (Exception ignored) {
            }
            screenWatch = false;
        }
        removeOverlay();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    // ---------------------------------------------------------------- messages over other apps

    private static volatile HomeWatcher instance;
    private MessageOverlay overlay;
    /** Like a phone's notification icon: shows this long, then goes away. */
    private static final long BANNER_MS = 10 * 1000L;

    private final Messages.Listener onMessage = new Messages.Listener() {
        @Override
        public void onMessage(String text, boolean mail) {
            // On Wheel, Wheel draws the banner itself. Do Not Disturb: it waits for Wheel.
            if (MainActivity.onScreen || Messages.dnd(HomeWatcher.this)) return;
            showOverlay(mail);
        }
    };

    private final Runnable iconTimeout = new Runnable() {
        @Override
        public void run() {
            removeOverlay();   // it stays on Wheel's card as MSG for a while
        }
    };

    private void showOverlay(boolean mail) {
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
            overlay.show(mail);
            // Still unread: back on Wheel the icon shows again and ☰ opens it.
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
