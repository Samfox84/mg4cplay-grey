package com.shilapi.xcertplay;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Live map-recolour tuning, shared between the cluster renderer (ClusterPlus) and
 * the settings screen (MapTunerActivity). Values are volatile so a slider change in
 * settings is picked up by the cluster on its very next frame - no reconnect needed.
 *
 * Source of truth is SharedPreferences (no storage permission needed). For backward
 * compatibility an unset value falls back to /sdcard/mg4cplay-grey.cfg, then a default.
 */
public final class Tune {
    private static final String PREFS = "mg4cplay_tune";
    private static final String CFG = "/sdcard/mg4cplay-grey.cfg";

    public static volatile float dim = 0.88f;
    public static volatile float gamma = 2.0f;
    public static volatile float blueSat = 0.34f;
    public static volatile float blueMargin = 0.14f;
    public static volatile float blueMin = 0.42f;

    private static Context ctx;

    private Tune() {}

    public static synchronized void init(Context c) {
        if (ctx == null) { ctx = c.getApplicationContext(); load(); }
    }

    public static synchronized void load() {
        if (ctx == null) return;
        dim        = val("dim")       / 100f;
        gamma      = val("soft")      / 10f;
        blueSat    = val("bluesat")   / 100f;
        blueMargin = val("bluemargin")/ 100f;
        blueMin    = val("bluemin")   / 100f;
    }

    /** Current integer value of a key (what the slider shows). */
    public static int val(String key) {
        int def = def(key);
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, 0);
            if (sp.contains(key)) return sp.getInt(key, def);
        } catch (Throwable ignore) {}
        int c = cfgFile(key);
        return c != Integer.MIN_VALUE ? c : def;
    }

    public static int def(String key) {
        if ("dim".equals(key)) return 88;
        if ("soft".equals(key)) return 20;
        if ("bluesat".equals(key)) return 34;
        if ("bluemargin".equals(key)) return 14;
        if ("bluemin".equals(key)) return 42;
        return 0;
    }

    /** Set a value from the settings screen; updates the live fields immediately. */
    public static synchronized void set(String key, int v) {
        try { ctx.getSharedPreferences(PREFS, 0).edit().putInt(key, v).apply(); } catch (Throwable ignore) {}
        if ("dim".equals(key)) dim = v / 100f;
        else if ("soft".equals(key)) gamma = v / 10f;
        else if ("bluesat".equals(key)) blueSat = v / 100f;
        else if ("bluemargin".equals(key)) blueMargin = v / 100f;
        else if ("bluemin".equals(key)) blueMin = v / 100f;
    }

    private static int cfgFile(String key) {
        try {
            File f = new File(CFG);
            if (!f.exists()) return Integer.MIN_VALUE;
            BufferedReader r = new BufferedReader(new FileReader(f));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith(key + "=")) return Integer.parseInt(line.substring(key.length() + 1).trim());
                }
            } finally { r.close(); }
        } catch (Throwable ignore) {}
        return Integer.MIN_VALUE;
    }

    // ---- CPU recolour, identical maths to the GLSL shader (for the live preview) ----
    public static int recolor(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float l = 0.299f * r + 0.587f * g + 0.114f * b;
        float mx = Math.max(r, Math.max(g, b));
        float mn = Math.min(r, Math.min(g, b));
        float sat = mx - mn;
        float or, og, ob;
        boolean route = sat > blueSat && b > r + blueMargin && b > g + blueMargin && b > blueMin;
        if (route) {
            float s = 0.75f + 0.35f * l;
            or = 0.310f * s; og = 0.608f * s; ob = 0.941f * s;
        } else {
            float t = (float) Math.pow(clamp01(l), gamma);
            or = lerp(0.055f, 0.47f, t);
            og = lerp(0.067f, 0.51f, t);
            ob = lerp(0.078f, 0.55f, t);
        }
        or *= dim; og *= dim; ob *= dim;
        return 0xFF000000 | (c8(or) << 16) | (c8(og) << 8) | c8(ob);
    }

    private static float clamp01(float x) { return x < 0 ? 0 : (x > 1 ? 1 : x); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static int c8(float v) { int i = Math.round(clamp01(v) * 255f); return i < 0 ? 0 : (i > 255 ? 255 : i); }
}
