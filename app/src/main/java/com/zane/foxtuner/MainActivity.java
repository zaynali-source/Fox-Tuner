package com.zane.foxtuner;

import android.app.Activity;
import android.graphics.Color;
import android.view.HapticFeedbackConstants;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

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
