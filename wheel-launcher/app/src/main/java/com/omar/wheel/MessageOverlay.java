package com.omar.wheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.List;

/**
 * What a message looks like over another app (Netflix, a game...): a small dot-matrix
 * card in the top-right corner, like a phone notification. It slides in, stays 5 seconds
 * (HomeWatcher removes it), and stays on Wheel's card afterwards.
 */
final class MessageOverlay extends View {

    private static final int CREAM = 0xFFEDE6DA, FAINT = 0xFF3A3834;
    private static final long IN_MS = 400;

    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private String text;
    private long since;

    MessageOverlay(Context c) {
        super(c);
        dot.setStyle(Paint.Style.FILL);
    }

    void show(String message) {
        text = message;
        since = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (text == null) return;
        if (!drawBanner(c, getWidth(), getHeight(), text, since, dot, box)) return;
        postInvalidateOnAnimation();
    }

    /**
     * Draws the banner (top-right card) and says whether it's still sliding in.
     * Wheel draws the same banner itself.
     */
    static boolean drawBanner(Canvas c, float w, float h, String text, long since,
                              Paint dot, RectF box) {
        float k = Math.min(1f, (SystemClock.uptimeMillis() - since) / (float) IN_MS);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);   // ease out

        float p = h / 220f;
        float cardW = w * 0.36f;
        float pad = p * 4;
        int maxCols = (int) ((cardW - 2 * pad) / p);
        List<String> lines = DotFont.wrap(text, maxCols, 4);
        float lineH = 10 * p;
        float labelP = p * 0.6f;
        float cardH = pad + 7 * labelP + p * 4 + (lines.size() - 1) * lineH + 7 * p + pad;
        float right = w * 0.95f;
        float x = right - cardW;
        // Slides down into place from just above.
        float top = h * 0.06f - (1f - e) * p * 8;
        int a = Math.round(e * 255);

        // Dark backing so it reads over a bright show.
        dot.setColor((Math.round(e * 0xE6) << 24));
        box.set(x, top, right, top + cardH);
        c.drawRoundRect(box, p * 3, p * 3, dot);
        dot.setColor((FAINT & 0x00FFFFFF) | (a << 24));
        for (float xx = x + p * 3; xx <= right - p * 3; xx += p * 1.6f) {
            c.drawCircle(xx, top, p * 0.3f, dot);
            c.drawCircle(xx, top + cardH, p * 0.3f, dot);
        }
        for (float yy = top + p * 3; yy <= top + cardH - p * 3; yy += p * 1.6f) {
            c.drawCircle(x, yy, p * 0.3f, dot);
            c.drawCircle(right, yy, p * 0.3f, dot);
        }
        float y = top + pad;
        DotFont.draw(c, "MESSAGE", x + pad, y, labelP, labelP * 0.36f, dot,
                new int[]{WheelView.TICK_MSG}, e);
        y += 7 * labelP + p * 4;
        for (String line : lines) {
            DotFont.draw(c, line, x + pad, y, p, p * 0.36f, dot, new int[]{CREAM}, e);
            y += lineH;
        }
        return k < 1f;
    }
}
