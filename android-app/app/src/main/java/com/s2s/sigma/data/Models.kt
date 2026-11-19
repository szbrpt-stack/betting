package com.s2s.sigma.data

import kotlinx.serialization.Serializable

@Serializable
data class Sport(
    val id: String,
    val name: String,
    val icon: String,
    val isFavorite: Boolean = false,
)

@Serializable
data class Competition(
    val id: String,
    val sportId: String,
    val name: String,
    val region: String? = null,
    val season: String? = null,
    val isDemo: Boolean = true,
)

@Serializable
enum class ParticipantKind { TEAM, INDIVIDUAL, CONSTRUCTOR }

@Serializable
data class Participant(
    val id: String,
    val name: String,
    val kind: ParticipantKind,
    val role: String? = null,
)

@Serializable
enum class EventStatus { SCHEDULED, LIVE, FINISHED, POSTPONED, CANCELLED }

@Serializable
enum class EventFormat { TEAM, INDIVIDUAL, MULTI_PARTICIPANT }

@Serializable
data class SportEvent(
    val id: String,
    val sportId: String,
    val competitionId: String,
    val name: String,
    val participants: List<Participant>,
    val startsAt: String,
    val timeZone: String,
    val venue: String? = null,
    val status: EventStatus,
    val format: EventFormat,
    val context: String? = null,
    val retrievedAt: String,
    val isDemo: Boolean = true,
    val competitionName: String? = null,
    val sources: List<EvidenceSource> = emptyList(),
)

@Serializable
data class TargetDefinition(
    val variable: String,
    val unit: String,
    val horizon: String,
    val evaluationCriteria: String,
    val operator: String = "none",
    val threshold: Double? = null,
)

@Serializable
data class SigmaQuestion(
    val id: String,
    val eventId: String,
    val text: String,
    val target: TargetDefinition,
    val custom: Boolean = false,
)

@Serializable
data class ResearchRequest(
    val id: String,
    val eventId: String,
    val question: SigmaQuestion,
    val requestedAt: String,
    val cutoffAt: String,
    val event: SportEvent? = null,
)

@Serializable
data class EvidenceSource(
    val id: String,
    val name: String,
    val url: String,
    val publishedAt: String? = null,
    val consultedAt: String,
    val cutoffAt: String,
    val isDemo: Boolean = true,
)

@Serializable
data class Observation(
    val id: String,
    val eventId: String,
    val subjectId: String,
    val variable: String,
    val value: Double?,
    val unit: String,
    val observationPeriod: String,
    val completedAt: String,
    val sourceId: String,
    val consultedAt: String,
    val cutoffAt: String,
)

@Serializable
enum class EvidenceState { VALID, STALE, CONFLICTING, MISSING, DEMO }

@Serializable
enum class SigmaFactorFamily {
    PARTICIPANTS,
    OFFICIALS,
    PUBLIC,
    TRAVEL,
    SCHEDULE_AND_REST,
    PHYSICAL_ENVIRONMENT,
    TACTICS_AND_STRATEGY,
    LOAD_AND_RECOVERY,
    COMPETITION_FORMAT,
    OBSERVABLE_BEHAVIOR,
    OPERATIONS_AND_EQUIPMENT,
    RANDOMNESS,
}

@Serializable
data class SigmaFactor(
    val family: SigmaFactorFamily,
    val description: String,
    val evidenceState: EvidenceState,
    val sourceIds: List<String> = emptyList(),
)

@Serializable
data class DescriptiveStatistics(
    val sampleSize: Int,
    val requestedSampleSize: Int,
    val mean: Double? = null,
    val median: Double? = null,
    val sampleStandardDeviation: Double? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val unit: String,
)

@Serializable
enum class SigmaVerdict { SUPPORTED, CONDITIONAL, DESCRIPTIVE, INSUFFICIENT }

@Serializable
data class SigmaAnalysis(
    val id: String,
    val eventId: String,
    val question: SigmaQuestion,
    val statistics: DescriptiveStatistics,
    val estimate: Double? = null,
    val uncertaintyInterval: List<Double>? = null,
    val factors: List<SigmaFactor>,
    val scenarios: List<String>,
    val sources: List<EvidenceSource>,
    val cutoffAt: String,
    val limitations: List<String>,
    val missingData: List<String>,
    val verdict: SigmaVerdict,
    val validatedModel: Boolean = false,
    val isDemo: Boolean = true,
)

@Serializable
data class ResearchResponse(
    val requestId: String,
    val observations: List<Observation>,
    val sources: List<EvidenceSource>,
    val factors: List<SigmaFactor>,
    val receivedAt: String,
    val isDemo: Boolean = true,
)

data class EventFilter(
    val sportId: String? = null,
    val competitionId: String? = null,
    val status: EventStatus? = null,
    val participantQuery: String = "",
)
