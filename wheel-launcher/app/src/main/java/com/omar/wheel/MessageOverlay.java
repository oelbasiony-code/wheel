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
 * Over other apps it's only the icon: Fire OS doesn't reliably hand ☰ to Wheel there,
 * so it waits for you on Wheel. HomeWatcher shows this view over apps; Wheel draws the
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

    MessageOverlay(Context c) {
        super(c);
        dot.setStyle(Paint.Style.FILL);
    }

    void show(boolean isMail) {
        showing = true;
        mail = isMail;
        since = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!showing) return;
        if (drawIcon(c, getWidth(), getHeight(), mail, false, since, dot)) postInvalidateOnAnimation();
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
