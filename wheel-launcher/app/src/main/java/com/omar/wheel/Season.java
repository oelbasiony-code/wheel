package com.omar.wheel;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Holiday themes. The dinosaur's costume and the icon next to the date last the
 * whole month (tree: Dec 1-26); the screen-wide effect and greeting are for the day itself.
 *
 *  October                  pumpkin, witch hat; bats and greeting on Oct 31
 *  November                 turkey (until Thanksgiving), scarf; leaves and greeting on Thanksgiving
 *  December                 tree (Dec 1-26), Santa hat; snow and greeting on Dec 25
 *  Ramadan (whole month)    lantern, lantern (no screen effect)
 *  Eid al-Fitr / al-Adha    crescent, party hat, gold sparkles
 *  Birthday (Apr 30)        cake, party hat, confetti
 */
final class Season {

    // icons next to the date
    static final int ICON_NONE = 0, ICON_PUMPKIN = 1, ICON_TURKEY = 2, ICON_TREE = 3,
            ICON_LANTERN = 4, ICON_CRESCENT = 5, ICON_CAKE = 6;
    // dinosaur costumes
    static final int WEAR_NONE = 0, WEAR_WITCH = 1, WEAR_SCARF = 2, WEAR_SANTA = 3,
            WEAR_LANTERN = 4, WEAR_PARTY_EID = 5, WEAR_PARTY_BDAY = 6;
    // screen-wide effects (only on the day)
    static final int FX_NONE = 0, FX_BATS = 1, FX_LEAVES = 2, FX_SNOW = 3, FX_SPARKLE = 4, FX_CONFETTI = 5;

    static final class Theme {
        int icon, wear, fx;
        String greeting; // null = the normal "GOOD EVENING, OMAR"
    }

    private static Theme cached;
    private static long cachedMinute = -1;

    /** Today's theme (recomputed once a minute). */
    static Theme today(long nowMs) {
        long minute = nowMs / 60000L;
        if (cached != null && minute == cachedMinute) return cached;
        cachedMinute = minute;
        cached = compute(nowMs);
        return cached;
    }

    private static Theme compute(long nowMs) {
        Theme t = new Theme();
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.setTimeInMillis(nowMs);
        if (Config.PREVIEW_DATE != null) {
            // "YYYY-MM-DD" to try a theme early. Keeps the current time of day.
            String[] parts = Config.PREVIEW_DATE.split("-");
            cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
        }
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int[] hijri = hijri(cal.getTimeInMillis());   // {month 1-12, day} or null
        String name = Config.NAME;

        boolean birthday = month == Config.BIRTHDAY_MONTH && day == Config.BIRTHDAY_DAY;
        boolean eidFitr = hijri != null && hijri[0] == 10 && hijri[1] <= 3;
        boolean eidAdha = hijri != null && hijri[0] == 12 && hijri[1] >= 10 && hijri[1] <= 13;
        boolean ramadan = hijri != null && hijri[0] == 9;
        boolean halloween = month == 10 && day == 31;
        // Thanksgiving: the fourth Thursday of November (always the 22nd-28th).
        boolean thanksgiving = month == 11 && day >= 22 && day <= 28
                && cal.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY;
        boolean christmas = month == 12 && day == 25;

        // The dinosaur dresses up for the whole month (or all of Ramadan);
        // the icon by the date, the screen effect and the greeting are for the day itself.
        if (ramadan) t.wear = WEAR_LANTERN;
        else if (month == 10) t.wear = WEAR_WITCH;
        else if (month == 11) t.wear = WEAR_SCARF;
        else if (month == 12 && day <= 25) t.wear = WEAR_SANTA;   // hat comes off on the 26th

        if (birthday) {
            t.icon = ICON_CAKE;
            t.wear = WEAR_PARTY_BDAY;
            t.fx = FX_CONFETTI;
            t.greeting = "HAPPY BIRTHDAY, " + name;
        } else if (eidFitr || eidAdha) {
            t.icon = ICON_CRESCENT;
            t.wear = WEAR_PARTY_EID;
            t.fx = FX_SPARKLE;
            t.greeting = "EID MUBARAK, " + name;
        } else if (ramadan) {
            // Ramadan is the month itself: lantern and greeting throughout.
            t.icon = ICON_LANTERN;
            t.greeting = "RAMADAN KAREEM, " + name;
        } else if (halloween) {
            t.icon = ICON_PUMPKIN;
            t.fx = FX_BATS;
            t.greeting = "HAPPY HALLOWEEN, " + name;
        } else if (thanksgiving) {
            t.icon = ICON_TURKEY;
            t.fx = FX_LEAVES;
            t.greeting = "HAPPY THANKSGIVING, " + name;
        } else if (christmas) {
            t.icon = ICON_TREE;
            t.fx = FX_SNOW;
            t.greeting = "MERRY CHRISTMAS, " + name;
        } else if (month == 10) {
            t.icon = ICON_PUMPKIN;       // all of October
        } else if (month == 12 && day <= 25) {
            t.icon = ICON_TREE;          // December 1st through Christmas, gone on the 26th
        }
        return t;
    }

    /** Day of November that Thanksgiving falls on this year (the fourth Thursday). */
    private static int thanksgivingDay(Calendar today) {
        Calendar n = (Calendar) today.clone();
        n.set(Calendar.MONTH, Calendar.NOVEMBER);
        n.set(Calendar.DAY_OF_MONTH, 1);
        int firstThursday = 1 + (Calendar.THURSDAY - n.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        return firstThursday + 21;
    }

    /** Islamic (Umm al-Qura) month and day, using the calendar built into Fire OS. */
    private static int[] hijri(long ms) {
        if (Build.VERSION.SDK_INT < 24) return null;
        try {
            android.icu.util.IslamicCalendar ic = new android.icu.util.IslamicCalendar();
            ic.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            ic.setTimeInMillis(ms + Config.HIJRI_ADJUST_DAYS * 86400000L);
            return new int[]{ic.get(android.icu.util.Calendar.MONTH) + 1,
                    ic.get(android.icu.util.Calendar.DAY_OF_MONTH)};
        } catch (Throwable e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- date icons

    private static final String[][] ICONS = {
            null,
            { // jack-o'-lantern: O deep orange, g stem, y candle-lit eyes and grin (flicker)
                    ".....gg....",
                    "..OOOOOOO..",
                    ".OOOOOOOOO.",
                    "OOyOOOOOyOO",
                    "OyyyOOOyyyO",
                    "OOOOOOOOOOO",
                    "OOyyyyyyyOO",
                    "OOOyOyOyOOO",
                    "..OOOOOOO.."},
            { // turkey: fan r/o/y, h head, k beak/feet, w wattle, b body
                    "...rrrrr...",
                    "..rooooor..",
                    ".royyyyyor.",
                    "roy..h..yor",
                    "roy.bkb.yor",
                    "ro.bbwbb.or",
                    ".r.bbbbb.r.",
                    "...bbbbb...",
                    "....bbb....",
                    "...k...k..."},
            { // tree: s star, t tiers, L lights, k trunk
                    ".....s.....",
                    "....sss....",
                    ".....t.....",
                    "....tLt....",
                    "...ttttt...",
                    "..tLtttLt..",
                    "...ttLtt...",
                    "..tttttLt..",
                    ".tLttttttt.",
                    "tttttLtttLt",
                    "....kkk...."},
            { // lantern (fanous): G dark gold frame, f bright windows
                    "....G....",
                    "...G.G...",
                    "....G....",
                    "...GGG...",
                    ".GGGGGGG.",
                    ".GfGfGfG.",
                    ".GfGfGfG.",
                    ".GfGfGfG.",
                    ".GGGGGGG.",
                    "..GGGGG..",
                    "....G...."},
            { // crescent and star
                    "..ggg....",
                    ".gg......",
                    "gg....s..",
                    "gg...sss.",
                    "gg....s..",
                    ".gg......",
                    "..ggg....",
                    "........."},
            { // cake: f flames, c candles, P frosting, w cake, d sprinkles, k plate
                    "..f.f.f..",
                    "..c.c.c..",
                    ".PPPPPPP.",
                    ".wwwwwww.",
                    ".wdwdwdw.",
                    ".wwwwwww.",
                    "kkkkkkkkk",
                    "........."},
    };

    /** Irregular candle flicker, 0..1. */
    private static float candle(float t) {
        double v = 0.55 + 0.25 * Math.sin(t * 7.3) + 0.15 * Math.sin(t * 13.1 + 1.7) + 0.1 * Math.sin(t * 2.3);
        return (float) Math.max(0, Math.min(1, v));
    }

    private static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static final int[] LIGHTS = {0xFFFF4D4D, 0xFFFFD24D, 0xFF4DA6FF, 0xFFFF7AE0};

    /** The icon next to the date: left edge x, centered on centerY. All icons are the same height. */
    static void drawIcon(Canvas c, Paint p, int icon, float x, float centerY, float height, long now, float alpha) {
        if (icon <= 0 || icon >= ICONS.length || alpha <= 0.01f) return;
        String[] rows = ICONS[icon];
        float pitch = height / rows.length;
        float top = centerY - height / 2f;
        float t = Doodles.animSeconds(now);
        float r = pitch * 0.4f;
        float flicker = 0.8f + 0.2f * (float) Math.abs(Math.sin(t * 3.1) * Math.sin(t * 1.7 + 1));
        int lightIdx = 0;
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            for (int col = 0; col < line.length(); col++) {
                char ch = line.charAt(col);
                if (ch == '.') continue;
                int color;
                float a = alpha;
                switch (ch) {
                    case 'o': color = 0xFFFF8A1F; break;
                    case 'O': color = 0xFFE0601A; break;
                    case 'g': color = icon == ICON_PUMPKIN ? 0xFF6BBF4A : 0xFFD9A441; break;
                    case 'y':
                        if (icon == ICON_PUMPKIN) {
                            // Candle inside: flickers between warm amber and bright yellow.
                            float k = candle(t);
                            color = mix(0xFFFFA020, 0xFFFFF0A0, k);
                        } else {
                            color = 0xFFFFE27A;
                            a *= flicker;
                        }
                        break;
                    case 'r': color = 0xFFD9472B; break;
                    case 'b': color = 0xFF9A6438; break;
                    case 'h': color = 0xFFC9B79C; break;
                    case 'w': color = icon == ICON_TURKEY ? 0xFFE8433A : 0xFFF5EBDD; break;
                    case 's': color = 0xFFFFD84D; a *= 0.8f + 0.2f * (float) Math.sin(t * 4); break;
                    case 't': color = 0xFF3FA65A; break;
                    case 'L': color = LIGHTS[(int) ((lightIdx++ + (long) (t * 2.5f)) % LIGHTS.length)]; break;
                    case 'k': color = icon == ICON_CAKE ? 0xFF8F96A3 : icon == ICON_TURKEY ? 0xFFFFB13B : 0xFF8A5A33; break;
                    case 'f': color = icon == ICON_LANTERN ? 0xFFFFF0A8 : 0xFFFFB347; a *= flicker; break;
                    case 'G': color = 0xFFB07A2A; break;
                    case 'c': color = 0xFFF5EBDD; break;
                    case 'P': color = 0xFFFF8FC8; break;
                    case 'd': color = LIGHTS[(col + row) % LIGHTS.length]; break;
                    default: color = 0xFFFFFFFF;
                }
                p.setColor(Doodles.withAlpha(color, a));
                c.drawCircle(x + col * pitch + pitch / 2f, top + row * pitch + pitch / 2f, r, p);
            }
        }
    }

    // ---------------------------------------------------------------- dinosaur costumes

    /**
     * Each costume is a little mask placed on the dinosaur's grid (facing right):
     * startCol / startRow give where the mask's top-left dot lands. Rows can be negative
     * (above the head).
     */
    private static final class Wear {
        final int col, row;
        final String[] mask;

        Wear(int col, int row, String... mask) {
            this.col = col;
            this.row = row;
            this.mask = mask;
        }
    }

    private static final Wear[] WEARS = {
            null,
            new Wear(14, -6, // witch hat: tall, bent tip, orange band, brim sits on the head
                    ".p....",
                    "..p...",
                    "..pp..",
                    "..pp..",
                    ".pppp.",
                    ".oooo.",
                    "pppppp"),
            new Wear(13, 4, // scarf around the neck: o / r stripes, dangling end
                    "oooo.",
                    "rrrro",
                    "....r",
                    "....o"),
            new Wear(13, -3, // Santa hat flopping back, pompom on the tip, white brim on the head
                    "..rrr.",
                    ".rrrrr",
                    "wrrrrr",
                    "..wwww"),
            new Wear(18, 3, // lantern hanging from the snout: s string, g gold, f light
                    ".s.",
                    ".g.",
                    "ggg",
                    "gfg",
                    ".g."),
            new Wear(15, -3, // party hat (Eid colors)
                    ".y..",
                    ".a..",
                    ".ab.",
                    "baba"),
            new Wear(15, -3, // party hat (birthday colors)
                    ".y..",
                    ".a..",
                    ".ab.",
                    "baba"),
    };

    /** Draws the costume. place(col,row) converts a dino grid cell to screen coordinates. */
    static void drawWear(Canvas c, Paint p, int wear, Doodles.Grid g, long now, float alpha) {
        if (wear <= 0 || wear >= WEARS.length || alpha <= 0.01f) return;
        Wear w = WEARS[wear];
        float t = Doodles.animSeconds(now);
        float flicker = 0.8f + 0.2f * (float) Math.abs(Math.sin(t * 3.1) * Math.sin(t * 1.7 + 1));
        for (int i = 0; i < w.mask.length; i++) {
            String line = w.mask[i];
            for (int k = 0; k < line.length(); k++) {
                char ch = line.charAt(k);
                if (ch == '.') continue;
                int color;
                float a = alpha;
                switch (ch) {
                    case 'p': color = 0xFF8456C9; break;
                    case 'o': color = 0xFFFF8A1F; break;
                    case 'r': color = 0xFFE23B3B; break;
                    case 'w': color = 0xFFF5F2EA; break;
                    case 's': color = 0xFFB89A5A; break;
                    case 'g': color = 0xFFE6B84A; break;
                    case 'f': color = 0xFFFFD27A; a *= flicker; break;
                    case 'y': color = 0xFFFFE066; break;
                    case 'a': color = wear == WEAR_PARTY_EID ? 0xFF3FBF7F : 0xFFFF6FB5; break;
                    case 'b': color = wear == WEAR_PARTY_EID ? 0xFFE6B84A : 0xFF4DC3FF; break;
                    default: color = 0xFFFFFFFF;
                }
                g.dot(c, p, w.col + k, w.row + i, Doodles.withAlpha(color, a));
            }
        }
    }

    // ---------------------------------------------------------------- screen-wide effects

    private static float rnd(int i, int salt) {
        double v = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    /** Two flap positions: wings up, wings down. Pointed ears, scalloped wing edges. */
    private static final String[][] BAT = {
            {"##...........##",
             ".###..#.#..###.",
             "..####...####..",
             "...##.###.##...",
             "......###......",
             ".......#......."},
            {"......#.#......",
             "......###......",
             "...#########...",
             ".####.###.####.",
             "##.#..###..#.##",
             "#......#......#"},
    };

    /** Faint full-screen effect for the day itself. */
    static void drawEffect(Canvas c, Paint p, int fx, float w, float h, long now, float alpha) {
        if (fx == FX_NONE || alpha <= 0.01f) return;
        float t = Doodles.animSeconds(now);
        switch (fx) {
            case FX_SNOW:
                fallers(c, p, w, h, t, 60, 28f, 0.9f, h / 420f, new int[]{0xFFF2F4F8}, 0.55f * alpha);
                break;
            case FX_LEAVES:
                fallers(c, p, w, h, t, 28, 40f, 2.2f, h / 260f,
                        new int[]{0xFFFF8A1F, 0xFFD9472B, 0xFFE6B84A, 0xFFB5651D}, 0.6f * alpha);
                break;
            case FX_CONFETTI:
                fallers(c, p, w, h, t, 70, 70f, 1.5f, h / 360f,
                        new int[]{0xFFFF6FB5, 0xFF4DC3FF, 0xFFFFE066, 0xFF7CE38B, 0xFFB58CFF}, 0.7f * alpha);
                break;
            case FX_SPARKLE:
                for (int i = 0; i < 40; i++) {
                    float x = rnd(i, 1) * w, y = rnd(i, 2) * h;
                    float tw = (float) Math.sin(t * (1.2 + rnd(i, 3) * 1.6) + rnd(i, 4) * 6.28);
                    if (tw < 0.2f) continue;
                    float r = h / 400f * (1 + tw);
                    p.setColor(Doodles.withAlpha(0xFFFFD84D, alpha * 0.6f * tw));
                    c.drawCircle(x, y, r, p);
                    if (tw > 0.85f) { // a little four-point sparkle at the peak
                        float s = r * 2.2f;
                        c.drawCircle(x - s, y, r * 0.5f, p);
                        c.drawCircle(x + s, y, r * 0.5f, p);
                        c.drawCircle(x, y - s, r * 0.5f, p);
                        c.drawCircle(x, y + s, r * 0.5f, p);
                    }
                }
                break;
            case FX_BATS:
                float bp = h / 220f;
                for (int i = 0; i < 6; i++) {
                    float speed = 60f + rnd(i, 1) * 50f;
                    float span = w + 30 * bp;
                    float x = ((t * speed + rnd(i, 2) * span) % span) - 15 * bp;
                    float y = h * (0.12f + 0.7f * rnd(i, 3)) + (float) Math.sin(t * 1.3 + i) * h * 0.03f;
                    String[] f = BAT[(int) ((now / 160 + i) % 2)];
                    p.setColor(Doodles.withAlpha(0xFFB79CFF, alpha * 0.85f));
                    for (int row = 0; row < f.length; row++) {
                        for (int col = 0; col < f[row].length(); col++) {
                            if (f[row].charAt(col) == '#') {
                                c.drawCircle(x + col * bp, y + row * bp, bp * 0.42f, p);
                            }
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    /** Dots drifting down with a gentle sideways sway (snow, leaves, confetti). */
    private static void fallers(Canvas c, Paint p, float w, float h, float t, int n, float speed,
                                float sway, float r, int[] colors, float alpha) {
        for (int i = 0; i < n; i++) {
            float sp = speed * (0.6f + 0.8f * rnd(i, 1));
            float span = h + 40;
            float y = ((t * sp + rnd(i, 2) * span) % span) - 20;
            float x = rnd(i, 3) * w + (float) Math.sin(t * sway * (0.6 + rnd(i, 4)) + i) * h * 0.02f;
            float size = r * (0.7f + 0.6f * rnd(i, 5));
            p.setColor(Doodles.withAlpha(colors[i % colors.length], alpha));
            c.drawCircle(x, y, size, p);
        }
    }

    private Season() {}
}
