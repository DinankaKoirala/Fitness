package com.example.fitness.gym

import com.example.fitness.data.SetEntry

enum class RecordKind { FIRST, WEIGHT, REPS, VOLUME, NONE }
fun personalRecord(candidate: SetEntry, previous: List<SetEntry>): RecordKind {
    val history = previous.filter { it.exerciseId == candidate.exerciseId && it.id != candidate.id }
    if (history.isEmpty()) return RecordKind.FIRST
    if (candidate.weightKg > history.maxOf { it.weightKg }) return RecordKind.WEIGHT
    val sameWeight = history.filter { it.weightKg == candidate.weightKg }
    if (sameWeight.isNotEmpty() && candidate.reps > sameWeight.maxOf { it.reps }) return RecordKind.REPS
    if (candidate.weightKg * candidate.reps > history.maxOf { it.weightKg * it.reps }) return RecordKind.VOLUME
    return RecordKind.NONE
}
fun validateSet(reps: Int, weight: Double) {
    require(reps in 1..1000) { "Enter reps from 1 to 1,000." }
    require(weight.isFinite() && weight in 0.0..1000.0) { "Enter weight from 0 to 1,000 kg (0 for unweighted)." }
}
fun timerText(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
