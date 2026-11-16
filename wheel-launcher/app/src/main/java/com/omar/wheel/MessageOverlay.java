package com.omar.wheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.List;

/**
 * What a message looks like over another app (Netflix, a game...): a small dot envelope
 * in the top-right corner. Press the Menu button (☰) to open it in a small card there;
 * ☰ or Back closes it. Shown by HomeWatcher, which can draw over other apps.
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
    private static final int CREAM = 0xFFEDE6DA, DIM = 0xFFA9A398, FAINT = 0xFF3A3834,
            ACCENT = 0xFF9AD0C2;

    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text;
    private boolean open;

    MessageOverlay(Context c) {
        super(c);
        dot.setStyle(Paint.Style.FILL);
    }

    void show(String message) {
        text = message;
        open = false;
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
        if (text == null) return;
        float w = getWidth(), h = getHeight();
        float right = w * 0.95f, top = h * 0.06f;
        if (!open) {
            // The envelope with a little accent dot, and how to open it underneath.
            float p = h / 150f;
            float ew = ENVELOPE[0].length() * p;
            float x = right - ew;
            dot.setColor(CREAM);
            for (int r = 0; r < ENVELOPE.length; r++) {
                for (int col = 0; col < ENVELOPE[r].length(); col++) {
                    if (ENVELOPE[r].charAt(col) != '#') continue;
                    c.drawCircle(x + col * p + p / 2f, top + r * p + p / 2f, p * 0.38f, dot);
                }
            }
            dot.setColor(ACCENT);
            c.drawCircle(right + p * 0.5f, top - p * 0.5f, p * 1.1f, dot);
            float hp = h / 300f;
            String hint = DotFont.MENU + " READ";
            DotFont.draw(c, hint, right - DotFont.cols(hint) * hp, top + ENVELOPE.length * p + p * 2,
                    hp, hp * 0.4f, dot, new int[]{DIM}, 1f);
            return;
        }

        // Open: a card in the corner with the message, wrapped.
        float p = h / 220f;
        float cardW = w * 0.36f;
        float pad = p * 4;
        int maxCols = (int) ((cardW - 2 * pad) / p);
        List<String> lines = DotFont.wrap(text, maxCols, 6);
        float lineH = 10 * p;
        float cardH = pad * 2 + 7 * p + p * 5 + lines.size() * lineH - (lineH - 7 * p) + p * 4 + 7 * p * 0.6f;
        float x = right - cardW;
        // Dark backing so it reads over a bright show.
        dot.setColor(0xE6000000);
        c.drawRoundRect(new android.graphics.RectF(x, top, right, top + cardH), p * 3, p * 3, dot);
        dot.setColor(FAINT);
        for (float xx = x + p * 3; xx <= right - p * 3; xx += p * 1.6f) {
            c.drawCircle(xx, top, p * 0.3f, dot);
            c.drawCircle(xx, top + cardH, p * 0.3f, dot);
        }
        for (float yy = top + p * 3; yy <= top + cardH - p * 3; yy += p * 1.6f) {
            c.drawCircle(x, yy, p * 0.3f, dot);
            c.drawCircle(right, yy, p * 0.3f, dot);
        }
        float y = top + pad;
        DotFont.draw(c, "MESSAGE", x + pad, y, p, p * 0.36f, dot, new int[]{ACCENT}, 1f);
        y += 7 * p + p * 5;
        for (String line : lines) {
            DotFont.draw(c, line, x + pad, y, p, p * 0.36f, dot, new int[]{CREAM}, 1f);
            y += lineH;
        }
        y += p * 4 - (lineH - 7 * p);
        float hp = p * 0.6f;
        DotFont.draw(c, DotFont.MENU + " CLOSE", x + pad, y, hp, hp * 0.4f, dot, new int[]{DIM}, 1f);
    }
}
