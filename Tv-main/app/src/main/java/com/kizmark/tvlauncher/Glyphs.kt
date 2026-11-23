package com.kizmark.tvlauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

enum class Glyph {
    GRID, SLIDERS, PLAY, PAUSE, NEXT, PREV, WIFI, SPEAKER, IMAGE, CLOCK, DISPLAY, BAG, GEAR, TYPE, MUSIC, LAYERS,
    BLUETOOTH, INPUT, MOON, ACCESS, BELL, POWER, SEARCH, CAST, MIC, HOME, BOLT, USER, LEFT, RIGHT,
    INFO, TRASH, MOVE, PIN, RESTART, TV, EYE_OFF, CHECK, ETHERNET, SHUFFLE
}

@Composable
fun GlyphIcon(glyph: Glyph, size: Dp, color: Color) {
    Canvas(Modifier.size(size)) { drawGlyph(glyph, color) }
}

fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val s = size.minDimension
    val sw = s * 0.075f
    val line = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(s * x, s * y)
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(c, p(x1, y1), p(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
    }
    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val path = Path()
        path.moveTo(s * x1, s * y1)
        path.lineTo(s * x2, s * y2)
        path.lineTo(s * x3, s * y3)
        path.close()
        drawPath(path, c)
    }
    fun bar(x: Float, y: Float, w: Float, h: Float) {
        drawRoundRect(c, topLeft = p(x, y), size = Size(s * w, s * h), cornerRadius = CornerRadius(s * 0.04f, s * 0.04f))
    }
    fun frame(x: Float, y: Float, w: Float, h: Float, r: Float) {
        drawRoundRect(
            c, topLeft = p(x, y), size = Size(s * w, s * h),
            cornerRadius = CornerRadius(s * r, s * r), style = line
        )
    }
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) {
        drawArc(
            c, startAngle = start, sweepAngle = sweep, useCenter = false,
            topLeft = p(cx - r, cy - r), size = Size(s * r * 2f, s * r * 2f), style = line
        )
    }

    when (g) {
        // Modern 2x2 yuvarlatılmış uygulama kutucukları
        Glyph.GRID -> {
            frame(0.14f, 0.14f, 0.30f, 0.30f, 0.08f)
            frame(0.56f, 0.14f, 0.30f, 0.30f, 0.08f)
            frame(0.14f, 0.56f, 0.30f, 0.30f, 0.08f)
            frame(0.56f, 0.56f, 0.30f, 0.30f, 0.08f)
        }
        // Modern çift anahtarlı Kontrol Merkezi ikonu
        Glyph.SLIDERS -> {
            frame(0.12f, 0.17f, 0.76f, 0.26f, 0.13f)
            drawCircle(c, radius = s * 0.078f, center = p(0.73f, 0.30f))
            frame(0.12f, 0.57f, 0.76f, 0.26f, 0.13f)
            drawCircle(c, radius = s * 0.078f, center = p(0.27f, 0.70f))
        }
        Glyph.PLAY -> tri(0.30f, 0.18f, 0.30f, 0.82f, 0.84f, 0.50f)
        Glyph.PAUSE -> {
            bar(0.26f, 0.20f, 0.16f, 0.60f)
            bar(0.58f, 0.20f, 0.16f, 0.60f)
        }
        Glyph.NEXT -> {
            tri(0.18f, 0.22f, 0.18f, 0.78f, 0.64f, 0.50f)
            bar(0.70f, 0.22f, 0.11f, 0.56f)
        }
        Glyph.PREV -> {
            tri(0.82f, 0.22f, 0.82f, 0.78f, 0.36f, 0.50f)
            bar(0.19f, 0.22f, 0.11f, 0.56f)
        }
        // Dengeli, optik olarak ortalanmış 3 kademeli Wi-Fi ikonu
        Glyph.WIFI -> {
            arc(0.5f, 0.78f, 0.54f, 228f, 84f)
            arc(0.5f, 0.78f, 0.36f, 228f, 84f)
            arc(0.5f, 0.78f, 0.18f, 228f, 84f)
            drawCircle(c, radius = s * 0.056f, center = p(0.5f, 0.78f))
        }
        Glyph.SPEAKER -> {
            val path = Path()
            path.moveTo(s * 0.12f, s * 0.40f)
            path.lineTo(s * 0.30f, s * 0.40f)
            path.lineTo(s * 0.52f, s * 0.20f)
            path.lineTo(s * 0.52f, s * 0.80f)
            path.lineTo(s * 0.30f, s * 0.60f)
            path.lineTo(s * 0.12f, s * 0.60f)
            path.close()
            drawPath(path, c)
            arc(0.52f, 0.50f, 0.18f, -45f, 90f)
            arc(0.52f, 0.50f, 0.34f, -45f, 90f)
        }
        Glyph.IMAGE -> {
            frame(0.12f, 0.18f, 0.76f, 0.64f, 0.10f)
            seg(0.22f, 0.70f, 0.40f, 0.48f)
            seg(0.40f, 0.48f, 0.54f, 0.62f)
            seg(0.54f, 0.62f, 0.64f, 0.52f)
            seg(0.64f, 0.52f, 0.78f, 0.70f)
            drawCircle(c, radius = s * 0.06f, center = p(0.68f, 0.36f))
        }
        Glyph.CLOCK -> {
            drawCircle(c, radius = s * 0.38f, center = p(0.5f, 0.5f), style = line)
            seg(0.5f, 0.5f, 0.5f, 0.28f)
            seg(0.5f, 0.5f, 0.66f, 0.58f)
        }
        Glyph.DISPLAY -> {
            frame(0.10f, 0.18f, 0.80f, 0.50f, 0.08f)
            seg(0.5f, 0.68f, 0.5f, 0.84f)
            seg(0.34f, 0.84f, 0.66f, 0.84f)
        }
        Glyph.BAG -> {
            frame(0.18f, 0.36f, 0.64f, 0.50f, 0.10f)
            arc(0.5f, 0.36f, 0.16f, 180f, 180f)
        }
        // Bütünleşik 8 dişli hassas ayarlar çarkı
        Glyph.GEAR -> {
            val path = Path()
            val teeth = 8
            val rOut = s * 0.42f
            val rIn = s * 0.31f
            val cx = s * 0.5f
            val cy = s * 0.5f
            val step = (2.0 * Math.PI) / teeth
            for (k in 0 until teeth) {
                val base = k * step - Math.PI / 2.0
                val a1 = base - step * 0.28
                val a2 = base - step * 0.15
                val a3 = base + step * 0.15
                val a4 = base + step * 0.28
                val x1 = cx + (rIn * cos(a1)).toFloat()
                val y1 = cy + (rIn * sin(a1)).toFloat()
                val x2 = cx + (rOut * cos(a2)).toFloat()
                val y2 = cy + (rOut * sin(a2)).toFloat()
                val x3 = cx + (rOut * cos(a3)).toFloat()
                val y3 = cy + (rOut * sin(a3)).toFloat()
                val x4 = cx + (rIn * cos(a4)).toFloat()
                val y4 = cy + (rIn * sin(a4)).toFloat()
                if (k == 0) path.moveTo(x1, y1) else path.lineTo(x1, y1)
                path.lineTo(x2, y2)
                path.lineTo(x3, y3)
                path.lineTo(x4, y4)
            }
            path.close()
            drawPath(path, c, style = line)
            drawCircle(c, radius = s * 0.145f, center = Offset(cx, cy), style = line)
        }
        Glyph.TYPE -> {
            seg(0.24f, 0.80f, 0.50f, 0.20f)
            seg(0.50f, 0.20f, 0.76f, 0.80f)
            seg(0.34f, 0.58f, 0.66f, 0.58f)
        }
        Glyph.LAYERS -> {
            seg(0.50f, 0.16f, 0.86f, 0.36f)
            seg(0.86f, 0.36f, 0.50f, 0.56f)
            seg(0.50f, 0.56f, 0.14f, 0.36f)
            seg(0.14f, 0.36f, 0.50f, 0.16f)
            seg(0.14f, 0.58f, 0.50f, 0.80f)
            seg(0.50f, 0.80f, 0.86f, 0.58f)
        }
        Glyph.MUSIC -> {
            drawCircle(c, radius = s * 0.13f, center = p(0.36f, 0.72f))
            seg(0.48f, 0.72f, 0.48f, 0.22f)
            seg(0.48f, 0.22f, 0.74f, 0.30f)
        }
        Glyph.BLUETOOTH -> {
            seg(0.30f, 0.32f, 0.68f, 0.66f)
            seg(0.68f, 0.66f, 0.50f, 0.84f)
            seg(0.50f, 0.84f, 0.50f, 0.16f)
            seg(0.50f, 0.16f, 0.68f, 0.34f)
            seg(0.68f, 0.34f, 0.30f, 0.68f)
        }
        Glyph.INPUT -> {
            seg(0.40f, 0.30f, 0.40f, 0.18f)
            seg(0.40f, 0.18f, 0.86f, 0.18f)
            seg(0.86f, 0.18f, 0.86f, 0.82f)
            seg(0.86f, 0.82f, 0.40f, 0.82f)
            seg(0.40f, 0.82f, 0.40f, 0.70f)
            seg(0.12f, 0.50f, 0.62f, 0.50f)
            seg(0.48f, 0.36f, 0.62f, 0.50f)
            seg(0.48f, 0.64f, 0.62f, 0.50f)
        }
        Glyph.MOON -> {
            val a = Path().apply { addOval(Rect(s * 0.16f, s * 0.16f, s * 0.84f, s * 0.84f)) }
            val b = Path().apply { addOval(Rect(s * 0.36f, s * 0.04f, s * 0.96f, s * 0.64f)) }
            val m = Path()
            m.op(a, b, PathOperation.Difference)
            drawPath(m, c)
        }
        Glyph.ACCESS -> {
            drawCircle(c, radius = s * 0.08f, center = p(0.5f, 0.17f))
            seg(0.20f, 0.36f, 0.80f, 0.36f)
            seg(0.50f, 0.36f, 0.50f, 0.60f)
            seg(0.50f, 0.60f, 0.34f, 0.86f)
            seg(0.50f, 0.60f, 0.66f, 0.86f)
        }
        Glyph.BELL -> {
            arc(0.5f, 0.46f, 0.24f, 180f, 180f)
            seg(0.26f, 0.46f, 0.22f, 0.70f)
            seg(0.74f, 0.46f, 0.78f, 0.70f)
            seg(0.14f, 0.72f, 0.86f, 0.72f)
            drawCircle(c, radius = s * 0.06f, center = p(0.5f, 0.85f))
        }
        Glyph.POWER -> {
            arc(0.5f, 0.54f, 0.31f, 305f, 290f)
            seg(0.5f, 0.12f, 0.5f, 0.48f)
        }
        Glyph.SEARCH -> {
            drawCircle(c, radius = s * 0.24f, center = p(0.43f, 0.43f), style = line)
            seg(0.61f, 0.61f, 0.84f, 0.84f)
        }
        Glyph.CAST -> {
            seg(0.14f, 0.42f, 0.14f, 0.20f)
            seg(0.14f, 0.20f, 0.86f, 0.20f)
            seg(0.86f, 0.20f, 0.86f, 0.80f)
            seg(0.86f, 0.80f, 0.58f, 0.80f)
            arc(0.14f, 0.82f, 0.20f, 270f, 90f)
            arc(0.14f, 0.82f, 0.38f, 270f, 90f)
            drawCircle(c, radius = s * 0.06f, center = p(0.17f, 0.79f))
        }
        Glyph.MIC -> {
            frame(0.38f, 0.12f, 0.24f, 0.44f, 0.12f)
            arc(0.5f, 0.46f, 0.23f, 0f, 180f)
            seg(0.5f, 0.69f, 0.5f, 0.84f)
            seg(0.34f, 0.86f, 0.66f, 0.86f)
        }
        Glyph.HOME -> {
            seg(0.12f, 0.48f, 0.5f, 0.16f)
            seg(0.5f, 0.16f, 0.88f, 0.48f)
            seg(0.24f, 0.40f, 0.24f, 0.84f)
            seg(0.76f, 0.40f, 0.76f, 0.84f)
            seg(0.24f, 0.84f, 0.76f, 0.84f)
        }
        Glyph.BOLT -> {
            val path = Path()
            path.moveTo(s * 0.58f, s * 0.08f)
            path.lineTo(s * 0.22f, s * 0.56f)
            path.lineTo(s * 0.46f, s * 0.56f)
            path.lineTo(s * 0.40f, s * 0.92f)
            path.lineTo(s * 0.78f, s * 0.42f)
            path.lineTo(s * 0.54f, s * 0.42f)
            path.close()
            drawPath(path, c)
        }
        Glyph.USER -> {
            drawCircle(c, radius = s * 0.16f, center = p(0.5f, 0.34f), style = line)
            arc(0.5f, 0.94f, 0.32f, 200f, 140f)
        }
        Glyph.LEFT -> {
            seg(0.62f, 0.18f, 0.32f, 0.50f)
            seg(0.32f, 0.50f, 0.62f, 0.82f)
        }
        Glyph.RIGHT -> {
            seg(0.38f, 0.18f, 0.68f, 0.50f)
            seg(0.68f, 0.50f, 0.38f, 0.82f)
        }
        Glyph.INFO -> {
            drawCircle(c, radius = s * 0.38f, center = p(0.5f, 0.5f), style = line)
            seg(0.5f, 0.46f, 0.5f, 0.70f)
            drawCircle(c, radius = s * 0.055f, center = p(0.5f, 0.31f))
        }
        Glyph.TRASH -> {
            frame(0.26f, 0.30f, 0.48f, 0.56f, 0.06f)
            seg(0.14f, 0.28f, 0.86f, 0.28f)
            seg(0.40f, 0.15f, 0.60f, 0.15f)
            seg(0.42f, 0.44f, 0.42f, 0.72f)
            seg(0.58f, 0.44f, 0.58f, 0.72f)
        }
        Glyph.MOVE -> {
            seg(0.10f, 0.5f, 0.90f, 0.5f)
            seg(0.10f, 0.5f, 0.27f, 0.33f)
            seg(0.10f, 0.5f, 0.27f, 0.67f)
            seg(0.90f, 0.5f, 0.73f, 0.33f)
            seg(0.90f, 0.5f, 0.73f, 0.67f)
        }
        Glyph.PIN -> {
            bar(0.12f, 0.18f, 0.09f, 0.64f)
            seg(0.32f, 0.5f, 0.88f, 0.5f)
            seg(0.32f, 0.5f, 0.50f, 0.32f)
            seg(0.32f, 0.5f, 0.50f, 0.68f)
        }
        Glyph.RESTART -> {
            arc(0.5f, 0.54f, 0.31f, 300f, 300f)
            tri(0.56f, 0.10f, 0.80f, 0.24f, 0.58f, 0.38f)
        }
        Glyph.TV -> {
            frame(0.10f, 0.18f, 0.80f, 0.54f, 0.08f)
            seg(0.5f, 0.72f, 0.5f, 0.84f)
            seg(0.32f, 0.86f, 0.68f, 0.86f)
        }
        Glyph.EYE_OFF -> {
            arc(0.5f, 0.86f, 0.46f, 232f, 76f)
            arc(0.5f, 0.14f, 0.46f, 52f, 76f)
            drawCircle(c, radius = s * 0.10f, center = p(0.5f, 0.5f))
            seg(0.18f, 0.18f, 0.82f, 0.82f)
        }
        Glyph.CHECK -> {
            seg(0.18f, 0.52f, 0.42f, 0.76f)
            seg(0.42f, 0.76f, 0.84f, 0.26f)
        }
        Glyph.ETHERNET -> {
            frame(0.18f, 0.16f, 0.64f, 0.44f, 0.10f)
            seg(0.36f, 0.27f, 0.36f, 0.39f)
            seg(0.50f, 0.27f, 0.50f, 0.39f)
            seg(0.64f, 0.27f, 0.64f, 0.39f)
            seg(0.50f, 0.60f, 0.50f, 0.82f)
            seg(0.28f, 0.82f, 0.72f, 0.82f)
        }
        Glyph.SHUFFLE -> {
            seg(0.14f, 0.30f, 0.36f, 0.30f)
            seg(0.36f, 0.30f, 0.64f, 0.70f)
            seg(0.64f, 0.70f, 0.84f, 0.70f)
            seg(0.73f, 0.59f, 0.84f, 0.70f)
            seg(0.73f, 0.81f, 0.84f, 0.70f)
            seg(0.14f, 0.70f, 0.36f, 0.70f)
            seg(0.36f, 0.70f, 0.64f, 0.30f)
            seg(0.64f, 0.30f, 0.84f, 0.30f)
            seg(0.73f, 0.19f, 0.84f, 0.30f)
            seg(0.73f, 0.41f, 0.84f, 0.30f)
        }
    }
}
