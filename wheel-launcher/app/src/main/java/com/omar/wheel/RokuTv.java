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

    private RokuTv() {}
}
