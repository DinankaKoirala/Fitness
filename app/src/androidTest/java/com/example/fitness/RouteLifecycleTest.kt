package com.example.fitness

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.activity.RouteService
import com.example.fitness.data.ActivitySettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteLifecycleTest {
    @Suppress("DEPRECATION")
    @Test fun locationForegroundServiceStartsAndStopsExplicitly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun shell(command: String) {
            val fd = instrumentation.uiAutomation.executeShellCommand(command)
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
        shell("pm grant ${context.packageName} android.permission.ACCESS_COARSE_LOCATION")
        shell("pm grant ${context.packageName} android.permission.ACCESS_FINE_LOCATION")
        if (Build.VERSION.SDK_INT >= 29) shell("pm grant ${context.packageName} android.permission.ACCESS_BACKGROUND_LOCATION")
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val manager = context.getSystemService(ActivityManager::class.java)
        fun active() = manager.getRunningServices(30).any { it.service.className == RouteService::class.java.name && it.foreground }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            runBlocking { ActivitySettings(context).routeEnabled(true) }
            scenario.onActivity { RouteService.start(it) }
            val startDeadline = System.currentTimeMillis() + 5000
            while (!active() && System.currentTimeMillis() < startDeadline) Thread.sleep(50)
            assertTrue("Location service must enter the foreground", active())
            scenario.onActivity { it.startService(Intent(it, RouteService::class.java).setAction("stop")) }
            val stopDeadline = System.currentTimeMillis() + 5000
            while (active() && System.currentTimeMillis() < stopDeadline) Thread.sleep(50)
            assertFalse(active())
            assertFalse(runBlocking { ActivitySettings(context).routeEnabled.first() })
        } finally {
            runBlocking { ActivitySettings(context).routeEnabled(false) }
            context.stopService(Intent(context, RouteService::class.java))
            scenario.close()
        }
    }
}
