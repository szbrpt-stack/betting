package com.s2s.sigma.research

import com.s2s.sigma.data.*

data class DiscoveryQuery(
    val text: String = "",
    val sportId: String? = null,
    val competitionId: String? = null,
    val status: EventStatus? = null,
)

interface ResearchGateway {
    suspend fun discoverEvents(query: DiscoveryQuery): List<SportEvent>
    suspend fun generateQuestions(event: SportEvent): List<SigmaQuestion>
    suspend fun researchQuestion(request: ResearchRequest): ResearchResponse
    suspend fun refreshEvidence(request: ResearchRequest): ResearchResponse
}

class MockResearchGateway : ResearchGateway {
    override suspend fun discoverEvents(query: DiscoveryQuery): List<SportEvent> {
        return DemoData.events.applyFilter(
            EventFilter(query.sportId, query.competitionId, query.status, query.text),
        )
    }

    override suspend fun generateQuestions(event: SportEvent): List<SigmaQuestion> {
        return DemoData.questions(event)
    }

    override suspend fun researchQuestion(request: ResearchRequest): ResearchResponse {
        val event = DemoData.events.first { it.id == request.eventId }
        return ResearchResponse(
            requestId = request.id,
            observations = DemoData.demoObservations(event, request.question),
            sources = DemoData.demoSources(request.question),
            factors = listOf(
                SigmaFactor(
                    SigmaFactorFamily.PARTICIPANTS,
                    "Identidad de participantes verificada solamente dentro del conjunto sintético.",
                    EvidenceState.DEMO,
                    listOf("demo-methodology"),
                ),
                SigmaFactor(
                    SigmaFactorFamily.COMPETITION_FORMAT,
                    "Formato del evento registrado en la ficha DEMO.",
                    EvidenceState.DEMO,
                    listOf("demo-methodology"),
                ),
                SigmaFactor(
                    SigmaFactorFamily.RANDOMNESS,
                    "La variación aleatoria se reconoce sin asignarle peso arbitrario.",
                    EvidenceState.DEMO,
                ),
            ),
            receivedAt = DemoData.consulted,
        )
    }

    override suspend fun refreshEvidence(request: ResearchRequest): ResearchResponse = researchQuestion(request)
}
