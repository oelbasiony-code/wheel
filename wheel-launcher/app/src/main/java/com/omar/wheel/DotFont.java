package com.omar.wheel;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.SparseArray;

/** A 5x7 dot-matrix font drawn as circles. */
final class DotFont {

    static final char UP = '↑';
    static final char DOWN = '↓';
    static final char DEG = '°';

    /** data[0] = width in columns, data[1..7] = row bitmasks (bit c = column c). */
    private static final SparseArray<int[]> GLYPHS = new SparseArray<>();

    static {
        g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        g('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###.");
        g('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..");
        g('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        g('N', "#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#");
        g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        g('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        g('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        g('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        g('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#.");
        g('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        g('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        g('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        g('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        g('3', "#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###.");
        g('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        g('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        g('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.");
        g('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        g('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        g('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..");
        g('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        g('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....");
        g('/', "....#", "....#", "...#.", "..#..", ".#...", "#....", "#....");
        g('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..");
        g('&', ".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#");
        g(':', "...", ".#.", ".#.", "...", ".#.", ".#.", "...");
        g('.', ".", ".", ".", ".", ".", ".", "#");
        g(',', "..", "..", "..", "..", "..", ".#", "#.");
        g('\'', "#", "#", ".", ".", ".", ".", ".");
        g('!', "#", "#", "#", "#", "#", ".", "#");
        g(' ', "...", "...", "...", "...", "...", "...", "...");
        g(DEG, "###", "#.#", "###", "...", "...", "...", "...");
        g(UP, "..#..", ".###.", "#.#.#", "..#..", "..#..", "..#..", "..#..");
        g(DOWN, "..#..", "..#..", "..#..", "..#..", "#.#.#", ".###.", "..#..");
    }

    private static void g(char ch, String... rows) {
        int w = rows[0].length();
        int[] data = new int[8];
        data[0] = w;
        for (int r = 0; r < 7; r++) {
            int bits = 0;
            String row = rows[r];
            for (int c = 0; c < w; c++) {
                if (row.charAt(c) == '#') bits |= 1 << c;
            }
            data[r + 1] = bits;
        }
        GLYPHS.put(ch, data);
    }

    private static int[] glyph(char ch) {
        return GLYPHS.get(Character.toUpperCase(ch));
    }

    /** Uppercases and drops characters the font can't draw. */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        boolean lastSpace = true;
        for (int i = 0; i < s.length(); i++) {
            char ch = Character.toUpperCase(s.charAt(i));
            if (ch == ' ' || Character.isWhitespace(ch)) {
                if (!lastSpace) b.append(' ');
                lastSpace = true;
            } else if (GLYPHS.get(ch) != null) {
                b.append(ch);
                lastSpace = false;
            }
        }
        return b.toString().trim();
    }

    /** Width of a string in dot columns (1 empty column between glyphs). */
    static int cols(String s) {
        int c = 0;
        boolean first = true;
        for (int i = 0; i < s.length(); i++) {
            int[] d = glyph(s.charAt(i));
            if (d == null) continue;
            if (!first) c += 1;
            c += d[0];
            first = false;
        }
        return c;
    }

    /**
     * Draws text with its top-left corner at (x, top). colors cycle per letter.
     * alpha (0..1) multiplies the colors' own alpha. Returns the drawn width in px.
     */
    static float draw(Canvas cv, String s, float x, float top, float pitch, float r,
                      Paint p, int[] colors, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        if (a == 0) return cols(s) * pitch;
        float cx = x;
        int letter = 0;
        boolean first = true;
        float half = pitch * 0.5f;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int[] d = glyph(ch);
            if (d == null) continue;
            if (!first) cx += pitch;
            first = false;
            int col = colors[letter % colors.length];
            if (ch != ' ') letter++;
            int ca = Math.round(((col >>> 24) & 0xFF) * (a / 255f));
            p.setColor((col & 0x00FFFFFF) | (ca << 24));
            int w = d[0];
            for (int row = 0; row < 7; row++) {
                int bits = d[row + 1];
                if (bits == 0) continue;
                float cy = top + row * pitch + half;
                for (int c = 0; c < w; c++) {
                    if ((bits & (1 << c)) != 0) cv.drawCircle(cx + c * pitch + half, cy, r, p);
                }
            }
            cx += w * pitch;
        }
        return cx - x;
    }

    private DotFont() {}
}
