package com.shilapi.xcertplay;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * In-app map-colour tuner. Sliders for the cluster recolour, named presets, and a
 * live preview that recolours a sample map with the exact same maths the cluster uses.
 * Changes are applied to the live cluster immediately (Tune is read every frame), so
 * no reconnect is needed to see them on the dashboard.
 */
public final class MapTunerActivity extends Activity {

    private static final int BG = 0xFF0E1114;
    private static final int CARD = 0xFF151A1F;
    private static final int TEXT = 0xFFE7ECF0;
    private static final int SUB = 0xFF9BA1A9;
    private static final int ACCENT = 0xFF4F9BF0;

    private Bitmap sample;         // the raw sample map (fixed)
    private Bitmap preview;        // recoloured copy shown to the user
    private int[] srcPx, outPx;
    private ImageView previewView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler bg = new Handler(Looper.getMainLooper());  // debounce on UI looper is fine (work is light)

    private static final String[] KEYS   = {"dim", "soft", "bluesat", "bluemargin", "bluemin"};
    private static final String[] LABELS = {"Brightness", "Darkness of mids", "Route: how blue counts", "Route: blue vs other", "Route: min brightness"};
    private static final int[] MINV = {40, 10, 5, 2, 10};
    private static final int[] MAXV = {100, 40, 60, 40, 80};
    private final TextView[] valLabels = new TextView[KEYS.length];
    private final SeekBar[] bars = new SeekBar[KEYS.length];

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tune.init(this);
        buildSample();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(title("Cluster map colours"));

        previewView = new ImageView(this);
        previewView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams pv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
        pv.topMargin = dp(8); pv.bottomMargin = dp(8);
        previewView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(previewView, pv);

        TextView hint = new TextView(this);
        hint.setText("Preview updates as you drag. Changes also apply to the dashboard live — no reconnect.");
        hint.setTextColor(SUB); hint.setTextSize(13f);
        hint.setPadding(0, 0, 0, dp(12));
        root.addView(hint);

        // presets with example thumbnails
        TextView presetLbl = new TextView(this);
        presetLbl.setText("Presets (tap to apply)");
        presetLbl.setTextColor(TEXT); presetLbl.setTextSize(16f);
        presetLbl.setPadding(0, dp(4), 0, dp(2));
        root.addView(presetLbl);
        root.addView(presetCard("Night (calm)",   new int[]{78, 18, 30, 12, 38}));
        root.addView(presetCard("Balanced",       new int[]{100, 13, 30, 12, 38}));
        root.addView(presetCard("Bright (day)",   new int[]{100, 10, 26, 10, 34}));
        root.addView(presetCard("Mono (no blue)", new int[]{100, 12, 99, 12, 38}));

        // sliders
        for (int i = 0; i < KEYS.length; i++) root.addView(sliderRow(i));

        Button reset = new Button(this);
        reset.setText("Reset to defaults");
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                for (int i = 0; i < KEYS.length; i++) applyValue(i, Tune.def(KEYS[i]), true);
            }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(16);
        root.addView(reset, rp);

        Button upd = new Button(this);
        upd.setText("Check for updates");
        upd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { ClusterUpdater.checkNow(MapTunerActivity.this); }
        });
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        up.topMargin = dp(8);
        root.addView(upd, up);

        setContentView(scroll);
        renderPreview();
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(TEXT); t.setTextSize(22f); t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    /** A preset row: a thumbnail of the sample map recoloured with the preset, plus its name. */
    private View presetCard(final String name, final int[] vals) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(CARD);
        int p = dp(8); row.setPadding(p, p, p, p);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(8); row.setLayoutParams(rp);

        ImageView thumb = new ImageView(this);
        thumb.setImageBitmap(presetThumb(vals));
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(132), dp(84));
        tp.rightMargin = dp(12);
        row.addView(thumb, tp);

        TextView t = new TextView(this);
        t.setText(name); t.setTextColor(TEXT); t.setTextSize(16f);
        row.addView(t);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                for (int i = 0; i < KEYS.length; i++) applyValue(i, vals[i], true);
            }
        });
        return row;
    }

    /** Recolour the sample map with a preset's explicit values -> thumbnail bitmap. */
    private Bitmap presetThumb(int[] vals) {
        int w = sample.getWidth(), h = sample.getHeight();
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] px = new int[w * h];
        float d = vals[0] / 100f, gm = vals[1] / 10f, bs = vals[2] / 100f, bmg = vals[3] / 100f, bn = vals[4] / 100f;
        for (int i = 0; i < srcPx.length; i++) px[i] = Tune.recolorWith(srcPx[i], d, gm, bs, bmg, bn);
        bm.setPixels(px, 0, w, 0, 0, w, h);
        return bm;
    }

    private View sliderRow(final int idx) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(CARD);
        int p = dp(10);
        box.setPadding(p, p, p, p);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(10);
        box.setLayoutParams(bp);

        TextView label = new TextView(this);
        valLabels[idx] = label;
        label.setTextColor(TEXT); label.setTextSize(15f);
        box.addView(label);

        SeekBar bar = new SeekBar(this);
        bars[idx] = bar;
        bar.setMax(MAXV[idx] - MINV[idx]);
        int cur = Tune.val(KEYS[idx]);
        bar.setProgress(clamp(cur, MINV[idx], MAXV[idx]) - MINV[idx]);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) applyValue(idx, MINV[idx] + progress, false);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        box.addView(bar);
        setLabel(idx, cur);
        return box;
    }

    private void setLabel(int idx, int v) {
        valLabels[idx].setText(LABELS[idx] + ":  " + v);
    }

    /** Apply a value: update Tune (live cluster), the label, optionally the slider, and the preview. */
    private void applyValue(int idx, int v, boolean moveSlider) {
        v = clamp(v, MINV[idx], MAXV[idx]);
        Tune.set(KEYS[idx], v);
        setLabel(idx, v);
        if (moveSlider && bars[idx] != null) bars[idx].setProgress(v - MINV[idx]);
        schedulePreview();
    }

    private final Runnable previewJob = new Runnable() { public void run() { renderPreview(); } };
    private void schedulePreview() {
        ui.removeCallbacks(previewJob);
        ui.postDelayed(previewJob, 50);
    }

    private void renderPreview() {
        if (sample == null) return;
        for (int i = 0; i < srcPx.length; i++) outPx[i] = Tune.recolor(srcPx[i]);
        preview.setPixels(outPx, 0, sample.getWidth(), 0, 0, sample.getWidth(), sample.getHeight());
        previewView.setImageBitmap(preview);
    }

    /** A synthetic sample map: dark land, a bluish (flood-prone) patch, grey roads, a vivid blue route, labels. */
    private void buildSample() {
        int w = 360, h = 220;
        sample = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sample);
        c.drawColor(0xFF20262E);                       // dark-grey land
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // a bluish water/area patch (like Apple's flood-prone blue-grey) - should NOT turn fully blue
        p.setColor(0xFF2B4A66);
        c.drawRect(0, 0, w * 0.42f, h * 0.5f, p);
        // grey roads
        p.setColor(0xFF6E757D); p.setStrokeWidth(dp(5));
        c.drawLine(0, h * 0.62f, w, h * 0.42f, p);
        c.drawLine(w * 0.55f, 0, w * 0.62f, h, p);
        p.setColor(0xFF565C64); p.setStrokeWidth(dp(3));
        c.drawLine(0, h * 0.85f, w, h * 0.80f, p);
        // vivid blue route line (should stay/become launcher blue)
        p.setColor(0xFF2A7BF6); p.setStrokeWidth(dp(8)); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(w * 0.2f, h * 0.9f, w * 0.5f, h * 0.5f, p);
        c.drawLine(w * 0.5f, h * 0.5f, w * 0.85f, h * 0.35f, p);
        // position dot
        p.setColor(0xFF2A7BF6); c.drawCircle(w * 0.2f, h * 0.9f, dp(7), p);
        p.setColor(0xFFCFE4FF); c.drawCircle(w * 0.2f, h * 0.9f, dp(3), p);
        // labels (white text - should become muted grey, not harsh)
        p.setColor(0xFFFFFFFF); p.setTextSize(dp(12));
        c.drawText("High Road", w * 0.3f, h * 0.35f, p);
        c.drawText("Park Side", w * 0.6f, h * 0.7f, p);

        srcPx = new int[w * h];
        sample.getPixels(srcPx, 0, w, 0, 0, w, h);
        outPx = new int[w * h];
        preview = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
