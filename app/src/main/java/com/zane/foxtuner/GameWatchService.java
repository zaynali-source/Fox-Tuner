package com.zane.foxtuner;

import android.accessibilityservice.AccessibilityService;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Event-driven foreground detection for Game Space (no polling). Only the package name of the front window is used. */
public class GameWatchService extends AccessibilityService {
    private static final String CTL = "/data/adb/modules/profiles_tuner/bin/ptctl.sh";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private String last = "";

    private static void su(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            p.getInputStream().readAllBytes();
            p.waitFor();
        } catch (Throwable ignored) { }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        IO.execute(() -> su("sh " + CTL + " game-evt 1"));
    }

    @Override public boolean onUnbind(android.content.Intent i) {
        IO.execute(() -> su("sh " + CTL + " game-evt 0"));
        return super.onUnbind(i);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        final String pkg = e.getPackageName().toString();
        if (pkg.equals(last) || pkg.equals("com.android.systemui") || pkg.equals("android")) return;
        String ime = "";
        try {
            String d = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (d != null && d.contains("/")) ime = d.substring(0, d.indexOf('/'));
        } catch (Throwable ignored) { }
        if (pkg.equals(ime) || !pkg.matches("[A-Za-z0-9._]+")) return;
        last = pkg;
        IO.execute(() -> su("sh " + CTL + " game-fg " + pkg));
    }

    @Override public void onInterrupt() { }
}
