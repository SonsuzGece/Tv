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
    INFO, TRASH, MOVE, PIN, RESTART, TV, EYE_OFF, CHECK, ETHERNET
}

@Composable
fun GlyphIcon(glyph: Glyph, size: Dp, color: Color) {
    Canvas(Modifier.size(size)) { drawGlyph(glyph, color) }
}

fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val s = size.minDimension
    val sw = s * 0.09f
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
        Glyph.GRID -> {
            val cell = 0.19f
            val starts = floatArrayOf(0.14f, 0.405f, 0.67f)
            val cr = CornerRadius(s * 0.055f, s * 0.055f)
            for (x in starts) for (y in starts) {
                drawRoundRect(c, topLeft = p(x, y), size = Size(s * cell, s * cell), cornerRadius = cr)
            }
        }
        Glyph.SLIDERS -> {
            val ys = floatArrayOf(0.24f, 0.50f, 0.76f)
            val xs = floatArrayOf(0.33f, 0.67f, 0.42f)
            val kr = 0.105f
            val gap = kr + 0.045f
            for (k in 0..2) {
                val y = ys[k]
                val x = xs[k]
                if (x - gap > 0.12f) seg(0.12f, y, x - gap, y)
                if (x + gap < 0.88f) seg(x + gap, y, 0.88f, y)
                drawCircle(c, radius = s * kr, center = p(x, y), style = line)
            }
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
        Glyph.WIFI -> {
            arc(0.5f, 0.78f, 0.54f, 228f, 84f)
            arc(0.5f, 0.78f, 0.36f, 228f, 84f)
            arc(0.5f, 0.78f, 0.19f, 228f, 84f)
            drawCircle(c, radius = s * 0.068f, center = p(0.5f, 0.78f))
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
        Glyph.GEAR -> {
            val gearStroke = Stroke(width = s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawCircle(c, radius = s * 0.24f, center = p(0.5f, 0.5f), style = gearStroke)
            for (k in 0 until 8) {
                val a = (k * 45.0) * Math.PI / 180.0
                val cx = cos(a).toFloat()
                val sy = sin(a).toFloat()
                drawLine(
                    c,
                    p(0.5f + 0.27f * cx, 0.5f + 0.27f * sy),
                    p(0.5f + 0.40f * cx, 0.5f + 0.40f * sy),
                    strokeWidth = s * 0.135f,
                    cap = StrokeCap.Round
                )
            }
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
            frame(0.22f, 0.14f, 0.56f, 0.46f, 0.08f)
            seg(0.5f, 0.60f, 0.5f, 0.88f)
            seg(0.38f, 0.28f, 0.38f, 0.40f)
            seg(0.5f, 0.28f, 0.5f, 0.40f)
            seg(0.62f, 0.28f, 0.62f, 0.40f)
        }
    }
}
