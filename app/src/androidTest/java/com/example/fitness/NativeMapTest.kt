package com.example.fitness

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitness.activity.*
import com.example.fitness.core.*
import com.example.fitness.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapView

@RunWith(AndroidJUnit4::class)
class NativeMapTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun nativeMapLoadsOfflineRouteLayersAndDisposes() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val state = ActivityRangeState(TimeRange(1000, 11000), loading = false,
            points = listOf(LocationPoint(timestamp = 1000, lat = 27.7, lon = 85.3, speed = 1.2, accuracy = 1.0),
                LocationPoint(timestamp = 11000, lat = 27.7001, lon = 85.3, speed = 1.2, accuracy = 1.0)),
            segments = listOf(Segment(start = 1000, end = 11000, type = SegmentType.WALKING, distance = 11.0)))
        fun find(view: View): MapView? {
            if (view is MapView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        try {
            scenario.onActivity { activity ->
                // Block real tile requests: route overlays must render without a basemap/network.
                ForegroundNetwork.leave()
                activity.setContent { FitnessTheme { NativeRouteMap(state, false, "", null, false, false, Modifier.fillMaxSize()) } }
            }
            var ready = false
            compose.waitUntil(10000) {
                scenario.onActivity { activity -> find(activity.window.decorView)?.getMapAsync { map -> ready = map.style?.getLayer("route") != null } }
                ready
            }
            scenario.onActivity { activity -> find(activity.window.decorView)!!.getMapAsync { map ->
                assertNotNull(map.style!!.getSource("route"))
                assertNotNull(map.style!!.getLayer("ends"))
            } }
        } finally { scenario.close() }
    }
}
