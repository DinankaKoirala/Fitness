package com.example.fitness.food.lookup

import com.example.fitness.data.FoodSource as Provenance
import org.json.JSONArray
import org.json.JSONObject

fun encodeDraft(value: FoodCandidate): String = JSONObject(serializeCandidate(value))
    .put("servingAmount", value.food.servingAmount).put("unit", value.food.servingUnit)
    .put("source", value.food.source.name).put("estimated", value.food.estimated)
    .put("attribution", value.attribution).put("missing", JSONArray(value.missing.toList()))
    .put("requestId", value.requestId ?: JSONObject.NULL).toString()
fun decodeDraft(value: String): FoodCandidate {
    val root = JSONObject(value)
    val nutrients = root.getJSONObject("nutrients")
    return candidate(root.getString("name"), GeminiProvider.fields.associateWith { nutrients.number(it) },
        Provenance.valueOf(root.getString("source")), root.getString("attribution"), root.getBoolean("estimated"),
        root.getDouble("servingAmount"), root.getString("unit")).copy(
        missing = root.getJSONArray("missing").let { (0 until it.length()).map { i -> it.getString(i) }.toSet() },
        requestId = if (root.isNull("requestId")) null else root.getLong("requestId"))
}
