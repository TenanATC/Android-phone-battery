package com.tenan.batteryanalyzer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tenan.batteryanalyzer.data.BatterySnapshot
import com.tenan.batteryanalyzer.ui.theme.LocalChartColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Battery level over time. Discharge segments draw as a thin line; charging
 * segments draw thicker in the charge color (color + weight, so the
 * distinction survives color-vision deficiency and grayscale).
 */
@Composable
fun BatteryChart(
    history: List<BatterySnapshot>,
    windowHours: Int,
    modifier: Modifier = Modifier,
) {
    val chartColors = LocalChartColors.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val now = System.currentTimeMillis()
    val windowMs = windowHours * 3_600_000L
    val start = now - windowMs
    val points = history.filter { it.timestamp >= start }

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            LegendChip(color = chartColors.discharge, label = "On battery")
            LegendChip(color = chartColors.charge, label = "Charging")
        }

        if (points.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Not enough data yet.\nHistory builds up as the app samples in the background.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.Top) {
            // Y axis labels
            Column(
                modifier = Modifier.height(180.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                for (label in listOf("100%", "50%", "0%")) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(start = 8.dp)
            ) {
                val w = size.width
                val h = size.height

                fun x(ts: Long) = ((ts - start).toFloat() / windowMs) * w
                fun y(level: Int) = h - (level / 100f) * h

                // Recessive horizontal grid at 0/25/50/75/100
                for (i in 0..4) {
                    val gy = h * i / 4f
                    drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
                }

                // Segment-wise polyline, styled per charging state
                for (i in 1 until points.size) {
                    val a = points[i - 1]
                    val b = points[i]
                    if (b.timestamp - a.timestamp > 2 * 3_600_000L) continue // gap
                    val charging = a.isCharging || b.isCharging
                    val path = Path().apply {
                        moveTo(x(a.timestamp), y(a.level))
                        lineTo(x(b.timestamp), y(b.level))
                    }
                    drawPath(
                        path,
                        color = if (charging) chartColors.charge else chartColors.discharge,
                        style = Stroke(
                            width = (if (charging) 4.dp else 2.dp).toPx(),
                            cap = StrokeCap.Round,
                        ),
                    )
                }
            }
        }

        // X axis: start and end timestamps only — recessive, no clutter
        val fmt = SimpleDateFormat(if (windowHours <= 24) "HH:mm" else "EEE HH:mm", Locale.getDefault())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(fmt.format(Date(start)), style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text("now", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

@Composable
private fun LegendChip(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
