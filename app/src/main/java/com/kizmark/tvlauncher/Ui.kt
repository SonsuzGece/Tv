package com.kizmark.tvlauncher

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR = Locale("tr", "TR")
private val INK = Color(0xFF10141F)

/** Saat rakamlarının taban çizgisinin ekran yüksekliğine oranı (kayaların arkasına girdiği yer). */
private const val CLOCK_BASELINE = 0.52f

// ---------------------------------------------------------------------------
// Tema: vurgu rengi duvar kağıdından gelir
// ---------------------------------------------------------------------------
val LocalAccent = staticCompositionLocalOf { Color(0xFF34D399) }

// ---------------------------------------------------------------------------
// Buzlu cam
// ---------------------------------------------------------------------------
class Backdrop(val image: ImageBitmap, val rootW: Float, val rootH: Float)

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Arkasındaki duvar kağıdının bulanık halini çizen cam yüzey. */
@Composable
fun glassModifier(shape: Shape, tint: Color = Color(0x26FFFFFF)): Modifier {
    val bd = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    return Modifier
        .onGloballyPositioned { pos = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            if (bd != null && bd.rootW > 0f && bd.rootH > 0f) {
                val iw = bd.image.width
                val ih = bd.image.height
                val sx = iw / bd.rootW
                val sy = ih / bd.rootH
                val ox = (pos.x * sx).toInt().coerceIn(0, iw - 1)
                val oy = (pos.y * sy).toInt().coerceIn(0, ih - 1)
                val sw = (size.width * sx).toInt().coerceIn(1, iw - ox)
                val sh = (size.height * sy).toInt().coerceIn(1, ih - oy)
                drawImage(
                    image = bd.image,
                    srcOffset = IntOffset(ox, oy),
                    srcSize = IntSize(sw, sh),
                    dstSize = IntSize(
                        size.width.toInt().coerceAtLeast(1),
                        size.height.toInt().coerceAtLeast(1)
                    ),
                    filterQuality = FilterQuality.Low
                )
            } else {
                drawRect(Color(0x80101828))
            }
            drawRect(tint)
            drawRect(Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color.Transparent)))
        }
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color(0x8CFFFFFF), Color(0x1FFFFFFF))),
            shape
        )
}

// ---------------------------------------------------------------------------
// Kumanda OK tuşu: kısa basış = tıkla, basılı tut = uzun basış
// ---------------------------------------------------------------------------
class KeyTrack {
    var down = false
    var long = false
}

@Composable
fun tvKeys(onClick: () -> Unit, onLong: (() -> Unit)? = null): Modifier {
    val track = remember { KeyTrack() }
    val click = rememberUpdatedState(onClick)
    val longClick = rememberUpdatedState(onLong)
    return Modifier.onPreviewKeyEvent { e ->
        val k = e.key
        if (k != Key.DirectionCenter && k != Key.Enter && k != Key.NumPadEnter) {
            false
        } else {
            if (e.type == KeyEventType.KeyDown) {
                if (e.nativeKeyEvent.repeatCount == 0) {
                    track.down = true
                    track.long = false
                } else if (track.down && !track.long && longClick.value != null) {
                    track.long = true
                    longClick.value?.invoke()
                }
            } else if (e.type == KeyEventType.KeyUp) {
                val fire = track.down && !track.long
                track.down = false
                track.long = false
                if (fire) click.value.invoke()
            }
            true
        }
    }
}

/** Bir kutucuğun kök koordinatındaki yeri (piksel). Açılış animasyonu ve odak ışığı bunu kullanır. */
class TileBounds {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
}

// ---------------------------------------------------------------------------
// Geçişler
// ---------------------------------------------------------------------------

/**
 * Bir ekran katmanı. Görünür olunca yaylanarak gelir, gizlenince söner ve sonra kaldırılır.
 * İçerik, 0..1 arası ilerleme değerini alır ve kendi hareketini ona göre çizer.
 * keep = true: gizliyken de bellekte kalır (uygulamadan dönünce odak aynı kutucukta olsun diye).
 */
@Composable
fun Layer(
    visible: Boolean,
    keep: Boolean = false,
    content: @Composable BoxScope.(State<Float>) -> Unit
) {
    var present by remember { mutableStateOf(visible) }
    val progress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) {
            spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(220)
        },
        label = "layer"
    )
    LaunchedEffect(visible, keep) {
        if (visible || keep) {
            present = true
        } else {
            delay(240)
            present = false
        }
    }
    if (present || visible || keep) {
        Box(Modifier.fillMaxSize()) { content(this, progress) }
    }
}

/** Duvar kağıdı, saat ve ön plan. Uygulama açılırken yakınlaşır, geri dönünce yerine oturur. */
@Composable
fun SceneStage(st: LauncherState, content: @Composable () -> Unit) {
    val target = if (st.launching) 1.10f else if (st.overlay == Overlay.APPS) 1.04f else 1f
    val zoom by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow),
        label = "scene"
    )
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }) { content() }
}

// ---------------------------------------------------------------------------
// Kök: katmanlar alttan üste
//   1) duvar kağıdı  2) saat  3) ön plan (kayalar / dağlar)  4) arayüz
// ---------------------------------------------------------------------------
@Composable
fun LauncherApp(st: LauncherState) {
    val ctx = LocalContext.current
    val screenDensity = LocalDensity.current.density

    val apps by produceState<List<AppItem>>(emptyList(), st.resumeTick, screenDensity) {
        value = withContext(Dispatchers.Default) { AppRepo.load(ctx.applicationContext) }
    }
    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) st.ensureFavorites(apps.map { it.pkg })
    }

    val custom = remember { loadDrawableBitmap(ctx, "wallpaper") }
    val customFg = remember { if (custom != null) loadDrawableBitmap(ctx, "wallpaper_fg") else null }
    val names = remember(custom) {
        if (custom != null) listOf("Fotoğraf") + WALLPAPER_NAMES else WALLPAPER_NAMES
    }
    SideEffect { st.wallCount = names.size }
    val wall = st.wallIndex % names.size
    val procStyle = if (custom != null) wall - 1 else wall
    val accent = remember(procStyle, custom) {
        if (procStyle < 0 && custom != null) accentFromBitmap(custom)
        else WALLPAPER_ACCENTS.getOrElse(procStyle) { Color(0xFF34D399) }
    }
    val depthAvailable = if (procStyle < 0) customFg != null else hasProceduralDepth(procStyle)

    val media by produceState<MediaInfo?>(null, st.resumeTick) {
        while (true) {
            value = readMedia(ctx, value)
            delay(1500)
        }
    }

    val favorites = st.favorites
    val favApps = remember(apps, favorites) {
        val byPkg = apps.associateBy { it.pkg }
        favorites.mapNotNull { byPkg[it] }
    }

    // Launcher'da geri tuşu uygulamayı kapatmamalı
    BackHandler(enabled = true) { st.overlay = Overlay.NONE }

    // Bir süre tuşa basılmazsa saat moduna geç
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000)
            val limit = Config.AMBIENT_AFTER_MIN * 60000L
            if (limit > 0 && st.resumed && !st.ambient &&
                System.currentTimeMillis() - st.lastInput > limit
            ) {
                st.overlay = Overlay.NONE
                st.ambient = true
            }
        }
    }

    // Uygulama açılamadıysa arayüzü geri getir
    val launching = st.launching
    LaunchedEffect(launching) {
        if (launching) {
            delay(3000)
            if (st.resumed) st.launching = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()
        val backdrop = remember(procStyle, custom, rootW, rootH) {
            Backdrop(renderBackdrop(procStyle, custom), rootW, rootH)
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop, LocalAccent provides accent) {
            // Saat hem ana ekranda hem kontrol merkezinde aynı saattir;
            // kontrol merkezi açılınca sola kayıp biraz küçülür.
            SceneStage(st) {
                Wallpaper(procStyle, custom)
                val depthOn = st.depth && depthAvailable
                if (depthOn) HorizonGlow()
                DepthClock(st.boldClock, compact = !st.ambient && st.overlay == Overlay.CONTROL)
                if (depthOn) WallpaperForeground(procStyle, customFg)
            }

            BlurLayer(st.overlay == Overlay.APPS && !st.ambient, backdrop.image)
            EdgeAura(st)
            if (!st.ambient) Scrims()

            val live = !st.ambient
            Layer(
                visible = live && st.overlay == Overlay.NONE && !launching,
                keep = launching
            ) { p ->
                HomeScreen(st, favApps, media, p)
            }
            Layer(visible = live && st.overlay == Overlay.APPS && !launching) { p ->
                AllApps(st, apps, p)
            }
            Layer(visible = live && st.overlay == Overlay.CONTROL) { p ->
                ControlCenter(st, media, names[wall], depthAvailable, p)
            }

            if (st.ambient) AmbientScreen(media)
        }
    }
}

@Composable
fun BlurLayer(visible: Boolean, image: ImageBitmap) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(260), label = "blur")
    if (a > 0.01f) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Low,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = a }
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = a }.background(Color(0x73000000)))
    }
}

@Composable
fun Scrims() {
    Box(
        Modifier.fillMaxSize().drawBehind {
            val topH = size.height * 0.24f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0x59000000), Color.Transparent),
                    startY = 0f, endY = topH
                ),
                size = Size(size.width, topH)
            )
            val y = size.height * 0.60f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x8C000000)),
                    startY = y, endY = size.height
                ),
                topLeft = Offset(0f, y),
                size = Size(size.width, size.height - y)
            )
        }
    )
}

/** Ekran kenarlarında vurgu renginde ışıma; odak değişince kısa bir nabız atar. */
@Composable
fun EdgeAura(st: LauncherState) {
    val accent = LocalAccent.current
    val pulse = st.pulse
    val strong = st.overlay != Overlay.NONE || st.launching
    val beat = remember { Animatable(0f) }
    val base by animateFloatAsState(if (strong) 1f else 0f, tween(500), label = "aura")
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            beat.animateTo(1f, tween(140))
            beat.animateTo(0f, tween(480))
        }
    }
    Box(
        Modifier.fillMaxSize().drawBehind {
            val k = (0.08f + 0.14f * beat.value + 0.18f * base).coerceIn(0f, 1f)
            val w = size.width
            val h = size.height
            val d = h * 0.13f
            val edge = accent.copy(alpha = k)
            val low = accent.copy(alpha = (k * 1.5f).coerceIn(0f, 1f))
            drawRect(
                Brush.verticalGradient(listOf(edge, Color.Transparent), startY = 0f, endY = d),
                size = Size(w, d)
            )
            val bd = d * 1.5f
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, low), startY = h - bd, endY = h),
                topLeft = Offset(0f, h - bd),
                size = Size(w, bd)
            )
            drawRect(
                Brush.horizontalGradient(listOf(edge, Color.Transparent), startX = 0f, endX = d),
                size = Size(d, h)
            )
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, edge), startX = w - d, endX = w),
                topLeft = Offset(w - d, 0f),
                size = Size(d, h)
            )
        }
    )
}

/** Saatin arkasında, ufuk çizgisinde hafif bir ışık; kayaların kenarını belirginleştirir. */
@Composable
fun HorizonGlow() {
    val accent = LocalAccent.current
    Box(
        Modifier.fillMaxSize().drawBehind {
            val c = Offset(size.width * 0.5f, size.height * 0.56f)
            val r = size.width * 0.34f
            scale(1f, 0.38f, c) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.06f), Color.Transparent),
                        center = c, radius = r
                    ),
                    radius = r, center = c
                )
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Saat
// ---------------------------------------------------------------------------
@Composable
fun rememberMinute(): Long {
    val minute by produceState(System.currentTimeMillis() / 60000L) {
        while (true) {
            val m = System.currentTimeMillis() / 60000L
            if (m != value) value = m
            delay(1000)
        }
    }
    return minute
}

/** İçeriği yatayda ortalar ve son yazı satırının taban çizgisini ekranın verilen oranına oturtur. */
@Composable
fun BaselineAnchor(fraction: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize().then(modifier)) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val line = placeable[LastBaseline]
        val base = if (line == AlignmentLine.Unspecified) placeable.height else line
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(
                (constraints.maxWidth - placeable.width) / 2,
                (constraints.maxHeight * fraction).toInt() - base
            )
        }
    }
}

/**
 * Büyük saat. Ön plan katmanının ALTINA çizilir, böylece rakamların altı kayaların arkasında kalır.
 * compact = true (kontrol merkezi açık): aynı saat sola kayar ve küçülür, panelin yanında durur.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun DepthClock(bold: Boolean, compact: Boolean = false) {
    val shift by animateFloatAsState(
        targetValue = if (compact) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "clockShift"
    )
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val accent = LocalAccent.current
    val minute = rememberMinute()
    val time = remember(minute) { SimpleDateFormat("HH:mm", TR).format(Date()) }
    val date = remember(minute) { SimpleDateFormat("EEEE, d MMMM", TR).format(Date()) }

    val sizeDp = cfg.screenWidthDp * (if (bold) 0.20f else 0.17f)
    val clockSize = with(density) { sizeDp.dp.toSp() }
    val dateSize = with(density) { (cfg.screenWidthDp * 0.0195f).dp.toSp() }

    // Buz beyazından duvar kağıdının tonuna inen dolgu
    val fill = remember(accent) {
        Brush.verticalGradient(
            0.20f to Color.White,
            0.60f to lerp(Color(0xFFE6EEF0), accent, 0.10f),
            0.84f to lerp(Color(0xFFA4B2B6), accent, 0.26f)
        )
    }
    val deep = remember(density) {
        with(density) { Shadow(Color(0xD9000000), Offset(0f, 7.dp.toPx()), 17.dp.toPx()) }
    }
    val soft = remember(density) {
        with(density) { Shadow(Color(0xE6000000), Offset(0f, 1.dp.toPx()), 7.dp.toPx()) }
    }

    BaselineAnchor(
        CLOCK_BASELINE,
        Modifier.graphicsLayer {
            // Taban çizgisinin ortası sabit nokta: oradan küçült, sonra sola ve biraz aşağı taşı
            val z = 1f - 0.20f * shift
            scaleX = z
            scaleY = z
            transformOrigin = TransformOrigin(0.5f, CLOCK_BASELINE)
            translationX = -0.23f * size.width * shift
            translationY = 0.04f * size.height * shift
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                date,
                modifier = Modifier.offset(y = (sizeDp * 0.13f).dp),
                style = TextStyle(
                    color = Color(0xF2EEF8F5),
                    fontSize = dateSize,
                    fontWeight = FontWeight.SemiBold,
                    shadow = soft
                ),
                maxLines = 1
            )
            val clockStyle = if (bold) {
                TextStyle(
                    brush = fill,
                    fontSize = clockSize,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.035f).em,
                    fontFeatureSettings = "tnum",
                    shadow = deep
                )
            } else {
                TextStyle(
                    color = Color(0xF2FFFFFF),
                    fontSize = clockSize,
                    fontWeight = FontWeight.Thin,
                    letterSpacing = (-0.02f).em,
                    fontFeatureSettings = "tnum",
                    shadow = deep
                )
            }
            BasicText(time, style = clockStyle, maxLines = 1, softWrap = false)
        }
    }
}

// ---------------------------------------------------------------------------
// Ana ekran
// ---------------------------------------------------------------------------
@Composable
fun BoxScope.HomeScreen(
    st: LauncherState,
    favApps: List<AppItem>,
    media: MediaInfo?,
    p: State<Float>
) {
    val ctx = LocalContext.current
    val net by produceState("", st.resumeTick) {
        while (true) {
            value = networkLabel(ctx)
            delay(10000)
        }
    }

    // Üst satır yukarıdan iner
    val topMotion = Modifier.graphicsLayer {
        val e = p.value
        alpha = e.coerceIn(0f, 1f)
        translationY = -(1f - e) * 28.dp.toPx()
    }

    if (media != null) {
        NowPlayingPill(
            media,
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 40.dp, top = 26.dp)
                .then(topMotion)
        )
    }

    Row(
        Modifier
            .align(Alignment.TopEnd)
            .padding(end = 40.dp, top = 26.dp)
            .then(topMotion),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (net.isNotEmpty()) StatusPill(net)
        RoundButton(Glyph.SLIDERS, 44.dp, onClick = { st.overlay = Overlay.CONTROL })
    }

    // Dock alttan yaylanarak çıkar
    Dock(
        st, favApps,
        Modifier
            .align(Alignment.BottomCenter)
            .graphicsLayer {
                val e = p.value
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * 72.dp.toPx()
                val z = 0.94f + 0.06f * e
                scaleX = z
                scaleY = z
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
    )
}

@Composable
fun StatusPill(text: String) {
    Row(
        Modifier
            .height(44.dp)
            .then(glassModifier(RoundedCornerShape(22.dp)))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(Glyph.WIFI, 18.dp, Color.White)
        Spacer(Modifier.width(8.dp))
        BasicText(
            text,
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
fun RoundButton(
    glyph: Glyph,
    size: Dp,
    requester: FocusRequester? = null,
    idleBg: Color = Color(0x33FFFFFF),
    idleFg: Color = Color.White,
    focusBg: Color = Color.White,
    focusFg: Color = INK,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(
        targetValue = if (focused) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "round"
    )
    val keys = tvKeys(onClick)
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = zoom; scaleY = zoom }
            .clip(CircleShape)
            .background(if (focused) focusBg else idleBg)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(keys)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, size * 0.42f, if (focused) focusFg else idleFg)
    }
}

@Composable
fun AlbumArt(media: MediaInfo, size: Dp, corner: Dp) {
    val shape = RoundedCornerShape(corner)
    val art = media.art
    if (art != null) {
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape)
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(Color(0x40808A9E)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.MUSIC, size * 0.5f, Color.White)
        }
    }
}

@Composable
fun MediaButtons(
    media: MediaInfo,
    size: Dp,
    idleBg: Color,
    idleFg: Color,
    focusBg: Color,
    focusFg: Color
) {
    val tc = media.controller.transportControls
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton(Glyph.PREV, size, null, idleBg, idleFg, focusBg, focusFg) {
            runCatching { tc.skipToPrevious() }
        }
        RoundButton(
            if (media.playing) Glyph.PAUSE else Glyph.PLAY, size, null, idleBg, idleFg, focusBg, focusFg
        ) {
            runCatching { if (media.playing) tc.pause() else tc.play() }
        }
        RoundButton(Glyph.NEXT, size, null, idleBg, idleFg, focusBg, focusFg) {
            runCatching { tc.skipToNext() }
        }
    }
}

@Composable
fun NowPlayingPill(media: MediaInfo, modifier: Modifier = Modifier) {
    Row(
        modifier
            .then(glassModifier(RoundedCornerShape(30.dp)))
            .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(media, 44.dp, 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 120.dp)) {
            BasicText(
                media.title,
                style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (media.artist.isNotEmpty()) {
                BasicText(
                    media.artist,
                    style = TextStyle(color = Color(0xB3FFFFFF), fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        MediaButtons(media, 32.dp, Color(0x33FFFFFF), Color.White, Color.White, INK)
    }
}

// ---------------------------------------------------------------------------
// Alt çubuk (dock): deniz camı görünümü, odağı izleyen ışık ve su yansıması
// ---------------------------------------------------------------------------
@Composable
fun Dock(st: LauncherState, favApps: List<AppItem>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val cfg = LocalConfiguration.current
    val accent = LocalAccent.current
    val accentLight = remember(accent) { lerp(accent, Color.White, 0.55f) }
    val accentDark = remember(accent) { lerp(accent, Color.Black, 0.78f) }

    val tileH = (cfg.screenHeightDp * 0.15f).dp
    val tileW = tileH * 16 / 9
    val corner = 32.dp
    val first = remember { FocusRequester() }
    var label by remember { mutableStateOf("") }
    var hasFocus by remember { mutableStateOf(false) }

    // Odaktaki kutucuğun yeri (kök koordinatında piksel)
    var pillX by remember { mutableStateOf(0f) }
    var targetX by remember { mutableStateOf(-1f) }
    var targetW by remember { mutableStateOf(0f) }
    var spotReady by remember { mutableStateOf(false) }
    val spot = remember { Animatable(0f) }
    val spotW = remember { Animatable(0f) }
    val glow by animateFloatAsState(if (hasFocus) 1f else 0f, tween(300), label = "dockGlow")
    val labelAnim = remember { Animatable(1f) }

    LaunchedEffect(targetX, targetW) {
        if (targetX >= 0f) {
            if (!spotReady) {
                spot.snapTo(targetX)
                spotW.snapTo(targetW)
                spotReady = true
            } else {
                launch { spotW.animateTo(targetW, tween(300)) }
                spot.animateTo(
                    targetX,
                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    }
    LaunchedEffect(label) {
        labelAnim.snapTo(0f)
        labelAnim.animateTo(1f, tween(240))
    }
    LaunchedEffect(favApps.isNotEmpty(), st.homeTick) {
        delay(120)
        runCatching { first.requestFocus() }
    }

    val onBounds: (Float, Float) -> Unit = { cx, w ->
        targetX = cx
        targetW = w
    }

    Column(
        modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dış katman: çift kenar ışığı ve dock'un altına düşen yansıma (kırpılmaz)
        Box(
            Modifier
                .padding(horizontal = 40.dp)
                .onGloballyPositioned { pillX = it.positionInRoot().x }
                .drawBehind {
                    if (spotReady && size.width > 0f) {
                        val sx = spot.value - pillX
                        val cy = size.height + 16.dp.toPx()
                        val rr = size.width * 0.20f
                        val pivot = Offset(sx, cy)
                        scale(1f, 0.14f, pivot) {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(
                                        accent.copy(alpha = 0.55f * glow),
                                        accent.copy(alpha = 0.16f * glow),
                                        Color.Transparent
                                    ),
                                    center = pivot, radius = rr
                                ),
                                radius = rr, center = pivot
                            )
                        }
                        val g = 4.dp.toPx()
                        val cr = corner.toPx() + g
                        drawRoundRect(
                            Brush.radialGradient(
                                listOf(
                                    accentLight.copy(alpha = 0.35f + 0.45f * glow),
                                    accent.copy(alpha = 0.22f),
                                    Color(0x14FFFFFF)
                                ),
                                center = Offset(sx, size.height / 2f),
                                radius = size.width * 0.6f
                            ),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + 2 * g, size.height + 2 * g),
                            cornerRadius = CornerRadius(cr, cr),
                            style = Stroke(1.5.dp.toPx())
                        )
                    }
                }
        ) {
            Box(
                Modifier
                    .then(glassModifier(RoundedCornerShape(corner), Color(0x14FFFFFF)))
                    .drawBehind {
                        // Deniz camı tonu
                        drawRect(
                            Brush.verticalGradient(
                                listOf(accentLight.copy(alpha = 0.16f), accentDark.copy(alpha = 0.50f))
                            )
                        )
                        if (spotReady && size.width > 0f) {
                            val c = Offset(spot.value - pillX, size.height / 2f)
                            // Odağı izleyen geniş ışık
                            val r1 = size.width * 0.38f
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(
                                        accent.copy(alpha = 0.28f * glow),
                                        accent.copy(alpha = 0.08f * glow),
                                        Color.Transparent
                                    ),
                                    center = c, radius = r1
                                ),
                                radius = r1, center = c
                            )
                            // Odaktaki kutucuğun arkasındaki parlak hale
                            val r2 = (spotW.value * 0.78f).coerceAtLeast(1f)
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(
                                        accentLight.copy(alpha = 0.62f * glow),
                                        accent.copy(alpha = 0.40f * glow),
                                        Color.Transparent
                                    ),
                                    center = c, radius = r2
                                ),
                                radius = r2, center = c
                            )
                        }
                    }
                    .onFocusChanged { hasFocus = it.hasFocus }
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(favApps, key = { _, a -> a.pkg }) { i, app ->
                        DockTile(
                            width = tileW,
                            height = tileH,
                            requester = if (i == 0) first else null,
                            onFocus = {
                                label = app.label
                                st.pulse += 1
                            },
                            onBounds = onBounds,
                            onClick = { b ->
                                // Uygulama, basılan kutucuğun yerinden büyüyerek açılır
                                st.launching = true
                                val ok = launchApp(ctx, app, launchOptions(view, b.x, b.y, b.w, b.h))
                                if (!ok) st.launching = false
                            },
                            onLong = { st.moveFavoriteLeft(app.pkg) }
                        ) {
                            AppTileContent(app, tileH)
                        }
                    }
                    item(key = "__all_apps__") {
                        DockTile(
                            width = tileH,
                            height = tileH,
                            requester = if (favApps.isEmpty()) first else null,
                            onFocus = {
                                label = "Tüm uygulamalar"
                                st.pulse += 1
                            },
                            onBounds = onBounds,
                            onClick = { st.overlay = Overlay.APPS },
                            onLong = null
                        ) {
                            UtilityFace(Glyph.GRID, tileH)
                        }
                    }
                    item(key = "__control__") {
                        DockTile(
                            width = tileH,
                            height = tileH,
                            requester = null,
                            onFocus = {
                                label = "Kontrol merkezi"
                                st.pulse += 1
                            },
                            onBounds = onBounds,
                            onClick = { st.overlay = Overlay.CONTROL },
                            onLong = null
                        ) {
                            UtilityFace(Glyph.SLIDERS, tileH)
                        }
                    }
                }
            }
        }
        BasicText(
            if (hasFocus) label else " ",
            modifier = Modifier
                .padding(top = 12.dp)
                .graphicsLayer {
                    alpha = labelAnim.value
                    translationY = (1f - labelAnim.value) * 6.dp.toPx()
                },
            style = TextStyle(
                color = Color(0xF5ECFDF5),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(Color(0xF2000000), Offset(0f, 2f), 12f)
            ),
            maxLines = 1
        )
    }
}

/** "Tüm uygulamalar" ve "Kontrol merkezi" kutucuklarının buzlu, vurgu tonlu yüzü. */
@Composable
fun UtilityFace(glyph: Glyph, tileH: Dp) {
    val accent = LocalAccent.current
    val light = remember(accent) { lerp(accent, Color.White, 0.55f) }
    val dark = remember(accent) { lerp(accent, Color.Black, 0.70f) }
    val faceShape = RoundedCornerShape(tileH * 0.17f)
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(light.copy(alpha = 0.26f), dark.copy(alpha = 0.55f)))
            )
            .border(1.5.dp, light.copy(alpha = 0.45f), faceShape),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, tileH * 0.4f, Color.White)
    }
}

@Composable
fun DockTile(
    width: Dp,
    height: Dp,
    requester: FocusRequester?,
    onFocus: () -> Unit,
    onBounds: (Float, Float) -> Unit,
    onClick: (TileBounds) -> Unit,
    onLong: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit
) {
    val accent = LocalAccent.current
    val ring = remember(accent) { lerp(accent, Color.White, 0.80f) }
    val shine = remember(accent) { lerp(accent, Color.White, 0.55f) }
    var focused by remember { mutableStateOf(false) }
    val bounds = remember { TileBounds() }
    val lift by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "tileLift"
    )
    // Odaklanınca kartın üzerinden geçen ışık süpürmesi
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(focused) {
        if (focused) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(720))
        }
    }
    val tileShape = RoundedCornerShape(height * 0.17f)
    val keys = tvKeys({ onClick(bounds) }, onLong)
    Box(
        Modifier
            .size(width, height)
            .zIndex(if (focused) 1f else 0f)
            .onGloballyPositioned {
                val pos = it.positionInRoot()
                bounds.x = pos.x
                bounds.y = pos.y
                bounds.w = it.size.width.toFloat()
                bounds.h = it.size.height.toFloat()
                if (focused) onBounds(bounds.x + bounds.w / 2f, bounds.w)
            }
            .graphicsLayer {
                val z = 1f + 0.09f * lift
                scaleX = z
                scaleY = z
                translationY = -8.dp.toPx() * lift
                shadowElevation = 17.dp.toPx() * lift.coerceIn(0f, 1f)
                ambientShadowColor = accent
                spotShadowColor = accent
                this.shape = tileShape
                clip = true
            }
            .drawWithContent {
                drawContent()
                if (!focused) drawRect(Color(0x26000000))
                val p = sweep.value
                if (p < 1f) {
                    val x0 = (-1.2f + 2.4f * p) * size.width
                    drawRect(
                        Brush.linearGradient(
                            listOf(
                                Color.Transparent,
                                shine.copy(alpha = 0.42f),
                                Color.White.copy(alpha = 0.50f),
                                Color.Transparent
                            ),
                            start = Offset(x0, 0f),
                            end = Offset(x0 + size.width * 0.9f, size.height * 0.5f)
                        )
                    )
                }
            }
            .then(if (focused) Modifier.border(2.5.dp, ring, tileShape) else Modifier)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocus()
                    if (bounds.w > 0f) onBounds(bounds.x + bounds.w / 2f, bounds.w)
                }
            }
            .then(keys)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick(bounds) },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun AppTileContent(app: AppItem, tileH: Dp) {
    if (app.isBanner) {
        Image(
            bitmap = app.image,
            contentDescription = app.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF2C3654), Color(0xFF151B2E)))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                bitmap = app.image,
                contentDescription = app.label,
                modifier = Modifier.size(tileH * 0.5f)
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                app.label,
                modifier = Modifier.padding(horizontal = 6.dp),
                style = TextStyle(color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Kontrol merkezi: duvar kağıdının renginde yarı saydam cam butonlar
// ---------------------------------------------------------------------------
@Composable
fun BoxScope.ControlCenter(
    st: LauncherState,
    media: MediaInfo?,
    wallName: String,
    depthAvailable: Boolean,
    p: State<Float>
) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val accent = LocalAccent.current
    val accentLight = remember(accent) { lerp(accent, Color.White, 0.55f) }
    val accentDark = remember(accent) { lerp(accent, Color.Black, 0.78f) }
    val first = remember { FocusRequester() }
    val net = remember { networkLabel(ctx) }
    val thumb = LocalBackdrop.current?.image

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }

    // Sağ tarafı karartan perde
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = p.value.coerceIn(0f, 1f) }
            .background(Brush.horizontalGradient(0.42f to Color.Transparent, 1f to Color(0xA6000000)))
    )

    val gap = Arrangement.spacedBy(12.dp)
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 36.dp)
            .width(410.dp)
            .heightIn(max = (cfg.screenHeightDp - 36).dp)
            .graphicsLayer {
                // Sağdan kayarak ve hafif büyüyerek gelir
                val e = p.value
                alpha = e.coerceIn(0f, 1f)
                translationX = (1f - e) * 64.dp.toPx()
                val z = 0.95f + 0.05f * e
                scaleX = z
                scaleY = z
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
            .then(glassModifier(RoundedCornerShape(38.dp), Color(0x14FFFFFF)))
            .drawBehind {
                // Panelin kendisi de duvar kağıdının tonunda
                drawRect(
                    Brush.verticalGradient(
                        listOf(accentLight.copy(alpha = 0.10f), accentDark.copy(alpha = 0.42f))
                    )
                )
                val r = size.width * 0.8f
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width, 0f), radius = r
                    ),
                    radius = r, center = Offset(size.width, 0f)
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(0f, size.height), radius = r
                    ),
                    radius = r, center = Offset(0f, size.height)
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = gap
    ) {
        if (media != null) MediaCard(media)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            CcToggle(
                glyph = Glyph.WIFI,
                title = "Ağ",
                value = net,
                active = net != "Bağlı değil",
                modifier = Modifier.weight(1f),
                requester = first
            ) {
                openSettings(ctx, Settings.ACTION_WIFI_SETTINGS)
            }
            CcToggle(
                glyph = Glyph.LAYERS,
                title = "Derinlik",
                value = if (!depthAvailable) "Yok" else if (st.depth) "Açık" else "Kapalı",
                active = depthAvailable && st.depth,
                modifier = Modifier.weight(1f)
            ) {
                st.toggleDepth()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            CcToggle(
                glyph = Glyph.IMAGE,
                title = "Duvar kağıdı",
                value = wallName,
                active = false,
                modifier = Modifier.weight(1f),
                thumb = thumb
            ) {
                st.nextWallpaper()
            }
            CcToggle(
                glyph = Glyph.TYPE,
                title = "Saat stili",
                value = if (st.boldClock) "Kalın" else "İnce",
                active = false,
                modifier = Modifier.weight(1f)
            ) {
                st.toggleBoldClock()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            CcAction(Glyph.SPEAKER, "Ses", Modifier.weight(1f)) {
                openSettings(ctx, Settings.ACTION_SOUND_SETTINGS)
            }
            CcAction(Glyph.CLOCK, "Saat modu", Modifier.weight(1f)) {
                st.overlay = Overlay.NONE
                st.ambient = true
            }
            CcAction(Glyph.DISPLAY, "Ekran", Modifier.weight(1f)) {
                openSettings(ctx, Settings.ACTION_DISPLAY_SETTINGS)
            }
            CcAction(Glyph.GEAR, "Ayarlar", Modifier.weight(1f)) {
                openSettings(ctx, Settings.ACTION_SETTINGS)
            }
        }
    }
}

/**
 * Kontrol merkezi butonlarının ortak yüzeyi: vurgu renginde yarı saydam cam.
 *  - active: açık durumdaki anahtar daha dolgun görünür
 *  - odak: yüzey aydınlanır, büyür, parlak çerçeve alır ve üzerinden ışık geçer
 */
@Composable
fun CcSurface(
    modifier: Modifier,
    corner: Dp,
    active: Boolean,
    requester: FocusRequester?,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val accent = LocalAccent.current
    val light = remember(accent) { lerp(accent, Color.White, 0.55f) }
    val dark = remember(accent) { lerp(accent, Color.Black, 0.72f) }
    val ring = remember(accent) { lerp(accent, Color.White, 0.80f) }
    var focused by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "ccGlow"
    )
    val on by animateFloatAsState(if (active) 1f else 0f, tween(260), label = "ccOn")
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(focused) {
        if (focused) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(650))
        }
    }
    val surfaceShape = RoundedCornerShape(corner)
    val idleRim = remember(accent) {
        Brush.verticalGradient(listOf(light.copy(alpha = 0.60f), accent.copy(alpha = 0.14f)))
    }
    val keys = tvKeys(onClick)
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                val z = 1f + 0.05f * glow
                scaleX = z
                scaleY = z
            }
            .clip(surfaceShape)
            .drawWithContent {
                val g = glow.coerceIn(0f, 1f)
                // Yarı saydam, duvar kağıdının renginde cam
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            light.copy(alpha = 0.20f + 0.16f * on + 0.22f * g),
                            lerp(dark, accent, 0.30f * on + 0.45f * g).copy(alpha = 0.50f + 0.12f * g)
                        )
                    )
                )
                // Üst yarıda ince ışık
                val sheenH = size.height * 0.5f
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.16f + 0.10f * g), Color.Transparent),
                        startY = 0f, endY = sheenH
                    ),
                    size = Size(size.width, sheenH)
                )
                drawContent()
                val s = sweep.value
                if (s < 1f) {
                    val x0 = (-1.2f + 2.4f * s) * size.width
                    drawRect(
                        Brush.linearGradient(
                            listOf(
                                Color.Transparent,
                                light.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.34f),
                                Color.Transparent
                            ),
                            start = Offset(x0, 0f),
                            end = Offset(x0 + size.width * 0.8f, size.height * 0.6f)
                        )
                    )
                }
            }
            .border(
                if (focused) 2.5.dp else 1.dp,
                if (focused) SolidColor(ring) else idleRim,
                surfaceShape
            )
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(keys)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        content = content
    )
}

/** Büyük buton: solda simge (veya duvar kağıdı önizlemesi), yanında ad ve o anki değer. */
@Composable
fun CcToggle(
    glyph: Glyph,
    title: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    thumb: ImageBitmap? = null,
    onClick: () -> Unit
) {
    val accent = LocalAccent.current
    val chipInk = remember(accent) { lerp(accent, Color.Black, 0.55f) }
    val valueColor = remember(accent) { lerp(accent, Color.White, 0.72f) }
    CcSurface(modifier.height(84.dp), 26.dp, active, requester, onClick) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0x99FFFFFF), CircleShape)
                )
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (active) Color.White else Color(0x2EFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(glyph, 22.dp, if (active) chipInk else Color.White)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                BasicText(
                    title,
                    style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    value,
                    style = TextStyle(color = valueColor, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Küçük buton: üstte simge, altında ad. */
@Composable
fun CcAction(glyph: Glyph, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CcSurface(modifier.height(78.dp), 24.dp, false, null, onClick) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GlyphIcon(glyph, 24.dp, Color.White)
            Spacer(Modifier.height(7.dp))
            BasicText(
                title,
                style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MediaCard(media: MediaInfo) {
    val accent = LocalAccent.current
    val light = remember(accent) { lerp(accent, Color.White, 0.55f) }
    val dark = remember(accent) { lerp(accent, Color.Black, 0.72f) }
    val valueColor = remember(accent) { lerp(accent, Color.White, 0.72f) }
    val cardShape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(light.copy(alpha = 0.30f), lerp(dark, accent, 0.25f).copy(alpha = 0.55f))
                    )
                )
                val sheenH = size.height * 0.5f
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0x29FFFFFF), Color.Transparent),
                        startY = 0f, endY = sheenH
                    ),
                    size = Size(size.width, sheenH)
                )
            }
            .border(
                1.dp,
                Brush.verticalGradient(listOf(light.copy(alpha = 0.60f), accent.copy(alpha = 0.14f))),
                cardShape
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(media, 60.dp, 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                media.title,
                style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (media.artist.isNotEmpty()) {
                BasicText(
                    media.artist,
                    style = TextStyle(color = valueColor, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        MediaButtons(media, 38.dp, Color(0x29FFFFFF), Color.White, Color.White, INK)
    }
}

// ---------------------------------------------------------------------------
// Tüm uygulamalar: alttan yaylanarak açılır, kutucuklar sırayla belirir
// ---------------------------------------------------------------------------
@Composable
fun AllApps(st: LauncherState, apps: List<AppItem>, p: State<Float>) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val first = remember { FocusRequester() }
    val favorites = st.favorites
    var introDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1100)
        introDone = true
    }
    LaunchedEffect(apps.isNotEmpty()) {
        delay(120)
        runCatching { first.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val e = p.value
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * 48.dp.toPx()
                val z = 0.90f + 0.10f * e
                scaleX = z
                scaleY = z
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .padding(start = 56.dp, end = 56.dp, top = 34.dp)
    ) {
        BasicText(
            "Tüm uygulamalar",
            style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
        )
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                AppCell(
                    app = app,
                    pinned = app.pkg in favorites,
                    introDelay = if (!introDone && i < 18) 60L + i * 40L else -1L,
                    requester = if (i == 0) first else null,
                    onClick = { b ->
                        st.launching = true
                        val ok = launchApp(ctx, app, launchOptions(view, b.x, b.y, b.w, b.h))
                        if (!ok) st.launching = false
                        st.overlay = Overlay.NONE
                    },
                    onLong = { st.toggleFavorite(app.pkg) }
                )
            }
        }
    }
}

@Composable
fun AppCell(
    app: AppItem,
    pinned: Boolean,
    introDelay: Long,
    requester: FocusRequester?,
    onClick: (TileBounds) -> Unit,
    onLong: () -> Unit
) {
    val accent = LocalAccent.current
    val ring = remember(accent) { lerp(accent, Color.White, 0.80f) }
    var focused by remember { mutableStateOf(false) }
    val bounds = remember { TileBounds() }
    val zoom by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "cell"
    )
    val appear = remember { Animatable(if (introDelay >= 0L) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            delay(introDelay.coerceAtLeast(0L))
            appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val cellShape = RoundedCornerShape(14.dp)
    val keys = tvKeys({ onClick(bounds) }, onLong)
    Column(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                val a = appear.value
                alpha = a.coerceIn(0f, 1f)
                translationY = (1f - a) * 26.dp.toPx()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onGloballyPositioned {
                    val pos = it.positionInRoot()
                    bounds.x = pos.x
                    bounds.y = pos.y
                    bounds.w = it.size.width.toFloat()
                    bounds.h = it.size.height.toFloat()
                }
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    shadowElevation = if (focused) 15.dp.toPx() else 0f
                    ambientShadowColor = accent
                    spotShadowColor = accent
                    this.shape = cellShape
                    clip = true
                }
                .then(if (focused) Modifier.border(2.5.dp, ring, cellShape) else Modifier)
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .then(keys)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick(bounds) }
        ) {
            AppTileContent(app, 76.dp)
            if (pinned) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }
        BasicText(
            app.label,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            style = TextStyle(
                color = if (focused) Color.White else Color(0xB3FFFFFF),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Saat modu: yalnızca manzara ve kayaların arkasındaki saat kalır
// ---------------------------------------------------------------------------
@Composable
fun BoxScope.AmbientScreen(media: MediaInfo?) {
    Box(Modifier.fillMaxSize().background(Color(0x2E000000)))
    if (media != null) {
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(media, 40.dp, 10.dp)
            Spacer(Modifier.width(12.dp))
            BasicText(
                if (media.artist.isNotEmpty()) media.title + "  ·  " + media.artist else media.title,
                style = TextStyle(color = Color(0xE6FFFFFF), fontSize = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
