package com.omar.wheel;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Govee light strip behind the TV (H615C).
 *  - Lights on when the TV turns on, off when it turns off (a tile in the all-apps list
 *    switches this on and off: LIGHTS FOLLOW TV).
 *  - Opening a streaming app from Wheel fades the strip to that app's color; back on
 *    Wheel it fades back to whatever it was before.
 *
 * Talks to the strip over the home network (Govee's LAN API: needs "LAN Control" switched
 * on for the strip in the Govee Home app). Wheel finds the strip by itself; its address
 * can also be set by hand:
 *   adb shell "am start -n com.omar.wheel/.MainActivity --es govee_ip '192.168.86.x'"
 * If LAN Control isn't available, a Govee API key (Govee Home app > Profile > Settings >
 * Apply for API Key) works instead, over the internet. It's NOT in the repo:
 *   adb shell "am start -n com.omar.wheel/.MainActivity --es govee_key 'KEY'"
 */
final class Govee {

    private static final String PREFS = "wheel";
    private static final String K_IP = "govee_ip", K_KEY = "govee_key", K_DEVICE = "govee_device",
            K_SKU = "govee_sku", K_FOLLOW = "govee_follow_tv",
            K_SAVED = "govee_saved", K_TINTED = "govee_tinted";

    private static final int SCAN_PORT = 4001, LISTEN_PORT = 4002, CMD_PORT = 4003;
    private static final String MULTICAST = "239.255.255.250";
    private static final String CLOUD = "https://openapi.api.govee.com/router/api/v1/";

    /** One thread, so commands reach the strip in the order they were given. */
    private static final ExecutorService q = Executors.newSingleThreadExecutor();

    /** Light colors per app (the strip shows saturated colors best; not always the logo color). */
    static int colorFor(String pkg) {
        if (pkg == null) return 0;
        if (pkg.startsWith("com.netflix")) return 0xE50914;
        if (pkg.startsWith("com.disney")) return 0x1F4BFF;
        if (pkg.startsWith("com.hulu")) return 0x1CE783;
        if (pkg.startsWith("com.amazon.avod") || pkg.contains("primevideo")) return 0x00A8E1;
        if (pkg.startsWith("com.peacock")) return 0xF5B800;
        if (pkg.contains("youtube")) return 0xFF1A1A;   // Google's and Amazon's YouTube TV builds
        if (pkg.startsWith("com.apple.atve")) return 0xC8D2FF;   // soft cool white
        if (pkg.startsWith("com.wbd") || pkg.startsWith("com.hbo")) return 0x6A2CFF;
        return 0;
    }

    static final int PLAYSTATION = 0x0070D1, XBOX = 0x107C10;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- settings

    static void setIp(Context c, String ip) {
        sp(c).edit().putString(K_IP, ip.trim()).apply();
    }

    static void setKey(Context c, String key) {
        sp(c).edit().putString(K_KEY, key.trim()).remove(K_DEVICE).remove(K_SKU).apply();
    }

    static boolean followTv(Context c) {
        return sp(c).getBoolean(K_FOLLOW, false);   // off until you turn it on
    }

    static void setFollowTv(Context c, boolean on) {
        sp(c).edit().putBoolean(K_FOLLOW, on).apply();
    }

    // ---------------------------------------------------------------- what Wheel asks for

    static void tvOn(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (!followTv(c)) return;
        q.execute(new Runnable() {
            @Override
            public void run() {
                power(c, true);
                home(c);
            }
        });
    }

    static void tvOff(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (!followTv(c)) return;
        q.execute(new Runnable() {
            @Override
            public void run() {
                power(c, false);
                // Next time it comes on as it was, not in an app's color.
                sp(c).edit().putBoolean(K_TINTED, false).apply();
            }
        });
    }

    /** An app opened: fade to its color (remembering what the strip showed before). */
    static void tint(Context ctx, final int rgb) {
        if (rgb == 0) return;
        final Context c = ctx.getApplicationContext();
        q.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    State now = read(c);
                    if (now == null) return;
                    if (!now.on) return;   // lights are off: leave them off
                    SharedPreferences p = sp(c);
                    if (!p.getBoolean(K_TINTED, false)) {
                        p.edit().putString(K_SAVED, now.toJson()).putBoolean(K_TINTED, true).apply();
                    }
                    fade(c, now, rgb);
                } catch (Exception e) {
                    Log.d(HomeWatcher.TAG, "govee tint: " + e);
                }
            }
        });
    }

    /** Back on Wheel: the home light (or, without one, what the strip showed before the app). */
    static void untint(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (Config.HOME_LIGHT_K <= 0 && !sp(c).getBoolean(K_TINTED, false)) return;
        q.execute(new Runnable() {
            @Override
            public void run() {
                SharedPreferences p = sp(c);
                try {
                    if (Config.HOME_LIGHT_K > 0) {
                        // Only send what's needed: the cloud allows a few commands a minute.
                        State now = read(c);
                        if (now != null && now.on && !isHome(now)) home(c);
                        p.edit().putBoolean(K_TINTED, false).apply();
                        return;
                    }
                    if (!p.getBoolean(K_TINTED, false)) return;
                    State saved = State.fromJson(p.getString(K_SAVED, null));
                    State now = read(c);
                    if (saved != null && now != null && now.on) restore(c, now, saved);
                } catch (Exception e) {
                    Log.d(HomeWatcher.TAG, "govee untint: " + e);
                }
                p.edit().putBoolean(K_TINTED, false).apply();
            }
        });
    }

    private static boolean isHome(State s) {
        boolean n = night();
        int k = n ? Config.NIGHT_LIGHT_K : Config.HOME_LIGHT_K;
        int b = n ? Config.NIGHT_LIGHT_BRIGHTNESS : Config.HOME_LIGHT_BRIGHTNESS;
        return s.rgb == 0 && s.kelvin == k && s.brightness == b;
    }

    /** True from 9 pm to 5 am (Config.NIGHT_FROM_HOUR / NIGHT_UNTIL_HOUR). */
    static boolean night() {
        int hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return hr >= Config.NIGHT_FROM_HOUR || hr < Config.NIGHT_UNTIL_HOUR;
    }

    private static volatile Boolean homeWasNight;

    /**
     * On Wheel when 9 pm (or 5 am) comes around: shift the home light. Only if the
     * strip is showing the home light (not an app's color). Call off the main thread.
     */
    static void homeTick(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (Config.HOME_LIGHT_K <= 0 || sp(c).getBoolean(K_TINTED, false)) return;
        boolean n = night();
        Boolean was = homeWasNight;
        if (was != null && was != n) {
            q.execute(new Runnable() {
                @Override
                public void run() {
                    home(c);
                }
            });
        }
        homeWasNight = n;
    }

    /** Wheel's own light: warm white by day, candlelight at night (see Config). */
    private static void home(Context c) {
        if (Config.HOME_LIGHT_K <= 0) return;
        boolean n = night();
        homeWasNight = n;
        int k = n ? Config.NIGHT_LIGHT_K : Config.HOME_LIGHT_K;
        int b = n ? Config.NIGHT_LIGHT_BRIGHTNESS : Config.HOME_LIGHT_BRIGHTNESS;
        try {
            String ip = lanIp(c);
            if (ip != null) {
                lanSend(ip, "colorwc", new JSONObject().put("color", rgbJson(0))
                        .put("colorTemInKelvin", k));
                lanSend(ip, "brightness", new JSONObject().put("value", b));
                return;
            }
            cloud(c, "devices.capabilities.color_setting", "colorTemperatureK", k);
            cloud(c, "devices.capabilities.range", "brightness", b);
        } catch (Exception e) {
            Log.d(HomeWatcher.TAG, "govee home: " + e);
        }
    }

    // ---------------------------------------------------------------- the strip's state

    static final class State {
        boolean on;
        int rgb;        // 0 when it's showing a white temperature instead
        int kelvin;
        int brightness;

        String toJson() {
            try {
                return new JSONObject().put("on", on).put("rgb", rgb).put("k", kelvin)
                        .put("b", brightness).toString();
            } catch (Exception e) {
                return null;
            }
        }

        static State fromJson(String s) {
            if (s == null) return null;
            try {
                JSONObject o = new JSONObject(s);
                State st = new State();
                st.on = o.optBoolean("on", true);
                st.rgb = o.optInt("rgb", 0);
                st.kelvin = o.optInt("k", 0);
                st.brightness = o.optInt("b", 100);
                return st;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static State read(Context c) throws Exception {
        String ip = lanIp(c);
        if (ip != null) {
            JSONObject d = lanAsk(ip, "devStatus", new JSONObject());
            if (d != null) {
                State s = new State();
                s.on = d.optInt("onOff", 1) == 1;
                s.brightness = d.optInt("brightness", 100);
                JSONObject col = d.optJSONObject("color");
                s.kelvin = d.optInt("colorTemInKelvin", 0);
                if (col != null && s.kelvin == 0) {
                    s.rgb = (col.optInt("r") << 16) | (col.optInt("g") << 8) | col.optInt("b");
                }
                return s;
            }
        }
        return cloudRead(c);
    }

    private static void power(Context c, boolean on) {
        try {
            String ip = lanIp(c);
            if (ip != null) {
                lanSend(ip, "turn", new JSONObject().put("value", on ? 1 : 0));
                return;
            }
            cloud(c, "devices.capabilities.on_off", "powerSwitch", on ? 1 : 0);
        } catch (Exception e) {
            Log.d(HomeWatcher.TAG, "govee power: " + e);
        }
    }

    /** About a second from the current color to the new one (LAN); one jump over the cloud. */
    private static void fade(Context c, State from, int to) throws Exception {
        String ip = lanIp(c);
        if (ip == null) {
            cloud(c, "devices.capabilities.color_setting", "colorRgb", to);
            return;
        }
        int a = from.rgb != 0 ? from.rgb : kelvinToRgb(from.kelvin);
        final int steps = 10;
        for (int i = 1; i <= steps; i++) {
            lanColor(ip, mix(a, to, i / (float) steps));
            Thread.sleep(90);
        }
    }

    private static void restore(Context c, State now, State saved) throws Exception {
        String ip = lanIp(c);
        int target = saved.rgb != 0 ? saved.rgb : kelvinToRgb(saved.kelvin);
        if (ip == null) {
            if (saved.rgb != 0) cloud(c, "devices.capabilities.color_setting", "colorRgb", saved.rgb);
            else cloud(c, "devices.capabilities.color_setting", "colorTemperatureK", saved.kelvin);
            return;
        }
        fade(c, now, target);
        // Land exactly on the white temperature it had, not an RGB guess of it.
        if (saved.rgb == 0 && saved.kelvin > 0) {
            lanSend(ip, "colorwc", new JSONObject()
                    .put("color", rgbJson(0)).put("colorTemInKelvin", saved.kelvin));
        }
        if (saved.brightness > 0 && saved.brightness != now.brightness) {
            lanSend(ip, "brightness", new JSONObject().put("value", saved.brightness));
        }
    }

    // ---------------------------------------------------------------- LAN API

    private static String lanIp(Context c) {
        SharedPreferences p = sp(c);
        String ip = p.getString(K_IP, "");
        if (!ip.isEmpty()) return ip;
        if (!p.getString(K_KEY, "").isEmpty()) return null;   // cloud set up instead
        ip = scan(c);
        if (ip != null) p.edit().putString(K_IP, ip).apply();
        return ip;
    }

    /** Asks every Govee light on the network to say where it is; takes the first answer. */
    private static String scan(Context c) {
        WifiManager.MulticastLock lock = null;
        DatagramSocket in = null, out = null;
        try {
            WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                lock = wm.createMulticastLock("wheel-govee");
                lock.acquire();
            }
            in = new DatagramSocket(null);
            in.setReuseAddress(true);
            in.bind(new InetSocketAddress(LISTEN_PORT));
            in.setSoTimeout(1500);
            out = new DatagramSocket();
            byte[] msg = cmd("scan", new JSONObject().put("account_topic", "reserve")).getBytes("UTF-8");
            for (int tries = 0; tries < 3; tries++) {
                out.send(new DatagramPacket(msg, msg.length, InetAddress.getByName(MULTICAST), SCAN_PORT));
                byte[] buf = new byte[2048];
                DatagramPacket pk = new DatagramPacket(buf, buf.length);
                try {
                    in.receive(pk);
                    JSONObject d = new JSONObject(new String(pk.getData(), 0, pk.getLength(), "UTF-8"))
                            .getJSONObject("msg").getJSONObject("data");
                    String ip = d.optString("ip", pk.getAddress().getHostAddress());
                    Log.d(HomeWatcher.TAG, "govee found " + d.optString("sku") + " at " + ip);
                    return ip;
                } catch (java.net.SocketTimeoutException ignored) {
                }
            }
        } catch (Exception e) {
            Log.d(HomeWatcher.TAG, "govee scan: " + e);
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
            if (lock != null && lock.isHeld()) lock.release();
        }
        return null;
    }

    private static String cmd(String name, JSONObject data) throws Exception {
        return new JSONObject().put("msg", new JSONObject().put("cmd", name).put("data", data)).toString();
    }

    private static void lanSend(String ip, String name, JSONObject data) throws Exception {
        DatagramSocket s = new DatagramSocket();
        try {
            byte[] b = cmd(name, data).getBytes("UTF-8");
            s.send(new DatagramPacket(b, b.length, InetAddress.getByName(ip), CMD_PORT));
        } finally {
            s.close();
        }
    }

    /** Sends a command and waits briefly for the strip's answer (it replies to port 4002). */
    private static JSONObject lanAsk(String ip, String name, JSONObject data) {
        DatagramSocket in = null;
        try {
            in = new DatagramSocket(null);
            in.setReuseAddress(true);
            in.bind(new InetSocketAddress(LISTEN_PORT));
            in.setSoTimeout(1200);
            lanSend(ip, name, data);
            byte[] buf = new byte[2048];
            DatagramPacket pk = new DatagramPacket(buf, buf.length);
            in.receive(pk);
            return new JSONObject(new String(pk.getData(), 0, pk.getLength(), "UTF-8"))
                    .getJSONObject("msg").getJSONObject("data");
        } catch (Exception e) {
            Log.d(HomeWatcher.TAG, "govee ask " + name + ": " + e);
            return null;
        } finally {
            if (in != null) in.close();
        }
    }

    private static void lanColor(String ip, int rgb) throws Exception {
        lanSend(ip, "colorwc", new JSONObject().put("color", rgbJson(rgb)).put("colorTemInKelvin", 0));
    }

    private static JSONObject rgbJson(int rgb) throws Exception {
        return new JSONObject().put("r", (rgb >> 16) & 0xFF).put("g", (rgb >> 8) & 0xFF).put("b", rgb & 0xFF);
    }

    // ---------------------------------------------------------------- cloud API (fallback)

    private static void cloud(Context c, String type, String instance, int value) throws Exception {
        String[] dev = cloudDevice(c);
        if (dev == null) return;
        JSONObject body = new JSONObject().put("requestId", String.valueOf(System.currentTimeMillis()))
                .put("payload", new JSONObject().put("sku", dev[1]).put("device", dev[0])
                        .put("capability", new JSONObject().put("type", type)
                                .put("instance", instance).put("value", value)));
        http(c, "POST", CLOUD + "device/control", body);
    }

    private static State cloudRead(Context c) throws Exception {
        String[] dev = cloudDevice(c);
        if (dev == null) return null;
        JSONObject body = new JSONObject().put("requestId", String.valueOf(System.currentTimeMillis()))
                .put("payload", new JSONObject().put("sku", dev[1]).put("device", dev[0]));
        JSONObject r = http(c, "POST", CLOUD + "device/state", body);
        if (r == null) return null;
        JSONArray caps = r.getJSONObject("payload").getJSONArray("capabilities");
        State s = new State();
        s.on = true;
        s.brightness = 100;
        for (int i = 0; i < caps.length(); i++) {
            JSONObject cap = caps.getJSONObject(i);
            String inst = cap.optString("instance");
            JSONObject st = cap.optJSONObject("state");
            if (st == null) continue;
            if ("powerSwitch".equals(inst)) s.on = st.optInt("value", 1) == 1;
            else if ("brightness".equals(inst)) s.brightness = st.optInt("value", 100);
            else if ("colorRgb".equals(inst)) s.rgb = st.optInt("value", 0);
            else if ("colorTemperatureK".equals(inst)) s.kelvin = st.optInt("value", 0);
        }
        if (s.kelvin > 0) s.rgb = 0;
        return s;
    }

    /** {device id, sku} of the first light on the account (saved after the first look). */
    private static String[] cloudDevice(Context c) throws Exception {
        SharedPreferences p = sp(c);
        if (p.getString(K_KEY, "").isEmpty()) return null;
        String id = p.getString(K_DEVICE, ""), sku = p.getString(K_SKU, "");
        if (!id.isEmpty()) return new String[]{id, sku};
        JSONObject r = http(c, "GET", CLOUD + "user/devices", null);
        if (r == null) return null;
        JSONArray list = r.optJSONArray("data");
        if (list == null || list.length() == 0) return null;
        JSONObject pick = list.getJSONObject(0);
        for (int i = 0; i < list.length(); i++) {
            if (list.getJSONObject(i).optString("sku").startsWith("H615")) pick = list.getJSONObject(i);
        }
        id = pick.optString("device");
        sku = pick.optString("sku");
        p.edit().putString(K_DEVICE, id).putString(K_SKU, sku).apply();
        return new String[]{id, sku};
    }

    private static JSONObject http(Context c, String method, String url, JSONObject body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setRequestMethod(method);
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setRequestProperty("Govee-API-Key", sp(c).getString(K_KEY, ""));
            con.setRequestProperty("Content-Type", "application/json");
            if (body != null) {
                con.setDoOutput(true);
                OutputStream o = con.getOutputStream();
                o.write(body.toString().getBytes("UTF-8"));
                o.close();
            }
            int code = con.getResponseCode();
            InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream();
            String text = in == null ? "" : new Scanner(in, "UTF-8").useDelimiter("\\A").next();
            if (code >= 400) {
                Log.d(HomeWatcher.TAG, "govee cloud " + code + ": " + text);
                return null;
            }
            return new JSONObject(text);
        } finally {
            con.disconnect();
        }
    }

    // ---------------------------------------------------------------- color helpers

    private static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** Rough RGB for a white temperature, only used as the fade's starting/ending point. */
    private static int kelvinToRgb(int k) {
        if (k <= 0) return 0xFFE4C4;
        if (k < 3000) return 0xFFB46B;
        if (k < 4000) return 0xFFD1A3;
        if (k < 5500) return 0xFFE4CE;
        return 0xF2F2FF;
    }

    private Govee() {}
}
