package app.sweettuner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import app.sweettuner.core.Music
import app.sweettuner.core.Sweetening
import app.sweettuner.core.TunerReading
import app.sweettuner.core.Tuning
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * The whole tuner screen, drawn by hand so the app needs no UI libraries.
 * Top: tuning + sweetening. Centre: big note and string number, cents meter.
 * Bottom: detected / target frequency and the six strings (tap to lock one).
 */
class TunerView(context: Context) : View(context) {

    var onMenu: () -> Unit = {}
    var onLockChanged: (Int?) -> Unit = {}
    var onMessageTap: () -> Unit = {}

    private var tuning: Tuning? = null
    private var sweetening: Sweetening? = null
    private var offsets = DoubleArray(6)
    private var a4 = 440

    private var reading: TunerReading? = null
    private var lastReadingAt = 0L
    private var shownCents = 0.0
    private var locked: Int? = null
    private var message: String? = null

    // ------------------------------------------------------------------ public API

    fun setConfig(t: Tuning, s: Sweetening, a4Hz: Int) {
        tuning = t; sweetening = s; a4 = a4Hz
        offsets = s.offsets(t)
        if (locked != null && locked!! >= t.stringCount) locked = null
        reading = null
        invalidate()
    }

    fun setReading(r: TunerReading?) {
        if (r != null) {
            if (reading?.stringIndex != r.stringIndex || isIdle()) shownCents = r.cents
            reading = r
            lastReadingAt = SystemClock.uptimeMillis()
        }
        invalidate()
    }

    fun setMessage(m: String?) {
        message = m; invalidate()
    }

    val lockedString: Int? get() = locked

    // ------------------------------------------------------------------ paints & sizes

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val bg = 0xFF0D0F12.toInt()
    private val fg = 0xFFF2F2F2.toInt()
    private val dim = 0xFF6B7280.toInt()
    private val faint = 0xFF2A2F37.toInt()
    private val accent = 0xFFE8B04B.toInt()
    private val good = 0xFF34D399.toInt()
    private val near = 0xFFFBBF24.toInt()
    private val bad = 0xFFF87171.toInt()

    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val sansLight = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val fadeOut = Runnable { invalidate() }
    private val menuRect = RectF()
    private val chipRects = mutableListOf<RectF>()

    private fun isIdle() = SystemClock.uptimeMillis() - lastReadingAt > 1500

    private fun colorFor(c: Double) = when {
        abs(c) <= 2.0 -> good
        abs(c) <= 10.0 -> near
        else -> bad
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        val t = tuning ?: return
        val s = sweetening ?: return

        val left = paddingLeft + dp(20f)
        val right = width - paddingRight - dp(20f)
        val top = paddingTop + dp(16f)
        val bottom = height - paddingBottom - dp(16f)
        val cx = (left + right) / 2f
        val r = reading
        val idle = r == null || isIdle()

        // Smoothly move the needle towards the latest value.
        if (r != null && !idle) {
            val delta = r.cents - shownCents
            shownCents += delta * 0.35
            if (abs(delta) > 0.05) postInvalidateOnAnimation()
        }
        if (!idle) { // repaint later so the display dims when the note stops
            removeCallbacks(fadeOut)
            postDelayed(fadeOut, 1600)
        }

        drawHeader(canvas, t, s, left, right, top)

        val m = message
        if (m != null) {
            drawCentred(canvas, m, cx, height * 0.45f, sp(17f), fg, sans, right - left)
            drawCentred(canvas, "Tap to continue", cx, height * 0.45f + sp(34f), sp(13f), accent, sansMedium, right - left)
            drawStrings(canvas, t, null, left, right, bottom)
            return
        }

        // ---- big note + string number
        val noteCy = top + (bottom - top) * 0.36f
        val noteSize = min((right - left) * 0.42f, height * 0.2f)
        if (r != null) {
            val col = if (idle) faint else colorFor(r.cents)
            drawNote(canvas, t, r.stringIndex, cx, noteCy, noteSize, col)
            drawCentred(
                canvas, "STRING ${t.stringNumber(r.stringIndex)}", cx, noteCy + noteSize * 0.42f + sp(26f),
                sp(16f), if (idle) faint else dim, sansMedium, right - left, spacing = 0.18f,
            )
        } else {
            drawCentred(canvas, "–", cx, noteCy + noteSize * 0.35f, noteSize, faint, sansLight, right - left)
            drawCentred(
                canvas, "PLAY AN OPEN STRING", cx, noteCy + noteSize * 0.42f + sp(26f),
                sp(14f), dim, sansMedium, right - left, spacing = 0.18f,
            )
        }

        // ---- cents meter
        val meterY = top + (bottom - top) * 0.62f
        drawMeter(canvas, left + dp(8f), right - dp(8f), meterY, if (idle) null else shownCents)

        // ---- frequency block
        val freqY = meterY + dp(82f)
        if (r != null) {
            val col = if (idle) faint else fg
            drawCentred(canvas, String.format(Locale.US, "%.2f Hz", r.detectedHz), cx, freqY, sp(30f), col, mono, right - left)
            val target = String.format(Locale.US, "target %.2f Hz", r.targetHz) +
                (if (r.sweetOffset != 0.0) "  ·  sweet ${Music.formatCents(r.sweetOffset)} ¢" else "")
            drawCentred(canvas, target, cx, freqY + sp(26f), sp(14f), if (idle) faint else dim, mono, right - left)
        } else {
            drawCentred(canvas, "A4 = $a4 Hz", cx, freqY, sp(16f), dim, mono, right - left)
        }

        drawStrings(canvas, t, if (idle) null else r?.stringIndex, left, right, bottom)
    }

    private fun drawHeader(c: Canvas, t: Tuning, s: Sweetening, left: Float, right: Float, top: Float) {
        // Menu button (three bars) on the right.
        val mSize = dp(22f)
        val mx = right - mSize
        val my = top + dp(6f)
        stroke.color = fg; stroke.strokeWidth = dp(2.2f)
        for (i in 0..2) {
            val y = my + i * dp(7f)
            c.drawLine(mx, y, mx + mSize, y, stroke)
        }
        menuRect.set(mx - dp(18f), top - dp(12f), right + dp(16f), top + dp(40f))

        val maxW = mx - dp(16f) - left
        text.typeface = sansMedium; text.textSize = sp(19f); text.color = fg; text.letterSpacing = 0f
        c.drawText(ellipsize(t.name, maxW), left, top + sp(18f), text)

        text.typeface = sansMedium; text.textSize = sp(12.5f); text.color = accent; text.letterSpacing = 0.12f
        c.drawText(ellipsize("SWEETENING · ${s.shortName.uppercase(Locale.US)}", maxW), left, top + sp(40f), text)

        if (!s.isApplicable(t)) {
            text.typeface = sans; text.textSize = sp(12f); text.color = dim; text.letterSpacing = 0f
            c.drawText(ellipsize("no effect on this tuning", maxW), left, top + sp(58f), text)
        }
        text.letterSpacing = 0f
    }

    private fun drawNote(c: Canvas, t: Tuning, idx: Int, cx: Float, cy: Float, size: Float, color: Int) {
        val name = t.noteName(idx)
        val letter = name.substring(0, 1)
        val accidental = name.substring(1)
        val octave = Music.octave(t.midi[idx]).toString()

        text.typeface = sansLight; text.color = color; text.letterSpacing = 0f
        text.textSize = size
        val wLetter = text.measureText(letter)
        text.textSize = size * 0.45f
        val wAcc = if (accidental.isEmpty()) 0f else text.measureText(accidental)
        text.textSize = size * 0.26f
        val wOct = text.measureText(octave)
        val wSide = maxOf(wAcc, wOct)
        val x0 = cx - (wLetter + wSide) / 2f
        val baseline = cy + size * 0.35f

        text.textSize = size
        c.drawText(letter, x0, baseline, text)
        if (accidental.isNotEmpty()) {
            text.textSize = size * 0.45f
            c.drawText(accidental, x0 + wLetter + size * 0.02f, baseline - size * 0.38f, text)
        }
        text.textSize = size * 0.26f
        text.color = if (color == faint) faint else dim
        c.drawText(octave, x0 + wLetter + size * 0.04f, baseline, text)
    }

    private fun drawMeter(c: Canvas, x0: Float, x1: Float, y: Float, cents: Double?) {
        val cx = (x0 + x1) / 2f
        val half = (x1 - x0) / 2f

        // in-tune zone (±2 ¢)
        fill.color = (good and 0x00FFFFFF) or 0x22000000
        val zone = half * 2f / 50f
        c.drawRoundRect(cx - zone, y - dp(26f), cx + zone, y + dp(26f), dp(3f), dp(3f), fill)

        for (k in -50..50 step 5) {
            val x = cx + half * k / 50f
            val major = k % 25 == 0
            stroke.color = if (k == 0) fg else if (major) dim else faint
            stroke.strokeWidth = if (k == 0) dp(2.5f) else dp(1.5f)
            val len = if (k == 0) dp(22f) else if (major) dp(16f) else dp(9f)
            c.drawLine(x, y - len, x, y + len, stroke)
        }
        text.typeface = mono; text.textSize = sp(11f); text.color = dim; text.letterSpacing = 0f
        text.textAlign = Paint.Align.CENTER
        c.drawText("−50", x0, y + dp(40f), text)
        c.drawText("+50", x1, y + dp(40f), text)
        c.drawText("cents", cx, y + dp(40f), text)
        text.textAlign = Paint.Align.LEFT

        if (cents != null) {
            val clamped = cents.coerceIn(-50.0, 50.0)
            val x = cx + half * (clamped / 50.0).toFloat()
            val col = colorFor(cents)
            stroke.color = col; stroke.strokeWidth = dp(5f)
            c.drawLine(x, y - dp(30f), x, y + dp(30f), stroke)
            fill.color = col
            c.drawCircle(x, y - dp(30f), dp(5f), fill)
            drawCentred(
                c, "${Music.formatCents(cents)} ¢", cx, y - dp(44f), sp(18f), col, mono, half * 2,
            )
        }
    }

    private fun drawStrings(c: Canvas, t: Tuning, active: Int?, left: Float, right: Float, bottom: Float) {
        val n = t.stringCount
        val radius = min(dp(22f), (right - left) / n / 2.4f)
        val cy = bottom - dp(22f) - radius
        val slot = (right - left) / n
        chipRects.clear()

        val hint = if (locked == null) "AUTO · tap a string to lock it" else "LOCKED · tap it again for auto"
        drawCentred(c, hint, (left + right) / 2f, cy - radius - dp(16f), sp(11.5f), dim, sans, right - left, spacing = 0.06f)

        for (i in 0 until n) {
            val x = left + slot * (i + 0.5f)
            val isActive = active == i
            val isLocked = locked == i
            if (isActive) {
                fill.color = colorFor(reading?.cents ?: 0.0)
                c.drawCircle(x, cy, radius, fill)
            } else {
                stroke.color = faint; stroke.strokeWidth = dp(1.5f)
                c.drawCircle(x, cy, radius, stroke)
            }
            if (isLocked) {
                stroke.color = accent; stroke.strokeWidth = dp(2.5f)
                c.drawCircle(x, cy, radius + dp(4f), stroke)
            }
            text.typeface = sansMedium; text.textSize = radius * 0.8f; text.letterSpacing = 0f
            text.color = if (isActive) bg else fg
            text.textAlign = Paint.Align.CENTER
            c.drawText(t.noteName(i), x, cy + radius * 0.28f, text)

            // per-string offset under the chip
            val off = offsets.getOrElse(i) { 0.0 }
            text.typeface = mono; text.textSize = sp(10.5f)
            text.color = if (off != 0.0) accent else faint
            c.drawText(if (off == 0.0) "0" else Music.formatCents(off), x, cy + radius + sp(14f), text)
            text.textAlign = Paint.Align.LEFT

            chipRects += RectF(x - slot / 2f, cy - radius - dp(10f), x + slot / 2f, cy + radius + dp(20f))
        }
    }

    private fun drawCentred(
        c: Canvas, s: String, cx: Float, baseline: Float, size: Float, color: Int,
        face: Typeface, maxWidth: Float, spacing: Float = 0f,
    ) {
        text.typeface = face; text.textSize = size; text.color = color; text.letterSpacing = spacing
        text.textAlign = Paint.Align.CENTER
        c.drawText(ellipsize(s, maxWidth), cx, baseline, text)
        text.textAlign = Paint.Align.LEFT
        text.letterSpacing = 0f
    }

    private fun ellipsize(s: String, maxWidth: Float): String {
        if (text.measureText(s) <= maxWidth) return s
        var end = s.length
        while (end > 1 && text.measureText(s.substring(0, end) + "…") > maxWidth) end--
        return s.substring(0, end) + "…"
    }

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) return true
        if (event.actionMasked != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
        val x = event.x
        val y = event.y
        if (menuRect.contains(x, y)) {
            performClick(); onMenu(); return true
        }
        chipRects.forEachIndexed { i, rect ->
            if (rect.contains(x, y)) {
                locked = if (locked == i) null else i
                onLockChanged(locked)
                invalidate()
                return true
            }
        }
        if (message != null) {
            onMessageTap(); return true
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
