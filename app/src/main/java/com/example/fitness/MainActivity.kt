package com.example.fitness

import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.fitness.core.FitnessTheme
import com.example.fitness.food.FoodScreen
import com.example.fitness.activity.ActivityScreen
import com.example.fitness.gym.GymScreen
import com.example.fitness.profile.ProfileScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { FitnessTheme { FitnessApp() } }
    }
}

@Composable
fun FitnessApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Food", "Activity", "Gym", "Profile")
    val icons = listOf(R.drawable.ic_food, R.drawable.ic_activity, R.drawable.ic_gym, R.drawable.ic_profile)
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { index, label ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { selected = index },
                    icon = { Icon(painterResource(icons[index]), contentDescription = null) },
                    label = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = "$label tab" },
                )
            }
        }
    }) { padding ->
        Surface(Modifier.padding(padding), color = MaterialTheme.colorScheme.background) {
            when (selected) {
                0 -> FoodScreen()
                1 -> ActivityScreen()
                2 -> GymScreen()
                3 -> ProfileScreen()
            }
        }
    }
}
