package com.example.fitness.food.lookup

import com.example.fitness.data.FoodSource as Provenance

/** Conservative label parser: missing macros stay blank in the confirmation form. */
fun parseLabel(text: String): FoodCandidate {
    fun amount(label: String, target: String): Double? {
        val pattern = Regex("(?im)^\\s*(?:$label)\\b\\s*:?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*(mcg|µg|ug|mg|g)\\b")
        val match = pattern.find(text) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase().replace("mcg", "µg").replace("ug", "µg")
        val inGrams = value * when (unit) { "g" -> 1.0; "mg" -> 0.001; else -> 0.000001 }
        return inGrams * when (target) { "g" -> 1.0; "mg" -> 1000.0; else -> 1_000_000.0 }
    }
    val kcal = Regex("(?i)([0-9]+(?:[.,][0-9]+)?)\\s*kcal").find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        ?: Regex("(?im)^\\s*calories?\\s*:?\\s*([0-9]+(?:[.,][0-9]+)?)").find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        ?: Regex("(?i)([0-9]+(?:[.,][0-9]+)?)\\s*kJ").find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.div(4.184)
    val basis = Regex("(?i)per\\s*100\\s*(g|ml)").find(text)?.groupValues?.get(1)?.lowercase()
    return candidate("Scanned nutrition label", mapOf("caloriesKcal" to kcal, "proteinG" to amount("protein", "g"),
        "fatG" to amount("(?:total\\s+)?fat", "g"), "carbsG" to amount("(?:total\\s+)?carbohydrates?", "g"),
        "fiberG" to amount("(?:dietary\\s+)?fib(?:er|re)", "g"), "sodiumMg" to amount("sodium", "mg"),
        "calciumMg" to amount("calcium", "mg"), "ironMg" to amount("iron", "mg"), "potassiumMg" to amount("potassium", "mg"),
        "magnesiumMg" to amount("magnesium", "mg"), "vitaminCMg" to amount("vitamin\\s*C", "mg"), "vitaminDMcg" to amount("vitamin\\s*D", "µg")),
        Provenance.OCR, "On-device OCR · estimated · verify every value, unit and column before saving", true,
        if (basis != null) 100.0 else 1.0, basis ?: "serving")
}
