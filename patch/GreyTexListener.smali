.class public final Lcom/shilapi/xcertplay/GreyTexListener;
.super Ljava/lang/Object;
.source "GreyTexListener.java"

# interfaces
.implements Landroid/view/TextureView$SurfaceTextureListener;


# instance fields
.field private final streamSide:I


# direct methods
.method public constructor <init>(I)V
    .locals 0
    .param p1, "streamSide"    # I

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput p1, p0, Lcom/shilapi/xcertplay/GreyTexListener;->streamSide:I

    return-void
.end method


# virtual methods
.method public onSurfaceTextureAvailable(Landroid/graphics/SurfaceTexture;II)V
    .locals 5
    .param p1, "st"       # Landroid/graphics/SurfaceTexture;
    .param p2, "width"    # I
    .param p3, "height"   # I

    iget v0, p0, Lcom/shilapi/xcertplay/GreyTexListener;->streamSide:I

    invoke-virtual {p1, v0, v0}, Landroid/graphics/SurfaceTexture;->setDefaultBufferSize(II)V

    new-instance v1, Landroid/view/Surface;

    invoke-direct {v1, p1}, Landroid/view/Surface;-><init>(Landroid/graphics/SurfaceTexture;)V

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "overlay "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    const-string v3, "x"

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    sget-object v4, Lcom/shilapi/xcertplay/Mg4ClusterMap;->INSTANCE:Lcom/shilapi/xcertplay/Mg4ClusterMap;

    invoke-static {v4, v1, v2}, Lcom/shilapi/xcertplay/Mg4ClusterMap;->access$onSurface(Lcom/shilapi/xcertplay/Mg4ClusterMap;Landroid/view/Surface;Ljava/lang/String;)V

    return-void
.end method

.method public onSurfaceTextureDestroyed(Landroid/graphics/SurfaceTexture;)Z
    .locals 3
    .param p1, "st"    # Landroid/graphics/SurfaceTexture;

    sget-object v0, Lcom/shilapi/xcertplay/Mg4ClusterMap;->INSTANCE:Lcom/shilapi/xcertplay/Mg4ClusterMap;

    const/4 v1, 0x0

    const-string v2, ""

    invoke-static {v0, v1, v2}, Lcom/shilapi/xcertplay/Mg4ClusterMap;->access$onSurface(Lcom/shilapi/xcertplay/Mg4ClusterMap;Landroid/view/Surface;Ljava/lang/String;)V

    const/4 v0, 0x1

    return v0
.end method

.method public onSurfaceTextureSizeChanged(Landroid/graphics/SurfaceTexture;II)V
    .locals 0
    .param p1, "st"       # Landroid/graphics/SurfaceTexture;
    .param p2, "width"    # I
    .param p3, "height"   # I

    return-void
.end method

.method public onSurfaceTextureUpdated(Landroid/graphics/SurfaceTexture;)V
    .locals 0
    .param p1, "st"    # Landroid/graphics/SurfaceTexture;

    return-void
.end method
