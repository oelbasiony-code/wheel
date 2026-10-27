package com.omar.wheel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Turns an app icon into a small grid of colored dots. */
final class IconDots {

    /** Fallback fill for near-black icon backgrounds when the icon has no visible dark shade. */
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
        boolean[] darkGap = new boolean[g * g];
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
                    if (A >= 110) darkGap[gy * g + gx] = true;
                    continue;
                }
                out[gy * g + gx] = (A << 24) | (R << 16) | (G << 8) | B;
            }
        }
        // Fill each gap from its nearest visible background dot, so the icon's own colors
        // and gradients carry into the gap instead of a flat patch. Icons with no visible
        // background at all get the default dark tile.
        fillGaps(out, darkGap, g);
        return out;
    }

    /**
     * Makes the whole tile one background color, the icon's own top-right corner shade,
     * keeping only the dots that match the brand color (the logo).
     */
    static void solidBackground(int[] icon, int g, int brand) {
        if (icon == null) return;
        int bg = 0;
        // top-right corner: scan inward until an opaque, non-logo dot is found
        for (int k = 0; k < g && bg == 0; k++) {
            for (int r = 0; r <= k && bg == 0; r++) {
                int p = icon[r * g + (g - 1 - (k - r))];
                if ((p >>> 24) >= 110 && !isLogo(p, brand)) bg = 0xFF000000 | (p & 0xFFFFFF);
            }
        }
        if (bg == 0) return;
        for (int i = 0; i < icon.length; i++) {
            if (!isLogo(icon[i], brand)) icon[i] = bg;
        }
    }

    /** Close to the brand's hue and reasonably saturated. */
    private static boolean isLogo(int p, int brand) {
        if ((p >>> 24) < 110) return false;
        float[] a = new float[3], b = new float[3];
        Color.colorToHSV(p | 0xFF000000, a);
        Color.colorToHSV(brand | 0xFF000000, b);
        float dh = Math.abs(a[0] - b[0]);
        if (dh > 180) dh = 360 - dh;
        return dh < 40 && a[1] > 0.3f && a[2] > 0.3f;
    }

    /** Saves the icon as a 256 x 256 PNG named after the app's package (once per app). */
    static void export(Drawable d, java.io.File dir, String pkg) {
        if (dir == null) return;
        java.io.File f = new java.io.File(dir, pkg + ".png");
        if (f.exists()) return;
        Bitmap b = null;
        java.io.FileOutputStream out = null;
        try {
            dir.mkdirs();
            b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Rect old = d.copyBounds();
            d.setBounds(0, 0, 256, 256);
            d.draw(c);
            d.setBounds(old);
            out = new java.io.FileOutputStream(f);
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
            if (b != null) b.recycle();
        }
    }

    /** Grows the visible background into the gaps, one ring at a time (keeps gradients). */
    private static void fillGaps(int[] out, boolean[] gap, int g) {
        boolean any = false;
        for (boolean b : gap) any |= b;
        if (!any) return;
        boolean[] src = new boolean[out.length];
        boolean haveSrc = false;
        for (int i = 0; i < out.length; i++) {
            src[i] = !gap[i] && isBackground(out[i]);
            haveSrc |= src[i];
        }
        if (!haveSrc) {
            for (int i = 0; i < out.length; i++) if (gap[i]) out[i] = DARK_TILE;
            return;
        }
        boolean[] done = src.clone();
        boolean changed = true;
        while (changed) {
            changed = false;
            int[] next = out.clone();
            boolean[] nextDone = done.clone();
            for (int y = 0; y < g; y++) {
                for (int x = 0; x < g; x++) {
                    int i = y * g + x;
                    if (!gap[i] || done[i]) continue;
                    long r = 0, gg = 0, b = 0;
                    int n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < 0 || yy < 0 || xx >= g || yy >= g) continue;
                            int k = yy * g + xx;
                            if (!done[k]) continue;
                            r += (out[k] >> 16) & 0xFF;
                            gg += (out[k] >> 8) & 0xFF;
                            b += out[k] & 0xFF;
                            n++;
                        }
                    }
                    if (n == 0) continue;
                    next[i] = 0xFF000000 | ((int) (r / n) << 16) | ((int) (gg / n) << 8) | (int) (b / n);
                    nextDone[i] = true;
                    changed = true;
                }
            }
            System.arraycopy(next, 0, out, 0, out.length);
            System.arraycopy(nextDone, 0, done, 0, done.length);
        }
        for (int i = 0; i < out.length; i++) if (gap[i] && !done[i]) out[i] = DARK_TILE;
    }

    /** Dark and neutral (not part of a colored logo). */
    private static boolean isBackground(int p) {
        if ((p >>> 24) < 110) return false;
        int R = (p >> 16) & 0xFF, G = (p >> 8) & 0xFF, B = p & 0xFF;
        int mx = Math.max(R, Math.max(G, B)), mn = Math.min(R, Math.min(G, B));
        return mx < 110;   // dark enough to be background (neutral or tinted)
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
