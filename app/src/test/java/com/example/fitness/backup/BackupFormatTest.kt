package com.example.fitness.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class BackupFormatTest {
    @Test fun csvEscapesUserTextAndPreservesNumbers() {
        assertEquals("'=SUM(A1:A2)", csvCell("=SUM(A1:A2)"))
        assertEquals("'  +1", csvCell("  +1"))
        assertEquals("-2.5", csvCell(-2.5))
        assertEquals("\"a,\"\"b\"\"\nc\"", csvCell("a,\"b\"\nc"))
        assertEquals("", csvCell(null))
    }
    @Test fun excessiveNestingRejectedWithoutWholeFileAllocation() {
        try { GuardedJsonReader(StringReader("[".repeat(33))).readText(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
        assertEquals("{\"a\":\"[\\\"\"}", GuardedJsonReader(StringReader("{\"a\":\"[\\\"\"}")).readText())
    }
}
