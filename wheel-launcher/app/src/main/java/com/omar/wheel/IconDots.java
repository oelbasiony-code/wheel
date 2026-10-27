package com.omar.wheel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Turns an app icon into a small grid of colored dots. */
final class IconDots {

    /** Draws the icon at 7x size, then averages each 7x7 block into one dot. */
    static int[] sample(Drawable d, int g) {
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
        int n = s * s;
        for (int gy = 0; gy < g; gy++) {
            for (int gx = 0; gx < g; gx++) {
                long a = 0, r = 0, gr = 0, bl = 0;
                for (int yy = 0; yy < s; yy++) {
                    int rowBase = (gy * s + yy) * big + gx * s;
                    for (int xx = 0; xx < s; xx++) {
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
                // Near-black dots would vanish on the black background anyway.
                if (Math.max(R, Math.max(G, B)) < 22) continue;
                out[gy * g + gx] = (A << 24) | (R << 16) | (G << 8) | B;
            }
        }
        return out;
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
