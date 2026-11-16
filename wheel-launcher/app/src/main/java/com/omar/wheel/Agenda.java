package com.omar.wheel;

import android.content.Context;
import android.os.Handler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Your Google Calendar (or any calendar's iCal link), shown on the panel.
 *
 * The link is private, so it is NOT in the code (the repo is public). It's saved in
 * the app's own private storage on the Fire Stick, sent once with adb:
 *   adb shell "am start -n com.omar.wheel/.MainActivity --es calendar_url 'LINK'"
 * Several calendars: separate the links with spaces; they're merged.
 */
final class Agenda {

    static final class Event {
        long start, end;
        boolean allDay;
        String title;
    }

    interface Callback {
        /** events = null if nothing could be loaded (no link saved, or every fetch failed). */
        void onAgenda(List<Event> events);
    }

    /** How far around "now" occurrences are collected. */
    private static final long DAY = 24 * 60 * 60 * 1000L;

    private static final String PREFS = "wheel", KEY_LINKS = "calendar_urls";

    static void fetch(final Context ctx, final Handler ui, final Callback cb) {
        final List<String> links = links(ctx);
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Event> out = load(links);
                final List<Event> res = out;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onAgenda(res);
                    }
                });
            }
        }, "wheel-agenda").start();
    }

    private static List<Event> load(List<String> links) {
        if (links.isEmpty()) return null;
        long now = System.currentTimeMillis();
        long from = now - DAY, to = now + 3 * DAY;
        List<Event> all = new ArrayList<>();
        boolean any = false;
        for (String link : links) {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(link).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(20000);
                if (con.getResponseCode() != 200) continue;
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
                parse(in, from, to, all);
                in.close();
                any = true;
            } catch (Exception ignored) {
            } finally {
                if (con != null) con.disconnect();
            }
        }
        if (!any) return null;
        Collections.sort(all, new Comparator<Event>() {
            @Override
            public int compare(Event a, Event b) {
                return a.start < b.start ? -1 : a.start > b.start ? 1 : 0;
            }
        });
        return all;
    }

    /** The saved calendar links (webcal:// turned into https://). */
    static List<String> links(Context ctx) {
        List<String> out = new ArrayList<>();
        String saved = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LINKS, "");
        for (String link : saved.trim().split("\\s+")) {
            if (link.startsWith("webcal://")) link = "https://" + link.substring(9);
            if (link.startsWith("http")) out.add(link);
        }
        return out;
    }

    // ---- What the panel shows -------------------------------------------------------

    /**
     * Up to max of today's events as {label, title}: what's on now or coming up
     * (by time), then all-day things. Nothing from tomorrow.
     */
    static List<String[]> lines(List<Event> events, long now, TimeZone tz, int max) {
        List<String[]> out = new ArrayList<>();
        if (events == null) return out;
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, 1);
        long tomorrow = c.getTimeInMillis();

        List<Event> allDay = new ArrayList<>();
        for (Event e : events) {
            if (e.end <= now || e.start >= tomorrow) continue;
            if (e.allDay) {
                allDay.add(e);
            } else if (out.size() < max) {
                out.add(new String[]{e.start <= now ? "NOW" : clock(e.start, tz), e.title});
            }
        }
        for (Event e : allDay) {
            if (out.size() >= max) break;
            out.add(new String[]{"TODAY", e.title});
        }
        if (out.isEmpty()) {
            // Nothing left today: the next thing coming up, so the card isn't blank at night.
            for (Event e : events) {
                if (e.start < tomorrow) continue;
                boolean isTomorrow = e.start < tomorrow + 24 * 3600000L;
                Calendar d = Calendar.getInstance(tz);
                d.setTimeInMillis(e.start);
                String day = isTomorrow ? "TOMORROW"
                        : new String[]{"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"}[d.get(Calendar.DAY_OF_WEEK) - 1];
                out.add(new String[]{e.allDay ? day : day + " " + clock(e.start, tz), e.title});
                break;
            }
        }
        return out;
    }

    /** Saves the private calendar link(s), separated by spaces, in the app's private storage. */
    static boolean saveLinks(Context ctx, String links) {
        if (links == null) return false;
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LINKS, links.trim()).commit();
    }

    /** True if a calendar link has been set up on this stick. */
    static boolean hasLink(Context ctx) {
        return !links(ctx).isEmpty();
    }

    /** 9A, 9:30A, 12P. */
    static String clock(long t, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(t);
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        String ap = h < 12 ? "A" : "P";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + (m == 0 ? "" : ":" + (m < 10 ? "0" : "") + m) + ap;
    }

    // ---- iCal parsing -----------------------------------------------------------------

    private static final class Raw {
        String uid, summary, rrule, status;
        String dtstart, dtstartTz, dtend, dtendTz, duration, recurId, recurIdTz;
        boolean startIsDate, endIsDate, recurIsDate;
        List<Long> exdates = new ArrayList<>();
    }

    /** Reads VEVENTs and adds every occurrence that overlaps [from, to]. */
    static void parse(BufferedReader in, long from, long to, List<Event> out) throws Exception {
        List<Raw> raws = new ArrayList<>();
        Raw cur = null;
        int nested = 0;   // inside VALARM etc.
        String pending = null;
        String line;
        while (true) {
            line = in.readLine();
            // Unfold: a line starting with a space or tab continues the previous one.
            if (line != null && line.length() > 0 && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                if (pending != null) pending += line.substring(1);
                continue;
            }
            if (pending != null) {
                String p = pending;
                if (p.equals("BEGIN:VEVENT")) {
                    cur = new Raw();
                    nested = 0;
                } else if (p.equals("END:VEVENT")) {
                    if (cur != null) raws.add(cur);
                    cur = null;
                } else if (cur != null && p.startsWith("BEGIN:")) {
                    nested++;
                } else if (cur != null && p.startsWith("END:")) {
                    nested = Math.max(0, nested - 1);
                } else if (cur != null && nested == 0) {
                    prop(cur, p);
                }
            }
            if (line == null) break;
            pending = line;
        }

        // Moved or edited single occurrences of a repeating event.
        Map<String, Set<Long>> overridden = new HashMap<>();
        for (Raw r : raws) {
            if (r.recurId == null || r.uid == null) continue;
            Long t = time(r.recurId, r.recurIdTz, r.recurIsDate);
            if (t == null) continue;
            Set<Long> s = overridden.get(r.uid);
            if (s == null) overridden.put(r.uid, s = new HashSet<>());
            s.add(t);
        }

        for (Raw r : raws) {
            if ("CANCELLED".equalsIgnoreCase(r.status)) continue;
            Long start = time(r.dtstart, r.dtstartTz, r.startIsDate);
            if (start == null) continue;
            long len;
            Long end = r.dtend == null ? null : time(r.dtend, r.dtendTz, r.endIsDate);
            if (end != null) len = Math.max(0, end - start);
            else if (r.duration != null) len = duration(r.duration);
            else len = r.startIsDate ? DAY : 0;
            String title = unescape(r.summary == null ? "BUSY" : r.summary);

            if (r.rrule == null || r.recurId != null) {
                add(out, start, len, r.startIsDate, title, from, to);
                continue;
            }
            Set<Long> skip = new HashSet<>(r.exdates);
            Set<Long> ov = r.uid == null ? null : overridden.get(r.uid);
            if (ov != null) skip.addAll(ov);
            TimeZone tz = r.startIsDate || r.dtstartTz == null
                    ? (r.dtstart.endsWith("Z") ? TimeZone.getTimeZone("UTC") : TimeZone.getDefault())
                    : TimeZone.getTimeZone(r.dtstartTz);
            expand(r.rrule, start, tz, from - len, to, skip, len, r.startIsDate, title, from, out);
        }
    }

    private static void add(List<Event> out, long start, long len, boolean allDay, String title,
                            long from, long to) {
        long end = start + len;
        if (end < from || start > to) return;
        Event e = new Event();
        e.start = start;
        e.end = len == 0 ? start + 30 * 60 * 1000L : end;   // a moment-long event shows for 30 min
        e.allDay = allDay;
        e.title = title;
        out.add(e);
    }

    private static void prop(Raw r, String p) {
        int colon = -1;
        boolean q = false;
        for (int i = 0; i < p.length(); i++) {
            char ch = p.charAt(i);
            if (ch == '"') q = !q;
            else if (ch == ':' && !q) {
                colon = i;
                break;
            }
        }
        if (colon < 0) return;
        String head = p.substring(0, colon), value = p.substring(colon + 1);
        String[] parts = head.split(";");
        String name = parts[0].toUpperCase(Locale.US);
        String tzid = null;
        boolean isDate = false;
        for (int i = 1; i < parts.length; i++) {
            String a = parts[i];
            if (a.toUpperCase(Locale.US).startsWith("TZID=")) tzid = a.substring(5).replace("\"", "");
            if (a.equalsIgnoreCase("VALUE=DATE")) isDate = true;
        }
        if (value.length() == 8) isDate = true;
        switch (name) {
            case "UID": r.uid = value; break;
            case "SUMMARY": r.summary = value; break;
            case "RRULE": r.rrule = value; break;
            case "STATUS": r.status = value; break;
            case "DURATION": r.duration = value; break;
            case "DTSTART": r.dtstart = value; r.dtstartTz = tzid; r.startIsDate = isDate; break;
            case "DTEND": r.dtend = value; r.dtendTz = tzid; r.endIsDate = isDate; break;
            case "RECURRENCE-ID": r.recurId = value; r.recurIdTz = tzid; r.recurIsDate = isDate; break;
            case "EXDATE":
                for (String v : value.split(",")) {
                    Long t = time(v.trim(), tzid, isDate || v.trim().length() == 8);
                    if (t != null) r.exdates.add(t);
                }
                break;
            default:
                break;
        }
    }

    /** 20261116, 20261116T090000, 20261116T140000Z. */
    static Long time(String v, String tzid, boolean isDate) {
        if (v == null) return null;
        try {
            v = v.trim();
            int y = Integer.parseInt(v.substring(0, 4));
            int mo = Integer.parseInt(v.substring(4, 6));
            int d = Integer.parseInt(v.substring(6, 8));
            int h = 0, mi = 0, s = 0;
            TimeZone tz = TimeZone.getDefault();
            if (!isDate && v.length() >= 15) {
                h = Integer.parseInt(v.substring(9, 11));
                mi = Integer.parseInt(v.substring(11, 13));
                s = Integer.parseInt(v.substring(13, 15));
                if (v.endsWith("Z")) tz = TimeZone.getTimeZone("UTC");
                else if (tzid != null) tz = TimeZone.getTimeZone(tzid);
            }
            Calendar c = Calendar.getInstance(tz);
            c.clear();
            c.set(y, mo - 1, d, h, mi, s);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return null;
        }
    }

    /** PT1H30M, P1D, P1W. */
    static long duration(String v) {
        long t = 0, n = 0;
        boolean neg = v.startsWith("-");
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch >= '0' && ch <= '9') {
                n = n * 10 + (ch - '0');
                continue;
            }
            switch (ch) {
                case 'W': t += n * 7 * DAY; break;
                case 'D': t += n * DAY; break;
                case 'H': t += n * 3600000L; break;
                case 'M': t += n * 60000L; break;
                case 'S': t += n * 1000L; break;
                default: break;
            }
            n = 0;
        }
        return neg ? 0 : t;
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char nx = s.charAt(++i);
                b.append(nx == 'n' || nx == 'N' ? ' ' : nx);
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    // ---- Repeating events (the common RRULE patterns Google writes) -------------------

    private static final String[] DAYS = {"SU", "MO", "TU", "WE", "TH", "FR", "SA"};

    private static void expand(String rrule, long start, TimeZone tz, long winFrom, long winTo,
                               Set<Long> skip, long len, boolean allDay, String title,
                               long from, List<Event> out) {
        String freq = null;
        int interval = 1, count = -1;
        Long until = null;
        String byDay = null, byMonthDay = null;
        int wkst = Calendar.MONDAY;
        for (String part : rrule.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String k = part.substring(0, eq).toUpperCase(Locale.US), v = part.substring(eq + 1);
            switch (k) {
                case "FREQ": freq = v.toUpperCase(Locale.US); break;
                case "INTERVAL": interval = Math.max(1, parseInt(v, 1)); break;
                case "COUNT": count = parseInt(v, -1); break;
                case "UNTIL": until = time(v, null, v.length() == 8); break;
                case "BYDAY": byDay = v.toUpperCase(Locale.US); break;
                case "BYMONTHDAY": byMonthDay = v; break;
                case "WKST": wkst = dayIndex(v) + 1; break;
                default: break;
            }
        }
        if (freq == null) return;
        if (until != null && allDay) until += DAY - 1;   // an all-day UNTIL includes that day

        Calendar base = Calendar.getInstance(tz);
        base.setTimeInMillis(start);
        int hh = base.get(Calendar.HOUR_OF_DAY), mm = base.get(Calendar.MINUTE), ss = base.get(Calendar.SECOND);

        int seen = 0;
        for (int p = 0; p < 40000; p++) {
            List<Long> cands = new ArrayList<>();
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(start);
            switch (freq) {
                case "DAILY":
                    c.add(Calendar.DAY_OF_MONTH, p * interval);
                    cands.add(at(c, hh, mm, ss));
                    break;
                case "WEEKLY": {
                    // Back to the start of the week (WKST), then forward p*interval weeks.
                    int back = (c.get(Calendar.DAY_OF_WEEK) - wkst + 7) % 7;
                    c.add(Calendar.DAY_OF_MONTH, -back + 7 * p * interval);
                    if (byDay == null) {
                        Calendar d = (Calendar) c.clone();
                        d.add(Calendar.DAY_OF_MONTH, back);
                        cands.add(at(d, hh, mm, ss));
                    } else {
                        for (int k = 0; k < 7; k++) {
                            Calendar d = (Calendar) c.clone();
                            d.add(Calendar.DAY_OF_MONTH, k);
                            String dn = DAYS[d.get(Calendar.DAY_OF_WEEK) - 1];
                            if (byDay.contains(dn)) cands.add(at(d, hh, mm, ss));
                        }
                    }
                    break;
                }
                case "MONTHLY": {
                    c.set(Calendar.DAY_OF_MONTH, 1);
                    c.add(Calendar.MONTH, p * interval);
                    int maxD = c.getActualMaximum(Calendar.DAY_OF_MONTH);
                    if (byDay != null) {
                        for (String bd : byDay.split(",")) {
                            Integer day = nthWeekday(c, bd.trim());
                            if (day != null) cands.add(dayOf(c, day, hh, mm, ss));
                        }
                    } else if (byMonthDay != null) {
                        for (String md : byMonthDay.split(",")) {
                            int n = parseInt(md.trim(), 0);
                            int day = n < 0 ? maxD + 1 + n : n;
                            if (day >= 1 && day <= maxD) cands.add(dayOf(c, day, hh, mm, ss));
                        }
                    } else {
                        int day = base.get(Calendar.DAY_OF_MONTH);
                        if (day <= maxD) cands.add(dayOf(c, day, hh, mm, ss));
                    }
                    break;
                }
                case "YEARLY": {
                    c.set(Calendar.DAY_OF_MONTH, 1);
                    c.add(Calendar.YEAR, p * interval);
                    int day = base.get(Calendar.DAY_OF_MONTH);
                    if (day <= c.getActualMaximum(Calendar.DAY_OF_MONTH)) cands.add(dayOf(c, day, hh, mm, ss));
                    break;
                }
                default:
                    return;
            }
            Collections.sort(cands);
            for (long t : cands) {
                if (t < start) continue;
                if (until != null && t > until) return;
                if (count >= 0 && seen >= count) return;
                seen++;
                if (t > winTo) return;
                if (t + Math.max(len, 1) < winFrom || skip.contains(t)) continue;
                add(out, t, len, allDay, title, from, winTo);
            }
        }
    }

    private static long at(Calendar c, int h, int m, int s) {
        Calendar d = (Calendar) c.clone();
        d.set(Calendar.HOUR_OF_DAY, h);
        d.set(Calendar.MINUTE, m);
        d.set(Calendar.SECOND, s);
        d.set(Calendar.MILLISECOND, 0);
        return d.getTimeInMillis();
    }

    private static long dayOf(Calendar month, int day, int h, int m, int s) {
        Calendar d = (Calendar) month.clone();
        d.set(Calendar.DAY_OF_MONTH, day);
        return at(d, h, m, s);
    }

    /** "2TU" = 2nd Tuesday, "-1FR" = last Friday, "TU" = first Tuesday. Day of month or null. */
    private static Integer nthWeekday(Calendar month, String spec) {
        if (spec.length() < 2) return null;
        String dn = spec.substring(spec.length() - 2);
        int n = spec.length() > 2 ? parseInt(spec.substring(0, spec.length() - 2), 1) : 1;
        int want = dayIndex(dn) + 1;
        Calendar d = (Calendar) month.clone();
        int maxD = d.getActualMaximum(Calendar.DAY_OF_MONTH);
        List<Integer> hits = new ArrayList<>();
        for (int day = 1; day <= maxD; day++) {
            d.set(Calendar.DAY_OF_MONTH, day);
            if (d.get(Calendar.DAY_OF_WEEK) == want) hits.add(day);
        }
        int i = n > 0 ? n - 1 : hits.size() + n;
        return i >= 0 && i < hits.size() ? hits.get(i) : null;
    }

    private static int dayIndex(String dn) {
        for (int i = 0; i < 7; i++) if (DAYS[i].equalsIgnoreCase(dn)) return i;
        return 1;
    }

    private static int parseInt(String s, int dflt) {
        try {
            return Integer.parseInt(s.replace("+", ""));
        } catch (Exception e) {
            return dflt;
        }
    }

    private Agenda() {}
}
