package com.kizmark.tvlauncher

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale

// ---------------------------------------------------------------------------
// AYARLAR - buradan özelleştir
// ---------------------------------------------------------------------------
object Config {
    /** İlk açılışta alt çubuğa konacak uygulamalar. Sonradan TV'den değiştirilebilir. */
    val DEFAULT_FAVORITES = listOf(
        "com.google.android.youtube.tv",          // YouTube
        "com.netflix.ninja",                      // Netflix
        "com.disney.disneyplus",                  // Disney+
        "com.amazon.amazonvideo.livingroom",      // Prime Video
        "com.spotify.tv.android",                 // Spotify
        "com.stremio.one"                         // Stremio
    )

    /** Bu kadar dakika tuşa basılmazsa saat moduna geçer. 0 = kapalı. */
    const val AMBIENT_AFTER_MIN = 5

    /**
     * true: TV uygulamalara 4K (veya daha yüksek) bir ekran modu sunuyorsa arayüz o modda çizilir.
     * Launcher'a girip çıkarken ekran kısa süre kararıyorsa false yap.
     */
    const val PREFER_4K = true
}

// ---------------------------------------------------------------------------
// Uygulamalar
// ---------------------------------------------------------------------------
class AppItem(
    val pkg: String,
    val label: String,
    val image: ImageBitmap,
    val isBanner: Boolean,
    val intent: Intent?
)

object AppRepo {
    private var signature: List<String> = emptyList()
    private var cache: List<AppItem> = emptyList()

    @Synchronized
    fun load(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        )
        val normal = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val infos = (leanback + normal)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
        // Görseller ekran yoğunluğuna göre yüklenir: 4K modda kutucuklar bulanık kalmaz
        val density = ctx.resources.displayMetrics.density
        val bannerW = (176f * density).toInt().coerceIn(320, 800)
        val iconSize = (96f * density).toInt().coerceIn(192, 384)
        val newSignature = infos.map { it.activityInfo.packageName }.sorted() + ("@" + bannerW)
        if (newSignature == signature && cache.isNotEmpty()) return cache
        val items = infos.mapNotNull { toItem(ctx, it, bannerW, iconSize) }.sortedBy { it.label.lowercase() }
        signature = newSignature
        cache = items
        return items
    }

    private fun toItem(ctx: Context, ri: ResolveInfo, bannerW: Int, iconSize: Int): AppItem? {
        return try {
            val pm = ctx.packageManager
            val pkg = ri.activityInfo.packageName
            val banner = ri.activityInfo.loadBanner(pm)
            val isBanner = banner != null
            val drawable = banner ?: ri.loadIcon(pm)
            val bmp = if (isBanner) drawable.toBitmap(bannerW, bannerW * 9 / 16) else drawable.toBitmap(iconSize, iconSize)
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            AppItem(pkg, ri.loadLabel(pm).toString(), bmp.asImageBitmap(), isBanner, intent)
        } catch (e: Throwable) {
            null
        }
    }
}

/** Uygulamayı açar. options: açılış animasyonu (kutucuktan büyüyerek). Açılamazsa false döner. */
fun launchApp(ctx: Context, item: AppItem, options: Bundle? = null): Boolean {
    val intent = item.intent ?: return false
    return try {
        ctx.startActivity(intent, options)
        true
    } catch (e: Throwable) {
        runCatching { ctx.startActivity(intent) }.isSuccess
    }
}

/** Açılan uygulamanın penceresi, basılan kutucuğun yerinden büyüyerek gelir. */
fun launchOptions(view: View, x: Float, y: Float, w: Float, h: Float): Bundle? {
    return try {
        ActivityOptions.makeScaleUpAnimation(
            view, x.toInt(), y.toInt(), w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)
        ).toBundle()
    } catch (e: Throwable) {
        null
    }
}

fun launchPackage(ctx: Context, pkg: String) {
    val pm = ctx.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }
}

fun openSettings(ctx: Context, action: String) {
    val ok = runCatching {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!ok && action != Settings.ACTION_SETTINGS) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

// ---------------------------------------------------------------------------
// Kalıcı ayarlar
// ---------------------------------------------------------------------------
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun favorites(): List<String>? {
        val raw = sp.getString("fav", null) ?: return null
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun saveFavorites(list: List<String>) {
        sp.edit().putString("fav", list.joinToString(",")).apply()
    }

    fun wallpaper(): Int = sp.getInt("wall", 0)

    fun saveWallpaper(index: Int) {
        sp.edit().putInt("wall", index).apply()
    }

    fun boldClock(): Boolean = sp.getBoolean("bold", true)

    fun saveBoldClock(bold: Boolean) {
        sp.edit().putBoolean("bold", bold).apply()
    }

    fun depth(): Boolean = sp.getBoolean("depth", true)

    fun saveDepth(on: Boolean) {
        sp.edit().putBoolean("depth", on).apply()
    }
}

// ---------------------------------------------------------------------------
// Sistem bilgisi
// ---------------------------------------------------------------------------
fun systemInfo(ctx: Context): String {
    return try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val stat = StatFs(Environment.getDataDirectory().path)
        val ram = mi.availMem / 1.0e9
        val disk = stat.availableBytes / 1.0e9
        String.format(Locale.US, "%.1f GB RAM boş  ·  %.1f GB depolama boş", ram, disk)
    } catch (e: Throwable) {
        ""
    }
}

fun networkLabel(ctx: Context): String {
    return try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "Bağlı değil"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> "Bağlı"
        }
    } catch (e: Throwable) {
        "Ağ"
    }
}

// ---------------------------------------------------------------------------
// Duvar kağıdı dosyaları (app/src/main/res/drawable-nodpi/)
//   wallpaper.jpg     : arka plan fotoğrafı
//   wallpaper_fg.png  : aynı fotoğrafın saydam zeminli ön planı (derinlik efekti için)
// ---------------------------------------------------------------------------
fun loadDrawableBitmap(ctx: Context, name: String): ImageBitmap? {
    return try {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (id == 0) {
            null
        } else {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeResource(ctx.resources, id, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 3840) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            opts.inScaled = false
            BitmapFactory.decodeResource(ctx.resources, id, opts)?.asImageBitmap()
        }
    } catch (e: Throwable) {
        null
    }
}

/** Fotoğraftaki baskın canlı rengi bulur; arayüzün vurgu rengi olur. */
fun accentFromBitmap(image: ImageBitmap): Color {
    return try {
        val small = Bitmap.createScaledBitmap(image.asAndroidBitmap(), 48, 27, true)
        val hsv = FloatArray(3)
        var sx = 0.0
        var sy = 0.0
        var total = 0.0
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                android.graphics.Color.colorToHSV(small.getPixel(x, y), hsv)
                val weight = (hsv[1] * hsv[1] * hsv[2]).toDouble()
                val angle = Math.toRadians(hsv[0].toDouble())
                sx += Math.cos(angle) * weight
                sy += Math.sin(angle) * weight
                total += weight
            }
        }
        if (total < 0.5) {
            Color(0xFF8FB4FF)
        } else {
            var hue = Math.toDegrees(Math.atan2(sy, sx)).toFloat()
            if (hue < 0f) hue += 360f
            Color.hsv(hue.coerceIn(0f, 359.9f), 0.70f, 0.84f)
        }
    } catch (e: Throwable) {
        Color(0xFF34D399)
    }
}

// ---------------------------------------------------------------------------
// Çalan müzik (bildirim erişimi izni gerekir, README'ye bak)
// ---------------------------------------------------------------------------
class MediaListener : NotificationListenerService()

class MediaInfo(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: ImageBitmap?,
    val controller: MediaController
)

fun hasMediaAccess(ctx: Context): Boolean {
    return try {
        val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        enabled != null && enabled.contains(ctx.packageName)
    } catch (e: Throwable) {
        false
    }
}

fun readMedia(ctx: Context, old: MediaInfo?): MediaInfo? {
    if (!hasMediaAccess(ctx)) return null
    try {
        val manager = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = manager.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        val controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
            ?: return null
        val md = controller.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        if (title.isBlank()) return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        val sameTrack = old != null && old.title == title && old.artist == artist
        if (old != null && sameTrack && old.playing == playing &&
            old.controller.sessionToken == controller.sessionToken
        ) {
            return old
        }
        var art: ImageBitmap? = if (sameTrack) old?.art else null
        if (art == null) {
            art = try {
                val raw = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                if (raw == null) null else Bitmap.createScaledBitmap(raw, 200, 200, true).asImageBitmap()
            } catch (e: Throwable) {
                null
            }
        }
        return MediaInfo(title, artist, playing, art, controller)
    } catch (e: Throwable) {
        return null
    }
}
