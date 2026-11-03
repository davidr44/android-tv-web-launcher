package pt.inicio.launcher;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * "Watched today": time since local midnight with a video app on screen, from Android's own usage log, per dock entry.
 * Sony's TV app shows both the antenna and the HDMI inputs, so its time is split with a log of when each HDMI port had
 * a signal (kept here, from HomeActivity's input callback): the part while the PS5's port was live counts as PS5.
 */
final class WatchTime {
    private WatchTime() {}

    /** Sony's TV app: the antenna channels and the HDMI inputs. Counted as "tv", or "ps5" while the PS5's port is live. */
    static final String TV_APP = "com.sony.dtv.tvx";
    private static final long DAY = 24 * 3600_000L;

    /** Whether the app may read the usage log (adb shell appops set pt.inicio.launcher GET_USAGE_STATS allow). */
    static boolean allowed(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(),
                c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * {"total": minutes, "apps": {"youtube": minutes, ...}}, or null without permission. counted: package -> dock entry
     * id for the apps that count (web/apps.js), besides the TV app. ps5Input: "input.hdmiN".
     */
    static JSONObject today(Context c, Map<String, String> counted, String ps5Input) {
        UsageStatsManager usage = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usage == null || !allowed(c)) return null;
        long now = System.currentTimeMillis(), midnight = midnight();
        Map<String, List<long[]>> spans = onScreen(usage, counted, midnight, now);
        Map<String, Long> ms = new HashMap<>();
        long total = 0;
        for (Map.Entry<String, List<long[]>> e : spans.entrySet()) {
            for (long[] s : e.getValue()) {
                long len = s[1] - s[0];
                total += len;
                if (e.getKey().equals(TV_APP)) {
                    long ps5 = overlap(s, hdmiSpans(c, ps5Input, midnight, now));
                    add(ms, "ps5", ps5);
                    add(ms, "tv", len - ps5);
                } else {
                    add(ms, counted.get(e.getKey()), len);
                }
            }
        }
        try {
            JSONObject apps = new JSONObject();
            for (Map.Entry<String, Long> e : ms.entrySet()) apps.put(e.getKey(), e.getValue() / 60_000);
            return new JSONObject().put("total", total / 60_000).put("apps", apps);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Each counted app's time on screen today (OnScreen), from Android's usage log. */
    private static Map<String, List<long[]>> onScreen(UsageStatsManager usage, Map<String, String> counted, long midnight,
            long now) {
        OnScreen screen = new OnScreen(midnight);
        // From a few hours before midnight, so an app already on screen at midnight counts from 0:00.
        UsageEvents events = usage.queryEvents(midnight - 6 * 3600_000L, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.getNextEvent(e)) {
            int type = e.getEventType();
            String pkg = e.getPackageName();
            if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
                screen.screenOff(e.getTimeStamp());
            } else if (counted.containsKey(pkg) || pkg.equals(TV_APP)) {
                if (type == UsageEvents.Event.ACTIVITY_RESUMED) screen.resumed(pkg, e.getClassName(), e.getTimeStamp());
                else if (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED)
                    screen.left(pkg, e.getClassName(), e.getTimeStamp());
            }
        }
        return screen.until(now);
    }

    // MARK: - HDMI signal log (SharedPreferences "watch", key "hdmi": {"input.hdmiN": {"open": t, "spans": [[s, e]]}})

    /** Records which HDMI ports have a signal now (Bridge.hdmiStates), opening and closing spans. */
    static synchronized void hdmiChanged(Context c, JSONObject states) {
        SharedPreferences prefs = c.getSharedPreferences("watch", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        try {
            JSONObject log = new JSONObject(prefs.getString("hdmi", "{}"));
            Iterator<String> ports = states.keys();
            while (ports.hasNext()) {
                String port = ports.next();
                JSONObject p = log.optJSONObject(port);
                if (p == null) log.put(port, p = new JSONObject().put("open", 0).put("spans", new JSONArray()));
                long open = p.optLong("open");
                boolean live = states.optBoolean(port);
                if (live && open == 0) p.put("open", now);
                if (!live && open > 0) {
                    p.getJSONArray("spans").put(new JSONArray().put(open).put(now));
                    p.put("open", 0);
                }
                // Keep two days.
                JSONArray spans = p.getJSONArray("spans"), kept = new JSONArray();
                for (int i = 0; i < spans.length(); i++) {
                    if (spans.getJSONArray(i).getLong(1) > now - 2 * DAY) kept.put(spans.getJSONArray(i));
                }
                p.put("spans", kept);
            }
            prefs.edit().putString("hdmi", log.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    /** The port's signal spans that touch today (an open one runs to now). */
    private static synchronized List<long[]> hdmiSpans(Context c, String port, long midnight, long now) {
        List<long[]> out = new ArrayList<>();
        try {
            JSONObject p = new JSONObject(c.getSharedPreferences("watch", Context.MODE_PRIVATE).getString("hdmi", "{}"))
                    .optJSONObject(port);
            if (p == null) return out;
            JSONArray spans = p.getJSONArray("spans");
            for (int i = 0; i < spans.length(); i++) {
                long s = spans.getJSONArray(i).getLong(0), e = spans.getJSONArray(i).getLong(1);
                if (e > midnight) out.add(new long[] {Math.max(s, midnight), e});
            }
            if (p.optLong("open") > 0) out.add(new long[] {Math.max(p.optLong("open"), midnight), now});
        } catch (JSONException ignored) {
        }
        return out;
    }

    private static long overlap(long[] span, List<long[]> others) {
        long sum = 0;
        for (long[] o : others) sum += Math.max(0, Math.min(span[1], o[1]) - Math.max(span[0], o[0]));
        return Math.min(sum, span[1] - span[0]);
    }

    private static void add(Map<String, Long> ms, String key, long value) {
        if (key == null || value <= 0) return;
        Long old = ms.get(key);
        ms.put(key, (old == null ? 0 : old) + value);
    }

    private static long midnight() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
