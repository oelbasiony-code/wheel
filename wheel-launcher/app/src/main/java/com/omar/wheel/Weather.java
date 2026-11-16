package com.omar.wheel;

import android.os.Handler;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Current weather from Open-Meteo (free, no account or key). If Open-Meteo doesn't answer
 * (it had an outage on Nov 16), MET Norway's free forecast is the backup. The last good
 * reading is kept, so an outage never blanks the screen.
 */
final class Weather {

    interface Callback {
        /** now/range are null if the fetch failed. code = WMO weather code, isDay = sun is up. */
        void onWeather(String now, String range, int code, boolean isDay, Extra extra);
    }

    /** The extra bits for the rotating line. Times are epoch millis, 0 = none. */
    static final class Extra {
        long[] sunrises = new long[0], sunsets = new long[0];
        /** First hour in the next 12 with a real chance of rain or snow (it's not falling now). */
        long wetAt;
        boolean wetIsSnow;
    }

    private static final String URL_FMT = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=" + Config.LAT + "&longitude=" + Config.LON
            + "&current=temperature_2m,weather_code,is_day"
            + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
            + "&hourly=precipitation_probability,weather_code"
            + "&temperature_unit=fahrenheit&timezone=auto&timeformat=unixtime&forecast_days=2";

    static void fetch(final Handler ui, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String now = null, range = null;
                int code = -1;
                boolean day = true;
                Extra extra = null;
                HttpURLConnection con = null;
                try {
                    con = (HttpURLConnection) new URL(URL_FMT).openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(8000);
                    StringBuilder sb = new StringBuilder();
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = in.readLine()) != null) sb.append(line);
                    in.close();
                    JSONObject o = new JSONObject(sb.toString());
                    JSONObject cur = o.getJSONObject("current");
                    JSONObject daily = o.getJSONObject("daily");
                    long t = Math.round(cur.getDouble("temperature_2m"));
                    long hi = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
                    long lo = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
                    code = cur.getInt("weather_code");
                    day = cur.optInt("is_day", 1) == 1;
                    now = t + "" + DotFont.DEG + " " + describe(code);
                    range = "H " + hi + DotFont.DEG + "   L " + lo + DotFont.DEG;
                    extra = extra(o, code);
                } catch (Exception ignored) {
                } finally {
                    if (con != null) con.disconnect();
                }
                if (now == null) {
                    // Backup: MET Norway.
                    try {
                        Object[] m = metNo();
                        now = (String) m[0];
                        range = (String) m[1];
                        code = (Integer) m[2];
                        day = (Boolean) m[3];
                        extra = (Extra) m[4];
                    } catch (Exception ignored) {
                    }
                }
                final String n = now, r = range;
                final int fc = code;
                final boolean fd = day;
                final Extra fx = extra;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onWeather(n, r, fc, fd, fx);
                    }
                });
            }
        }, "wheel-weather").start();
    }

    private static Extra extra(JSONObject o, int nowCode) {
        Extra x = new Extra();
        try {
            JSONObject daily = o.getJSONObject("daily");
            org.json.JSONArray sr = daily.getJSONArray("sunrise"), ss = daily.getJSONArray("sunset");
            x.sunrises = new long[sr.length()];
            x.sunsets = new long[ss.length()];
            for (int i = 0; i < sr.length(); i++) x.sunrises[i] = sr.getLong(i) * 1000L;
            for (int i = 0; i < ss.length(); i++) x.sunsets[i] = ss.getLong(i) * 1000L;
        } catch (Exception ignored) {
        }
        try {
            if (isWet(nowCode)) return x;   // already raining: the weather line says so
            JSONObject hourly = o.getJSONObject("hourly");
            org.json.JSONArray t = hourly.getJSONArray("time");
            org.json.JSONArray pp = hourly.getJSONArray("precipitation_probability");
            org.json.JSONArray wc = hourly.getJSONArray("weather_code");
            long now = System.currentTimeMillis();
            for (int i = 0; i < t.length(); i++) {
                long ts = t.getLong(i) * 1000L;
                if (ts + 3600000L <= now) continue;
                if (ts > now + 12 * 3600000L) break;
                int code = wc.optInt(i, 0);
                if (pp.optInt(i, 0) >= 50 && isWet(code)) {
                    x.wetAt = Math.max(ts, now);
                    x.wetIsSnow = (code >= 71 && code <= 77) || code == 85 || code == 86;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return x;
    }

    private static final String MET_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
            + "?lat=" + Config.LAT + "&lon=" + Config.LON;

    /** {now, range, code, isDay, extra} from MET Norway (asks for a User-Agent naming the app). */
    private static Object[] metNo() throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(MET_URL).openConnection();
        try {
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setRequestProperty("User-Agent", "wheel-tv github.com/oelbasiony-code/wheel");
            StringBuilder sb = new StringBuilder();
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            org.json.JSONArray ts = new JSONObject(sb.toString()).getJSONObject("properties")
                    .getJSONArray("timeseries");
            java.text.SimpleDateFormat iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                    java.util.Locale.US);
            iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int today = cal.get(java.util.Calendar.DAY_OF_YEAR);
            long nowMs = System.currentTimeMillis();

            JSONObject first = ts.getJSONObject(0).getJSONObject("data");
            double tC = first.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            String sym = symbol(first);
            double hiC = tC, loC = tC;
            Extra x = new Extra();
            for (int i = 0; i < ts.length(); i++) {
                JSONObject e = ts.getJSONObject(i);
                long t = iso.parse(e.getString("time")).getTime();
                cal.setTimeInMillis(t);
                JSONObject data = e.getJSONObject("data");
                if (cal.get(java.util.Calendar.DAY_OF_YEAR) == today) {
                    double v = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
                    hiC = Math.max(hiC, v);
                    loC = Math.min(loC, v);
                }
                if (x.wetAt == 0 && t > nowMs && t < nowMs + 12 * 3600000L) {
                    int c = code(symbol(data));
                    JSONObject n1 = data.optJSONObject("next_1_hours");
                    double mm = n1 == null ? 0 : n1.getJSONObject("details").optDouble("precipitation_amount", 0);
                    if (isWet(c) && mm >= 0.3) {
                        x.wetAt = t;
                        x.wetIsSnow = (c >= 71 && c <= 77) || c == 85 || c == 86;
                    }
                }
            }
            int code = code(sym);
            if (isWet(code)) x.wetAt = 0;   // already falling: the weather line says so
            boolean day = !sym.endsWith("_night");
            String now = f(tC) + DotFont.DEG + " " + describe(code);
            String range = "H " + f(hiC) + DotFont.DEG + "   L " + f(loC) + DotFont.DEG;
            return new Object[]{now, range, code, day, x};
        } finally {
            con.disconnect();
        }
    }

    private static long f(double c) {
        return Math.round(c * 9 / 5 + 32);
    }

    private static String symbol(JSONObject data) {
        JSONObject n = data.optJSONObject("next_1_hours");
        if (n == null) n = data.optJSONObject("next_6_hours");
        return n == null ? "cloudy" : n.getJSONObject("summary").optString("symbol_code", "cloudy");
    }

    /** MET Norway symbol ("rainshowers_day") to the WMO code the icons use. */
    static int code(String sym) {
        String s = sym.replace("_day", "").replace("_night", "").replace("_polartwilight", "");
        if (s.contains("thunder")) return 95;
        if (s.startsWith("clearsky")) return 0;
        if (s.startsWith("fair")) return 1;
        if (s.startsWith("partlycloudy")) return 2;
        if (s.startsWith("cloudy")) return 3;
        if (s.startsWith("fog")) return 45;
        if (s.contains("sleet")) return 66;
        if (s.contains("snowshowers")) return s.startsWith("heavy") ? 86 : 85;
        if (s.contains("snow")) return s.startsWith("light") ? 71 : s.startsWith("heavy") ? 75 : 73;
        if (s.contains("rainshowers")) return s.startsWith("light") ? 80 : s.startsWith("heavy") ? 82 : 81;
        if (s.contains("rain")) return s.startsWith("light") ? 61 : s.startsWith("heavy") ? 65 : 63;
        return 3;
    }

    // ---------------------------------------------------------------- the last good reading

    static void save(android.content.Context c, String now, String range, int code, boolean day) {
        c.getApplicationContext().getSharedPreferences("wheel", android.content.Context.MODE_PRIVATE).edit()
                .putString("wx_now", now).putString("wx_range", range).putInt("wx_code", code)
                .putBoolean("wx_day", day).putLong("wx_at", System.currentTimeMillis()).apply();
    }

    /** {now, range, code, day} from the last good reading if it's under 6 hours old, else null. */
    static Object[] saved(android.content.Context c) {
        android.content.SharedPreferences p = c.getApplicationContext()
                .getSharedPreferences("wheel", android.content.Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("wx_at", 0) > 6 * 3600000L) return null;
        String now = p.getString("wx_now", null);
        if (now == null) return null;
        return new Object[]{now, p.getString("wx_range", null), p.getInt("wx_code", -1), p.getBoolean("wx_day", true)};
    }

    static boolean isWet(int code) {
        return (code >= 51 && code <= 67) || (code >= 71 && code <= 86) || code >= 95;
    }

    /** WMO weather codes to short words. */
    static String describe(int code) {
        if (code == 0) return "CLEAR";
        if (code == 1) return "CLEAR";
        if (code == 2) return "CLOUDY";
        if (code == 3) return "CLOUDY";
        if (code == 45 || code == 48) return "FOG";
        if (code >= 51 && code <= 57) return "DRIZZLE";
        if (code >= 61 && code <= 67) return "RAIN";
        if (code >= 71 && code <= 77) return "SNOW";
        if (code >= 80 && code <= 82) return "SHOWERS";
        if (code == 85 || code == 86) return "SNOW";
        if (code >= 95) return "STORMS";
        return "";
    }

    private Weather() {}
}
