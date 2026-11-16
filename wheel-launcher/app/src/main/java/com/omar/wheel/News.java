package com.omar.wheel;

import android.os.Handler;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Top headlines from the New York Times' public RSS feed (no key), for idle mode's ticker. */
final class News {

    interface Callback {
        /** Headlines, already cleaned for the dot font. null if the fetch failed. */
        void onNews(List<String> headlines);
    }

    private static final String FEED = "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml";
    private static final int MAX = 12;

    static void fetch(final Handler ui, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> out = null;
                try {
                    out = load();
                } catch (Exception ignored) {
                }
                final List<String> res = out;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onNews(res);
                    }
                });
            }
        }, "wheel-news").start();
    }

    private static List<String> load() throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(FEED).openConnection();
        try {
            con.setConnectTimeout(8000);
            con.setReadTimeout(12000);
            InputStream in = con.getInputStream();
            XmlPullParser x = Xml.newPullParser();
            x.setInput(in, "UTF-8");
            List<String> out = new ArrayList<>();
            boolean inItem = false;
            for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT && out.size() < MAX; ev = x.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    if ("item".equals(x.getName())) inItem = true;
                    else if (inItem && "title".equals(x.getName())) {
                        String t = DotFont.clean(plain(x.nextText()));
                        if (!t.isEmpty()) out.add(t);
                    }
                } else if (ev == XmlPullParser.END_TAG && "item".equals(x.getName())) {
                    inItem = false;
                }
            }
            in.close();
            return out.isEmpty() ? null : out;
        } finally {
            con.disconnect();
        }
    }

    /** Curly quotes and long dashes to the plain ones the dot font has. */
    private static String plain(String s) {
        return s.replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .replace("…", "...");
    }

    private News() {}
}
