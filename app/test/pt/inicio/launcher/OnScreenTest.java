package pt.inicio.launcher;

import java.util.List;
import java.util.Map;

/**
 * Checks OnScreen with event sequences recorded from the TV's usage log, minutes as timestamps. Plain Java, no test
 * framework: python3 tools/test.py
 */
public final class OnScreenTest {
    private static final long MIN = 60_000L;
    private static int failures;

    public static void main(String[] args) {
        multiScreenApp();
        singleScreenApp();
        screenOff();
        clippedToMidnight();
        stillOnScreen();
        if (failures > 0) {
            System.out.println(failures + " failed");
            System.exit(1);
        }
        System.out.println("OnScreenTest: all passed");
    }

    /** The 0.17 bug: menu -> player in the same app. The menu's late "stopped" must not end the player's time. */
    private static void multiScreenApp() {
        OnScreen s = new OnScreen(0);
        s.resumed("app", "Menu", 0);
        s.left("app", "Menu", 5 * MIN);          // menu paused
        s.resumed("app", "Player", 5 * MIN + 100);
        s.left("app", "Menu", 5 * MIN + 600);    // menu stopped, after the player is already on screen
        s.left("app", "Player", 95 * MIN);
        s.resumed("app", "Menu", 95 * MIN + 100);
        s.left("app", "Menu", 96 * MIN);
        check("menu -> player -> menu counts the whole 96 minutes", minutes(s.until(100 * MIN), "app"), 96);
    }

    private static void singleScreenApp() {
        OnScreen s = new OnScreen(0);
        s.resumed("yt", "Main", 10 * MIN);
        s.left("yt", "Main", 70 * MIN);
        s.left("yt", "Main", 71 * MIN);          // stopped after paused: nothing more to close
        check("one screen, paused then stopped", minutes(s.until(100 * MIN), "yt"), 60);
    }

    private static void screenOff() {
        OnScreen s = new OnScreen(0);
        s.resumed("yt", "Main", 0);
        s.screenOff(30 * MIN);                   // TV turned off while playing
        check("screen off ends the time", minutes(s.until(100 * MIN), "yt"), 30);
    }

    private static void clippedToMidnight() {
        OnScreen s = new OnScreen(60 * MIN);     // "midnight" at minute 60
        s.resumed("yt", "Main", 0);
        s.left("yt", "Main", 90 * MIN);
        check("only time after midnight counts", minutes(s.until(100 * MIN), "yt"), 30);
    }

    private static void stillOnScreen() {
        OnScreen s = new OnScreen(0);
        s.resumed("tv", "Live", 40 * MIN);
        check("an app still on screen counts until now", minutes(s.until(100 * MIN), "tv"), 60);
    }

    private static long minutes(Map<String, List<long[]>> spans, String pkg) {
        long ms = 0;
        if (spans.containsKey(pkg)) for (long[] span : spans.get(pkg)) ms += span[1] - span[0];
        return Math.round((double) ms / MIN);
    }

    private static void check(String name, long got, long want) {
        System.out.println((got == want ? "ok    " : "FAIL  ") + name + (got == want ? "" : ": got " + got + ", want " + want));
        if (got != want) failures++;
    }
}
