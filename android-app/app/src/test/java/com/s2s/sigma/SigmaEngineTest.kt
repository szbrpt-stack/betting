package com.s2s.sigma

import com.s2s.sigma.data.*
import com.s2s.sigma.domain.SigmaEngine
import org.junit.Assert.*
import org.junit.Test

class SigmaEngineTest {
    private val event = DemoData.events.first()
    private val question = DemoData.questions(event).first()
    private val request = ResearchRequest("test", event.id, question, DemoData.consulted, DemoData.cutoff)

    @Test
    fun validatesTraceableSources() {
        val result = SigmaEngine.validateSources(DemoData.demoSources(question), DemoData.cutoff)
        assertTrue(result.valid)
    }

    @Test
    fun rejectsSourcesConsultedAfterCutoff() {
        val source = DemoData.demoSources(question).first().copy(consultedAt = "2026-11-18T18:00:00-05:00")
        val result = SigmaEngine.validateSources(listOf(source), DemoData.cutoff)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("después del corte") })
    }

    @Test
    fun temporalValidationFailsClosedAgainstLeakage() {
        val row = DemoData.demoObservations(event, question).first().copy(completedAt = DemoData.cutoff)
        assertFalse(SigmaEngine.validateTemporal(listOf(row), DemoData.cutoff).valid)
    }

    @Test
    fun missingDataProducesInsufficientVerdict() {
        val response = ResearchResponse("test", emptyList(), emptyList(), emptyList(), DemoData.consulted)
        val result = SigmaEngine.analyze(request, response)
        assertEquals(SigmaVerdict.INSUFFICIENT, result.verdict)
        assertNull(result.statistics.mean)
        assertTrue(result.missingData.isNotEmpty())
    }

    @Test
    fun descriptiveStatisticsAreReproducible() {
        val observations = DemoData.demoObservations(event, question)
        val stats = SigmaEngine.descriptiveStatistics(observations, "points")
        assertEquals(8, stats.sampleSize)
        assertEquals(107.0, stats.mean!!, 0.0001)
        assertEquals(107.5, stats.median!!, 0.0001)
        assertNotNull(stats.sampleStandardDeviation)
    }

    @Test
    fun noValidatedModelMeansNoPredictionOrInterval() {
        val response = ResearchResponse(
            "test",
            DemoData.demoObservations(event, question),
            DemoData.demoSources(question),
            emptyList(),
            DemoData.consulted,
        )
        val result = SigmaEngine.analyze(request, response)
        assertEquals(SigmaVerdict.DESCRIPTIVE, result.verdict)
        assertFalse(result.validatedModel)
        assertNull(result.estimate)
        assertNull(result.uncertaintyInterval)
    }

    @Test
    fun normalizesOnlyCompatibleUnits() {
        assertEquals(120.0, SigmaEngine.normalizeValue(2.0, "minutes", "seconds")!!, 0.0)
        assertNull(SigmaEngine.normalizeValue(2.0, "goals", "points"))
    }
}

internal fun testAnalysis(event: SportEvent, question: SigmaQuestion): SigmaAnalysis {
    val request = ResearchRequest("json", event.id, question, DemoData.consulted, DemoData.cutoff)
    val response = ResearchResponse(
        "json", DemoData.demoObservations(event, question), DemoData.demoSources(question),
        emptyList(), DemoData.consulted,
    )
    return SigmaEngine.analyze(request, response)
}
