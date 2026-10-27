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
        void onWeather(String now, String range, int code, boolean isDay);
    }

    private static final String URL_FMT = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=" + Config.LAT + "&longitude=" + Config.LON
            + "&current=temperature_2m,weather_code,is_day"
            + "&daily=temperature_2m_max,temperature_2m_min"
            + "&temperature_unit=fahrenheit&timezone=auto&forecast_days=1";

    static void fetch(final Handler ui, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String now = null, range = null;
                int code = -1;
                boolean day = true;
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
                } catch (Exception ignored) {
                } finally {
                    if (con != null) con.disconnect();
                }
                final String n = now, r = range;
                final int fc = code;
                final boolean fd = day;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onWeather(n, r, fc, fd);
                    }
                });
            }
        }, "wheel-weather").start();
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
