package com.kizmark.tvlauncher

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// AYARLAR - buradan özelleştir
// ---------------------------------------------------------------------------
object Config {
    /** İlk açılışta dock'un başına gelecek uygulamalar (kalanlar alfabetik arkadan gelir). */
    val DEFAULT_ORDER = listOf(
        "com.google.android.youtube.tv",          // YouTube
        "com.netflix.ninja",                      // Netflix
        "com.disney.disneyplus",                  // Disney+
        "com.amazon.amazonvideo.livingroom",      // Prime Video
        "com.exxen.android",                      // Exxen
        "com.turkcell.ott",                       // TV+
        "com.digiturk.iq.mobil",                  // beIN / Digiturk
        "com.apple.atve.androidtv.appletv",       // Apple TV
        "com.google.android.youtube.tvmusic",     // YouTube Music
        "com.spotify.tv.android",                 // Spotify
        "com.stremio.one"                         // Stremio
    )

    /** Dock'ta aynı anda görünen uygulama sayısı. */
    const val DOCK_VISIBLE = 5

    /** Bu kadar dakika tuşa basılmazsa saat moduna geçer. 0 = kapalı. */
    const val AMBIENT_AFTER_MIN = 5

    /** true: TV uygulamalara 4K ekran modu sunuyorsa arayüz o modda çizilir. */
    const val PREFER_4K = true

    /** Canlı TV uygulaması (Vestel / MediaTek). */
    const val LIVE_TV_PKG = "com.mediatek.tv.oneworld.tvcenter"
}

// ---------------------------------------------------------------------------
// Uygulamalar
// Liste hızlı çıkarılır; kutucuk görselleri arka planda, bir kez, yuvarlatılmış olarak hazırlanır.
// ---------------------------------------------------------------------------
class AppItem(val pkg: String, val label: String, val intent: Intent?, val system: Boolean) {
    var tile by mutableStateOf<ImageBitmap?>(null)
    var tileKey = 0
}

object AppRepo {
    private val items = HashMap<String, AppItem>()

    /** Yüklü uygulamalar (başlatılabilir olanlar). Var olan öğeler korunur, görselleri yeniden çizilmez. */
    @Synchronized
    fun list(ctx: Context, changedPkg: String? = null): List<AppItem> {
        if (changedPkg != null) items.remove(changedPkg)
        val pm = ctx.packageManager
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        )
        val normal = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val infos: List<ResolveInfo> = (leanback + normal)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
        val alive = HashSet<String>()
        val out = ArrayList<AppItem>(infos.size)
        for (ri in infos) {
            val pkg = ri.activityInfo.packageName
            alive += pkg
            val old = items[pkg]
            if (old != null) {
                out += old
                continue
            }
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val sys = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val item = AppItem(pkg, ri.loadLabel(pm).toString(), intent, sys)
            items[pkg] = item
            out += item
        }
        items.keys.retainAll(alive)
        return out.sortedBy { it.label.lowercase() }
    }

    /** Kutucuk görsellerini hazırlar (arka plan iş parçacığında çağır). Önce verilen sıradakiler. */
    fun renderTiles(ctx: Context, ordered: List<AppItem>, wPx: Int, hPx: Int) {
        val key = wPx * 10000 + hPx
        val pm = ctx.packageManager
        for (item in ordered) {
            if (item.tile != null && item.tileKey == key) continue
            val bmp = try {
                renderTile(ctx, pm, item, wPx, hPx)
            } catch (e: Throwable) {
                null
            } ?: continue
            bmp.prepareToDraw()
            item.tileKey = key
            item.tile = bmp.asImageBitmap()
        }
    }

    private fun renderTile(ctx: Context, pm: android.content.pm.PackageManager, item: AppItem, w: Int, h: Int): Bitmap? {
        val ai = pm.getApplicationInfo(item.pkg, 0)
        val comp = item.intent?.component
        val banner = try {
            (if (comp != null) pm.getActivityInfo(comp, 0).loadBanner(pm) else null) ?: ai.loadBanner(pm)
        } catch (e: Throwable) {
            null
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = h * 0.16f
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) }
        c.clipPath(clip)
        if (banner != null) {
            banner.setBounds(0, 0, w, h)
            banner.draw(c)
        } else {
            val icon = ai.loadIcon(pm)
            val iconBmp = icon.toBitmap(96, 96)
            val base = dominantColor(iconBmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                shade(base, 0.62f), shade(base, 0.38f), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            val s = (h * 0.46f).roundToInt()
            icon.setBounds((w - s) / 2, (h * 0.12f).roundToInt(), (w + s) / 2, (h * 0.12f).roundToInt() + s)
            icon.draw(c)
            val tp = Paint(Paint.ANTI_ALIAS_FLAG)
            tp.color = 0xF2FFFFFF.toInt()
            tp.textSize = h * 0.15f
            tp.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            tp.textAlign = Paint.Align.CENTER
            val label = TextUtils.ellipsize(
                item.label, android.text.TextPaint(tp), w * 0.88f, TextUtils.TruncateAt.END
            ).toString()
            c.drawText(label, w / 2f, h * 0.86f, tp)
        }
        return out
    }

    private fun dominantColor(b: Bitmap): Int {
        val hsv = FloatArray(3)
        var best = 0xFF3A4660.toInt()
        var bestScore = 0f
        val step = 6
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val px = b.getPixel(x, y)
                if ((px ushr 24) > 200) {
                    android.graphics.Color.colorToHSV(px, hsv)
                    val score = hsv[1] * hsv[2]
                    if (score > bestScore) {
                        bestScore = score
                        best = px
                    }
                }
                x += step
            }
            y += step
        }
        return best
    }

    private fun shade(color: Int, v: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 0.85f).coerceAtMost(0.75f)
        hsv[2] = v
        return android.graphics.Color.HSVToColor(hsv)
    }
}

/** Uygulamayı açar. options: kutucuktan büyüyerek açılma animasyonu. */
fun launchApp(ctx: Context, item: AppItem, options: Bundle? = null): Boolean {
    val intent = item.intent ?: return false
    return try {
        ctx.startActivity(intent, options)
        true
    } catch (e: Throwable) {
        runCatching { ctx.startActivity(intent) }.isSuccess
    }
}

fun launchOptions(view: View, x: Int, y: Int, w: Int, h: Int): Bundle? {
    return try {
        ActivityOptions.makeScaleUpAnimation(view, x, y, w.coerceAtLeast(1), h.coerceAtLeast(1)).toBundle()
    } catch (e: Throwable) {
        null
    }
}

fun launchPackage(ctx: Context, pkg: String): Boolean {
    val pm = ctx.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { ctx.startActivity(intent) }.isSuccess
}

/** Sıradaki ilk çalışan intent'i açar. */
fun startFirst(ctx: Context, vararg intents: Intent): Boolean {
    for (i in intents) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isSuccess) return true
    }
    return false
}

fun openSettings(ctx: Context, vararg actions: String) {
    val list = actions.map { Intent(it) } + Intent(Settings.ACTION_SETTINGS)
    startFirst(ctx, *list.toTypedArray())
}

fun openAppInfo(ctx: Context, pkg: String) {
    startFirst(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
}

fun uninstallApp(ctx: Context, pkg: String) {
    startFirst(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
}

// ---------------------------------------------------------------------------
// Kalıcı ayarlar
// ---------------------------------------------------------------------------
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun order(): List<String>? = sp.getString("order", null)?.split(",")?.filter { it.isNotBlank() }
    fun saveOrder(list: List<String>) = sp.edit().putString("order", list.joinToString(",")).apply()

    fun hidden(): Set<String> = sp.getString("hidden", "")!!.split(",").filter { it.isNotBlank() }.toSet()
    fun saveHidden(set: Set<String>) = sp.edit().putString("hidden", set.joinToString(",")).apply()

    fun wallpaper(): Int = sp.getInt("wall", 0)
    fun saveWallpaper(index: Int) = sp.edit().putInt("wall", index).apply()

    fun boldClock(): Boolean = sp.getBoolean("bold", true)
    fun saveBoldClock(bold: Boolean) = sp.edit().putBoolean("bold", bold).apply()

    fun depth(): Boolean = sp.getBoolean("depth", true)
    fun saveDepth(on: Boolean) = sp.edit().putBoolean("depth", on).apply()

    fun adbReady(): Boolean = sp.getBoolean("adb", false)
    fun saveAdbReady(on: Boolean) = sp.edit().putBoolean("adb", on).apply()

    fun sleepAt(): Long = sp.getLong("sleepAt", 0L)
    fun saveSleepAt(t: Long) = sp.edit().putLong("sleepAt", t).apply()
}

// ---------------------------------------------------------------------------
// Sistem durumu (kontrol merkezindeki alt yazılar)
// ---------------------------------------------------------------------------
class NetInfo(val title: String, val sub: String, val glyph: Glyph)

fun networkInfo(ctx: Context): NetInfo {
    return try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> NetInfo("Wi-Fi", "Bağlı değil", Glyph.WIFI)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetInfo("Ethernet", "Bağlı", Glyph.ETHERNET)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val level = when {
                    caps.signalStrength >= -60 -> "Güçlü"
                    caps.signalStrength >= -72 -> "İyi"
                    caps.signalStrength > -200 -> "Zayıf"
                    else -> "Bağlı"
                }
                NetInfo("Wi-Fi", if (online) level else "İnternet yok", Glyph.WIFI)
            }
            else -> NetInfo("Ağ", "Bağlı", Glyph.WIFI)
        }
    } catch (e: Throwable) {
        NetInfo("Ağ", "", Glyph.WIFI)
    }
}

fun audioInfo(ctx: Context): String {
    return try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val types = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        val out = when {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in types -> "Bluetooth"
            Build.VERSION.SDK_INT >= 31 && AudioDeviceInfo.TYPE_HDMI_EARC in types -> "Ses sistemi"
            AudioDeviceInfo.TYPE_HDMI_ARC in types -> "Ses sistemi"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES in types || AudioDeviceInfo.TYPE_WIRED_HEADSET in types -> "Kulaklık"
            AudioDeviceInfo.TYPE_USB_DEVICE in types || AudioDeviceInfo.TYPE_USB_HEADSET in types -> "USB ses"
            else -> "TV hoparlörü"
        }
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val v = am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        "$out · %$v"
    } catch (e: Throwable) {
        "Ses"
    }
}

fun bluetoothOn(ctx: Context): Boolean? {
    return try {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bm.adapter?.isEnabled
    } catch (e: Throwable) {
        null
    }
}

fun freeRamMb(ctx: Context): Long {
    return try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.availMem / (1024 * 1024)
    } catch (e: Throwable) {
        0L
    }
}

// ---------------------------------------------------------------------------
// TV girişleri (HDMI, anten)
// ---------------------------------------------------------------------------
class TvSource(val id: String, val label: String, val passthrough: Boolean, val type: Int)

fun tvSources(ctx: Context): List<TvSource> {
    return try {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        tim.tvInputList
            .filter { !it.isHidden(ctx) }
            .filter { it.parentId == null || it.type == TvInputInfo.TYPE_HDMI }
            .map {
                val custom = it.loadCustomLabel(ctx)?.toString()
                val label = if (!custom.isNullOrBlank()) custom else it.loadLabel(ctx).toString()
                TvSource(it.id, label, it.isPassthroughInput, it.type)
            }
            .distinctBy { it.label }
            .sortedWith(compareBy({ !it.passthrough }, { it.label }))
    } catch (e: Throwable) {
        emptyList()
    }
}

fun openSource(ctx: Context, src: TvSource?) {
    if (src != null && src.passthrough) {
        if (startFirst(ctx, Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(src.id)))) return
    }
    if (launchPackage(ctx, Config.LIVE_TV_PKG)) return
    startFirst(ctx, Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI))
}

// ---------------------------------------------------------------------------
// Uyku zamanlayıcısı (süre dolunca TV'yi bekleme moduna alır; ADB izni gerekir)
// ---------------------------------------------------------------------------
object SleepTimer {
    val STEPS = intArrayOf(0, 15, 30, 60, 90, 120)

    fun set(ctx: Context, minutes: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 7, Intent(ctx, SleepReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        val prefs = Prefs(ctx)
        if (minutes <= 0) {
            prefs.saveSleepAt(0L)
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        prefs.saveSleepAt(at)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun remainingMin(ctx: Context): Int {
        val at = Prefs(ctx).sleepAt()
        if (at <= 0L) return 0
        val left = at - System.currentTimeMillis()
        return if (left <= 0) 0 else ((left + 59_999) / 60_000).toInt()
    }
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                Prefs(ctx).saveSleepAt(0L)
                Adb.exec(ctx, "input keyevent 223", allowPrompt = false)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

// ---------------------------------------------------------------------------
// Bildirimler ve çalan müzik (bildirim erişimi izni gerekir, kurulum scripti verir)
// ---------------------------------------------------------------------------
object NotifBus {
    var version by mutableIntStateOf(0)
}

class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        NotifBus.version++
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        NotifBus.version++
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        NotifBus.version++
    }

    companion object {
        @Volatile
        var instance: MediaListener? = null
    }
}

class NotifItem(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val intent: PendingIntent?,
    val clearable: Boolean
)

fun readNotifications(ctx: Context): List<NotifItem> {
    val svc = MediaListener.instance ?: return emptyList()
    return try {
        val pm = ctx.packageManager
        svc.activeNotifications
            .filter { it.packageName != ctx.packageName }
            .filter { (it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE) == 0 }
            .mapNotNull { sbn ->
                val ex = sbn.notification.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                val app = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                }.getOrDefault(sbn.packageName)
                NotifItem(sbn.key, app, title, text, sbn.notification.contentIntent, sbn.isClearable)
            }
            .sortedByDescending { it.key }
    } catch (e: Throwable) {
        emptyList()
    }
}

fun dismissNotification(key: String?) {
    val svc = MediaListener.instance ?: return
    runCatching { if (key == null) svc.cancelAllNotifications() else svc.cancelNotification(key) }
}

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
                if (raw == null) null else Bitmap.createScaledBitmap(raw, 160, 160, true).asImageBitmap()
            } catch (e: Throwable) {
                null
            }
        }
        return MediaInfo(title, artist, playing, art, controller)
    } catch (e: Throwable) {
        return null
    }
}
