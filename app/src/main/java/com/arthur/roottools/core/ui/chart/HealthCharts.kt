package com.arthur.roottools.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.arthur.roottools.model.HealthHistoryPoint

@Composable
fun RootToolsTemperatureSparkline(history: List<HealthHistoryPoint>) {
    val cutoff = System.currentTimeMillis() - 30 * 60_000L
    val points = history.filter { it.timestampMs >= cutoff && (it.apTempC != null || it.skinTempC != null) }
    val apColor = MaterialTheme.colorScheme.tertiary
    val skinColor = MaterialTheme.colorScheme.primary
    val values = points.flatMap { listOfNotNull(it.apTempC, it.skinTempC) }
    val minValue = (values.minOrNull() ?: 25f) - 1f
    val maxValue = (values.maxOrNull() ?: 45f) + 1f
    val range = (maxValue - minValue).coerceAtLeast(1f)
    Canvas(modifier = Modifier.fillMaxWidth().height(88.dp)) {
        if (points.size < 2) return@Canvas
        val step = size.width / (points.size - 1).coerceAtLeast(1)
        fun y(value: Float) = size.height * (1f - (value - minValue) / range)
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            if (start.apTempC != null && end.apTempC != null) {
                drawLine(
                    color = apColor,
                    start = androidx.compose.ui.geometry.Offset(index * step, y(start.apTempC)),
                    end = androidx.compose.ui.geometry.Offset((index + 1) * step, y(end.apTempC)),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
            if (start.skinTempC != null && end.skinTempC != null) {
                drawLine(
                    color = skinColor,
                    start = androidx.compose.ui.geometry.Offset(index * step, y(start.skinTempC)),
                    end = androidx.compose.ui.geometry.Offset((index + 1) * step, y(end.skinTempC)),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
