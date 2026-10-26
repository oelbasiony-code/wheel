package com.omar.wheel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** The whole home screen, drawn on one canvas. */
final class WheelView extends View {

    interface Host {
        void launch(AppEntry e);

        void toast(String msg);
    }

    private static final int NORMAL = 0, MENU = 1, MOVE = 2;
    private static final int ICON = AppRepo.ICON_GRID;

    private static final int BG = 0xFF07080A;
    private static final int[] PANEL = {0xFFEDE6DA};
    private static final int[] DIM = {0xFFA9A398};
    private static final int[] FAINT = {0xFF6B675F};
    private static final int[] HINT = {0xFF55524C};

    private final Host host;
    private final AppRepo repo;
    private List<AppEntry> items = Collections.emptyList();
    private int target;        // unbounded when the wheel wraps
    private float pos;         // animated toward target
    private int state = NORMAL;
    private int menuChoice;
    private boolean showAll;
    private String weatherNow, weatherRange;

    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Paint vignette = new Paint();
    private final SimpleDateFormat hm = new SimpleDateFormat("h:mm", Locale.US);
    private final SimpleDateFormat ampm = new SimpleDateFormat("a", Locale.US);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEE MMM d", Locale.US);
    private final int[] ordIdx = new int[16];
    private final float[] ordD = new float[16];

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            long now = System.currentTimeMillis();
            postDelayed(this, 1000 - now % 1000 + 5);
        }
    };

    WheelView(Context c, Host host, AppRepo repo) {
        super(c);
        this.host = host;
        this.repo = repo;
        setFocusable(true);
        setFocusableInTouchMode(true);
        dot.setStyle(Paint.Style.FILL);
    }

    // ---------------------------------------------------------------- data

    void refresh() {
        String keep = items.isEmpty() ? null : items.get(sel()).pkg;
        state = NORMAL;
        items = repo.visible(showAll);
        int idx = 0;
        if (keep != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).pkg.equals(keep)) {
                    idx = i;
                    break;
                }
            }
        }
        target = idx;
        pos = idx;
        invalidate();
    }

    void setWeather(String now, String range) {
        if (now == null) return;
        weatherNow = now;
        weatherRange = range;
        invalidate();
    }

    private int sel() {
        int n = items.size();
        return n == 0 ? 0 : mod(target, n);
    }

    private boolean wraps() {
        return items.size() >= 5;
    }

    private static int mod(int a, int n) {
        int m = a % n;
        return m < 0 ? m + n : m;
    }

    // ---------------------------------------------------------------- input

    void onUpDown(int dir) {
        int n = items.size();
        if (n == 0) return;
        if (state == NORMAL) {
            if (wraps()) target += dir;
            else target = Math.max(0, Math.min(n - 1, target + dir));
        } else if (state == MOVE) {
            int i = sel(), j = i + dir;
            if (j < 0 || j >= n) return;
            AppEntry a = items.get(i), b = items.get(j);
            if (a.missing || b.missing || a.action != null || b.action != null) return;
            repo.swap(a.pkg, b.pkg);
            Collections.swap(items, i, j);
            target += dir;
        }
        invalidate();
    }

    void onLeftRight(int dir) {
        if (state != MENU) return;
        menuChoice = Math.max(0, Math.min(2, menuChoice + dir));
        invalidate();
    }

    void onOk() {
        if (items.isEmpty()) return;
        AppEntry e = items.get(sel());
        if (state == NORMAL) {
            host.launch(e);
        } else if (state == MOVE) {
            state = NORMAL;
            repo.save();
        } else if (state == MENU) {
            if (menuChoice == 0) {
                state = MOVE;
            } else if (menuChoice == 1) {
                repo.setHidden(e.pkg, !e.hidden);
                int keepIdx = sel();
                state = NORMAL;
                if (showAll) {
                    refresh();
                } else {
                    items = repo.visible(false);
                    int n = items.size();
                    target = n == 0 ? 0 : Math.min(keepIdx, n - 1);
                    pos = target;
                }
            } else {
                state = NORMAL;
            }
        }
        invalidate();
    }

    void onOkLong() {
        if (state != NORMAL || items.isEmpty()) return;
        AppEntry e = items.get(sel());
        if (e.missing) {
            host.toast("Couldn't find " + e.label + " on this Fire Stick");
            return;
        }
        if (e.action != null) return;
        state = MENU;
        menuChoice = 0;
        invalidate();
    }

    /** Returns true if Back did something. */
    boolean onBack() {
        if (state == MOVE) {
            repo.save();
            state = NORMAL;
        } else if (state == MENU) {
            state = NORMAL;
        } else if (showAll) {
            showAll = false;
            refresh();
        } else {
            return false;
        }
        invalidate();
        return true;
    }

    void onMenu() {
        if (state != NORMAL) return;
        showAll = !showAll;
        refresh();
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        // Faint unlit LED grid behind everything, as a tiny repeating tile.
        int s = Math.max(6, Math.round(h / 90f));
        Bitmap tile = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas tc = new Canvas(tile);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0x16FFFFFF);
        tc.drawCircle(s / 2f, s / 2f, Math.max(1f, s * 0.13f), tp);
        grid.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        vignette.setShader(new RadialGradient(w * 0.4f, h * 0.5f, Math.max(w, h) * 0.75f,
                new int[]{0x00000000, 0x00000000, 0xC0000000}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        long now = System.currentTimeMillis();

        float diff = target - pos;
        boolean animating = Math.abs(diff) > 0.001f;
        if (animating) {
            pos += diff * 0.22f;
            if (Math.abs(target - pos) < 0.002f) pos = target;
        }

        c.drawColor(BG);
        c.drawRect(0, 0, w, h, grid);
        drawWheel(c, now);
        drawPanel(c, now);
        drawHints(c);
        c.drawRect(0, 0, w, h, vignette);

        if (animating) postInvalidateOnAnimation();
        else if (state == MOVE) postInvalidateDelayed(200);
    }

    private void drawWheel(Canvas c, long now) {
        float w = getWidth(), h = getHeight();
        float base = h / 78f;
        float cx0 = w * 0.065f, cy = h * 0.5f, R = h * 0.56f, step = 0.25f;
        int n = items.size();

        String tag = showAll ? "ALL APPS" : "MY APPS";
        float tp = h / 230f;
        DotFont.draw(c, tag, cx0, h * 0.045f, tp, tp * 0.38f, dot, FAINT, 1f);

        if (n == 0) {
            String msg = repo.isLoaded() ? "NO APPS" : "LOADING";
            DotFont.draw(c, msg, cx0, cy - 3.5f * base, base, base * 0.36f, dot, DIM, 1f);
            return;
        }

        int count = 0;
        if (wraps()) {
            int b = Math.round(pos);
            int K = Math.min(3, (n - 1) / 2);
            for (int k = -K - 1; k <= K + 1; k++) {
                int i = b + k;
                float d = i - pos;
                if (Math.abs(d) > K + 0.6f) continue;
                ordIdx[count] = mod(i, n);
                ordD[count] = d;
                count++;
            }
        } else {
            for (int i = 0; i < n; i++) {
                ordIdx[count] = i;
                ordD[count] = i - pos;
                count++;
            }
        }
        // Far items first so the focused one is drawn on top.
        for (int i = 1; i < count; i++) {
            int ki = ordIdx[i];
            float kd = ordD[i];
            int j = i - 1;
            while (j >= 0 && Math.abs(ordD[j]) < Math.abs(kd)) {
                ordIdx[j + 1] = ordIdx[j];
                ordD[j + 1] = ordD[j];
                j--;
            }
            ordIdx[j + 1] = ki;
            ordD[j + 1] = kd;
        }

        int selIdx = sel();
        for (int q = 0; q < count; q++) {
            AppEntry e = items.get(ordIdx[q]);
            float d = ordD[q];
            float a = d * step;
            if (Math.abs(a) > 1.5f) continue;
            float cos = (float) Math.cos(a);
            float y = cy + R * (float) Math.sin(a);
            float near = (float) Math.exp(-2.5 * d * d);
            float scale = (0.5f + 0.5f * near) * (0.55f + 0.45f * cos);
            float alpha = (0.22f + 0.78f * (float) Math.exp(-3 * d * d))
                    * Math.max(0f, Math.min(1f, (3.4f - Math.abs(d)) / 0.9f));
            boolean isSel = ordIdx[q] == selIdx && Math.abs(d) < 0.5f;
            if (state != NORMAL && !isSel) alpha *= 0.35f;
            if (e.missing) alpha *= 0.4f;
            else if (e.hidden) alpha *= 0.5f;
            float x = cx0 + R * (1 - cos) * 0.55f;
            drawItem(c, e, x, y, base * scale, alpha, isSel && near > 0.6f, now);
        }

        if (state == MENU) drawMenu(c, items.get(selIdx), base, cx0, cy);
    }

    private void drawItem(Canvas c, AppEntry e, float x, float y, float pitch, float alpha,
                          boolean focus, long now) {
        float ip = pitch * 0.8f;
        float iconH = ICON * ip;
        float iconTop = y - iconH / 2f;
        int ia = Math.max(0, Math.min(255, Math.round(alpha * 255f)));

        if (e.icon != null) {
            float r = ip * 0.38f;
            for (int row = 0; row < ICON; row++) {
                for (int col = 0; col < ICON; col++) {
                    int px = e.icon[row * ICON + col];
                    if ((px >>> 24) < 110) continue;
                    dot.setColor((px & 0x00FFFFFF) | (ia << 24));
                    c.drawCircle(x + col * ip + ip / 2f, iconTop + row * ip + ip / 2f, r, dot);
                }
            }
        } else {
            // No icon (or not installed): a hollow square in the brand color.
            dot.setColor((e.colors[0] & 0x00FFFFFF) | (ia << 24));
            float r = ip * 0.3f;
            for (int k = 0; k < ICON; k++) {
                float o = k * ip + ip / 2f;
                c.drawCircle(x + o, iconTop + ip / 2f, r, dot);
                c.drawCircle(x + o, iconTop + iconH - ip / 2f, r, dot);
                c.drawCircle(x + ip / 2f, iconTop + o, r, dot);
                c.drawCircle(x + iconH - ip / 2f, iconTop + o, r, dot);
            }
        }

        float tx = x + iconH + pitch * 2.2f;
        // Long names shrink so they never cross the divider.
        float fit = (getWidth() * 0.575f - tx) / Math.max(1, DotFont.cols(e.display));
        float tp = Math.max(pitch * 0.5f, Math.min(pitch, fit));
        float top = y - 3.5f * tp;
        if (focus) DotFont.draw(c, e.display, tx, top, tp, tp * 0.62f, dot, e.colors, alpha * 0.13f);
        DotFont.draw(c, e.display, tx, top, tp, tp * 0.36f, dot, e.colors, alpha);

        if (!focus) return;
        String tag = e.missing ? "NOT INSTALLED" : e.hidden ? "HIDDEN" : null;
        if (tag != null) {
            float sp = pitch * 0.4f;
            DotFont.draw(c, tag, tx, y + 4.6f * pitch, sp, sp * 0.38f, dot, FAINT, 1f);
        }
        if (state == MOVE && (now / 400) % 2 == 0) {
            float ap = pitch * 0.6f;
            float ax = x + iconH / 2f - 2.5f * ap;
            DotFont.draw(c, String.valueOf(DotFont.UP), ax, iconTop - 8.5f * ap, ap, ap * 0.38f, dot, e.colors, 1f);
            DotFont.draw(c, String.valueOf(DotFont.DOWN), ax, iconTop + iconH + 1.5f * ap, ap, ap * 0.38f, dot, e.colors, 1f);
        }
    }

    private void drawMenu(Canvas c, AppEntry e, float base, float cx0, float cy) {
        String[] opts = {"MOVE", e.hidden ? "UNHIDE" : "HIDE", "CANCEL"};
        float mp = base * 0.5f;
        float ox = cx0 + ICON * base * 0.8f + base * 2.2f;
        float top = cy + 6.2f * base;
        for (int i = 0; i < opts.length; i++) {
            boolean on = i == menuChoice;
            float wd = DotFont.draw(c, opts[i], ox, top, mp, mp * 0.38f, dot, on ? e.colors : DIM, on ? 1f : 0.7f);
            if (on) {
                dot.setColor(e.colors[0]);
                int cols = DotFont.cols(opts[i]);
                for (int k = 0; k < cols; k++) {
                    c.drawCircle(ox + k * mp + mp / 2f, top + 8.8f * mp, mp * 0.3f, dot);
                }
            }
            ox += wd + 4 * mp;
        }
    }

    private void drawPanel(Canvas c, long nowMs) {
        float w = getWidth(), h = getHeight();
        float px = w * 0.635f, pw = w * 0.32f;

        // Dotted divider between wheel and panel.
        dot.setColor(0x26FFFFFF);
        float dr = Math.max(1f, h / 600f);
        for (float yy = h * 0.22f; yy < h * 0.78f; yy += h / 90f) c.drawCircle(w * 0.6f, yy, dr, dot);

        Date now = new Date(nowMs);
        // Follow the Fire Stick's time zone even if it changes while Wheel is running.
        TimeZone tz = TimeZone.getDefault();
        hm.setTimeZone(tz);
        ampm.setTimeZone(tz);
        dateFmt.setTimeZone(tz);
        Calendar cal = Calendar.getInstance(tz);
        cal.setTime(now);
        int hr = cal.get(Calendar.HOUR_OF_DAY);
        String part = hr < 5 ? "GOOD NIGHT" : hr < 12 ? "GOOD MORNING" : hr < 17 ? "GOOD AFTERNOON"
                : hr < 22 ? "GOOD EVENING" : "GOOD NIGHT";
        String greet = part + ", " + Config.NAME;

        float y = h * 0.27f;
        float gp = Math.min(h / 150f, pw / Math.max(1, DotFont.cols(greet)));
        DotFont.draw(c, greet, px, y, gp, gp * 0.36f, dot, DIM, 1f);
        y += 7 * gp + h * 0.04f;

        String hhmm = hm.format(now);
        String ap = ampm.format(now).toUpperCase(Locale.US);
        float cp = Math.min(h / 46f, (pw * 0.8f) / Math.max(1, DotFont.cols(hhmm)));
        int ci = hhmm.indexOf(':');
        float cx = px;
        cx += DotFont.draw(c, hhmm.substring(0, ci), cx, y, cp, cp * 0.38f, dot, PANEL, 1f) + cp;
        boolean colonOn = (nowMs / 1000) % 2 == 0;
        int[] accent = items.isEmpty() ? PANEL : items.get(sel()).colors;
        cx += DotFont.draw(c, ":", cx, y, cp, cp * 0.38f, dot, accent, colonOn ? 1f : 0.15f) + cp;
        cx += DotFont.draw(c, hhmm.substring(ci + 1), cx, y, cp, cp * 0.38f, dot, PANEL, 1f) + cp * 1.5f;
        float sp = cp * 0.4f;
        DotFont.draw(c, ap, cx, y + 7 * cp - 7 * sp, sp, sp * 0.38f, dot, DIM, 1f);
        y += 7 * cp + h * 0.05f;

        String date = dateFmt.format(now).toUpperCase(Locale.US);
        float dp = Math.min(h / 120f, pw / Math.max(1, DotFont.cols(date)));
        DotFont.draw(c, date, px, y, dp, dp * 0.36f, dot, PANEL, 0.9f);
        y += 7 * dp + h * 0.055f;

        String wn = weatherNow != null ? weatherNow : "--" + DotFont.DEG;
        float wp = Math.min(h / 120f, pw / Math.max(1, DotFont.cols(wn)));
        DotFont.draw(c, wn, px, y, wp, wp * 0.36f, dot, DIM, 1f);
        y += 7 * wp + h * 0.022f;
        if (weatherRange != null) {
            float rp = h / 200f;
            DotFont.draw(c, weatherRange, px, y, rp, rp * 0.38f, dot, FAINT, 1f);
        }
    }

    private void drawHints(Canvas c) {
        float w = getWidth(), h = getHeight();
        String t;
        if (state == MENU) t = "LEFT/RIGHT CHOOSE   OK SELECT   BACK CANCEL";
        else if (state == MOVE) t = "UP/DOWN MOVE   OK DONE";
        else t = "UP/DOWN SPIN   OK OPEN   HOLD OK EDIT   MENU " + (showAll ? "MY APPS" : "ALL APPS");
        float hp = h / 300f;
        DotFont.draw(c, t, w * 0.065f, h * 0.94f, hp, hp * 0.4f, dot, HINT, 1f);
    }
}
