package com.example.fitness.food.lookup

/** Only an explicit positive save command skips the default review flow. */
internal fun requestsDatabaseSave(prompt: String): Boolean {
    val text = prompt.lowercase()
    if (Regex("\\b(don't|do not|never|not)\\b").containsMatchIn(text)) return false
    return Regex("\\b(add|save|store)\\b.*\\b(database|saved foods)\\b").containsMatchIn(text)
}
