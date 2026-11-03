package pt.inicio.launcher;

import android.service.dreams.DreamService;
import android.webkit.WebView;

/**
 * The screensaver (TV Settings > Screen saver > Wallpapers & verse): the home screen's photos, a big clock and the day's verse
 * (assets/web/dream.html). Any key wakes the TV, as with any screensaver.
 */
public class InicioDream extends DreamService {
    private WebView web;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        show();
    }

    private void show() {
        web = Web.create(this, new Assets(this), new Runnable() {
            @Override
            public void run() {
                WebView dead = web;
                web = null;
                dead.destroy();
                finish();
            }
        });
        setContentView(web);
        web.loadUrl(Web.ORIGIN + "/web/dream.html");
    }

    @Override
    public void onDetachedFromWindow() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDetachedFromWindow();
    }
}
