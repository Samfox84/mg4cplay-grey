# MG4CPlay — greyscale cluster

Private, personal project. A small patch over **MG4CPlay 0.2.9-mg4.96-swi68.14**
(the working MG4 / SWI68 CarPlay build) that renders the **instrument-cluster map
in greyscale**, for reduced visual load (AuDHD sensory comfort).

MG4CPlay itself is not mine and has no public source — this repo only holds the
original APK (as a fallback), the greyscale change, and a script to re-apply it.

## What's here

| Path | What |
| --- | --- |
| `base/MG4CPlay_0.2.9-mg4.96-swi68.14.apk` | **The original working build — the fall-back.** Reinstall this if a grey build ever misbehaves. |
| `dist/MG4CPlay-grey-b138.apk` | The current greyscale build (versionCode 138), ready to install. |
| `patch/GreyTexListener.smali` | New class: a `TextureView.SurfaceTextureListener` that hands the video Surface to `Mg4ClusterMap.onSurface`. |
| `patch/apply_patch.py` | Applies the change to a decompiled tree. |
| `build.sh` | One command: decompile a base APK → apply greyscale → rebuild → sign. |
| `keys/grey.p12` | Throwaway signing key (pass `greypass`). Committed on purpose so every grey build is signed with the **same** key and updates install over each other. |
| `tools/Sign.java` | Reference signer (apksig). `build.sh` uses `apksigner`/`jarsigner`. |

## How the greyscale works

MG4CPlay draws the cluster map on a **SurfaceView**, whose video sits outside the
normal view-drawing pipeline — a colour filter can't touch it. The patch swaps it
for a **TextureView** (which *does* render in that pipeline) and puts a
full-desaturation `ColorMatrix` on a hardware layer. The Surface is fed to the
decoder exactly as before, via `Mg4ClusterMap.onSurface(surface, label)`.

> Caveat: this relies on the MG4 cluster display accepting a TextureView. The app
> already uses TextureView for the main CarPlay screen, so it should — but if a
> future firmware refuses it, the cluster could go black; reinstall `base/` and
> revert.

## Rebuild (e.g. when a newer MG4CPlay comes out)

```sh
./build.sh path/to/new-MG4CPlay.apk     # or no arg to rebuild from base/
# -> dist/MG4CPlay-grey.apk
```

Needs: `java` (JDK 17+), `python3`, `curl`. Then host `dist/MG4CPlay-grey.apk` on
the launcher's DiPlay download (`releases/DiPlay.apk` in `mg4-calm-launcher`) and
bump `releases/DiPlay.json` so the launcher offers it.

## Install on the car

1. Uninstall the current DiPlay/MG4CPlay (different signature — can't update over).
2. Install the grey build from the launcher's **DiPlay** box, or sideload
   `dist/MG4CPlay-grey-b138.apk`.
3. Connect CarPlay and check the dash.

## Credit / licence

Built on **MG4CPlay**, itself based on **xcertplay** (GPL-3.0). This repo is
private and for personal use on my own car. The bundled accessory identity comes
from the original build; nothing here is for redistribution.
