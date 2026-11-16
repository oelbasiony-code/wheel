package com.omar.wheel;

import android.os.Handler;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Current weather from Open-Meteo (free, no account or key). */
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
