package com.kizmark.tvlauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

enum class Glyph {
    GRID, SLIDERS, PLAY, PAUSE, NEXT, PREV, WIFI, SPEAKER, IMAGE, CLOCK, DISPLAY, BAG, GEAR, TYPE, MUSIC
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
            val d = s / 3f
            for (i in 0..2) for (j in 0..2) {
                drawCircle(c, radius = d * 0.2f, center = Offset(d * (i + 0.5f), d * (j + 0.5f)))
            }
        }
        Glyph.SLIDERS -> {
            val ys = floatArrayOf(0.22f, 0.5f, 0.78f)
            val xs = floatArrayOf(0.32f, 0.68f, 0.42f)
            for (k in 0..2) {
                drawLine(
                    c.copy(alpha = c.alpha * 0.55f), p(0.08f, ys[k]), p(0.92f, ys[k]),
                    strokeWidth = sw * 0.8f, cap = StrokeCap.Round
                )
                drawCircle(c, radius = s * 0.11f, center = p(xs[k], ys[k]))
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
            arc(0.5f, 0.80f, 0.60f, 225f, 90f)
            arc(0.5f, 0.80f, 0.40f, 225f, 90f)
            arc(0.5f, 0.80f, 0.20f, 225f, 90f)
            drawCircle(c, radius = s * 0.07f, center = p(0.5f, 0.80f))
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
            drawCircle(c, radius = s * 0.20f, center = p(0.5f, 0.5f), style = line)
            for (k in 0 until 8) {
                val a = (k * 45.0) * Math.PI / 180.0
                val cx = cos(a).toFloat()
                val sy = sin(a).toFloat()
                seg(0.5f + 0.31f * cx, 0.5f + 0.31f * sy, 0.5f + 0.40f * cx, 0.5f + 0.40f * sy)
            }
        }
        Glyph.TYPE -> {
            seg(0.24f, 0.80f, 0.50f, 0.20f)
            seg(0.50f, 0.20f, 0.76f, 0.80f)
            seg(0.34f, 0.58f, 0.66f, 0.58f)
        }
        Glyph.MUSIC -> {
            drawCircle(c, radius = s * 0.13f, center = p(0.36f, 0.72f))
            seg(0.48f, 0.72f, 0.48f, 0.22f)
            seg(0.48f, 0.22f, 0.74f, 0.30f)
        }
    }
}
