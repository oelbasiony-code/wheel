package com.omar.wheel;

import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * The next commuter rail trains from your station, from the MBTA's free live feed.
 * Uses the timetable, with live predictions laid on top when the train is running.
 */
final class Train {

    interface Callback {
        /** Departure times (epoch millis), soonest first. null if the fetch failed. */
        void onTrains(List<Long> departures);
    }

    private static final String API = "https://api-v3.mbta.com";

    static void fetch(final Handler ui, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Long> out = null;
                try {
                    out = load();
                } catch (Exception ignored) {
                }
                final List<Long> res = out;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onTrains(res);
                    }
                });
            }
        }, "wheel-train").start();
    }

    private static List<Long> load() throws Exception {
        String filter = "filter%5Bstop%5D=" + Config.TRAIN_STOP
                + "&filter%5Broute%5D=" + Config.TRAIN_ROUTE
                + "&filter%5Bdirection_id%5D=" + Config.TRAIN_DIRECTION;

        // Timetable for the rest of the service day (it runs past midnight, so 1 AM = "25:00").
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        int hr = c.get(Calendar.HOUR_OF_DAY), min = c.get(Calendar.MINUTE);
        if (hr < 3) hr += 24;
        String minTime = (hr < 10 ? "0" : "") + hr + "%3A" + (min < 10 ? "0" : "") + min;
        JSONArray sched = get(API + "/schedules?" + filter + "&filter%5Bmin_time%5D=" + minTime
                + "&sort=departure_time&page%5Blimit%5D=8");

        Map<String, Long> byTrip = new HashMap<>();
        for (int i = 0; i < sched.length(); i++) {
            JSONObject s = sched.getJSONObject(i);
            long t = parse(s.getJSONObject("attributes").optString("departure_time", null));
            String trip = tripOf(s);
            if (t > 0 && trip != null) byTrip.put(trip, t);
        }

        // Live predictions win when there are any (delays, cancellations).
        Set<String> cancelled = new HashSet<>();
        try {
            JSONArray pred = get(API + "/predictions?" + filter + "&sort=departure_time");
            for (int i = 0; i < pred.length(); i++) {
                JSONObject p = pred.getJSONObject(i);
                JSONObject a = p.getJSONObject("attributes");
                String trip = tripOf(p);
                if (trip == null) continue;
                if ("CANCELLED".equalsIgnoreCase(a.optString("schedule_relationship", ""))) {
                    cancelled.add(trip);
                    continue;
                }
                long t = parse(a.optString("departure_time", null));
                if (t > 0) byTrip.put(trip, t);
            }
        } catch (Exception ignored) {
            // The timetable alone is still useful.
        }

        long now = System.currentTimeMillis();
        List<Long> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : byTrip.entrySet()) {
            if (cancelled.contains(e.getKey())) continue;
            if (e.getValue() > now - 60000L) out.add(e.getValue());
        }
        Collections.sort(out);

        // Last train of the night is gone: tomorrow's first trains, so there's always a time.
        if (out.isEmpty() || out.get(out.size() - 1) < now) {
            Calendar t = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
            if (t.get(Calendar.HOUR_OF_DAY) >= 3) t.add(Calendar.DAY_OF_MONTH, 1);
            String date = t.get(Calendar.YEAR) + "-" + two(t.get(Calendar.MONTH) + 1) + "-"
                    + two(t.get(Calendar.DAY_OF_MONTH));
            JSONArray next = get(API + "/schedules?" + filter + "&filter%5Bdate%5D=" + date
                    + "&sort=departure_time&page%5Blimit%5D=3");
            for (int i = 0; i < next.length(); i++) {
                long d = parse(next.getJSONObject(i).getJSONObject("attributes")
                        .optString("departure_time", null));
                if (d > now) out.add(d);
            }
            Collections.sort(out);
        }
        return out;
    }

    private static String two(int n) {
        return (n < 10 ? "0" : "") + n;
    }

    private static String tripOf(JSONObject o) {
        try {
            return o.getJSONObject("relationships").getJSONObject("trip")
                    .getJSONObject("data").getString("id");
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONArray get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(8000);
            con.setReadTimeout(10000);
            con.setRequestProperty("Accept", "application/vnd.api+json");
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            return new JSONObject(sb.toString()).getJSONArray("data");
        } finally {
            con.disconnect();
        }
    }

    /** "2026-11-16T07:12:00-05:00" to epoch millis (0 if missing). */
    static long parse(String iso) {
        if (iso == null || iso.length() < 19 || iso.equals("null")) return 0;
        try {
            int y = Integer.parseInt(iso.substring(0, 4));
            int mo = Integer.parseInt(iso.substring(5, 7));
            int d = Integer.parseInt(iso.substring(8, 10));
            int h = Integer.parseInt(iso.substring(11, 13));
            int mi = Integer.parseInt(iso.substring(14, 16));
            int s = Integer.parseInt(iso.substring(17, 19));
            String off = iso.substring(19);
            int sign = 1, oh = 0, om = 0;
            if (off.startsWith("+") || off.startsWith("-")) {
                sign = off.startsWith("-") ? -1 : 1;
                oh = Integer.parseInt(off.substring(1, 3));
                om = Integer.parseInt(off.substring(4, 6));
            }
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.clear();
            c.set(y, mo - 1, d, h, mi, s);
            return c.getTimeInMillis() - sign * (oh * 3600000L + om * 60000L);
        } catch (Exception e) {
            return 0;
        }
    }

    private Train() {}
}
