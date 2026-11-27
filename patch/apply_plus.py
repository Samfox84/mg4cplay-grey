#!/usr/bin/env python3
"""
Apply the DiPlay cluster-plus patch (palette recolour GL shader + ETA line) to a
decompiled MG4CPlay tree.

  python3 apply_plus.py <decoded_dir> <smali_src_dir>

<smali_src_dir> holds the compiled ClusterPlus*.smali (from javac->d8->baksmali).
"""
import sys, os, re, glob, shutil

decoded, smali_src = sys.argv[1], sys.argv[2]

# 1. locate Mg4ClusterMap.smali and its smali_classesN dir
hits = glob.glob(os.path.join(decoded, "smali*", "com/shilapi/xcertplay/Mg4ClusterMap.smali"))
if not hits:
    sys.exit("ERROR: Mg4ClusterMap.smali not found")
mg4 = hits[0]
pkgdir = os.path.dirname(mg4)

# 2. drop our injected classes' smali next to it
n = 0
for prefix in ("ClusterPlus", "Tune", "MapTunerActivity", "ClusterUpdater"):
    for f in glob.glob(os.path.join(smali_src, "com/shilapi/xcertplay/%s*.smali" % prefix)):
        shutil.copy(f, os.path.join(pkgdir, os.path.basename(f)))
        n += 1
if n == 0:
    sys.exit("ERROR: no injected smali in " + smali_src)
print("placed %d injected smali files in %s" % (n, pkgdir))

# 3. replace surfaceVideo() body -> return ClusterPlus.videoView(context, streamSide)
s = open(mg4).read()
sig = '.method private final surfaceVideo(Landroid/content/Context;I)Landroid/view/View;'
start = s.index(sig)
end = s.index('.end method', start) + len('.end method')
new_method = sig + '''
    .locals 1
    .param p1, "context"    # Landroid/content/Context;
    .param p2, "streamSide"    # I

    invoke-static {p1, p2}, Lcom/shilapi/xcertplay/ClusterPlus;->videoView(Landroid/content/Context;I)Landroid/view/View;

    move-result-object v0

    return-object v0
.end method'''
s = s[:start] + new_method + s[end:]
print("rewrote surfaceVideo() -> ClusterPlus.videoView()")

# 4. in attach$lambda$4, add ClusterPlus.addEta(frameLayout, context) right after the
#    FrameLayout.addView(video) call. The FrameLayout is in v0, context is p1.
m = re.search(r'\.method static final attach\$lambda\$4\(ILandroid/content/Context;\)Landroid/view/View;.*?\.end method', s, re.S)
if not m:
    sys.exit("ERROR: attach$lambda$4 not found")
block = m.group(0)
addview = 'invoke-virtual {v1, v3, v4}, Landroid/widget/FrameLayout;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V'
if addview not in block:
    sys.exit("ERROR: addView call not found in attach$lambda$4")
inject = addview + '\n\n    invoke-static {v0, p1}, Lcom/shilapi/xcertplay/ClusterPlus;->addEta(Landroid/widget/FrameLayout;Landroid/content/Context;)V'
new_block = block.replace(addview, inject, 1)
s = s.replace(block, new_block, 1)
print("injected ClusterPlus.addEta() into attach$lambda$4")

open(mg4, "w").write(s)

# 5. extractNativeLibs true + register the Map tuner activity (launchable)
man = os.path.join(decoded, "AndroidManifest.xml")
mm = open(man).read()
if 'android:extractNativeLibs="false"' in mm:
    mm = mm.replace('android:extractNativeLibs="false"', 'android:extractNativeLibs="true"')
    print("extractNativeLibs -> true")
tuner = ('<activity android:name="com.shilapi.xcertplay.MapTunerActivity" '
         'android:exported="true" android:label="MG4CPlay Map" '
         'android:theme="@android:style/Theme.DeviceDefault">'
         '<intent-filter><action android:name="android.intent.action.MAIN"/>'
         '<category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>')
if 'MapTunerActivity' not in mm:
    mm = mm.replace('</application>', tuner + '</application>', 1)
    print("registered MapTunerActivity (launchable)")
open(man, "w").write(mm)

# 6. repoint the built-in OtaUpdater from the upstream repo to ours
otas = glob.glob(os.path.join(decoded, "smali*", "com/shilapi/xcertplay/OtaUpdater.smali"))
if otas:
    o = open(otas[0]).read()
    o = o.replace("kennyvv/DiPlay-MG4-Plus", "Samfox84/mg4cplay-grey")
    open(otas[0], "w").write(o)
    print("repointed OtaUpdater -> Samfox84/mg4cplay-grey")

# 7. start the robust auto-updater (ClusterUpdater) from DiPlayActivity.onCreate
dpa = glob.glob(os.path.join(decoded, "smali*", "com/shilapi/xcertplay/DiPlayActivity.smali"))
if dpa:
    d = open(dpa[0]).read()
    anchor = "invoke-super {p0, p1}, Landroidx/activity/ComponentActivity;->onCreate(Landroid/os/Bundle;)V"
    if anchor in d and "ClusterUpdater;->start" not in d:
        inject = anchor + "\n\n    invoke-static {p0}, Lcom/shilapi/xcertplay/ClusterUpdater;->start(Landroid/content/Context;)V"
        d = d.replace(anchor, inject, 1)
        open(dpa[0], "w").write(d)
        print("wired ClusterUpdater.start() into DiPlayActivity.onCreate")

# 8. bump versionCode, make versionName unique per build (so update checks detect it)
yml = os.path.join(decoded, "apktool.yml")
y = open(yml).read()
newcode = [None]
def _bump(mm):
    newcode[0] = int(mm.group(1)) + 1
    return f'versionCode: {newcode[0]}'
y = re.sub(r'versionCode: (\d+)', _bump, y, count=1)
# strip any previous -plusNNN tag, then add the current one
y = re.sub(r'(versionName: [^\n]*?)(-plus\d*)?(\n)', lambda mm: f'{mm.group(1)}-plus{newcode[0]}{mm.group(3)}', y, count=1)
open(yml, "w").write(y)
print(f"versionCode -> {newcode[0]}, versionName tagged -plus{newcode[0]}")
print("PLUS PATCH APPLIED OK")
