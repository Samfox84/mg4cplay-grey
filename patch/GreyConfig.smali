.class public Lcom/shilapi/xcertplay/GreyConfig;
.super Ljava/lang/Object;
.source "GreyConfig.java"


# direct methods
.method public constructor <init>()V
    .registers 1

    .line 5
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static dim()F
    .registers 2

    .line 7
    const-string v0, "dim"

    const/16 v1, 0x3c

    invoke-static {v0, v1}, Lcom/shilapi/xcertplay/GreyConfig;->read(Ljava/lang/String;I)I

    move-result v0

    int-to-float v0, v0

    const/high16 v1, 0x42c80000    # 100.0f

    div-float/2addr v0, v1

    return v0
.end method

.method private static read(Ljava/lang/String;I)I
    .registers 6

    .line 9
    nop

    .line 11
    const/4 v0, 0x0

    :try_start_2
    new-instance v1, Ljava/io/File;

    const-string v2, "/sdcard/mg4cplay-grey.cfg"

    invoke-direct {v1, v2}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 12
    invoke-virtual {v1}, Ljava/io/File;->exists()Z

    move-result v2

    if-nez v2, :cond_11

    .line 26
    nop

    .line 12
    return p1

    .line 13
    :cond_11
    new-instance v2, Ljava/io/BufferedReader;

    new-instance v3, Ljava/io/FileReader;

    invoke-direct {v3, v1}, Ljava/io/FileReader;-><init>(Ljava/io/File;)V

    invoke-direct {v2, v3}, Ljava/io/BufferedReader;-><init>(Ljava/io/Reader;)V
    :try_end_1b
    .catchall {:try_start_2 .. :try_end_1b} :catchall_66

    .line 15
    :cond_1b
    :try_start_1b
    invoke-virtual {v2}, Ljava/io/BufferedReader;->readLine()Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_5f

    .line 16
    invoke-virtual {v0}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v0

    .line 17
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v3, "="

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v1

    if-eqz v1, :cond_1b

    .line 18
    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result p0

    add-int/lit8 p0, p0, 0x1

    invoke-virtual {v0, p0}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object p0

    invoke-static {p0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

    move-result p0
    :try_end_50
    .catchall {:try_start_1b .. :try_end_50} :catchall_63

    .line 19
    if-gez p0, :cond_53

    const/4 p0, 0x0

    .line 20
    :cond_53
    const/16 p1, 0x64

    if-le p0, p1, :cond_58

    move p0, p1

    .line 21
    :cond_58
    nop

    .line 26
    :try_start_59
    invoke-virtual {v2}, Ljava/io/BufferedReader;->close()V
    :try_end_5c
    .catchall {:try_start_59 .. :try_end_5c} :catchall_5d

    goto :goto_5e

    :catchall_5d
    move-exception p1

    .line 21
    :goto_5e
    return p0

    .line 26
    :cond_5f
    :try_start_5f
    invoke-virtual {v2}, Ljava/io/BufferedReader;->close()V

    goto :goto_6f

    .line 24
    :catchall_63
    move-exception p0

    move-object v0, v2

    goto :goto_67

    :catchall_66
    move-exception p0

    .line 26
    :goto_67
    if-eqz v0, :cond_6f

    invoke-virtual {v0}, Ljava/io/BufferedReader;->close()V
    :try_end_6c
    .catchall {:try_start_5f .. :try_end_6c} :catchall_6d

    goto :goto_6f

    :catchall_6d
    move-exception p0

    .line 27
    nop

    .line 28
    :cond_6f
    :goto_6f
    return p1
.end method

.method public static sat()F
    .registers 2

    .line 6
    const-string v0, "sat"

    const/16 v1, 0x1e

    invoke-static {v0, v1}, Lcom/shilapi/xcertplay/GreyConfig;->read(Ljava/lang/String;I)I

    move-result v0

    int-to-float v0, v0

    const/high16 v1, 0x42c80000    # 100.0f

    div-float/2addr v0, v1

    return v0
.end method
