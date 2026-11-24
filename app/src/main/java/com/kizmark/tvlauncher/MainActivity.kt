package com.kizmark.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
import kotlin.random.Random

enum class Overlay { NONE, APPS, CONTROL }
enum class SheetKind { INPUTS, NOTIFS, POWER, APP_MENU, WALLPAPER }

class LauncherState(val ctx: Context) {
    val prefs = Prefs(ctx)

    var overlay by mutableStateOf(Overlay.NONE)
    var sheet by mutableStateOf<SheetKind?>(null)
    var sheetApp by mutableStateOf<AppItem?>(null)
    var sheetFromDock = false
    var ambient by mutableStateOf(false)
    var resumed by mutableStateOf(false)

    /** Tüm uygulamalar (alfabetik) ve kullanıcının sırası. Dock bu sıradan oluşur. */
    var apps by mutableStateOf<List<AppItem>>(emptyList())
    var order by mutableStateOf(prefs.order() ?: emptyList())
    var hidden by mutableStateOf(prefs.hidden())
    var appsVersion by mutableIntStateOf(0)
    var changedPkg: String? = null

    var dockIndex by mutableIntStateOf(0)
    var dockFirst by mutableIntStateOf(0)
    var moving by mutableStateOf(false)

    var wallIndex by mutableIntStateOf(prefs.wallpaper())
    var randomWall by mutableStateOf(prefs.randomWall())
    var wallItems by mutableStateOf<List<WallpaperItem>>(emptyList())
    var boldClock by mutableStateOf(prefs.boldClock())
    var depth by mutableStateOf(prefs.depth())
    var wallCount = 1

    var toast by mutableStateOf<String?>(null)
    var toastId by mutableIntStateOf(0)
    var sys by mutableStateOf<Map<String, Boolean>?>(null)
    var adbBusy by mutableStateOf(false)
    var homeTick by mutableIntStateOf(0)
    var refocusTick by mutableIntStateOf(0)

    var lastInput = System.currentTimeMillis()

    fun initWallpapers(items: List<WallpaperItem>) {
        wallItems = items
        wallCount = items.size.coerceAtLeast(1)
        if (randomWall) {
            pickRandomWallpaper()
        } else {
            wallIndex = ((wallIndex % wallCount) + wallCount) % wallCount
        }
    }

    /** Rastgele havuzdan bir öncekiyle farklı bir duvar kağıdı seçer. */
    fun pickRandomWallpaper() {
        val items = wallItems
        if (items.isEmpty()) return
        val pool = items.indices.filter { items[it].inRandomPool }.ifEmpty { items.indices.toList() }
        val last = prefs.lastRandomWall()
        val candidates = if (pool.size > 1) {
            pool.filter { it != last && it != wallIndex }.ifEmpty { pool.filter { it != last } }
        } else {
            pool
        }
        val chosen = candidates[Random.nextInt(candidates.size)]
        wallIndex = chosen
        prefs.saveLastRandomWall(chosen)
    }

    fun selectWallpaper(index: Int) {
        val count = wallCount.coerceAtLeast(1)
        val idx = ((index % count) + count) % count
        randomWall = false
        prefs.saveRandomWall(false)
        wallIndex = idx
        prefs.saveWallpaper(idx)
    }

    fun enableRandomWallpaper() {
        randomWall = true
        prefs.saveRandomWall(true)
        pickRandomWallpaper()
    }

    fun currentWallpaperName(): String {
        val items = wallItems
        if (items.isEmpty()) return ""
        val idx = ((wallIndex % items.size) + items.size) % items.size
        return items[idx].name
    }

    /** Uygulama listesi değişince sırayı günceller: yeni uygulamalar sona eklenir. */
    fun syncOrder(list: List<AppItem>) {
        val installed = list.map { it.pkg }.toSet()
        val base = if (order.isEmpty()) {
            Config.DEFAULT_ORDER.filter { it in installed }
        } else {
            order.filter { it in installed }
        }
        val rest = list.map { it.pkg }.filter { it !in base }
        val next = base + rest
        if (next != order) {
            order = next
            prefs.saveOrder(next)
        }
    }

    fun dockApps(): List<AppItem> {
        val by = apps.associateBy { it.pkg }
        return order.mapNotNull { by[it] }.filter { it.pkg !in hidden }
    }

    fun allAppsOrdered(): List<AppItem> {
        val by = apps.associateBy { it.pkg }
        val shown = order.mapNotNull { by[it] }
        return shown.filter { it.pkg !in hidden } + shown.filter { it.pkg in hidden }
    }

    /** Dock'taki iki komşu uygulamanın yerini değiştirir (Taşı modu). */
    fun swap(a: String, b: String) {
        val list = order.toMutableList()
        val i = list.indexOf(a)
        val j = list.indexOf(b)
        if (i < 0 || j < 0) return
        list[i] = b
        list[j] = a
        order = list
    }

    fun finishMove() {
        moving = false
        prefs.saveOrder(order)
        say("Sıralama kaydedildi")
    }

    fun moveToFront(pkg: String) {
        order = listOf(pkg) + order.filter { it != pkg }
        prefs.saveOrder(order)
        dockIndex = 0
        dockFirst = 0
    }

    fun toggleHidden(pkg: String) {
        hidden = if (pkg in hidden) hidden - pkg else hidden + pkg
        prefs.saveHidden(hidden)
    }

    fun nextWallpaper() {
        val count = wallCount.coerceAtLeast(1)
        val next = (wallIndex + 1) % count
        selectWallpaper(next)
        val name = currentWallpaperName()
        if (name.isNotEmpty()) say("Duvar kağıdı: $name")
    }

    fun toggleDepth() {
        depth = !depth
        prefs.saveDepth(depth)
    }

    fun toggleBoldClock() {
        boldClock = !boldClock
        prefs.saveBoldClock(boldClock)
    }

    fun say(msg: String) {
        toast = msg
        toastId += 1
    }

    fun closeSheet() {
        sheet = null
        sheetApp = null
        refocusTick += 1
    }

    fun goHome() {
        val alreadyHome = overlay == Overlay.NONE && sheet == null && !ambient
        overlay = Overlay.NONE
        sheet = null
        sheetApp = null
        ambient = false
        if (moving) finishMove()
        if (alreadyHome) {
            dockIndex = 0
            dockFirst = 0
        }
        homeTick += 1
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var st: LauncherState

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            st.changedPkg = intent.data?.schemeSpecificPart
            st.appsVersion += 1
        }
    }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (st.randomWall) st.pickRandomWallpaper()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        st = LauncherState(applicationContext)
        st.initWallpapers(WALLPAPER_CATALOG)
        addOnNewIntentListener { st.goHome() }
        if (Config.PREFER_4K) requestBestDisplayMode()
        setContent { LauncherApp(st) }
        // Sahne tüm ekranı kapladığı için pencere zemini boşuna çizilmesin
        window.setBackgroundDrawable(null)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val screenFilter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
            registerReceiver(screenOnReceiver, screenFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
            registerReceiver(screenOnReceiver, screenFilter)
        }

        // APK güncellemesinden sonra Android varsayılan Home kaydını sıfırlayabilir; sessizce onar
        if (st.prefs.adbReady() && !SysToggles.isDefaultHome(applicationContext)) {
            Thread { SysToggles.repairHome(applicationContext, allowPrompt = false, allowDisableGtv = false) }.start()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::st.isInitialized) {
            st.refocusTick += 1
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        runCatching { unregisterReceiver(screenOnReceiver) }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        st.resumed = true
        st.ambient = false
        st.lastInput = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        st.resumed = false
    }

    /** TV'nin uygulamalara sunduğu en yüksek çözünürlüklü ekran modunu ister. */
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        st.lastInput = System.currentTimeMillis()

        // Saat modundayken herhangi bir tuş geri döndürür
        if (st.ambient) {
            if (event.action == KeyEvent.ACTION_UP) st.ambient = false
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && st.sheet == null) {
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
