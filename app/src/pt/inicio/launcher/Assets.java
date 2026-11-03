package pt.inicio.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the page loads: /web/... from the APK's assets, and /icon/<package>.png, the app's own icon. Anything else
 * (and any other host) gets a 404, so the page never touches the network.
 */
final class Assets {
    private static final Map<String, String> TYPES = new HashMap<>();
    static {
        TYPES.put("html", "text/html");
        TYPES.put("js", "text/javascript");
        TYPES.put("css", "text/css");
        TYPES.put("json", "application/json");
        TYPES.put("svg", "image/svg+xml");
        TYPES.put("jpg", "image/jpeg");
        TYPES.put("png", "image/png");
        TYPES.put("webp", "image/webp");
        TYPES.put("woff2", "font/woff2");
        TYPES.put("ttf", "font/ttf");
    }

    private final Context context;
    private final Map<String, byte[]> icons = new HashMap<>();

    Assets(Context context) {
        this.context = context.getApplicationContext();
    }

    WebResourceResponse open(Uri uri) {
        String path = uri.getPath();
        if (!Web.ORIGIN.equals(uri.getScheme() + "://" + uri.getHost()) || path == null || path.contains("..")) {
            return missing();
        }
        try {
            if (path.startsWith("/web/")) {
                InputStream in = context.getAssets().open(path.substring(1));
                return response(type(path), in);
            }
            if (path.startsWith("/icon/") && path.endsWith(".png")) {
                byte[] png = icon(path.substring(6, path.length() - 4));
                if (png != null) return response("image/png", new ByteArrayInputStream(png));
            }
        } catch (IOException ignored) {
        }
        return missing();
    }

    private static WebResourceResponse response(String type, InputStream in) {
        String charset = type.startsWith("text/") || type.endsWith("json") || type.endsWith("svg+xml") ? "utf-8" : null;
        return new WebResourceResponse(type, charset, in);
    }

    private static WebResourceResponse missing() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null,
                new ByteArrayInputStream(new byte[0]));
    }

    private static String type(String path) {
        String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        String t = TYPES.get(ext);
        return t != null ? t : "application/octet-stream";
    }

    /**
     * Draws every TV app's icon in the background, so All apps opens with them ready. extra: packages the page asks
     * for that aren't in the TV's app list (the official YouTube's icon for TizenTube, the live TV app's).
     */
    void warm(final String... extra) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = context.getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                for (ResolveInfo r : pm.queryIntentActivities(main, 0)) icon(r.activityInfo.packageName);
                for (String pkg : extra) icon(pkg);
            }
        }, "icons");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** The TV app's icon as a 192 px PNG, drawn once and kept. */
    private synchronized byte[] icon(String pkg) {
        if (icons.containsKey(pkg)) return icons.get(pkg);
        byte[] png = null;
        PackageManager pm = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(pkg);
        List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
        Drawable d = null;
        try {
            d = found.isEmpty() ? pm.getApplicationIcon(pkg) : found.get(0).loadIcon(pm);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        if (d != null) {
            Bitmap b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 192, 192);
            d.draw(new Canvas(b));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            b.recycle();
            png = out.toByteArray();
        }
        icons.put(pkg, png);
        return png;
    }
}
