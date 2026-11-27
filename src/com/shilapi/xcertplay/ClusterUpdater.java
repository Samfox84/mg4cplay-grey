package com.shilapi.xcertplay;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Self-updater for MG4CPlay, injected alongside ClusterPlus.
 *
 * On app start it checks the GitHub repo's latest release every 30s for the first
 * 5 minutes, then stops. If the release's versionCode (its tag, e.g. "147") is
 * higher than the installed one, it downloads the attached APK and installs it
 * silently with PackageInstaller (works because the app is platform-signed).
 *
 * checkNow() runs one check immediately - wired to the manual button.
 *
 * Tunable from /sdcard/mg4cplay-grey.cfg:
 *    update=1          1 = auto-update on, 0 = off
 *    updaterepo=Samfox84/mg4cplay-grey
 */
public final class ClusterUpdater {
    private static final String TAG = "ClusterUpdater";
    private static final String CFG = "/sdcard/mg4cplay-grey.cfg";
    private static final String DEFAULT_REPO = "Samfox84/mg4cplay-grey";
    private static final int POLL_MS = 30000;      // every 30s
    private static final int POLL_COUNT = 10;      // for 5 minutes
    private static final String ACTION_RESULT = "com.shilapi.xcertplay.UPDATE_RESULT";

    private static boolean started;
    private static Handler bg;
    private static Context appCtx;

    private ClusterUpdater() {}

    private static String cfgStr(String key, String def) {
        try {
            java.io.File f = new java.io.File(CFG);
            if (!f.exists()) return def;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith(key + "=")) return line.substring(key.length() + 1).trim();
                }
            } finally { r.close(); }
        } catch (Throwable ignore) {}
        return def;
    }

    private static int cfgInt(String key, int def) {
        try { return Integer.parseInt(cfgStr(key, Integer.toString(def))); } catch (Throwable t) { return def; }
    }

    /** Called once at app start. Polls every 30s for 5 minutes. */
    public static synchronized void start(Context ctx) {
        if (started) return;
        started = true;
        appCtx = ctx.getApplicationContext();
        if (cfgInt("update", 1) == 0) { Log.i(TAG, "auto-update disabled by cfg"); return; }
        HandlerThread t = new HandlerThread("mg4cplay-update");
        t.start();
        bg = new Handler(t.getLooper());
        bg.post(new Runnable() {
            int n = 0;
            public void run() {
                try { checkOnce(false); } catch (Throwable e) { Log.e(TAG, "poll failed", e); }
                n++;
                if (n < POLL_COUNT) bg.postDelayed(this, POLL_MS);
                else Log.i(TAG, "update polling window finished");
            }
        });
        Log.i(TAG, "auto-update polling started (every 30s x " + POLL_COUNT + ")");
    }

    /** Manual check (from the settings button). Shows a toast with the result. */
    public static void checkNow(final Context ctx) {
        appCtx = ctx.getApplicationContext();
        if (bg == null) {
            HandlerThread t = new HandlerThread("mg4cplay-update-manual");
            t.start();
            bg = new Handler(t.getLooper());
        }
        toast("Checking for updates…");
        bg.post(new Runnable() {
            public void run() {
                try { checkOnce(true); } catch (Throwable e) { toast("Update check failed"); Log.e(TAG, "manual check failed", e); }
            }
        });
    }

    private static void toast(final String s) {
        try {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() { try { Toast.makeText(appCtx, s, Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {} }
            });
        } catch (Throwable ignore) {}
    }

    private static int installedCode() {
        try {
            PackageInfo pi = appCtx.getPackageManager().getPackageInfo(appCtx.getPackageName(), 0);
            return pi.versionCode;
        } catch (Throwable t) { return Integer.MAX_VALUE; }
    }

    private static void checkOnce(boolean manual) throws Exception {
        String repo = cfgStr("updaterepo", DEFAULT_REPO);
        // Same pattern as the Calm Launcher: a latest.json + APK committed under releases/.
        String base = "https://raw.githubusercontent.com/" + repo + "/main/releases/";
        JSONObject j = getJson(base + "latest.json");
        int releaseCode = j.optInt("versionCode", -1);
        String apkName = j.optString("apk", "");
        int have = installedCode();
        Log.i(TAG, "latest.json versionCode=" + releaseCode + " installed=" + have + " apk=" + apkName);
        if (apkName.isEmpty() || releaseCode < 0) { if (manual) toast("No update info found"); return; }
        if (releaseCode <= have) { if (manual) toast("Already up to date (" + have + ")"); return; }
        toast("Updating to " + releaseCode + "…");
        byte[] apk = download(base + apkName);
        if (apk.length < 1000 || !(apk[0] == 0x50 && apk[1] == 0x4b)) { toast("Download failed"); return; }
        installApk(apk);
    }

    private static JSONObject getJson(String url) throws Exception {
        byte[] b = download(url);
        return new JSONObject(new String(b, "UTF-8"));
    }

    private static byte[] download(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("User-Agent", "MG4CPlay-Updater");
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            return download(loc);
        }
        if (code != 200) { c.disconnect(); throw new Exception("HTTP " + code); }
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        in.close();
        c.disconnect();
        return out.toByteArray();
    }

    private static void installApk(byte[] apk) throws Exception {
        PackageInstaller pi = appCtx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(appCtx.getPackageName());
        // request a silent replace where the platform allows it
        try {
            Field f = PackageInstaller.SessionParams.class.getField("installFlags");
            int flags = f.getInt(p);
            flags |= 0x00000002; // INSTALL_REPLACE_EXISTING
            flags |= 0x00000080; // INSTALL_ALLOW_DOWNGRADE (harmless; lets re-install)
            f.setInt(p, flags);
        } catch (Throwable ignore) {}
        int id = pi.createSession(p);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            java.io.OutputStream o = s.openWrite("mg4cplay", 0, apk.length);
            o.write(apk);
            s.fsync(o);
            o.close();
            Intent intent = new Intent(ACTION_RESULT).setPackage(appCtx.getPackageName());
            PendingIntent pend = PendingIntent.getBroadcast(appCtx, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingImmutable());
            s.commit(pend.getIntentSender());
            Log.i(TAG, "install committed");
        } finally {
            s.close();
        }
    }

    private static int pendingImmutable() {
        // FLAG_MUTABLE/IMMUTABLE only exist from API 23/31; 0x02000000 = FLAG_IMMUTABLE
        try { return android.os.Build.VERSION.SDK_INT >= 23 ? 0x02000000 : 0; } catch (Throwable t) { return 0; }
    }
}
