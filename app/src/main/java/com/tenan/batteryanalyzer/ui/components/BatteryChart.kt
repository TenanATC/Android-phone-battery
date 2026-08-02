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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tenan.batteryanalyzer.data.BatterySnapshot
import com.tenan.batteryanalyzer.ui.theme.LocalChartColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val CHART_HEIGHT = 180.dp
private val Y_AXIS_GUTTER = 40.dp
private val PLUGGED_STROKE = 3.5.dp
private val BATTERY_STROKE = 2.dp

/**
 * Two samples closer than this are joined with a solid line — the reading is
 * dense enough that the straight interpolation between them is trustworthy.
 */
private val SOLID_MAX_MS = TimeUnit.MINUTES.toMillis(35)

/**
 * Between [SOLID_MAX_MS] and this, the samples are joined with a faded dashed
 * line: the endpoints are real, everything between them is inferred. Beyond
 * this the series is broken rather than inventing a shape across the hole.
 */
private val INFERRED_MAX_MS = TimeUnit.HOURS.toMillis(3)

/**
 * Battery level over time.
 *
 * Colour encodes whether the cable was connected, taken from EXTRA_PLUGGED
 * rather than the battery status flag — status reports FULL/NOT_CHARGING while
 * still on power, which paints discharges as charges. Both endpoints of a
 * segment must agree before it is drawn as plugged in, so an unplug is never
 * back-filled onto the discharge that follows it. Line weight carries the same
 * distinction, so the series stay separable without relying on colour.
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
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    val now = remember(history) { System.currentTimeMillis() }
    val windowMs = windowHours * 3_600_000L
    val start = now - windowMs

    // Carry in the last sample from before the window so the line enters at the
    // left edge; otherwise the chart looks empty until the first in-window
    // sample, even when the phone was being tracked continuously.
    val points = remember(history, start) {
        val firstIn = history.indexOfFirst { it.timestamp >= start }
        when {
            firstIn < 0 -> emptyList()
            firstIn == 0 -> history
            else -> history.subList(firstIn - 1, history.size)
        }
    }
    val inWindow = remember(points, start) { points.count { it.timestamp >= start } }

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            LegendChip(color = chartColors.discharge, label = "On battery")
            LegendChip(color = chartColors.charge, label = "Plugged in")
        }

        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No samples in this window yet.\nHistory builds up as the app samples in the background.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .clipToBounds()
        ) {
            val gutter = Y_AXIS_GUTTER.toPx()
            // Inset the plot vertically by half the thickest stroke so a 100%
            // or 0% reading draws in full instead of being sliced by the edge.
            val inset = PLUGGED_STROKE.toPx() / 2f
            val plotLeft = gutter
            val plotRight = size.width
            val plotTop = inset
            val plotBottom = size.height - inset
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop

            fun x(ts: Long) = plotLeft + ((ts - start).toFloat() / windowMs) * plotW
            fun y(level: Int) = plotBottom - (level / 100f) * plotH

            // Grid, with labels centred on their own line rather than
            // distributed by a layout that knows nothing about the gridlines.
            for (pct in listOf(100, 75, 50, 25, 0)) {
                val gy = y(pct)
                drawLine(gridColor, Offset(plotLeft, gy), Offset(plotRight, gy), 1.dp.toPx())
                if (pct % 50 == 0) {
                    val label = textMeasurer.measure(AnnotatedString("$pct%"), labelStyle)
                    drawText(
                        label,
                        topLeft = Offset(
                            plotLeft - 8.dp.toPx() - label.size.width,
                            gy - label.size.height / 2f,
                        ),
                    )
                }
            }

            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                val dt = b.timestamp - a.timestamp
                if (dt > INFERRED_MAX_MS) continue

                // Both ends must be on the cable. A segment spanning an unplug
                // is a discharge, and is drawn as one.
                val plugged = a.isPlugged && b.isPlugged
                val inferred = dt > SOLID_MAX_MS
                val dash = 5.dp.toPx()
                drawLine(
                    color = if (plugged) chartColors.charge else chartColors.discharge,
                    start = Offset(x(a.timestamp), y(a.level)),
                    end = Offset(x(b.timestamp), y(b.level)),
                    strokeWidth = (if (plugged) PLUGGED_STROKE else BATTERY_STROKE).toPx(),
                    cap = if (inferred) StrokeCap.Butt else StrokeCap.Round,
                    alpha = if (inferred) 0.4f else 1f,
                    pathEffect = if (inferred) {
                        PathEffect.dashPathEffect(floatArrayOf(dash, dash))
                    } else null,
                )
            }

            // A sample with no reachable neighbour would otherwise vanish; draw
            // it as a point so isolated readings are visible as what they are.
            for (i in points.indices) {
                val p = points[i]
                val prevGap = if (i > 0) p.timestamp - points[i - 1].timestamp else Long.MAX_VALUE
                val nextGap = if (i < points.lastIndex) points[i + 1].timestamp - p.timestamp else Long.MAX_VALUE
                if (prevGap > INFERRED_MAX_MS && nextGap > INFERRED_MAX_MS) {
                    drawCircle(
                        color = if (p.isPlugged) chartColors.charge else chartColors.discharge,
                        radius = 2.5.dp.toPx(),
                        center = Offset(x(p.timestamp), y(p.level)),
                    )
                }
            }
        }

        val fmt = SimpleDateFormat(
            if (windowHours <= 24) "HH:mm" else "EEE HH:mm", Locale.getDefault()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = Y_AXIS_GUTTER),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(fmt.format(Date(start)), style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text(
                fmt.format(Date(start + windowMs / 2)),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Text("now", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }

        Text(
            "$inWindow samples in this window · dashed = inferred across a gap",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
