package com.example.fitness

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.core.FitnessTheme
import com.example.fitness.data.ActivitySettings
import com.example.fitness.food.lookup.*
import com.example.fitness.settings.SettingsScreen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun lookupAndBatterySettingsPersistAndExportControlsAreReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = ActivitySettings(context)
        val lookup = LookupSettings(context)
        val oldSaver = runBlocking { settings.batterySaver.first() }
        val oldMode = runBlocking { lookup.mode.first() }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { it.setContent { FitnessTheme { SettingsScreen {} } } }
            compose.onNodeWithTag("settingsMode_DATABASE_ONLY").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { lookup.mode.first() == LookupMode.DATABASE_ONLY } }
            compose.onNodeWithTag("settingsBatterySaver").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { settings.batterySaver.first() != oldSaver } }
            compose.onNodeWithTag("exportJson").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("importJson").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("exportGpx").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("openLicenses").performScrollTo().performClick()
            compose.onNodeWithText("Licenses and notices").assertExists()
            Assert.assertTrue(context.assets.open("tesseract4android-LICENSE.txt").bufferedReader().use { it.readText() }.contains("Apache"))
        } finally { scenario.close(); runBlocking { settings.batterySaver(oldSaver); lookup.mode(oldMode) } }
    }
}
