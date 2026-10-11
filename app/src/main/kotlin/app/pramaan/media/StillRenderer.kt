package app.pramaan.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import app.pramaan.core.scoring.broadjump.BjAttempt
import app.pramaan.core.scoring.broadjump.GroundCalibration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object StillRenderer {

    suspend fun renderLandingStill(
        sourceBitmap: Bitmap,
        calib: GroundCalibration,
        attempt: BjAttempt,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            outputFile.parentFile?.mkdirs()

            // Scale down if long side > 1280
            val maxSide = max(sourceBitmap.width, sourceBitmap.height)
            val scale = if (maxSide > 1280) 1280f / maxSide else 1.0f
            val targetW = (sourceBitmap.width * scale).toInt()
            val targetH = (sourceBitmap.height * scale).toInt()

            val working = Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
            val mutable = working.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutable)

            val w = mutable.width.toFloat()
            val h = mutable.height.toFloat()

            // 1. Draw Takeoff Line
            val linePxX = (calib.line.x * w).toFloat()
            val linePaint = Paint().apply {
                color = Color.rgb(234, 88, 12) // Saffron / Orange
                strokeWidth = 6f * scale
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            canvas.drawLine(linePxX, 0f, linePxX, h, linePaint)

            // 2. Draw Marker if present
            if (calib.marker != null) {
                val markerPxX = (calib.marker!!.x * w).toFloat()
                val markerPaint = Paint().apply {
                    color = Color.rgb(37, 99, 235) // Blue
                    strokeWidth = 4f * scale
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(15f * scale, 10f * scale), 0f)
                    isAntiAlias = true
                }
                canvas.drawLine(markerPxX, 0f, markerPxX, h, markerPaint)
            }

            // 3. Draw Contact Point & Vertical Drop Line
            val contactX = (attempt.contact.at.x * w).toFloat()
            val contactY = (attempt.contact.at.y * h).toFloat()
            val groundY = (calib.line.y * h).toFloat()

            val dropPaint = Paint().apply {
                color = Color.rgb(22, 163, 74) // Emerald
                strokeWidth = 4f * scale
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(12f * scale, 8f * scale), 0f)
                isAntiAlias = true
            }
            canvas.drawLine(contactX, contactY, contactX, groundY, dropPaint)

            val pointPaint = Paint().apply {
                color = Color.rgb(22, 163, 74)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(contactX, contactY, 12f * scale, pointPaint)

            // 4. Draw Distance Badge
            val text = "${attempt.distanceCm.toInt()} cm"
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 28f * scale
                isFakeBoldText = true
                isAntiAlias = true
            }
            val textW = textPaint.measureText(text)
            val badgePad = 12f * scale
            val badgeRect = RectF(
                contactX - textW / 2 - badgePad,
                contactY - 50f * scale - badgePad,
                contactX + textW / 2 + badgePad,
                contactY - 10f * scale
            )
            val badgeBgPaint = Paint().apply {
                color = Color.argb(220, 15, 23, 42) // Dark Slate
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRoundRect(badgeRect, 8f * scale, 8f * scale, badgeBgPaint)
            canvas.drawText(text, badgeRect.left + badgePad, badgeRect.bottom - badgePad, textPaint)

            FileOutputStream(outputFile).use { out ->
                mutable.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
