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
    private boolean menuDown, menuLongFired;

    /** Holding ☰ opens the inbox (a short press is the usual ☰). */
    private final Runnable menuLong = new Runnable() {
        @Override
        public void run() {
            if (menuDown) {
                menuLongFired = true;
                ambient.openInbox();
            }
        }
    };
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

    private static final long AGENDA_MAX_AGE_MS = 10 * 60 * 1000L;
    private boolean fetchingAgenda;
    private long lastAgenda;

    /** Your calendar: reloaded every 10 minutes while Wheel is on screen. */
    private final Runnable agendaLoop = new Runnable() {
        @Override
        public void run() {
            if (!fetchingAgenda && System.currentTimeMillis() - lastAgenda > AGENDA_MAX_AGE_MS) {
                fetchingAgenda = true;
                Agenda.fetch(MainActivity.this, h, new Agenda.Callback() {
                    @Override
                    public void onAgenda(java.util.List<Agenda.Event> events) {
                        fetchingAgenda = false;
                        long now = System.currentTimeMillis();
                        wheel.setCalendarNote(!Agenda.hasLink(MainActivity.this) ? "NO LINK YET"
                                : events == null ? "CAN'T LOAD"
                                : Agenda.lines(events, now, java.util.TimeZone.getDefault(), 1).isEmpty()
                                        ? "NOTHING TODAY" : null);
                        if (events != null) {
                            lastAgenda = System.currentTimeMillis();
                            wheel.setAgenda(events);
                        } else {
                            lastAgenda = System.currentTimeMillis() - AGENDA_MAX_AGE_MS + 60 * 1000L;
                        }
                    }
                });
            }
            h.postDelayed(this, 60 * 1000L);
        }
    };

    private static final long TRAIN_EVERY_MS = 3 * 60 * 1000L;
    private boolean fetchingTrains;
    private long lastTrains;

    /** Next trains: every 3 minutes while Wheel is on screen (live delays change fast). */
    private final Runnable trainLoop = new Runnable() {
        @Override
        public void run() {
            if (Config.TRAIN && !fetchingTrains && System.currentTimeMillis() - lastTrains > TRAIN_EVERY_MS) {
                fetchingTrains = true;
                Train.fetch(h, new Train.Callback() {
                    @Override
                    public void onTrains(java.util.List<Long> departures) {
                        fetchingTrains = false;
                        if (departures != null) {
                            lastTrains = System.currentTimeMillis();
                            wheel.setTrains(departures);
                        }
                    }
                });
            }
            h.postDelayed(this, 60 * 1000L);
        }
    };

    private final Runnable weatherLoop = new Runnable() {
        @Override
        public void run() {
            if (!fetchingWeather && System.currentTimeMillis() - lastWeather > WEATHER_MAX_AGE_MS) {
                fetchingWeather = true;
                Weather.fetch(h, new Weather.Callback() {
                    @Override
                    public void onWeather(String now, String range, int code, boolean isDay,
                                          Weather.Extra extra) {
                        fetchingWeather = false;
                        if (now != null) {
                            lastWeather = System.currentTimeMillis();
                            wheel.setWeather(now, range, code, isDay);
                            wheel.setWeatherExtra(extra);
                            Weather.save(MainActivity.this, now, range, code, isDay);
                        } else {
                            // Neither service answered: try again in a minute, not 5.
                            h.removeCallbacks(weatherLoop);
                            h.postDelayed(weatherLoop, 60 * 1000L);
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
        // The last good weather right away (and through an outage) until a fresh one arrives.
        Object[] wx = Weather.saved(this);
        if (wx != null) wheel.setWeather((String) wx[0], (String) wx[1], (Integer) wx[2], (Boolean) wx[3]);
        checkDemo(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent i) {
        super.onNewIntent(i);
        checkDemo(i);
    }

    /**
     * `am start ... --ez train_demo true` sends a train across right away, to try it out.
     * `am start ... --es calendar_url 'LINK LINK'` saves the private calendar link(s) in the
     * app's private storage (Fire OS won't let adb write into the app's folder directly).
     */
    private void checkDemo(android.content.Intent i) {
        if (i == null) return;
        if (i.getBooleanExtra("train_demo", false)) {
            i.removeExtra("train_demo");
            ambient.demoTrain();
        }
        if (i.getBooleanExtra("boot_demo", false)) {
            i.removeExtra("boot_demo");
            Startup.play(i.getBooleanExtra("briefing", false));
        }
        String cmd = i.getStringExtra("command");   // try a phone command: --es command home
        if (cmd != null) {
            i.removeExtra("command");
            Commands.run(this, cmd);
        }
        if (i.getBooleanExtra("inbox_demo", false)) {
            i.removeExtra("inbox_demo");
            ambient.openInbox();
        }
        if (i.getBooleanExtra("briefing_demo", false)) {
            i.removeExtra("briefing_demo");
            Startup.playBriefing();
        }
        if (i.getBooleanExtra("idle_demo", false)) {
            i.removeExtra("idle_demo");
            ambient.demoIdle();
        }
        String topic = i.getStringExtra("message_topic");
        if (topic != null) {
            i.removeExtra("message_topic");
            Messages.setTopic(this, topic);
            toast("Message topic saved");
        }
        String goveeIp = i.getStringExtra("govee_ip");
        if (goveeIp != null) {
            i.removeExtra("govee_ip");
            Govee.setIp(this, goveeIp);
            toast("Light strip address saved");
        }
        String goveeKey = i.getStringExtra("govee_key");
        if (goveeKey != null) {
            i.removeExtra("govee_key");
            Govee.setKey(this, goveeKey);
            toast("Govee key saved");
        }
        String link = i.getStringExtra("calendar_url");
        if (link == null) link = i.getStringExtra("calendar_link");   // older name, same thing
        if (link != null) {
            i.removeExtra("calendar_url");
            i.removeExtra("calendar_link");
            toast(Agenda.saveLinks(this, link) ? "Calendar link saved" : "Couldn't save the calendar link");
            lastAgenda = 0;
            h.removeCallbacks(agendaLoop);
            h.post(agendaLoop);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back on Wheel: the Home takeover is always active again.
        HomeWatcher.releaseHold();
        Govee.untint(this);   // lights back to how they were before the app
        swallowKey = -1;
        ambient.setActive(true);
        Startup.onWheelShown(this);   // the TV just came on: boot sequence (+ morning briefing)
        Startup.whileShown = startupNow;
        immersive();
        reload();
        h.removeCallbacks(weatherLoop);
        h.post(weatherLoop);
        h.removeCallbacks(agendaLoop);
        h.post(agendaLoop);
        h.removeCallbacks(trainLoop);
        h.post(trainLoop);
        // Messages: Wheel shows them itself while it's on screen.
        onScreen = true;
        HomeWatcher.hideOverlay();
        Messages.start(this);
        Messages.addListener(onMessage);
        String unread = Messages.unread(this);
        if (unread != null) ambient.showMessage(unread, Messages.unreadIsMail(this));   // the icon
    }

    /** True while Wheel is the app on screen (HomeWatcher then leaves messages to it). */
    static volatile boolean onScreen;

    private final Messages.Listener onMessage = new Messages.Listener() {
        @Override
        public void onMessage(String text, boolean mail) {
            ambient.showMessage(text, mail);
        }
    };

    private final Runnable startupNow = new Runnable() {
        @Override
        public void run() {
            h.post(new Runnable() {
                @Override
                public void run() {
                    Startup.onWheelShown(MainActivity.this);
                }
            });
        }
    };

    @Override
    protected void onPause() {
        Startup.whileShown = null;
        onScreen = false;
        Messages.removeListener(onMessage);
        h.removeCallbacks(weatherLoop);
        h.removeCallbacks(agendaLoop);
        h.removeCallbacks(trainLoop);
        h.removeCallbacks(longPress);
        h.removeCallbacks(menuLong);
        menuDown = false;
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
        // Startup sequence playing: any button skips it (and does nothing else).
        if (Startup.showing()) {
            if (down) {
                Startup.skip();
                swallowKey = e.getKeyCode();
            }
            return true;
        }
        // The inbox: up/down choose, OK opens, Back or ☰ closes.
        if (ambient.inboxShowing()) {
            int k = e.getKeyCode();
            if (down && k == KeyEvent.KEYCODE_DPAD_UP) ambient.inboxMove(-1);
            else if (down && k == KeyEvent.KEYCODE_DPAD_DOWN) ambient.inboxMove(1);
            else if (act == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_DPAD_CENTER
                    || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A)) {
                if (!menuLongFired) ambient.inboxOpenSelected();
            } else if (act == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_BACK
                    || k == KeyEvent.KEYCODE_MENU)) {
                if (k == KeyEvent.KEYCODE_MENU && menuLongFired) {
                    menuLongFired = false;   // the release of the hold that opened it
                    menuDown = false;
                } else {
                    ambient.closeInbox();
                }
            }
            ambient.poke();
            return true;
        }
        // Play/Pause opens the last message big (it's on the card for 2 hours) and closes it.
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (act == KeyEvent.ACTION_UP) {
                if (ambient.messageShowing()) {
                    ambient.dismissMessage();
                } else {
                    String[] last = Messages.latest(this, System.currentTimeMillis());
                    if (last != null) ambient.openMessage(last[0], "mail".equals(last[1]));
                    else toast("No messages");
                }
                swallowKey = -1;
            }
            return true;
        }
        // An opened message: any button closes it (and does nothing else).
        if (ambient.messageShowing() && e.getKeyCode() != swallowKey) {
            if (act == KeyEvent.ACTION_DOWN) swallowKey = e.getKeyCode();
            ambient.poke();
            return true;
        }
        // In idle mode, the first press just wakes the screen.
        if (e.getKeyCode() == swallowKey) {
            if (act == KeyEvent.ACTION_UP && ambient.messageShowing()) {
                ambient.dismissMessage();
                Messages.markRead(this);
            }
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
                if (down && !wheel.onLeftRight(-1)) ambient.stepTicker(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down && !wheel.onLeftRight(1)) ambient.stepTicker(1);
                return true;
            case KeyEvent.KEYCODE_MENU:
                // Hold: the inbox. Press: a message icon in the corner opens; otherwise the
                // all-apps list.
                if (down && e.getRepeatCount() == 0) {
                    menuDown = true;
                    menuLongFired = false;
                    h.postDelayed(menuLong, LONG_PRESS_MS);
                } else if (act == KeyEvent.ACTION_UP) {
                    h.removeCallbacks(menuLong);
                    if (menuDown && !menuLongFired) {
                        if (ambient.iconShowing()) ambient.openIcon();
                        else wheel.onMenu();
                    }
                    menuDown = false;
                }
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
            Govee.tint(this, Govee.colorFor(e.pkg, e.label));   // lights in the app's color
        } catch (Exception ex) {
            toast("Can't open " + e.label);
        }
    }

    private void runShortcut(String action) {
        if (action.startsWith(AppRepo.TV_INPUT)) {
            final String input = action.substring(AppRepo.TV_INPUT.length());
            if (Config.PLAYSTATION_INPUT.equals(input)) Govee.tint(this, Govee.PLAYSTATION);
            else if (Config.XBOX_INPUT.equals(input)) Govee.tint(this, Govee.XBOX);
            RokuTv.switchInput(this, input, new RokuTv.Done() {
                @Override
                public void done(final String error) {
                    if (error == null) return;
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            toast(error);
                        }
                    });
                }
            });
            return;
        }
        if (AppRepo.ACTION_LIGHTS.equals(action)) {
            boolean on = !Govee.followTv(this);
            Govee.setFollowTv(this, on);
            toast(on ? "Lights follow the TV" : "Lights left alone when the TV turns on/off");
            wheel.refresh();
            return;
        }
        if (AppRepo.ACTION_DND.equals(action)) {
            boolean on = !Messages.dnd(this);
            Messages.setDnd(this, on);
            toast(on ? "Do Not Disturb on: messages wait for Wheel" : "Do Not Disturb off");
            wheel.refresh();
            return;
        }
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
