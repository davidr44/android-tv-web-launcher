package pt.inicio.launcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns Android's usage events into each app's time on screen, as [start, end] spans clipped to [from, now]. Plain Java
 * (no Android classes) so it can be tested on a PC: see app/test.
 *
 * Spans are kept per activity, not per app: going from an app's menu to its player logs "menu paused, player resumed,
 * menu stopped", and the menu's stop must not end the player's span (a multi-screen video app lost all its playback
 * time that way, until 0.17).
 */
final class OnScreen {
    private final long from;
    private final Map<String, List<long[]>> spans = new HashMap<>();
    private final Map<String, Long> open = new HashMap<>();  // "package/activity" -> resumed at

    OnScreen(long from) {
        this.from = from;
    }

    void resumed(String pkg, String activity, long t) {
        String key = pkg + "/" + activity;
        if (!open.containsKey(key)) open.put(key, t);
    }

    /** Paused or stopped: the activity left the screen. */
    void left(String pkg, String activity, long t) {
        Long start = open.remove(pkg + "/" + activity);
        if (start != null) span(pkg, start, t);
    }

    /** The screen went off or the TV shut down: nothing is on screen any more. */
    void screenOff(long t) {
        for (Map.Entry<String, Long> o : open.entrySet()) span(o.getKey().substring(0, o.getKey().indexOf('/')), o.getValue(), t);
        open.clear();
    }

    /** Package -> spans, with the activities still on screen running to now. */
    Map<String, List<long[]>> until(long now) {
        screenOff(now);
        return spans;
    }

    private void span(String pkg, long start, long end) {
        start = Math.max(start, from);
        if (end <= start) return;
        List<long[]> list = spans.get(pkg);
        if (list == null) spans.put(pkg, list = new ArrayList<>());
        list.add(new long[] {start, end});
    }
}
