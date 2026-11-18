package com.s2s.sigma

import com.s2s.sigma.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilteringTest {
    @Test
    fun competitionsAreFilteredBySport() {
        val basketball = DemoData.competitions.forSport("basketball")
        assertEquals(setOf("nba", "euroleague", "acb"), basketball.map { it.id }.toSet())
        assertTrue(basketball.all { it.sportId == "basketball" })
    }

    @Test
    fun eventsAreFilteredByCompetition() {
        val nba = DemoData.events.applyFilter(EventFilter(competitionId = "nba"))
        assertEquals(listOf("nba-demo-001"), nba.map { it.id })
    }

    @Test
    fun participantAndStatusFiltersCompose() {
        val found = DemoData.events.applyFilter(
            EventFilter(status = EventStatus.SCHEDULED, participantQuery = "Nova"),
        )
        assertEquals("f1-demo-001", found.single().id)
    }
}
