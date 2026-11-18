package com.s2s.sigma.research

import com.s2s.sigma.data.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpenAIResearchGatewayTest {
    private val now = "2026-11-17T10:00:00-05:00"

    @Test
    fun missingEndpointFailsWithoutMockFallback() = runTest {
        val gateway = gateway(endpoint = "", response = "{}")
        val error = expectFailure { gateway.discoverEvents(DiscoveryQuery("NBA hoy")) }
        assertEquals(ResearchFailureKind.CONFIGURATION, error.kind)
        assertTrue(error.message.orEmpty().contains("Falta la URL"))
    }

    @Test
    fun malformedResponseIsVisibleInsteadOfSyntheticData() = runTest {
        val gateway = gateway(response = "not-json")
        val error = expectFailure { gateway.discoverEvents(DiscoveryQuery("NBA hoy")) }
        assertEquals(ResearchFailureKind.FORMAT, error.kind)
    }

    @Test
    fun realDiscoveryRequiresAndPreservesSources() = runTest {
        val response = """
            {"events":[{
              "id":"nba-real-1","sportId":"basketball","competitionId":"nba","name":"A vs B",
              "participants":[{"id":"a","name":"A","kind":"TEAM","role":"home"}],
              "startsAt":"2026-11-17T20:00:00-05:00","timeZone":"America/Bogota","venue":null,
              "status":"SCHEDULED","format":"TEAM","context":"Real","retrievedAt":"$now","isDemo":false,
              "competitionName":"NBA","sources":[{"id":"src","name":"NBA","url":"https://www.nba.com/","publishedAt":null,"consultedAt":"$now","cutoffAt":"$now","isDemo":false}]
            }]}
        """.trimIndent()
        val events = gateway(response = response).discoverEvents(DiscoveryQuery("NBA hoy"))
        assertEquals(1, events.size)
        assertFalse(events.single().isDemo)
        assertEquals("https://www.nba.com/", events.single().sources.single().url)
    }

    @Test
    fun realResearchRejectsDemoPayload() = runTest {
        val response = """{"requestId":"r","observations":[],"sources":[],"factors":[],"receivedAt":"$now","isDemo":true}"""
        val error = expectFailure { gateway(response = response).researchQuestion(request()) }
        assertEquals(ResearchFailureKind.FORMAT, error.kind)
    }

    private fun gateway(endpoint: String = "http://localhost:8787", response: String) = OpenAIResearchGateway(
        endpointProvider = { endpoint },
        nowProvider = { now },
        timeZoneProvider = { "America/Bogota" },
        transport = ResearchTransport { _, _, _ -> response },
        allowInsecureLocalhost = true,
    )

    private fun request(): ResearchRequest {
        val event = DemoData.events.first().copy(isDemo = false)
        return ResearchRequest(
            id = "r",
            eventId = event.id,
            question = DemoData.questions(event).first(),
            requestedAt = now,
            cutoffAt = now,
            event = event,
        )
    }

    private suspend fun expectFailure(block: suspend () -> Unit): ResearchGatewayException {
        return try {
            block()
            fail("Expected ResearchGatewayException")
            error("unreachable")
        } catch (error: ResearchGatewayException) {
            error
        }
    }
}
