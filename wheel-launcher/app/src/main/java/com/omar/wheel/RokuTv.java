package com.omar.wheel;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the Roku TV over Wi-Fi (Roku's External Control Protocol, port 8060),
 * so a wheel tile can switch the TV to a console's HDMI input.
 */
final class RokuTv {

    interface Done {
        /** error = null on success, otherwise a short message to show. */
        void done(String error);
    }

    private static final String PREFS = "wheel";
    private static final String K_IP = "roku_ip";
    /** The TV's Wi-Fi + Ethernet MACs, learned from device-info, for Wake-on-LAN. */
    private static final String K_MACS = "roku_macs";
    private static final Pattern LOCATION =
            Pattern.compile("(?im)^location:\\s*http://([0-9.]+):8060");

    /** Sends the input switch in the background. */
    static void switchInput(final Context ctx, final String input, final Done done) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                done.done(switchNow(app, input));
            }
        }, "roku").start();
    }

    private static String switchNow(Context ctx, String input) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String ip = Config.ROKU_TV_IP.isEmpty() ? sp.getString(K_IP, "") : Config.ROKU_TV_IP;
        int code = ip.isEmpty() ? -1 : keypress(ip, input);
        if (code < 0 && Config.ROKU_TV_IP.isEmpty()) {
            // Not known yet, or the TV got a new address: look for it again.
            String found = discover(ctx);
            if (found == null) return "Couldn't find the Roku TV on Wi-Fi";
            sp.edit().putString(K_IP, found).apply();
            code = keypress(found, input);
        }
        if (code == 200) return null;
        if (code == 403 || code == 401) {
            return "Turn on Control by mobile apps on the TV (Settings > System > Advanced)";
        }
        if (code < 0) return "Couldn't reach the Roku TV";
        return "The TV didn't switch (" + code + ")";
    }

    /** Presses a key on the TV ("PowerOn", "PowerOff", "InputHDMI4"...). Off the main thread. */
    static boolean press(Context ctx, String key) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String ip = Config.ROKU_TV_IP.isEmpty() ? sp.getString(K_IP, "") : Config.ROKU_TV_IP;
        int code = ip.isEmpty() ? -1 : keypress(ip, key);
        if (code < 0 && Config.ROKU_TV_IP.isEmpty()) {
            String found = discover(ctx);
            if (found == null) return false;
            sp.edit().putString(K_IP, found).apply();
            code = keypress(found, key);
        }
        return code == 200;
    }

    /**
     * Turns the TV on, even from deep sleep. A Roku TV that's been off a while drops off
     * Wi-Fi and ignores ECP, but it wakes on a Wake-on-LAN packet (supports-wake-on-wlan),
     * so if PowerOn doesn't go through, send the magic packet and keep trying for ~40 s.
     * Off the main thread.
     */
    static boolean powerOn(Context ctx) {
        // Send the magic packet first, so a sleeping TV starts waking right away instead of
        // after a timed-out PowerOn; then keep nudging it every second for up to ~40 s.
        wakeOnLan(ctx);
        for (int i = 0; i < 40; i++) {
            if (press(ctx, "PowerOn")) return true;
            wakeOnLan(ctx);
            try { Thread.sleep(1000); } catch (InterruptedException e) { return false; }
        }
        return false;
    }

    /** Sends the Wake-on-LAN magic packet to the TV's Wi-Fi and Ethernet addresses. */
    static void wakeOnLan(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String macs = sp.getString(K_MACS, Config.ROKU_TV_MACS);
        DatagramSocket sock = null;
        try {
            sock = new DatagramSocket();
            sock.setBroadcast(true);
            InetAddress all = InetAddress.getByName("255.255.255.255");
            String ip = Config.ROKU_TV_IP.isEmpty() ? sp.getString(K_IP, "") : Config.ROKU_TV_IP;
            InetAddress subnet = ip.contains(".")
                    ? InetAddress.getByName(ip.substring(0, ip.lastIndexOf('.')) + ".255") : null;
            for (String mac : macs.split("\\s+")) {
                String hex = mac.replace(":", "").replace("-", "");
                if (hex.length() != 12) continue;
                byte[] p = new byte[6 + 16 * 6];
                for (int i = 0; i < 6; i++) p[i] = (byte) 0xff;
                for (int r = 0; r < 16; r++) {
                    for (int i = 0; i < 6; i++) {
                        p[6 + r * 6 + i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                    }
                }
                for (int port : new int[] {9, 7}) {
                    sock.send(new DatagramPacket(p, p.length, all, port));
                    if (subnet != null) sock.send(new DatagramPacket(p, p.length, subnet, port));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (sock != null) sock.close();
        }
    }

    /** POST /keypress/InputHDMI2 -> HTTP status, or -1 if the TV can't be reached. */
    private static int keypress(String ip, String key) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + ip + ":8060/keypress/" + key).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(1500);
            c.setReadTimeout(2500);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(0);
            OutputStream o = c.getOutputStream();
            o.close();
            return c.getResponseCode();
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Asks the network "any Rokus here?" (SSDP) and returns the first one's IP. */
    private static String discover(Context ctx) {
        WifiManager.MulticastLock lock = null;
        DatagramSocket sock = null;
        try {
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                lock = wm.createMulticastLock("wheel-roku");
                lock.acquire();
            }
            String msg = "M-SEARCH * HTTP/1.1\r\n"
                    + "HOST: 239.255.255.250:1900\r\n"
                    + "MAN: \"ssdp:discover\"\r\n"
                    + "MX: 2\r\n"
                    + "ST: roku:ecp\r\n\r\n";
            byte[] out = msg.getBytes("US-ASCII");
            sock = new DatagramSocket();
            sock.setSoTimeout(1200);
            InetAddress group = InetAddress.getByName("239.255.255.250");
            byte[] buf = new byte[2048];
            for (int attempt = 0; attempt < 3; attempt++) {
                sock.send(new DatagramPacket(out, out.length, group, 1900));
                long until = System.currentTimeMillis() + 1500;
                while (System.currentTimeMillis() < until) {
                    DatagramPacket in = new DatagramPacket(buf, buf.length);
                    try {
                        sock.receive(in);
                    } catch (SocketTimeoutException t) {
                        break;
                    }
                    String reply = new String(in.getData(), 0, in.getLength(), "US-ASCII");
                    if (!reply.toLowerCase(Locale.US).contains("roku:ecp")) continue;
                    Matcher m = LOCATION.matcher(reply);
                    if (m.find()) return m.group(1);
                    return in.getAddress().getHostAddress();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (sock != null) sock.close();
            if (lock != null && lock.isHeld()) lock.release();
        }
        return null;
    }

    /**
     * Is the TV's screen on? Roku reports "PowerOn" while you're watching and something
     * else (DisplayOff, Ready, Suspend...) when it's off. null = couldn't ask.
     * The Fire Stick stays awake when the TV turns off, so this is how Wheel knows.
     * Call off the main thread.
     */
    static Boolean screenOn(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String ip = Config.ROKU_TV_IP.isEmpty() ? sp.getString(K_IP, "") : Config.ROKU_TV_IP;
        Boolean on = ip.isEmpty() ? null : powerMode(ip, sp);
        if (on == null && Config.ROKU_TV_IP.isEmpty()) {
            String found = discover(ctx);
            if (found == null) return null;
            sp.edit().putString(K_IP, found).apply();
            on = powerMode(found, sp);
        }
        return on;
    }

    /** What the TV is showing: an app id like "tvinput.hdmi4", or null. Off the main thread. */
    static String activeApp(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String ip = Config.ROKU_TV_IP.isEmpty() ? sp.getString(K_IP, "") : Config.ROKU_TV_IP;
        if (ip.isEmpty()) return null;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + ip + ":8060/query/active-app").openConnection();
            c.setConnectTimeout(2000);
            c.setReadTimeout(2000);
            if (c.getResponseCode() != 200) return null;
            String body = new java.util.Scanner(c.getInputStream(), "UTF-8").useDelimiter("\\A").next();
            Matcher m = Pattern.compile("<app id=\"([^\"]*)\"").matcher(body);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static Boolean powerMode(String ip, SharedPreferences sp) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + ip + ":8060/query/device-info").openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            if (c.getResponseCode() != 200) return null;
            String body = new java.util.Scanner(c.getInputStream(), "UTF-8").useDelimiter("\\A").next();
            Matcher mac = Pattern.compile("<(?:wifi|ethernet)-mac>([0-9a-fA-F:]{17})<").matcher(body);
            StringBuilder macs = new StringBuilder();
            while (mac.find()) macs.append(macs.length() > 0 ? " " : "").append(mac.group(1));
            if (macs.length() > 0 && !macs.toString().equals(sp.getString(K_MACS, ""))) {
                sp.edit().putString(K_MACS, macs.toString()).apply();
            }
            Matcher m = Pattern.compile("<power-mode>([^<]*)</power-mode>").matcher(body);
            if (!m.find()) return null;
            return "PowerOn".equalsIgnoreCase(m.group(1).trim());
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private RokuTv() {}
}
