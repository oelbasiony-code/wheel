package com.omar.wheel;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * A dot-matrix commuter rail train (MBTA purple) that rolls along the bottom of the
 * screen with its departure time riding above it. It only runs in the half hour
 * before your train leaves, once every few minutes, so it stays special.
 */
final class TrainDoodle {

    static final long PASS_MS = 15000;              // one trip across the screen
    static final long EVERY_MS = 5 * 60 * 1000L;    // a pass every 5 minutes...
    static final long WINDOW_MS = 30 * 60 * 1000L;  // ...in the 30 minutes before departure

    private static final int PURPLE = 0xFF9C3F8C;   // body (brighter than the real paint, for a black screen)
    private static final int STRIPE = 0xFFC583BC;   // same purple as TRAIN on the rotating line
    private static final int WINDOW = 0xFFEDE6DA;
    private static final int STEEL = 0xFF6E6A63;
    private static final int WHEEL_LIT = 0xFFA9A398;
    private static final int SMOKE = 0xFFA9A398;

    // P body, S stripe, W window, G undercarriage, O wheel. Locomotive faces right.
    private static final String[] LOCO = {
            "...GG.....................",
            "PPPPPPPPPPPPPPPPPPPPPP....",
            "PPPPPPPPPPPPPPPPPPPPPPP...",
            "PPPPPPPPPPPPPPPPPPPWWWPP..",
            "PPPPPPPPPPPPPPPPPPPWWWPPP.",
            "PPPPPPPPPPPPPPPPPPPPPPPPPP",
            "SSSSSSSSSSSSSSSSSSSSSSSSSS",
            "PPPPPPPPPPPPPPPPPPPPPPPPPP",
            "GGGGGGGGGGGGGGGGGGGGGGGGGG",
            ".OO.OO..............OO.OO.",
    };
    // A bi-level coach: two rows of windows.
    private static final String[] COACH = {
            "..............................",
            ".PPPPPPPPPPPPPPPPPPPPPPPPPPPP.",
            "PPPPPPPPPPPPPPPPPPPPPPPPPPPPPP",
            "PPWWPWWPWWPWWPWWPWWPWWPWWPWWPP",
            "PPPPPPPPPPPPPPPPPPPPPPPPPPPPPP",
            "PPWWPWWPWWPWWPWWPWWPWWPWWPWWPP",
            "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSS",
            "PPPPPPPPPPPPPPPPPPPPPPPPPPPPPP",
            "GGGGGGGGGGGGGGGGGGGGGGGGGGGGGG",
            "..OO.OO..............OO.OO....",
    };
    static final int ROWS = 10;
    private static final int COACHES = 2;
    private static final int GAP = 1;               // coupler gap between cars, in dots
    static final int COLS = COACHES * (COACH[0].length() + GAP) + LOCO[0].length();
    private static final int STACK_COL = 3;         // where the smoke comes out, from the loco's left

    /** Which pass is on screen right now, 0..1 progress; -1 if none. */
    static float progress(long now, long departs) {
        long until = departs - now;
        if (until <= 0 || until > WINDOW_MS) return -1;
        long phase = now % EVERY_MS;
        if (phase >= PASS_MS) return -1;
        // Make sure this whole pass fits before the train leaves.
        if (departs - (now - phase) < PASS_MS) return -1;
        return phase / (float) PASS_MS;
    }

    /**
     * Draws the train with its wheels on `bottom`, rolling left to right across width w.
     * t = 0..1 progress of the pass (from progress()), label rides above the coaches.
     */
    static void draw(Canvas c, Paint p, float w, float bottom, float pitch, float t,
                     String label, float alpha) {
        if (alpha <= 0.01f || t < 0) return;
        float len = COLS * pitch;
        float top = bottom - ROWS * pitch;
        float x = trainX(t, w, len, pitch);
        int step = (int) (x / pitch);   // wheels turn as the train moves a dot

        float r = pitch * 0.38f;
        float cx = x;
        for (int k = 0; k < COACHES; k++) {
            car(c, p, COACH, cx, top, pitch, r, step, alpha);
            cx += (COACH[0].length() + GAP) * pitch;
        }
        car(c, p, LOCO, cx, top, pitch, r, step, alpha);

        smoke(c, p, t, w, len, pitch, top, alpha);

        if (label != null) {
            // The time rides along above the coaches.
            float lp = pitch * 0.8f;
            float lw = DotFont.cols(label) * lp;
            float lx = x + (COACHES * (COACH[0].length() + GAP) * pitch - lw) / 2f;
            DotFont.draw(c, label, lx, top - lp * 10.5f, lp, lp * 0.36f, p, new int[]{STRIPE}, alpha);
        }
    }

    /** Left edge of the train: comes in from off the left, leaves off the right, snapped to the dots. */
    private static float trainX(float t, float w, float len, float pitch) {
        float x = -len + (w + len) * t;
        return Math.round(x / pitch) * pitch;
    }

    private static void car(Canvas c, Paint p, String[] sprite, float x, float top, float pitch,
                            float r, int step, float alpha) {
        int o = 0;
        for (int row = 0; row < sprite.length; row++) {
            String line = sprite[row];
            float cy = top + row * pitch + pitch / 2f;
            for (int col = 0; col < line.length(); col++) {
                char ch = line.charAt(col);
                int color;
                switch (ch) {
                    case 'P': color = PURPLE; break;
                    case 'S': color = STRIPE; break;
                    case 'W': color = WINDOW; break;
                    case 'G': color = STEEL; break;
                    case 'O':
                        // Each wheel is two dots; the lit one swaps as it rolls.
                        color = ((o++ + step) % 2 == 0) ? WHEEL_LIT : STEEL;
                        break;
                    default: continue;
                }
                p.setColor(Doodles.withAlpha(color, alpha));
                c.drawCircle(x + col * pitch + pitch / 2f, cy, r, p);
            }
        }
    }

    /** Small puffs from the stack that rise, spread, and fade where they were left behind. */
    private static void smoke(Canvas c, Paint p, float t, float w, float len, float pitch,
                              float top, float alpha) {
        final float every = 0.075f;     // a puff every ~1.1 s of the pass
        final float life = 0.045f;      // each lasts ~0.7 s, so it stays near the stack
                                        // (and out of the time label above)
        float locoLeft = COACHES * (COACH[0].length() + GAP) * pitch;
        int newest = (int) (t / every);
        for (int k = newest; k >= 0 && t - k * every < life; k--) {
            float born = k * every;
            float age = (t - born) / life;               // 0..1
            float sx = trainX(born, w, len, pitch) + locoLeft + (STACK_COL + 1) * pitch;
            float sy = top - pitch * (1.2f + age * 1.3f);
            int rad = (int) (age * 2f);                   // grows from a dot to a little puff
            float a = alpha * 0.55f * (1f - age);
            p.setColor(Doodles.withAlpha(SMOKE, a));
            float dr = pitch * 0.32f;
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dx = -rad; dx <= rad; dx++) {
                    if (dx * dx + dy * dy > rad * rad) continue;
                    // A loose, airy cloud: skip every other dot once it has spread.
                    if (rad > 1 && ((dx + dy) & 1) != 0) continue;
                    c.drawCircle(sx + dx * pitch, sy + dy * pitch, dr, p);
                }
            }
        }
    }

    private TrainDoodle() {}
}
