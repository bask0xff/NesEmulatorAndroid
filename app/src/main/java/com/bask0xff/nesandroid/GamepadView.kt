package com.bask0xff.nesandroid

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class GamepadView(context: Context) : View(context) {

    companion object {
        const val BTN_A      = 0x01
        const val BTN_B      = 0x02
        const val BTN_SELECT = 0x04
        const val BTN_START  = 0x08
        const val BTN_UP     = 0x10
        const val BTN_DOWN   = 0x20
        const val BTN_LEFT   = 0x40
        const val BTN_RIGHT  = 0x80
    }

    data class ButtonDef(val label: String, val bit: Int, val rect: RectF = RectF())

    private val allButtons = listOf(
        ButtonDef("↑", BTN_UP),
        ButtonDef("↓", BTN_DOWN),
        ButtonDef("←", BTN_LEFT),
        ButtonDef("→", BTN_RIGHT),
        ButtonDef("B", BTN_B),
        ButtonDef("A", BTN_A),
        ButtonDef("SEL", BTN_SELECT),
        ButtonDef("STA", BTN_START)
    )

    var onStateChanged: ((UByte) -> Unit)? = null
    private var controllerState = 0
    // pointerId -> суммарная битовая маска нажатых им кнопок
    private val activePointers = mutableMapOf<Int, Int>()

    // ── Кисти ────────────────────────────────────────────────────────────────

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000"); style = Paint.Style.FILL
    }
    private val paintBgPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB00CC44"); style = Paint.Style.FILL
    }
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FFFFFF"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val paintBorderPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF00FF88"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutButtons(w.toFloat(), h.toFloat())
    }

    private fun layoutButtons(w: Float, h: Float) {
        val s    = dp(48f)
        val g    = dp(6f)
        val m    = dp(16f)
        val lift = dp(120f)   // ← поднимаем на это значение; меняй по вкусу

        // D-pad
        val dcx = m + s + g
        val dcy = h - m - s - g - lift
        btn(BTN_UP).rect.set   (dcx - s/2,   dcy - s - g, dcx + s/2,   dcy - g    )
        btn(BTN_DOWN).rect.set (dcx - s/2,   dcy + g,     dcx + s/2,   dcy + s + g)
        btn(BTN_LEFT).rect.set (dcx - s - g, dcy - s/2,   dcx - g,     dcy + s/2  )
        btn(BTN_RIGHT).rect.set(dcx + g,     dcy - s/2,   dcx + s + g, dcy + s/2  )

        // A / B
        val acx = w - m - s / 2
        val acy = h - m - s / 2 - lift
        btn(BTN_A).rect.set(acx - s/2, acy - s/2, acx + s/2, acy + s/2)
        val bcx = acx - s - g
        val bcy = acy - s / 2 - g
        btn(BTN_B).rect.set(bcx - s/2, bcy - s/2, bcx + s/2, bcy + s/2)

        // SELECT / START
        val sw = dp(64f); val sh = dp(30f)
        val cx = w / 2f
        val sy = h - m - sh / 2 - lift
        btn(BTN_SELECT).rect.set(cx - sw - g/2, sy - sh/2, cx - g/2,      sy + sh/2)
        btn(BTN_START ).rect.set(cx + g/2,      sy - sh/2, cx + sw + g/2, sy + sh/2)
    }

    private fun btn(bit: Int) = allButtons.first { it.bit == bit }

    // ── Draw ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        for (b in allButtons) {
            val pressed = (controllerState and b.bit) != 0
            val r = if (b.bit == BTN_SELECT || b.bit == BTN_START) dp(6f) else dp(10f)
            canvas.drawRoundRect(b.rect, r, r, if (pressed) paintBgPressed else paintBg)
            canvas.drawRoundRect(b.rect, r, r, if (pressed) paintBorderPressed else paintBorder)
            paintText.textSize = if (b.bit == BTN_SELECT || b.bit == BTN_START) dp(9f) else dp(15f)
            val ty = b.rect.centerY() - (paintText.descent() + paintText.ascent()) / 2
            canvas.drawText(b.label, b.rect.centerX(), ty, paintText)
        }
    }

    // ── Мультитач ────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val bit = hitTest(event.getX(idx), event.getY(idx))
                if (bit != 0) {
                    activePointers[pid] = bit
                    controllerState = controllerState or bit
                    onStateChanged?.invoke(controllerState.toUByte())
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val bit = activePointers.remove(pid) ?: 0
                if (bit != 0) {
                    controllerState = controllerState and bit.inv()
                    onStateChanged?.invoke(controllerState.toUByte())
                    invalidate()
                }
            }
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Int {
        for (b in allButtons) if (b.rect.contains(x, y)) return b.bit
        return 0
    }

    fun reset() {
        controllerState = 0
        activePointers.clear()
        onStateChanged?.invoke(0u)
        invalidate()
    }
}