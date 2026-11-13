package com.kizmark.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// AYARLAR - buradan özelleştir
// ---------------------------------------------------------------------------
object Config {
    /** true yaparsan arka plandaki ışıklar yavaşça hareket eder (zayıf TV'lerde kasabilir). */
    const val ANIMATE_BACKGROUND = false

    /** Alt çubukta görünecek uygulamalar, sırasıyla. Kurulu olmayanlar otomatik atlanır. */
    val FAVORITES = listOf(
        "com.google.android.youtube.tv",          // YouTube
        "com.netflix.ninja",                      // Netflix
        "com.disney.disneyplus",                  // Disney+
        "com.amazon.amazonvideo.livingroom",      // Prime Video
        "com.spotify.tv.android",                 // Spotify
        "com.stremio.one",                        // Stremio
        "com.android.vending"                     // Play Store
    )
}

// ---------------------------------------------------------------------------
// Veri
// ---------------------------------------------------------------------------
enum class Glyph { GRID, SLIDERS }

class AppItem(
    val label: String,
    val image: ImageBitmap?,
    val isBanner: Boolean,
    val glyph: Glyph?,
    val launch: () -> Unit
)

class LauncherData(val favorites: List<AppItem>, val all: List<AppItem>)

private fun queryApps(ctx: Context): List<ResolveInfo> {
    val pm = ctx.packageManager
    val leanback = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
    )
    val normal = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
    )
    return (leanback + normal)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
}

private fun toItem(ctx: Context, ri: ResolveInfo): AppItem? {
    return try {
        val pm = ctx.packageManager
        val pkg = ri.activityInfo.packageName
        val banner = ri.activityInfo.loadBanner(pm)
        val isBanner = banner != null
        val drawable = banner ?: ri.loadIcon(pm)
        val bmp = if (isBanner) drawable.toBitmap(320, 180) else drawable.toBitmap(192, 192)
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        AppItem(ri.loadLabel(pm).toString(), bmp.asImageBitmap(), isBanner, null) {
            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(it) }
            }
        }
    } catch (e: Throwable) {
        null
    }
}

private fun loadData(ctx: Context): LauncherData {
    val pm = ctx.packageManager
    val all = queryApps(ctx).sortedBy { it.loadLabel(pm).toString().lowercase() }
    val byPkg = all.associateBy { it.activityInfo.packageName }
    val fav = Config.FAVORITES.mapNotNull { byPkg[it] }
    val favInfos = if (fav.size >= 3) fav else (fav + all.filter { it !in fav }).take(6)
    return LauncherData(
        favorites = favInfos.mapNotNull { toItem(ctx, it) },
        all = all.mapNotNull { toItem(ctx, it) }
    )
}

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    private var homeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Home tuşuna tekrar basılınca başa dön
        addOnNewIntentListener { homeTick++ }
        setContent { LauncherApp(resumeTick, homeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

// ---------------------------------------------------------------------------
// Arayüz
// ---------------------------------------------------------------------------
@Composable
fun LauncherApp(resumeTick: Int, homeTick: Int) {
    val ctx = LocalContext.current
    val data by produceState<LauncherData?>(null, resumeTick) {
        value = withContext(Dispatchers.Default) { loadData(ctx) }
    }
    var showAll by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(homeTick) { showAll = false }
    BackHandler(enabled = true) { if (showAll) showAll = false }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(5000)
        }
    }

    val dockItems = remember(data) {
        val d = data
        if (d == null) emptyList() else d.favorites + listOf(
            AppItem("Tüm uygulamalar", null, false, Glyph.GRID) { showAll = true },
            AppItem("Ayarlar", null, false, Glyph.SLIDERS) {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF05070D))) {
        Background()
        Hero(now)
        if (!showAll && dockItems.isNotEmpty()) {
            Dock(dockItems, focusKey = homeTick to dockItems.size)
        }
        if (showAll) {
            AllApps(data?.all ?: emptyList()) { showAll = false }
        }
    }
}

@Composable
fun Background() {
    val t: State<Float> = if (Config.ANIMATE_BACKGROUND) {
        rememberInfiniteTransition(label = "bg").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Reverse),
            label = "bgT"
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        val w = size.width
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF070B16), Color(0xFF0C1630), Color(0xFF070B16))
            )
        )
        val c1 = Offset(w * (0.15f + 0.25f * p), h * 0.25f)
        val c2 = Offset(w * (0.85f - 0.25f * p), h * 0.55f)
        val c3 = Offset(w * 0.5f, h * (0.95f - 0.15f * p))
        drawCircle(
            Brush.radialGradient(listOf(Color(0x553A7BFF), Color.Transparent), center = c1, radius = w * 0.45f),
            radius = w * 0.45f, center = c1
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0x447A4DFF), Color.Transparent), center = c2, radius = w * 0.40f),
            radius = w * 0.40f, center = c2
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0x3320C4B0), Color.Transparent), center = c3, radius = w * 0.45f),
            radius = w * 0.45f, center = c3
        )
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Günaydın"
    in 12..17 -> "İyi günler"
    in 18..22 -> "İyi akşamlar"
    else -> "İyi geceler"
}

@Composable
fun Hero(now: Long) {
    val cfg = LocalConfiguration.current
    val minute = now / 60000
    val time = remember(minute) { SimpleDateFormat("HH:mm", Locale("tr")).format(Date(now)) }
    val date = remember(minute) { SimpleDateFormat("EEEE, d MMMM", Locale("tr")).format(Date(now)) }
    val hello = remember(minute) { greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }

    Column(Modifier.padding(start = 72.dp, top = 48.dp)) {
        BasicText(
            hello,
            style = TextStyle(color = Color(0xB3FFFFFF), fontSize = 26.sp, fontWeight = FontWeight.Medium)
        )
        BasicText(
            time,
            style = TextStyle(
                color = Color.White,
                fontSize = (cfg.screenWidthDp * 0.12f).sp,
                fontWeight = FontWeight.Thin
            )
        )
        BasicText(
            date,
            style = TextStyle(color = Color(0xCCFFFFFF), fontSize = 26.sp, fontWeight = FontWeight.Normal)
        )
    }
}

@Composable
fun BoxScope.Dock(items: List<AppItem>, focusKey: Any) {
    val cfg = LocalConfiguration.current
    val tileW = (cfg.screenWidthDp * 0.15f).dp
    val first = remember { FocusRequester() }
    var label by remember { mutableStateOf(items.firstOrNull()?.label ?: "") }
    LaunchedEffect(focusKey) {
        delay(80)
        runCatching { first.requestFocus() }
    }
    val panel = RoundedCornerShape(40.dp)
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            label,
            modifier = Modifier.padding(bottom = 14.dp),
            style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium),
            maxLines = 1
        )
        Box(
            Modifier
                .padding(horizontal = 48.dp)
                .fillMaxWidth()
                .clip(panel)
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x33FFFFFF), panel)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(items) { i, item ->
                    Tile(item, tileW, if (i == 0) first else null) { label = it }
                }
            }
        }
    }
}

@Composable
fun Tile(item: AppItem, w: Dp, requester: FocusRequester?, onFocus: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.14f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tileScale"
    )
    val shape = RoundedCornerShape(w * 0.12f)
    var m = Modifier
        .width(w)
        .height(w * 9 / 16)
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(if (focused) 28.dp else 0.dp, shape)
        .clip(shape)
        .background(Color(0x26FFFFFF))
    if (focused) m = m.border(3.dp, Color.White, shape)
    if (requester != null) m = m.focusRequester(requester)
    m = m
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocus(item.label)
        }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { item.launch() }

    Box(m, contentAlignment = Alignment.Center) {
        TileContent(item, w * 9 / 16)
    }
}

@Composable
fun TileContent(item: AppItem, h: Dp) {
    val img = item.image
    when {
        img != null && item.isBanner -> Image(
            bitmap = img,
            contentDescription = item.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        img != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bitmap = img, contentDescription = item.label, modifier = Modifier.size(h * 0.5f))
            Spacer(Modifier.height(4.dp))
            BasicText(
                item.label,
                modifier = Modifier.padding(horizontal = 6.dp),
                style = TextStyle(color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        item.glyph != null -> GlyphIcon(item.glyph, h * 0.42f)
    }
}

@Composable
fun GlyphIcon(g: Glyph, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = this.size.minDimension
        when (g) {
            Glyph.GRID -> {
                val d = s / 3f
                for (i in 0..2) for (j in 0..2) {
                    drawCircle(Color.White, radius = d * 0.2f, center = Offset(d * (i + 0.5f), d * (j + 0.5f)))
                }
            }
            Glyph.SLIDERS -> {
                val ys = listOf(0.2f, 0.5f, 0.8f)
                val xs = listOf(0.3f, 0.7f, 0.4f)
                for (k in 0..2) {
                    drawLine(
                        Color(0x99FFFFFF),
                        Offset(0f, s * ys[k]), Offset(s, s * ys[k]),
                        strokeWidth = s * 0.07f, cap = StrokeCap.Round
                    )
                    drawCircle(Color.White, radius = s * 0.11f, center = Offset(s * xs[k], s * ys[k]))
                }
            }
        }
    }
}

@Composable
fun AllApps(apps: List<AppItem>, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { first.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color(0xEB070B16)).padding(horizontal = 64.dp, vertical = 40.dp)) {
        Column {
            BasicText(
                "Tüm uygulamalar",
                style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
            )
            Spacer(Modifier.height(24.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(apps) { i, app ->
                    AppCell(app, if (i == 0) first else null) {
                        app.launch()
                        onClose()
                    }
                }
            }
        }
    }
}

@Composable
fun AppCell(item: AppItem, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cellScale")
    val shape = RoundedCornerShape(18.dp)
    var m = Modifier
        .fillMaxWidth()
        .height(116.dp)
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(if (focused) Color(0x33FFFFFF) else Color(0x14FFFFFF))
    if (focused) m = m.border(2.dp, Color.White, shape)
    if (requester != null) m = m.focusRequester(requester)
    m = m
        .onFocusChanged { focused = it.isFocused }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

    Column(m.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        item.image?.let {
            if (item.isBanner) {
                Image(
                    bitmap = it, contentDescription = item.label, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp))
                )
            } else {
                Image(bitmap = it, contentDescription = item.label, modifier = Modifier.size(60.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            item.label,
            style = TextStyle(color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
