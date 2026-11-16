package com.omar.wheel;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Commands from Omar's iPhone, sent to the same private topic as messages but starting
 * with "!" (never shown as messages). Each is a one-action Shortcut or automation:
 *   !home   arrived home: strip on, TV on to the Fire Stick, WELCOME HOME briefing
 *   !away   left home: strip off, and the TV off if it's only showing Wheel
 *   !wake   alarm stopped (any time): TV on, soft light, the briefing (GOOD ___ by the time)
 *   !ps5    TV on to the PlayStation, strip blue
 *   !xbox   TV on to the Xbox, strip green
 * Runs inside the message listener (HomeWatcher), so it works whatever is on screen.
 */
final class Commands {

    private static final ExecutorService q = Executors.newSingleThreadExecutor();

    private static String lastCmd;
    private static long lastAt;

    static void run(Context ctx, final String cmd) {
        final Context c = ctx.getApplicationContext();
        // The phone sometimes runs an automation twice in a row: do it once.
        long now = System.currentTimeMillis();
        synchronized (Commands.class) {
            if (cmd.equals(lastCmd) && now - lastAt < 30000L) return;
            lastCmd = cmd;
            lastAt = now;
        }
        q.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    handle(c, cmd);
                } catch (Exception e) {
                    Log.w(HomeWatcher.TAG, "command " + cmd + ": " + e);
                }
            }
        });
    }

    private static void handle(Context c, String cmd) throws Exception {
        switch (cmd) {
            case "home":
                Govee.lightsOn(c);
                toFireStick(c, Startup.HOME);
                break;
            case "away":
                Govee.lightsOff(c);
                // Only if nobody's watching something: the TV is just showing Wheel.
                if (MainActivity.onScreen && Boolean.TRUE.equals(RokuTv.screenOn(c))
                        && Config.FIRE_TV_APP_ID.equals(RokuTv.activeApp(c))) {
                    RokuTv.press(c, "PowerOff");
                }
                break;
            case "wake":
                Govee.lightsOn(c);
                toFireStick(c, Startup.WAKE);
                break;
            case "ps5":
                toInput(c, Config.PLAYSTATION_INPUT);
                Govee.tint(c, Govee.PLAYSTATION);
                break;
            case "xbox":
                toInput(c, Config.XBOX_INPUT);
                Govee.tint(c, Govee.XBOX);
                break;
            default:
                Log.w(HomeWatcher.TAG, "unknown command " + cmd);
        }
    }

    /** TV on (if it's off) and over to an input. */
    private static void toInput(Context c, String input) throws InterruptedException {
        RokuTv.wakeOnLan(c);   // a sleeping TV starts waking now, while we ask if it's on
        if (!Boolean.TRUE.equals(RokuTv.screenOn(c))) {
            RokuTv.powerOn(c);
            Thread.sleep(4000);
        }
        RokuTv.press(c, input);
    }

    /**
     * TV on to the Fire Stick with Wheel in front; once the picture is actually up, the
     * welcome (or the wake-up briefing) plays.
     */
    private static void toFireStick(Context c, int kind) throws InterruptedException {
        Startup.holdAutoBoot();   // the TV turning on shouldn't also play the normal boot
        RokuTv.wakeOnLan(c);      // a sleeping TV starts waking now, while we ask if it's on
        boolean wasOn = Boolean.TRUE.equals(RokuTv.screenOn(c));
        if (!wasOn) {
            RokuTv.powerOn(c);
            Thread.sleep(4000);
        }
        if (!Config.FIRE_TV_APP_ID.equals(RokuTv.activeApp(c))) RokuTv.press(c, Config.FIRE_TV_INPUT);
        // Wait until the TV shows the Fire Stick (up to 30 s), then a moment for the picture.
        for (int i = 0; i < 30 && !Config.FIRE_TV_APP_ID.equals(RokuTv.activeApp(c)); i++) Thread.sleep(1000);
        if (!wasOn) Thread.sleep(2500);
        Startup.queue(kind);
        if (!MainActivity.onScreen) {
            Intent i = new Intent(c, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            c.startActivity(i);
        }
    }

    private Commands() {}
}
