package com.omar.wheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/**
 * A new message or email, like a phone's notification icon: a small dot envelope in the
 * top-right corner with a colored dot (teal = text, orange = mail). It slides in, stays
 * 10 seconds and goes away by itself; it stays on Wheel's card afterwards (MSG / MAIL).
 * On Wheel, ☰ opens it big in the middle (☰ again, Back or any button closes it).
 * Over other apps HomeWatcher catches ☰ while the icon shows and opens the whole message in
 * the corner (☰ or Back closes it). No preview beside the icon (Omar's call: no clutter). HomeWatcher shows this view over apps; Wheel draws the
 * same icon with drawIcon().
 */
final class MessageOverlay extends View {

    private static final String[] ENVELOPE = {
            "###########",
            "##.......##",
            "#.#.....#.#",
            "#..#...#..#",
            "#...#.#...#",
            "#....#....#",
            "#.........#",
            "###########",
    };
    private static final int CREAM = 0xFFEDE6DA, DIM = 0xFF6E6A62;
    private static final long IN_MS = 500;

    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean showing, mail;
    private long since;
    private String preview;

    MessageOverlay(Context c) {
        super(c);
        dot.setStyle(Paint.Style.FILL);
    }

    private String full;     // the whole message, for ☰
    private boolean open;    // ☰ opened it in the corner

    /** withPreview: a one-line preview beside the icon (over Disney+). */
    void show(String text, boolean isMail, boolean withPreview) {
        showing = true;
        mail = isMail;
        full = text;
        open = false;
        preview = withPreview ? text : null;
        since = SystemClock.uptimeMillis();
        invalidate();
    }

    void setOpen(boolean on) {
        open = on;
        invalidate();
    }

    boolean isOpen() {
        return open;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!showing) return;
        if (open && full != null) {
            drawOpen(c, getWidth(), getHeight());
            return;
        }
        boolean moving = drawIcon(c, getWidth(), getHeight(), mail, false, since, dot);
        if (preview != null) drawPreview(c, getWidth(), getHeight(), preview, since, dot);
        if (moving) postInvalidateOnAnimation();
    }

    private final android.graphics.RectF pill = new android.graphics.RectF();

    /** ☰ over an app: the whole message in a card in the corner (☰ or Back closes it). */
    private void drawOpen(Canvas c, float w, float h) {
        float p = h / 220f;
        float cardW = w * 0.38f, pad = p * 5;
        int maxCols = (int) ((cardW - 2 * pad) / p);
        java.util.List<String> lines = DotFont.wrap(full, maxCols, 8);
        float lineH = 10 * p, lp = p * 0.7f;
        float cardH = pad + 7 * lp + p * 5 + (lines.size() - 1) * lineH + 7 * p + pad;
        float right = w * 0.95f, left = right - cardW, top = h * 0.06f;
        dot.setColor(0xE8000000);
        pill.set(left, top, right, top + cardH);
        c.drawRoundRect(pill, p * 3, p * 3, dot);
        float y = top + pad;
        DotFont.draw(c, mail ? "MAIL" : "MESSAGE", left + pad, y, lp, lp * 0.36f, dot,
                new int[]{accent(mail)}, 1f);
        y += 7 * lp + p * 5;
        for (String line : lines) {
            DotFont.draw(c, line, left + pad, y, p, p * 0.36f, dot, new int[]{CREAM}, 1f);
            y += lineH;
        }
    }

    /** One line of the message in a dark pill, just left of the envelope. */
    private void drawPreview(Canvas c, float w, float h, String text, long since, Paint dot) {
        float k = Math.min(1f, (SystemClock.uptimeMillis() - since) / (float) IN_MS);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);
        float ip = h / 150f;                              // the envelope's pitch
        float envX = w * 0.95f - ENVELOPE[0].length() * ip;
        float envMid = h * 0.06f - (1f - e) * ip * 6 + ENVELOPE.length * ip / 2f;
        float p = h / 220f;
        String line = WheelView.fit(text, 46);
        float tw = DotFont.cols(line) * p, pad = p * 4;
        float right = envX - ip * 3, left = right - tw - pad * 2;
        float top = envMid - 3.5f * p - pad * 0.8f, bottom = envMid + 3.5f * p + pad * 0.8f;
        dot.setColor(Math.round(e * 0xD9) << 24);
        pill.set(left, top, right, bottom);
        c.drawRoundRect(pill, p * 3, p * 3, dot);
        DotFont.draw(c, line, left + pad, envMid - 3.5f * p, p, p * 0.36f, dot, new int[]{CREAM}, e);
    }

    static int accent(boolean mail) {
        return mail ? WheelView.TICK_MAIL : WheelView.TICK_MSG;
    }

    /** Draws the envelope; says whether it's still sliding in. withMenu = a small ☰ under it. */
    static boolean drawIcon(Canvas c, float w, float h, boolean mail, boolean withMenu, long since,
                            Paint dot) {
        float k = Math.min(1f, (SystemClock.uptimeMillis() - since) / (float) IN_MS);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);
        float p = h / 150f;
        float ew = ENVELOPE[0].length() * p;
        float right = w * 0.95f;
        float top = h * 0.06f - (1f - e) * p * 6;
        float x = right - ew;
        // A soft dark backing so it reads over a bright show.
        dot.setColor(Math.round(e * 0x99) << 24);
        c.drawCircle(x + ew / 2f, top + ENVELOPE.length * p / 2f, ew * 0.85f, dot);
        int a = Math.round(e * 255);
        dot.setColor((CREAM & 0x00FFFFFF) | (a << 24));
        for (int r = 0; r < ENVELOPE.length; r++) {
            for (int col = 0; col < ENVELOPE[r].length(); col++) {
                if (ENVELOPE[r].charAt(col) != '#') continue;
                c.drawCircle(x + col * p + p / 2f, top + r * p + p / 2f, p * 0.38f, dot);
            }
        }
        dot.setColor((accent(mail) & 0x00FFFFFF) | (a << 24));
        c.drawCircle(right + p * 0.5f, top - p * 0.5f, p * 1.2f, dot);
        if (withMenu) {
            float hp = h / 300f;
            String m = String.valueOf(DotFont.MENU);
            DotFont.draw(c, m, x + (ew - DotFont.cols(m) * hp) / 2f, top + ENVELOPE.length * p + p * 2,
                    hp, hp * 0.4f, dot, new int[]{DIM}, e);
        }
        return k < 1f;
    }
}
