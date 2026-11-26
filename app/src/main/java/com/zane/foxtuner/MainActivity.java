package com.zane.foxtuner;

import android.app.Activity;
import android.graphics.Color;
import android.view.HapticFeedbackConstants;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Fox Tuner app: hosts the module's WebUI (assets/index.html) and exposes the same
 * window.ksu.exec(cmd, opts, callbackName) bridge that KernelSU/APatch provide, backed by su.
 * The Fox Tuner Helper module must be installed (it provides /data/adb/modules/profiles_tuner/bin/ptctl.sh).
 */
public class MainActivity extends Activity {
    private WebView web;
    private static final Set<String> GAMES = new HashSet<>(java.util.Arrays.asList(
        "com.wanmei.hotta.wm", "com.levelinfinite.dfm", "com.miHoYo.GenshinImpact", "com.miHoYo.Yuanshen",
        "com.HoYoverse.hkrpgoversea", "com.miHoYo.hkrpg", "com.HoYoverse.Nap", "com.miHoYo.Nap",
        "com.miHoYo.bh3oversea", "com.miHoYo.bh3global", "com.kurogame.wutheringwaves.global", "com.kurogame.kjw.kuro",
        "com.kurogame.wutheringwaves.bilibili", "com.activision.callofduty.warzone", "com.carxtech.sr",
        "com.levelinfinite.hotta.gp", "com.tencent.ig", "com.pubg.imobile", "com.activision.callofduty.shooter",
        "com.mobile.legends", "com.dts.freefireth", "com.dts.freefiremax", "com.supercell.brawlstars"));
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private static final Pattern CB = Pattern.compile("^[A-Za-z0-9_$]+$");

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "ksu");
        web.loadUrl("file:///android_asset/index.html");
    }

    private class Bridge {
        @JavascriptInterface
        public void haptic() {
            runOnUiThread(() -> { if (web != null) web.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); });
        }

        @JavascriptInterface
        public String tileModes() { return FoxTunerTileService.getCsv(MainActivity.this); }

        @JavascriptInterface
        public void setTileModes(String csv) { FoxTunerTileService.saveCsv(MainActivity.this, csv); }

        @JavascriptInterface
        public String apps() {
            try {
                PackageManager pm = getPackageManager();
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> l = pm.queryIntentActivities(i, 0);
                Set<String> seen = new HashSet<>();
                JSONArray a = new JSONArray();
                for (ResolveInfo r : l) {
                    String p = r.activityInfo.packageName;
                    if (!seen.add(p) || p.equals(getPackageName())) continue;
                    JSONObject o = new JSONObject();
                    o.put("p", p);
                    o.put("l", String.valueOf(r.loadLabel(pm)));
                    o.put("g", GAMES.contains(p) ? 1 : 0);
                    a.put(o);
                }
                return a.toString();
            } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public boolean app() { return true; }

        @JavascriptInterface
        public void exec(final String cmd, String opts, final String cb) {
            if (cb == null || !CB.matcher(cb).matches()) return;
            pool.execute(() -> {
                int code = -1;
                String out = "", err = "";
                try {
                    Process p = new ProcessBuilder("su", "-c", cmd).start();
                    final ByteArrayOutputStream eb = new ByteArrayOutputStream();
                    final InputStream es = p.getErrorStream();
                    Thread et = new Thread(() -> { try { copy(es, eb); } catch (Exception ignored) { } });
                    et.start();
                    ByteArrayOutputStream ob = new ByteArrayOutputStream();
                    copy(p.getInputStream(), ob);
                    code = p.waitFor();
                    et.join(1500);
                    out = ob.toString("UTF-8");
                    err = eb.toString("UTF-8");
                } catch (Exception e) {
                    err = String.valueOf(e);
                }
                final String js = cb + "(" + code + "," + JSONObject.quote(out) + "," + JSONObject.quote(err) + ")";
                runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); });
            });
        }
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
