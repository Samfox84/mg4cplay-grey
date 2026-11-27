package com.shilapi.xcertplay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * DiPlay cluster extras, injected into MG4CPlay.
 *
 *  - videoView(): replaces the plain cluster SurfaceView with a GLES2 view that
 *    repaints CarPlay's cluster stream into the Calm Launcher palette
 *    (black / grey / white) while keeping the route line blue. If GL set-up
 *    fails for any reason it falls back to the original plain SurfaceView, so
 *    the cluster can never go black or crash because of this code.
 *  - addEta(): draws a live ETA / time-remaining line at the bottom of the
 *    cluster, read from Mg4ClusterNavigation.route by reflection.
 *
 * Everything is tunable live from /sdcard/mg4cplay-grey.cfg (no rebuild):
 *    recolor=1        1 = palette repaint on, 0 = original colours
 *    eta=1            1 = show the ETA line, 0 = hide it
 *    bluesat=22       how saturated a pixel must be to count as "route" (0..100)
 *    bluemargin=8     how much bluer than red/green it must be (0..100)
 * Values are read once per cluster start; change the file then reconnect.
 */
public final class ClusterPlus {
    private static final String TAG = "ClusterPlus";
    private static final String CFG = "/sdcard/mg4cplay-grey.cfg";

    private ClusterPlus() {}

    // ---- config ------------------------------------------------------------
    private static int cfg(String key, int def) {
        try {
            File f = new File(CFG);
            if (!f.exists()) return def;
            BufferedReader r = new BufferedReader(new FileReader(f));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith(key + "=")) {
                        int v = Integer.parseInt(line.substring(key.length() + 1).trim());
                        return v;
                    }
                }
            } finally { r.close(); }
        } catch (Throwable t) { /* ignore - use default */ }
        return def;
    }

    private static void log(String m) { Log.i(TAG, m); }

    // ---- entry point from patched surfaceVideo() ---------------------------
    /** Build the cluster video view. streamSide is the square stream size. */
    public static View videoView(Context ctx, int streamSide) {
        Tune.init(ctx);
        if (cfg("recolor", 1) == 0) {
            log("recolor disabled by cfg; using plain surface");
            return plainSurface(ctx, streamSide);
        }
        try {
            RecolorView v = new RecolorView(ctx, streamSide);
            log("recolor GL view created, streamSide=" + streamSide);
            return v;
        } catch (Throwable t) {
            Log.e(TAG, "GL view failed, falling back to plain surface", t);
            return plainSurface(ctx, streamSide);
        }
    }

    /** Original behaviour: a SurfaceView whose Surface is handed straight to the decoder. */
    private static View plainSurface(Context ctx, final int streamSide) {
        final SurfaceView sv = new SurfaceView(ctx);
        sv.getHolder().setFixedSize(streamSide, streamSide);
        sv.getHolder().addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder h) { feedDecoder(h.getSurface(), "plain"); }
            public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
            public void surfaceDestroyed(SurfaceHolder h) { feedDecoder(null, "plain-gone"); }
        });
        return sv;
    }

    // ---- hand a Surface to Mg4ClusterMap.onSurface (decoder sink) -----------
    private static void feedDecoder(Surface s, String tag) {
        try {
            Class<?> cm = Class.forName("com.shilapi.xcertplay.Mg4ClusterMap");
            Object inst = cm.getField("INSTANCE").get(null);
            Method m;
            try {
                m = cm.getDeclaredMethod("access$onSurface", cm, Surface.class, String.class);
                m.setAccessible(true);
                m.invoke(null, inst, s, tag);
            } catch (NoSuchMethodException e) {
                m = cm.getDeclaredMethod("onSurface", Surface.class, String.class);
                m.setAccessible(true);
                m.invoke(inst, s, tag);
            }
        } catch (Throwable t) {
            Log.e(TAG, "feedDecoder failed", t);
        }
    }

    // ========================================================================
    //  GL recolour view
    // ========================================================================
    private static final class RecolorView extends GLSurfaceView {
        private final RecolorRenderer renderer;
        RecolorView(Context ctx, int streamSide) {
            super(ctx);
            setEGLContextClientVersion(2);
            renderer = new RecolorRenderer(streamSide);
            setRenderer(renderer);
            setRenderMode(RENDERMODE_CONTINUOUSLY);
        }
        @Override protected void onDetachedFromWindow() {
            try { renderer.release(); } catch (Throwable ignore) {}
            super.onDetachedFromWindow();
        }
    }

    private static final String VERT =
        "attribute vec4 aPos;\n" +
        "attribute vec4 aTex;\n" +
        "uniform mat4 uTex;\n" +
        "varying vec2 vUv;\n" +
        "void main(){ gl_Position = aPos; vUv = (uTex * aTex).xy; }\n";

    // Palette repaint: luminance ramp black->road->white, route (saturated blue) kept blue.
    private static final String FRAG =
        "#extension GL_OES_EGL_image_external : require\n" +
        "precision mediump float;\n" +
        "uniform samplerExternalOES uTexUnit;\n" +
        "uniform float uBlueSat;\n" +
        "uniform float uBlueMargin;\n" +
        "uniform float uBlueMin;\n" +
        "uniform float uDim;\n" +
        "uniform float uGamma;\n" +
        "varying vec2 vUv;\n" +
        "const vec3 BLACK = vec3(0.055,0.067,0.078);\n" + // #0E1114 launcher background
        "const vec3 LABEL = vec3(0.47,0.51,0.55);\n" +    // muted grey top - no harsh white
        "const vec3 BLUE  = vec3(0.310,0.608,0.941);\n" + // #4F9BF0 launcher route
        "void main(){\n" +
        "  vec3 c = texture2D(uTexUnit, vUv).rgb;\n" +
        "  float l = dot(c, vec3(0.299,0.587,0.114));\n" +
        "  float mx = max(max(c.r,c.g),c.b);\n" +
        "  float mn = min(min(c.r,c.g),c.b);\n" +
        "  float sat = mx - mn;\n" +
        // strict: only a vivid, saturated, bright, clearly-blue pixel is the route
        "  bool route = (sat > uBlueSat) && (c.b > c.r + uBlueMargin) && (c.b > c.g + uBlueMargin) && (c.b > uBlueMin);\n" +
        "  vec3 outc;\n" +
        "  if (route) {\n" +
        "    outc = BLUE * (0.75 + 0.35*l);\n" +
        "  } else {\n" +
        // everything else: calm dark ramp, gamma keeps mids dark, never reaches white
        "    float t = pow(clamp(l,0.0,1.0), uGamma);\n" +
        "    outc = mix(BLACK, LABEL, t);\n" +
        "  }\n" +
        "  outc = outc * uDim;\n" +
        "  gl_FragColor = vec4(clamp(outc,0.0,1.0), 1.0);\n" +
        "}\n";

    // Passthrough: shown only if the recolour shader fails to compile, so the
    // cluster still shows the normal map instead of going dark.
    private static final String FRAG_PASS =
        "#extension GL_OES_EGL_image_external : require\n" +
        "precision mediump float;\n" +
        "uniform samplerExternalOES uTexUnit;\n" +
        "varying vec2 vUv;\n" +
        "void main(){ gl_FragColor = texture2D(uTexUnit, vUv); }\n";

    private static final class RecolorRenderer implements GLSurfaceView.Renderer {
        private final int streamSide;
        private final float[] texMatrix = new float[16];
        private int program, oesTex = -1;
        private int aPos, aTex, uTex, uUnit, uBlueSat, uBlueMargin, uBlueMin, uDim, uGamma;
        private SurfaceTexture surfaceTexture;
        private Surface inputSurface;
        private volatile boolean frameReady;

        private FloatBuffer posBuf, texBuf;
        private static final float[] POS = { -1,-1, 1,-1, -1,1, 1,1 };
        private static final float[] UV  = {  0, 0, 1, 0,  0,1, 1,1 };

        RecolorRenderer(int streamSide) {
            this.streamSide = streamSide > 0 ? streamSide : 720;
            posBuf = buf(POS); texBuf = buf(UV);
            Matrix.setIdentityM(texMatrix, 0);
        }

        private static FloatBuffer buf(float[] a) {
            FloatBuffer b = ByteBuffer.allocateDirect(a.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
            b.put(a).position(0);
            return b;
        }

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            program = buildProgram(VERT, FRAG);
            if (program == 0) { // recolour shader failed - try passthrough so the map still shows
                Log.e(TAG, "recolour shader failed; trying passthrough");
                program = buildProgram(VERT, FRAG_PASS);
            }
            if (program == 0) { // GL unusable - give the decoder a plain surface instead
                Log.e(TAG, "all shaders failed; reverting to plain surface");
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() { feedDecoder(null, "glfail"); }
                });
                return;
            }
            aPos = GLES20.glGetAttribLocation(program, "aPos");
            aTex = GLES20.glGetAttribLocation(program, "aTex");
            uTex = GLES20.glGetUniformLocation(program, "uTex");
            uUnit = GLES20.glGetUniformLocation(program, "uTexUnit");
            uBlueSat = GLES20.glGetUniformLocation(program, "uBlueSat");
            uBlueMargin = GLES20.glGetUniformLocation(program, "uBlueMargin");
            uBlueMin = GLES20.glGetUniformLocation(program, "uBlueMin");
            uDim = GLES20.glGetUniformLocation(program, "uDim");
            uGamma = GLES20.glGetUniformLocation(program, "uGamma");

            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            oesTex = tex[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            surfaceTexture = new SurfaceTexture(oesTex);
            surfaceTexture.setDefaultBufferSize(streamSide, streamSide);
            surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                public void onFrameAvailable(SurfaceTexture st) { frameReady = true; /* trigger redraw */ }
            });
            inputSurface = new Surface(surfaceTexture);
            log("GL ready; handing input surface to decoder (dim=" + Tune.dim + " gamma=" + Tune.gamma + ")");
            feedDecoder(inputSurface, "recolor");
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        public void onDrawFrame(GL10 gl) {
            if (surfaceTexture == null || program == 0) return;
            try {
                surfaceTexture.updateTexImage();
                surfaceTexture.getTransformMatrix(texMatrix);
            } catch (Throwable t) { /* no new frame yet - redraw last */ }
            GLES20.glClearColor(0.055f, 0.067f, 0.078f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex);
            GLES20.glUniform1i(uUnit, 0);
            GLES20.glUniformMatrix4fv(uTex, 1, false, texMatrix, 0);
            GLES20.glUniform1f(uBlueSat, Tune.blueSat);
            GLES20.glUniform1f(uBlueMargin, Tune.blueMargin);
            GLES20.glUniform1f(uBlueMin, Tune.blueMin);
            GLES20.glUniform1f(uDim, Tune.dim);
            GLES20.glUniform1f(uGamma, Tune.gamma);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, posBuf);
            GLES20.glEnableVertexAttribArray(aTex);
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texBuf);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(aPos);
            GLES20.glDisableVertexAttribArray(aTex);
        }

        void release() {
            feedDecoder(null, "recolor-gone");
            try { if (surfaceTexture != null) surfaceTexture.release(); } catch (Throwable ignore) {}
            try { if (inputSurface != null) inputSurface.release(); } catch (Throwable ignore) {}
            surfaceTexture = null; inputSurface = null;
        }

        private int buildProgram(String vs, String fs) {
            int v = compile(GLES20.GL_VERTEX_SHADER, vs);
            int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
            if (v == 0 || f == 0) return 0;
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, v);
            GLES20.glAttachShader(p, f);
            GLES20.glLinkProgram(p);
            int[] ok = new int[1];
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
            if (ok[0] == 0) { Log.e(TAG, "link: " + GLES20.glGetProgramInfoLog(p)); GLES20.glDeleteProgram(p); return 0; }
            return p;
        }

        private int compile(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) { Log.e(TAG, "compile: " + GLES20.glGetShaderInfoLog(s)); GLES20.glDeleteShader(s); return 0; }
            return s;
        }
    }

    // ========================================================================
    //  ETA overlay
    // ========================================================================
    /** Add an ETA / time-remaining line at the bottom of the cluster root. */
    public static void addEta(FrameLayout root, Context ctx) {
        try {
            if (cfg("eta", 1) == 0) { log("eta disabled by cfg"); return; }
            final TextView tv = new TextView(ctx);
            tv.setTextColor(0xFFEEF2F6);
            tv.setTextSize(22f);
            tv.setShadowLayer(6f, 0f, 2f, 0xC0000000);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(24, 8, 24, 10);
            tv.setVisibility(View.GONE);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = 18;
            root.addView(tv, lp);

            final Handler h = new Handler(Looper.getMainLooper());
            h.post(new Runnable() {
                public void run() {
                    try {
                        String s = etaText();
                        if (s == null) { tv.setVisibility(View.GONE); }
                        else { tv.setText(s); tv.setVisibility(View.VISIBLE); }
                    } catch (Throwable ignore) { tv.setVisibility(View.GONE); }
                    h.postDelayed(this, 1000);
                }
            });
            log("eta line added");
        } catch (Throwable t) {
            Log.e(TAG, "addEta failed", t);
        }
    }

    /** Read Mg4ClusterNavigation.route by reflection and format the ETA line, or null if no active route. */
    private static Object navRoute;
    private static Field fActive, fArrival, fRemain;
    private static boolean navReflectReady;
    private static String etaText() throws Exception {
        if (!navReflectReady) {
            Class<?> nav = Class.forName("com.shilapi.xcertplay.vehicle.Mg4ClusterNavigation");
            Field rf = nav.getDeclaredField("route"); rf.setAccessible(true);
            navRoute = rf.get(null);
            Class<?> rgs = navRoute.getClass();
            fActive = rgs.getDeclaredField("routeActive"); fActive.setAccessible(true);
            fArrival = rgs.getDeclaredField("arrivalEpochSeconds"); fArrival.setAccessible(true);
            fRemain = rgs.getDeclaredField("remainingSeconds"); fRemain.setAccessible(true);
            navReflectReady = true;
        }
        if (navRoute == null) return null;
        boolean active = fActive.getBoolean(navRoute);
        if (!active) return null;
        Long arrival = (Long) fArrival.get(navRoute);
        Long remain = (Long) fRemain.get(navRoute);
        StringBuilder sb = new StringBuilder();
        if (arrival != null && arrival > 0) {
            long ms = arrival * 1000L;
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(ms);
            sb.append(String.format("ETA %02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)));
        }
        if (remain != null && remain > 0) {
            long mins = Math.round(remain / 60.0);
            if (sb.length() > 0) sb.append("   ");
            if (mins >= 60) sb.append(String.format("%dh %02dm", mins / 60, mins % 60));
            else sb.append(mins).append(" min");
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
