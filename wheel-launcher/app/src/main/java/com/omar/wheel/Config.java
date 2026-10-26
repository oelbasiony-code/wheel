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
    };

    static final class Brand {
        final String display;
        final int[] colors;
        final String[] aliases;
        final String[] packages;

        Brand(String display, int[] colors, String[] aliases, String[] packages) {
            this.display = display;
            this.colors = colors;
            this.aliases = aliases;
            this.packages = packages;
        }
    }

    private Config() {}
}
