package com.example.yttoxicitychecker.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var toxicPercentage = 0f
    private var neutralPercentage = 0f
    private var safePercentage = 0f

    private val toxicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        style = Paint.Style.FILL
    }

    private val neutralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.FILL
    }

    private val safePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    fun updateData(toxic: Int, neutral: Int, safe: Int) {
        val total = (toxic + neutral + safe).toFloat()
        if (total > 0) {
            toxicPercentage = toxic / total
            neutralPercentage = neutral / total
            safePercentage = safe / total
        } else {
            toxicPercentage = 0f
            neutralPercentage = 0f
            safePercentage = 0f
        }
        invalidate() // Force redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val radius = minOf(width, height) / 2.5f
        val centerX = width / 2
        val centerY = height / 2

        var startAngle = -90f

        // Draw toxic slice
        val toxicSweep = 360f * toxicPercentage
        if (toxicSweep > 0) {
            canvas.drawArc(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                startAngle, toxicSweep, true, toxicPaint
            )
            startAngle += toxicSweep
        }

        // Draw neutral slice
        val neutralSweep = 360f * neutralPercentage
        if (neutralSweep > 0) {
            canvas.drawArc(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                startAngle, neutralSweep, true, neutralPaint
            )
            startAngle += neutralSweep
        }

        // Draw safe slice
        val safeSweep = 360f * safePercentage
        if (safeSweep > 0) {
            canvas.drawArc(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                startAngle, safeSweep, true, safePaint
            )
        }

        // Draw center circle for donut effect
        val innerRadius = radius * 0.5f
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, innerRadius, innerPaint)

        // Draw total percentage text
        val totalPercentage = (toxicPercentage * 100).toInt()
        if (totalPercentage > 0) {
            canvas.drawText("$totalPercentage%", centerX, centerY + 15, textPaint)
        } else if (toxicPercentage == 0f && neutralPercentage == 0f && safePercentage == 0f) {
            canvas.drawText("0%", centerX, centerY + 15, textPaint)
        }
    }
}