package com.omar.wheel;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

public final class MainActivity extends Activity implements WheelView.Host {

    private static final long LONG_PRESS_MS = 550;
    private static final long WEATHER_CHECK_MS = 5 * 60 * 1000L;
    private static final long WEATHER_MAX_AGE_MS = 15 * 60 * 1000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private AppRepo repo;
    private WheelView wheel;
    private AmbientView ambient;
    /** A key that woke idle mode; its release is swallowed too. */
    private int swallowKey = -1;

    private boolean okDown, okLongFired;
    private boolean fetchingWeather;
    private long lastWeather;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (okDown) {
                okLongFired = true;
                wheel.onOkLong();
            }
        }
    };

    private final Runnable weatherLoop = new Runnable() {
        @Override
        public void run() {
            if (!fetchingWeather && System.currentTimeMillis() - lastWeather > WEATHER_MAX_AGE_MS) {
                fetchingWeather = true;
                Weather.fetch(h, new Weather.Callback() {
                    @Override
                    public void onWeather(String now, String range, int code, boolean isDay) {
                        fetchingWeather = false;
                        if (now != null) {
                            lastWeather = System.currentTimeMillis();
                            wheel.setWeather(now, range, code, isDay);
                        }
                    }
                });
            }
            h.postDelayed(this, WEATHER_CHECK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        repo = new AppRepo(this);
        wheel = new WheelView(this, this, repo);
        ambient = new AmbientView(this, wheel);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(wheel);
        root.addView(ambient);
        setContentView(root);
        wheel.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back on Wheel: the Home takeover is always active again.
        HomeWatcher.releaseHold();
        swallowKey = -1;
        ambient.setActive(true);
        immersive();
        reload();
        h.removeCallbacks(weatherLoop);
        h.post(weatherLoop);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(weatherLoop);
        h.removeCallbacks(longPress);
        ambient.setActive(false);
        okDown = false;
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    private void immersive() {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= 19) flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    /** Rescans apps in the background (picks up installs and uninstalls). */
    private void reload() {
        final AppRepo r = repo;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final AppRepo.Snapshot s = r.scan();
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        r.apply(s);
                        wheel.refresh();
                    }
                });
            }
        }, "wheel-scan").start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int act = e.getAction();
        boolean down = act == KeyEvent.ACTION_DOWN;
        // In idle mode, the first press just wakes the screen.
        if (e.getKeyCode() == swallowKey) {
            if (act == KeyEvent.ACTION_UP) swallowKey = -1;
            return true;
        }
        if (down && ambient.isIdle()) {
            ambient.poke();
            swallowKey = e.getKeyCode();
            return true;
        }
        ambient.poke();
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (down && e.getRepeatCount() == 0) {
                    okDown = true;
                    okLongFired = false;
                    h.postDelayed(longPress, LONG_PRESS_MS);
                } else if (act == KeyEvent.ACTION_UP) {
                    h.removeCallbacks(longPress);
                    if (okDown && !okLongFired) wheel.onOk();
                    okDown = false;
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) wheel.onUpDown(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) wheel.onUpDown(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) wheel.onLeftRight(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) wheel.onLeftRight(1);
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (act == KeyEvent.ACTION_UP) wheel.onMenu();
                return true;
            case KeyEvent.KEYCODE_BACK:
                // A home screen doesn't close on Back; Home still goes to Amazon's.
                if (act == KeyEvent.ACTION_UP) wheel.onBack();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    @Override
    public void launch(AppEntry e) {
        if (e.action != null) {
            runShortcut(e.action);
            return;
        }
        if (e.missing) {
            toast("Couldn't find " + e.label + " on this Fire Stick");
            return;
        }
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(e.pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(e.pkg);
        if (i == null) {
            toast("Can't open " + e.label);
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception ex) {
            toast("Can't open " + e.label);
        }
    }

    private void runShortcut(String action) {
        Intent i;
        if (AppRepo.ACTION_SETTINGS.equals(action)) {
            // Settings opens via Amazon's home screen for a split second; let that through.
            HomeWatcher.expectSettings();
            i = new Intent(android.provider.Settings.ACTION_SETTINGS);
        } else {
            // Stay on Amazon's home screen until Home is pressed.
            HomeWatcher.holdForAmazonHome();
            i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setPackage(HomeWatcher.AMAZON_LAUNCHER);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception ex) {
            HomeWatcher.releaseHold();
            toast("Couldn't open that on this Fire Stick");
        }
    }

    @Override
    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
