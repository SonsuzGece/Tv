package com.kizmark.tvlauncher

import android.app.ActivityManager
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
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.ui.graphics.ImageBitmap
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
        val newSignature = infos.map { it.activityInfo.packageName }.sorted()
        if (newSignature == signature && cache.isNotEmpty()) return cache
        val items = infos.mapNotNull { toItem(ctx, it) }.sortedBy { it.label.lowercase() }
        signature = newSignature
        cache = items
        return items
    }

    private fun toItem(ctx: Context, ri: ResolveInfo): AppItem? {
        return try {
            val pm = ctx.packageManager
            val pkg = ri.activityInfo.packageName
            val banner = ri.activityInfo.loadBanner(pm)
            val isBanner = banner != null
            val drawable = banner ?: ri.loadIcon(pm)
            val bmp = if (isBanner) drawable.toBitmap(400, 225) else drawable.toBitmap(192, 192)
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            AppItem(pkg, ri.loadLabel(pm).toString(), bmp.asImageBitmap(), isBanner, intent)
        } catch (e: Throwable) {
            null
        }
    }
}

fun launchApp(ctx: Context, item: AppItem) {
    val intent = item.intent ?: return
    runCatching { ctx.startActivity(intent) }
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

    fun boldClock(): Boolean = sp.getBoolean("bold", false)

    fun saveBoldClock(bold: Boolean) {
        sp.edit().putBoolean("bold", bold).apply()
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
// Kendi duvar kağıdın: app/src/main/res/drawable-nodpi/wallpaper.jpg (veya .png)
// ---------------------------------------------------------------------------
fun loadCustomWallpaper(ctx: Context): ImageBitmap? {
    return try {
        val id = ctx.resources.getIdentifier("wallpaper", "drawable", ctx.packageName)
        if (id == 0) {
            null
        } else {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeResource(ctx.resources, id, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 2400) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            opts.inScaled = false
            BitmapFactory.decodeResource(ctx.resources, id, opts)?.asImageBitmap()
        }
    } catch (e: Throwable) {
        null
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
