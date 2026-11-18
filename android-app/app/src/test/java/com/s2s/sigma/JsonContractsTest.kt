package com.s2s.sigma

import com.s2s.sigma.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonContractsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun sportEventRoundTripsWithoutLosingMultiParticipantShape() {
        val event = DemoData.events.first { it.format == EventFormat.MULTI_PARTICIPANT }
        val encoded = json.encodeToString(event)
        val decoded = json.decodeFromString<SportEvent>(encoded)
        assertEquals(event, decoded)
        assertEquals(5, decoded.participants.size)
        assertTrue(encoded.contains("MULTI_PARTICIPANT"))
    }

    @Test
    fun analysisContractKeepsUnsupportedPredictionFieldsNull() {
        val event = DemoData.events.first()
        val question = DemoData.questions(event).first()
        val analysis = testAnalysis(event, question)
        val roundTrip = json.decodeFromString<SigmaAnalysis>(json.encodeToString(analysis))
        assertEquals(null, roundTrip.estimate)
        assertEquals(null, roundTrip.uncertaintyInterval)
        assertEquals(false, roundTrip.validatedModel)
    }
}
