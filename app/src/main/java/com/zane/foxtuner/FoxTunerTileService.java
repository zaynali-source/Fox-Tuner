package com.zane.foxtuner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * One Quick Settings tile that cycles through the modes selected in the app (More -> Quick Settings tile).
 * Uses root (su) to call the Fox Tuner Helper module's ptctl.sh. Every call is wrapped so it can never crash the tile.
 */
public class FoxTunerTileService extends TileService {
    static final String[] ALL = {"battery", "normal", "performance", "extreme"};
    static final String[] NAMES = {"Battery", "Normal", "Performance", "Extreme"};
    private static final String PREFS = "fox";
    private static final String KEY = "tile_modes";
    private static final String CTL = "/data/adb/modules/profiles_tuner/bin/ptctl.sh";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile String current = "normal";
    private volatile boolean listening = false;

    // ---- selection storage (shared with the app's bridge) ----
    static String getCsv(Context c) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return join(enabled(p.getString(KEY, "battery,normal,performance,extreme")));
        } catch (Exception e) {
            return "battery,normal,performance,extreme";
        }
    }

    static void saveCsv(Context c, String csv) {
        try {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, join(enabled(csv))).apply();
        } catch (Exception ignored) { }
    }

    private static List<String> enabled(String csv) {
        List<String> out = new ArrayList<>();
        String s = csv == null ? "" : "," + csv + ",";
        for (String m : ALL) if (s.contains("," + m + ",")) out.add(m);
        if (out.isEmpty()) for (String m : ALL) out.add(m);
        return out;
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String x : l) { if (b.length() > 0) b.append(','); b.append(x); }
        return b.toString();
    }

    private static String nameOf(String mode) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(mode)) return NAMES[i];
        return "Normal";
    }

    // ---- root helper ----
    private static String su(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            ByteArrayOutputStream ob = new ByteArrayOutputStream();
            InputStream in = p.getInputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) ob.write(buf, 0, n);
            if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroy(); return null; }
            return ob.toString("UTF-8").trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String readActive() {
        String a = su("cat /data/adb/profiles_tuner/active 2>/dev/null");
        if (a == null) return null;
        for (String m : ALL) if (m.equals(a)) return m;
        return "normal";
    }

    // ---- tile ----
    private void paint() {
        try {
            if (!listening) return;
            Tile t = getQsTile();
            if (t == null) return;
            t.setLabel(nameOf(current));
            if (Build.VERSION.SDK_INT >= 29) t.setSubtitle("Fox Tuner");
            t.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_fox));
            t.setState(Tile.STATE_ACTIVE);
            t.updateTile();
        } catch (Exception ignored) { }
    }

    @Override
    public void onStartListening() {
        listening = true;
        try { current = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("cur", current); } catch (Exception ignored) { }
        paint();
        IO.execute(() -> {
            String a = readActive();
            if (a != null) { current = a; remember(a); ui.post(this::paint); }
        });
    }

    @Override
    public void onStopListening() {
        listening = false;
    }

    @Override
    public void onClick() {
        final List<String> modes = enabled(getCsv(this));
        // instant feedback: show the next mode right away from the cached one, then apply with ONE root call
        final String cached = current;
        int i0 = modes.indexOf(cached);
        final String guess = modes.get((i0 + 1) % modes.size());
        current = guess;
        paint();
        IO.execute(() -> {
            try {
                String r = su("sh " + CTL + " cycle " + join(modes));
                String got = null;
                if (r != null) {
                    int k = r.lastIndexOf("ok ");
                    if (k >= 0) got = r.substring(k + 3).trim();
                }
                if (got != null) {
                    for (String m : ALL) if (m.equals(got)) { current = m; remember(m); }
                } else {
                    String a = readActive();
                    if (a != null) current = a;
                }
                ui.post(this::paint);
            } catch (Exception ignored) { }
        });
    }

    private void remember(String m) {
        try { getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("cur", m).apply(); } catch (Exception ignored) { }
    }
}
