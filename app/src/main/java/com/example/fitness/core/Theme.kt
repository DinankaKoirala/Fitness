package com.example.fitness.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object FitnessColors {
    val Background = Color(0xFF12151A)
    val Card = Color(0xFF1B1F26)
    val Protein = Color(0xFF8FDCF5)
    val Fat = Color(0xFFE8D38F)
    val Carbs = Color(0xFFB4A6EE)
    val Accent = Color(0xFF8FB8FF)
    val Walking = Color(0xFF7FD6B0)
    val Vehicle = Color(0xFFF0A35E)
}

private val Colors = darkColorScheme(
    primary = FitnessColors.Accent,
    onPrimary = FitnessColors.Background,
    secondary = FitnessColors.Walking,
    background = FitnessColors.Background,
    surface = FitnessColors.Card,
    onBackground = Color(0xFFE5E8EF),
    onSurface = Color(0xFFE5E8EF),
)

@Composable
fun FitnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
