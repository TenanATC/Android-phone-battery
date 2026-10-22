package com.tenan.equalizer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** One vertical EQ fader with its gain above and frequency below. */
@Composable
fun BandSlider(
    label: String,
    valueDb: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatDb(valueDb),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Slider(
            value = valueDb,
            onValueChange = { onChange((it * 2).roundToInt() / 2f) },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier
                .semantics { contentDescription = "$label Hz" }
                // Rotate a horizontal slider into a vertical one: lay it out
                // with swapped constraints, then turn it so minimum is at the bottom.
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth,
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(-placeable.width, 0)
                    }
                }
                .width(200.dp)
                .height(32.dp),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** "+1.5", "0", "-3" — locale-independent so the decimal point never becomes a comma. */
fun formatDb(db: Float): String {
    val tenths = (db * 10).roundToInt()
    val magnitude = abs(tenths)
    val body = if (magnitude % 10 == 0) "${magnitude / 10}" else "${magnitude / 10}.${magnitude % 10}"
    return when {
        tenths > 0 -> "+$body"
        tenths < 0 -> "-$body"
        else -> "0"
    }
}
