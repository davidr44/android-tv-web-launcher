package pt.inicio.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * window.AndroidTV in the page (device.js). Each call returns "" when it worked, or the reason it didn't.
 * App ids: an Android package, "input.tv" (live TV) or "input.hdmiN" (HDMI port N).
 */
final class Bridge {
    private static final String TAG = "Inicio";
    /** The TV's original home screen (Android TV), for Settings > TV > Original Home. */
    private static final ComponentName ORIGINAL_HOME =
            new ComponentName("com.google.android.tvlauncher", "com.google.android.tvlauncher.MainActivity");
    /** Sony's live TV app: the last resort when no tuner input answers. */
    private static final String LIVE_TV_APP = "com.sony.dtv.tvx";

    private final Activity activity;

    Bridge(Activity activity) {
        this.activity = activity;
    }

    /** The TV apps (the ones with a TV launcher entry), as JSON [{appId, name, icon}]. */
    @JavascriptInterface
    public String apps() {
        PackageManager pm = activity.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        JSONArray out = new JSONArray();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(main, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(activity.getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            try {
                out.put(new JSONObject()
                        .put("appId", pkg)
                        .put("name", String.valueOf(r.loadLabel(pm)).trim())
                        .put("icon", "/icon/" + pkg + ".png"));
            } catch (JSONException ignored) {
            }
        }
        return out.toString();
    }

    @JavascriptInterface
    public String launch(String id) {
        Intent intent;
        if ("input.tv".equals(id)) {
            intent = liveTv();
        } else if (id != null && id.startsWith("input.hdmi")) {
            String input = hdmiInput(id.substring("input.hdmi".length()));
            if (input == null) return "HDMI " + id.substring("input.hdmi".length()) + " was not found.";
            intent = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input));
        } else {
            PackageManager pm = activity.getPackageManager();
            intent = pm.getLeanbackLaunchIntentForPackage(id);
            if (intent == null) intent = pm.getLaunchIntentForPackage(id);
            if (intent == null) return "This app is not installed.";
        }
        if (intent == null) return "Live TV was not found.";
        return start(intent);
    }

    /**
     * The TV's navigation sound for a move in that direction (left, right, up, down), played by the page only when the
     * focus moved. Silent when sound effects are off in the TV's settings.
     */
    @JavascriptInterface
    public void sound(String direction) {
        AudioManager audio = (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE);
        if (audio == null) return;
        int fx = "left".equals(direction) ? AudioManager.FX_FOCUS_NAVIGATION_LEFT
                : "right".equals(direction) ? AudioManager.FX_FOCUS_NAVIGATION_RIGHT
                : "up".equals(direction) ? AudioManager.FX_FOCUS_NAVIGATION_UP
                : AudioManager.FX_FOCUS_NAVIGATION_DOWN;
        audio.playSoundEffect(fx);
    }

    /**
     * Today's time in the video apps: {"total": minutes, "apps": {...}} (WatchTime), or "" without permission.
     * counted: {"package": "dock entry id"} for the apps that count (web/apps.js).
     */
    @JavascriptInterface
    public String watchToday(String counted, String ps5Input) {
        Map<String, String> apps = new HashMap<>();
        try {
            JSONObject json = new JSONObject(counted);
            Iterator<String> packages = json.keys();
            while (packages.hasNext()) {
                String pkg = packages.next();
                apps.put(pkg, json.getString(pkg));
            }
        } catch (JSONException e) {
            return "";
        }
        JSONObject today = WatchTime.today(activity, apps, ps5Input);
        return today != null ? today.toString() : "";
    }

    /** YouVersion's verse of the day, {"ref", "text"} (VerseOfTheDay), or "" until it's here. */
    @JavascriptInterface
    public String verseToday() {
        return VerseOfTheDay.today(activity);
    }

    @JavascriptInterface
    public String settings() {
        return start(new Intent(Settings.ACTION_SETTINGS));
    }

    @JavascriptInterface
    public String originalHome() {
        try {
            int state = activity.getPackageManager().getApplicationEnabledSetting(ORIGINAL_HOME.getPackageName());
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                return "The original Home screen is turned off. To bring it back: TV Settings > Apps > See all apps > "
                        + "Show system apps > Android TV Home > Enable.";
            }
        } catch (IllegalArgumentException e) {
            return "The original Home screen is not on this TV.";
        }
        return start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(ORIGINAL_HOME));
    }

    private String start(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            activity.startActivity(intent);
            return "";
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "couldn't open " + intent + ": " + e);
            return e.getMessage() != null ? e.getMessage() : e.toString();
        }
    }

    /**
     * Live TV on the antenna/cable tuner. Sony's TV app otherwise reopens the last source, which can be an HDMI port.
     * The terrestrial/cable tuner comes first; then any hardware tuner; then the TV app as it is.
     */
    private Intent liveTv() {
        PackageManager pm = activity.getPackageManager();
        String tuner = null;
        for (TvInputInfo info : inputs()) {
            if (info.getType() != TvInputInfo.TYPE_TUNER || info.isPassthroughInput() || !isSystem(info)) continue;
            if (info.getId().contains("TerrCable")) {
                tuner = info.getId();
                break;
            }
            if (tuner == null && info.getId().contains("/HW")) tuner = info.getId();
        }
        if (tuner != null) {
            Intent channels = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(tuner));
            if (channels.resolveActivity(pm) != null) return channels;
        }
        return pm.getLeanbackLaunchIntentForPackage(LIVE_TV_APP);
    }

    /** Which HDMI ports have a device on and sending a picture, as JSON {"input.hdmiN": true|false}. */
    @JavascriptInterface
    public String hdmiStates() {
        return hdmiStateObject().toString();
    }

    JSONObject hdmiStateObject() {
        TvInputManager tim = (TvInputManager) activity.getSystemService(Activity.TV_INPUT_SERVICE);
        List<TvInputInfo> all = inputs();
        JSONObject out = new JSONObject();
        for (Map.Entry<String, TvInputInfo> port : hdmiPorts(all).entrySet()) {
            String id = port.getValue().getId();
            // The port itself, or the per-device input HDMI-CEC adds under it ("PlayStation 5").
            boolean live = connected(tim, id);
            for (TvInputInfo info : all) {
                if (id.equals(info.getParentId()) && connected(tim, info.getId())) live = true;
            }
            try {
                out.put("input.hdmi" + port.getKey(), live);
            } catch (JSONException ignored) {
            }
        }
        return out;
    }

    private static boolean connected(TvInputManager tim, String id) {
        try {
            return tim != null && tim.getInputState(id) == TvInputManager.INPUT_STATE_CONNECTED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String hdmiInput(String number) {
        TvInputInfo info = hdmiPorts(inputs()).get(number);
        return info != null ? info.getId() : null;
    }

    /**
     * The HDMI ports (not the per-device inputs HDMI-CEC adds) by number: the number in their label, else their place
     * in id order (on this Bravia, HDMI 1-4 are .../HW2 to .../HW5).
     */
    private Map<String, TvInputInfo> hdmiPorts(List<TvInputInfo> all) {
        List<TvInputInfo> ports = new ArrayList<>();
        for (TvInputInfo info : all) {
            if (info.getType() == TvInputInfo.TYPE_HDMI && info.getParentId() == null) ports.add(info);
        }
        Collections.sort(ports, new Comparator<TvInputInfo>() {
            @Override
            public int compare(TvInputInfo a, TvInputInfo b) {
                return a.getId().compareTo(b.getId());
            }
        });
        Pattern digit = Pattern.compile("(\\d)");
        Map<String, TvInputInfo> out = new LinkedHashMap<>();
        for (int i = 0; i < ports.size(); i++) {
            Matcher m = digit.matcher(String.valueOf(ports.get(i).loadLabel(activity)));
            String number = m.find() ? m.group(1) : String.valueOf(i + 1);
            if (!out.containsKey(number)) out.put(number, ports.get(i));
        }
        return out;
    }

    private List<TvInputInfo> inputs() {
        TvInputManager tim = (TvInputManager) activity.getSystemService(Activity.TV_INPUT_SERVICE);
        try {
            if (tim != null) return tim.getTvInputList();
        } catch (RuntimeException e) {
            Log.w(TAG, "couldn't list TV inputs: " + e);
        }
        return new ArrayList<>();
    }

    private static boolean isSystem(TvInputInfo info) {
        return info.getServiceInfo() != null && info.getServiceInfo().applicationInfo != null
                && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }
}
