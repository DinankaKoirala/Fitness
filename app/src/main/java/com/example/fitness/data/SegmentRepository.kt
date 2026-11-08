package com.example.fitness.data

import androidx.room.withTransaction
import com.example.fitness.activity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ActivityRangeState(val range: TimeRange, val loading: Boolean = true,
    val points: List<LocationPoint> = emptyList(), val segments: List<Segment> = emptyList(),
    val stats: ActivityStats = activityStats(emptyList()), val error: String? = null)

class SegmentRepository(private val database: FitnessDatabase) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(range: TimeRange): Flow<ActivityRangeState> = combine(
        database.locationDao().observeWithBoundaries(range.from, range.to),
        database.segmentDao().observeSteps(range.from, range.to),
        database.segmentDao().observeLabels(range.from, range.to),
    ) { points, steps, labels -> Triple(points, steps, labels) }.mapLatest { (points, steps, labels) ->
        val segments = withContext(Dispatchers.Default) { buildSegments(points, steps, labels, range) }
        val zone = java.time.ZoneId.systemDefault()
        fun midnight(value: Long): Boolean {
            val date = java.time.Instant.ofEpochMilli(value).atZone(zone).toLocalDate()
            return date.atStartOfDay(zone).toInstant().toEpochMilli() == value
        }
        // Cache canonical whole days only. Slider queries must never replace a
        // complete historical segment with a partial range's clipped estimate.
        if (midnight(range.from) && midnight(range.to)) database.withTransaction {
            database.segmentDao().deleteRange(range.from, range.to)
            database.segmentDao().insert(segments)
        }
        ActivityRangeState(range, loading = false, points = points, segments = segments, stats = activityStats(segments))
    }.catch { emit(ActivityRangeState(range, loading = false, error = "Could not load activity for this range. Try again.")) }

    suspend fun correct(segment: Segment, type: SegmentType) {
        require(segment.end > segment.start)
        database.segmentDao().label(SegmentLabel(start = segment.start, end = segment.end, type = type))
    }
}
