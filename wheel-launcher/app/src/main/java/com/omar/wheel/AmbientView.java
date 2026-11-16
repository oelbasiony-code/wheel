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
            if (Startup.showing() && Startup.animating()) postOnAnimation(this);   // ~60 fps
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
        Messages.markItemRead(getContext(), text);
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

    // ---------------------------------------------------------------- the inbox (hold ☰)

    private java.util.List<Messages.Item> inbox;
    private int inboxSel;
    private long inboxSince;

    void openInbox() {
        inbox = Messages.history(getContext());
        inboxSel = 0;
        inboxSince = System.currentTimeMillis();
        inboxSelAt = inboxSince;
        iconText = null;
        Messages.markRead(getContext());
        poke();
        holdIdle = false;
        invalidate();
    }

    boolean inboxShowing() {
        return inbox != null;
    }

    void closeInbox() {
        inbox = null;
        invalidate();
    }

    private long inboxSelAt;   // when the selection last moved (its text scrolls from then)

    void inboxMove(int dir) {
        if (inbox == null || inbox.isEmpty()) return;
        inboxSel = Math.max(0, Math.min(inbox.size() - 1, inboxSel + dir));
        inboxSelAt = System.currentTimeMillis();
        invalidate();
    }

    /** Hold OK in the inbox: delete the chosen one. */
    void inboxDeleteSelected() {
        if (inbox == null || inbox.isEmpty()) return;
        Messages.deleteFromHistory(getContext(), inbox.remove(inboxSel));
        inboxSel = Math.max(0, Math.min(inboxSel, inbox.size() - 1));
        invalidate();
    }

    /** OK in the inbox: open the chosen one big (Back from there closes it). */
    void inboxOpenSelected() {
        if (inbox == null || inbox.isEmpty()) return;
        Messages.Item it = inbox.get(inboxSel);
        inbox = null;
        openMessage(it.text, it.mail);
    }

    private static String ago(long t, long now) {
        long m = Math.max(0, (now - t) / 60000);
        if (m < 1) return "NOW";
        if (m < 60) return m + " MIN";
        if (m < 24 * 60) return (m / 60) + " H";
        return (m / (24 * 60)) + " D";
    }

    private void drawInbox(Canvas c, long now) {
        float w = getWidth(), h = getHeight();
        float k = Math.min(1f, (now - inboxSince) / 300f);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);
        c.drawColor(Doodles.withAlpha(0xF5000000, e));

        float p = h / 150f;                         // row text pitch
        float lp = p * 0.75f;                       // labels and times
        float rowH = 7 * p + p * 7;
        float panelW = w * 0.7f;
        float left = (w - panelW) / 2f;
        float top = h * 0.14f;
        DotFont.draw(c, "INBOX", left, top, lp, lp * 0.36f, dot, CLOCK, e);
        float y = top + 7 * lp + p * 8;

        if (inbox.isEmpty()) {
            DotFont.draw(c, "NOTHING YET", left, y, p, p * 0.36f, dot, DIM, e);
            return;
        }
        int visible = (int) ((h * 0.92f - y) / rowH);
        int first = Math.max(0, Math.min(inboxSel - visible / 2, inbox.size() - visible));
        float labelW = DotFont.cols("MAIL") * lp + p * 5;
        float timeW = DotFont.cols("59 MIN") * lp + p * 4;
        int maxCols = (int) ((panelW - labelW - timeW - p * 6) / p);
        for (int i = first; i < Math.min(inbox.size(), first + visible); i++) {
            Messages.Item it = inbox.get(i);
            boolean sel = i == inboxSel;
            float a = e * (sel ? 1f : 0.55f);
            if (sel) {
                // A soft bar behind the chosen row, and a marker on its left.
                dot.setColor(Doodles.withAlpha(0xFF15171A, e));
                c.drawRoundRect(new android.graphics.RectF(left - p * 4, y - p * 3, left + panelW + p * 2,
                        y + 7 * p + p * 3), p * 2, p * 2, dot);
                dot.setColor(Doodles.withAlpha(it.mail ? WheelView.TICK_MAIL : WheelView.TICK_MSG, e));
                c.drawCircle(left - p * 2, y + 3.5f * p, p * 0.6f, dot);
            }
            float ly = y + (7 * p - 7 * lp) / 2f;
            if (!it.read && !sel) {
                // Not seen yet: a small dot in its color.
                dot.setColor(Doodles.withAlpha(it.mail ? WheelView.TICK_MAIL : WheelView.TICK_MSG, e));
                c.drawCircle(left - p * 2, y + 3.5f * p, p * 0.6f, dot);
            }
            DotFont.draw(c, it.mail ? "MAIL" : "MSG", left, ly, lp, lp * 0.36f, dot,
                    new int[]{it.mail ? WheelView.TICK_MAIL : WheelView.TICK_MSG}, a);
            float tx = left + labelW, room = maxCols * p;
            float over = DotFont.cols(it.text) * p - room;
            if (sel && over > 0) {
                // The chosen one scrolls sideways so all of it can be read: wait, scroll, hold, repeat.
                float pxPerMs = SCROLL_DOTS_PER_SEC * p / 1000f;
                long cycle = SCROLL_WAIT_MS + (long) (over / pxPerMs) + SCROLL_HOLD_MS;
                long tt = (now - inboxSelAt) % cycle;
                float shift = Math.max(0f, Math.min(over, (tt - SCROLL_WAIT_MS) * pxPerMs));
                c.save();
                c.clipRect(tx - p * 0.5f, y - p, tx + room, y + 8 * p);
                DotFont.draw(c, it.text, tx - shift, y, p, p * 0.36f, dot, CLOCK, a, tx - p, tx + room);
                c.restore();
            } else {
                DotFont.draw(c, WheelView.fit(it.text, maxCols), tx, y, p, p * 0.36f, dot, CLOCK, a);
            }
            String t = ago(it.time, now);
            DotFont.draw(c, t, left + panelW - DotFont.cols(t) * lp, ly, lp, lp * 0.36f, dot, DIM, a);
            y += rowH;
        }
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

        // Big for short messages; smaller (and a wider card) for long ones, so all of it fits.
        float p = h / 130f;
        float cardW = w * 0.56f;
        int maxCols = (int) ((cardW - 10 * p) / p);
        java.util.List<String> lines = DotFont.wrap(message, maxCols, 100);
        if (lines.size() > 5) {
            p = h / 190f;
            cardW = w * 0.7f;
            maxCols = (int) ((cardW - 10 * p) / p);
            lines = DotFont.wrap(message, maxCols, 10);
        }
        float pad = p * 5;
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

    private static final int TICK_NYT = 0xFFA99BF2;   // lavender

    /**
     * The card's items plus one NYT headline, a different one each time around the card.
     * Not while an event countdown has the card to itself.
     */
    private java.util.List<WheelView.Tick> withHeadline(java.util.List<WheelView.Tick> items) {
        java.util.List<String> heads = news;
        if (heads == null || heads.isEmpty()) return items;
        if (items.size() == 1 && items.get(0).pulse) return items;
        int n = items.size() + 1;
        int round = Math.floorDiv(tickIdx, n);
        String h = heads.get(Math.floorMod(round, heads.size()));
        java.util.ArrayList<WheelView.Tick> out = new java.util.ArrayList<>(items);
        out.add(new WheelView.Tick("NYT", h, TICK_NYT));
        return out;
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
            drawTicker(c, withHeadline(wheel.tickerItems(now)), now, wheel.tickX, wheel.tickTop,
                    wheel.tickPitch, wheel.tickW, normal);
        }
        if (idle > 0.01f) {
            c.drawColor(Doodles.withAlpha(0xFF000000, idle));
            Season.drawEffect(c, dot, theme.fx, getWidth(), h, now, idle * IDLE_BRIGHTNESS);
            drawIdle(c, now, idle * IDLE_BRIGHTNESS, theme);
        }

        // The bottom of the screen belongs to the train (NYT headlines are on the card now;
        // Omar's call). Same height it always rode at. Never at the top.
        float tp = h / 190f;                         // train dot pitch
        float t = trainProgress(now);
        float np = h / 170f;
        float trackY = h * 0.955f - 3.5f * np - TrainDoodle.BODY_MID_ROWS * tp + TrainDoodle.ROWS * tp;
        if (t >= 0) drawTrain(c, now, t, trackY, tp, normal + idle * IDLE_BRIGHTNESS);
        if (inbox != null) drawInbox(c, now);
        else if (message != null) drawMessage(c, now);
        else if (iconText != null) drawIcon(c);
        // The startup sequence / morning briefing goes over everything.
        if (Startup.showing()) {
            lastInput = up;   // no idle mode while it plays
            Startup.draw(c, dot, getWidth(), h, wheel);
        }
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

    // The whistle: a soft two-blast "choo-choo" from the TV speakers, on the first pass of
    // the hour (heads-up) and the last one (about 5 minutes out), and on a test pass.
    private android.media.MediaPlayer whistle;
    private long whistledFor;   // the pass that already whistled (its start time)

    private long leftFor;       // the pass that already whistled leaving

    /** "Woo wooooo" as a whistling pass comes in, and again as it leaves. */
    private void maybeWhistle(long now, float t) {
        if (!Config.TRAIN_SOUND) return;
        long passStart = now - (long) (t * TrainDoodle.PASS_MS);
        boolean arriving = t < 0.1f, leaving = t > 0.5f && t < 0.6f;   // second one about 7.5 s in
        if (!arriving && !leaving) return;
        if (arriving && Math.abs(passStart - whistledFor) < 2000) return;
        if (leaving && Math.abs(passStart - leftFor) < 2000) return;
        boolean play;
        if (demoTrain > 0) {
            play = true;
        } else {
            long dep = wheel.nextTrain(now);
            long until = dep - passStart;
            play = until > TrainDoodle.WINDOW_MS - TrainDoodle.EVERY_MS            // first pass
                    || until < TrainDoodle.EVERY_MS + TrainDoodle.PASS_MS;         // last pass
        }
        if (arriving) whistledFor = passStart;
        else leftFor = passStart;
        if (!play) return;
        try {
            if (whistle == null) {
                whistle = android.media.MediaPlayer.create(getContext(), R.raw.choo);
                whistle.setVolume(Config.TRAIN_SOUND_VOLUME, Config.TRAIN_SOUND_VOLUME);
            }
            whistle.seekTo(0);
            whistle.start();
        } catch (Exception ignored) {
        }
    }

    /** The commuter rail train, with "NEXT TRAIN TO BOSTON 4:58 AM" above it. */
    private void drawTrain(Canvas c, long now, float t, float bottom, float pitch, float alpha) {
        maybeWhistle(now, t);
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
        float dinoH = Doodles.DINO_ROWS * dinoP;
        float blockH = dinoH + h * 0.02f + 7 * cp + h * 0.04f + 7 * dp
                + (wx == null ? 0 : h * 0.035f + Doodles.ICON_ROWS * iconP);

        // Calm: just the time, date and weather, every line centered on the screen
        // (no news ticker or card here; Omar's call).
        float y = (h - blockH) / 2f;

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
