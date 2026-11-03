package pt.inicio.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * YouVersion's Verse of the Day (the same one as the Bible app and bible.com), in the Berean Standard Bible: the
 * day's passage from the official YouVersion Platform API, fetched once a day in the background and kept. Needs a free
 * app key from platform.youversion.com, stored on the TV only (HomeActivity, extra "yvp_key"); without a key or
 * internet the page keeps its own offline list.
 */
final class VerseOfTheDay {
    private VerseOfTheDay() {}

    private static final String TAG = "Inicio";
    private static final String API = "https://api.youversion.com/v1";
    /** Berean Standard Bible: license-free, and the default of YouVersion's own SDKs. */
    private static final int BSB = 3034;
    /** At most one try per this long when a fetch fails (no internet, API down). */
    private static final long RETRY_MS = 30 * 60_000L;
    private static boolean fetching;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("verse", Context.MODE_PRIVATE);
    }

    /** Saves the app key (empty removes it) and fetches today's verse again with it. */
    static void setKey(Context c, String key) {
        prefs(c).edit().putString("key", key == null ? "" : key.trim()).remove("date").remove("tried").apply();
        refresh(c);
    }

    /** Today's verse as JSON {"ref", "text"}, or "" when there is none yet; starts a fetch when it's out of date. */
    static String today(Context c) {
        SharedPreferences p = prefs(c);
        if (!today().equals(p.getString("date", ""))) {
            refresh(c);
            return "";
        }
        try {
            return new JSONObject().put("ref", p.getString("ref", "")).put("text", p.getString("text", "")).toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Fetches today's verse in the background unless it's already here, being fetched, or failed a moment ago. */
    static synchronized void refresh(Context context) {
        final Context c = context.getApplicationContext();
        final SharedPreferences p = prefs(c);
        final String key = p.getString("key", ""), date = today();
        if (key.isEmpty() || fetching || date.equals(p.getString("date", ""))) return;
        if (System.currentTimeMillis() - p.getLong("tried", 0) < RETRY_MS) return;
        fetching = true;
        p.edit().putLong("tried", System.currentTimeMillis()).apply();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
                    String passage = get(API + "/verse_of_the_days/" + day, key).getString("passage_id");
                    JSONObject text = get(API + "/bibles/" + BSB + "/passages/" + passage + "?format=text", key);
                    p.edit().putString("date", date)
                            .putString("ref", text.getString("reference").trim())
                            .putString("text", text.getString("content").replaceAll("\\s+", " ").trim())
                            .apply();
                } catch (Exception e) {
                    Log.w(TAG, "verse of the day: " + e);
                } finally {
                    synchronized (VerseOfTheDay.class) {
                        fetching = false;
                    }
                }
            }
        }, "verse").start();
    }

    private static JSONObject get(String url, String key) throws IOException, org.json.JSONException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setConnectTimeout(10_000);
        http.setReadTimeout(10_000);
        http.setRequestProperty("Accept", "application/json");
        http.setRequestProperty("X-YVP-App-Key", key);
        try {
            int status = http.getResponseCode();
            if (status != 200) throw new IOException(url.replace(API, "") + " returned HTTP " + status);
            try (InputStream in = http.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            http.disconnect();
        }
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }
}
