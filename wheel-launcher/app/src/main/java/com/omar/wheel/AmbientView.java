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
    private static final long SLIDE_MS = 700;
    private int tickIdx, prevIdx;
    private long tickSince, slideStart;
    private int slideDir = 1;      // 1 = new message comes up from below, -1 = from above

    private static final int CARD = 0xFF3A3834;

    // A train pass started by hand (for trying it out), 0 if none.
    private long demoTrain;

    // NYT headlines for idle mode's ticker, refreshed every 15 minutes while Wheel is up.
    private static final long NEWS_EVERY_MS = 15 * 60 * 1000L;
    private static final float NEWS_DOTS_PER_SEC = 12f;
    private static final float NEWS_MAIN_BRIGHTNESS = 0.45f;
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
            // While the startup sequence plays: every other screen refresh (a steady 30 fps;
            // the stick can't quite hold 60, and uneven frames look choppier than fewer).
            if (Startup.showing()) postOnAnimation(this);   // every screen refresh (~60 fps)
            else postDelayed(this, FRAME_MS);
        }
    };

    private final Runnable everyOther = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            postOnAnimation(frame);   // skip this refresh, draw on the next
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
        removeCallbacks(everyOther);
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

    // A message or email on Wheel: the envelope icon in the corner (10 seconds, doesn't
    // take the remote except ☰, which opens it big in the middle). Opened, it stays until
    // you close it (☰, Back or any button). Play/Pause opens the newest one any time.
    private static final long ICON_FOR_MS = 10 * 1000L;
    private static final long MESSAGE_IN_MS = 400;
    private String iconText;
    private boolean iconMail;
    private long iconSince;        // uptime, like MessageOverlay
    private String message;        // opened big
    private boolean messageMail;
    private long messageSince;

    void showMessage(String text, boolean mail) {
        if (text == null || text.isEmpty()) return;
        iconText = text;
        iconMail = mail;
        iconSince = android.os.SystemClock.uptimeMillis();
        poke();          // wakes idle mode so you see it
        holdIdle = false;
        invalidate();
    }

    boolean iconShowing() {
        return iconText != null && message == null;
    }

    /** ☰ while the icon shows: open that message. */
    void openIcon() {
        if (iconText == null) return;
        openMessage(iconText, iconMail);
    }

    /** Opens a message big in the middle (it stays until closed). */
    void openMessage(String text, boolean mail) {
        if (text == null || text.isEmpty()) return;
        iconText = null;
        message = text;
        messageMail = mail;
        messageSince = System.currentTimeMillis();
        Messages.markRead(getContext());
        poke();
        holdIdle = false;
        invalidate();
    }

    private void drawIcon(Canvas c) {
        if (android.os.SystemClock.uptimeMillis() - iconSince > ICON_FOR_MS) {
            iconText = null;
            Messages.markRead(getContext());   // gone by itself; still on the card
            return;
        }
        MessageOverlay.drawIcon(c, getWidth(), getHeight(), iconMail, false, iconSince, dot);
    }

    boolean messageShowing() {
        return message != null;
    }

    void dismissMessage() {
        message = null;
        invalidate();
    }

    private void drawMessage(Canvas c, long now) {
        long t = now - messageSince;
        float w = getWidth(), h = getHeight();
        float k = Math.min(1f, t / (float) MESSAGE_IN_MS);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);
        // Dim everything behind it.
        c.drawColor(Doodles.withAlpha(0xD8000000, e));

        float p = h / 130f;
        float cardW = w * 0.56f;
        float pad = p * 5;
        int maxCols = (int) ((cardW - 2 * pad) / p);
        java.util.List<String> lines = DotFont.wrap(message, maxCols, 7);
        float lineH = 11 * p;
        float labelP = p * 0.6f;
        float cardH = pad + 7 * labelP + p * 5 + (lines.size() - 1) * lineH + 7 * p + pad;
        float x = (w - cardW) / 2f;
        // Slides up into place, like the card.
        float y = (h - cardH) / 2f + (1f - e) * p * 8;
        // Solid inside, so nothing behind shows through the words.
        dot.setColor(Doodles.withAlpha(0xFF000000, e));
        c.drawRoundRect(new android.graphics.RectF(x, y, x + cardW, y + cardH), p * 2, p * 2, dot);
        drawCard(c, x, y, cardW, cardH, p * 0.7f, e);
        float ty = y + pad;
        DotFont.draw(c, messageMail ? "MAIL" : "MESSAGE", x + pad, ty, labelP, labelP * 0.36f, dot,
                new int[]{messageMail ? WheelView.TICK_MAIL : WheelView.TICK_MSG}, e);
        ty += 7 * labelP + p * 5;
        for (String line : lines) {
            DotFont.draw(c, line, x + pad, ty, p, p * 0.36f, dot, CLOCK, e);
            ty += lineH;
        }
    }

    /** Go into idle mode right now (for trying it out); any remote press wakes it. */
    void demoIdle() {
        holdIdle = true;    // stays idle until a real remote press
    }

    private boolean holdIdle;

    /** Send one train across the screen right now, whatever the timetable says. */
    void demoTrain() {
        demoTrain = System.currentTimeMillis();
    }

    /** Moves the rotating line on once the current message has had its time (dwellMs). */
    private void advanceTicker(long now, int n, long dwellMs) {
        if (tickSince == 0) tickSince = now;
        if (n > 1 && now - tickSince >= dwellMs) {
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

    // Long messages (a fun fact, a long event name) scroll sideways inside the card.
    private static final float SCROLL_DOTS_PER_SEC = 12f;
    private static final long SCROLL_WAIT_MS = 1500;   // read the start first
    private static final long SCROLL_HOLD_MS = 2200;   // and the end

    /** How far (px) a message's text runs past the room it has. */
    private static float overflow(WheelView.Tick k, float p, float room) {
        float textRoom = room - DotFont.cols(k.label) * p - p * 5;
        return Math.max(0f, DotFont.cols(k.text) * p - textRoom);
    }

    /**
     * The rotating line in its card: a faint dotted rounded box, the message sliding
     * into place, and position dots stacked on the right.
     */
    private void drawTicker(Canvas c, java.util.List<WheelView.Tick> items, long now,
                            float x, float top, float p, float width, float alpha) {
        int n = items.size();
        if (n == 0 || alpha <= 0.01f) return;

        float pad = WheelView.TICK_PAD_ROWS * p;
        float cardTop = top - pad, cardH = 7 * p + 2 * pad;
        float textX = x + p * 3.5f;
        float dotsW = n > 1 ? p * 4.5f : 0;
        float room = width - p * 7f - dotsW;
        float pxPerMs = SCROLL_DOTS_PER_SEC * p / 1000f;

        WheelView.Tick curK = items.get(wrap(tickIdx, n));
        float over = overflow(curK, p, room);
        long dwell = over <= 0 ? TICK_MS
                : Math.max(TICK_MS, SLIDE_MS + SCROLL_WAIT_MS + (long) (over / pxPerMs) + SCROLL_HOLD_MS);
        advanceTicker(now, n, dwell);
        int cur = wrap(tickIdx, n);
        curK = items.get(cur);
        over = overflow(curK, p, room);
        long t = now - tickSince - SLIDE_MS - SCROLL_WAIT_MS;
        float scroll = n == 1 && over > 0
                // Only message: scroll, hold, jump back, repeat.
                ? Math.max(0f, Math.min(over, ((now - tickSince) % (long) (SCROLL_WAIT_MS
                        + over / pxPerMs + SCROLL_HOLD_MS) - SCROLL_WAIT_MS) * pxPerMs))
                : Math.max(0f, Math.min(over, t * pxPerMs));

        drawCard(c, x, cardTop, width, cardH, p, alpha);

        float k = slideStart == 0 ? 1f : Math.min(1f, (now - slideStart) / (float) SLIDE_MS);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);   // ease out
        if (k < 1f) {
            // Only the inside of the card shows the sliding text.
            c.save();
            c.clipRect(x + p, cardTop + p * 0.8f, x + width - p, cardTop + cardH - p * 0.8f);
            float shift = cardH * slideDir;
            WheelView.Tick prevK = items.get(wrap(prevIdx, n));
            // The old message leaves from where it finished (scrolled to its end).
            drawTick(c, prevK, textX, top - e * shift, p, room, overflow(prevK, p, room), alpha);
            drawTick(c, curK, textX, top + (1f - e) * shift, p, room, 0f, alpha);
            c.restore();
        } else {
            drawTick(c, curK, textX, top, p, room, scroll, alpha);
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

    /** Label in its color, then the message in `room` px, shifted left by `scroll` px. */
    private void drawTick(Canvas c, WheelView.Tick k, float x, float top, float p, float room,
                          float scroll, float alpha) {
        if (k.pulse) {
            // A slow breath (about 2.4 s), never fading out completely.
            double ph = (System.currentTimeMillis() % 2400L) / 2400.0 * Math.PI * 2;
            alpha *= 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.cos(ph));
        }
        int[] lc = {k.color};
        float lw = DotFont.draw(c, k.label, x, top, p, p * 0.36f, dot, lc, alpha);
        float tx = x + lw + p * 5, right = x + room;
        c.save();
        c.clipRect(tx - p * 0.5f, top - p, right, top + 8 * p);
        DotFont.draw(c, k.text, tx - scroll, top, p, p * 0.36f, dot, DIM, alpha, tx - p, right);
        c.restore();
    }

    boolean isIdle() {
        return idle > 0.02f && !waking;
    }

    /** Any remote press counts as activity and wakes idle mode. */
    void poke() {
        holdIdle = false;
        lastInput = SystemClock.uptimeMillis();
        if (idle > 0) waking = true;
    }

    @Override
    protected void onDraw(Canvas c) {
        long up = SystemClock.uptimeMillis();
        long now = System.currentTimeMillis();
        long dt = lastFrame == 0 ? 0 : Math.min(200, up - lastFrame);
        lastFrame = up;
        if (holdIdle) {
            lastInput = up - IDLE_AFTER_MS - 1;
            waking = false;
        }

        // Startup covering the whole screen: draw only that (keeps it smooth on the stick).
        if (Startup.coversAll()) {
            lastInput = up;
            wheel.setCovered(true);
            Startup.draw(c, dot, getWidth(), getHeight(), wheel);
            return;
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

        // The bottom of the screen is shared: the NYT ticker always runs there (dimmer on
        // the main screen so it doesn't pull your eye from the wheel). When a train is due it
        // runs right through the ticker line: the headlines fade out ahead of it and fade
        // back in behind it. Never at the top.
        float tp = h / 190f;                         // train dot pitch
        float t = trainProgress(now);
        float trackY = h * 0.985f;                   // no headlines yet: along the very bottom
        if (news != null && !news.isEmpty()) {
            float np = h / 170f;
            float newsTop = h * 0.955f - 7 * np;
            float gapL = 0, gapR = 0;
            if (t >= 0) {
                float[] sp = TrainDoodle.span(t, getWidth(), tp);
                gapL = sp[0] - np * 3;
                gapR = sp[1] + np * 3;
            }
            drawNews(c, up, newsTop, np, normal * NEWS_MAIN_BRIGHTNESS + idle * IDLE_BRIGHTNESS, gapL, gapR);
            // Train body centered on the headline text.
            trackY = newsTop + 3.5f * np - TrainDoodle.BODY_MID_ROWS * tp + TrainDoodle.ROWS * tp;
        }
        if (t >= 0) drawTrain(c, now, t, trackY, tp, normal + idle * IDLE_BRIGHTNESS);
        if (message != null) drawMessage(c, now);
        else if (iconText != null) drawIcon(c);
        // The startup sequence / morning briefing goes over everything.
        if (Startup.showing()) {
            lastInput = up;   // no idle mode while it plays
            Startup.draw(c, dot, getWidth(), h, wheel);
        }
    }

    /**
     * The NYT ticker. gapL..gapR (px) is where the train is: nothing drawn there, and the
     * headlines fade out over a short stretch on each side of it. gapR <= gapL: no train.
     */
    private void drawNews(Canvas c, long up, float top, float p, float alpha, float gapL, float gapR) {
        float w = getWidth();
        if (gapR <= gapL) {
            newsLayer(c, up, top, p, alpha, 0, w);
            return;
        }
        final float fade = p * 24f;
        final int steps = 8;
        float sw = fade / steps;
        newsLayer(c, up, top, p, alpha, 0, gapL - fade);
        for (int i = 0; i < steps; i++) {
            // Dimmer step by step toward the train, then brighter step by step behind it.
            newsLayer(c, up, top, p, alpha * (1f - (i + 1f) / (steps + 1)),
                    gapL - fade + i * sw, gapL - fade + (i + 1) * sw);
            newsLayer(c, up, top, p, alpha * ((i + 1f) / (steps + 1)),
                    gapR + i * sw, gapR + (i + 1) * sw);
        }
        newsLayer(c, up, top, p, alpha, gapR + fade, w);
    }

    /** One vertical slice (l..r px) of the ticker: "NYT", the little bar, scrolling headlines. */
    private void newsLayer(Canvas c, long up, float top, float p, float alpha, float l, float r) {
        if (r <= l || alpha <= 0.01f) return;
        java.util.List<String> items = news;
        float w = getWidth();
        float left = w * 0.04f, right = w * 0.96f;
        c.save();
        c.clipRect(l, top - p, r, top + 8 * p);
        float lw = DotFont.draw(c, "NYT", left, top, p, p * 0.36f, dot, NYT_LABEL, alpha, l - p, r);
        // A short faint bar between the label and the moving text.
        float bx = left + lw + p * 4f;
        if (bx >= l - p && bx <= r + p) {
            dot.setColor(Doodles.withAlpha(CARD, alpha));
            for (int i = 0; i < 7; i++) c.drawCircle(bx, top + i * p + p / 2f, p * 0.3f, dot);
        }
        float x0 = left + lw + p * 8f;
        float lo = Math.max(x0, l), hi = Math.min(right, r);
        if (hi > lo) {
            // One loop = every headline plus a separator after each.
            float sep = p * 8f;
            float total = 0;
            for (String s : items) total += DotFont.cols(s) * p + sep;
            if (total > 0) {
                float offset = (up * NEWS_DOTS_PER_SEC * p / 1000f) % total;
                c.clipRect(x0, top - p, right, top + 8 * p);
                float x = x0 - offset;
                // The loop twice back to back so it never runs out mid-screen.
                for (int pass = 0; pass < 2 && x < hi; pass++) {
                    for (String s : items) {
                        float sw = DotFont.cols(s) * p;
                        if (x + sw >= lo && x <= hi) {
                            DotFont.draw(c, s, x, top, p, p * 0.36f, dot, DIM, alpha, lo - p, hi);
                        }
                        x += sw;
                        float dx = x + sep / 2f;
                        if (dx >= lo && dx <= hi) {
                            dot.setColor(Doodles.withAlpha(0xFFC583BC, alpha));
                            c.drawCircle(dx, top + 3.5f * p, p * 0.55f, dot);
                        }
                        x += sep;
                        if (x > hi) break;
                    }
                }
            }
        }
        c.restore();
    }

    /** How far along a train pass is right now (0..1), or -1 if no train is on screen. */
    private float trainProgress(long now) {
        if (!Config.TRAIN) return -1;
        if (demoTrain > 0) {
            float t = (now - demoTrain) / (float) TrainDoodle.PASS_MS;
            if (t < 1f) return t;
            demoTrain = 0;
            return -1;
        }
        return TrainDoodle.progress(now, wheel.nextTrain(now));
    }

    /** The commuter rail train, with "NEXT TRAIN TO BOSTON 4:58 AM" above it. */
    private void drawTrain(Canvas c, long now, float t, float bottom, float pitch, float alpha) {
        long dep = wheel.nextTrain(now);
        String label = "NEXT TRAIN TO " + Config.TRAIN_TO;
        if (dep > 0) {
            TimeZone tz = TimeZone.getDefault();
            hm.setTimeZone(tz);
            ampm.setTimeZone(tz);
            Date d = new Date(dep);
            label += "  " + hm.format(d) + " " + ampm.format(d).toUpperCase(Locale.US);
        }
        TrainDoodle.draw(c, dot, getWidth(), bottom, pitch, t, label, alpha);
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
        java.util.List<WheelView.Tick> ticks = wheel.tickerItems(now);
        float calP = h / 170f;
        float calH = ticks.isEmpty() ? 0 : 7 * calP + 2 * WheelView.TICK_PAD_ROWS * calP + h * 0.04f;
        float dinoH = Doodles.DINO_ROWS * dinoP;
        float blockH = dinoH + h * 0.02f + 7 * cp + h * 0.04f + 7 * dp
                + (wx == null ? 0 : h * 0.035f + Doodles.ICON_ROWS * iconP) + calH;

        // Every line centered on the screen; the block nudged up when the news ticker
        // (and maybe a train) runs below.
        float y = (h - blockH) / 2f;
        if (news != null && !news.isEmpty()) y -= h * 0.055f;

        // The clock line (digits + AM/PM) centered; the dino walks above the digits only.
        float x = (w - clockW) / 2f;
        float digitsW = DotFont.cols(time) * cp;
        float walk = Math.max(digitsW, Doodles.DINO_COLS * dinoP * 1.5f);
        float walkX = x + (digitsW - walk) / 2f;
        Doodles.drawDino(c, dot, walkX, walkX + walk, y + dinoH, dinoP, now, alpha, theme.wear);
        y += dinoH + h * 0.02f;

        float cx = x;
        cx += DotFont.draw(c, time, cx, y, cp, cp * 0.38f, dot, CLOCK, alpha) + cp * 1.5f;
        DotFont.draw(c, ap, cx, y + 7 * cp - 7 * sp, sp, sp * 0.38f, dot, DIM, alpha);
        y += 7 * cp + h * 0.04f;

        if (!ticks.isEmpty()) {
            // The rotating line right under the time, in its card, as wide as the clock.
            float cardW = Math.max(clockW, w * 0.4f);
            float pad = WheelView.TICK_PAD_ROWS * calP;
            drawTicker(c, ticks, now, (w - cardW) / 2f, y + pad, calP, cardW, alpha);
            y += 7 * calP + 2 * pad + h * 0.04f;
        }

        // Date centered (the month's icon hangs off its right end, not counted).
        float dateW = DotFont.cols(date) * dp;
        float dx = (w - dateW) / 2f;
        DotFont.draw(c, date, dx, y, dp, dp * 0.36f, dot, CLOCK, alpha * 0.9f);
        Season.drawIcon(c, dot, theme.icon, dx + dateW + dp * 3, y + dp * 3.5f, dp * 8f, now, alpha);
        y += 7 * dp + h * 0.035f;

        if (wx != null) {
            // Weather text and its animated icon, centered together.
            float wxX = (w - wxW) / 2f;
            float ty = y + (Doodles.ICON_ROWS * iconP - 7 * dp) / 2f;
            DotFont.draw(c, wx, wxX, ty, dp, dp * 0.36f, dot, DIM, alpha);
            Doodles.drawWeather(c, dot, wxX + DotFont.cols(wx) * dp + dp * 3, y, iconP,
                    wheel.weatherCode(), wheel.weatherDay(), now, alpha);
            y += Doodles.ICON_ROWS * iconP;
        }
    }
}
