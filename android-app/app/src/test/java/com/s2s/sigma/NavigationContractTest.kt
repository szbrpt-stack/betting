package com.s2s.sigma

import com.s2s.sigma.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationContractTest {
    @Test
    fun tenMandatoryScreensHaveUniqueRoutes() {
        assertEquals(10, Routes.mandatoryScreens.size)
        assertEquals(10, Routes.mandatoryScreens.distinct().size)
    }

    @Test
    fun sigmaFlowPreservesEveryHierarchyIdentifier() {
        val sport = Routes.competitions("basketball")
        val competition = Routes.events("nba")
        val event = Routes.event("nba-demo-001")
        val questions = Routes.questions("nba-demo-001")
        val research = Routes.research("nba-demo-001", "question-1")
        val result = Routes.result("nba-demo-001", "question-1")
        assertTrue(sport.endsWith("basketball"))
        assertTrue(competition.endsWith("nba"))
        assertTrue(event.contains("nba-demo-001"))
        assertTrue(questions.contains("nba-demo-001"))
        assertTrue(research.contains("question-1"))
        assertTrue(result.contains("question-1"))
    }
}
