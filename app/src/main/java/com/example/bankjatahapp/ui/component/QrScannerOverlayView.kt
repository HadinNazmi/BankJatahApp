package com.example.bankjatahapp.ui.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class QrScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#99000000") // semi-transparan hitam
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#FF6B35") // oranye tema
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val frameRect = RectF()
    private val cornerLength = 48f
    private val cornerRadius = 16f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Ukuran frame QR — 70% lebar layar, persegi
        val frameSize = width * 0.70f
        val cx = width / 2f
        val cy = height / 2f

        frameRect.set(
            cx - frameSize / 2,
            cy - frameSize / 2,
            cx + frameSize / 2,
            cy + frameSize / 2
        )

        // Layer untuk efek punch-out
        val sc = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // Gambar background gelap
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Hapus area dalam frame (transparan)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, clearPaint)

        canvas.restoreToCount(sc)

        // Gambar sudut-sudut oranye
        drawCorners(canvas)
    }

    private fun drawCorners(canvas: Canvas) {
        val l = frameRect.left
        val t = frameRect.top
        val r = frameRect.right
        val b = frameRect.bottom
        val cl = cornerLength

        // Sudut kiri atas
        canvas.drawLine(l, t + cl, l, t + cornerRadius, cornerPaint)
        canvas.drawLine(l, t, l + cl, t, cornerPaint)

        // Sudut kanan atas
        canvas.drawLine(r - cl, t, r, t, cornerPaint)
        canvas.drawLine(r, t, r, t + cl, cornerPaint)

        // Sudut kiri bawah
        canvas.drawLine(l, b - cl, l, b, cornerPaint)
        canvas.drawLine(l, b, l + cl, b, cornerPaint)

        // Sudut kanan bawah
        canvas.drawLine(r - cl, b, r, b, cornerPaint)
        canvas.drawLine(r, b - cl, r, b, cornerPaint)
    }

    fun getFrameRect(): RectF = frameRect
}