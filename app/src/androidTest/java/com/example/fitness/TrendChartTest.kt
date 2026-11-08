package com.example.fitness
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitness.core.FitnessTheme
import com.example.fitness.trends.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class TrendChartTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyOneDayAndMonthsRenderAndSupportInspection() {
        compose.setContent { FitnessTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TrendChart("Empty", emptyList(), listOf(ChartSeries("Test", Color.Cyan, emptyList())), "g")
            TrendChart("One day", listOf("2026-01-01"), listOf(ChartSeries("Test", Color.Cyan, listOf(42.0))), "kg")
            TrendChart("Months", (1..180).map { "Day $it" }, listOf(ChartSeries("Walk", Color.Green, (1..180).map { it.toDouble() }),
                ChartSeries("Vehicle", Color.Yellow, (1..180).map { (it * 2).toDouble() })), "km", stacked = true)
        } } }
        compose.onNodeWithText("No recorded data in this range.").assertExists()
        compose.onNodeWithTag("chart_One_day").performScrollTo().assertExists()
        assertTrue(compose.onNodeWithTag("chart_One_day").captureToImage().width > 0)
        compose.onNodeWithTag("chart_Months").performScrollTo().assertExists()
        assertTrue(compose.onNodeWithTag("chart_Months").captureToImage().height > 0)
    }
}
