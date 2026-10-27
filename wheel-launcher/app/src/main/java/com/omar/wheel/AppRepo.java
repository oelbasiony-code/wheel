package com.omar.wheel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Finds installed apps, matches your pinned ones, and remembers order and hidden apps. */
final class AppRepo {

    static final int ICON_GRID = 14;

    private static final String PREFS = "wheel";
    private static final String K_ORDER = "order";
    private static final String K_HIDDEN = "hidden";
    private static final String K_INIT = "init";

    private final Context ctx;
    private final SharedPreferences sp;
    private final Map<String, AppEntry> installed = new HashMap<>();
    private final List<String> order = new ArrayList<>();
    private final Set<String> hidden = new HashSet<>();
    private final List<AppEntry> missing = new ArrayList<>();
    private boolean loaded;

    /** Result of a background scan; applied on the UI thread. */
    static final class Snapshot {
        final LinkedHashMap<String, AppEntry> apps = new LinkedHashMap<>();
        final List<String> pinned = new ArrayList<>();
        final List<AppEntry> missing = new ArrayList<>();
    }

    AppRepo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.sp = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isLoaded() {
        return loaded;
    }

    /** Runs off the UI thread: lists launchable apps and samples their icons. */
    Snapshot scan() {
        PackageManager pm = ctx.getPackageManager();
        LinkedHashMap<String, ResolveInfo> found = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, found);
        collect(pm, Intent.CATEGORY_LAUNCHER, found);
        found.remove(ctx.getPackageName());

        Snapshot s = new Snapshot();
        Map<String, String> byName = new HashMap<>();
        for (ResolveInfo ri : found.values()) {
            String pkg = ri.activityInfo.packageName;
            CharSequence l = null;
            try {
                l = ri.loadLabel(pm);
            } catch (Exception ignored) {
            }
            AppEntry e = new AppEntry();
            e.pkg = pkg;
            e.label = l == null ? pkg : l.toString();
            try {
                Drawable d = ri.activityInfo.loadIcon(pm);
                Config.Brand br = Config.Brand.find(norm(e.label), pkg);
                if (d != null) {
                    e.icon = IconDots.sample(d, ICON_GRID, br != null && br.sharp);
                    if (br != null && br.solidBackground) IconDots.solidBackground(e.icon, ICON_GRID, br.colors[0]);
                    // Keep a copy of the real icon so it can be pulled off the stick with adb:
                    //   adb pull /sdcard/Android/data/com.omar.wheel/files/icons
                    IconDots.export(d, ctx.getExternalFilesDir("icons"), pkg);
                }
            } catch (Exception ignored) {
            }
            s.apps.put(pkg, e);
            byName.put(norm(e.label), pkg);
        }

        for (Config.Brand b : Config.PINNED) {
            String pkg = null;
            for (String alias : b.aliases) {
                pkg = byName.get(alias);
                if (pkg != null) break;
            }
            if (pkg == null) {
                for (String p : b.packages) {
                    if (s.apps.containsKey(p)) {
                        pkg = p;
                        break;
                    }
                }
            }
            if (pkg == null) {
                AppEntry m = new AppEntry();
                m.pkg = "missing:" + b.display;
                m.label = b.display;
                m.display = b.display;
                m.colors = b.colors;
                m.missing = true;
                s.missing.add(m);
            } else if (!s.pinned.contains(pkg)) {
                AppEntry e = s.apps.get(pkg);
                e.display = b.display;
                e.colors = b.colors;
                if (b.icon != null) e.icon = b.icon;
                s.pinned.add(pkg);
            }
        }

        for (AppEntry e : s.apps.values()) {
            if (e.display != null) continue;
            String d = DotFont.clean(e.label);
            if (d.isEmpty()) d = DotFont.clean(e.pkg.substring(e.pkg.lastIndexOf('.') + 1));
            if (d.length() > 16) d = d.substring(0, 16).trim();
            e.display = d;
            e.colors = new int[]{IconDots.accent(e.icon)};
        }
        return s;
    }

    /** UI thread: merges a scan with the saved order and hidden list. */
    void apply(final Snapshot s) {
        installed.clear();
        installed.putAll(s.apps);
        missing.clear();
        missing.addAll(s.missing);
        order.clear();
        hidden.clear();

        List<String> rest = new ArrayList<>();
        for (String p : s.apps.keySet()) {
            if (!s.pinned.contains(p)) rest.add(p);
        }
        Collections.sort(rest, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return s.apps.get(a).display.compareToIgnoreCase(s.apps.get(b).display);
            }
        });

        if (!sp.getBoolean(K_INIT, false)) {
            // First run: your apps on the wheel, everything else tucked away.
            order.addAll(s.pinned);
            order.addAll(rest);
            hidden.addAll(rest);
        } else {
            String saved = sp.getString(K_ORDER, "");
            for (String p : saved.split("\n")) {
                if (!p.isEmpty() && s.apps.containsKey(p) && !order.contains(p)) order.add(p);
            }
            for (String p : s.pinned) if (!order.contains(p)) order.add(p);
            for (String p : rest) if (!order.contains(p)) order.add(p);
            Set<String> hs = sp.getStringSet(K_HIDDEN, Collections.<String>emptySet());
            for (String p : hs) if (s.apps.containsKey(p)) hidden.add(p);
        }
        loaded = true;
        save();
    }

    static final String ACTION_SETTINGS = "settings";
    static final String ACTION_AMAZON_HOME = "amazon-home";

    private static AppEntry shortcut(String action, String display) {
        AppEntry e = new AppEntry();
        e.pkg = "action:" + action;
        e.label = display;
        e.display = display;
        e.colors = new int[]{0xFFB8B2A8};
        e.action = action;
        return e;
    }

    private final AppEntry settingsShortcut = shortcut(ACTION_SETTINGS, "FIRE TV SETTINGS");
    private final AppEntry amazonShortcut = shortcut(ACTION_AMAZON_HOME, "AMAZON HOME");

    /** The wheel's contents. all = include hidden apps and the built-in shortcuts. */
    List<AppEntry> visible(boolean all) {
        List<AppEntry> out = new ArrayList<>();
        if (all) {
            out.add(settingsShortcut);
            out.add(amazonShortcut);
        }
        for (String p : order) {
            AppEntry e = installed.get(p);
            if (e == null) continue;
            e.hidden = hidden.contains(p);
            if (all || !e.hidden) out.add(e);
        }
        out.addAll(missing);
        return out;
    }

    void setHidden(String pkg, boolean h) {
        if (h) hidden.add(pkg);
        else hidden.remove(pkg);
        save();
    }

    void swap(String a, String b) {
        int i = order.indexOf(a), j = order.indexOf(b);
        if (i < 0 || j < 0) return;
        Collections.swap(order, i, j);
        save();
    }

    void save() {
        sp.edit()
                .putString(K_ORDER, TextUtils.join("\n", order))
                .putStringSet(K_HIDDEN, new HashSet<>(hidden))
                .putBoolean(K_INIT, true)
                .apply();
    }

    private static void collect(PackageManager pm, String category, Map<String, ResolveInfo> out) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> list;
        try {
            list = pm.queryIntentActivities(i, 0);
        } catch (Exception e) {
            return;
        }
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            String p = ri.activityInfo.packageName;
            if (!out.containsKey(p)) out.put(p, ri);
        }
    }

    /** "Disney+" -> "disneyplus", "YouTube TV" -> "youtubetv". */
    static String norm(String s) {
        String t = s.toLowerCase(Locale.US).replace("+", "plus");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) b.append(c);
        }
        return b.toString();
    }
}
