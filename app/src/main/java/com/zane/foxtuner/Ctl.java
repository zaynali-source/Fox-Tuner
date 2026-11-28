package com.zane.foxtuner;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Talks to the Fox Tuner Helper module (ptctl.sh) through su. One serialized queue, writes are batched. */
public class Ctl {
    public static final String CTL = "sh /data/adb/modules/profiles_tuner/bin/ptctl.sh";
    public static final String DIR = "/data/adb/profiles_tuner";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public interface Done { void run(String out); }

    public final Map<String, String> S = new HashMap<>();
    private final Map<String, String> pend = new LinkedHashMap<>();
    private boolean flushPosted = false;
    public volatile boolean ready = false;

    public static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }

    public static String su(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            InputStream in = p.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            p.waitFor();
            return bo.toString("UTF-8");
        } catch (Throwable t) { return ""; }
    }

    /** run a shell command on the queue; result delivered on the UI thread */
    public void exec(final String cmd, final Done d) {
        IO.execute(new Runnable() { public void run() {
            final String o = su(cmd);
            if (d != null) UI.post(new Runnable() { public void run() { d.run(o); } });
        }});
    }

    public void ctl(Done d, String... a) {
        StringBuilder sb = new StringBuilder(CTL);
        for (String x : a) sb.append(' ').append(q(x));
        exec(sb.toString(), d);
    }

    public void load(final Done d) {
        flush();
        exec(CTL + " get", new Done() { public void run(String out) {
            if (out != null && out.trim().length() > 0) {
                for (String l : out.split("\n")) {
                    int i = l.indexOf('=');
                    if (i > 0) S.put(l.substring(0, i), l.substring(i + 1).trim());
                }
                ready = true;
            }
            if (d != null) d.run(out);
        }});
    }

    public String g(String k, String def) { String v = S.get(k); return v == null ? def : v; }
    public String g(String k) { return g(k, ""); }
    public boolean on(String k, boolean def) { return g(k, def ? "1" : "0").equals("1"); }

    /** set a profile value; applied to the device after a short debounce, in one shell call */
    public void set(String k, String v) {
        S.put(k, v);
        pend.put(k, v);
        if (!flushPosted) { flushPosted = true; UI.postDelayed(new Runnable() { public void run() { flush(); } }, 120); }
    }

    public void flush() {
        flushPosted = false;
        if (pend.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : pend.entrySet()) sb.append(CTL).append(" setq ").append(q(e.getKey())).append(' ').append(q(e.getValue())).append("; ");
        sb.append(CTL).append(" apply");
        pend.clear();
        exec(sb.toString(), null);
    }

    public static Map<String, String> parse(String t) {
        Map<String, String> m = new HashMap<>();
        if (t == null) return m;
        for (String l : t.split("\n")) { int i = l.indexOf('='); if (i > 0) m.put(l.substring(0, i), l.substring(i + 1).trim()); }
        return m;
    }

    public static List<Integer> nums(String s) {
        List<Integer> r = new ArrayList<>();
        if (s == null) return r;
        for (String x : s.trim().split("\\s+")) { try { if (!x.isEmpty()) r.add(Integer.parseInt(x)); } catch (Throwable ignored) { } }
        java.util.Collections.sort(r);
        return r;
    }
}
