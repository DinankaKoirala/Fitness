package com.example.fitness

import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitness.data.ProfileRepository
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.example.fitness.data.FoodRepository
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.fitness.core.FitnessTheme
import com.example.fitness.core.ForegroundNetwork
import com.example.fitness.food.FoodScreen
import com.example.fitness.activity.ActivityScreen
import com.example.fitness.gym.GymScreen
import com.example.fitness.profile.ProfileScreen

class MainActivity : ComponentActivity() {
    override fun onStart() { super.onStart(); ForegroundNetwork.enter() }
    override fun onStop() { ForegroundNetwork.leave(); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { FitnessTheme { FitnessApp((application as FitnessApplication).profiles, (application as FitnessApplication).foods) } }
    }
}

@Composable
fun FitnessApp(repository: ProfileRepository, foods: FoodRepository) {
    val state by repository.state.collectAsStateWithLifecycle()
    if (state.loading || state.error != null) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (state.loading) CircularProgressIndicator()
                else {
                    Text(state.error.orEmpty())
                    Button(onClick = repository::retry) { Text("Try again") }
                }
            }
        }
        return
    }
    if (state.profile == null) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
            ProfileScreen(state, repository, onboarding = true)
        }
        return
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabStates = rememberSaveableStateHolder()
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
            tabStates.SaveableStateProvider(selected) {
            when (selected) {
                0 -> FoodScreen(requireNotNull(state.goal), estimated = state.manualGoal == null, repository = foods)
                1 -> ActivityScreen()
                2 -> GymScreen()
                3 -> ProfileScreen(state, repository)
            }
            }
        }
    }
}
