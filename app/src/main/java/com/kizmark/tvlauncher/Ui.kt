package com.kizmark.tvlauncher

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val INK = Color(0xFF10141F)

// ---------------------------------------------------------------------------
// Tema ve cam
// ---------------------------------------------------------------------------
val LocalAccent = staticCompositionLocalOf { Color(0xFF34D399) }

class Glass(val image: ImageBitmap, val rootW: Float, val rootH: Float)

val LocalGlass = staticCompositionLocalOf<Glass?> { null }

class PosRef {
    var x = 0f
    var y = 0f
}

/**
 * Buzlu cam zemin: sahnenin küçük, bulanık kopyasından kendi bölgesini çizer.
 * origin verilirse konum ölçülmez (kayarak gelen paneller için).
 */
@Composable
fun glassBg(shape: Shape, tint: Color, origin: Offset? = null): Modifier {
    val g = LocalGlass.current
    val ref = remember { PosRef() }
    val measure = if (origin == null) {
        Modifier.onGloballyPositioned {
            val p = it.positionInRoot()
            ref.x = p.x
            ref.y = p.y
        }
    } else {
        Modifier
    }
    return measure
        .clip(shape)
        .drawBehind {
            if (g != null && g.rootW > 0f) {
                val iw = g.image.width
                val ih = g.image.height
                val sx = iw / g.rootW
                val sy = ih / g.rootH
                val px = origin?.x ?: ref.x
                val py = origin?.y ?: ref.y
                val ox = (px * sx).toInt().coerceIn(0, iw - 1)
                val oy = (py * sy).toInt().coerceIn(0, ih - 1)
                val sw = (size.width * sx).toInt().coerceIn(1, iw - ox)
                val sh = (size.height * sy).toInt().coerceIn(1, ih - oy)
                drawImage(
                    g.image, srcOffset = IntOffset(ox, oy), srcSize = IntSize(sw, sh),
                    dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Low
                )
            } else {
                drawRect(Color(0xCC161C28))
            }
            drawRect(tint)
            drawRect(
                Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color.Transparent), endY = size.height * 0.5f),
                size = Size(size.width, size.height * 0.5f)
            )
        }
        .border(1.dp, Brush.verticalGradient(listOf(Color(0x8CFFFFFF), Color(0x1AFFFFFF))), shape)
}

// ---------------------------------------------------------------------------
// Kumanda OK tuşu: kısa basış = tıkla, basılı tut = uzun basış
// ---------------------------------------------------------------------------
class KeyTrack {
    var down = false
    var long = false
}

fun isOk(k: Key) = k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter

@Composable
fun tvKeys(onClick: () -> Unit, onLong: (() -> Unit)? = null): Modifier {
    val track = remember { KeyTrack() }
    val click = rememberUpdatedState(onClick)
    val longClick = rememberUpdatedState(onLong)
    val hasLong = onLong != null
    return Modifier
        .pointerInput(hasLong) {
            detectTapGestures(
                onTap = { click.value.invoke() },
                onLongPress = if (hasLong) { { longClick.value?.invoke() } } else null
            )
        }
        .onPreviewKeyEvent { e ->
            if (!isOk(e.key)) {
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

/** Görünürken içerik çizilir; gizlenince söner ve bellekten çıkar. */
@Composable
fun Presence(visible: Boolean, inMs: Int = 200, outMs: Int = 150, content: @Composable (State<Float>) -> Unit) {
    val p = animateFloatAsState(
        if (visible) 1f else 0f,
        tween(if (visible) inMs else outMs, easing = FastOutSlowInEasing),
        label = "presence"
    )
    val vis = rememberUpdatedState(visible)
    val present by remember { derivedStateOf { vis.value || p.value > 0.001f } }
    if (present) content(p)
}

private fun safeFocus(r: FocusRequester) {
    runCatching { r.requestFocus() }
}

// ---------------------------------------------------------------------------
// Kök
// ---------------------------------------------------------------------------
@Composable
fun LauncherApp(st: LauncherState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Uygulama listesi: açılışta ve paket değişince (arka planda)
    LaunchedEffect(st.appsVersion) {
        val changed = st.changedPkg
        st.changedPkg = null
        val list = withContext(Dispatchers.IO) { AppRepo.list(ctx.applicationContext, changed) }
        st.apps = list
        st.syncOrder(list)
    }

    // Çalan müzik: sadece launcher ekrandayken bakılır (uygulama açıkken hiç iş yapmaz)
    var media by remember { mutableStateOf<MediaInfo?>(null) }
    LaunchedEffect(st.resumed) {
        while (st.resumed) {
            media = withContext(Dispatchers.IO) { readMedia(ctx, media) }
            delay(2000)
        }
    }

    // Boşta kalınca saat modu
    LaunchedEffect(st.resumed) {
        while (st.resumed) {
            delay(15000)
            val limit = Config.AMBIENT_AFTER_MIN * 60000L
            if (limit > 0 && !st.ambient && st.sheet == null &&
                System.currentTimeMillis() - st.lastInput > limit
            ) {
                st.overlay = Overlay.NONE
                st.ambient = true
            }
        }
    }

    LaunchedEffect(st.toastId) {
        if (st.toast != null) {
            delay(2600)
            st.toast = null
        }
    }

    BackHandler(enabled = true) {
        when {
            st.sheet != null -> st.closeSheet()
            st.moving -> st.finishMove()
            st.overlay != Overlay.NONE -> st.overlay = Overlay.NONE
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val density = LocalDensity.current.density
        val baker = remember { SceneBaker(ctx.applicationContext) }
        val bakeLock = remember { Mutex() }
        LaunchedEffect(Unit) { if (st.wallItems.isEmpty()) st.initWallpapers(baker.availableWallpapers()) }

        var home by remember { mutableStateOf<SceneSet?>(null) }
        var control by remember { mutableStateOf<SceneSet?>(null) }
        var bare by remember { mutableStateOf<SceneSet?>(null) }
        val sceneKey = "$w:$h:${st.wallIndex}:${st.boldClock}:${st.depth}"

        suspend fun bake(kind: SceneKind): SceneSet = bakeLock.withLock {
            withContext(Dispatchers.Default) {
                baker.bake(kind, w, h, density, st.wallIndex, st.boldClock, st.depth)
            }
        }

        // Ana sahne: dakikada bir pişirilir; launcher görünmüyorken hiç çalışmaz
        LaunchedEffect(st.resumed, sceneKey) {
            if (!st.resumed) {
                delay(1500)
                bakeLock.withLock { baker.release() }
                control = null
                bare = null
                return@LaunchedEffect
            }
            while (true) {
                val minute = System.currentTimeMillis() / 60000L
                val cur = home
                if (cur == null || cur.minute != minute || !cur.key.startsWith(sceneKey)) {
                    home = bake(SceneKind.HOME)
                    control = null
                    bare = null
                }
                val now = System.currentTimeMillis()
                delay(60000L - now % 60000L + 30L)
            }
        }

        val wantControl = st.overlay == Overlay.CONTROL && !st.ambient
        LaunchedEffect(wantControl, home) {
            val hm = home ?: return@LaunchedEffect
            val c = control
            if (wantControl && (c == null || c.minute != hm.minute || !c.key.startsWith(sceneKey))) {
                control = bake(SceneKind.CONTROL)
            }
        }
        LaunchedEffect(st.ambient, home) {
            val hm = home ?: return@LaunchedEffect
            val b = bare
            if (st.ambient && (b == null || b.minute != hm.minute || !b.key.startsWith(sceneKey))) {
                bare = bake(SceneKind.BARE)
            }
        }

        val accent = home?.accent ?: Color(0xFF34D399)
        val glassImg = home?.glass
        val glass = remember(glassImg, w, h) { glassImg?.let { Glass(it, w.toFloat(), h.toFloat()) } }

        CompositionLocalProvider(LocalAccent provides accent, LocalGlass provides glass) {
            // 1) Sahne (tek resim)
            val ccAmt = animateFloatAsState(
                if (wantControl && control != null) 1f else 0f, tween(260, easing = FastOutSlowInEasing), label = "cc"
            )
            val ambAmt = animateFloatAsState(if (st.ambient && bare != null) 1f else 0f, tween(500), label = "amb")
            val homeImg = home?.home
            val ctrlImg = control?.home
            val bareImg = bare?.home
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val a = ccAmt.value
                    val b = ambAmt.value
                    val dst = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                    if (homeImg == null) {
                        drawRect(Color.Black)
                    } else if (a < 1f && b < 1f) {
                        drawImage(homeImg, dstSize = dst, filterQuality = FilterQuality.Medium)
                    }
                    if (ctrlImg != null && a > 0f) drawImage(ctrlImg, dstSize = dst, alpha = a, filterQuality = FilterQuality.Medium)
                    if (bareImg != null && b > 0f) {
                        drawImage(bareImg, dstSize = dst, alpha = b, filterQuality = FilterQuality.Medium)
                        drawRect(Color.Black, alpha = 0.22f * b)
                    }
                }
            )

            val dockReq = remember { FocusRequester() }
            val homeVisible = st.overlay == Overlay.NONE && !st.ambient && home != null

            // 2) Ana ekran arayüzü
            Presence(homeVisible, inMs = 180, outMs = 120) { p ->
                HomeUi(st, media, p, dockReq, w, h)
            }
            // 3) Tüm uygulamalar
            Presence(st.overlay == Overlay.APPS && !st.ambient, inMs = 220, outMs = 150) { p ->
                AppsOverlay(st, p)
            }
            // 4) Kontrol merkezi
            Presence(wantControl, inMs = 280, outMs = 180) { p ->
                ControlPanel(st, media, p, glass?.image, w)
            }
            // 5) Alt pencereler
            Presence(st.sheet != null, inMs = 180, outMs = 120) { p ->
                SheetLayer(st, p)
            }
            // 6) Saat modu
            Presence(st.ambient, inMs = 500, outMs = 200) { p ->
                AmbientExtras(media, p) { st.ambient = false }
            }
            // 7) Kısa mesaj
            Presence(st.toast != null, inMs = 160, outMs = 200) { p ->
                ToastPill(st.toast ?: "", p)
            }

            // Odak: ana ekrana dönünce veya pencere odağı geri gelince dock'a kesin odaklan
            LaunchedEffect(homeVisible, st.homeTick, st.resumed, st.refocusTick) {
                if (homeVisible && st.resumed && st.sheet == null) {
                    delay(40)
                    safeFocus(dockReq)
                    delay(140)
                    safeFocus(dockReq)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ana ekran
// ---------------------------------------------------------------------------
@Composable
fun HomeUi(st: LauncherState, media: MediaInfo?, p: State<Float>, dockReq: FocusRequester, w: Int, h: Int) {
    val ctx = LocalContext.current
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = p.value
            translationY = (1f - p.value) * 18.dp.toPx()
        }
    ) {
        if (media != null) {
            NowPlayingPill(media, Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 28.dp))
        }

        var net by remember { mutableStateOf(NetInfo("Wi-Fi", "", Glyph.WIFI)) }
        LaunchedEffect(st.resumed) {
            while (st.resumed) {
                net = withContext(Dispatchers.IO) { networkInfo(ctx) }
                delay(8000)
            }
        }

        Row(
            Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillButton(net.glyph, net.title) { openSettings(ctx, Settings.ACTION_WIFI_SETTINGS) }
            RoundButton(Glyph.SLIDERS, 42.dp) { st.overlay = Overlay.CONTROL }
            RoundButton(Glyph.GRID, 42.dp) { st.overlay = Overlay.APPS }
            RoundButton(Glyph.GEAR, 42.dp) { openSettings(ctx, Settings.ACTION_SETTINGS) }
        }

        Dock(st, dockReq, w, h)
    }
}

@Composable
fun BoxScope.Dock(st: LauncherState, req: FocusRequester, w: Int, h: Int) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val dens = LocalDensity.current
    val accent = LocalAccent.current
    val items = remember(st.apps, st.order, st.hidden) { st.dockApps() }
    val n = items.size
    val vis = Config.DOCK_VISIBLE
    val index = st.dockIndex.coerceIn(0, (n - 1).coerceAtLeast(0))
    var focused by remember { mutableStateOf(false) }

    // Kutucuk görselleri: dock sırasıyla, görünenler önce
    val tilePx = with(dens) { IntSize(Dims.TILE_W.dp.roundToPx(), Dims.TILE_H.dp.roundToPx()) }
    LaunchedEffect(items, tilePx) {
        if (items.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                AppRepo.renderTiles(ctx.applicationContext, items, tilePx.width, tilePx.height)
                AppRepo.renderTiles(ctx.applicationContext, st.apps, tilePx.width, tilePx.height)
            }
        }
    }

    val stridePx = with(dens) { (Dims.TILE_W + Dims.GAP).dp.toPx() }
    val scroll = remember { Animatable(-st.dockFirst * stridePx) }
    LaunchedEffect(st.dockFirst, stridePx) {
        scroll.animateTo(-st.dockFirst * stridePx, spring(dampingRatio = 0.9f, stiffness = 560f))
    }

    fun reveal(i: Int) {
        var f = st.dockFirst
        if (i > f + vis - 2) f = i - (vis - 2)
        if (i < f + 1) f = i - 1
        st.dockFirst = f.coerceIn(0, (n - vis).coerceAtLeast(0))
    }

    fun launch(i: Int) {
        val item = items.getOrNull(i) ?: return
        val d = dens.density
        val slot = i - st.dockFirst
        val left = (w - Dims.DOCK_W * d) / 2f
        val top = h - (Dims.DOCK_BOTTOM + Dims.DOCK_H) * d
        val x = left + (Dims.PAD + slot * (Dims.TILE_W + Dims.GAP)) * d
        val y = top + Dims.PAD * d
        val opts = launchOptions(view, x.toInt(), y.toInt(), (Dims.TILE_W * d).toInt(), (Dims.TILE_H * d).toInt())
        if (!launchApp(ctx, item, opts)) st.say("${item.label} açılamadı")
    }

    val track = remember { KeyTrack() }

    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = Dims.DOCK_BOTTOM.dp)
            .size(Dims.DOCK_W.dp, Dims.DOCK_H.dp)
            .focusRequester(req)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                when {
                    e.key == Key.DirectionLeft || e.key == Key.DirectionRight -> {
                        if (e.type == KeyEventType.KeyDown && n > 0) {
                            val cur = st.dockIndex.coerceIn(0, n - 1)
                            val ni = cur + if (e.key == Key.DirectionLeft) -1 else 1
                            if (ni in 0 until n) {
                                if (st.moving) st.swap(items[cur].pkg, items[ni].pkg)
                                st.dockIndex = ni
                                reveal(ni)
                            }
                        }
                        true
                    }
                    isOk(e.key) -> {
                        if (e.type == KeyEventType.KeyDown) {
                            if (e.nativeKeyEvent.repeatCount == 0) {
                                track.down = true
                                track.long = false
                            } else if (track.down && !track.long && !st.moving) {
                                track.long = true
                                items.getOrNull(st.dockIndex)?.let {
                                    st.sheetApp = it
                                    st.sheetFromDock = true
                                    st.sheet = SheetKind.APP_MENU
                                }
                            }
                        } else if (e.type == KeyEventType.KeyUp) {
                            val fire = track.down && !track.long
                            track.down = false
                            track.long = false
                            if (fire) {
                                if (st.moving) st.finishMove() else launch(st.dockIndex.coerceIn(0, (n - 1).coerceAtLeast(0)))
                            }
                        }
                        true
                    }
                    e.key == Key.DirectionDown -> true
                    e.key == Key.DirectionUp -> st.moving
                    e.key == Key.Back && st.moving -> {
                        if (e.type == KeyEventType.KeyUp) st.finishMove()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        // Kaydırılan şerit: sadece görünen pencerenin çevresi çizilir, hareket GPU'da (translationX)
        Box(Modifier.fillMaxSize().padding(horizontal = (Dims.PAD - 6f).dp).clipToBounds()) {
            Box(Modifier.fillMaxHeight().graphicsLayer { translationX = scroll.value }) {
                if (n > 0) {
                    val from = (st.dockFirst - 4).coerceAtLeast(0)
                    val to = (st.dockFirst + vis + 4).coerceAtMost(n - 1)
                    for (i in from..to) {
                        val item = items[i]
                        key(item.pkg) {
                            DockTile(
                                item,
                                xDp = 6f + i * (Dims.TILE_W + Dims.GAP),
                                sel = focused && i == index,
                                moving = st.moving && i == index,
                                onTap = {
                                    st.dockIndex = i
                                    reveal(i)
                                    if (st.moving) st.finishMove() else launch(i)
                                },
                                onLong = {
                                    st.dockIndex = i
                                    st.sheetApp = item
                                    st.sheetFromDock = true
                                    st.sheet = SheetKind.APP_MENU
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Daha fazla uygulama olduğunu gösteren oklar
    val leftMore = st.dockFirst > 0
    val rightMore = st.dockFirst + vis < n
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = (Dims.DOCK_BOTTOM + Dims.DOCK_H / 2f - 9f).dp)
            .width((Dims.DOCK_W + 56f).dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier
                .size(28.dp)
                .graphicsLayer { alpha = if (leftMore) 0.80f else 0f }
                .pointerInput(leftMore, n) {
                    if (leftMore) detectTapGestures {
                        val ni = (st.dockIndex - 1).coerceAtLeast(0)
                        st.dockIndex = ni
                        reveal(ni)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.LEFT, 18.dp, Color.White)
        }
        Box(
            Modifier
                .size(28.dp)
                .graphicsLayer { alpha = if (rightMore) 0.80f else 0f }
                .pointerInput(rightMore, n) {
                    if (rightMore) detectTapGestures {
                        val ni = (st.dockIndex + 1).coerceAtMost((n - 1).coerceAtLeast(0))
                        st.dockIndex = ni
                        reveal(ni)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.RIGHT, 18.dp, Color.White)
        }
    }

    // Odaktaki uygulamanın adı
    val label = when {
        !focused || n == 0 -> ""
        st.moving -> "◀ ▶ ile taşı · OK ile bitir"
        else -> items[index].label
    }
    BasicText(
        label,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        style = TextStyle(
            color = if (st.moving) accent else Color(0xF2FFFFFF),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        maxLines = 1
    )
}

@Composable
fun DockTile(item: AppItem, xDp: Float, sel: Boolean, moving: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val tapRef = rememberUpdatedState(onTap)
    val longRef = rememberUpdatedState(onLong)
    val accent = LocalAccent.current
    val z = animateFloatAsState(
        if (sel) 1.08f else 1f,
        spring(dampingRatio = 0.72f, stiffness = 650f),
        label = "tile"
    )
    val lift = animateFloatAsState(if (moving) 1f else 0f, tween(160), label = "lift")
    Box(
        Modifier
            .offset(x = xDp.dp, y = Dims.PAD.dp)
            .size(Dims.TILE_W.dp, Dims.TILE_H.dp)
            .pointerInput(item.pkg) {
                detectTapGestures(
                    onTap = { tapRef.value.invoke() },
                    onLongPress = { longRef.value.invoke() }
                )
            }
            .graphicsLayer {
                scaleX = z.value
                scaleY = z.value
                translationY = -lift.value * 7.dp.toPx()
            }
            .drawBehind {
                val r = CornerRadius(size.height * 0.16f)
                if (sel) {
                    val g = 6.dp.toPx()
                    drawRoundRect(
                        accent.copy(alpha = 0.22f), topLeft = Offset(-g, -g),
                        size = Size(size.width + 2 * g, size.height + 2 * g),
                        cornerRadius = CornerRadius(r.x + g)
                    )
                }
                val t = item.tile
                if (t != null) {
                    drawImage(
                        t, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.Medium
                    )
                } else {
                    drawRoundRect(Color(0x33FFFFFF), cornerRadius = r)
                }
                if (sel) {
                    drawRoundRect(
                        if (moving) accent else Color.White,
                        style = Stroke(2.5.dp.toPx()),
                        cornerRadius = r
                    )
                }
            }
    ) {
        if (moving) {
            GlyphIcon(Glyph.LEFT, 16.dp, Color.White, Modifier.align(Alignment.CenterStart).offset(x = (-20).dp))
            GlyphIcon(Glyph.RIGHT, 16.dp, Color.White, Modifier.align(Alignment.CenterEnd).offset(x = 20.dp))
        }
    }
}

@Composable
fun PillButton(glyph: Glyph, text: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    var f by remember { mutableStateOf(false) }
    val z = animateFloatAsState(if (f) 1.08f else 1f, spring(dampingRatio = 0.72f, stiffness = 680f), label = "pill")
    val shape = RoundedCornerShape(21.dp)
    Row(
        Modifier
            .height(42.dp)
            .graphicsLayer { scaleX = z.value; scaleY = z.value }
            .drawBehind {
                if (f) {
                    val g = 5.dp.toPx()
                    drawRoundRect(
                        accent.copy(alpha = 0.28f),
                        topLeft = Offset(-g, -g),
                        size = Size(size.width + 2 * g, size.height + 2 * g),
                        cornerRadius = CornerRadius(size.height / 2f + g)
                    )
                }
            }
            .clip(shape)
            .background(if (f) Color.White else Color(0x700E1420))
            .border(
                1.dp,
                if (f) Brush.verticalGradient(listOf(Color.White, Color.White))
                else Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x1CFFFFFF))),
                shape
            )
            .onFocusChanged { f = it.isFocused }
            .then(tvKeys(onClick))
            .focusable()
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(glyph, 18.dp, if (f) INK else Color(0xF5FFFFFF))
        Spacer(Modifier.width(8.dp))
        BasicText(
            text,
            style = TextStyle(color = if (f) INK else Color(0xF5FFFFFF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
fun RoundButton(
    glyph: Glyph,
    size: Dp,
    requester: FocusRequester? = null,
    idleBg: Color = Color(0x700E1420),
    onClick: () -> Unit
) {
    val accent = LocalAccent.current
    var f by remember { mutableStateOf(false) }
    val z = animateFloatAsState(if (f) 1.12f else 1f, spring(dampingRatio = 0.68f, stiffness = 680f), label = "round")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = z.value; scaleY = z.value }
            .drawBehind {
                if (f) {
                    drawCircle(accent.copy(alpha = 0.30f), radius = this.size.minDimension / 2f + 5.dp.toPx())
                }
            }
            .clip(CircleShape)
            .background(if (f) Color.White else idleBg)
            .border(
                1.dp,
                if (f) Brush.verticalGradient(listOf(Color.White, Color.White))
                else Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x1CFFFFFF))),
                CircleShape
            )
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .then(tvKeys(onClick))
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, size * 0.46f, if (f) INK else Color(0xF5FFFFFF))
    }
}

@Composable
fun AlbumArt(media: MediaInfo, size: Dp, corner: Dp) {
    val art = media.art
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner)).background(Color(0x40808A9E))
            .drawBehind { if (art != null) drawImage(art, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt())) },
        contentAlignment = Alignment.Center
    ) {
        if (art == null) GlyphIcon(Glyph.MUSIC, size * 0.5f, Color.White)
    }
}

@Composable
fun NowPlayingPill(media: MediaInfo, modifier: Modifier) {
    Row(
        modifier
            .height(48.dp)
            .widthIn(max = 360.dp)
            .then(glassBg(RoundedCornerShape(24.dp), Color(0x1AFFFFFF)))
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(media, 36.dp, 18.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            BasicText(
                media.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            )
            if (media.artist.isNotEmpty()) {
                BasicText(
                    media.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Color(0xB3FFFFFF), fontSize = 11.sp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tüm uygulamalar
// ---------------------------------------------------------------------------
@Composable
fun AppsOverlay(st: LauncherState, p: State<Float>) {
    val ctx = LocalContext.current
    val g = LocalGlass.current
    val items = remember(st.apps, st.order, st.hidden) { st.allAppsOrdered() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(40)
        safeFocus(first)
    }
    LaunchedEffect(st.refocusTick) {
        if (st.refocusTick > 0 && st.sheet == null) {
            delay(20)
            safeFocus(first)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = p.value }
            .drawBehind {
                if (g != null) {
                    drawImage(
                        g.image, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.Low
                    )
                }
                drawRect(Color(0x99000000))
            }
    ) {
        Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 37.dp, end = 37.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    "Uygulamalar",
                    style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                )
                PillButton(Glyph.LEFT, "Geri") { st.overlay = Overlay.NONE }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(start = 37.dp, end = 37.dp, top = 18.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items, key = { _, it -> it.pkg }) { i, item ->
                    GridTile(
                        item,
                        hidden = item.pkg in st.hidden,
                        requester = if (i == 0) first else null,
                        onClick = { if (!launchApp(ctx, item)) st.say("${item.label} açılamadı") },
                        onLong = {
                            st.sheetApp = item
                            st.sheetFromDock = false
                            st.sheet = SheetKind.APP_MENU
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GridTile(item: AppItem, hidden: Boolean, requester: FocusRequester?, onClick: () -> Unit, onLong: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val z = animateFloatAsState(if (f) 1.1f else 1f, spring(dampingRatio = 0.72f, stiffness = 650f), label = "grid")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dims.TILE_H.dp)
                .graphicsLayer { scaleX = z.value; scaleY = z.value; alpha = if (hidden && !f) 0.55f else 1f }
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                .onFocusChanged { f = it.isFocused }
                .then(tvKeys(onClick, onLong))
                .focusable()
                .drawBehind {
                    val r = CornerRadius(size.height * 0.16f)
                    val t = item.tile
                    if (t != null) {
                        drawImage(
                            t, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            filterQuality = FilterQuality.Medium
                        )
                    } else {
                        drawRoundRect(Color(0x33FFFFFF), cornerRadius = r)
                    }
                    if (f) drawRoundRect(Color.White, style = Stroke(2.5.dp.toPx()), cornerRadius = r)
                }
        ) {
            if (hidden) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(Glyph.EYE_OFF, 14.dp, Color.White)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            item.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = if (f) Color.White else Color(0xB3FFFFFF),
                fontSize = 13.sp,
                fontWeight = if (f) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Kontrol merkezi
// ---------------------------------------------------------------------------
@Composable
fun ControlPanel(st: LauncherState, media: MediaInfo?, p: State<Float>, thumb: ImageBitmap?, rootW: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current
    val dens = LocalDensity.current
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(40)
        safeFocus(first)
    }
    LaunchedEffect(st.refocusTick) {
        if (st.refocusTick > 0 && st.sheet == null) {
            delay(20)
            safeFocus(first)
        }
    }

    // Durumlar panel açılınca bir kez okunur
    var net by remember { mutableStateOf(NetInfo("Wi-Fi", "", Glyph.WIFI)) }
    var audio by remember { mutableStateOf("") }
    var bt by remember { mutableStateOf<Boolean?>(null) }
    var ram by remember { mutableStateOf(0L) }
    var sleepLeft by remember { mutableStateOf(0) }
    var isHome by remember { mutableStateOf(true) }
    var sources by remember { mutableStateOf<List<TvSource>>(emptyList()) }
    val notifVersion = NotifBus.version
    var notifCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            net = networkInfo(ctx)
            audio = audioInfo(ctx)
            bt = bluetoothOn(ctx)
            ram = freeRamMb(ctx)
            sleepLeft = SleepTimer.remainingMin(ctx)
            isHome = SysToggles.isDefaultHome(ctx)
            sources = tvSources(ctx)
        }
        if (st.prefs.adbReady()) st.sys = withContext(Dispatchers.IO) { SysToggles.query(ctx) }
    }
    LaunchedEffect(notifVersion) {
        notifCount = withContext(Dispatchers.IO) { readNotifications(ctx).size }
    }

    fun adbToggle(group: String, name: String) {
        if (st.adbBusy) return
        scope.launch {
            st.adbBusy = true
            if (!st.prefs.adbReady()) st.say("TV'de çıkan pencerede 'Her zaman izin ver'i seç")
            val cur = st.sys?.get(group) ?: true
            val res = withContext(Dispatchers.IO) { SysToggles.set(ctx, group, !cur) }
            st.sys = withContext(Dispatchers.IO) { SysToggles.query(ctx) }
            isHome = withContext(Dispatchers.IO) { SysToggles.isDefaultHome(ctx) }
            if (res.msg.isNotEmpty()) {
                st.say(res.msg)
            } else if (res.ok) {
                st.say(if (cur) "$name kapatıldı" else "$name açıldı")
            } else {
                st.say("İşlem tamamlanamadı")
            }
            st.adbBusy = false
        }
    }

    fun sysSub(group: String): String = when {
        st.adbBusy -> "…"
        !st.prefs.adbReady() -> "Dokun: izin ver"
        st.sys == null -> "—"
        st.sys?.get(group) == true -> "Açık"
        else -> "Kapalı"
    }

    val panelW = 432.dp
    val origin = with(dens) { Offset(rootW - (panelW + 28.dp).toPx(), 24.dp.toPx()) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { st.overlay = Overlay.NONE } }
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) { detectTapGestures { /* consume taps inside panel */ } }
                .padding(end = 28.dp, top = 24.dp, bottom = 24.dp)
                .width(panelW)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = (1f - p.value) * 120.dp.toPx()
                    alpha = p.value
                }
                .then(glassBg(RoundedCornerShape(34.dp), accent.copy(alpha = 0.10f), origin))
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (media != null) MediaCard(media)

                val curWallName = st.currentWallpaperName()
                TileRow {
                    CcTile(
                        net.glyph, net.title, net.sub, on = net.sub != "Bağlı değil", requester = first,
                        modifier = Modifier.weight(1f)
                    ) { openSettings(ctx, Settings.ACTION_WIFI_SETTINGS) }
                    CcTile(
                        Glyph.BLUETOOTH, "Bluetooth",
                        when (bt) { true -> "Açık"; false -> "Kapalı"; null -> "Ayarlar" },
                        on = bt == true, modifier = Modifier.weight(1f)
                    ) { openSettings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) }
                }
                TileRow {
                    CcTile(Glyph.SPEAKER, "Ses", audio, on = null, modifier = Modifier.weight(1f)) {
                        openSettings(ctx, Settings.ACTION_SOUND_SETTINGS)
                    }
                    CcTile(Glyph.DISPLAY, "Görüntü", "Resim ve ekran", on = null, modifier = Modifier.weight(1f)) {
                        openSettings(ctx, "android.settings.PICTURE_SETTINGS", Settings.ACTION_DISPLAY_SETTINGS)
                    }
                }
                TileRow {
                    CcTile(
                        Glyph.INPUT, "Girişler",
                        if (sources.isEmpty()) "Canlı TV" else "${sources.size} kaynak",
                        on = null, modifier = Modifier.weight(1f)
                    ) { st.sheet = SheetKind.INPUTS }
                    CcTile(
                        Glyph.MOON, "Uyku zamanlayıcı",
                        if (sleepLeft > 0) "$sleepLeft dk sonra" else "Kapalı",
                        on = sleepLeft > 0, modifier = Modifier.weight(1f)
                    ) {
                        val next = SleepTimer.STEPS.firstOrNull { it > sleepLeft } ?: 0
                        scope.launch {
                            if (next > 0 && !st.prefs.adbReady()) {
                                st.say("TV'de çıkan pencerede 'Her zaman izin ver'i seç")
                                val ok = withContext(Dispatchers.IO) { Adb.exec(ctx, "echo ok") != null }
                                if (!ok) {
                                    st.say("Bağlanamadı: Ağ hata ayıklama açık mı?")
                                    return@launch
                                }
                            }
                            SleepTimer.set(ctx, next)
                            sleepLeft = next
                            st.say(if (next > 0) "TV $next dakika sonra kapanacak" else "Uyku zamanlayıcı kapalı")
                        }
                    }
                }
                TileRow {
                    CcTile(
                        Glyph.IMAGE, "Duvar kağıdı",
                        if (st.randomWall) "Rastgele · $curWallName" else curWallName,
                        on = if (st.randomWall) true else null, thumb = thumb, modifier = Modifier.weight(1f)
                    ) { st.sheet = SheetKind.WALLPAPER }
                    CcTile(
                        Glyph.TYPE, "Saat stili", if (st.boldClock) "Kalın" else "İnce",
                        on = null, modifier = Modifier.weight(1f)
                    ) { st.toggleBoldClock() }
                }
                TileRow {
                    CcTile(
                        Glyph.LAYERS, "Derinlik", if (st.depth) "Açık" else "Kapalı",
                        on = st.depth, modifier = Modifier.weight(1f)
                    ) { st.toggleDepth() }
                    CcTile(
                        Glyph.BOLT, "Hızlandır", if (ram > 0) "$ram MB boş" else "RAM temizle",
                        on = null, modifier = Modifier.weight(1f)
                    ) {
                        if (!st.adbBusy) {
                            scope.launch {
                                st.adbBusy = true
                                if (!st.prefs.adbReady()) st.say("TV'de çıkan pencerede 'Her zaman izin ver'i seç")
                                val freed = withContext(Dispatchers.IO) { SysToggles.boost(ctx) }
                                ram = freeRamMb(ctx)
                                st.say(if (freed == null) "Bağlanamadı: Ağ hata ayıklama açık mı?" else "$freed MB boşaltıldı")
                                st.adbBusy = false
                            }
                        }
                    }
                }
                TileRow {
                    CcTile(
                        Glyph.CAST, "Yansıtma", sysSub(SysToggles.CAST),
                        on = st.sys?.get(SysToggles.CAST) == true, modifier = Modifier.weight(1f)
                    ) { adbToggle(SysToggles.CAST, "Yansıtma") }
                    CcTile(
                        Glyph.MIC, "Sesli asistan", sysSub(SysToggles.ASSIST),
                        on = st.sys?.get(SysToggles.ASSIST) == true, modifier = Modifier.weight(1f)
                    ) { adbToggle(SysToggles.ASSIST, "Sesli asistan") }
                }
                TileRow {
                    CcTile(
                        Glyph.HOME, "Home tuşu",
                        if (isHome) "Bu launcher aktif" else "Dokun: onar",
                        on = isHome, modifier = Modifier.weight(1f)
                    ) {
                        if (!st.adbBusy) {
                            scope.launch {
                                st.adbBusy = true
                                if (!st.prefs.adbReady()) st.say("TV'de çıkan pencerede 'Her zaman izin ver'i seç")
                                val res = withContext(Dispatchers.IO) { SysToggles.repairHome(ctx, allowPrompt = true, allowDisableGtv = true) }
                                st.sys = withContext(Dispatchers.IO) { SysToggles.query(ctx) }
                                isHome = withContext(Dispatchers.IO) { SysToggles.isDefaultHome(ctx) }
                                st.say(res.msg)
                                st.adbBusy = false
                            }
                        }
                    }
                    CcTile(
                        Glyph.TV, "Google TV ekranı", sysSub(SysToggles.GTV),
                        on = st.sys?.get(SysToggles.GTV) == true, modifier = Modifier.weight(1f)
                    ) { adbToggle(SysToggles.GTV, "Google TV ekranı") }
                }
                TileRow {
                    CcTile(
                        Glyph.BELL, "Bildirimler",
                        if (!hasMediaAccess(ctx)) "İzin yok" else if (notifCount == 0) "Yok" else "$notifCount yeni",
                        on = notifCount > 0, modifier = Modifier.weight(1f)
                    ) { st.sheet = SheetKind.NOTIFS }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RoundButton(Glyph.ACCESS, 50.dp) { openSettings(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                    RoundButton(Glyph.CLOCK, 50.dp) {
                        st.overlay = Overlay.NONE
                        st.ambient = true
                    }
                    RoundButton(Glyph.BAG, 50.dp) {
                        if (!launchPackage(ctx, "com.android.vending")) st.say("Play Store bulunamadı")
                    }
                    RoundButton(Glyph.USER, 50.dp) {
                        openSettings(ctx, Settings.ACTION_SYNC_SETTINGS, Settings.ACTION_ADD_ACCOUNT)
                    }
                    RoundButton(Glyph.POWER, 50.dp) { st.sheet = SheetKind.POWER }
                    RoundButton(Glyph.GEAR, 50.dp) { openSettings(ctx, Settings.ACTION_SETTINGS) }
                }
            }
        }
    }
}

@Composable
fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun CcTile(
    glyph: Glyph,
    title: String,
    sub: String,
    on: Boolean?,
    modifier: Modifier = Modifier,
    thumb: ImageBitmap? = null,
    requester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val accent = LocalAccent.current
    var f by remember { mutableStateOf(false) }
    val z = animateFloatAsState(if (f) 1.04f else 1f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "cc")
    val shape = RoundedCornerShape(22.dp)
    val bg = when {
        f -> Color(0xF2FFFFFF)
        on == true -> accent.copy(alpha = 0.34f)
        else -> Color(0x1CFFFFFF)
    }
    val edge = when {
        f -> Color.White
        on == true -> accent.copy(alpha = 0.75f)
        else -> Color(0x26FFFFFF)
    }
    Row(
        modifier
            .height(66.dp)
            .graphicsLayer { scaleX = z.value; scaleY = z.value }
            .clip(shape)
            .background(bg)
            .border(1.dp, edge, shape)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .then(tvKeys(onClick))
            .focusable()
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (on == true || f) accent else Color(0x2EFFFFFF))
                .drawBehind {
                    if (thumb != null) {
                        drawImage(
                            thumb, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            filterQuality = FilterQuality.Low
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (thumb == null) GlyphIcon(glyph, 20.dp, Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            BasicText(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = if (f) INK else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
            if (sub.isNotEmpty()) {
                BasicText(
                    sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = if (f) Color(0x9910141F) else Color(0xB3FFFFFF), fontSize = 12.sp)
                )
            }
        }
    }
}

@Composable
fun MediaCard(media: MediaInfo) {
    val accent = LocalAccent.current
    val tc = media.controller.transportControls
    Row(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(accent.copy(alpha = 0.22f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(media, 60.dp, 14.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                media.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            )
            if (media.artist.isNotEmpty()) {
                BasicText(
                    media.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Color(0xB3FFFFFF), fontSize = 12.sp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundButton(Glyph.PREV, 38.dp) { runCatching { tc.skipToPrevious() } }
            RoundButton(if (media.playing) Glyph.PAUSE else Glyph.PLAY, 38.dp) {
                runCatching { if (media.playing) tc.pause() else tc.play() }
            }
            RoundButton(Glyph.NEXT, 38.dp) { runCatching { tc.skipToNext() } }
        }
    }
}

// ---------------------------------------------------------------------------
// Alt pencereler: girişler, bildirimler, güç, uygulama menüsü
// ---------------------------------------------------------------------------
class SheetRow(val glyph: Glyph, val title: String, val sub: String = "", val action: () -> Unit)

@Composable
fun SheetLayer(st: LauncherState, p: State<Float>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val kind = st.sheet ?: return
    val app = st.sheetApp
    val notifVersion = NotifBus.version

    var sources by remember { mutableStateOf<List<TvSource>>(emptyList()) }
    var notifs by remember { mutableStateOf<List<NotifItem>>(emptyList()) }
    LaunchedEffect(kind, notifVersion) {
        withContext(Dispatchers.IO) {
            if (kind == SheetKind.INPUTS) sources = tvSources(ctx)
            if (kind == SheetKind.NOTIFS) notifs = readNotifications(ctx)
        }
    }

    fun done(block: () -> Unit) {
        st.closeSheet()
        block()
    }

    val title: String
    val rows = ArrayList<SheetRow>()
    when (kind) {
        SheetKind.INPUTS -> {
            title = "Girişler"
            rows += SheetRow(Glyph.TV, "Canlı TV", "Anten / uydu") { done { openSource(ctx, null) } }
            for (s in sources.filter { it.passthrough }) {
                rows += SheetRow(Glyph.INPUT, s.label) { done { openSource(ctx, s) } }
            }
        }
        SheetKind.NOTIFS -> {
            title = "Bildirimler"
            if (!hasMediaAccess(ctx)) {
                rows += SheetRow(Glyph.INFO, "Bildirim izni yok", "Telefondaki kurulum scriptini çalıştır") { st.closeSheet() }
            } else if (notifs.isEmpty()) {
                rows += SheetRow(Glyph.CHECK, "Yeni bildirim yok") { st.closeSheet() }
            } else {
                rows += SheetRow(Glyph.TRASH, "Tümünü temizle") { done { dismissNotification(null) } }
                for (n in notifs) {
                    rows += SheetRow(Glyph.BELL, "${n.app}: ${n.title}", n.text) {
                        done {
                            runCatching { n.intent?.send() }
                            if (n.clearable) dismissNotification(n.key)
                        }
                    }
                }
            }
        }
        SheetKind.POWER -> {
            title = "Güç"
            rows += SheetRow(Glyph.MOON, "Ekranı kapat", "Bekleme moduna al") {
                done { scope.launch(Dispatchers.IO) { if (!SysToggles.sleepNow(ctx)) st.say("Bağlanamadı") } }
            }
            rows += SheetRow(Glyph.RESTART, "Yeniden başlat") {
                done { scope.launch(Dispatchers.IO) { if (!SysToggles.reboot(ctx)) st.say("Bağlanamadı") } }
            }
            rows += SheetRow(Glyph.CLOCK, "Saat modu") {
                done {
                    st.overlay = Overlay.NONE
                    st.ambient = true
                }
            }
        }
        SheetKind.APP_MENU -> {
            val a = app
            title = a?.label ?: ""
            if (a != null) {
                rows += SheetRow(Glyph.PLAY, "Aç") { done { launchApp(ctx, a) } }
                if (st.sheetFromDock) {
                    rows += SheetRow(Glyph.MOVE, "Taşı", "◀ ▶ ile yerini değiştir") {
                        done {
                            st.overlay = Overlay.NONE
                            st.moving = true
                        }
                    }
                }
                rows += SheetRow(Glyph.PIN, "Başa al") {
                    done {
                        if (a.pkg in st.hidden) st.toggleHidden(a.pkg)
                        st.moveToFront(a.pkg)
                    }
                }
                val isHidden = a.pkg in st.hidden
                rows += SheetRow(Glyph.EYE_OFF, if (isHidden) "Dock'ta göster" else "Dock'tan gizle") {
                    done { st.toggleHidden(a.pkg) }
                }
                rows += SheetRow(Glyph.INFO, "Uygulama bilgisi") { done { openAppInfo(ctx, a.pkg) } }
                if (!a.system) rows += SheetRow(Glyph.TRASH, "Kaldır") { done { uninstallApp(ctx, a.pkg) } }
            }
        }
        SheetKind.WALLPAPER -> {
            title = "Duvar kağıdı"
            val items = st.wallItems
            val activeIdx = if (items.isEmpty()) 0 else ((st.wallIndex % items.size) + items.size) % items.size
            val curName = st.currentWallpaperName()
            rows += SheetRow(
                if (st.randomWall) Glyph.CHECK else Glyph.SHUFFLE,
                "Her açılışta rastgele",
                if (st.randomWall) "Aktif · Şu an: $curName (bas: değiştir)" else "Her açılışta farklı duvar kağıdı"
            ) {
                done {
                    st.enableRandomWallpaper()
                    st.say("Rastgele: ${st.currentWallpaperName()}")
                }
            }
            items.forEachIndexed { i, item ->
                val pinned = !st.randomWall && i == activeIdx
                val showingNow = st.randomWall && i == activeIdx
                val parts = ArrayList<String>()
                if (item.supportsDepth) parts += "Derinlik destekli"
                if (pinned) parts += "Seçili" else if (showingNow) parts += "Şu an açık"
                rows += SheetRow(
                    if (pinned) Glyph.CHECK else Glyph.IMAGE,
                    item.name,
                    parts.joinToString(" · ")
                ) {
                    done {
                        st.selectWallpaper(i)
                        st.say("Duvar kağıdı: ${item.name}")
                    }
                }
            }
        }
    }

    val first = remember(kind) { FocusRequester() }
    LaunchedEffect(kind, rows.size) {
        delay(30)
        safeFocus(first)
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = p.value }
            .background(Color(0x73000000))
            .pointerInput(Unit) { detectTapGestures { st.closeSheet() } }
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .pointerInput(Unit) { detectTapGestures { /* consume taps inside sheet */ } }
                .width(500.dp)
                .heightIn(max = 470.dp)
                .graphicsLayer {
                    val s = 0.94f + 0.06f * p.value
                    scaleX = s
                    scaleY = s
                }
                .then(glassBg(RoundedCornerShape(30.dp), Color(0x33101826)))
                .padding(18.dp)
        ) {
            BasicText(
                title,
                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEachIndexed { i, r ->
                    SheetRowView(r, if (i == 0) first else null)
                }
            }
        }
    }
}

@Composable
fun SheetRowView(row: SheetRow, requester: FocusRequester?) {
    val accent = LocalAccent.current
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(if (f) Color(0xF2FFFFFF) else Color(0x14FFFFFF))
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .then(tvKeys(row.action))
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (f) accent else Color(0x26FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(row.glyph, 18.dp, Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            BasicText(
                row.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = if (f) INK else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            )
            if (row.sub.isNotEmpty()) {
                BasicText(
                    row.sub, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = if (f) Color(0x9910141F) else Color(0xA6FFFFFF), fontSize = 12.sp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Saat modu ve kısa mesaj
// ---------------------------------------------------------------------------
@Composable
fun AmbientExtras(media: MediaInfo?, p: State<Float>, onWake: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = p.value }
            .pointerInput(Unit) { detectTapGestures { onWake() } }
    ) {
        if (media != null) {
            NowPlayingPill(media, Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
        }
    }
}

@Composable
fun ToastPill(text: String, p: State<Float>) {
    Box(Modifier.fillMaxSize()) {
        BasicText(
            text,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .graphicsLayer {
                    alpha = p.value
                    translationY = (1f - p.value) * -12.dp.toPx()
                }
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6161C28))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )
    }
}
