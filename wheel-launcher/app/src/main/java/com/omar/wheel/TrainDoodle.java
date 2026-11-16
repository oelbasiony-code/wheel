package com.omar.wheel;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * A dot-matrix commuter rail train (MBTA purple) that rolls along the bottom of the
 * screen with its departure time riding above it, puffing smoke. It only runs in the hour
 * before your train leaves, once every 5 minutes, so it stays special. (The whistle is in
 * AmbientView: first and last pass of the hour.)
 */
final class TrainDoodle {

    static final long PASS_MS = 15000;              // one trip across the screen
    static final long EVERY_MS = 5 * 60 * 1000L;    // a pass every 5 minutes...
    static final long WINDOW_MS = 60 * 60 * 1000L;  // ...in the hour before departure

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
            // Small, centered over the whole train, above the smoke.
            float lp = pitch * 0.55f;
            float lw = DotFont.cols(label) * lp;
            float lx = x + (len - lw) / 2f;
            float ly = top - pitch * 8.5f - 7 * lp;   // above the smoke
            DotFont.draw(c, label, lx, ly, lp, lp * 0.38f, p, new int[]{STRIPE}, alpha);
        }
    }

    /** Where the train is now: {left, right} in px, for clearing the news ticker around it. */
    static float[] span(float t, float w, float pitch) {
        float len = COLS * pitch;
        float x = trainX(t, w, len, pitch);
        return new float[]{x, x + len};
    }

    /** Rows down from the train's top to the middle of its body (to line it up with the ticker). */
    static final float BODY_MID_ROWS = 4.5f;

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

    /**
     * Puffs from the stack: each rises, spreads into a loose cloud and fades, staying where it
     * was let out, so a trail drifts back over the coaches as the train rolls on.
     */
    private static void smoke(Canvas c, Paint p, float t, float w, float len, float pitch,
                              float top, float alpha) {
        final float every = 0.05f;      // a puff every ~0.75 s of the pass
        final float life = 0.17f;       // each lasts ~2.5 s
        float locoLeft = COACHES * (COACH[0].length() + GAP) * pitch;
        int newest = (int) (t / every);
        float dr = pitch * 0.4f;
        for (int k = newest; k >= 0 && t - k * every < life; k--) {
            float born = k * every;
            float age = (t - born) / life;               // 0..1
            float sx = trainX(born, w, len, pitch) + locoLeft + (STACK_COL + 1) * pitch
                    - age * pitch * 3;                   // a little drift back
            float sy = top - pitch * (1f + age * 4.5f);  // rises about 4 rows
            float rad = 0.6f + age * 2.4f;               // a dot, then a soft cloud
            float a = alpha * 0.85f * (1f - age);
            p.setColor(Doodles.withAlpha(SMOKE, a));
            int ri = (int) Math.ceil(rad);
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dx = -ri; dx <= ri; dx++) {
                    if (dx * dx + dy * dy > rad * rad) continue;
                    // Airy: skip a checkerboard of dots once it has spread.
                    if (rad > 1.5f && ((dx + dy + k) & 1) != 0) continue;
                    c.drawCircle(sx + dx * pitch, sy + dy * pitch, dr, p);
                }
            }
        }
    }

    private TrainDoodle() {}
}
