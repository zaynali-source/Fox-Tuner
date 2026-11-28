package com.zane.foxtuner;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Fox Tuner: native UI. All root work is done by the Fox Tuner Helper module through ptctl.sh. */
public class MainActivity extends Activity {
    private final Ctl c = new Ctl();
    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout content, header, nav;
    private ScrollView sv;
    private TextView navProf, navMore;
    private String view = "prof", cluster = "little";
    private final Set<String> open = new HashSet<>();
    private boolean loaded = false;
    private String bkTime = "", bkPath = "";
    private boolean bkOk = false;

    // ---- tables ----
    static final String[][] MODES = {{"battery", "Battery"}, {"normal", "Normal"}, {"performance", "Performance"}, {"extreme", "Extreme"}};
    static final String[][] CL = {{"little", "Little"}, {"big", "Big"}, {"prime", "Prime"}};
    static final String[][] PR = {{"0", "Minimal"}, {"1", "Low"}, {"2", "Medium"}, {"3", "Maximum"}};
    static final String[][] TH = {{"normal", "Normal", "0"}, {"huanji", "Huanji", "1"}, {"nolimits", "No Limits", "6"}, {"class0", "Class 0", "7"},
            {"arvr", "AR/VR", "9"}, {"navigation", "Navigation", "10"}, {"video", "Video", "11"}, {"videochat", "Video Chat", "14"},
            {"camera", "Camera", "15"}, {"4k", "4K Video", "16"}, {"mgame", "Mobile Game", "19"}, {"highfps", "High FPS", "26"}, {"tgame18", "TGame", "18"}};
    static final String[] TD = {"Reduced peak frequency", "Balanced adaptive limits", "Higher available headroom", "Highest detected headroom"};
    static final int[][] THR = {{3, 6, 10, 15}, {3, 6, 10, 15}, {0, 3, 6, 10}, {0, 3, 6, 10}};
    static final int[] SHIFT = {8, 5, 3, 0};
    // [cluster][mode] windows
    static final int[][][] WIN = {
            {{1708800, 1593600, 1478400, 1344000}, {2016000, 1900800, 1843200, 1708800}, {2016000, 1900800, 1843200, 1708800}, {2016000, 1900800, 1843200, 1708800}},
            {{2073600, 1920000, 1824000, 1708800}, {2361600, 2188800, 2073600, 1920000}, {2572800, 2361600, 2188800, 2073600}, {2803200, 2707200, 2611200, 2572800}},
            {{1900800, 1766400, 1651200, 1459200}, {2304000, 2150400, 1900800, 1766400}, {2803200, 2668800, 2457600, 2304000}, {3014400, 2918400, 2803200, 2668800}}};
    static final int[][] GMAX = {{684, 633, 500, 353}, {900, 835, 736, 684}, {950, 900, 835, 736}, {1100, 1000, 950, 900}};
    static final int[] GMIN = {633, 500, 353, 255};
    static final int[][] FULL = {
            {364800, 441600, 595200, 787200, 902400, 1017600, 1113600, 1228800, 1344000, 1478400, 1593600, 1708800, 1843200, 1900800, 2016000},
            {480000, 633600, 787200, 940800, 1056000, 1190400, 1286400, 1401600, 1497600, 1593600, 1708800, 1824000, 1920000, 2073600, 2188800, 2361600, 2572800, 2611200, 2707200, 2803200},
            {480000, 633600, 787200, 960000, 1094400, 1209600, 1324800, 1459200, 1651200, 1766400, 1900800, 2150400, 2304000, 2457600, 2668800, 2803200, 2918400, 3014400}};
    static final String[] CSDEF = {"background", "camera-daemon", "foreground", "foreground_window", "restricted", "surfaceflinger", "system-background", "top-app"};

    // ---- touch rate (native MotionEvent counting) ----
    private int touchHz = 0, touchMax = 0, wCnt = 0;
    private long wStart = 0;
    private TextView hzView, hzMax;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.density = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(22), Ui.dp(22), Ui.dp(22), Ui.dp(6));
        col.addView(header, Ui.lp(-1, -2));
        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(120));
        sv.addView(content);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        // bottom nav pill
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackground(Ui.rect(0xE6161616, 34));
        nav.setPadding(Ui.dp(5), Ui.dp(5), Ui.dp(5), Ui.dp(5));
        navProf = navItem("Profiles", "prof");
        navMore = navItem("More", "more");
        nav.addView(navProf, new LinearLayout.LayoutParams(0, Ui.dp(58), 1f));
        nav.addView(navMore, new LinearLayout.LayoutParams(0, Ui.dp(58), 1f));
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(Ui.dp(300), -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        np.bottomMargin = Ui.dp(22);
        root.addView(nav, np);
        setContentView(root);
        c.load(new Ctl.Done() { public void run(String o) { loaded = true; loadBackup(); render(); } });
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (loaded) c.load(new Ctl.Done() { public void run(String o) { render(); } });
    }

    @Override protected void onPause() { super.onPause(); c.flush(); }

    @Override public void onBackPressed() {
        if (view.equals("tune")) { view = "prof"; render(); sv.scrollTo(0, 0); } else super.onBackPressed();
    }

    private TextView navItem(String label, final String target) {
        TextView t = Ui.text(this, label, 12.5f, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.tap(v); c.flush(); view = target; render(); sv.scrollTo(0, 0); } });
        return t;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------- helpers on state ----------
    private String active() { return c.g("active", "normal"); }
    private int modeIdx(String m) { for (int i = 0; i < MODES.length; i++) if (MODES[i][0].equals(m)) return i; return 1; }
    private String modeName(String m) { return MODES[modeIdx(m)][1]; }
    private List<String> roles() {
        List<String> r = new ArrayList<>();
        for (String x : c.g("hw.roles", "little big prime").trim().split("\\s+")) for (String[] k : CL) if (k[0].equals(x)) r.add(x);
        if (r.isEmpty()) r.addAll(Arrays.asList("little", "big", "prime"));
        return r;
    }
    private int clIdx(String r) { for (int i = 0; i < CL.length; i++) if (CL[i][0].equals(r)) return i; return 0; }
    private String secKey(String k) { return (k.equals("touch") || k.equals("thp")) ? active() + ".sec_" + k : "sec." + k; }
    private static String ghz(long k) { return String.format(Locale.US, "%.2f GHz", k / 1e6); }
    private static long num(String s) { try { return Long.parseLong(s.trim()); } catch (Throwable t) { return 0; } }

    private List<Integer> freqs(String r) {
        if (r.equals("gpu")) {
            List<Integer> o = new ArrayList<>();
            for (int n : Ctl.nums(c.g("hw.gpu.freqs"))) o.add(Math.round(n / 1e6f));
            return o;
        }
        List<Integer> l = Ctl.nums(c.g("hw." + r + ".freqs"));
        if (l.isEmpty()) for (int n : FULL[clIdx(r)]) l.add(n);
        return l;
    }
    private List<Integer> fb(List<Integer> l, String m) {
        List<Integer> o = new ArrayList<>();
        if (l.isEmpty()) return o;
        int top = Math.max(0, l.size() - 1 - SHIFT[modeIdx(m)]);
        for (int i = 0; i < 4; i++) { int v = l.get(Math.max(0, top - i)); if (!o.contains(v)) o.add(v); }
        return o;
    }
    private List<Integer> winCpu(String r, String m) {
        int[] b = WIN[clIdx(r)][modeIdx(m)];
        List<Integer> l = freqs(r), o = new ArrayList<>();
        boolean all = true;
        for (int v : b) { if (!l.contains(v)) all = false; o.add(v); }
        return all ? o : fb(l, m);
    }
    private List<Integer> winGpu(boolean max, String m) {
        List<Integer> l = freqs("gpu"), o = new ArrayList<>();
        int[] b = max ? GMAX[modeIdx(m)] : GMIN;
        boolean all = true;
        for (int v : b) { if (!l.contains(v)) all = false; o.add(v); }
        if (all) return o;
        if (max) return fb(l, m);
        o.clear();
        for (int i = Math.min(3, l.size() - 1); i >= 0; i--) o.add(l.get(i));
        return o;
    }
    private List<Integer> winHs(String r) {
        List<Integer> l = freqs(r);
        int d = r.equals("little") ? 1113600 : r.equals("big") ? 1190400 : 1459200;
        int i = l.indexOf(d);
        if (i < 0) i = l.size() / 2;
        int st = Math.max(0, Math.min(i - 1, l.size() - 4));
        return new ArrayList<>(l.subList(st, Math.min(l.size(), st + 4)));
    }
    private static List<Integer> ranges(String s) {
        List<Integer> o = new ArrayList<>();
        for (String p : s.split(",")) {
            if (p.isEmpty()) continue;
            String[] ab = p.split("-");
            try { int a = Integer.parseInt(ab[0]), b2 = ab.length > 1 ? Integer.parseInt(ab[1]) : a; for (int i = a; i <= b2; i++) o.add(i); } catch (Throwable ignored) { }
        }
        return o;
    }
    private List<String> csGroups() {
        List<String> g = new ArrayList<>();
        for (String x : c.g("hw.cs.groups").trim().split("\\s+")) if (!x.isEmpty()) g.add(x);
        if (g.isEmpty()) g.addAll(Arrays.asList(CSDEF));
        return g;
    }
    private List<Integer> csSel(String g) {
        String v = c.g("cs." + g, c.g("cs.cur." + g));
        List<Integer> o = new ArrayList<>();
        if (v.isEmpty()) return o;
        if (v.contains("-")) return ranges(v);
        for (String x : v.split(",")) { try { if (!x.isEmpty()) o.add(Integer.parseInt(x)); } catch (Throwable ignored) { } }
        return o;
    }
    private Set<Integer> parkedCpus() {
        Set<Integer> out = new HashSet<>();
        if (c.g("sec.priority").equals("0")) return out;
        int first = 0;
        for (String r : roles()) {
            int n = (int) num(c.g("hw." + r + ".cpus"));
            if (c.g("hw." + r + ".corectl").equals("1") && c.g(active() + "." + r + ".prio").equals("0")) {
                int keep = n > 1 ? 1 : 0;
                for (int i = first + keep; i < first + n; i++) out.add(i);
            }
            first += n;
        }
        return out;
    }
    private int ncpu() {
        String[] p = c.g("hw.present", "0-7").split("-");
        try { return Integer.parseInt(p[p.length - 1].trim()) + 1; } catch (Throwable t) { return 8; }
    }
    private String fmtV(String kind, String v) {
        if (v == null || v.isEmpty()) return "Auto";
        if (kind.equals("cpu") || kind.equals("hsf")) return ghz(num(v));
        if (kind.equals("gmax") || kind.equals("gmin")) return String.valueOf(Math.round(num(v) / 1e6f));
        return v;
    }

    // ---------- render ----------
    private void render() {
        final int y = sv.getScrollY();
        header.removeAllViews();
        content.removeAllViews();
        navProf.setTextColor(view.equals("more") ? Ui.MUTED : Ui.ACCT);
        navProf.setBackground(view.equals("more") ? null : Ui.rect(Ui.ACC, 29));
        navMore.setTextColor(view.equals("more") ? Ui.ACCT : Ui.MUTED);
        navMore.setBackground(view.equals("more") ? Ui.rect(Ui.ACC, 29) : null);
        navProf.setTypeface(view.equals("more") ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
        navMore.setTypeface(view.equals("more") ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        buildHeader();
        if (!c.ready && loaded) {
            content.addView(Ui.note(this, "Fox Tuner Helper module not responding. Install the helper module in KernelSU / APatch, enable it, reboot, and grant this app root.", Ui.WARN));
        }
        if (view.equals("prof")) buildProfiles();
        else if (view.equals("tune")) buildTune();
        else buildMore();
        sv.post(new Runnable() { public void run() { sv.scrollTo(0, y); } });
    }

    private void buildHeader() {
        if (view.equals("tune")) {
            TextView back = Ui.text(this, "‹", 30, Ui.TXT, false);
            back.setGravity(Gravity.CENTER);
            back.setBackground(Ui.rect(Ui.CARD, 18));
            back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.tap(v); view = "prof"; render(); sv.scrollTo(0, 0); } });
            header.addView(back, new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(52)));
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setPadding(Ui.dp(16), 0, 0, 0);
            t.addView(Ui.text(this, modeName(active()) + " tuning", 24, Ui.TXT, true));
            t.addView(Ui.text(this, "Profile configuration", 14, Ui.MUTED, false));
            header.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            return;
        }
        TextView t = Ui.text(this, "Fox Tuner", 34, Ui.TXT, false);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        header.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        View dot = new View(this);
        dot.setBackground(Ui.rect(c.g("svc").equals("1") ? Ui.OK : 0xFF444444, 8));
        header.addView(dot, Ui.lpm(Ui.dp(10), Ui.dp(10), 0, 0, 14, 0));
        final Ui.Sw sw = new Ui.Sw(this, c.on("opt.enabled", true));
        sw.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Ui.tap(v);
            boolean nv = !c.on("opt.enabled", true);
            c.S.put("opt.enabled", nv ? "1" : "0");
            c.flush();
            c.ctl(null, "opt", "enabled", nv ? "1" : "0");
            toast(nv ? "Profiles on" : "Profiles paused");
            render();
        } });
        header.addView(sw);
    }

    // ---------- Profiles ----------
    private void buildProfiles() {
        Ui.Card card = new Ui.Card(this, "Performance profile");
        card.body.addView(Ui.text(this, "Tap a profile to use it. Tap the lines to configure it.", 13, Ui.MUTED, false));
        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        for (int i = 0; i < 4; i++) {
            View t = tile(i);
            LinearLayout row = i < 2 ? r1 : r2;
            row.addView(t, Ui.lpm(0, -2, i % 2 == 0 ? 0 : 5, 0, i % 2 == 0 ? 5 : 0, 0));
            ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
        }
        card.body.addView(r1, Ui.lpm(-1, -2, 0, 14, 0, 0));
        card.body.addView(r2, Ui.lpm(-1, -2, 0, 10, 0, 0));
        content.addView(card, Ui.lpm(-1, -2, 0, 0, 0, 12));

        Ui.Card dev = new Ui.Card(this, "Device");
        String model = (c.g("hw.model") + " " + c.g("hw.device")).trim();
        dev.body.addView(Ui.text(this, model.isEmpty() ? "Unknown device" : model, 16, Ui.TXT, false));
        content.addView(dev, Ui.lpm(-1, -2, 0, 0, 0, 12));
        content.addView(statusCard(), Ui.lpm(-1, -2, 0, 0, 0, 12));
    }

    private Ui.Card statusCard() {
        Ui.Card st = new Ui.Card(this, "System status");
        Ui.Flow f = new Ui.Flow(this);
        boolean cc = true, walt = false;
        for (String r : roles()) { if (!c.g("hw." + r + ".corectl").equals("1")) cc = false; if (c.g("hw." + r + ".walt").equals("1")) walt = true; }
        f.addView(tag(true, "CPU caps"));
        f.addView(tag(cc, "Priority"));
        f.addView(tag(walt, "Governor"));
        f.addView(tag(!c.g("hw.gpu.ok").equals("0"), "GPU"));
        f.addView(tag(c.g("hw.cs").equals("1"), "Cpuset"));
        f.addView(tag(c.g("hw.sconfig").equals("1"), "Thermal"));
        f.addView(tag(c.g("hw.touch").equals("1"), "Touch"));
        f.addView(tag(c.g("hw.thp").equals("1"), "THP"));
        st.body.addView(f);
        return st;
    }

    private View tag(boolean ok, String t) {
        TextView v = Ui.text(this, (ok ? "✓ " : "✕ ") + t, 12.5f, ok ? Ui.OK : Ui.WARN, false);
        v.setBackground(Ui.rect(Ui.CARD2, 16));
        v.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        return v;
    }

    private View tile(final int i) {
        final String id = MODES[i][0];
        final boolean sel = id.equals(active());
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(16));
        t.setBackground(Ui.rect(sel ? Ui.ACC : Ui.CARD2, 24));
        int fg = sel ? Ui.ACCT : Ui.TXT, mu = sel ? 0xFF555555 : Ui.MUTED;
        View lines = new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas cv) {
                p.setColor(sel ? Ui.ACCT : Ui.TXT);
                p.setStrokeWidth(Ui.dp(2));
                p.setStrokeCap(Paint.Cap.ROUND);
                float w = getWidth(), hh = getHeight();
                for (int k = 0; k < 3; k++) cv.drawLine(w * 0.22f, hh * (0.3f + 0.2f * k), w * 0.78f, hh * (0.3f + 0.2f * k), p);
            }
        };
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.END);
        top.addView(lines, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(32)));
        lines.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Ui.tap(v);
            c.flush();
            if (!id.equals(active())) { c.S.put("active", id); c.ctl(null, "mode", id); }
            view = "tune"; render(); sv.scrollTo(0, 0);
        } });
        t.addView(top, Ui.lp(-1, -2));
        t.addView(Ui.text(this, MODES[i][1], 18, fg, true));
        TextView d = Ui.text(this, TD[i], 12.5f, mu, false);
        d.setPadding(0, Ui.dp(4), 0, 0);
        t.addView(d);
        if (sel) {
            TextView a = Ui.text(this, "ACTIVE", 11, fg, true);
            a.setLetterSpacing(0.14f);
            a.setPadding(0, Ui.dp(10), 0, 0);
            t.addView(a);
        }
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Ui.tap(v);
            c.flush();
            c.S.put("active", id);
            c.ctl(null, "mode", id);
            render();
        } });
        return t;
    }

    // ---------- Tuning ----------
    private Ui.Card section(final String key, String title, boolean fold) {
        final Ui.Card cd = new Ui.Card(this, title);
        final boolean on = !c.g(secKey(key)).equals("0");
        if (fold) {
            cd.title.setText(title.toUpperCase() + (open.contains(key) ? "  ⌃" : "  ⌄"));
            cd.head.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (open.contains(key)) open.remove(key); else open.add(key); render(); } });
        }
        Ui.Sw sw = new Ui.Sw(this, on);
        sw.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Ui.tap(v);
            boolean now = !c.g(secKey(key)).equals("0");
            c.set(secKey(key), now ? "0" : "1");
            render();
        } });
        cd.head.addView(sw);
        if (!on) cd.body.setAlpha(0.4f);
        if (fold && !open.contains(key)) cd.body.setVisibility(View.GONE);
        content.addView(cd, Ui.lpm(-1, -2, 0, 0, 0, 12));
        return cd;
    }

    private View chips(String[][] items, final String key, String sel, boolean[] dis) {
        Ui.Flow f = new Ui.Flow(this);
        for (int i = 0; i < items.length; i++) {
            final String v = items[i][0];
            Ui.Chip ch = new Ui.Chip(this, items[i][1], v.equals(sel), dis == null || !dis[i]);
            ch.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { Ui.tap(x); c.set(key, v); render(); } });
            f.addView(ch);
        }
        return f;
    }

    private View chipsAll(List<String[]> list, final String key, final String kind) {
        String sel = c.g(key);
        List<String[]> it = new ArrayList<>(list);
        boolean has = false;
        for (String[] x : it) if (x[0].equals(sel)) has = true;
        if (!has && !it.isEmpty()) it.set(it.size() - 1, new String[]{sel, fmtV(kind, sel)});
        long capv = 0;
        if (kind.equals("hsf") && key.endsWith(".hsfreq") && !c.g("sec.freq").equals("0")) capv = num(c.g(key.replaceAll("\\.hsfreq$", ".max")));
        boolean tlsLock = kind.equals("tls") && !c.g("sec.threshold").equals("0");
        boolean[] dis = new boolean[it.size()];
        int nd = 0;
        for (int i = 0; i < it.size(); i++) {
            String v = it.get(i)[0];
            if (tlsLock || (capv > 0 && !v.isEmpty() && num(v) > capv)) { dis[i] = true; nd++; }
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        Ui.Flow f = new Ui.Flow(this);
        for (int i = 0; i < it.size(); i++) {
            final String v = it.get(i)[0];
            Ui.Chip ch = new Ui.Chip(this, it.get(i)[1], v.equals(sel), !dis[i]);
            ch.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { Ui.tap(x); c.set(key, v); render(); } });
            f.addView(ch);
        }
        Ui.Chip more = new Ui.Chip(this, "⋯", false, true);
        more.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { allSheet(key, kind); } });
        f.addView(more);
        col.addView(f);
        if (tlsLock) col.addView(Ui.note(this, "Load shift is controlled by Frequency threshold (Ulq formula: base + threshold/5). Turn off Frequency threshold to set it here.", Ui.WARN));
        else if (nd > 0) col.addView(Ui.note(this, "Frequencies above the Max frequency cap are greyed out. Raise the cap or turn off Max frequency to use them.", Ui.WARN));
        return col;
    }

    private static List<String[]> items(int[] vals) { List<String[]> o = new ArrayList<>(); for (int v : vals) o.add(new String[]{"" + v, "" + v}); return o; }
    private static List<String[]> cpuItems(List<Integer> l) { List<String[]> o = new ArrayList<>(); for (int v : l) o.add(new String[]{"" + v, ghz(v)}); return o; }
    private static List<String[]> gpuItems(List<Integer> l) { List<String[]> o = new ArrayList<>(); for (int v : l) o.add(new String[]{"" + (v * 1000000L), "" + v}); return o; }

    private void buildTune() {
        final String m = active();
        List<String> R = roles();
        if (!R.contains(cluster)) cluster = R.get(0);
        // mode switcher
        Ui.Flow seg = new Ui.Flow(this);
        for (String[] mo : MODES) {
            final String id = mo[0];
            Ui.Chip ch = new Ui.Chip(this, mo[1], id.equals(m), true);
            ch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.tap(v); c.flush(); c.S.put("active", id); c.ctl(null, "mode", id); render(); } });
            seg.addView(ch);
        }
        content.addView(seg, Ui.lpm(-1, -2, 4, 0, 4, 14));

        Ui.Card cd = section("threshold", "Frequency threshold", false);
        cd.body.addView(chipsAll(items(THR[modeIdx(m)]), m + ".threshold", "thr"));
        cd.body.addView(Ui.note(this, "Ulq controls: up rate limit (250 x T), down rate limit (2000 + 1000 x T) and Target load shift (base + T/5). Those options below are locked while this is on. Turn it off to set them yourself.", Ui.MUTED));

        Ui.Flow tabs = new Ui.Flow(this);
        for (String r : R) {
            final String rr = r;
            Ui.Chip ch = new Ui.Chip(this, CL[clIdx(r)][1], r.equals(cluster), true);
            ch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.tap(v); cluster = rr; render(); } });
            tabs.addView(ch);
        }
        content.addView(tabs, Ui.lpm(-1, -2, 4, 0, 4, 12));

        final String cl = cluster;
        boolean noCC = c.g("hw." + cl + ".corectl").equals("0");
        cd = section("priority", "Cluster priority", false);
        cd.body.addView(chips(PR, m + "." + cl + ".prio", c.g(m + "." + cl + ".prio"), null));
        cd.body.addView(Ui.sub(this, "Minimum online cores"));
        int nc = (int) Math.min(Math.max(num(c.g("hw." + cl + ".cpus")), 4), 4);
        List<String[]> mi = new ArrayList<>();
        mi.add(new String[]{"", "Auto"});
        for (int i = 0; i <= nc; i++) if (i > 0 || !cl.equals("little")) mi.add(new String[]{"" + i, "" + i});
        cd.body.addView(chips(mi.subList(0, Math.min(6, mi.size())).toArray(new String[0][]), m + "." + cl + ".minc", c.g(m + "." + cl + ".minc"), null));
        if (noCC) cd.body.addView(Ui.note(this, "No core_ctl for this cluster on this kernel.", Ui.WARN));
        cd.body.addView(Ui.note(this, "Ulq controls: core_ctl min cores, max cores and busy_up_thres for this cluster. CPUs it parks are locked in Cpuset. Turn it off to set them yourself.", Ui.MUTED));

        if (c.g("hw." + cl + ".boost").equals("1")) {
            cd = section("boost", "Frequency boost %", false);
            cd.body.addView(chipsAll(items(new int[]{0, 3, 5, 10, 15, 20}), m + "." + cl + ".boost", "bst"));
        }

        cd = section("freq", "Max frequency", false);
        cd.body.addView(chipsAll(cpuItems(winCpu(cl, m)), m + "." + cl + ".max", "cpu"));

        cd = section("gov", "Governor · WALT", true);
        if (c.g("hw." + cl + ".walt").equals("0")) cd.body.addView(Ui.note(this, "WALT tunables not found for this cluster.", Ui.WARN));
        String p = m + "." + cl + ".";
        cd.body.addView(Ui.sub(this, "Hispeed frequency"));
        cd.body.addView(chipsAll(cpuItems(winHs(cl)), p + "hsfreq", "hsf"));
        cd.body.addView(Ui.sub(this, "Hispeed load"));
        cd.body.addView(chipsAll(items(new int[]{80, 85, 90, 95}), p + "hsload", "hsl"));
        cd.body.addView(Ui.sub(this, "RTG boost frequency"));
        List<String[]> rtg = new ArrayList<>();
        rtg.add(new String[]{"", "Auto"});
        rtg.add(new String[]{"0", "Off"});
        rtg.addAll(cpuItems(winHs(cl)));
        cd.body.addView(chipsAll(rtg, p + "rtg", "hsf"));
        cd.body.addView(Ui.sub(this, "Target load shift"));
        cd.body.addView(chipsAll(items(new int[]{2, 3, 4, 5}), p + "tls", "tls"));
        if (!c.g("hw." + cl + ".tlt").equals("0")) {
            cd.body.addView(Ui.sub(this, "Target load thresh"));
            List<String[]> tl = new ArrayList<>();
            tl.add(new String[]{"", "Auto"});
            for (int v : new int[]{25, 50, 75}) tl.add(new String[]{"" + v, "" + v});
            cd.body.addView(chipsAll(tl, p + "tlt", "tlt"));
        }

        cd = section("gpu", "GPU", true);
        if (c.g("hw.gpu.ok").equals("0")) cd.body.addView(Ui.note(this, "GPU devfreq not found.", Ui.WARN));
        else {
            cd.body.addView(Ui.sub(this, "Maximum · MHz"));
            cd.body.addView(chipsAll(gpuItems(winGpu(true, m)), m + ".gpu.max", "gmax"));
            cd.body.addView(Ui.sub(this, "Minimum · MHz"));
            cd.body.addView(chipsAll(gpuItems(winGpu(false, m)), m + ".gpu.min", "gmin"));
        }

        cd = section("cpuset", "Cpuset", true);
        if (c.g("hw.cs").equals("0")) cd.body.addView(Ui.note(this, "cpuset not found on this ROM.", Ui.WARN));
        else {
            Set<Integer> parked = parkedCpus();
            TextView rs = Ui.text(this, "Reset", 14, Ui.TXT, true);
            rs.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { c.flush(); c.ctl(new Ctl.Done() { public void run(String o) { c.load(new Ctl.Done() { public void run(String o2) { render(); } }); } }, "resetcs"); toast("Cpuset reset"); } });
            cd.body.addView(rs);
            for (final String g : csGroups()) {
                cd.body.addView(Ui.sub(this, Character.toUpperCase(g.charAt(0)) + g.substring(1)));
                Ui.Flow f = new Ui.Flow(this);
                final List<Integer> sel = csSel(g);
                for (int i = 0; i < ncpu(); i++) {
                    final int cpu = i;
                    Ui.Chip ch = new Ui.Chip(this, "" + i, sel.contains(i), !parked.contains(i));
                    ch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                        Ui.tap(v);
                        List<Integer> s2 = new ArrayList<>(sel);
                        if (s2.contains(cpu)) s2.remove(Integer.valueOf(cpu)); else s2.add(cpu);
                        if (s2.isEmpty()) { toast("Keep at least one CPU"); return; }
                        java.util.Collections.sort(s2);
                        StringBuilder sb = new StringBuilder();
                        for (int x : s2) sb.append(sb.length() > 0 ? "," : "").append(x);
                        c.set("cs." + g, sb.toString());
                        render();
                    } });
                    f.addView(ch);
                }
                cd.body.addView(f);
            }
            if (!parked.isEmpty()) cd.body.addView(Ui.note(this, "Greyed CPUs are parked by Cluster priority (Minimal). Raise the priority or turn off Cluster priority to use them.", Ui.WARN));
        }

        cd = section("touch", "Touch sampling", false);
        if (!c.g("hw.touch").equals("1")) cd.body.addView(Ui.note(this, "bump_sample_rate not found on this kernel.", Ui.WARN));
        cd.body.addView(chips(new String[][]{{"0", "Default"}, {"1", "Boosted"}}, m + ".touch", c.g(m + ".touch", "0"), null));

        cd = section("thp", "Transparent Huge Pages", false);
        if (c.g("hw.thp").equals("0")) cd.body.addView(Ui.note(this, "THP node not found on this kernel.", Ui.WARN));
        cd.body.addView(chips(new String[][]{{"0", "Default · madvise"}, {"1", "Boost · always"}}, m + ".thp", c.g(m + ".thp", "0"), null));
        cd.body.addView(Ui.note(this, "Written once when the profile is applied. Boost can speed up large-memory apps and games a little, and uses more RAM.", Ui.MUTED));

        cd = section("thermal", "Thermal", false);
        String th = c.g(m + ".thermal", "default"), sub = "ROM default";
        for (String[] t : TH) if (t[0].equals(th)) sub = t[1] + " · sconfig " + t[2];
        cd.body.addView(Ui.row(this, "Thermal profile", sub, Ui.text(this, "›", 22, Ui.MUTED, false), new View.OnClickListener() { public void onClick(View v) { thermalSheet(); } }));
    }

    // ---------- sheets ----------
    private LinearLayout sheetCol(String title, String sub) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, title, 20, Ui.TXT, true));
        if (sub != null) { TextView s = Ui.text(this, sub, 13, Ui.MUTED, false); s.setPadding(0, Ui.dp(4), 0, Ui.dp(8)); col.addView(s); }
        return col;
    }

    private View option(String label, String small, boolean sel, View.OnClickListener l) {
        LinearLayout r = Ui.row(this, label, small, sel ? Ui.text(this, "●", 16, Ui.ACC, false) : null, l);
        return r;
    }

    private void allSheet(final String key, String kind) {
        List<String[]> vals = new ArrayList<>();
        String title;
        final String cl = cluster;
        if (kind.equals("thr")) { for (int i = 0; i <= 20; i++) vals.add(new String[]{"" + i, "" + i}); title = "Frequency threshold"; }
        else if (kind.equals("cpu") || kind.equals("hsf")) {
            List<Integer> l = freqs(cl);
            for (int i = l.size() - 1; i >= 0; i--) vals.add(new String[]{"" + l.get(i), ghz(l.get(i))});
            title = CL[clIdx(cl)][1] + (kind.equals("cpu") ? " max frequency" : " hispeed frequency");
        }
        else if (kind.equals("hsl")) { for (int v : new int[]{100, 95, 90, 85, 80, 75, 70, 65, 60, 50, 40, 30, 20, 10, 1}) vals.add(new String[]{"" + v, "" + v}); title = "Hispeed load"; }
        else if (kind.equals("tls")) { for (int i = 0; i <= 10; i++) vals.add(new String[]{"" + i, "" + i}); title = "Target load shift"; }
        else if (kind.equals("tlt")) { vals.add(new String[]{"", "Auto"}); for (int i = 0; i <= 20; i++) vals.add(new String[]{"" + i * 5, "" + i * 5}); title = "Target load thresh"; }
        else if (kind.equals("bst")) { for (int i = 0; i <= 30; i++) vals.add(new String[]{"" + i, "" + i}); title = "Frequency boost %"; }
        else { List<Integer> l = freqs("gpu"); for (int i = l.size() - 1; i >= 0; i--) vals.add(new String[]{"" + (l.get(i) * 1000000L), l.get(i) + " MHz"}); title = kind.equals("gmax") ? "GPU maximum" : "GPU minimum"; }
        LinearLayout col = sheetCol(title, null);
        final Dialog[] dl = new Dialog[1];
        String cur = c.g(key);
        for (final String[] v : vals) {
            col.addView(option(v[1], null, v[0].equals(cur), new View.OnClickListener() { public void onClick(View x) { c.set(key, v[0]); dl[0].dismiss(); render(); } }));
        }
        dl[0] = Ui.sheet(this, col);
    }

    private void thermalSheet() {
        final String m = active();
        LinearLayout col = sheetCol("Thermal profile", modeName(m) + " mode · writes thermal_message/sconfig (indexes from the ROM).");
        final Dialog[] dl = new Dialog[1];
        String cur = c.g(m + ".thermal", "default");
        col.addView(option("ROM default", "leave untouched", cur.equals("default"), new View.OnClickListener() { public void onClick(View x) { c.set(m + ".thermal", "default"); dl[0].dismiss(); render(); } }));
        for (final String[] t : TH) {
            col.addView(option(t[1], "sconfig " + t[2], cur.equals(t[0]), new View.OnClickListener() { public void onClick(View x) { c.set(m + ".thermal", t[0]); dl[0].dismiss(); render(); } }));
        }
        dl[0] = Ui.sheet(this, col);
    }

    private void confirm(String title, String msg, String yes, final Runnable go) {
        LinearLayout col = sheetCol(title, msg);
        final Dialog[] dl = new Dialog[1];
        LinearLayout act = new LinearLayout(this);
        act.setGravity(Gravity.END);
        TextView no = Ui.button(this, "Cancel", false), ok = Ui.button(this, yes, true);
        no.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dl[0].dismiss(); } });
        ok.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dl[0].dismiss(); go.run(); } });
        act.addView(no, Ui.lpm(-2, -2, 0, 0, 10, 0));
        act.addView(ok);
        col.addView(act, Ui.lpm(-1, -2, 0, 16, 0, 0));
        dl[0] = Ui.sheet(this, col);
    }

    // ---------- More ----------
    private void loadBackup() {
        c.ctl(new Ctl.Done() { public void run(String o) {
            Map<String, String> m = Ctl.parse(o);
            if (m.containsKey("path")) { bkOk = true; bkPath = m.get("path"); bkTime = m.containsKey("time") ? m.get("time") : "saved"; }
            else { bkOk = false; bkPath = ""; bkTime = "no backup yet"; }
            if (view.equals("more")) render();
        } }, "backup-info");
    }

    private View optRow(String title, String sub, final String opt, boolean def, boolean enabled) {
        final Ui.Sw sw = new Ui.Sw(this, c.on("opt." + opt, def) && enabled);
        LinearLayout r = Ui.row(this, title, sub, sw, null);
        if (!enabled) r.setAlpha(0.5f);
        else r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Ui.tap(v);
            boolean nv = !c.on("opt." + opt, def);
            c.S.put("opt." + opt, nv ? "1" : "0");
            c.ctl(null, "opt", opt, nv ? "1" : "0");
            if (opt.equals("bundle")) toast("Reboot to apply");
            render();
        } });
        return r;
    }

    private String ovl() {
        String t = c.g("hw.bundle");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("ok=(\\d+) bad=(\\d+) skip=(\\d+)").matcher(t);
        if (!m.find()) return t.isEmpty() ? "Systemless · reboot to apply" : t;
        return (Integer.parseInt(m.group(1)) > 0 ? "powerhint.json active" : "not active") + (Integer.parseInt(m.group(2)) > 0 ? " · mount failed" : "") + (Integer.parseInt(m.group(3)) > 0 ? " · file missing on this ROM" : "");
    }

    private void buildMore() {
        Ui.Card hm = new Ui.Card(this, "Helper module");
        String ver = c.g("hw.ver");
        hm.body.addView(Ui.row(this, "Fox Tuner Helper " + ver, ver.isEmpty() ? "Not detected" : "Connected · applies your settings at boot", null, null));
        boolean ov2 = c.g("hw.ovl2").equals("1");
        hm.body.addView(optRow("PowerHAL powerhint", ov2 ? "FoxOverlay module is active and owns these files. Disable or remove it to use this one." : ovl(), "bundle", true, !ov2));
        hm.body.addView(optRow("Take over from ROM", "Stops ulquiorrad so it can't overwrite values", "takeover", true, true));
        hm.body.addView(optRow("Keep enforcing", "Re-applies if the ROM changes values (5 s check). Off = lighter", "watch", false, true));
        content.addView(hm, Ui.lpm(-1, -2, 0, 0, 0, 12));

        Ui.Card bk = new Ui.Card(this, "Backup");
        bk.body.addView(Ui.text(this, bkTime, 12.5f, Ui.MUTED, false));
        LinearLayout act = new LinearLayout(this);
        TextView now = Ui.button(this, "Back up", false), res = Ui.button(this, "Restore", false);
        if (!bkOk) res.setAlpha(0.35f);
        now.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { c.flush(); c.ctl(new Ctl.Done() { public void run(String o) { toast(o != null && o.contains("ok") ? "Backup saved" : "Backup failed"); loadBackup(); } }, "backup"); } });
        res.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (!bkOk) { toast("No backup found"); return; }
            confirm("Restore backup?", bkTime + "\nReplaces all current profile settings.", "Restore", new Runnable() { public void run() {
                c.ctl(new Ctl.Done() { public void run(String o) { if (o != null && o.contains("ok")) { c.load(new Ctl.Done() { public void run(String o2) { render(); toast("Settings restored"); } }); } else toast("Restore failed"); } }, "restore");
            } });
        } });
        act.addView(now, new LinearLayout.LayoutParams(0, -2, 1f));
        act.addView(res, Ui.lpm(0, -2, 10, 0, 0, 0));
        ((LinearLayout.LayoutParams) res.getLayoutParams()).weight = 1f;
        bk.body.addView(act, Ui.lpm(-1, -2, 0, 12, 0, 0));
        bk.body.addView(optRow("Auto backup", bkPath.isEmpty() ? "/sdcard/ProfilesTuner" : bkPath, "autobackup", true, true));
        content.addView(bk, Ui.lpm(-1, -2, 0, 0, 0, 12));

        Ui.Card ds = new Ui.Card(this, "Display");
        ds.body.addView(Ui.row(this, "Resolution & DPI", "Change screen size and density", Ui.text(this, "›", 22, Ui.MUTED, false), new View.OnClickListener() { public void onClick(View v) { displaySheet(); } }));
        content.addView(ds, Ui.lpm(-1, -2, 0, 0, 0, 12));

        Ui.Card tc = new Ui.Card(this, "Touch");
        tc.body.addView(Ui.row(this, "Touch test", "Measure touch sampling rate", Ui.text(this, "›", 22, Ui.MUTED, false), new View.OnClickListener() { public void onClick(View v) { touchSheet(); } }));
        content.addView(tc, Ui.lpm(-1, -2, 0, 0, 0, 12));

        Ui.Card tl = new Ui.Card(this, "Quick Settings tile");
        Ui.Flow f = new Ui.Flow(this);
        final Set<String> tm = new LinkedHashSet<>(Arrays.asList(FoxTunerTileService.getCsv(this).split(",")));
        for (final String[] mo : MODES) {
            Ui.Chip ch = new Ui.Chip(this, mo[1], tm.contains(mo[0]), true);
            ch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                boolean has = tm.contains(mo[0]);
                if (has && tm.size() == 1) { toast("Keep at least one mode"); return; }
                List<String> o = new ArrayList<>();
                for (String[] x : MODES) if (x[0].equals(mo[0]) ? !has : tm.contains(x[0])) o.add(x[0]);
                FoxTunerTileService.saveCsv(MainActivity.this, android.text.TextUtils.join(",", o));
                render();
            } });
            f.addView(ch);
        }
        tl.body.addView(f);
        tl.body.addView(Ui.note(this, "One tile, tap to cycle through the selected modes. Add “Fox Tuner” from the Quick Settings edit (pencil) menu.", Ui.MUTED));
        content.addView(tl, Ui.lpm(-1, -2, 0, 0, 0, 12));

        Ui.Card rs = new Ui.Card(this, "Reset");
        rs.body.addView(Ui.row(this, "Reset this mode", null, null, new View.OnClickListener() { public void onClick(View v) {
            final String m = active();
            confirm("Reset " + modeName(m) + "?", "Restore default values for this mode.", "Reset", new Runnable() { public void run() {
                c.flush();
                c.ctl(new Ctl.Done() { public void run(String o) { c.load(new Ctl.Done() { public void run(String o2) { render(); toast("Mode reset"); } }); } }, "reset", m);
            } });
        } }));
        rs.body.addView(Ui.row(this, "Reset all profiles", null, null, new View.OnClickListener() { public void onClick(View v) {
            confirm("Reset all profiles?", "Restore default values for all modes.", "Reset all", new Runnable() { public void run() {
                c.flush();
                c.ctl(new Ctl.Done() { public void run(String o) { c.load(new Ctl.Done() { public void run(String o2) { render(); toast("All reset"); } }); } }, "resetall");
            } });
        } }));
        content.addView(rs, Ui.lpm(-1, -2, 0, 0, 0, 12));
        content.addView(statusCard(), Ui.lpm(-1, -2, 0, 0, 0, 12));
    }

    // ---------- touch test ----------
    private void touchSheet() {
        LinearLayout col = sheetCol("Touch test", "Drag your finger on the pad. Shows the real touch samples per second from the panel, not the screen refresh.");
        touchMax = 0; touchHz = 0; wStart = 0; wCnt = 0;
        FrameLayout pad = new FrameLayout(this);
        pad.setBackground(Ui.rect(Ui.CARD2, 24));
        hzView = Ui.text(this, "0", 40, Ui.TXT, true);
        hzMax = Ui.text(this, "max 0 Hz", 13, Ui.MUTED, false);
        LinearLayout lab = new LinearLayout(this);
        lab.setOrientation(LinearLayout.VERTICAL);
        lab.setPadding(Ui.dp(16), Ui.dp(10), 0, 0);
        lab.addView(hzView);
        lab.addView(hzMax);
        pad.addView(lab);
        pad.setOnTouchListener(new View.OnTouchListener() { public boolean onTouch(View v, MotionEvent ev) {
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) v.getParent().requestDisallowInterceptTouchEvent(true);
            if (a == MotionEvent.ACTION_MOVE) {
                long now = ev.getEventTime();
                if (wStart == 0) wStart = now;
                wCnt += ev.getHistorySize() + 1;
                if (now - wStart >= 400) {
                    touchHz = (int) (wCnt * 1000L / (now - wStart));
                    wStart = now; wCnt = 0;
                    if (touchHz > touchMax) touchMax = touchHz;
                    hzView.setText("" + touchHz);
                    hzMax.setText("max " + touchMax + " Hz");
                }
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { wStart = 0; wCnt = 0; }
            return true;
        } });
        col.addView(pad, Ui.lpm(-1, -2, 0, 12, 0, 0));
        pad.setMinimumHeight(Ui.dp(200));
        col.addView(touchSwitch("Game mode", "touch_dev/game_mode", "game"));
        col.addView(touchSwitch("Super report rate", "bump_sample_rate · 500 Hz", "super"));
        final Dialog[] dl = new Dialog[1];
        TextView done = Ui.button(this, "Done", true);
        done.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dl[0].dismiss(); } });
        col.addView(done, Ui.lpm(-1, -2, 0, 14, 0, 0));
        dl[0] = Ui.sheet(this, col);
    }

    private View touchSwitch(String t, String s, final String which) {
        final Ui.Sw sw = new Ui.Sw(this, false);
        LinearLayout r = Ui.row(this, t, s, sw, null);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.tap(v); sw.setOn(!sw.isOn()); c.ctl(null, "touch", which, sw.isOn() ? "1" : "0"); } });
        return r;
    }

    // ---------- display ----------
    private int dPw, dPh, dW, dH, dPd, dD;
    private String dOw = "", dOd = "";

    private void displaySheet() {
        c.exec("wm size; wm density", new Ctl.Done() { public void run(String t) {
            java.util.regex.Matcher m;
            m = java.util.regex.Pattern.compile("Physical size: (\\d+)x(\\d+)").matcher(t);
            if (m.find()) { dPw = Integer.parseInt(m.group(1)); dPh = Integer.parseInt(m.group(2)); }
            dOw = "";
            m = java.util.regex.Pattern.compile("Override size: (\\d+)x(\\d+)").matcher(t);
            if (m.find()) { dOw = m.group(1) + "x" + m.group(2); dW = Integer.parseInt(m.group(1)); dH = Integer.parseInt(m.group(2)); } else { dW = dPw; dH = dPh; }
            m = java.util.regex.Pattern.compile("Physical density: (\\d+)").matcher(t);
            if (m.find()) dPd = Integer.parseInt(m.group(1));
            dOd = "";
            m = java.util.regex.Pattern.compile("Override density: (\\d+)").matcher(t);
            if (m.find()) { dOd = m.group(1); dD = Integer.parseInt(dOd); } else dD = dPd;
            showDisplay();
        } });
    }

    private void showDisplay() {
        if (dPw == 0) { toast("Cannot read display info (root needed)"); return; }
        LinearLayout col = sheetCol("Resolution & DPI", "Native " + dPw + "×" + dPh + " · " + dPd + " dpi. Changes revert after 20 s unless you tap Keep.");
        final Dialog[] dl = new Dialog[1];
        col.addView(Ui.sub(this, "Current: " + dW + "×" + dH + " · " + dD + " dpi"));
        Ui.Flow rp = new Ui.Flow(this);
        for (int p : new int[]{100, 90, 80, 70, 60}) {
            final int w = Math.round(dPw * p / 100f / 2) * 2, hh = Math.round(dPh * p / 100f / 2) * 2;
            Ui.Chip ch = new Ui.Chip(this, p + "% · " + w + "×" + hh, w == dW && hh == dH, true);
            ch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dl[0].dismiss(); dispChange("wm size " + w + "x" + hh); } });
            rp.addView(ch);
        }
        col.addView(rp);
        col.addView(Ui.sub(this, "Custom size"));
        LinearLayout two = new LinearLayout(this);
        final EditText ew = edit("Width", "" + dW), eh = edit("Height", "" + dH);
        two.addView(ew, new LinearLayout.LayoutParams(0, -2, 1f));
        two.addView(eh, Ui.lpm(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) eh.getLayoutParams()).weight = 1f;
        col.addView(two);
        TextView ap = Ui.button(this, "Apply size", false);
        ap.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            int w = (int) num(ew.getText().toString()), hh = (int) num(eh.getText().toString());
            if (!(w >= 320 && w <= dPw * 1.5 && hh >= 480 && hh <= dPh * 1.5)) { toast("Invalid size"); return; }
            dl[0].dismiss(); dispChange("wm size " + w + "x" + hh);
        } });
        col.addView(ap, Ui.lpm(-1, -2, 0, 8, 0, 0));
        col.addView(Ui.sub(this, "Density · DPI"));
        Ui.Flow dp = new Ui.Flow(this);
        Set<Integer> ds = new java.util.TreeSet<>(Arrays.asList(dPd, 320, 360, 400, 440, 480, 520));
        for (final int d : ds) {
            if (d <= 0) continue;
            Ui.Chip ch = new Ui.Chip(this, "" + d, d == dD, true);
            ch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dl[0].dismiss(); dispChange("wm density " + d); } });
            dp.addView(ch);
        }
        col.addView(dp);
        final EditText ed = edit("DPI", "" + dD);
        col.addView(ed, Ui.lpm(-1, -2, 0, 8, 0, 0));
        TextView ad = Ui.button(this, "Apply DPI", false);
        ad.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            int d = (int) num(ed.getText().toString());
            if (!(d >= 120 && d <= 800)) { toast("DPI must be 120–800"); return; }
            dl[0].dismiss(); dispChange("wm density " + d);
        } });
        col.addView(ad, Ui.lpm(-1, -2, 0, 8, 0, 0));
        LinearLayout rr = new LinearLayout(this);
        TextView rsz = Ui.button(this, "Reset size", false), rdp = Ui.button(this, "Reset DPI", false);
        rsz.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dl[0].dismiss(); dispChange("wm size reset"); } });
        rdp.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dl[0].dismiss(); dispChange("wm density reset"); } });
        rr.addView(rsz, new LinearLayout.LayoutParams(0, -2, 1f));
        rr.addView(rdp, Ui.lpm(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) rdp.getLayoutParams()).weight = 1f;
        col.addView(rr, Ui.lpm(-1, -2, 0, 12, 0, 0));
        dl[0] = Ui.sheet(this, col);
    }

    private EditText edit(String hint, String val) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(val);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setTextColor(Ui.TXT);
        e.setHintTextColor(Ui.MUTED);
        e.setBackground(Ui.rect(Ui.CARD2, 16));
        e.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        return e;
    }

    /** apply a wm change with an automatic revert after 20 s unless the user taps Keep */
    private void dispChange(String cmd) {
        String rs = dOw.isEmpty() ? "wm size reset" : "wm size " + dOw, rd = dOd.isEmpty() ? "wm density reset" : "wm density " + dOd;
        String revert = rs + "; " + rd;
        String sh = "D=" + Ctl.DIR + "; mkdir -p \"$D\"; rm -f \"$D/disp_keep\"; printf '%s\\n' " + Ctl.q(revert) + " > \"$D/disp_revert.sh\"; (sleep 20; [ -f \"$D/disp_keep\" ] || sh \"$D/disp_revert.sh\") >/dev/null 2>&1 & " + cmd;
        c.exec(sh, new Ctl.Done() { public void run(String o) { keepDialog(); } });
    }

    private void keepDialog() {
        LinearLayout col = sheetCol("Keep this display setting?", "Reverts automatically in 20 seconds.");
        final Dialog[] dl = new Dialog[1];
        LinearLayout act = new LinearLayout(this);
        act.setGravity(Gravity.END);
        TextView rev = Ui.button(this, "Revert", false), keep = Ui.button(this, "Keep", true);
        rev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dl[0].dismiss(); c.exec("D=" + Ctl.DIR + "; sh \"$D/disp_revert.sh\"; : > \"$D/disp_keep\"", null); } });
        keep.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dl[0].dismiss(); c.exec("D=" + Ctl.DIR + "; mkdir -p \"$D\"; : > \"$D/disp_keep\"", null); toast("Kept"); } });
        act.addView(rev, Ui.lpm(-2, -2, 0, 0, 10, 0));
        act.addView(keep);
        col.addView(act, Ui.lpm(-1, -2, 0, 16, 0, 0));
        dl[0] = Ui.sheet(this, col);
        dl[0].setCancelable(false);
        h.postDelayed(new Runnable() { public void run() { if (dl[0].isShowing()) dl[0].dismiss(); } }, 21000);
    }
}
