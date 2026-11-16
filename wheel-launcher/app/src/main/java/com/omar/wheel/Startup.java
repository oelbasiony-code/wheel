package com.omar.wheel;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/**
 * What plays when the TV turns on:
 *  1. Boot (~2.3 s): dots fly in from everywhere and settle into "WELCOME BACK, OMAR",
 *     then a scan line sweeps left to right and the wheel appears behind it.
 *  2. The daily briefing (the first time the TV comes on at 5 am or later, once a day):
 *     the panel's greeting (GOOD MORNING / AFTERNOON / EVENING), the weather, the first
 *     thing on the calendar and the next train, gliding in line by line.
 * Any button skips it. Drawn by AmbientView (the only layer that animates).
 */
final class Startup {

    private static final long FLY_MS = 2400, HOLD_MS = 1400, OUT_MS = 2000, FADE_MS = 700;
    private static final long BOOT_MS = FLY_MS + HOLD_MS + OUT_MS + FADE_MS;
    private static final long BRIEF_IN_MS = 1000, BRIEF_MS = 11000, BRIEF_OUT_MS = 1600;
    /** The day's briefing waits for the first time the TV comes on at or after this hour. */
    private static final int BRIEF_FROM_HOUR = 5;

    private static final int CREAM = 0xFFEDE6DA, TEAL = 0xFF9AD0C2, DIM = 0xFF6E6A62;
    private static final int GOLD = 0xFFE8C872, BLUE = 0xFF8FB8DE;

    /** Set when the TV turns on (and when Wheel first starts). */
    private static volatile boolean bootPending = true;

    /** Phone commands (see Commands): what to play next time Wheel shows. */
    static final int NONE = 0, HOME = 1, WAKE = 2;
    private static volatile int queued = NONE;
    /** A phone command is turning the TV on: the normal boot stays out of it for a bit. */
    private static volatile long noAutoBootUntil;
    /** The briefing's top line when it isn't the panel's greeting (WELCOME HOME, OMAR). */
    private static volatile String briefTitle;
    /** The briefing (and WELCOME HOME) stays up until a button is pressed (Omar's call). */
    private static volatile boolean briefHold;

    /** The briefing's clock, frozen just before it would leave while it's held. */
    private static long briefT(long up) {
        long t = up - briefAt;
        return briefHold ? Math.min(t, BRIEF_MS - 1) : t;
    }

    /** Still moving (else AmbientView can drop back to its slow frame rate). */
    static boolean animating() {
        if (dark && bootAt < 0 && briefAt < 0) return false;
        long up = SystemClock.uptimeMillis();
        if (bootAt >= 0 && up - bootAt < BOOT_MS) return true;
        return briefAt >= 0 && !(briefHold && up - briefAt > 5000);
    }

    static void holdAutoBoot() {
        noAutoBootUntil = SystemClock.uptimeMillis() + 2 * 60 * 1000L;
    }

    /** Plays HOME (welcome home briefing) or WAKE (the briefing) as soon as Wheel shows. */
    static void queue(int kind) {
        holdAutoBoot();
        queued = kind;
        Runnable r = whileShown;
        if (r != null) r.run();
    }

    private static long bootAt = -1;      // uptime; -1 = not playing
    private static long briefAt = -1;
    private static float[] pts;
    private static float[] from;

    /** Set by Wheel while it's on screen, in case the TV comes on with Wheel already up. */
    static volatile Runnable whileShown;

    /**
     * The TV went off: Wheel goes black right away and stays black until the boot starts,
     * so turning the TV on never shows a glimpse of the wheel first.
     */
    private static volatile boolean dark;

    static void tvOff() {
        dark = true;
    }

    /** The TV came on but never went to the Fire Stick: stop waiting in the dark. */
    static void clearDark() {
        dark = false;
    }

    static void tvOn() {
        if (SystemClock.uptimeMillis() < noAutoBootUntil) return;   // a phone command has it
        bootPending = true;
        Runnable r = whileShown;
        if (r != null) r.run();
    }

    /** Wheel is showing: play the boot if the TV just came on. */
    static void onWheelShown(Context c) {
        int k = queued;
        if (k != NONE) {
            queued = NONE;
            bootPending = false;
            if (k == HOME) {
                // Straight to the briefing, under "WELCOME HOME, OMAR".
                playBriefing();
                briefTitle = "WELCOME HOME, " + Config.NAME;
                briefHold = true;
            } else {
                // Waking up: straight to the briefing (GOOD ___ by the time), no boot,
                // even if it already played today.
                markBriefed(c);
                playBriefing();
            }
            return;
        }
        if (!bootPending) return;
        bootPending = false;
        boolean brief = briefingDue(c);
        if (brief) markBriefed(c);
        play(brief);
    }

    static void play(boolean withBriefing) {
        dark = false;          // the boot draws its own black from the first frame
        bootPending = false;   // a demo replaces the automatic one
        briefTitle = null;
        briefHold = true;      // every briefing stays until a button (Omar's call)
        bootAt = SystemClock.uptimeMillis();
        briefAt = withBriefing ? bootAt + BOOT_MS : -1;
        pts = null;
    }

    static void playBriefing() {
        dark = false;
        bootPending = false;
        briefTitle = null;
        briefHold = true;
        bootAt = -1;
        briefAt = SystemClock.uptimeMillis();
    }

    static boolean showing() {
        if (dark) return true;
        long up = SystemClock.uptimeMillis();
        if (bootAt >= 0 && up - bootAt < BOOT_MS) return true;
        return briefAt >= 0 && up >= briefAt - 1 && briefT(up) < BRIEF_MS + BRIEF_OUT_MS;
    }

    /** True while the screen is fully covered (nothing underneath needs drawing). */
    static boolean coversAll() {
        if (dark) return true;
        long up = SystemClock.uptimeMillis();
        if (bootAt >= 0 && up - bootAt < FLY_MS + HOLD_MS + OUT_MS) return true;
        return briefAt >= 0 && up - briefAt > BRIEF_IN_MS && briefT(up) < BRIEF_MS;
    }

    /** Any button: skip what's playing. The briefing fades out instead of cutting. */
    static void skip() {
        if (dark) {
            dark = false;   // a button while waiting in the dark: just show the wheel
            return;
        }
        long up = SystemClock.uptimeMillis();
        if (bootAt >= 0 && up - bootAt < BOOT_MS) {
            bootAt = -1;
            if (briefAt >= 0) briefAt = up;   // a press during boot goes straight to the briefing
            return;
        }
        if (briefAt >= 0 && briefT(up) < BRIEF_MS) {
            briefHold = false;
            briefAt = up - BRIEF_MS;   // fade out now
        }
    }

    private static boolean briefingDue(Context c) {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.HOUR_OF_DAY) < BRIEF_FROM_HOUR) return false;   // late nights don't use it up
        return !today().equals(sp(c).getString("briefed_on", ""));
    }

    private static void markBriefed(Context c) {
        sp(c).edit().putString("briefed_on", today()).apply();
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("wheel", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- drawing

    static void draw(Canvas c, Paint dot, float w, float h, WheelView wheel) {
        long up = SystemClock.uptimeMillis();
        if (dark && bootAt < 0 && briefAt < 0) {
            dot.setColor(0xFF000000);
            c.drawRect(0, 0, w, h, dot);
            return;
        }
        if (bootAt >= 0) {
            long t = up - bootAt;
            if (t < BOOT_MS) {
                drawBoot(c, dot, w, h, t, wheel);
                return;
            }
            bootAt = -1;
        }
        if (briefAt >= 0) {
            long t = briefT(up);
            if (t < 0) return;
            if (t < BRIEF_MS + BRIEF_OUT_MS) drawBriefing(c, dot, w, h, t, wheel);
            else briefAt = -1;
        }
    }

    private static float ease(float k) {
        k = Math.max(0f, Math.min(1f, k));
        return 1f - (1f - k) * (1f - k) * (1f - k);
    }

    /** Slow start, slow end: for anything that slides across the screen. */
    private static float smooth(float k) {
        k = Math.max(0f, Math.min(1f, k));
        return k * k * k * (k * (k * 6f - 15f) + 10f);
    }

    private static int alpha(int color, float a) {
        int ca = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (color & 0x00FFFFFF) | (ca << 24);
    }


    private static void drawBoot(Canvas c, Paint dot, float w, float h, long t, WheelView wheel) {
        String text = "WELCOME BACK, " + Config.NAME;
        float p = h / 90f;                          // dot pitch: big, centered
        if (pts == null) {
            pts = DotFont.points(text);
            from = new float[pts.length];
            Random r = new Random(7);
            for (int i = 0; i < pts.length; i += 2) {
                // Start anywhere on screen (in dot units relative to the text's corner).
                from[i] = (r.nextFloat() * 1.4f - 0.2f) * w / p;
                from[i + 1] = (r.nextFloat() * 1.4f - 0.2f) * h / p;
            }
        }
        int cols = DotFont.cols(text);
        float x0 = (w - cols * p) / 2f, y0 = (h - 7 * p) / 2f;

        // Black behind it until the dots have flown off, then it fades to the wheel.
        long fadeAt = FLY_MS + HOLD_MS + OUT_MS;
        float bg = t < fadeAt ? 1f : 1f - smooth((t - fadeAt) / (float) FADE_MS);
        dot.setColor(alpha(0xFF000000, bg));
        c.drawRect(0, 0, w, h, dot);
        if (t >= fadeAt) return;

        int n = pts.length / 2;
        float r = p * 0.36f;
        long outAt = FLY_MS + HOLD_MS;
        for (int i = 0; i < n; i++) {
            float tx = pts[2 * i], ty = pts[2 * i + 1];
            // Staggered left to right, so the words assemble as you read them...
            float stagger = tx / Math.max(1, cols);
            float k = smooth((t - stagger * FLY_MS * 0.35f) / (FLY_MS * 0.65f));
            // ...and leave the same way they came, back to where they started.
            if (t > outAt) {
                k = 1f - smooth((t - outAt - stagger * OUT_MS * 0.35f) / (OUT_MS * 0.65f));
            }
            if (k <= 0f) continue;
            float fx = x0 + (from[2 * i] + (tx - from[2 * i]) * k) * p + p / 2;
            float fy = y0 + (from[2 * i + 1] + (ty - from[2 * i + 1]) * k) * p + p / 2;
            boolean landed = k >= 1f;
            dot.setColor(alpha(landed ? CREAM : TEAL, 0.25f + 0.75f * k));
            c.drawCircle(fx, fy, landed ? r : r * 0.7f, dot);
        }
    }

    private static void drawBriefing(Canvas c, Paint dot, float w, float h, long t, WheelView wheel) {
        float in = smooth(t / (float) BRIEF_IN_MS);
        float out = t > BRIEF_MS ? smooth((t - BRIEF_MS) / (float) BRIEF_OUT_MS) : 0f;
        float bg = in * (1f - out);
        dot.setColor(alpha(0xFA000000, bg));
        c.drawRect(0, 0, w, h, dot);

        long now = System.currentTimeMillis();
        TimeZone tz = TimeZone.getDefault();
        float p = h / 150f;                         // body text pitch
        float big = h / 95f;                        // greeting pitch
        float lp = p * 0.7f;                        // label pitch
        float r = 0.36f;

        String bt = briefTitle;
        String hello = DotFont.clean(bt != null ? bt : WheelView.greeting(now));   // else the panel's greeting
        String date = DotFont.clean(new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date(now)));
        String weather = wheel.weatherNow();
        String range = wheel.weatherRange();
        String wLine = weather == null ? "LOADING" : weather + (range != null ? "  " + range : "");
        String[] ev = wheel.firstEvent(now);
        String eLine = ev == null ? "NOTHING ON THE CALENDAR" : ev[0] + " " + ev[1];
        // What came in while the TV was off.
        int[] m = Messages.missed(wheel.getContext(), now);
        String mLine;
        if (m[0] == 0 && m[1] == 0) {
            mLine = "NO NEW MAIL/MESSAGES";
        } else {
            mLine = "";
            if (m[0] > 0) mLine = m[0] + (m[0] == 1 ? " MESSAGE" : " MESSAGES");
            if (m[1] > 0) mLine += (mLine.isEmpty() ? "" : ", ") + m[1] + " MAIL";
        }
        String[] labels = {"WEATHER", "MSG/MAIL", "CAL"};   // the card's labels and colors
        String[] vals = {DotFont.clean(wLine), DotFont.clean(mLine), DotFont.clean(eLine)};
        int[] labelColors = {BLUE, WheelView.TICK_MSG, WheelView.TICK_CAL};

        // Lay the whole thing out as one block, centered on screen.
        float labelW = DotFont.cols("MSG/MAIL") * lp + p * 6;
        // MSG in teal, the slash dim, MAIL in orange (colors go letter by letter).
        int[] msgMail = {WheelView.TICK_MSG, WheelView.TICK_MSG, WheelView.TICK_MSG, 0xFF6E6A62,
                WheelView.TICK_MAIL, WheelView.TICK_MAIL, WheelView.TICK_MAIL, WheelView.TICK_MAIL};
        float rowsW = 0;
        for (String v : vals) rowsW = Math.max(rowsW, labelW + DotFont.cols(v) * p);
        float helloW = DotFont.cols(hello) * big, dateW = DotFont.cols(date) * p;
        float blockW = Math.max(helloW, rowsW);
        float rowH = 7 * p, rowGap = p * 7;
        float blockH = 7 * big + p * 5 + 7 * p + p * 14 + 3 * rowH + 2 * rowGap;
        float top = (h - blockH) / 2f;
        float left = (w - blockW) / 2f;

        // Everything glides up a little as it fades in, one piece after another,
        // and drifts up as the briefing leaves.
        float lift = p * 6;
        float leave = out * p * 10;

        float k = smooth((t - 300) / 1400f) * (1f - out);
        float y = top + (1f - k) * lift - leave;
        DotFont.draw(c, hello, (w - helloW) / 2f, y, big, big * r, dot, new int[]{CREAM}, k);
        y += 7 * big + p * 5;
        k = smooth((t - 700) / 1400f) * (1f - out);
        DotFont.draw(c, date, (w - dateW) / 2f, y + (1f - k) * lift - leave, p, p * r, dot,
                new int[]{DIM}, k);
        y = top + 7 * big + p * 5 + 7 * p + p * 14;

        // A faint dotted rule between the greeting and the rows.
        float rk = smooth((t - 1100) / 1600f) * (1f - out);
        float ruleY = y - p * 7 - leave;
        float half = blockW / 2f * rk;
        dot.setColor(alpha(0xFF3A3834, rk));
        for (float x = w / 2f - half; x <= w / 2f + half; x += p * 1.6f) {
            c.drawCircle(x, ruleY, p * 0.3f, dot);
        }

        for (int i = 0; i < 3; i++) {
            k = smooth((t - 1500 - i * 550L) / 1400f) * (1f - out);
            if (k > 0f) {
                float ry = y + (1f - k) * lift - leave;
                DotFont.draw(c, labels[i], left, ry + (rowH - 7 * lp) / 2f, lp, lp * r, dot,
                        i == 1 ? msgMail : new int[]{labelColors[i]}, k);
                DotFont.draw(c, vals[i], left + labelW, ry, p, p * r, dot, new int[]{CREAM}, k);
            }
            y += rowH + rowGap;
        }
    }

    private Startup() {}
}
