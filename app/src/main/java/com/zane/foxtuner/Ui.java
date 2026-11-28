package com.zane.foxtuner;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Small widget toolkit: cards, chips, switches, rows, bottom sheets. Everything is native views. */
public class Ui {
    public static final int BG = 0xFF000000, CARD = 0xFF151515, CARD2 = 0xFF242424, LINE = 0xFF2A2A2A,
            TXT = 0xFFFFFFFF, MUTED = 0xFF8C8C8C, ACC = 0xFFFFFFFF, ACCT = 0xFF000000, RED = 0xFFD71921, WARN = 0xFFFF8A80, OK = 0xFF7BE0A0;

    public static float density = 3f;
    public static int dp(float v) { return Math.round(v * density); }

    public static GradientDrawable rect(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    public static LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    public static void tap(View v) { try { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); } catch (Throwable ignored) { } }

    /** wrapping row of children */
    public static class Flow extends ViewGroup {
        private final int gap;
        public Flow(Context c) { super(c); gap = dp(8); }
        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec), x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                c.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
                int cw = c.getMeasuredWidth(), ch = c.getMeasuredHeight();
                if (x > 0 && x + cw > w) { x = 0; y += rowH + gap; rowH = 0; }
                x += cw + gap; rowH = Math.max(rowH, ch);
            }
            setMeasuredDimension(w, y + rowH);
        }
        @Override protected void onLayout(boolean ch, int l, int t, int r, int b) {
            int w = r - l, x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                int cw = c.getMeasuredWidth(), chh = c.getMeasuredHeight();
                if (x > 0 && x + cw > w) { x = 0; y += rowH + gap; rowH = 0; }
                c.layout(x, y, x + cw, y + chh);
                x += cw + gap; rowH = Math.max(rowH, chh);
            }
        }
    }

    /** pill switch */
    public static class Sw extends View {
        private boolean on;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public Sw(Context c, boolean on) { super(c); this.on = on; setMinimumWidth(dp(52)); setMinimumHeight(dp(30)); }
        public void setOn(boolean v) { on = v; invalidate(); }
        public boolean isOn() { return on; }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(dp(52), dp(32)); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            p.setColor(on ? ACC : 0xFF2E2E2E);
            cv.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
            p.setColor(on ? ACCT : 0xFF8A8A8A);
            float r = h / 2 - dp(5);
            cv.drawCircle(on ? w - h / 2 : h / 2, h / 2, r, p);
        }
    }

    public static class Chip extends TextView {
        public Chip(Context c, String label, boolean sel, boolean enabled) {
            super(c);
            setText(label);
            setTextSize(14);
            setGravity(Gravity.CENTER);
            setPadding(dp(16), dp(10), dp(16), dp(10));
            setMinHeight(dp(42));
            setTextColor(sel ? ACCT : TXT);
            setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            setBackground(rect(sel ? ACC : CARD2, 22));
            setAlpha(enabled ? 1f : 0.35f);
            setEnabled(enabled);
            setClickable(enabled);
        }
    }

    /** a card with an optional header (title + trailing view) and a body column */
    public static class Card extends LinearLayout {
        public final LinearLayout body;
        public final LinearLayout head;
        public final TextView title;
        public Card(Context c, String t) {
            super(c);
            setOrientation(VERTICAL);
            setBackground(rect(CARD, 26));
            setPadding(dp(18), dp(16), dp(18), dp(16));
            head = new LinearLayout(c);
            head.setOrientation(HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            title = text(c, t.toUpperCase(), 12.5f, MUTED, false);
            title.setLetterSpacing(0.12f);
            head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            addView(head, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            body = new LinearLayout(c);
            body.setOrientation(VERTICAL);
            addView(body, lpm(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0));
        }
    }

    public static View sub(Context c, String s) {
        TextView t = text(c, s, 13, MUTED, false);
        t.setPadding(0, dp(14), 0, dp(8));
        return t;
    }

    public static View note(Context c, String s, int color) {
        TextView t = text(c, s, 12, color, false);
        t.setPadding(0, dp(10), 0, 0);
        return t;
    }

    /** two-line row: title + small subtitle, optional trailing view; whole row clickable when onClick given */
    public static LinearLayout row(Context c, String title, String subt, View trailing, View.OnClickListener onClick) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(10), 0, dp(10));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(c, title, 16, TXT, false));
        if (subt != null && subt.length() > 0) {
            TextView s = text(c, subt, 12.5f, MUTED, false);
            s.setPadding(0, dp(2), 0, 0);
            col.addView(s);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null) r.addView(trailing);
        if (onClick != null) r.setOnClickListener(onClick);
        return r;
    }

    /** modal bottom sheet with a scrolling content column */
    public static Dialog sheet(Activity a, LinearLayout content) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(rect(0xFF1A1A1A, 28));
        wrap.setPadding(dp(20), dp(20), dp(20), dp(20));
        ScrollView sv = new ScrollView(a);
        sv.addView(content);
        wrap.addView(sv, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();
        return d;
    }

    public static TextView button(Context c, String label, boolean primary) {
        TextView t = text(c, label, 15, primary ? ACCT : TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), dp(12), dp(20), dp(12));
        t.setBackground(rect(primary ? ACC : CARD2, 24));
        return t;
    }
}
