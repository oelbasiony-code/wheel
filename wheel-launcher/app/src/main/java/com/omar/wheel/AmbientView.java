package com.omar.wheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A see-through layer over the wheel for everything that moves: the dinosaur,
 * the weather icon, and idle mode. Only this layer redraws ~25 times a second;
 * the wheel underneath still redraws once a second, which keeps the stick fast.
 */
final class AmbientView extends View {

    private static final long FRAME_MS = 40;
    private static final long IDLE_AFTER_MS = Config.IDLE_MINUTES * 60 * 1000L;
    private static final float FADE_IN_PER_MS = 1f / 1500f;   // into idle: 1.5 s
    private static final float FADE_OUT_PER_MS = 1f / 350f;   // waking up: 0.35 s
    private static final float IDLE_BRIGHTNESS = 0.7f;

    private static final int[] CLOCK = {0xFFEDE6DA};
    private static final int[] DIM = {0xFFA9A398};

    private final WheelView wheel;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat hm = new SimpleDateFormat("h:mm", Locale.US);
    private final SimpleDateFormat ampm = new SimpleDateFormat("a", Locale.US);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEE MMM d", Locale.US);

    private boolean active;
    private long lastInput = SystemClock.uptimeMillis();
    private long lastFrame;
    private float idle;          // 0 = normal, 1 = fully in idle mode
    private boolean waking;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    AmbientView(Context c, WheelView wheel) {
        super(c);
        this.wheel = wheel;
        setFocusable(false);
        setWillNotDraw(false);
        dot.setStyle(Paint.Style.FILL);
    }

    /** Called from onResume / onPause so nothing animates while you're in an app. */
    void setActive(boolean on) {
        active = on;
        removeCallbacks(frame);
        if (on) {
            lastInput = SystemClock.uptimeMillis();
            lastFrame = 0;
            post(frame);
        } else {
            idle = 0;
            wheel.setCovered(false);
        }
    }

    boolean isIdle() {
        return idle > 0.02f && !waking;
    }

    /** Any remote press counts as activity and wakes idle mode. */
    void poke() {
        lastInput = SystemClock.uptimeMillis();
        if (idle > 0) waking = true;
    }

    @Override
    protected void onDraw(Canvas c) {
        long up = SystemClock.uptimeMillis();
        long now = System.currentTimeMillis();
        long dt = lastFrame == 0 ? 0 : Math.min(200, up - lastFrame);
        lastFrame = up;

        boolean wantIdle = Config.IDLE_MINUTES > 0 && up - lastInput > IDLE_AFTER_MS;
        if (waking || !wantIdle) {
            idle = Math.max(0f, idle - dt * FADE_OUT_PER_MS);
            if (idle == 0f) waking = false;
        } else {
            idle = Math.min(1f, idle + dt * FADE_IN_PER_MS);
        }
        wheel.setCovered(idle >= 1f);

        float h = getHeight();
        float normal = 1f - idle;
        Season.Theme theme = Season.today(now);
        if (normal > 0.01f) {
            Season.drawEffect(c, dot, theme.fx, getWidth(), h, now, normal);
            Doodles.drawDino(c, dot, wheel.dinoLeft, wheel.dinoRight, wheel.dinoBottom,
                    h / 200f, now, normal, theme.wear);
            // The month's icon sits right after the date, a bit taller than the text.
            Season.drawIcon(c, dot, theme.icon, wheel.dateRight + wheel.datePitch * 3,
                    wheel.dateTop + wheel.datePitch * 3.5f, wheel.datePitch * 11f, now, normal);
            Doodles.drawWeather(c, dot, wheel.iconX, wheel.iconTop, h / 120f,
                    wheel.weatherCode(), wheel.weatherDay(), now, normal);
        }
        if (idle > 0.01f) {
            c.drawColor(Doodles.withAlpha(0xFF000000, idle));
            Season.drawEffect(c, dot, theme.fx, getWidth(), h, now, idle * IDLE_BRIGHTNESS);
            drawIdle(c, now, idle * IDLE_BRIGHTNESS, theme);
        }
    }

    /** A big clock that drifts slowly around the screen (no burn-in), with the dino on top. */
    private void drawIdle(Canvas c, long now, float alpha, Season.Theme theme) {
        float w = getWidth(), h = getHeight();
        TimeZone tz = TimeZone.getDefault();
        hm.setTimeZone(tz);
        ampm.setTimeZone(tz);
        dateFmt.setTimeZone(tz);
        Date d = new Date(now);
        String time = hm.format(d);
        String ap = ampm.format(d).toUpperCase(Locale.US);
        String date = dateFmt.format(d).toUpperCase(Locale.US);
        String wx = wheel.weatherNow();

        float cp = h / 34f;                       // clock dot pitch
        float sp = cp * 0.4f;                     // AM/PM
        float dp = h / 110f;                      // date / weather text
        float dinoP = h / 150f;
        float iconP = h / 140f;

        float clockW = DotFont.cols(time) * cp + cp * 1.5f + DotFont.cols(ap) * sp;
        float wxW = wx == null ? 0 : Doodles.ICON_COLS * iconP + dp * 2 + DotFont.cols(wx) * dp;
        float blockW = Math.max(clockW, Math.max(DotFont.cols(date) * dp, wxW));
        float dinoH = Doodles.DINO_ROWS * dinoP;
        float blockH = dinoH + h * 0.02f + 7 * cp + h * 0.04f + 7 * dp
                + (wx == null ? 0 : h * 0.035f + Doodles.ICON_ROWS * iconP);

        // Slow drift: two different periods so the path never quite repeats.
        double tsec = now / 1000.0;
        float mx = Math.max(0, (w - blockW) / 2f - w * 0.05f);
        float my = Math.max(0, (h - blockH) / 2f - h * 0.06f);
        float x = (w - blockW) / 2f + mx * (float) Math.sin(tsec * 2 * Math.PI / 97.0);
        float y = (h - blockH) / 2f + my * (float) Math.sin(tsec * 2 * Math.PI / 61.0);

        Doodles.drawDino(c, dot, x, x + Math.max(clockW, Doodles.DINO_COLS * dinoP * 2.5f),
                y + dinoH, dinoP, now, alpha, theme.wear);
        y += dinoH + h * 0.02f;

        float cx = x;
        cx += DotFont.draw(c, time, cx, y, cp, cp * 0.38f, dot, CLOCK, alpha) + cp * 1.5f;
        DotFont.draw(c, ap, cx, y + 7 * cp - 7 * sp, sp, sp * 0.38f, dot, DIM, alpha);
        y += 7 * cp + h * 0.04f;

        DotFont.draw(c, date, x, y, dp, dp * 0.36f, dot, CLOCK, alpha * 0.9f);
        Season.drawIcon(c, dot, theme.icon, x + DotFont.cols(date) * dp + dp * 3, y + dp * 3.5f,
                dp * 11f, now, alpha);
        y += 7 * dp + h * 0.035f;

        if (wx != null) {
            Doodles.drawWeather(c, dot, x, y, iconP, wheel.weatherCode(), wheel.weatherDay(), now, alpha);
            float tx = x + Doodles.ICON_COLS * iconP + dp * 2;
            float ty = y + (Doodles.ICON_ROWS * iconP - 7 * dp) / 2f;
            DotFont.draw(c, wx, tx, ty, dp, dp * 0.36f, dot, DIM, alpha);
        }
    }
}
