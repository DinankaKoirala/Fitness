package com.example.fitness.food

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FoodScreen(goal: Int, estimated: Boolean) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Food", style = MaterialTheme.typography.headlineLarge)
        Text("Daily calorie goal", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$goal kcal / day", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("foodGoal"))
        Text(if (estimated) "Estimated · edit in Profile" else "Manual goal · edit in Profile",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("goalSource"))
    }
}
