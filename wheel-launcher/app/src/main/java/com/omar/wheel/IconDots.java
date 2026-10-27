package com.omar.wheel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Turns an app icon into a small grid of colored dots. */
final class IconDots {

    /** Fill for near-black icon backgrounds (same dark tile as Netflix). */
    static final int DARK_TILE = 0xFF1C1C1F;

    /** Draws the icon at 7x size, then averages each 7x7 block into one dot. */
    static int[] sample(Drawable d, int g) {
        return sample(d, g, false);
    }

    /**
     * sharp = average only the middle 3x3 of each 7x7 patch (instead of all of it) and
     * add a little contrast, so edges stay crisp like the hand-picked logos.
     */
    static int[] sample(Drawable d, int g, boolean sharp) {
        final int s = 7;
        final int big = g * s;
        Bitmap b = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Rect old = d.copyBounds();
        d.setBounds(0, 0, big, big);
        d.draw(c);
        d.setBounds(old);
        int[] px = new int[big * big];
        b.getPixels(px, 0, big, 0, 0, big, big);
        b.recycle();

        int[] out = new int[g * g];
        final int from = sharp ? 2 : 0, to = sharp ? 5 : s;
        int n = (to - from) * (to - from);
        for (int gy = 0; gy < g; gy++) {
            for (int gx = 0; gx < g; gx++) {
                long a = 0, r = 0, gr = 0, bl = 0;
                for (int yy = from; yy < to; yy++) {
                    int rowBase = (gy * s + yy) * big + gx * s;
                    for (int xx = from; xx < to; xx++) {
                        int p = px[rowBase + xx];
                        int pa = p >>> 24;
                        a += pa;
                        r += ((p >> 16) & 0xFF) * pa;
                        gr += ((p >> 8) & 0xFF) * pa;
                        bl += (p & 0xFF) * pa;
                    }
                }
                if (a == 0) continue;
                int A = (int) (a / n);
                int R = (int) (r / a), G = (int) (gr / a), B = (int) (bl / a);
                if (sharp) {
                    R = contrast(R);
                    G = contrast(G);
                    B = contrast(B);
                }
                // Near-black parts of an icon's own background would vanish into the screen,
                // leaving the tile looking chopped off. Fill them with a dark tile color instead
                // (only where the icon is solid, so transparent edges stay empty).
                if (Math.max(R, Math.max(G, B)) < 22) {
                    if (A >= 110) out[gy * g + gx] = DARK_TILE;
                    continue;
                }
                out[gy * g + gx] = (A << 24) | (R << 16) | (G << 8) | B;
            }
        }
        return out;
    }

    private static int contrast(int v) {
        return Math.max(0, Math.min(255, Math.round((v - 128) * 1.18f + 128)));
    }

    /** A bright accent color from the icon's most saturated hues. */
    static int accent(int[] px) {
        if (px == null) return 0xFFD8D8D8;
        double sx = 0, sy = 0, wsum = 0;
        float[] hsv = new float[3];
        for (int p : px) {
            if ((p >>> 24) < 110) continue;
            Color.colorToHSV(p | 0xFF000000, hsv);
            double w = hsv[1] * hsv[2];
            if (w < 0.15) continue;
            double rad = Math.toRadians(hsv[0]);
            sx += Math.cos(rad) * w;
            sy += Math.sin(rad) * w;
            wsum += w;
        }
        if (wsum < 1.0) return 0xFFD8D8D8;
        float hue = (float) Math.toDegrees(Math.atan2(sy, sx));
        if (hue < 0) hue += 360f;
        return Color.HSVToColor(new float[]{hue, 0.7f, 1f});
    }

    private IconDots() {}
}
