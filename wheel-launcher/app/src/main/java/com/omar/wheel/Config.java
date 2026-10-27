package com.omar.wheel;

/**
 * Everything personal lives here. Edit this file to change the apps,
 * their order, their colors, your name, or the weather location.
 */
final class Config {

    /** Shown in the greeting: "GOOD MORNING, OMAR". */
    static final String NAME = "OMAR";

    /** Weather location (Andover, MA). Change to move the forecast. */
    static final double LAT = 42.66;
    static final double LON = -71.14;

    /** Minutes with no button presses before idle mode (big clock in the middle). 0 = never. */
    static final int IDLE_MINUTES = 3;

    /** Birthday (April 30): cake, party hat and confetti on the day. */
    static final int BIRTHDAY_MONTH = 4;
    static final int BIRTHDAY_DAY = 30;

    /**
     * Ramadan and Eid follow the Umm al-Qura calendar built into Fire OS. If your
     * mosque starts a day later (or earlier), set this to 1 (or -1).
     */
    static final int HIJRI_ADJUST_DAYS = 0;

    /** Try a theme early: e.g. "2026-12-25" for Christmas. null = use today's date. */
    static final String PREVIEW_DATE = null;

    /**
     * Try a weather animation instead of the real forecast. -1 = real weather.
     *   0 clear    2 partly cloudy    3 cloudy    45 fog
     *   53 drizzle    63 rain    73 snow    95 thunderstorm
     */
    static final int PREVIEW_WEATHER = -1;

    /**
     * Your wheel, top to bottom. Apps are matched on the Fire Stick by their
     * name first ("aliases": lowercase, letters and digits only, + becomes "plus"),
     * then by package name as a fallback.
     */
    static final Brand[] PINNED = {
        new Brand("YOUTUBE TV", new int[]{0xFFFF2A2A},
                new String[]{"youtubetv"},
                new String[]{"com.google.android.youtube.tvunplugged"})
                // YouTube TV's red TV icon on white, shrunk the same way as the other icons.
                .pixels(
                        0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF,
                        0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF,
                        0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF,
                        0xFFFFFF, 0xFFFEFE, 0xFF9BAC, 0xFF7991, 0xFF7991, 0xFF7991, 0xFF7991, 0xFF7991, 0xFF7991, 0xFF7991, 0xFF7991, 0xFF9BAC, 0xFFFEFE, 0xFFFFFF,
                        0xFFFFFF, 0xFFFEFE, 0xFF3157, 0xFF0033, 0xFF0033, 0xFF0033, 0xFF0032, 0xFF0033, 0xFF0033, 0xFF0033, 0xFF0033, 0xFF3157, 0xFFFEFE, 0xFFFFFF,
                        0xFFFFFF, 0xFFFEFE, 0xFF3157, 0xFF0033, 0xFF0033, 0xFF0031, 0xFFA0B1, 0xFF204B, 0xFF0033, 0xFF0033, 0xFF0033, 0xFF3157, 0xFFFEFE, 0xFFFFFF,
                        0xFFFFFF, 0xFFFEFE, 0xFF3157, 0xFF0033, 0xFF0033, 0xFF0031, 0xFFF3F5, 0xFFEEF1, 0xFF2B53, 0xFF0033, 0xFF0033, 0xFF3157, 0xFFFEFE, 0xFFFFFF,
                        0xFFFFFF, 0xFFFEFE, 0xFF3157, 0xFF0033, 0xFF0033, 0xFF0031, 0xFFA4B4, 0xFF234D, 0xFF0033, 0xFF0033, 0xFF0033, 0xFF3157, 0xFFFEFE, 0xFFFFFF,
                        0xFFFFFF, 0xFFFEFE, 0xFF3157, 0xFF0033, 0xFF0033, 0xFF0033, 0xFF0032, 0xFF0033, 0xFF0033, 0xFF0033, 0xFF0033, 0xFF3157, 0xFFFEFE, 0xFFFFFF,
                        0xFFFFFF, 0xFFFEFE, 0xFFA2B2, 0xFF7B93, 0xFF627E, 0xFF627E, 0xFF627E, 0xFF627E, 0xFF627E, 0xFF627E, 0xFF7B93, 0xFFA2B2, 0xFFFEFE, 0xFFFFFF,
                        0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFE1E5, 0xFF738C, 0xFF728C, 0xFF728C, 0xFF728C, 0xFF728C, 0xFF738C, 0xFFE1E5, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF,
                        0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF,
                        0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF,
                        0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF),
        new Brand("APPLE TV", new int[]{0xFFE8E8ED},
                new String[]{"appletv", "appletvplus"},
                new String[]{"com.apple.atve.amazon.appletv"}),
        new Brand("DISNEY+", new int[]{0xFF2F7BFF},
                new String[]{"disneyplus", "disney"},
                new String[]{"com.disney.disneyplus"}),
        new Brand("NETFLIX", new int[]{0xFFE50914},
                new String[]{"netflix"},
                new String[]{"com.netflix.ninja"})
                // The Netflix N, shrunk like the other icons, on a full dark square tile
                // (the real icon's black tile would vanish into the background).
                .pixels(
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0xAD1C1F, 0xE71C1F, 0x631C1F, 0x231C1F, 0xB11C1F, 0x941C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x961C1F, 0xE61C1F, 0xB41C1F, 0x231C1F, 0xB01C1F, 0x931C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x8B1C1F, 0xDA1C1F, 0xE51C1F, 0x431C1F, 0xB01C1F, 0x931C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x8F1C1F, 0xC11C1F, 0xE61C1F, 0x901C1F, 0xAF1C1F, 0x931C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x8F1C1F, 0xAB1C1F, 0xE61C1F, 0xD31C1F, 0xA71C1F, 0x931C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x8F1C1F, 0xA81C1F, 0xD11C1F, 0xE61C1F, 0xAC1C1F, 0x931C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x8F1C1F, 0xAF1C1F, 0x8F1C1F, 0xE61C1F, 0xC21C1F, 0x931C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x8F1C1F, 0xB01C1F, 0x421C1F, 0xE51C1F, 0xDC1C1F, 0x8F1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x8F1C1F, 0xB01C1F, 0x261C1F, 0xB01C1F, 0xE61C1F, 0x9B1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x8F1C1F, 0xAE1C1F, 0x241C1F, 0x5D1C1F, 0xE21C1F, 0xB21C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F,
                        0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F, 0x1C1C1F),
        new Brand("HULU", new int[]{0xFF1CE783},
                new String[]{"hulu"},
                new String[]{"com.hulu.plus"}).sharp(),
        new Brand("PRIME VIDEO", new int[]{0xFF00A8E1},
                new String[]{"primevideo", "amazonprimevideo"},
                new String[]{}),
        // Peacock gets its six feather colors, one per letter.
        new Brand("PEACOCK", new int[]{0xFFFCCC12, 0xFFF37121, 0xFFEB1F33, 0xFF6E55DC, 0xFF069DE0, 0xFF05AC3F},
                new String[]{"peacock", "peacocktv"},
                new String[]{"com.peacocktv.peacockandroid"}),
        // HBO Max: its own icon turns to mush at dot size, so it uses the HBO logo on purple.
        new Brand("HBO MAX", new int[]{0xFFF4F4F6},
                new String[]{"hbomax", "max"},
                new String[]{"com.wbd.stream", "com.hbo.hbonow"})
                // The HBO logo shrunk the same way as the other icons, on HBO Max's purple.
                .pixels(
                        0x230338, 0x230338, 0x240339, 0x24033A, 0x25033A, 0x26033B, 0x26033C, 0x27033C, 0x27033D, 0x28033E, 0x29033E, 0x29033F, 0x2A0340, 0x2B0441,
                        0x24033C, 0x25033C, 0x25033D, 0x26033D, 0x27033E, 0x28033F, 0x28033F, 0x290340, 0x2A0340, 0x2A0341, 0x2B0341, 0x2C0342, 0x2C0343, 0x2D0343,
                        0x260340, 0x260340, 0x270341, 0x280341, 0x290342, 0x290342, 0x2A0343, 0x2B0343, 0x2C0343, 0x2D0344, 0x2D0344, 0x2E0345, 0x2F0345, 0x300346,
                        0x270444, 0x280444, 0x290345, 0x2A0345, 0x2B0345, 0x2B0346, 0x2C0346, 0x2D0346, 0x2E0347, 0x2F0347, 0x300347, 0x300348, 0x310348, 0x320348,
                        0x290448, 0x494A4E, 0x6B6B6E, 0x444549, 0x707173, 0x919396, 0x94969A, 0x707479, 0x30034A, 0x77787B, 0x9E9E9F, 0x4C4C50, 0x34024B, 0x35024B,
                        0x2A044C, 0x828183, 0xBCBCBD, 0x77797A, 0xC5C4C6, 0xFCFCFC, 0x92969C, 0xF5F5F5, 0xD0D1D2, 0xDDDDDD, 0xBEBEBF, 0xF2F2F2, 0x4A4A4E, 0x37024E,
                        0x2C0550, 0x828183, 0xF7F7F7, 0xF0F0F0, 0xC5C5C6, 0xFCFCFC, 0xD8DADB, 0xEBEBEB, 0xF7F7F7, 0xBEBFBF, 0xFEFEFE, 0xBEBEBE, 0x99989A, 0x3A0250,
                        0x2D0555, 0x828183, 0xC9C9C8, 0x929294, 0xC5C5C6, 0xFCFCFC, 0x6E747A, 0xF5F5F5, 0xF4F5F5, 0xC8C8C9, 0xBDBDBE, 0xDFDFDF, 0x6D6E70, 0x3C0153,
                        0x2F0659, 0x79797B, 0xB0B0B1, 0x707174, 0xB8B8BA, 0xEBEBEC, 0xEDEDED, 0xC4C5C7, 0x40464D, 0xC6C6C7, 0xEBEBEB, 0x969698, 0x3E0156, 0x3F0155,
                        0x30065D, 0x32065D, 0x33055C, 0x34055C, 0x36045B, 0x37045B, 0x38045B, 0x3A035A, 0x3B035A, 0x3C025A, 0x3D0259, 0x3F0259, 0x400158, 0x410158,
                        0x320661, 0x330661, 0x350560, 0x360560, 0x37055F, 0x39045F, 0x3A045E, 0x3C035E, 0x3D035D, 0x3E025D, 0x40025C, 0x41015C, 0x43015B, 0x44005B,
                        0x330765, 0x350665, 0x360664, 0x380563, 0x390563, 0x3B0462, 0x3C0462, 0x3E0361, 0x3F0360, 0x410260, 0x42025F, 0x44015E, 0x45015E, 0x46005D,
                        0x350769, 0x370769, 0x380668, 0x3A0567, 0x3B0566, 0x3D0466, 0x3E0465, 0x400364, 0x410364, 0x430263, 0x440162, 0x460161, 0x470061, 0x490060,
                        0x37086E, 0x38076D, 0x3A066C, 0x3B066B, 0x3D056A, 0x3F0469, 0x400468, 0x420368, 0x430367, 0x450266, 0x470165, 0x480164, 0x4A0063, 0x4C0063),
    };

    static final class Brand {
        final String display;
        final int[] colors;
        final String[] aliases;
        final String[] packages;

        /** A hand-drawn 14 x 14 dot logo instead of the app's own icon (null = use the icon). */
        int[] icon;
        /** Shrink the app's own icon with crisper edges (to match the hand-picked logos). */
        boolean sharp;

        Brand sharp() {
            sharp = true;
            return this;
        }

        /** The pinned brand for an app (by name, then package), or null. */
        static Brand find(String normLabel, String pkg) {
            for (Brand b : PINNED) {
                for (String a : b.aliases) if (a.equals(normLabel)) return b;
            }
            for (Brand b : PINNED) {
                for (String p : b.packages) if (p.equals(pkg)) return b;
            }
            return null;
        }

        Brand(String display, int[] colors, String[] aliases, String[] packages) {
            this.display = display;
            this.colors = colors;
            this.aliases = aliases;
            this.packages = packages;
        }

        /**
         * '#' = fg, '+' = bg, '.' = empty. The letters are softened into the tile a little
         * (each dot blends with its neighbors) so the logo matches the other icons, which are
         * shrunk from real artwork and have soft edges.
         */
        Brand logo(int fg, int bg, String... rows) {
            int g = AppRepo.ICON_GRID;
            float[] cover = new float[g * g];
            boolean[] tile = new boolean[g * g];
            for (int r = 0; r < rows.length && r < g; r++) {
                for (int c = 0; c < rows[r].length() && c < g; c++) {
                    char ch = rows[r].charAt(c);
                    cover[r * g + c] = ch == '#' ? 1f : 0f;
                    tile[r * g + c] = ch != '.';
                }
            }
            icon = new int[g * g];
            for (int r = 0; r < g; r++) {
                for (int c = 0; c < g; c++) {
                    if (!tile[r * g + c]) continue;
                    float sum = 0;
                    int n = 0;
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            if (dr == 0 && dc == 0) continue;
                            int rr = r + dr, cc = c + dc;
                            if (rr < 0 || rr >= g || cc < 0 || cc >= g || !tile[rr * g + cc]) continue;
                            sum += cover[rr * g + cc];
                            n++;
                        }
                    }
                    float k = 0.72f * cover[r * g + c] + 0.28f * (n == 0 ? 0 : sum / n);
                    icon[r * g + c] = mix(bg, fg, k);
                }
            }
            return this;
        }

        /** A ready-made 14 x 14 icon, as 0xRRGGBB values (row by row). */
        Brand pixels(int... rgb) {
            icon = new int[rgb.length];
            for (int i = 0; i < rgb.length; i++) {
                int v = rgb[i] & 0xFFFFFF;
                int mx = Math.max((v >> 16) & 0xFF, Math.max((v >> 8) & 0xFF, v & 0xFF));
                icon[i] = mx < 22 ? 0 : 0xFF000000 | v;   // near-black is skipped, like sampled icons
            }
            return this;
        }

        private static int mix(int a, int b, float k) {
            int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
            int gg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
            int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
            return 0xFF000000 | (r << 16) | (gg << 8) | bl;
        }
    }

    private Config() {}
}
