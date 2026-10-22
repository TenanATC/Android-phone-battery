package com.tenan.equalizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tenan.equalizer.dsp.ResponseCurve
import kotlin.math.ln
import kotlin.math.pow

private const val MIN_HZ = 20.0
private const val MAX_HZ = 20000.0
private const val RANGE_DB = 15.0

/**
 * The frequency response being applied: solid line and fill for the total
 * curve, dashed line for the headphone correction on its own.
 */
@Composable
fun ResponseGraph(
    total: ResponseCurve,
    correction: ResponseCurve?,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val correctionColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics { contentDescription = "Equalizer frequency response graph" }
    ) {
        val w = size.width
        val h = size.height
        fun x(hz: Double) = (ln(hz / MIN_HZ) / ln(MAX_HZ / MIN_HZ) * w).toFloat()
        fun y(db: Double) = (h / 2 - db.coerceIn(-RANGE_DB, RANGE_DB) / RANGE_DB * (h / 2)).toFloat()

        listOf(-12.0, -6.0, 0.0, 6.0, 12.0).forEach { db ->
            drawLine(gridColor, Offset(0f, y(db)), Offset(w, y(db)), strokeWidth = if (db == 0.0) 2f else 1f)
            drawText(measurer, "${if (db > 0) "+" else ""}${db.toInt()}", Offset(4f, y(db) - 14f), labelStyle)
        }
        listOf(100.0 to "100", 1000.0 to "1k", 10000.0 to "10k").forEach { (hz, label) ->
            drawLine(gridColor, Offset(x(hz), 0f), Offset(x(hz), h), strokeWidth = 1f)
            drawText(measurer, label, Offset(x(hz) + 4f, h - 16f), labelStyle)
        }

        val steps = 160
        fun pathOf(curve: ResponseCurve): Path = Path().apply {
            for (i in 0..steps) {
                val hz = MIN_HZ * (MAX_HZ / MIN_HZ).pow(i / steps.toDouble())
                val px = x(hz)
                val py = y(curve.dbAt(hz))
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        }

        val line = pathOf(total)
        val fill = Path().apply {
            addPath(line)
            lineTo(w, y(0.0))
            lineTo(0f, y(0.0))
            close()
        }
        drawPath(fill, lineColor.copy(alpha = 0.15f))
        drawPath(line, lineColor, style = Stroke(width = 3.dp.toPx()))
        correction?.let {
            drawPath(
                pathOf(it), correctionColor,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
            )
        }
    }
}
