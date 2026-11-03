package pt.inicio.launcher;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * A WebView for our pages (the home screen and the screensaver), served from
 * https://appassets.androidplatform.net (a reserved name that never reaches the network, see Assets) so they get a
 * real origin: localStorage keeps the settings, shared by both, and the CSS icon masks load.
 */
final class Web {
    static final String ORIGIN = "https://appassets.androidplatform.net";

    private Web() {}

    /** onGone runs when the page's renderer died (out of memory, a WebView update); the view is then unusable. */
    static WebView create(android.content.Context context, final Assets assets, final Runnable onGone) {
        WebView web = new WebView(context);
        web.setBackgroundColor(Color.BLACK);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        // The pages are a fixed 1920x1080 stage: a 1920 px wide viewport (in the HTML) zoomed to fit the screen, and
        // text at 100% whatever the TV's font size setting, so the stage's own scaling stays at 1.
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setTextZoom(100);
        // chrome://inspect on the computer (over adb) shows the page, for fixing the UI on the TV itself.
        WebView.setWebContentsDebuggingEnabled(true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.i("Inicio", "page: " + m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assets.open(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith(ORIGIN + "/");
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                onGone.run();
                return true;
            }
        });
        return web;
    }
}
