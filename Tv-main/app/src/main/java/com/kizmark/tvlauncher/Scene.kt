package com.kizmark.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as GCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * HIZIN SIRRI: duvar kağıdı + saat + ön plan derinliği + dock camı tek bir resme "pişirilir".
 * 4K TV'lerde bile pişirme çözünürlüğü 1920x1080 ile sınırlandırılır; GPU donanımsal ölçekleme
 * yapar, böylece bellek kullanımı %75 azalır ve geçişler kasmadan 60fps akar.
 */
object Dims {
    const val TILE_W = 136f
    const val TILE_H = 77f
    const val GAP = 12f
    const val PAD = 12f
    val STRIP_W = Config.DOCK_VISIBLE * TILE_W + (Config.DOCK_VISIBLE - 1) * GAP
    val DOCK_W = STRIP_W + 2 * PAD
    val DOCK_H = TILE_H + 2 * PAD
    const val DOCK_BOTTOM = 42f
    const val DOCK_RADIUS = 30f
    const val CLOCK_BASELINE = 0.50f
    const val MAX_BAKE_W = 1920
    const val MAX_BAKE_H = 1080
}

enum class SceneKind { HOME, CONTROL, BARE }

class SceneSet(
    val home: ImageBitmap,
    val glass: ImageBitmap,
    val accent: Color,
    val minute: Long,
    val key: String
)

class SceneBaker(private val ctx: Context) {
    private val tr = Locale("tr", "TR")
    private var srcKey = ""
    private var base: Bitmap? = null
    private var fg: Bitmap? = null
    private var glass: Bitmap? = null
    private var accent: Color = Color(0xFF34D399)
    private var currentItem: WallpaperItem = WALLPAPER_CATALOG.first()

    fun availableWallpapers(): List<WallpaperItem> = WALLPAPER_CATALOG

    fun wallpaperNames(): List<String> = WALLPAPER_CATALOG.map { it.name }

    /** Uygulama açıkken bellek boşaltılır; sadece son pişmiş ekran resmi kalır. */
    fun release() {
        base = null
        fg = null
        glass = null
        srcKey = ""
    }

    private fun ensureSources(bw: Int, bh: Int, wall: Int, depth: Boolean) {
        val key = "$bw:$bh:$wall:$depth"
        if (key == srcKey && base != null) return
        val items = WALLPAPER_CATALOG
        val idx = ((wall % items.size) + items.size) % items.size
        val item = items[idx]
        currentItem = item
        val b: Bitmap
        var f: Bitmap? = null
        if (item.drawableRes != 0) {
            b = decodeCrop(item.drawableRes, bw, bh, false) ?: renderProcedural(0, bw, bh, 0)
            if (depth && item.fgDrawableRes != 0) {
                f = decodeCrop(item.fgDrawableRes, bw, bh, true)
            }
            accent = accentFromBitmap(b)
        } else {
            val style = item.proceduralStyle.coerceAtLeast(0)
            b = renderProcedural(style, bw, bh, 0)
            if (depth && hasProceduralDepth(style)) f = renderProcedural(style, bw, bh, 1)
            accent = WALLPAPER_ACCENTS.getOrElse(style) { Color(0xFF34D399) }
        }
        base = b
        fg = f
        glass = makeGlass(b, f, bw, bh)
        srcKey = key
    }

    private fun decodeCrop(@DrawableRes resId: Int, w: Int, h: Int, alpha: Boolean): Bitmap? {
        return try {
            if (resId == 0) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(ctx.resources, resId, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeResource(ctx.resources, resId, opts) ?: return null
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val scale = maxOf(w / raw.width.toFloat(), h / raw.height.toFloat())
            val dw = raw.width * scale
            val dh = raw.height * scale
            val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            c.drawBitmap(raw, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
            raw.recycle()
            if (!alpha) out.setHasAlpha(false)
            out
        } catch (e: Throwable) {
            null
        }
    }

    private fun renderProcedural(style: Int, w: Int, h: Int, part: Int): Bitmap {
        val img = ImageBitmap(w, h)
        val cache = buildWallCache(style, w.toFloat(), h.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GCanvas(img), Size(w.toFloat(), h.toFloat())) {
            drawWallpaper(style, true, cache, part)
        }
        return img.asAndroidBitmap()
    }

    /** Cam yüzeyler için: küçük çözünürlüklü (en fazla 144x81) hızlı bulanık kopya. */
    private fun makeGlass(b: Bitmap, f: Bitmap?, w: Int, h: Int): Bitmap {
        val ratio = minOf(144f / w.coerceAtLeast(1), 81f / h.coerceAtLeast(1), 1f / 8f)
        val sw = (w * ratio).roundToInt().coerceAtLeast(16)
        val sh = (h * ratio).roundToInt().coerceAtLeast(9)
        val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val c = Canvas(small)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        val dst = Rect(0, 0, sw, sh)
        c.drawBitmap(b, null, dst, p)
        if (f != null) c.drawBitmap(f, null, dst, p)
        boxBlur(small, 3)
        boxBlur(small, 2)
        return small
    }

    private fun boxBlur(bmp: Bitmap, r: Int) {
        val w = bmp.width
        val h = bmp.height
        val src = IntArray(w * h)
        val tmp = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        val div = 2 * r + 1
        for (pass in 0..1) {
            val inp = if (pass == 0) src else tmp
            val out = if (pass == 0) tmp else src
            val lenA = if (pass == 0) h else w
            val lenB = if (pass == 0) w else h
            for (a in 0 until lenA) {
                var sr = 0
                var sg = 0
                var sb = 0
                for (k in -r..r) {
                    val px = inp[idx(pass, a, k.coerceIn(0, lenB - 1), w)]
                    sr += (px shr 16) and 0xFF
                    sg += (px shr 8) and 0xFF
                    sb += px and 0xFF
                }
                for (bb in 0 until lenB) {
                    out[idx(pass, a, bb, w)] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                    val addI = (bb + r + 1).coerceAtMost(lenB - 1)
                    val subI = (bb - r).coerceAtLeast(0)
                    val pa = inp[idx(pass, a, addI, w)]
                    val ps = inp[idx(pass, a, subI, w)]
                    sr += ((pa shr 16) and 0xFF) - ((ps shr 16) and 0xFF)
                    sg += ((pa shr 8) and 0xFF) - ((ps shr 8) and 0xFF)
                    sb += (pa and 0xFF) - (ps and 0xFF)
                }
            }
        }
        bmp.setPixels(src, 0, w, 0, 0, w, h)
    }

    private fun idx(pass: Int, a: Int, b: Int, w: Int) = if (pass == 0) a * w + b else b * w + a

    /** Tüm sahneyi pişirir. Arka plan iş parçacığında çağrılmalı. */
    fun bake(kind: SceneKind, w: Int, h: Int, density: Float, wall: Int, bold: Boolean, depth: Boolean): SceneSet {
        val scale = minOf(1f, Dims.MAX_BAKE_W / w.toFloat(), Dims.MAX_BAKE_H / h.toFloat())
        val bw = (w * scale).roundToInt().coerceAtLeast(16)
        val bh = (h * scale).roundToInt().coerceAtLeast(9)
        val bd = density * scale

        ensureSources(bw, bh, wall, depth)
        val b = base!!
        val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(b, 0f, 0f, null)
        val f = fg
        if (f != null) drawHorizonGlow(c, bw, bh)
        drawClock(c, bw, bh, bd, bold, kind == SceneKind.CONTROL, currentItem)
        if (f != null) c.drawBitmap(f, 0f, 0f, null)
        drawScrims(c, bw, bh)
        if (kind == SceneKind.HOME) drawDockGlass(c, bw, bh, bd)
        out.setHasAlpha(false)
        out.prepareToDraw()
        val g = glass!!
        return SceneSet(
            out.asImageBitmap(), g.asImageBitmap(), accent,
            System.currentTimeMillis() / 60000L, "$w:$h:$wall:$bold:$depth:$kind"
        )
    }

    private fun drawHorizonGlow(c: Canvas, w: Int, h: Int) {
        val cx = w * 0.5f
        val cy = h * 0.56f
        val r = w * 0.34f
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(accent.copy(alpha = 0.16f).toArgb(), accent.copy(alpha = 0.06f).toArgb(), 0),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.save()
        c.scale(1f, 0.38f, cx, cy)
        c.drawCircle(cx, cy, r, p)
        c.restore()
    }

    private fun weight(wt: Int, fallbackBold: Boolean): Typeface {
        return if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(Typeface.DEFAULT, wt, false)
        } else {
            Typeface.create(Typeface.DEFAULT, if (fallbackBold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun drawClock(c: Canvas, w: Int, h: Int, d: Float, bold: Boolean, compact: Boolean, item: WallpaperItem) {
        val now = Date()
        val time = SimpleDateFormat("HH:mm", tr).format(now)
        val date = SimpleDateFormat("EEEE, d MMMM", tr).format(now)
        val size = w * (if (bold) 0.20f else 0.17f)
        val baseline = if (compact) h * Dims.CLOCK_BASELINE else h * item.clockY
        val cx = if (compact) w * 0.50f else w * item.clockX

        c.save()
        if (compact) {
            c.translate(-0.23f * w, 0.04f * h)
            c.scale(0.8f, 0.8f, cx, baseline)
        }

        val tp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = size
        tp.fontFeatureSettings = "tnum"
        if (bold) {
            tp.typeface = weight(800, true)
            tp.letterSpacing = -0.035f
            val fm = tp.fontMetrics
            val top = baseline + fm.ascent
            val bottom = baseline + fm.descent
            tp.shader = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(
                    Color.White.toArgb(), Color.White.toArgb(),
                    lerp(Color(0xFFE6EEF0), accent, 0.10f).toArgb(),
                    lerp(Color(0xFFA4B2B6), accent, 0.26f).toArgb()
                ),
                floatArrayOf(0f, 0.20f, 0.60f, 0.84f), Shader.TileMode.CLAMP
            )
        } else {
            tp.typeface = weight(100, false)
            tp.letterSpacing = -0.02f
            tp.color = 0xF2FFFFFF.toInt()
        }
        tp.setShadowLayer(17f * d, 0f, 7f * d, 0xD9000000.toInt())
        c.drawText(time, cx, baseline, tp)

        val dp = Paint(Paint.ANTI_ALIAS_FLAG)
        dp.textAlign = Paint.Align.CENTER
        dp.textSize = w * 0.0195f
        dp.typeface = weight(600, true)
        dp.color = 0xF2EEF8F5.toInt()
        dp.setShadowLayer(7f * d, 0f, 1f * d, 0xE6000000.toInt())
        val digitTop = baseline - size * 0.72f
        c.drawText(date, cx, digitTop - size * 0.09f, dp)
        c.restore()
    }

    private fun drawScrims(c: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.DITHER_FLAG)
        val topH = h * 0.24f
        p.shader = LinearGradient(0f, 0f, 0f, topH, 0x59000000, 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), topH, p)
        val y = h * 0.58f
        p.shader = LinearGradient(0f, y, 0f, h.toFloat(), 0, 0x8C000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, y, w.toFloat(), h.toFloat(), p)
    }

    /** Dock'un buzlu cam zemini: yazılım gölge filtresi yerine hızlı çok katmanlı gölge kullanır. */
    private fun drawDockGlass(c: Canvas, w: Int, h: Int, d: Float) {
        val g = glass ?: return
        val dw = Dims.DOCK_W * d
        val dh = Dims.DOCK_H * d
        val left = (w - dw) / 2f
        val top = h - Dims.DOCK_BOTTOM * d - dh
        val rect = RectF(left, top, left + dw, top + dh)
        val r = Dims.DOCK_RADIUS * d

        // Hızlı yumuşak gölge (CPU blur olmadan)
        val sh = Paint(Paint.ANTI_ALIAS_FLAG)
        val offsets = floatArrayOf(12f, 7f, 3f)
        val alphas = intArrayOf(0x18000000, 0x24000000, 0x30000000)
        for (i in offsets.indices) {
            val pad = offsets[i] * d
            sh.color = alphas[i]
            c.drawRoundRect(
                RectF(rect.left - pad, rect.top + pad * 0.35f, rect.right + pad, rect.bottom + pad * 1.15f),
                r + pad * 0.5f, r + pad * 0.5f, sh
            )
        }

        val path = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        c.save()
        c.clipPath(path)
        val sx = g.width / w.toFloat()
        val sy = g.height / h.toFloat()
        val src = Rect(
            (rect.left * sx).toInt(), (rect.top * sy).toInt(),
            (rect.right * sx).toInt().coerceAtMost(g.width), (rect.bottom * sy).toInt().coerceAtMost(g.height)
        )
        c.drawBitmap(g, src, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        val tint = Paint()
        tint.color = lerp(Color(0x24FFFFFF), accent.copy(alpha = 0.16f), 0.45f).toArgb()
        c.drawRect(rect, tint)
        val sheen = Paint(Paint.DITHER_FLAG)
        sheen.shader = LinearGradient(0f, rect.top, 0f, rect.top + dh * 0.55f, 0x2EFFFFFF, 0, Shader.TileMode.CLAMP)
        c.drawRect(rect, sheen)
        c.restore()

        val border = Paint(Paint.ANTI_ALIAS_FLAG)
        border.style = Paint.Style.STROKE
        border.strokeWidth = 1.2f * d
        border.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            0x99FFFFFF.toInt(), 0x1FFFFFFF, Shader.TileMode.CLAMP
        )
        val inset = border.strokeWidth / 2f
        c.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), r, r, border)
    }
}

/** Fotoğraftaki baskın canlı rengi bulur; arayüzün vurgu rengi olur. */
fun accentFromBitmap(image: Bitmap): Color {
    var small: Bitmap? = null
    return try {
        small = Bitmap.createScaledBitmap(image, 48, 27, true)
        val hsv = FloatArray(3)
        var sx = 0.0
        var sy = 0.0
        var total = 0.0
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                android.graphics.Color.colorToHSV(small.getPixel(x, y), hsv)
                val weight = (hsv[1] * hsv[1] * hsv[2]).toDouble()
                val angle = Math.toRadians(hsv[0].toDouble())
                sx += Math.cos(angle) * weight
                sy += Math.sin(angle) * weight
                total += weight
            }
        }
        if (total < 0.5) {
            Color(0xFF8FB4FF)
        } else {
            var hue = Math.toDegrees(Math.atan2(sy, sx)).toFloat()
            if (hue < 0f) hue += 360f
            Color.hsv(hue.coerceIn(0f, 359.9f), 0.70f, 0.84f)
        }
    } catch (e: Throwable) {
        Color(0xFF34D399)
    } finally {
        small?.recycle()
    }
}
