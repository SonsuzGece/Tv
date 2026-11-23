package com.kizmark.tvlauncher

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.Random
import kotlin.math.sin

class WallpaperItem(
    val id: String,
    val name: String,
    @DrawableRes val drawableRes: Int = 0,
    @DrawableRes val fgDrawableRes: Int = 0,
    val proceduralStyle: Int = -1,
    val clockX: Float = 0.50f,
    val clockY: Float = 0.50f,
    val inRandomPool: Boolean = true
) {
    val isPhoto: Boolean get() = drawableRes != 0
    val supportsDepth: Boolean get() = fgDrawableRes != 0 || (!isPhoto && hasProceduralDepth(proceduralStyle))
}

val WALLPAPER_CATALOG = listOf(
    WallpaperItem("ocean", "Okyanus", R.drawable.wp_ocean, 0, clockX = 0.50f, clockY = 0.50f, inRandomPool = true),
    WallpaperItem("luffy", "Mavi Ada", R.drawable.wp_luffy, 0, clockX = 0.50f, clockY = 0.43f, inRandomPool = true),
    WallpaperItem("dubai", "Dubai", R.drawable.wp_dubai, R.drawable.wp_dubai_fg, clockX = 0.50f, clockY = 0.49f, inRandomPool = true),
    WallpaperItem("mountain", "Gece Dağı", R.drawable.wp_mountain, R.drawable.wp_mountain_fg, clockX = 0.50f, clockY = 0.40f, inRandomPool = true),
    WallpaperItem("knight", "Şövalye", R.drawable.wp_knight, 0, clockX = 0.69f, clockY = 0.40f, inRandomPool = true),
    WallpaperItem("rocks", "Kaya Gölü", R.drawable.wallpaper, R.drawable.wallpaper_fg, clockX = 0.50f, clockY = 0.50f, inRandomPool = true),
    WallpaperItem("night_lake", "Gece gölü", proceduralStyle = 0, inRandomPool = false),
    WallpaperItem("aurora", "Aurora", proceduralStyle = 1, inRandomPool = false),
    WallpaperItem("sunset", "Gün batımı", proceduralStyle = 2, inRandomPool = false),
    WallpaperItem("deep_blue", "Derin mavi", proceduralStyle = 3, inRandomPool = false)
)

val WALLPAPER_NAMES = listOf("Gece gölü", "Aurora", "Gün batımı", "Derin mavi")

/** Her hazır duvar kağıdının vurgu rengi (odak ışığı, kenar parlaması, dock tonu). */
val WALLPAPER_ACCENTS = listOf(Color(0xFF8FB4FF), Color(0xFF34E8A0), Color(0xFFFFB067), Color(0xFF6C8CFF))

/** Bu hazır duvar kağıdında saat dağların arkasına girebilir mi? */
fun hasProceduralDepth(style: Int): Boolean = style == 0 || style == 2

class WallCache(val ridges: List<Path>, val stars: FloatArray)

private fun buildRidge(w: Float, h: Float, base: Float, amp: Float, seed: Long): Path {
    val r = Random(seed)
    val p1 = r.nextFloat() * 6.28f
    val p2 = r.nextFloat() * 6.28f
    val p3 = r.nextFloat() * 6.28f
    val f1 = 1.2f + r.nextFloat() * 1.3f
    val f2 = 3.5f + r.nextFloat() * 2.5f
    val f3 = 8f + r.nextFloat() * 6f
    val path = Path()
    path.moveTo(0f, h)
    val steps = 180
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val n = 0.55f * sin(t * f1 * 6.28f + p1) +
            0.30f * sin(t * f2 * 6.28f + p2) +
            0.15f * sin(t * f3 * 6.28f + p3)
        path.lineTo(t * w, h * base - h * amp * (0.5f + 0.5f * n))
    }
    path.lineTo(w, h)
    path.close()
    return path
}

fun buildWallCache(style: Int, w: Float, h: Float): WallCache {
    val ridges = when (style) {
        0 -> listOf(
            buildRidge(w, h, 0.70f, 0.24f, 11L),
            buildRidge(w, h, 0.70f, 0.10f, 23L),
            buildRidge(w, h, 0.70f, 0.05f, 37L)
        )
        1 -> listOf(
            buildRidge(w, h, 0.86f, 0.20f, 5L),
            buildRidge(w, h, 0.94f, 0.14f, 9L),
            buildRidge(w, h, 1.00f, 0.08f, 15L)
        )
        2 -> listOf(
            buildRidge(w, h, 0.66f, 0.24f, 41L),
            buildRidge(w, h, 0.66f, 0.11f, 43L),
            buildRidge(w, h, 0.66f, 0.05f, 47L)
        )
        else -> emptyList()
    }
    val count = 150
    val r = Random(7L)
    val stars = FloatArray(count * 4)
    for (i in 0 until count) {
        stars[i * 4] = r.nextFloat()
        stars[i * 4 + 1] = r.nextFloat() * 0.62f
        stars[i * 4 + 2] = 0.8f + r.nextFloat() * 1.6f
        stars[i * 4 + 3] = 0.25f + r.nextFloat() * 0.65f
    }
    return WallCache(ridges, stars)
}

private fun DrawScope.glow(color: Color, cx: Float, cy: Float, radius: Float) {
    val c = Offset(cx, cy)
    drawCircle(
        Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = radius),
        radius = radius,
        center = c
    )
}

private fun DrawScope.stars(cache: WallCache, maxY: Float) {
    val w = size.width
    val h = size.height
    val k = h / 1080f
    val s = cache.stars
    var i = 0
    while (i < s.size) {
        if (s[i + 1] < maxY) {
            drawCircle(
                Color.White.copy(alpha = s[i + 3]),
                radius = s[i + 2] * k,
                center = Offset(s[i] * w, s[i + 1] * h)
            )
        }
        i += 4
    }
}

private fun DrawScope.reflection(color: Color, cx: Float, horizon: Float) {
    val w = size.width
    val h = size.height
    for (k in 0 until 14) {
        val y = horizon + (h - horizon) * (k + 1) / 15f
        val half = w * (0.012f + 0.004f * k)
        val a = 0.38f * (1f - k / 14f)
        drawLine(
            color.copy(alpha = a),
            Offset(cx - half, y),
            Offset(cx + half, y),
            strokeWidth = h * 0.004f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.ripples(color: Color, horizon: Float) {
    val w = size.width
    val h = size.height
    val r = Random(99L)
    for (k in 0 until 26) {
        val y = horizon + (h - horizon) * r.nextFloat()
        val x = w * r.nextFloat()
        val len = w * (0.02f + 0.06f * r.nextFloat())
        drawLine(
            color.copy(alpha = 0.05f + 0.07f * r.nextFloat()),
            Offset(x, y),
            Offset(x + len, y),
            strokeWidth = h * 0.002f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * part = 0: tüm sahne. part = 1: yalnızca ön plan (dağlar ve su); saatin üstüne çizilir.
 */
fun DrawScope.drawWallpaper(style: Int, detail: Boolean, cache: WallCache, part: Int = 0) {
    val all = part == 0
    val w = size.width
    val h = size.height
    when (style) {
        // Gece gölü
        0 -> {
            val horizon = h * 0.70f
            val mx = w * 0.80f
            val my = h * 0.20f
            if (all) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF040817), Color(0xFF0A1A3A), Color(0xFF1C3C62)),
                        startY = 0f, endY = horizon
                    )
                )
                glow(Color(0x66BFD8FF), mx, my, w * 0.28f)
                if (detail) stars(cache, 0.60f)
                drawCircle(Color(0xFFEFF5FF), radius = w * 0.022f, center = Offset(mx, my))
            }
            val colors = listOf(Color(0xFF16305A), Color(0xFF0D1F3D), Color(0xFF071224))
            cache.ridges.forEachIndexed { i, p -> drawPath(p, colors[i % colors.size]) }
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C1D3A), Color(0xFF030814)),
                    startY = horizon, endY = h
                ),
                topLeft = Offset(0f, horizon),
                size = Size(w, h - horizon)
            )
            if (detail) {
                reflection(Color(0xFFD6E6FF), mx, horizon)
                ripples(Color(0xFFB8D0FF), horizon)
            }
        }
        // Aurora
        1 -> {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF02060F), Color(0xFF061827), Color(0xFF0A2A33))
                )
            )
            val p1 = Offset(w * 0.35f, h * 0.30f)
            rotate(-14f, p1) {
                scale(1f, 0.32f, p1) { glow(Color(0x8834E8A0), p1.x, p1.y, w * 0.55f) }
            }
            val p2 = Offset(w * 0.70f, h * 0.24f)
            rotate(-8f, p2) {
                scale(1f, 0.28f, p2) { glow(Color(0x7720C4D8), p2.x, p2.y, w * 0.45f) }
            }
            val p3 = Offset(w * 0.55f, h * 0.44f)
            rotate(-18f, p3) {
                scale(1f, 0.25f, p3) { glow(Color(0x668A5CFF), p3.x, p3.y, w * 0.50f) }
            }
            if (detail) stars(cache, 0.62f)
            val colors = listOf(Color(0xFF0A1E2A), Color(0xFF06131C), Color(0xFF02090E))
            cache.ridges.forEachIndexed { i, p -> drawPath(p, colors[i % colors.size]) }
        }
        // Gün batımı
        2 -> {
            val horizon = h * 0.66f
            val sx = w * 0.5f
            val sy = h * 0.58f
            if (all) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1036), Color(0xFF6A2A5F), Color(0xFFE0664B), Color(0xFFF6B56B)),
                        startY = 0f, endY = horizon
                    )
                )
                glow(Color(0x99FFD08A), sx, sy, w * 0.30f)
                drawCircle(Color(0xFFFFE9B8), radius = w * 0.045f, center = Offset(sx, sy))
            }
            val colors = listOf(Color(0xFF5A2456), Color(0xFF34163F), Color(0xFF190B26))
            cache.ridges.forEachIndexed { i, p -> drawPath(p, colors[i % colors.size]) }
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF8A3B52), Color(0xFF160A24)),
                    startY = horizon, endY = h
                ),
                topLeft = Offset(0f, horizon),
                size = Size(w, h - horizon)
            )
            if (detail) {
                reflection(Color(0xFFFFE0A6), sx, horizon)
                ripples(Color(0xFFFFC7A0), horizon)
            }
        }
        // Derin mavi
        else -> {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF050915), Color(0xFF0B1836), Color(0xFF060A18))
                )
            )
            glow(Color(0x773A7BFF), w * 0.20f, h * 0.25f, w * 0.50f)
            glow(Color(0x667A4DFF), w * 0.85f, h * 0.45f, w * 0.45f)
            glow(Color(0x5520C4B0), w * 0.50f, h * 1.00f, w * 0.50f)
            glow(Color(0x44FF5FA2), w * 0.95f, h * 0.05f, w * 0.30f)
        }
    }
}

/** Küçük çözünürlükte çizilmiş kopya: büyütülünce buzlu cam için bulanık zemin olur. */
fun renderBackdrop(style: Int, custom: ImageBitmap?): ImageBitmap {
    val w = 96
    val h = 54
    if (style < 0 && custom != null) {
        return Bitmap.createScaledBitmap(custom.asAndroidBitmap(), w, h, true).asImageBitmap()
    }
    val img = ImageBitmap(w, h)
    val cache = buildWallCache(style, w.toFloat(), h.toFloat())
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        GCanvas(img),
        Size(w.toFloat(), h.toFloat())
    ) {
        drawWallpaper(style, false, cache, 0)
    }
    return img
}

@Composable
fun Wallpaper(style: Int, custom: ImageBitmap?) {
    if (style < 0 && custom != null) {
        Image(
            bitmap = custom,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        key(style) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val cache = buildWallCache(style, size.width, size.height)
                        onDrawBehind { drawWallpaper(style, true, cache, 0) }
                    }
            )
        }
    }
}

/** Saatin ÜSTÜNE çizilen ön plan: fotoğrafta kayalar, hazır manzaralarda dağlar ve su. */
@Composable
fun WallpaperForeground(style: Int, customFg: ImageBitmap?) {
    if (style < 0) {
        if (customFg != null) {
            Image(
                bitmap = customFg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else if (hasProceduralDepth(style)) {
        key(style) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val cache = buildWallCache(style, size.width, size.height)
                        onDrawBehind { drawWallpaper(style, true, cache, 1) }
                    }
            )
        }
    }
}
