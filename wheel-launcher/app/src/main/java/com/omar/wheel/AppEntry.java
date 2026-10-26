package com.omar.wheel;

/** One app on the wheel. */
final class AppEntry {
    String pkg;
    String label;      // the app's real name on the Fire Stick
    String display;    // what the wheel shows, in dot-matrix capitals
    int[] colors;      // brand color(s); several = one per letter
    int[] icon;        // ICON_GRID x ICON_GRID colors sampled from the app icon, or null
    boolean missing;   // one of your pinned apps that isn't installed
    boolean hidden;
    String action;     // non-null for built-in shortcuts (Fire TV Settings, Amazon Home)
}
