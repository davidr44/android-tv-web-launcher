package pt.inicio.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;

/**
 * The home screen: CleanFrame's web UI (assets/web/index.html) in a full-screen WebView (see Web). The page talks to
 * the TV through Bridge; HDMI signal changes are pushed to it as they happen.
 */
public class HomeActivity extends Activity {
    private WebView web;
    private Assets assets;
    private Bridge bridge;
    private TvInputManager tvInputs;
    private AudioManager audio;
    private boolean visible;
    /** OK or Back was pressed here (not in the app we just left), so its release counts. */
    private boolean keyDownHere;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** A device on an HDMI port turned on or off (or HDMI-CEC added its input): tell the page. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String inputId, int state) {
            pushInputs();
        }

        @Override
        public void onInputAdded(String inputId) {
            pushInputs();
        }

        @Override
        public void onInputRemoved(String inputId) {
            pushInputs();
        }
    };

    /**
     * Logs HDMI signal changes for "Watched today" (WatchTime) for as long as the home screen is alive, not only while
     * it's on screen: the PS5 turns on and off while another app is showing.
     */
    private final TvInputManager.TvInputCallback watchCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String inputId, int state) {
            WatchTime.hdmiChanged(HomeActivity.this, bridge.hdmiStateObject());
        }

        @Override
        public void onInputAdded(String inputId) {
            WatchTime.hdmiChanged(HomeActivity.this, bridge.hdmiStateObject());
        }

        @Override
        public void onInputRemoved(String inputId) {
            WatchTime.hdmiChanged(HomeActivity.this, bridge.hdmiStateObject());
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.getDecorView().setBackgroundColor(Color.BLACK);
        assets = new Assets(this);
        assets.warm("com.google.android.youtube.tv", "com.sony.dtv.tvx");
        bridge = new Bridge(this);
        tvInputs = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (tvInputs != null) tvInputs.registerCallback(watchCallback, main);
        WatchTime.hdmiChanged(this, bridge.hdmiStateObject());
        saveVerseKey(getIntent());
        show();
    }

    /**
     * The YouVersion app key, given once with adb so it stays on the TV and out of the code:
     * adb shell am start -n pt.inicio.launcher/.HomeActivity --es yvp_key KEY   (an empty key removes it)
     */
    private void saveVerseKey(Intent intent) {
        if (intent != null && intent.hasExtra("yvp_key")) VerseOfTheDay.setKey(this, intent.getStringExtra("yvp_key"));
    }

    @Override
    protected void onDestroy() {
        if (tvInputs != null) tvInputs.unregisterCallback(watchCallback);
        super.onDestroy();
    }

    private void show() {
        web = Web.create(this, assets, new Runnable() {
            @Override
            public void run() {
                // The renderer died: start the page again instead of crashing.
                WebView dead = web;
                setContentView(new View(HomeActivity.this));
                dead.destroy();
                show();
            }
        });
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.addJavascriptInterface(bridge, "AndroidTV");
        setContentView(web);
        web.loadUrl(Web.ORIGIN + "/web/index.html");
        web.requestFocus();
    }

    /** Home pressed while already home: back to the dock, like any launcher. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        saveVerseKey(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) js("ShoreQA.scene('home')");
    }

    /**
     * Back goes to the page (closes panels); the launcher itself never closes. OK acts when it's released, like any
     * Android button: acting on the press opened the app with the button still down, and the app took the release as
     * a click of its own (TizenTube started its first video). A release whose press happened inside another app does
     * nothing.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            boolean ok = key != KeyEvent.KEYCODE_BACK;
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                keyDownHere = true;
                if (ok) clickSound();
            }
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (keyDownHere && !e.isCanceled()) js(ok ? "ShoreQA.nav('ok')" : "ShoreQA.nav('back')");
                keyDownHere = false;
            }
            return true;
        }
        // The arrows' sounds come from the page (Bridge.sound), which knows whether the focus actually moved.
        return super.dispatchKeyEvent(e);
    }

    /** OK's click: the TV's own sound, silent when sound effects are off in the TV's settings. */
    private void clickSound() {
        if (audio != null) audio.playSoundEffect(AudioManager.FX_KEY_CLICK);
    }

    /**
     * Android keeps the home screen alive ahead of other apps, so when the TV runs short of memory while another app
     * is on screen, give up the page (its renderer is ~120 MB) and build it again on the way back (onStart).
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (!visible && web != null && level >= TRIM_MEMORY_BACKGROUND) {
            WebView old = web;
            web = null;
            setContentView(new View(this));
            old.destroy();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        if (web == null) show();
    }

    @Override
    protected void onStop() {
        visible = false;
        keyDownHere = false;
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tvInputs != null) tvInputs.registerCallback(inputCallback, main);
        VerseOfTheDay.refresh(this);
        if (web != null) {
            web.onResume();
            web.requestFocus();
        }
        pushInputs();
        // Back from an app: today's watch time and the verse now, not at the next minute.
        js("InicioRefresh && InicioRefresh()");
    }

    @Override
    protected void onPause() {
        if (tvInputs != null) tvInputs.unregisterCallback(inputCallback);
        if (web != null) web.onPause();
        super.onPause();
    }

    private void pushInputs() {
        js("InicioInputs && InicioInputs(" + bridge.hdmiStates() + ")");
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript("window.ShoreQA && window." + code, null);
    }
}
