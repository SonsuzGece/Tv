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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR = Locale("tr", "TR")
private val ACCENT = Color(0xFF2F7BFF)
private val INK = Color(0xFF10141F)

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

// ---------------------------------------------------------------------------
// Kök
// ---------------------------------------------------------------------------
@Composable
fun LauncherApp(st: LauncherState) {
    val ctx = LocalContext.current

    val apps by produceState<List<AppItem>>(emptyList(), st.resumeTick) {
        value = withContext(Dispatchers.Default) { AppRepo.load(ctx.applicationContext) }
    }
    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) st.ensureFavorites(apps.map { it.pkg })
    }

    val custom = remember { loadCustomWallpaper(ctx) }
    val names = remember(custom) {
        if (custom != null) listOf("Fotoğraf") + WALLPAPER_NAMES else WALLPAPER_NAMES
    }
    SideEffect { st.wallCount = names.size }
    val wall = st.wallIndex % names.size
    val procStyle = if (custom != null) wall - 1 else wall

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

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()
        val backdrop = remember(procStyle, custom, rootW, rootH) {
            Backdrop(renderBackdrop(procStyle, custom), rootW, rootH)
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Wallpaper(procStyle, custom)
            BlurLayer(st.overlay == Overlay.APPS, backdrop.image)
            if (st.ambient) {
                AmbientScreen(st.boldClock, media)
            } else {
                Scrims()
                when (st.overlay) {
                    Overlay.NONE -> HomeScreen(st, favApps, media)
                    Overlay.APPS -> AllApps(st, apps)
                    Overlay.CONTROL -> ControlCenter(st, media, names[wall])
                }
            }
        }
    }
}

@Composable
fun BlurLayer(visible: Boolean, image: ImageBitmap) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "blur")
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
            val topH = size.height * 0.26f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0x66000000), Color.Transparent),
                    startY = 0f, endY = topH
                ),
                size = Size(size.width, topH)
            )
            val y = size.height * 0.55f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x99000000)),
                    startY = y, endY = size.height
                ),
                topLeft = Offset(0f, y),
                size = Size(size.width, size.height - y)
            )
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

@Composable
fun ClockBlock(bold: Boolean, modifier: Modifier = Modifier, factor: Float = 1f) {
    val cfg = LocalConfiguration.current
    val minute = rememberMinute()
    val time = remember(minute) { SimpleDateFormat("HH:mm", TR).format(Date()) }
    val date = remember(minute) { SimpleDateFormat("EEEE, d MMMM", TR).format(Date()) }
    val soft = Shadow(Color(0x80000000), Offset(0f, 3f), 18f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            date,
            style = TextStyle(
                color = Color(0xE6FFFFFF),
                fontSize = (22f * factor).sp,
                fontWeight = FontWeight.Medium,
                shadow = soft
            )
        )
        BasicText(
            time,
            style = TextStyle(
                color = Color(0xF2FFFFFF),
                fontSize = (cfg.screenWidthDp * 0.15f * factor).sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Thin,
                letterSpacing = (-2).sp,
                shadow = soft
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Ana ekran
// ---------------------------------------------------------------------------
@Composable
fun BoxScope.HomeScreen(st: LauncherState, favApps: List<AppItem>, media: MediaInfo?) {
    val ctx = LocalContext.current
    val net by produceState("", st.resumeTick) {
        while (true) {
            value = networkLabel(ctx)
            delay(10000)
        }
    }

    if (media != null) {
        NowPlayingPill(
            media,
            Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 26.dp)
        )
    }

    Row(
        Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (net.isNotEmpty()) StatusPill(net)
        RoundButton(Glyph.SLIDERS, 44.dp, onClick = { st.overlay = Overlay.CONTROL })
    }

    ClockBlock(st.boldClock, Modifier.align(Alignment.TopCenter).padding(top = 30.dp))

    Dock(st, favApps, Modifier.align(Alignment.BottomCenter))
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
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "round")
    val keys = tvKeys(onClick)
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
// Alt çubuk (dock)
// ---------------------------------------------------------------------------
@Composable
fun Dock(st: LauncherState, favApps: List<AppItem>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val tileH = (cfg.screenHeightDp * 0.155f).dp
    val tileW = tileH * 16 / 9
    val first = remember { FocusRequester() }
    var label by remember { mutableStateOf("") }
    var hasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(favApps.isNotEmpty(), st.homeTick) {
        delay(120)
        runCatching { first.requestFocus() }
    }

    Column(
        modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .padding(horizontal = 40.dp)
                .then(glassModifier(RoundedCornerShape(32.dp)))
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
                        onFocus = { label = app.label },
                        onClick = { launchApp(ctx, app) },
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
                        onFocus = { label = "Tüm uygulamalar" },
                        onClick = { st.overlay = Overlay.APPS },
                        onLong = null
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(Color(0x33FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.GRID, tileH * 0.4f, Color.White)
                        }
                    }
                }
                item(key = "__control__") {
                    DockTile(
                        width = tileH,
                        height = tileH,
                        requester = null,
                        onFocus = { label = "Kontrol merkezi" },
                        onClick = { st.overlay = Overlay.CONTROL },
                        onLong = null
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(Color(0x33FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.SLIDERS, tileH * 0.4f, Color.White)
                        }
                    }
                }
            }
        }
        BasicText(
            if (hasFocus) label else " ",
            modifier = Modifier.padding(top = 8.dp),
            style = TextStyle(
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 10f)
            ),
            maxLines = 1
        )
    }
}

@Composable
fun DockTile(
    width: Dp,
    height: Dp,
    requester: FocusRequester?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLong: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "tile"
    )
    val tileShape = RoundedCornerShape(height * 0.17f)
    val keys = tvKeys(onClick, onLong)
    Box(
        Modifier
            .size(width, height)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused) 36f else 0f
                this.shape = tileShape
                clip = true
            }
            .then(if (focused) Modifier.border(2.5.dp, Color.White, tileShape) else Modifier)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .then(keys)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
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
// Kontrol merkezi
// ---------------------------------------------------------------------------
@Composable
fun BoxScope.ControlCenter(st: LauncherState, media: MediaInfo?, wallName: String) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val first = remember { FocusRequester() }
    val info = remember { systemInfo(ctx) }
    val net = remember { networkLabel(ctx) }
    val access = remember { hasMediaAccess(ctx) }
    val minute = rememberMinute()
    val time = remember(minute) { SimpleDateFormat("HH:mm", TR).format(Date()) }
    val enter = remember { Animatable(0f) }

    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220)) }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xA6000000))))
    )

    ClockBlock(
        st.boldClock,
        Modifier.align(Alignment.CenterStart).padding(start = 80.dp),
        factor = 0.7f
    )

    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 36.dp)
            .width(410.dp)
            .heightIn(max = (cfg.screenHeightDp - 36).dp)
            .graphicsLayer {
                alpha = enter.value
                translationX = (1f - enter.value) * 60f
            }
            .then(glassModifier(RoundedCornerShape(34.dp), Color(0x30FFFFFF)))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Kontrol Merkezi",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            )
            BasicText(time, style = TextStyle(color = Color(0xCCFFFFFF), fontSize = 16.sp))
        }

        if (media != null) {
            MediaCard(media)
        } else if (!access) {
            CcPill(Glyph.MUSIC, "Müzik kartı", "İzin gerekli (README)", Modifier.fillMaxWidth()) {
                openSettings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
        }

        val gap = Arrangement.spacedBy(10.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            CcPill(Glyph.WIFI, "Ağ", net, Modifier.weight(1f), first) {
                openSettings(ctx, Settings.ACTION_WIFI_SETTINGS)
            }
            CcPill(Glyph.SPEAKER, "Ses", "Ses ayarları", Modifier.weight(1f)) {
                openSettings(ctx, Settings.ACTION_SOUND_SETTINGS)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            CcPill(Glyph.IMAGE, "Duvar kağıdı", wallName, Modifier.weight(1f)) {
                st.nextWallpaper()
            }
            CcPill(Glyph.TYPE, "Saat stili", if (st.boldClock) "Kalın" else "İnce", Modifier.weight(1f)) {
                st.toggleBoldClock()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            CcPill(Glyph.CLOCK, "Saat modu", "Başlat", Modifier.weight(1f)) {
                st.overlay = Overlay.NONE
                st.ambient = true
            }
            CcPill(Glyph.DISPLAY, "Ekran", "Görüntü ayarları", Modifier.weight(1f)) {
                openSettings(ctx, Settings.ACTION_DISPLAY_SETTINGS)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            CcPill(Glyph.BAG, "Mağaza", "Play Store", Modifier.weight(1f)) {
                launchPackage(ctx, "com.android.vending")
            }
            CcPill(Glyph.GEAR, "Ayarlar", "Tüm ayarlar", Modifier.weight(1f)) {
                openSettings(ctx, Settings.ACTION_SETTINGS)
            }
        }

        if (info.isNotEmpty()) {
            BasicText(
                info,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                style = TextStyle(color = Color(0xB3FFFFFF), fontSize = 12.sp, textAlign = TextAlign.Center)
            )
        }
    }
}

@Composable
fun MediaCard(media: MediaInfo) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xEBFFFFFF))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(media, 54.dp, 14.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                media.title,
                style = TextStyle(color = INK, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (media.artist.isNotEmpty()) {
                BasicText(
                    media.artist,
                    style = TextStyle(color = Color(0xFF5A6072), fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        MediaButtons(media, 36.dp, Color(0x1A000000), INK, ACCENT, Color.White)
    }
}

@Composable
fun CcPill(
    glyph: Glyph,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "pill")
    val keys = tvKeys(onClick)
    Row(
        modifier
            .height(56.dp)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(28.dp))
            .background(if (focused) Color(0xF2FFFFFF) else Color(0x29FFFFFF))
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(keys)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (focused) ACCENT else Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph, 18.dp, Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            BasicText(
                title,
                style = TextStyle(
                    color = if (focused) INK else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                sub,
                style = TextStyle(
                    color = if (focused) Color(0xFF5A6072) else Color(0xB3FFFFFF),
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Tüm uygulamalar
// ---------------------------------------------------------------------------
@Composable
fun AllApps(st: LauncherState, apps: List<AppItem>) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    val favorites = st.favorites
    LaunchedEffect(apps.isNotEmpty()) {
        delay(120)
        runCatching { first.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 34.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            BasicText(
                "Tüm uygulamalar",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            )
            BasicText(
                "OK tuşuna basılı tut: çubuğa sabitle / kaldır",
                style = TextStyle(color = Color(0xB3FFFFFF), fontSize = 13.sp)
            )
        }
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
                    requester = if (i == 0) first else null,
                    onClick = {
                        launchApp(ctx, app)
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
    requester: FocusRequester?,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cell")
    val cellShape = RoundedCornerShape(14.dp)
    val keys = tvKeys(onClick, onLong)
    Column(
        Modifier.zIndex(if (focused) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (focused) 30f else 0f
                    this.shape = cellShape
                    clip = true
                }
                .then(if (focused) Modifier.border(2.5.dp, Color.White, cellShape) else Modifier)
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .then(keys)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
        ) {
            AppTileContent(app, 76.dp)
            if (pinned) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(ACCENT)
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
// Saat modu
// ---------------------------------------------------------------------------
@Composable
fun BoxScope.AmbientScreen(bold: Boolean, media: MediaInfo?) {
    Box(Modifier.fillMaxSize().background(Color(0x59000000)))
    ClockBlock(bold, Modifier.align(Alignment.Center), factor = 1.2f)
    if (media != null) {
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
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
