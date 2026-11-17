package com.example.fitness.food.lookup

import org.junit.Assert.*
import org.junit.Test

class PromptPolicyTest {
    @Test fun savesOnlyWhenExplicitlyRequested() {
        assertTrue(requestsDatabaseSave("Estimate this meal and add it to the database"))
        assertTrue(requestsDatabaseSave("Save this to my saved foods"))
        assertFalse(requestsDatabaseSave("Estimate this meal"))
        assertFalse(requestsDatabaseSave("Do not add this to the database"))
        assertFalse(requestsDatabaseSave("Don't save this to the database"))
    }
}
