package com.omar.wheel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Messages to the TV. Your phone posts text to a private ntfy.sh topic (free, no account);
 * Wheel keeps a connection open to that topic and shows what arrives in dots.
 *
 * The topic name works like a password, so it's NOT in the code (the repo is public).
 * It's saved in the app's private storage, sent once with adb:
 *   adb shell "am start -n com.omar.wheel/.MainActivity --es message_topic 'TOPIC'"
 *
 * The listener runs in HomeWatcher (always on), so messages arrive even while you're
 * watching something else.
 */
final class Messages {

    interface Listener {
        /** mail = it came in as an email (to the topic's email address), not a text. */
        void onMessage(String text, boolean mail);
    }

    private static final String PREFS = "wheel";
    private static final String K_TOPIC = "ntfy_topic", K_LAST_ID = "ntfy_last_id",
            K_TEXT = "msg_text", K_TIME = "msg_time", K_UNREAD = "msg_unread", K_DND = "dnd",
            K_MAIL_TEXT = "mail_text", K_MAIL_TIME = "mail_time", K_UNREAD_MAIL = "msg_unread_mail";

    /** How long the last message stays on the card. */
    static final long ON_CARD_MS = 2 * 60 * 60 * 1000L;

    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final List<Listener> listeners = new ArrayList<>();
    private static Thread thread;
    private static volatile boolean running;
    private static volatile HttpURLConnection live;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- settings

    static void setTopic(Context c, String topic) {
        sp(c).edit().putString(K_TOPIC, topic.trim()).remove(K_LAST_ID).commit();
        restart(c);
    }

    static boolean hasTopic(Context c) {
        return !sp(c).getString(K_TOPIC, "").isEmpty();
    }

    /** Do Not Disturb: nothing shows over your shows; messages wait for Wheel. */
    static boolean dnd(Context c) {
        return sp(c).getBoolean(K_DND, false);
    }

    static void setDnd(Context c, boolean on) {
        sp(c).edit().putBoolean(K_DND, on).apply();
    }

    // ---------------------------------------------------------------- the last message

    /** The last message if it's recent enough for the card, else null. */
    static String recent(Context c, long now) {
        SharedPreferences p = sp(c);
        long t = p.getLong(K_TIME, 0);
        return now - t < ON_CARD_MS ? p.getString(K_TEXT, null) : null;
    }

    /** How long a text or email shows on the card before it says NO NEW MESSAGES / MAIL. */
    static final long FRESH_MS = 10 * 60 * 1000L;

    /** The last text (or email) if it came in the last 10 minutes, else null. */
    static String fresh(Context c, long now, boolean mail) {
        SharedPreferences p = sp(c);
        if (p.getBoolean(mail ? "mail_opened" : "msg_opened", false)) return null;   // read it
        long t = p.getLong(mail ? K_MAIL_TIME : K_TIME, 0);
        return now - t < FRESH_MS ? p.getString(mail ? K_MAIL_TEXT : K_TEXT, null) : null;
    }

    /** The last email if it's recent enough to open with Play/Pause, else null. */
    static String recentMail(Context c, long now) {
        SharedPreferences p = sp(c);
        long t = p.getLong(K_MAIL_TIME, 0);
        return now - t < ON_CARD_MS ? p.getString(K_MAIL_TEXT, null) : null;
    }

    /** The newest of the two that's still on the card: {text, "mail" or "msg"}, or null. */
    static String[] latest(Context c, long now) {
        SharedPreferences p = sp(c);
        String m = recent(c, now), e = recentMail(c, now);
        if (m == null && e == null) return null;
        if (e == null || (m != null && p.getLong(K_TIME, 0) >= p.getLong(K_MAIL_TIME, 0))) {
            return new String[]{m, "msg"};
        }
        return new String[]{e, "mail"};
    }

    /** What you haven't seen yet, or null. */
    static String unread(Context c) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean(K_UNREAD, false)) return null;
        return p.getString(p.getBoolean(K_UNREAD_MAIL, false) ? K_MAIL_TEXT : K_TEXT, null);
    }

    static boolean unreadIsMail(Context c) {
        return sp(c).getBoolean(K_UNREAD_MAIL, false);
    }

    static void markRead(Context c) {
        sp(c).edit().putBoolean(K_UNREAD, false).apply();
    }

    static void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    static void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ---------------------------------------------------------------- listening

    static synchronized void start(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (running || !hasTopic(c)) return;
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        listen(c);
                    } catch (Exception e) {
                        Log.d(HomeWatcher.TAG, "messages: " + e);
                    }
                    if (!running) break;
                    try {
                        Thread.sleep(15000);   // dropped (Wi-Fi blip, server restart): reconnect
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }, "wheel-messages");
        thread.start();
    }

    static synchronized void restart(Context c) {
        running = false;
        HttpURLConnection h = live;
        if (h != null) h.disconnect();
        if (thread != null) thread.interrupt();
        thread = null;
        start(c);
    }

    /** One long-lived connection: ntfy sends a line of JSON per message (and keepalives). */
    private static void listen(final Context c) throws Exception {
        SharedPreferences p = sp(c);
        String topic = p.getString(K_TOPIC, "");
        if (topic.isEmpty()) return;
        // Pick up where we left off; the very first time, only new messages.
        String since = p.getString(K_LAST_ID, String.valueOf(System.currentTimeMillis() / 1000));
        URL url = new URL("https://ntfy.sh/" + java.net.URLEncoder.encode(topic, "UTF-8")
                + "/json?since=" + since);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        live = con;
        try {
            con.setConnectTimeout(10000);
            con.setReadTimeout(120000);   // ntfy sends a keepalive every ~45 s
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            String line;
            while (running && (line = in.readLine()) != null) {
                JSONObject o = new JSONObject(line);
                if (!"message".equals(o.optString("event"))) continue;
                String raw = o.optString("message", "");
                // A command from the phone ("!home"...), in the text or the title: do it, don't show it.
                String rawTitle = o.optString("title", "").trim();
                String cmd = raw.trim().startsWith("!") ? raw.trim()
                        : rawTitle.startsWith("!") ? rawTitle : null;
                if (cmd != null) {
                    p.edit().putString(K_LAST_ID, o.optString("id", since)).apply();
                    Commands.run(c, cmd.substring(1).trim().toLowerCase(java.util.Locale.US));
                    continue;
                }
                // Emails always have a body; texts from the phone don't (all in the title).
                String body = raw.trim();
                final boolean mail = !body.isEmpty() && !body.equalsIgnoreCase("triggered");
                String text = tidy(raw);
                String title = nameOrNumber(o.optString("title", "").trim());
                // ntfy fills in "triggered" when the phone sent an empty body.
                if (text.equalsIgnoreCase("triggered")) text = "";
                if (!title.isEmpty()) text = text.isEmpty() ? title : title + ": " + text;
                final String clean = DotFont.clean(text);
                // Two phone automations can both catch one text: show it once.
                long nowMs = System.currentTimeMillis();
                if (!clean.isEmpty() && clean.equals(lastShown) && nowMs - lastShownAt < 30000L) continue;
                lastShown = clean;
                lastShownAt = nowMs;
                p.edit().putString(K_LAST_ID, o.optString("id", since))
                        .putString(mail ? K_MAIL_TEXT : K_TEXT, clean)
                        .putLong(mail ? K_MAIL_TIME : K_TIME, System.currentTimeMillis())
                        .putBoolean(K_UNREAD, true)
                        .putBoolean(K_UNREAD_MAIL, mail)
                        .putBoolean(mail ? "mail_opened" : "msg_opened", false)
                        .apply();
                if (clean.isEmpty()) continue;
                addToHistory(p, clean, mail, nowMs);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Listener l : new ArrayList<>(listeners)) l.onMessage(clean, mail);
                    }
                });
            }
        } finally {
            live = null;
            con.disconnect();
        }
    }

    /**
     * The phone sends "NAME|NUMBER : TEXT" (the contact's name, a bar, then the number).
     * Shows the name when the sender is a saved contact, otherwise the number.
     */
    static String nameOrNumber(String title) {
        int bar = title.indexOf('|');
        if (bar < 0) return title;
        String name = title.substring(0, bar).trim();
        if (name.equalsIgnoreCase("No Name")) name = "";   // Shortcuts' word for "not a contact"
        String rest = title.substring(bar + 1);
        int colon = rest.indexOf(':');
        String number = (colon < 0 ? rest : rest.substring(0, colon)).trim();
        String text = colon < 0 ? "" : rest.substring(colon + 1).trim();
        String who = name.isEmpty() ? number : name;
        return text.isEmpty() ? who : who + ": " + text;
    }

    // ---------------------------------------------------------------- the inbox
    // Keeps up to 200 from the past 7 days; the inbox shows all of them (texts and emails on
    // their own tabs), and the briefing counts from them.

    private static final String K_HISTORY = "msg_history";
    private static final int HISTORY_MAX = 200;
    /** Inbox items older than this drop off by themselves. */
    private static final long HISTORY_DAYS_MS = 7 * 24 * 3600000L;

    static final class Item {
        final String text;
        final boolean mail;
        final long time;
        boolean read;   // popped up while the TV was on, or opened

        Item(String text, boolean mail, long time, boolean read) {
            this.text = text;
            this.mail = mail;
            this.time = time;
            this.read = read;
        }
    }

    private static void addToHistory(SharedPreferences p, String text, boolean mail, long time) {
        try {
            org.json.JSONArray old = new org.json.JSONArray(p.getString(K_HISTORY, "[]"));
            org.json.JSONArray out = new org.json.JSONArray();
            // Seen if it came in while the TV was on (it popped up); missed if the TV was off.
            boolean seen = Boolean.TRUE.equals(HomeWatcher.tvIsOn);
            out.put(new JSONObject().put("x", text).put("m", mail).put("t", time).put("r", seen));
            for (int i = 0; i < old.length() && out.length() < HISTORY_MAX; i++) out.put(old.get(i));
            p.edit().putString(K_HISTORY, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Opened it: no longer missed, and off the card if it's the newest one. */
    static void markItemRead(Context c, String text) {
        SharedPreferences p = sp(c);
        if (text.equals(p.getString(K_TEXT, null))) p.edit().putBoolean("msg_opened", true).apply();
        if (text.equals(p.getString(K_MAIL_TEXT, null))) p.edit().putBoolean("mail_opened", true).apply();
        try {
            org.json.JSONArray a = new org.json.JSONArray(p.getString(K_HISTORY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optString("x").equals(text)) o.put("r", true);
            }
            p.edit().putString(K_HISTORY, a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** CLEAR ALL in the inbox: forget every text (or every email); the card says NO NEW ... */
    static void clearHistory(Context c, boolean mail) {
        SharedPreferences p = sp(c);
        try {
            org.json.JSONArray a = new org.json.JSONArray(p.getString(K_HISTORY, "[]"));
            org.json.JSONArray out = new org.json.JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optBoolean("m") != mail) out.put(o);
            }
            p.edit().putString(K_HISTORY, out.toString())
                    .putBoolean(mail ? "mail_opened" : "msg_opened", true)
                    .putBoolean(K_UNREAD, false).apply();
        } catch (Exception ignored) {
        }
    }

    /** Hold OK in the inbox: forget this one. */
    static void deleteFromHistory(Context c, Item gone) {
        SharedPreferences p = sp(c);
        try {
            org.json.JSONArray a = new org.json.JSONArray(p.getString(K_HISTORY, "[]"));
            org.json.JSONArray out = new org.json.JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optLong("t") == gone.time && o.optString("x").equals(gone.text)) continue;
                out.put(o);
            }
            SharedPreferences.Editor e = p.edit().putString(K_HISTORY, out.toString());
            // Also off the card / Play-Pause if it was the newest one.
            if (gone.text.equals(p.getString(gone.mail ? K_MAIL_TEXT : K_TEXT, null))) {
                e.remove(gone.mail ? K_MAIL_TEXT : K_TEXT).remove(gone.mail ? K_MAIL_TIME : K_TIME);
            }
            e.apply();
        } catch (Exception ignored) {
        }
    }

    private static final String K_TV_OFF = "tv_off_at";

    /** The TV just turned off: the next briefing tallies what came in from here on. */
    static void tvOff(Context c) {
        sp(c).edit().putLong(K_TV_OFF, System.currentTimeMillis()).apply();
    }

    /**
     * For the briefing: {texts, emails} you haven't seen yet (came in while the TV was off, not
     * opened since), since the TV last turned off.
     */
    static int[] missed(Context c, long now) {
        // Everything unseen since the TV last turned off (the inbox keeps up to 7 days).
        long since = sp(c).getLong(K_TV_OFF, now - 12 * 3600000L);
        int texts = 0, mails = 0;
        for (Item it : history(c)) {
            if (it.time < since || it.read) continue;
            if (it.mail) mails++;
            else texts++;
        }
        return new int[]{texts, mails};
    }

    /** Newest first. */
    static List<Item> history(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(sp(c).getString(K_HISTORY, "[]"));
            long old = System.currentTimeMillis() - HISTORY_DAYS_MS;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optLong("t") < old) continue;
                out.add(new Item(o.optString("x"), o.optBoolean("m"), o.optLong("t"), o.optBoolean("r", true)));
            }
        } catch (Exception ignored) {
        }
        if (out.isEmpty() && !sp(c).contains(K_HISTORY)) {
            // Before the inbox existed only the newest text and email were kept: show those.
            SharedPreferences p = sp(c);
            String m = p.getString(K_TEXT, null), e = p.getString(K_MAIL_TEXT, null);
            long mt = p.getLong(K_TIME, 0), et = p.getLong(K_MAIL_TIME, 0);
            if (m != null && !m.isEmpty()) out.add(new Item(m, false, mt, true));
            if (e != null && !e.isEmpty()) out.add(et > mt ? 0 : out.size(), new Item(e, true, et, true));
        }
        return out;
    }

    private static String lastShown;
    private static long lastShownAt;

    private static final int MAX_CHARS = 120;
    private static final java.util.regex.Pattern SIGN_OFF = java.util.regex.Pattern.compile(
            "(?i)^(best|thanks|thank you|thx|regards|kind regards|best regards|warm regards|sincerely|"
                    + "cheers|sent from|get outlook|--|__|on .+ wrote:|from:|-+ ?forwarded message).*");

    /**
     * Emails (sent to the topic's email address) arrive with line breaks, a sign-off and a
     * signature. Keep the first lines, stop at the sign-off, and cut long ones short.
     */
    static String tidy(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\r", "");
        if (s.indexOf('\n') < 0) return s.trim();
        StringBuilder b = new StringBuilder();
        for (String line : s.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (SIGN_OFF.matcher(line).matches()) break;
            if (line.startsWith(">")) continue;   // quoted reply
            if (b.length() > 0) b.append(' ');
            b.append(line);
            if (b.length() >= MAX_CHARS) break;
        }
        String out = b.toString().trim();
        if (out.length() > MAX_CHARS) {
            int cut = out.lastIndexOf(' ', MAX_CHARS);
            out = out.substring(0, cut > 40 ? cut : MAX_CHARS) + "...";
        }
        return out;
    }

    private Messages() {}
}
