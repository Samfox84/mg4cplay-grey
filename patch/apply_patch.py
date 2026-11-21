#!/usr/bin/env python3
"""
Apply the greyscale-cluster patch to a decompiled MG4CPlay tree.

What it does:
  1. Drops GreyTexListener.smali next to Mg4ClusterMap.smali (a TextureView
     SurfaceTextureListener that feeds the Surface to Mg4ClusterMap.onSurface,
     the same way the old SurfaceHolder.Callback did).
  2. Rewrites Mg4ClusterMap.surfaceVideo() to build a TextureView (instead of a
     SurfaceView) with a full-desaturation ColorMatrix on a hardware layer, so
     the cluster map renders in greyscale.
  3. Flips extractNativeLibs to true (so libs are extracted on install and we
     don't need zipalign for uncompressed-lib alignment).
  4. Bumps versionCode by 1 and tags versionName with "-grey".

Usage: python3 apply_patch.py <decoded_dir> <patch_dir>
"""
import sys, os, re, shutil, glob

decoded, patchdir = sys.argv[1], sys.argv[2]

# 1. locate Mg4ClusterMap.smali (its smali_classesN folder varies by build)
hits = glob.glob(os.path.join(decoded, "smali*", "com/shilapi/xcertplay/Mg4ClusterMap.smali"))
if not hits:
    sys.exit("ERROR: Mg4ClusterMap.smali not found - is this an MG4CPlay build?")
mg4 = hits[0]
pkgdir = os.path.dirname(mg4)
shutil.copy(os.path.join(patchdir, "GreyTexListener.smali"),
            os.path.join(pkgdir, "GreyTexListener.smali"))
print("placed GreyTexListener.smali in", pkgdir)

# 2. rewrite surfaceVideo()
s = open(mg4).read()
start = s.index('.method private final surfaceVideo(Landroid/content/Context;I)Landroid/view/View;')
end = s.index('.end method', start) + len('.end method')
new_method = '''.method private final surfaceVideo(Landroid/content/Context;I)Landroid/view/View;
    .locals 7
    .param p1, "context"    # Landroid/content/Context;
    .param p2, "streamSide"    # I

    new-instance v0, Landroid/view/TextureView;

    invoke-direct {v0, p1}, Landroid/view/TextureView;-><init>(Landroid/content/Context;)V

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/view/TextureView;->setOpaque(Z)V

    new-instance v1, Landroid/graphics/ColorMatrix;

    invoke-direct {v1}, Landroid/graphics/ColorMatrix;-><init>()V

    const v2, 0x3ee66666

    invoke-virtual {v1, v2}, Landroid/graphics/ColorMatrix;->setSaturation(F)V

    new-instance v4, Landroid/graphics/ColorMatrix;

    invoke-direct {v4}, Landroid/graphics/ColorMatrix;-><init>()V

    const v5, 0x3f19999a

    const/high16 v6, 0x3f800000

    invoke-virtual {v4, v5, v5, v5, v6}, Landroid/graphics/ColorMatrix;->setScale(FFFF)V

    invoke-virtual {v1, v4}, Landroid/graphics/ColorMatrix;->postConcat(Landroid/graphics/ColorMatrix;)V

    new-instance v2, Landroid/graphics/Paint;

    invoke-direct {v2}, Landroid/graphics/Paint;-><init>()V

    new-instance v3, Landroid/graphics/ColorMatrixColorFilter;

    invoke-direct {v3, v1}, Landroid/graphics/ColorMatrixColorFilter;-><init>(Landroid/graphics/ColorMatrix;)V

    invoke-virtual {v2, v3}, Landroid/graphics/Paint;->setColorFilter(Landroid/graphics/ColorFilter;)Landroid/graphics/ColorFilter;

    const/4 v3, 0x2

    invoke-virtual {v0, v3, v2}, Landroid/view/TextureView;->setLayerType(ILandroid/graphics/Paint;)V

    new-instance v3, Lcom/shilapi/xcertplay/GreyTexListener;

    invoke-direct {v3, p2}, Lcom/shilapi/xcertplay/GreyTexListener;-><init>(I)V

    check-cast v3, Landroid/view/TextureView$SurfaceTextureListener;

    invoke-virtual {v0, v3}, Landroid/view/TextureView;->setSurfaceTextureListener(Landroid/view/TextureView$SurfaceTextureListener;)V

    check-cast v0, Landroid/view/View;

    return-object v0
.end method'''
open(mg4, "w").write(s[:start] + new_method + s[end:])
print("rewrote surfaceVideo() -> TextureView + greyscale ColorMatrix")

# 3. extractNativeLibs true
man = os.path.join(decoded, "AndroidManifest.xml")
m = open(man).read()
if 'android:extractNativeLibs="false"' in m:
    open(man, "w").write(m.replace('android:extractNativeLibs="false"',
                                   'android:extractNativeLibs="true"'))
    print("extractNativeLibs -> true")

# 4. bump versionCode, tag versionName
yml = os.path.join(decoded, "apktool.yml")
y = open(yml).read()
y = re.sub(r'versionCode: (\d+)', lambda mm: f'versionCode: {int(mm.group(1))+1}', y, count=1)
if '-grey' not in y:
    y = re.sub(r'(versionName: .*?)(\n)', r'\1-grey\2', y, count=1)
open(yml, "w").write(y)
print("bumped versionCode, tagged versionName -grey")
print("PATCH APPLIED OK")
