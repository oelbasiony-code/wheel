package com.omar.wheel;

import android.graphics.Canvas;
import android.graphics.Paint;

/** The dot-matrix brontosaurus and the animated weather icons. */
final class Doodles {

    // ---------------------------------------------------------------- dinosaur

    static final int DINO_COLS = 20, DINO_ROWS = 14;
    private static final int DINO_GREEN = 0xFF8FD47E;

    /** Body facing right. Legs are drawn separately so they can walk. */
    private static final String[] DINO = {
            "................##..",
            "...............####.",
            "...............#####",
            "...............##...",
            "..............##....",
            "..............##....",
            ".............##.....",
            "......#########.....",
            "....###########.....",
            "...############.....",
            ".#############......",
            "#...##########......",
    };
    private static final int EYE_COL = 17, EYE_ROW = 1;
    /** Leg columns: near back, far back, far front, near front. Each leg is 2 dots wide. */
    private static final int[] LEG_COLS = {4, 7, 9, 12};
    private static final boolean[] LEG_NEAR = {true, false, false, true};
    private static final boolean[] LEG_BACK = {true, true, false, false};

    private static final float WALK_SPEED_COLS = 7f;   // dots per second
    private static final long PAUSE_MS = 2200;          // rest at each end

    /**
     * Walks back and forth between left and right, feet at bottom.
     * pitch = dot spacing. Position comes from the clock alone, so it needs no state.
     */
    static void drawDino(Canvas c, Paint p, float left, float right, float bottom,
                         float pitch, long now, float alpha, int wear) {
        if (alpha <= 0.01f) return;
        float span = (right - left) - DINO_COLS * pitch;
        if (span <= pitch) span = pitch;
        long walkMs = (long) (span / (pitch * WALK_SPEED_COLS) * 1000f);
        long period = 2 * (walkMs + PAUSE_MS);
        long t = now % period;

        float x;
        boolean facingRight, walking;
        if (t < walkMs) {                        // walking right
            x = left + span * t / walkMs;
            facingRight = true;
            walking = true;
        } else if (t < walkMs + PAUSE_MS) {      // resting on the right
            x = left + span;
            facingRight = true;
            walking = false;
        } else if (t < 2 * walkMs + PAUSE_MS) {  // walking left
            x = left + span - span * (t - walkMs - PAUSE_MS) / walkMs;
            facingRight = false;
            walking = true;
        } else {                                 // resting on the left
            x = left;
            facingRight = false;
            walking = false;
        }
        // Snap to the dot grid for a chunky, retro step.
        x = left + Math.round((x - left) / pitch) * pitch;

        int step = walking ? (int) ((now / 180) % 2) : -1;
        // Blink a couple of times while resting.
        long rest = t - (t < 2 * walkMs + PAUSE_MS ? walkMs : 2 * walkMs + PAUSE_MS);
        boolean blink = !walking && ((rest > 700 && rest < 820) || (rest > 1500 && rest < 1620));
        // Bob the head down a dot while resting, like it's grazing.
        boolean graze = !walking && rest > 900 && rest < 1450;

        float top = bottom - DINO_ROWS * pitch;
        float r = pitch * 0.38f;
        int full = withAlpha(DINO_GREEN, alpha);
        int far = withAlpha(DINO_GREEN, alpha * 0.5f);

        for (int row = 0; row < DINO.length; row++) {
            String line = DINO[row];
            for (int col = 0; col < DINO_COLS; col++) {
                if (line.charAt(col) != '#') continue;
                if (row == EYE_ROW && col == EYE_COL && !blink) continue;
                int dy = (graze && col >= 13 && row <= 6) ? 1 : 0;
                dot(c, p, x, top, pitch, facingRight ? col : DINO_COLS - 1 - col, row + dy, r, full);
            }
        }
        for (int leg = 0; leg < 4; leg++) {
            // Near and far legs swing in opposite phases.
            boolean stride = step >= 0 && ((step == 0) == LEG_NEAR[leg]);
            int shift = stride ? (LEG_BACK[leg] ? -1 : 1) : 0;
            int color = LEG_NEAR[leg] ? full : far;
            for (int k = 0; k < 2; k++) {
                int c0 = LEG_COLS[leg] + k;
                drawLegDot(c, p, x, top, pitch, facingRight, c0, 12, r, color);
                drawLegDot(c, p, x, top, pitch, facingRight, c0 + shift, 13, r, color);
            }
        }
        if (wear > 0) {
            GRID.x = x;
            GRID.top = top;
            GRID.pitch = pitch;
            GRID.r = r;
            GRID.right = facingRight;
            GRID.dy = graze ? 1 : 0;
            Season.drawWear(c, p, wear, GRID, now, alpha);
        }
    }

    private static final Grid GRID = new Grid();

    /** Maps a cell of the dinosaur's grid (as if facing right) to the screen. */
    static final class Grid {
        float x, top, pitch, r;
        boolean right;
        int dy;

        void dot(Canvas c, Paint p, int col, int row, int color) {
            int cc = right ? col : DINO_COLS - 1 - col;
            p.setColor(color);
            c.drawCircle(x + cc * pitch + pitch / 2f, top + (row + dy) * pitch + pitch / 2f, r, p);
        }
    }

    private static void drawLegDot(Canvas c, Paint p, float x, float top, float pitch,
                                   boolean right, int col, int row, float r, int color) {
        dot(c, p, x, top, pitch, right ? col : DINO_COLS - 1 - col, row, r, color);
    }

    // ---------------------------------------------------------------- weather

    static final int ICON_COLS = 14, ICON_ROWS = 11;

    private static final int SUN = 0xFFFFC94A;
    private static final int MOON = 0xFFE9E4C8;
    private static final int CLOUD = 0xFFC9CED6;
    private static final int CLOUD_DARK = 0xFF7F8794;
    /** Darker than the moon and sun so the two read apart by brightness, not just color. */
    private static final int CLOUD_FRONT = 0xFFA3AAB5;
    private static final int RAIN = 0xFF5AA9FF;
    private static final int SNOW = 0xFFF2F4F8;
    private static final int BOLT = 0xFFFFE066;
    private static final int FOG = 0xFF9EA4AE;

    /** A smaller, puffier cloud for partly cloudy and cloudy (10 x 4). */
    private static final String[] CLOUD_SMALL = {
            "...####...",
            ".########.",
            "##########",
            ".########.",
    };

    private static final String[] CLOUD_MASK = {
            "....####....",
            "..########..",
            ".##########.",
            "############",
            ".##########.",
    };

    /** WMO code -> animated icon. Draws nothing for an unknown code. */
    static void drawWeather(Canvas c, Paint p, float x, float top, float pitch,
                            int code, boolean day, long now, float alpha) {
        if (code < 0 || alpha <= 0.01f) return;
        float r = pitch * 0.38f;
        float t = Doodles.animSeconds(now);
        int drift = Math.round((float) Math.sin(t / 4f * 2 * Math.PI / 3f));

        if (code <= 1) {
            if (day) sun(c, p, x, top, pitch, 7, 5, 2.6f, true, t, alpha, r);
            else moon(c, p, x, top, pitch, 7, 5, 4.6f, now, alpha, r, true);
        } else if (code == 2) {
            // Sun or moon up top, a small cloud drifting in front of its lower half.
            // A one-dot dark gap around the cloud keeps it readable against the sun or
            // moon by shape and brightness alone, not just color.
            int cx0 = 3 + drift, cy0 = 6;
            SKIP = cloudHalo(CLOUD_SMALL, cx0, cy0);
            if (day) {
                sun(c, p, x, top, pitch, 5, 4, 2.4f, false, t, alpha, r);
            } else {
                moon(c, p, x, top, pitch, 5, 4, 3.3f, now, alpha, r, false);
            }
            SKIP = null;
            if (!day) {
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 2 * Math.PI / 3.1);
                dot(c, p, x, top, pitch, 12, 1, r, withAlpha(MOON, alpha * (0.25f + 0.75f * tw)));
            }
            cloud(c, p, x, top, pitch, CLOUD_SMALL, cx0, cy0, CLOUD_FRONT, alpha, r);
        } else if (code == 3) {
            // Two small clouds, one behind the other, drifting opposite ways.
            SKIP = cloudHalo(CLOUD_SMALL, 3 + drift, 5);   // gap so the two clouds stay separate
            cloud(c, p, x, top, pitch, CLOUD_SMALL, 1 - drift, 2, CLOUD_DARK, alpha, r);
            SKIP = null;
            cloud(c, p, x, top, pitch, CLOUD_SMALL, 3 + drift, 5, CLOUD, alpha, r);
        } else if (code == 45 || code == 48) {
            fog(c, p, x, top, pitch, t, alpha, r);
        } else if (code >= 51 && code <= 57) {
            cloud(c, p, x, top, pitch, 1 + drift, 0, CLOUD, alpha, r);
            fall(c, p, x, top, pitch, new int[]{2, 5, 8, 11}, 1, 3.5f, false, t, RAIN, alpha, r);
        } else if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) {
            cloud(c, p, x, top, pitch, 1 + drift, 0, CLOUD, alpha, r);
            fall(c, p, x, top, pitch, new int[]{2, 5, 8, 11}, 2, 7f, false, t, RAIN, alpha, r);
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            cloud(c, p, x, top, pitch, 1 + drift, 0, CLOUD, alpha, r);
            fall(c, p, x, top, pitch, new int[]{2, 5, 8, 11}, 1, 2.2f, true, t, SNOW, alpha, r);
        } else if (code >= 95) {
            cloud(c, p, x, top, pitch, 1 + drift, 0, CLOUD_DARK, alpha, r);
            fall(c, p, x, top, pitch, new int[]{3, 10}, 2, 7f, false, t, RAIN, alpha * 0.6f, r);
            long ms = now % 3200;
            if (ms < 140 || (ms > 260 && ms < 380)) bolt(c, p, x, top, pitch, alpha, r);
        }
    }

    private static void sun(Canvas c, Paint p, float x, float top, float pitch, int cx, int cy,
                            float rad, boolean longRays, float t, float alpha, float r) {
        int body = withAlpha(SUN, alpha);
        for (int gy = 0; gy < ICON_ROWS; gy++) {
            for (int gx = 0; gx < ICON_COLS; gx++) {
                float dx = gx - cx, dy = gy - cy;
                if (dx * dx + dy * dy <= rad * rad) dot(c, p, x, top, pitch, gx, gy, r, body);
            }
        }
        // Eight rays; alternate rays pulse in opposite phase so the sun twinkles.
        int[][] dirs = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2 * Math.PI / 2.4);
        int start = (int) Math.ceil(rad) + 1;
        int len = longRays ? 2 : 1;
        for (int i = 0; i < dirs.length; i++) {
            float a = (i % 2 == 0) ? pulse : 1f - pulse;
            int col = withAlpha(SUN, alpha * (0.25f + 0.75f * a));
            boolean diag = dirs[i][0] != 0 && dirs[i][1] != 0;
            int s = diag ? Math.max(1, start - 1) : start;
            for (int k = 0; k < len; k++) {
                dot(c, p, x, top, pitch, cx + dirs[i][0] * (s + k), cy + dirs[i][1] * (s + k), r, col);
            }
        }
    }

    /** 0 = new moon, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter. */
    static double moonPhase(long ms) {
        final long NEW_MOON_2000 = 947182440000L;   // 2000-01-06 18:14 UTC
        final double SYNODIC_DAYS = 29.530588853;
        double days = (ms - NEW_MOON_2000) / 86400000.0;
        double p = (days % SYNODIC_DAYS) / SYNODIC_DAYS;
        return p < 0 ? p + 1 : p;
    }

    /**
     * Tonight's actual moon: the lit part is drawn bright, the dark part as faint
     * "earthshine" dots so the round shape always shows. Waxing moons are lit on the
     * right, waning on the left (as seen from Massachusetts).
     */
    private static void moon(Canvas c, Paint p, float x, float top, float pitch, int cx, int cy,
                             float rad, long now, float alpha, float r, boolean stars) {
        float t = Doodles.animSeconds(now);
        double ph = moonPhase(now);
        double k = Math.cos(2 * Math.PI * ph);
        boolean waxing = ph < 0.5;
        int lit = withAlpha(MOON, alpha);
        int dark = withAlpha(MOON, alpha * 0.13f);
        for (int gy = 0; gy < ICON_ROWS; gy++) {
            for (int gx = 0; gx < ICON_COLS; gx++) {
                float dx = gx - cx, dy = gy - cy;
                if (dx * dx + dy * dy > rad * rad) continue;
                double half = Math.sqrt(Math.max(0, rad * rad - dy * dy));
                boolean on = waxing ? dx > half * k : dx < -half * k;
                dot(c, p, x, top, pitch, gx, gy, r, on ? lit : dark);
            }
        }
        if (!stars) return;
        float tw = 0.5f + 0.5f * (float) Math.sin(t * 2 * Math.PI / 3.1);
        dot(c, p, x, top, pitch, 13, 0, r, withAlpha(MOON, alpha * (0.2f + 0.8f * tw)));
        dot(c, p, x, top, pitch, 0, 9, r, withAlpha(MOON, alpha * (1f - 0.8f * tw)));
    }

    private static void cloud(Canvas c, Paint p, float x, float top, float pitch, int ox, int oy,
                              int color, float alpha, float r) {
        cloud(c, p, x, top, pitch, CLOUD_MASK, ox, oy, color, alpha, r);
    }

    private static void cloud(Canvas c, Paint p, float x, float top, float pitch, String[] mask,
                              int ox, int oy, int color, float alpha, float r) {
        int col = withAlpha(color, alpha);
        for (int row = 0; row < mask.length; row++) {
            String line = mask[row];
            for (int k = 0; k < line.length(); k++) {
                if (line.charAt(k) == '#') dot(c, p, x, top, pitch, ox + k, oy + row, r, col);
            }
        }
    }

    /** Rain or snow falling from under the cloud (rows 5-10). */
    private static void fall(Canvas c, Paint p, float x, float top, float pitch, int[] cols, int len,
                             float speed, boolean wobble, float t, int color, float alpha, float r) {
        final int y0 = 5, h = 6;
        for (int i = 0; i < cols.length; i++) {
            float phase = i * 2.3f;
            int head = (int) Math.floor((t * speed + phase) % h);
            int gx = cols[i];
            if (wobble) gx += Math.round((float) Math.sin(t * 1.7f + i));
            for (int k = 0; k < len; k++) {
                int gy = head - k;
                if (gy < 0) continue;
                float a = alpha * (k == 0 ? 1f : 0.5f);
                dot(c, p, x, top, pitch, gx, y0 + gy, r, withAlpha(color, a));
            }
        }
    }

    private static void bolt(Canvas c, Paint p, float x, float top, float pitch, float alpha, float r) {
        int col = withAlpha(BOLT, alpha);
        int[][] pts = {{8, 5}, {7, 6}, {6, 7}, {7, 7}, {8, 7}, {7, 8}, {6, 9}, {5, 10}};
        for (int[] q : pts) dot(c, p, x, top, pitch, q[0], q[1], r, col);
    }

    private static void fog(Canvas c, Paint p, float x, float top, float pitch, float t, float alpha, float r) {
        for (int i = 0; i < 4; i++) {
            int gy = 2 + i * 2;
            int shift = Math.round((float) Math.sin(t * 0.8f + i * 1.3f) * 1.5f);
            int col = withAlpha(FOG, alpha * (0.45f + 0.15f * i));
            for (int gx = 1; gx < 13; gx++) {
                if ((gx + i) % 5 == 0) continue; // small gaps so the bands read as mist
                dot(c, p, x, top, pitch, gx + shift, gy, r, col);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Seconds for animations, wrapped to the current day. Today's full timestamp is too
     * big for a float to hold to the second, which would freeze the animations.
     */
    static float animSeconds(long now) {
        return (now % 86400000L) / 1000f;
    }

    /** Cells to leave empty while drawing (the gap around a cloud), or null. */
    private static boolean[] SKIP;

    /** The cloud's cells plus every cell touching them. */
    private static boolean[] cloudHalo(String[] mask, int ox, int oy) {
        boolean[] out = new boolean[ICON_COLS * ICON_ROWS];
        for (int row = 0; row < mask.length; row++) {
            for (int k = 0; k < mask[row].length(); k++) {
                if (mask[row].charAt(k) != '#') continue;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int gx = ox + k + dx, gy = oy + row + dy;
                        if (gx >= 0 && gx < ICON_COLS && gy >= 0 && gy < ICON_ROWS) out[gy * ICON_COLS + gx] = true;
                    }
                }
            }
        }
        return out;
    }

    private static void dot(Canvas c, Paint p, float x, float top, float pitch, int gx, int gy, float r, int color) {
        if (SKIP != null && gx >= 0 && gx < ICON_COLS && gy >= 0 && gy < ICON_ROWS
                && SKIP[gy * ICON_COLS + gx]) return;
        p.setColor(color);
        c.drawCircle(x + gx * pitch + pitch / 2f, top + gy * pitch + pitch / 2f, r, p);
    }

    static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private Doodles() {}
}
