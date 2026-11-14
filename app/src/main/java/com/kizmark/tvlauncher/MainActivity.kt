package com.kizmark.tvlauncher

import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

enum class Overlay { NONE, APPS, CONTROL }

class LauncherState(ctx: Context) {
    private val prefs = Prefs(ctx)

    var overlay by mutableStateOf(Overlay.NONE)
    var ambient by mutableStateOf(false)

    /** Bir uygulama açılırken true olur: arayüz çekilir, sahne yakınlaşır. Geri dönünce false. */
    var launching by mutableStateOf(false)
    var wallIndex by mutableIntStateOf(prefs.wallpaper())
    var boldClock by mutableStateOf(prefs.boldClock())
    var depth by mutableStateOf(prefs.depth())

    /** Her odak değişiminde artar; kenar parlamasını tetikler. */
    var pulse by mutableIntStateOf(0)
    var favorites by mutableStateOf(prefs.favorites() ?: emptyList())
    var resumeTick by mutableIntStateOf(0)
    var homeTick by mutableIntStateOf(0)

    var lastInput = System.currentTimeMillis()
    var resumed = false
    var wallCount = WALLPAPER_NAMES.size
    private var favoritesSaved = prefs.favorites() != null

    /** İlk açılışta: varsayılanlardan kurulu olanları çubuğa koy. */
    fun ensureFavorites(installed: List<String>) {
        if (favoritesSaved) return
        val set = installed.toSet()
        var list = Config.DEFAULT_FAVORITES.filter { it in set }
        if (list.size < 3) list = (list + installed.filter { it !in list }).take(6)
        favorites = list
        prefs.saveFavorites(list)
        favoritesSaved = true
    }

    fun toggleFavorite(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        prefs.saveFavorites(favorites)
        favoritesSaved = true
    }

    fun moveFavoriteLeft(pkg: String) {
        val i = favorites.indexOf(pkg)
        if (i < 0 || favorites.size < 2) return
        val list = favorites.toMutableList()
        list.removeAt(i)
        list.add(if (i == 0) list.size else i - 1, pkg)
        favorites = list
        prefs.saveFavorites(list)
    }

    fun nextWallpaper() {
        wallIndex = (wallIndex + 1) % wallCount
        prefs.saveWallpaper(wallIndex)
    }

    fun toggleDepth() {
        depth = !depth
        prefs.saveDepth(depth)
    }

    fun toggleBoldClock() {
        boldClock = !boldClock
        prefs.saveBoldClock(boldClock)
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var st: LauncherState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        st = LauncherState(applicationContext)
        // Home tuşuna tekrar basılınca ana ekrana dön
        addOnNewIntentListener {
            st.overlay = Overlay.NONE
            st.ambient = false
            st.launching = false
            st.homeTick += 1
        }
        if (Config.PREFER_4K) requestBestDisplayMode()
        setContent { LauncherApp(st) }
    }

    override fun onResume() {
        super.onResume()
        st.resumed = true
        st.ambient = false
        st.launching = false
        st.lastInput = System.currentTimeMillis()
        st.resumeTick += 1
    }

    /**
     * TV'nin uygulamalara sunduğu en yüksek çözünürlüklü ekran modunu ister (ör. 3840x2160).
     * TV yalnızca 1080p arayüz modu sunuyorsa hiçbir şey değişmez.
     */
    private fun requestBestDisplayMode() {
        try {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay ?: return
            val current = display.mode
            val currentPixels = current.physicalWidth * current.physicalHeight
            var best: Display.Mode? = null
            for (mode in display.supportedModes) {
                val pixels = mode.physicalWidth * mode.physicalHeight
                if (pixels <= currentPixels) continue
                val b = best
                if (b == null) {
                    best = mode
                } else {
                    val bPixels = b.physicalWidth * b.physicalHeight
                    val closer = abs(mode.refreshRate - current.refreshRate) < abs(b.refreshRate - current.refreshRate)
                    if (pixels > bPixels || (pixels == bPixels && closer)) best = mode
                }
            }
            val chosen = best ?: return
            val lp = window.attributes
            lp.preferredDisplayModeId = chosen.modeId
            window.attributes = lp
        } catch (e: Throwable) {
            // Mod değiştirilemiyorsa varsayılan modda devam et
        }
    }

    override fun onPause() {
        super.onPause()
        st.resumed = false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        st.lastInput = System.currentTimeMillis()

        // Saat modundayken herhangi bir tuş geri döndürür
        if (st.ambient) {
            if (event.action == KeyEvent.ACTION_DOWN) st.ambient = false
            return true
        }

        // Kumanda kısayolları (sadece ana ekrandayken çalışır)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_PROG_RED -> {
                    st.overlay = if (st.overlay == Overlay.CONTROL) Overlay.NONE else Overlay.CONTROL
                    return true
                }
                KeyEvent.KEYCODE_PROG_GREEN -> {
                    st.overlay = if (st.overlay == Overlay.APPS) Overlay.NONE else Overlay.APPS
                    return true
                }
                KeyEvent.KEYCODE_PROG_YELLOW -> {
                    st.nextWallpaper()
                    return true
                }
                KeyEvent.KEYCODE_PROG_BLUE -> {
                    st.overlay = Overlay.NONE
                    st.ambient = true
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
