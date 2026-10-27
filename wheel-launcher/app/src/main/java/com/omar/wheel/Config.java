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
                new String[]{"com.google.android.youtube.tvunplugged"}),
        new Brand("APPLE TV", new int[]{0xFFE8E8ED},
                new String[]{"appletv", "appletvplus"},
                new String[]{"com.apple.atve.amazon.appletv"}),
        new Brand("DISNEY+", new int[]{0xFF2F7BFF},
                new String[]{"disneyplus", "disney"},
                new String[]{"com.disney.disneyplus"}),
        new Brand("NETFLIX", new int[]{0xFFE50914},
                new String[]{"netflix"},
                new String[]{"com.netflix.ninja"}),
        new Brand("HULU", new int[]{0xFF1CE783},
                new String[]{"hulu"},
                new String[]{"com.hulu.plus"}),
        new Brand("PRIME VIDEO", new int[]{0xFF00A8E1},
                new String[]{"primevideo", "amazonprimevideo"},
                new String[]{}),
        // Peacock gets its six feather colors, one per letter.
        new Brand("PEACOCK", new int[]{0xFFFCCC12, 0xFFF37121, 0xFFEB1F33, 0xFF6E55DC, 0xFF069DE0, 0xFF05AC3F},
                new String[]{"peacock", "peacocktv"},
                new String[]{"com.peacocktv.peacockandroid"}),
        // HBO Max: its real icon is tiny white lettering that turns to mush at dot size,
        // so it gets a hand-drawn logo (monochrome, like the 2025 brand).
        new Brand("HBO MAX", new int[]{0xFFF4F4F6},
                new String[]{"hbomax", "max"},
                new String[]{"com.wbd.stream", "com.hbo.hbonow"})
                .logo(0xFFF4F4F6, 0xFF2A2D35,
                        ".+++++++++++..",
                        "+#+#+##++###+.",
                        "+#+#+#+#+#+#+.",
                        "+###+##++#+#+.",
                        "+#+#+#+#+#+#+.",
                        "+#+#+##++###+.",
                        "+++++++++++++.",
                        "+++++++++++++.",
                        "+#+#++#++#+#+.",
                        "+###+#+#+#+#+.",
                        "+#+#+###++#++.",
                        "+#+#+#+#+#+#+.",
                        "+#+#+#+#+#+#+.",
                        ".+++++++++++.."),
    };

    static final class Brand {
        final String display;
        final int[] colors;
        final String[] aliases;
        final String[] packages;

        /** A hand-drawn 14 x 14 dot logo instead of the app's own icon (null = use the icon). */
        int[] icon;

        Brand(String display, int[] colors, String[] aliases, String[] packages) {
            this.display = display;
            this.colors = colors;
            this.aliases = aliases;
            this.packages = packages;
        }

        /** '#' = fg, '+' = bg, '.' = empty. */
        Brand logo(int fg, int bg, String... rows) {
            int g = AppRepo.ICON_GRID;
            icon = new int[g * g];
            for (int r = 0; r < rows.length && r < g; r++) {
                for (int c = 0; c < rows[r].length() && c < g; c++) {
                    char ch = rows[r].charAt(c);
                    icon[r * g + c] = ch == '#' ? fg : ch == '+' ? bg : 0;
                }
            }
            return this;
        }
    }

    private Config() {}
}
