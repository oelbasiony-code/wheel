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
 *  2. Morning briefing (the first time the TV comes on before 11 am): good morning,
 *     the weather, the first thing on the calendar and the next train, typed out line
 *     by line, then it settles into the wheel.
 * Any button skips it. Drawn by AmbientView (the only layer that animates).
 */
final class Startup {

    private static final long FLY_MS = 950, HOLD_MS = 650, SWEEP_MS = 700;
    private static final long BOOT_MS = FLY_MS + HOLD_MS + SWEEP_MS;
    private static final long BRIEF_IN_MS = 400, BRIEF_MS = 10000, BRIEF_OUT_MS = 600;
    private static final int BRIEF_BEFORE_HOUR = 11;

    private static final int CREAM = 0xFFEDE6DA, TEAL = 0xFF9AD0C2, DIM = 0xFF6E6A62;
    private static final int GOLD = 0xFFE8C872, BLUE = 0xFF8FB8DE;

    /** Set when the TV turns on (and when Wheel first starts). */
    private static volatile boolean bootPending = true;

    private static long bootAt = -1;      // uptime; -1 = not playing
    private static long briefAt = -1;
    private static float[] pts;
    private static float[] from;

    /** Set by Wheel while it's on screen, in case the TV comes on with Wheel already up. */
    static volatile Runnable whileShown;

    static void tvOn() {
        bootPending = true;
        Runnable r = whileShown;
        if (r != null) r.run();
    }

    /** Wheel is showing: play the boot if the TV just came on. */
    static void onWheelShown(Context c) {
        if (!bootPending) return;
        bootPending = false;
        boolean brief = briefingDue(c);
        if (brief) markBriefed(c);
        play(brief);
    }

    static void play(boolean withBriefing) {
        bootAt = SystemClock.uptimeMillis();
        briefAt = withBriefing ? bootAt + BOOT_MS : -1;
        pts = null;
    }

    static void playBriefing() {
        bootAt = -1;
        briefAt = SystemClock.uptimeMillis();
    }

    static boolean showing() {
        long up = SystemClock.uptimeMillis();
        if (bootAt >= 0 && up - bootAt < BOOT_MS) return true;
        return briefAt >= 0 && up >= briefAt - 1 && up - briefAt < BRIEF_MS + BRIEF_OUT_MS;
    }

    /** Any button: skip what's playing. The briefing fades out instead of cutting. */
    static void skip() {
        long up = SystemClock.uptimeMillis();
        if (bootAt >= 0 && up - bootAt < BOOT_MS) {
            bootAt = -1;
            if (briefAt >= 0) briefAt = up;   // a press during boot goes straight to the briefing
            return;
        }
        if (briefAt >= 0 && up - briefAt < BRIEF_MS) briefAt = up - BRIEF_MS;
    }

    private static boolean briefingDue(Context c) {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.HOUR_OF_DAY) >= BRIEF_BEFORE_HOUR) return false;
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
        if (bootAt >= 0) {
            long t = up - bootAt;
            if (t < BOOT_MS) {
                drawBoot(c, dot, w, h, t);
                return;
            }
            bootAt = -1;
        }
        if (briefAt >= 0) {
            long t = up - briefAt;
            if (t < 0) return;
            if (t < BRIEF_MS + BRIEF_OUT_MS) drawBriefing(c, dot, w, h, t, wheel);
            else briefAt = -1;
        }
    }

    private static float ease(float k) {
        k = Math.max(0f, Math.min(1f, k));
        return 1f - (1f - k) * (1f - k) * (1f - k);
    }

    private static int alpha(int color, float a) {
        int ca = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (color & 0x00FFFFFF) | (ca << 24);
    }

    private static void drawBoot(Canvas c, Paint dot, float w, float h, long t) {
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

        // The scan line: everything right of it is still the boot screen.
        float edge = 0;
        if (t > FLY_MS + HOLD_MS) edge = ease((t - FLY_MS - HOLD_MS) / (float) SWEEP_MS) * (w + p * 6);
        dot.setColor(0xFF000000);
        c.drawRect(edge, 0, w, h, dot);

        int n = pts.length / 2;
        float r = p * 0.36f;
        for (int i = 0; i < n; i++) {
            float tx = pts[2 * i], ty = pts[2 * i + 1];
            // Staggered left to right, so the words assemble as you read them.
            float delay = (tx / Math.max(1, cols)) * FLY_MS * 0.45f;
            float k = ease((t - delay) / (FLY_MS * 0.55f));
            float fx = x0 + (from[2 * i] + (tx - from[2 * i]) * k) * p + p / 2;
            float fy = y0 + (from[2 * i + 1] + (ty - from[2 * i + 1]) * k) * p + p / 2;
            if (fx < edge) continue;                // already swept away
            boolean landed = k >= 1f;
            dot.setColor(alpha(landed ? CREAM : TEAL, 0.25f + 0.75f * k));
            c.drawCircle(fx, fy, landed ? r : r * 0.7f, dot);
        }

        if (edge > 0 && edge < w) {
            // A dotted vertical line, brighter in the middle, like a scanner.
            for (float y = p; y < h; y += p * 1.2f) {
                float mid = 1f - Math.abs(y - h / 2f) / (h / 2f);
                dot.setColor(alpha(TEAL, 0.25f + 0.75f * mid));
                c.drawCircle(edge, y, p * 0.3f, dot);
            }
        }
    }

    private static void drawBriefing(Canvas c, Paint dot, float w, float h, long t, WheelView wheel) {
        float in = ease(t / (float) BRIEF_IN_MS);
        float out = t > BRIEF_MS ? 1f - (t - BRIEF_MS) / (float) BRIEF_OUT_MS : 1f;
        float a = Math.min(in, out);
        dot.setColor(alpha(0xF2000000, a));
        c.drawRect(0, 0, w, h, dot);

        long now = System.currentTimeMillis();
        TimeZone tz = TimeZone.getDefault();
        float p = h / 140f;                         // body text pitch
        float big = h / 70f;                        // greeting pitch
        float x = w * 0.16f;
        float y = h * 0.22f;
        float r = 0.36f;

        String hello = "GOOD MORNING, " + Config.NAME;
        DotFont.draw(c, hello, x, y, big, big * r, dot, new int[]{CREAM}, a);
        y += 7 * big + p * 4;
        String date = DotFont.clean(new SimpleDateFormat("EEEE MMMM d", Locale.US).format(new Date(now)));
        DotFont.draw(c, date, x, y, p, p * r, dot, new int[]{DIM}, a);
        y += 7 * p + p * 12;

        String weather = wheel.weatherNow();
        String range = wheel.weatherRange();
        String wLine = weather == null ? "LOADING" : weather + (range != null ? "   " + range : "");
        String[] ev = wheel.firstEvent(now);
        String eLine = ev == null ? "NOTHING ON THE CALENDAR" : ev[0] + "  " + ev[1];
        long train = wheel.nextTrain(now);
        String tLine = train <= 0 ? "NO TRAINS SOON" : Agenda.clock(train, tz) + "  TO " + Config.TRAIN_TO;

        String[][] rows = {
                {"WEATHER", wLine},
                {"FIRST UP", eLine},
                {"NEXT TRAIN", tLine},
        };
        int[] labelColors = {BLUE, CREAM, GOLD};
        float labelW = DotFont.cols("NEXT TRAIN") * p * 0.7f + p * 6;
        for (int i = 0; i < rows.length; i++) {
            // Each line types itself out, a moment after the one above.
            long start = 700 + i * 650L;
            if (t < start) break;
            String val = DotFont.clean(rows[i][1]);
            int chars = (int) Math.min(val.length(), (t - start) / 28);
            float lp = p * 0.7f;
            DotFont.draw(c, rows[i][0], x, y + (7 * p - 7 * lp) / 2f, lp, lp * r, dot,
                    new int[]{labelColors[i]}, a);
            DotFont.draw(c, val.substring(0, chars), x + labelW, y, p, p * r, dot, new int[]{CREAM}, a);
            if (chars < val.length() && (t / 120) % 2 == 0) {
                // Typing cursor.
                float cx = x + labelW + DotFont.cols(val.substring(0, chars)) * p + p;
                dot.setColor(alpha(TEAL, a));
                for (int k = 0; k < 7; k++) c.drawCircle(cx + p / 2, y + k * p + p / 2, p * r, dot);
            }
            y += 7 * p + p * 7;
        }
    }

    private Startup() {}
}
