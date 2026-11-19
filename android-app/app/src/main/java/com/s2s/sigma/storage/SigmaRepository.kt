package com.s2s.sigma.storage

import com.s2s.sigma.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SigmaRepository(private val dao: SigmaDao) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    val favorites: Flow<List<FavoriteEntity>> = dao.observeFavorites()
    val analyses: Flow<List<AnalysisEntity>> = dao.observeAnalyses()
    val imports: Flow<List<ImportDocumentEntity>> = dao.observeImportDocuments()

    fun decodeAnalysis(item: AnalysisEntity): SigmaAnalysis? =
        runCatching { json.decodeFromString<SigmaAnalysis>(item.payloadJson) }.getOrNull()

    fun decodeImport(item: ImportDocumentEntity): ResearchImportDocument? =
        runCatching { json.decodeFromString<ResearchImportDocument>(item.payloadJson) }.getOrNull()

    suspend fun seedDemoCatalog() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        dao.upsertCompetitions(DemoData.competitions.map {
            CompetitionEntity(it.id, it.sportId, it.name, it.region, it.season, DemoData.consulted)
        })
        dao.upsertEvents(DemoData.events.map {
            EventEntity(
                it.id, it.sportId, it.competitionId, it.name, it.startsAt,
                it.status.name, json.encodeToString(it), it.retrievedAt,
            )
        })
    }

    suspend fun setFavorite(kind: String, referenceId: String, favorite: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (favorite) dao.upsertFavorite(FavoriteEntity(kind, referenceId, DemoData.consulted))
        else dao.deleteFavorite(kind, referenceId)
    }

    suspend fun saveDiscoveredEvents(events: List<SportEvent>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        dao.upsertEvents(events.map {
            EventEntity(
                it.id, it.sportId, it.competitionId, it.name, it.startsAt,
                it.status.name, json.encodeToString(it), it.retrievedAt,
            )
        })
    }

    suspend fun save(request: ResearchRequest, analysis: SigmaAnalysis) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        dao.upsertResearch(
            ResearchEntity(request.id, request.eventId, request.question.id, json.encodeToString(request), request.requestedAt),
        )
        dao.upsertSources(analysis.sources.map {
            SourceEntity(it.id, request.id, it.name, it.url, it.consultedAt, it.cutoffAt, json.encodeToString(it))
        })
        dao.upsertAnalysis(
            AnalysisEntity(
                analysis.id,
                analysis.eventId,
                analysis.question.id,
                analysis.verdict.name,
                analysis.cutoffAt,
                DemoData.consulted,
                json.encodeToString(analysis),
            ),
        )
    }

    suspend fun saveImport(
        document: ResearchImportDocument,
        fileName: String,
        importedAt: String,
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val event = document.event
        val questions = document.investigations.map { it.request.question }
        val responses = document.investigations.map { it.response }
        dao.saveImportedResearch(
            document = ImportDocumentEntity(
                documentId = document.documentId,
                schemaVersion = document.schemaVersion,
                eventId = event.id,
                importedAt = importedAt,
                fileName = fileName,
                provenanceJson = json.encodeToString(document.provenance),
                payloadJson = json.encodeToString(document),
            ),
            event = EventEntity(
                event.id,
                event.sportId,
                event.competitionId,
                event.name,
                event.startsAt,
                event.status.name,
                json.encodeToString(event),
                event.retrievedAt,
            ),
            questions = questions.map {
                ImportedQuestionEntity(it.id, document.documentId, event.id, json.encodeToString(it))
            },
            research = document.investigations.map {
                val request = it.request
                ResearchEntity(request.id, request.eventId, request.question.id, json.encodeToString(request), request.requestedAt)
            },
            sources = document.investigations.flatMap { item ->
                item.response.sources.map { source ->
                    SourceEntity(
                        source.id,
                        item.request.id,
                        source.name,
                        source.url,
                        source.consultedAt,
                        source.cutoffAt,
                        json.encodeToString(source),
                    )
                }
            }.distinctBy { it.id },
            observations = document.investigations.flatMap { item ->
                item.response.observations.map { observation ->
                    ImportedObservationEntity(
                        observation.id,
                        document.documentId,
                        item.request.id,
                        observation.eventId,
                        observation.sourceId,
                        json.encodeToString(observation),
                    )
                }
            },
        )
    }

    suspend fun saveDailyCache(key: String, date: String, payload: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        dao.upsertCache(
            DailyCacheEntity(key, date, DemoData.consulted, "2026-11-18T00:00:00-05:00", payload),
        )
    }
}
