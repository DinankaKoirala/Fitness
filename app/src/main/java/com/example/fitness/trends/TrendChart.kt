package com.example.fitness.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fitness.food.displayNumber

data class ChartSeries(val name: String, val color: Color, val values: List<Double?>)

@Composable
fun TrendChart(title: String, dates: List<String>, series: List<ChartSeries>, unit: String, goal: Double? = null, stacked: Boolean = false) {
    var selectedIndex by remember(dates) { mutableStateOf<Int?>(null) }
    val count = dates.size
    val hasData = series.any { it.values.any { value -> value != null } }
    val maxValue = if (stacked) (0 until count).maxOfOrNull { i -> series.sumOf { it.values.getOrNull(i) ?: 0.0 } } ?: 0.0
        else series.flatMap { it.values }.filterNotNull().maxOrNull() ?: 0.0
    val maximum = maxOf(maxValue, goal ?: 0.0, 1.0) * 1.1
    Card(Modifier.fillMaxWidth().testTag("chart_${title.replace(' ', '_')}").semantics { contentDescription = "$title, ${if (hasData) "recorded data" else "no recorded data"}, $unit" }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (!hasData) Text("No recorded data in this range.")
            else {
                Text("Scale 0–${displayNumber(maximum)} $unit", style = MaterialTheme.typography.bodySmall)
                Canvas(Modifier.fillMaxWidth().height(150.dp).pointerInput(dates, series) { detectTapGestures { offset ->
                    if (count > 0) selectedIndex = if (count == 1) 0 else (((offset.x - 8) / (size.width - 16).coerceAtLeast(1) * (count - 1)).roundToInt()).coerceIn(0, count - 1)
                } }) {
                    val w = (size.width - 16).coerceAtLeast(1f); val h = (size.height - 12).coerceAtLeast(1f)
                    fun x(i: Int) = 8f + chartX(i, count) * w
                    fun y(v: Double) = 6f + chartY(v, maximum) * h
                    repeat(4) { row -> val line = 6f + h * row / 3; drawLine(Color(0xFF383D48), Offset(8f, line), Offset(8f + w, line), 1f) }
                    goal?.let { drawLine(Color(0xFF8FB8FF), Offset(8f, y(it)), Offset(8f + w, y(it)), 2f) }
                    if (stacked) {
                        val barWidth = (w / count.coerceAtLeast(1) * 0.6f).coerceAtLeast(1f)
                        (0 until count).forEach { i ->
                            var baseline = 0.0
                            series.forEach { line -> line.values.getOrNull(i)?.let { value ->
                                val top = y(baseline + value); val bottom = y(baseline)
                                drawRect(line.color, Offset(x(i) - barWidth / 2, top), Size(barWidth, (bottom - top).coerceAtLeast(1f)))
                                baseline += value
                            } }
                        }
                    } else series.forEach { line ->
                        var previous: Offset? = null
                        line.values.forEachIndexed { i, value ->
                            if (value == null || !value.isFinite()) previous = null
                            else {
                                val position = Offset(x(i), y(value))
                                previous?.let { drawLine(line.color, it, position, 3f) }
                                drawCircle(line.color, 3f, position); previous = position
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(dates.firstOrNull().orEmpty(), style = MaterialTheme.typography.bodySmall); Text(dates.lastOrNull().orEmpty(), style = MaterialTheme.typography.bodySmall) }
                val detail = selectedIndex ?: dates.indices.lastOrNull { i -> series.any { it.values.getOrNull(i) != null } }
                detail?.let { index -> Text("${dates[index]}: " + series.joinToString { line -> "${line.name} ${line.values.getOrNull(index)?.let(::displayNumber) ?: "unknown"} $unit" }, style = MaterialTheme.typography.bodySmall) }
                Text("Tap the chart to inspect a date.", style = MaterialTheme.typography.bodySmall)
                series.forEach { line -> Text("${line.name} · ${line.values.count { it != null }} observations", color = line.color, style = MaterialTheme.typography.bodySmall) }
                goal?.let { Text("Current goal: ${displayNumber(it)} $unit", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
