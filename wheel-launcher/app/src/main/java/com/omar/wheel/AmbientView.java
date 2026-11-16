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

    // The rotating line: which message, and since when. Messages slide up into place,
    // like a Smart Stack on an iPhone.
    private static final long TICK_MS = 7000;
    private static final long SLIDE_MS = 450;
    private int tickIdx, prevIdx;
    private long tickSince, slideStart;
    private int slideDir = 1;      // 1 = new message comes up from below, -1 = from above

    private static final int CARD = 0xFF3A3834;

    // A train pass started by hand (for trying it out), 0 if none.
    private long demoTrain;

    // NYT headlines for idle mode's ticker, refreshed every 15 minutes while Wheel is up.
    private static final long NEWS_EVERY_MS = 15 * 60 * 1000L;
    private static final float NEWS_DOTS_PER_SEC = 14f;
    private static final int[] NYT_LABEL = {0xFFEDE6DA};
    private java.util.List<String> news;
    private long lastNews;
    private boolean fetchingNews;
    private final android.os.Handler ui = new android.os.Handler();

    private final Runnable newsLoop = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            if (!fetchingNews && System.currentTimeMillis() - lastNews > NEWS_EVERY_MS) {
                fetchingNews = true;
                News.fetch(ui, new News.Callback() {
                    @Override
                    public void onNews(java.util.List<String> headlines) {
                        fetchingNews = false;
                        if (headlines != null) {
                            news = headlines;
                            lastNews = System.currentTimeMillis();
                        } else {
                            // Try again in a couple of minutes.
                            lastNews = System.currentTimeMillis() - NEWS_EVERY_MS + 2 * 60 * 1000L;
                        }
                    }
                });
            }
            postDelayed(this, 60 * 1000L);
        }
    };

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
        removeCallbacks(newsLoop);
        if (on) {
            lastInput = SystemClock.uptimeMillis();
            lastFrame = 0;
            post(frame);
            post(newsLoop);
        } else {
            idle = 0;
            wheel.setCovered(false);
        }
    }

    /** Left/right on the remote flips the rotating line. */
    void stepTicker(int dir) {
        long now = System.currentTimeMillis();
        prevIdx = tickIdx;
        tickIdx += dir;
        tickSince = now;
        slideStart = now;
        slideDir = dir;
        invalidate();
    }

    /** Go into idle mode right now (for trying it out); any remote press wakes it. */
    void demoIdle() {
        forceIdle = true;   // applied on the next frame (Wheel resumes after the intent)
    }

    private boolean forceIdle;

    /** Send one train across the screen right now, whatever the timetable says. */
    void demoTrain() {
        demoTrain = System.currentTimeMillis();
    }

    /** Moves the rotating line on every TICK_MS. */
    private void advanceTicker(long now, int n) {
        if (tickSince == 0) tickSince = now;
        if (n > 1 && now - tickSince >= TICK_MS) {
            prevIdx = tickIdx;
            tickIdx++;
            tickSince = now;
            slideStart = now;
            slideDir = 1;
        }
    }

    private static int wrap(int i, int n) {
        return ((i % n) + n) % n;
    }

    /**
     * The rotating line in its card: a faint dotted rounded box, the message sliding
     * into place, and position dots stacked on the right.
     */
    private void drawTicker(Canvas c, java.util.List<WheelView.Tick> items, long now,
                            float x, float top, float p, float width, float alpha) {
        int n = items.size();
        if (n == 0 || alpha <= 0.01f) return;
        advanceTicker(now, n);

        float pad = WheelView.TICK_PAD_ROWS * p;
        float cardTop = top - pad, cardH = 7 * p + 2 * pad;
        drawCard(c, x, cardTop, width, cardH, p, alpha);

        float textX = x + p * 3.5f;
        float dotsW = n > 1 ? p * 4.5f : 0;
        float room = width - p * 7f - dotsW;

        int cur = wrap(tickIdx, n);
        float k = slideStart == 0 ? 1f : Math.min(1f, (now - slideStart) / (float) SLIDE_MS);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);   // ease out
        if (k < 1f) {
            // Only the inside of the card shows the sliding text.
            c.save();
            c.clipRect(x + p, cardTop + p * 0.8f, x + width - p, cardTop + cardH - p * 0.8f);
            float shift = cardH * slideDir;
            drawTick(c, items.get(wrap(prevIdx, n)), textX, top - e * shift, p, room, alpha);
            drawTick(c, items.get(cur), textX, top + (1f - e) * shift, p, room, alpha);
            c.restore();
        } else {
            drawTick(c, items.get(cur), textX, top, p, room, alpha);
        }

        if (n > 1) {
            // Position dots, top to bottom, just inside the card's right edge.
            float gap = n > 4 ? p * 2f : p * 2.4f;
            float dx = x + width - p * 3f;
            float dy = cardTop + cardH / 2f - (n - 1) * gap / 2f;
            for (int i = 0; i < n; i++) {
                dot.setColor(Doodles.withAlpha(i == cur ? 0xFFA9A398 : CARD, alpha));
                c.drawCircle(dx, dy + i * gap, p * 0.5f, dot);
            }
        }
    }

    /** A rounded rectangle traced in faint dots. */
    private void drawCard(Canvas c, float x, float y, float w, float h, float p, float alpha) {
        dot.setColor(Doodles.withAlpha(CARD, alpha));
        float r = p * 0.32f;
        float step = p * 1.6f;
        float rr = p * 3f;                     // corner radius
        // Straight edges.
        int nx = Math.max(1, Math.round((w - 2 * rr) / step));
        for (int i = 0; i <= nx; i++) {
            float xx = x + rr + (w - 2 * rr) * i / nx;
            c.drawCircle(xx, y, r, dot);
            c.drawCircle(xx, y + h, r, dot);
        }
        int ny = Math.max(1, Math.round((h - 2 * rr) / step));
        for (int i = 0; i <= ny; i++) {
            float yy = y + rr + (h - 2 * rr) * i / ny;
            c.drawCircle(x, yy, r, dot);
            c.drawCircle(x + w, yy, r, dot);
        }
        // Corners: quarter circles, spaced about the same as the edges.
        int na = Math.max(2, Math.round((float) (Math.PI / 2 * rr / step)));
        for (int i = 1; i < na; i++) {
            double a = Math.PI / 2 * i / na;
            float ox = rr - (float) Math.cos(a) * rr, oy = rr - (float) Math.sin(a) * rr;
            c.drawCircle(x + ox, y + oy, r, dot);
            c.drawCircle(x + w - ox, y + oy, r, dot);
            c.drawCircle(x + ox, y + h - oy, r, dot);
            c.drawCircle(x + w - ox, y + h - oy, r, dot);
        }
    }

    /** Label in its color, then the message, trimmed to fit `room` px. */
    private void drawTick(Canvas c, WheelView.Tick k, float x, float top, float p, float room,
                          float alpha) {
        int[] lc = {k.color};
        float lw = DotFont.draw(c, k.label, x, top, p, p * 0.36f, dot, lc, alpha);
        int cols = (int) ((room - lw) / p) - 5;
        String text = WheelView.fit(k.text, Math.max(4, cols));
        DotFont.draw(c, text, x + lw + p * 5, top, p, p * 0.36f, dot, DIM, alpha);
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
        if (forceIdle) {
            forceIdle = false;
            lastInput = up - IDLE_AFTER_MS - 1;
            waking = false;
        }

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
                    wheel.dateTop + wheel.datePitch * 3.5f, wheel.datePitch * 8f, now, normal);
            Doodles.drawWeather(c, dot, wheel.iconX, wheel.iconTop, wheel.iconPitch,
                    wheel.weatherCode(), wheel.weatherDay(), now, normal);
            drawTicker(c, wheel.tickerItems(now), now, wheel.tickX, wheel.tickTop,
                    wheel.tickPitch, wheel.tickW, normal);
        }
        if (idle > 0.01f) {
            c.drawColor(Doodles.withAlpha(0xFF000000, idle));
            Season.drawEffect(c, dot, theme.fx, getWidth(), h, now, idle * IDLE_BRIGHTNESS);
            drawIdle(c, now, idle * IDLE_BRIGHTNESS, theme);
        }

        // The bottom of the screen is shared. Normal mode: it's the train's track.
        // Idle mode: the news ticker runs there, and a train rides on top of it like rails.
        float trackY = h * 0.985f;    // low, so the time above it clears the wheel
        boolean ticker = idle > 0.01f && news != null && !news.isEmpty();
        if (ticker) {
            float np = h / 170f;
            float newsTop = h * 0.955f - 7 * np;
            drawNews(c, up, newsTop, np, idle * IDLE_BRIGHTNESS);
            float rails = newsTop - np * 1.6f;
            trackY = trackY + (rails - trackY) * idle;
        }
        drawTrain(c, now, trackY, normal + idle * IDLE_BRIGHTNESS);
    }

    /** NYT headlines scrolling right to left, "NYT" fixed on the left, a dot between headlines. */
    private void drawNews(Canvas c, long up, float top, float p, float alpha) {
        java.util.List<String> items = news;
        float w = getWidth();
        float left = w * 0.04f, right = w * 0.96f;
        float lw = DotFont.draw(c, "NYT", left, top, p, p * 0.36f, dot, NYT_LABEL, alpha);
        // A short faint bar between the label and the moving text.
        dot.setColor(Doodles.withAlpha(CARD, alpha));
        for (int i = 0; i < 7; i++) c.drawCircle(left + lw + p * 4f, top + i * p + p / 2f, p * 0.3f, dot);
        float x0 = left + lw + p * 8f;

        // One loop = every headline plus a separator after each.
        float sep = p * 8f;
        float total = 0;
        for (String s : items) total += DotFont.cols(s) * p + sep;
        if (total <= 0) return;
        float offset = (up * NEWS_DOTS_PER_SEC * p / 1000f) % total;

        c.save();
        c.clipRect(x0, top - p, right, top + 8 * p);
        float x = x0 - offset;
        // Draw the loop twice back to back so it never runs out mid-screen.
        for (int pass = 0; pass < 2 && x < right; pass++) {
            for (String s : items) {
                float sw = DotFont.cols(s) * p;
                if (x + sw >= x0 && x <= right) {
                    DotFont.draw(c, s, x, top, p, p * 0.36f, dot, DIM, alpha, x0, right);
                }
                x += sw;
                float dx = x + sep / 2f;
                if (dx >= x0 && dx <= right) {
                    dot.setColor(Doodles.withAlpha(0xFFC583BC, alpha));
                    c.drawCircle(dx, top + 3.5f * p, p * 0.55f, dot);
                }
                x += sep;
                if (x > right) break;
            }
        }
        c.restore();
    }

    /** The commuter rail train along the bottom, in the half hour before it leaves. */
    private void drawTrain(Canvas c, long now, float bottom, float alpha) {
        if (!Config.TRAIN) return;
        long dep = wheel.nextTrain(now);
        float t;
        if (demoTrain > 0) {
            t = (now - demoTrain) / (float) TrainDoodle.PASS_MS;
            if (t >= 1f) {
                demoTrain = 0;
                return;
            }
        } else {
            t = TrainDoodle.progress(now, dep);
        }
        if (t < 0) return;
        String label = "TO " + Config.TRAIN_TO;
        if (dep > 0) {
            TimeZone tz = TimeZone.getDefault();
            hm.setTimeZone(tz);
            ampm.setTimeZone(tz);
            Date d = new Date(dep);
            label = hm.format(d) + " " + ampm.format(d).toUpperCase(Locale.US) + " " + label;
        }
        float h = getHeight();
        TrainDoodle.draw(c, dot, getWidth(), bottom, h / 190f, t, label, alpha);
    }

    /** A big clock centered on the screen, with the dino walking on top. */
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
        float wxW = wx == null ? 0 : DotFont.cols(wx) * dp + dp * 3 + Doodles.ICON_COLS * iconP;
        float blockW = Math.max(clockW, Math.max(DotFont.cols(date) * dp, wxW));
        java.util.List<WheelView.Tick> ticks = wheel.tickerItems(now);
        float calP = h / 170f;
        float calH = ticks.isEmpty() ? 0 : h * 0.045f + 7 * calP;
        float dinoH = Doodles.DINO_ROWS * dinoP;
        float blockH = dinoH + h * 0.02f + 7 * cp + h * 0.04f + 7 * dp
                + (wx == null ? 0 : h * 0.035f + Doodles.ICON_ROWS * iconP) + calH;

        // Centered and still; nudged up when the news ticker (and maybe a train) runs below.
        float x = (w - blockW) / 2f;
        float y = (h - blockH) / 2f;
        if (news != null && !news.isEmpty()) y -= h * 0.055f;

        // Walk only above the clock digits (not past them over the AM/PM).
        float digitsW = DotFont.cols(time) * cp;
        Doodles.drawDino(c, dot, x, x + Math.max(digitsW, Doodles.DINO_COLS * dinoP * 1.5f),
                y + dinoH, dinoP, now, alpha, theme.wear);
        y += dinoH + h * 0.02f;

        float cx = x;
        cx += DotFont.draw(c, time, cx, y, cp, cp * 0.38f, dot, CLOCK, alpha) + cp * 1.5f;
        DotFont.draw(c, ap, cx, y + 7 * cp - 7 * sp, sp, sp * 0.38f, dot, DIM, alpha);
        y += 7 * cp + h * 0.04f;

        DotFont.draw(c, date, x, y, dp, dp * 0.36f, dot, CLOCK, alpha * 0.9f);
        Season.drawIcon(c, dot, theme.icon, x + DotFont.cols(date) * dp + dp * 3, y + dp * 3.5f,
                dp * 8f, now, alpha);
        y += 7 * dp + h * 0.035f;

        if (wx != null) {
            // Weather text first, the animated icon to its right (same as the main screen).
            float ty = y + (Doodles.ICON_ROWS * iconP - 7 * dp) / 2f;
            DotFont.draw(c, wx, x, ty, dp, dp * 0.36f, dot, DIM, alpha);
            Doodles.drawWeather(c, dot, x + DotFont.cols(wx) * dp + dp * 3, y, iconP,
                    wheel.weatherCode(), wheel.weatherDay(), now, alpha);
            y += Doodles.ICON_ROWS * iconP;
        }
        if (!ticks.isEmpty()) {
            // The rotating line under the weather, in its card.
            drawTicker(c, ticks, now, x, y + h * 0.045f, calP, Math.max(blockW, w * 0.4f), alpha);
        }
    }
}
