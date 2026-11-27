package com.zane.foxtuner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GameBar: real-time performance overlay (FPS, CPU, GPU, RAM, battery) drawn as an accessibility overlay window
 * (no overlay permission needed). Sampling runs ONLY while the bar is visible and the screen is on, using one
 * persistent root shell, so nothing runs when it is off.
 */
public class GameBarOverlay {
    static final String PREFS = "gamebar";
    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread ht;
    private Handler bg;
    private TextView view;
    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private boolean screenOn = true, shown = false, running = false;
    private String fg = "";
    private Process sh;
    private OutputStream shIn;
    private BufferedReader shOut;

    // discovered paths (on the sampler thread)
    private String cpuTemp, gpuTemp, layer, layerFor = "";
    private boolean discovered;
    private long pIdle = -1, pTotal = -1;

    GameBarOverlay(Context c) { ctx = c; }

    SharedPreferences p() { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    String s(String k, String d) { return p().getString(k, d); }
    boolean b(String k, boolean d) { return "1".equals(s(k, d ? "1" : "0")); }
    int i(String k, int d) { try { return Integer.parseInt(s(k, "" + d)); } catch (Throwable t) { return d; } }

    void setScreen(boolean on) { screenOn = on; main.post(this::update); }
    void setFg(String pkg) { fg = pkg; main.post(this::update); }
    void refresh() { main.post(() -> { if (shown) { hide(); } update(); }); }
    void destroy() { main.post(this::hide); }

    private boolean wanted() {
        if (!b("on", false) || !screenOn) return false;
        if ("games".equals(s("scope", "always"))) {
            Set<String> g = new HashSet<>();
            for (String x : s("apps", "").split(",")) if (!x.isEmpty()) g.add(x);
            return g.contains(fg);
        }
        return true;
    }

    private void update() {
        boolean w = wanted();
        if (w && !shown) show();
        else if (!w && shown) hide();
    }

    private void show() {
        try {
            wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            view = new TextView(ctx);
            view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            view.setTextSize(i("size", 11));
            view.setTextColor(parse(s("color", "#FFFFFF")));
            int pad = (int) (6 * ctx.getResources().getDisplayMetrics().density);
            view.setPadding(pad * 2, pad, pad * 2, pad);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.argb(Math.max(0, Math.min(255, i("alpha", 60) * 255 / 100)), 0, 0, 0));
            g.setCornerRadius(pad * 2.5f);
            view.setBackground(g);
            view.setText("…");
            lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT);
            int pos = i("pos", 0);
            int v = pos / 3 == 0 ? Gravity.TOP : pos / 3 == 1 ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM;
            int h = pos % 3 == 0 ? Gravity.START : pos % 3 == 1 ? Gravity.CENTER_HORIZONTAL : Gravity.END;
            lp.gravity = v | h;
            lp.x = pad * 2; lp.y = pad * 4;
            wm.addView(view, lp);
            shown = true;
            startSampler();
        } catch (Throwable t) { shown = false; }
    }

    private void hide() {
        stopSampler();
        try { if (view != null && wm != null) wm.removeView(view); } catch (Throwable ignored) { }
        view = null; shown = false;
    }

    static int parse(String c) { try { return Color.parseColor(c); } catch (Throwable t) { return Color.WHITE; } }

    // ---- sampler ----
    private void startSampler() {
        if (running) return;
        running = true;
        ht = new HandlerThread("gamebar"); ht.start();
        bg = new Handler(ht.getLooper());
        bg.post(this::tick);
    }

    private void stopSampler() {
        running = false;
        final Handler h = bg; final HandlerThread t = ht; final Process pr = sh;
        bg = null; ht = null; sh = null; shIn = null; shOut = null; discovered = false; pIdle = pTotal = -1; layerFor = "";
        if (h != null) h.removeCallbacksAndMessages(null);
        new Thread(() -> {
            try { if (pr != null) pr.destroy(); } catch (Throwable ignored) { }
            if (t != null) t.quitSafely();
        }).start();
    }

    private boolean ensureShell() {
        if (sh != null) return true;
        try {
            sh = new ProcessBuilder("su").redirectErrorStream(true).start();
            shIn = sh.getOutputStream();
            shOut = new BufferedReader(new InputStreamReader(sh.getInputStream()));
            return true;
        } catch (Throwable t) { sh = null; return false; }
    }

    private List<String> run(String script) {
        List<String> out = new ArrayList<>();
        try {
            shIn.write((script + "\necho @E\n").getBytes());
            shIn.flush();
            String l;
            while ((l = shOut.readLine()) != null) { if (l.equals("@E")) return out; out.add(l); }
        } catch (Throwable t) { try { if (sh != null) sh.destroy(); } catch (Throwable ignored) { } sh = null; }
        return null;
    }

    private static final String DRM = "/sys/class/drm/sde-crtc-0/measured_fps";
    private static final String KG = "/sys/class/kgsl/kgsl-3d0/";

    private void discover() {
        List<String> z = run("for z in /sys/class/thermal/thermal_zone*; do echo \"z $z $(cat $z/type 2>/dev/null)\"; done");
        if (z == null) return;
        cpuTemp = null; gpuTemp = null;
        String c2 = null;
        for (String l : z) {
            String[] a = l.split(" ");
            if (a.length < 3) continue;
            String t = a[2];
            if (cpuTemp == null && (t.startsWith("cpuss-0") || t.equals("cpu-1-0") || t.startsWith("cpu-0-0"))) cpuTemp = a[1] + "/temp";
            if (c2 == null && t.startsWith("cpu")) c2 = a[1] + "/temp";
            if (gpuTemp == null && t.startsWith("gpuss-0")) gpuTemp = a[1] + "/temp";
        }
        if (cpuTemp == null) cpuTemp = c2;
        List<String> k = run("[ -r " + KG + "temp ] && echo y");
        if (k != null && !k.isEmpty()) gpuTemp = KG + "temp";
        discovered = true;
    }

    private void findLayer(String pkg) {
        layerFor = pkg; layer = null;
        if (pkg.isEmpty()) return;
        List<String> r = run("dumpsys SurfaceFlinger --list 2>/dev/null | grep -F '" + pkg.replace("'", "") + "'");
        if (r == null) return;
        String best = null;
        for (String l : r) { if (l.startsWith("SurfaceView") && l.contains("BLAST")) { best = l; break; } }
        if (best == null) for (String l : r) { if (l.contains("SurfaceView")) { best = l; break; } }
        if (best == null && !r.isEmpty()) best = r.get(0);
        layer = best;
    }

    private float sfFps() {
        if (layer == null) return -1;
        List<String> r = run("dumpsys SurfaceFlinger --latency '" + layer.replace("'", "") + "' 2>/dev/null");
        if (r == null || r.size() < 3) return -1;
        List<Long> ts = new ArrayList<>();
        for (int j = 1; j < r.size(); j++) {
            String[] a = r.get(j).trim().split("\\s+");
            if (a.length < 3) continue;
            try { long v = Long.parseLong(a[1]); if (v > 0 && v < Long.MAX_VALUE / 2) ts.add(v); } catch (Throwable ignored) { }
        }
        if (ts.size() < 2) return 0;
        long last = 0;
        for (long v : ts) last = Math.max(last, v);
        long first = last; int n = 0;
        for (long v : ts) if (v > last - 1_000_000_000L) { n++; first = Math.min(first, v); }
        if (n < 2 || last == first) return 0;
        return (n - 1) * 1e9f / (last - first);
    }

    private static int num(String s) { try { return Integer.parseInt(s.trim().split("\\s+")[0]); } catch (Throwable t) { return -1; } }
    private static String temp(int v) {
        if (v <= -1000) return "N/A";
        float f = v >= 1000 ? v / 1000f : v >= 150 ? v / 10f : v;
        return String.format("%.0f°", f);
    }

    private void tick() {
        if (!running || bg == null) return;
        try {
            if (!ensureShell()) { post("su unavailable"); schedule(); return; }
            if (!discovered) discover();
            String cur = fg;
            if (!cur.equals(layerFor)) findLayer(cur);
            StringBuilder sc = new StringBuilder();
            sc.append("echo \"stat $(head -1 /proc/stat)\";");
            sc.append("for f in /sys/devices/system/cpu/cpufreq/policy*/scaling_cur_freq; do echo \"f $(cat $f 2>/dev/null)\"; done;");
            sc.append("echo \"gu $(cat ").append(KG).append("gpu_busy_percentage 2>/dev/null)\";");
            sc.append("echo \"gc $(cat ").append(KG).append("gpuclk 2>/dev/null)\";");
            sc.append("echo \"gt $(cat ").append(gpuTemp == null ? "/dev/null" : gpuTemp).append(" 2>/dev/null)\";");
            sc.append("echo \"ct $(cat ").append(cpuTemp == null ? "/dev/null" : cpuTemp).append(" 2>/dev/null)\";");
            sc.append("echo \"bt $(cat /sys/class/power_supply/battery/temp 2>/dev/null)\";");
            sc.append("echo \"rf $(cat /sys/devices/system/cpu/bus_dcvs/DDR/cur_freq 2>/dev/null)\";");
            sc.append("awk '/^MemTotal/{t=$2}/^MemAvailable/{a=$2}END{print \"m\",t,a}' /proc/meminfo;");
            sc.append("echo \"p $(cat ").append(DRM).append(" 2>/dev/null)\"");
            List<String> r = run(sc.toString());
            if (r == null) { schedule(); return; }
            float fps = sfFps();
            String cpuUse = "N/A", gu = "N/A", gc = "N/A", gt = "N/A", ct = "N/A", bt = "N/A", ram = "N/A", rfq = "";
            List<Integer> fr = new ArrayList<>();
            float disp = -1;
            for (String l : r) {
                int sp = l.indexOf(' ');
                if (sp < 0) continue;
                String k = l.substring(0, sp), v = l.substring(sp + 1).trim();
                if (v.isEmpty()) continue;
                switch (k) {
                    case "stat": {
                        String[] a = v.split("\\s+");
                        if (a.length >= 8) {
                            long idle = Long.parseLong(a[4]) + Long.parseLong(a[5]), tot = 0;
                            for (int j = 1; j < a.length && j < 9; j++) tot += Long.parseLong(a[j]);
                            if (pTotal >= 0 && tot != pTotal) cpuUse = (100 * ((tot - pTotal) - (idle - pIdle)) / (tot - pTotal)) + "%";
                            pTotal = tot; pIdle = idle;
                        }
                        break;
                    }
                    case "f": fr.add(num(v)); break;
                    case "gu": gu = Math.max(0, num(v)) + "%"; break;
                    case "gc": { long hz = 0; try { hz = Long.parseLong(v.split("\\s+")[0]); } catch (Throwable ignored) { } gc = (hz / 1000000) + "MHz"; break; }
                    case "gt": gt = temp(num(v)); break;
                    case "ct": ct = temp(num(v)); break;
                    case "bt": bt = temp(num(v)); break;
                    case "rf": { int f = num(v); if (f > 0) rfq = (f >= 1000000 ? f / 1000 : f) + "MHz"; break; }
                    case "m": { String[] a = v.split(" "); if (a.length >= 2) { float t = Float.parseFloat(a[0]), av = Float.parseFloat(a[1]); ram = String.format("%.1f/%.1fG", (t - av) / 1048576f, t / 1048576f); } break; }
                    case "p": if (v.startsWith("fps:")) { try { disp = Float.parseFloat(v.split("\\s+")[1]); } catch (Throwable ignored) { } } break;
                }
            }
            if (fps < 0) fps = disp;
            final SpannableStringBuilder sb = new SpannableStringBuilder();
            if (b("fps", true)) {
                int s0 = sb.length();
                sb.append("FPS ").append(fps < 0 ? "N/A" : String.valueOf(Math.round(fps)));
                if (fps >= 0) sb.setSpan(new ForegroundColorSpan(fps >= 55 ? 0xFF4CAF50 : fps >= 30 ? 0xFFFFC107 : 0xFFF44336), s0 + 4, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append("\n");
            }
            if (b("cpu", true)) {
                sb.append("CPU ").append(cpuUse);
                if (b("cpufreq", true) && !fr.isEmpty()) { sb.append(" "); for (int j = 0; j < fr.size(); j++) { if (j > 0) sb.append("/"); sb.append(fr.get(j) > 0 ? String.format("%.1f", fr.get(j) / 1e6f) : "-"); } sb.append("G"); }
                if (b("temps", true)) sb.append(" ").append(ct);
                sb.append("\n");
            }
            if (b("gpu", true)) { sb.append("GPU ").append(gu).append(" ").append(gc); if (b("temps", true)) sb.append(" ").append(gt); sb.append("\n"); }
            if (b("ram", true)) { sb.append("RAM ").append(ram); if (!rfq.isEmpty()) sb.append(" ").append(rfq); sb.append("\n"); }
            if (b("bat", true)) sb.append("BAT ").append(bt).append("\n");
            if (sb.length() > 0) sb.delete(sb.length() - 1, sb.length());
            main.post(() -> { if (view != null) { view.setText(sb); view.setTextColor(parse(s("color", "#FFFFFF"))); view.setTextSize(i("size", 11)); } });
        } catch (Throwable t) { /* keep running */ }
        schedule();
    }

    private void post(String m) { main.post(() -> { if (view != null) view.setText(m); }); }
    private void schedule() { Handler h = bg; if (running && h != null) h.postDelayed(this::tick, Math.max(500, Math.min(5000, i("interval", 1000)))); }
}
