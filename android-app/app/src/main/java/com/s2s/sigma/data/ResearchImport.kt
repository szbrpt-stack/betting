package com.s2s.sigma.data

import com.s2s.sigma.domain.SigmaEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

const val RESEARCH_IMPORT_SCHEMA_VERSION = 1

@Serializable
data class ImportProvenance(
    val generator: String,
    val generatedAt: String,
    val description: String? = null,
    val originalDocumentUrl: String? = null,
)

@Serializable
data class ImportedQuestionResearch(
    val request: ResearchRequest,
    val response: ResearchResponse,
)

@Serializable
data class ResearchImportDocument(
    val schemaVersion: Int,
    val documentId: String,
    val provenance: ImportProvenance,
    val event: SportEvent,
    val investigations: List<ImportedQuestionResearch>,
)

data class ImportValidationResult(
    val document: ResearchImportDocument? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val valid: Boolean get() = document != null && errors.isEmpty()
}

object ResearchImportContract {
    val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }

    fun decodeAndValidate(raw: String): ImportValidationResult {
        val document = try {
            json.decodeFromString<ResearchImportDocument>(raw)
        } catch (error: Exception) {
            return ImportValidationResult(errors = listOf("JSON inválido: ${error.message ?: "no cumple el contrato"}"))
        }
        return validate(document)
    }

    fun validate(document: ResearchImportDocument): ImportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (document.schemaVersion != RESEARCH_IMPORT_SCHEMA_VERSION) {
            errors += "schemaVersion ${document.schemaVersion} no es compatible; se requiere $RESEARCH_IMPORT_SCHEMA_VERSION"
        }
        if (document.documentId.isBlank()) errors += "documentId es obligatorio"
        validateTimestamp(document.provenance.generatedAt, "provenance.generatedAt", errors)
        if (document.provenance.generator.isBlank()) errors += "provenance.generator es obligatorio"
        document.provenance.originalDocumentUrl?.let {
            if (!it.startsWith("https://")) errors += "provenance.originalDocumentUrl debe usar HTTPS"
        }

        val event = document.event
        if (event.id.isBlank()) errors += "event.id es obligatorio"
        if (event.name.isBlank()) errors += "event.name es obligatorio"
        if (event.sportId.isBlank()) errors += "event.sportId es obligatorio"
        if (event.competitionId.isBlank()) errors += "event.competitionId es obligatorio"
        validateTimestamp(event.startsAt, "event.startsAt", errors)
        validateTimestamp(event.retrievedAt, "event.retrievedAt", errors)
        if (event.timeZone.isBlank()) errors += "event.timeZone es obligatorio"
        if (event.isDemo) errors += "El evento importado no puede estar marcado como DEMO"
        if (event.participants.isEmpty()) errors += "El evento debe contener participantes"
        if (event.participants.map { it.id }.distinct().size != event.participants.size) errors += "Hay participantes duplicados"
        event.participants.forEachIndexed { index, participant ->
            if (participant.id.isBlank()) errors += "participants[$index].id es obligatorio"
            if (participant.name.isBlank()) errors += "participants[$index].name es obligatorio"
        }
        if (document.investigations.isEmpty()) errors += "Debe existir al menos una investigación"
        if (document.investigations.map { it.request.question.id }.distinct().size != document.investigations.size) {
            errors += "Hay preguntas duplicadas"
        }

        document.investigations.forEachIndexed { index, item ->
            val prefix = "investigations[$index]"
            val request = item.request
            val response = item.response
            if (request.id.isBlank()) errors += "$prefix.request.id es obligatorio"
            if (request.eventId != event.id) errors += "$prefix.request.eventId no coincide con event.id"
            if (request.event?.id != event.id) errors += "$prefix.request.event debe contener el evento importado"
            if (request.question.id.isBlank()) errors += "$prefix.request.question.id es obligatorio"
            if (request.question.eventId != event.id) errors += "$prefix.request.question.eventId no coincide con event.id"
            if (request.question.text.isBlank()) errors += "$prefix.request.question.text es obligatorio"
            if (request.question.target.variable.isBlank()) errors += "$prefix.request.question.target.variable es obligatoria"
            if (request.question.target.unit.isBlank()) errors += "$prefix.request.question.target.unit es obligatoria"
            val requestedAt = validateTimestamp(request.requestedAt, "$prefix.request.requestedAt", errors)
            val cutoffAt = validateTimestamp(request.cutoffAt, "$prefix.request.cutoffAt", errors)
            if (requestedAt != null && cutoffAt != null && cutoffAt.isAfter(requestedAt)) {
                errors += "$prefix.request.cutoffAt no puede ser posterior a requestedAt"
            }
            if (response.requestId != request.id) errors += "$prefix.response.requestId no coincide con request.id"
            if (response.isDemo) errors += "$prefix.response no puede estar marcada como DEMO"
            validateTimestamp(response.receivedAt, "$prefix.response.receivedAt", errors)

            val sourceIds = response.sources.map { it.id }.toSet()
            if (sourceIds.size != response.sources.size) errors += "$prefix contiene fuentes duplicadas"
            if (response.sources.isEmpty()) errors += "$prefix debe contener fuentes"
            response.sources.forEachIndexed { sourceIndex, source ->
                if (source.id.isBlank()) errors += "$prefix.sources[$sourceIndex].id es obligatorio"
                if (source.name.isBlank()) errors += "$prefix.sources[$sourceIndex].name es obligatorio"
                if (!source.url.startsWith("https://")) errors += "$prefix.sources[$sourceIndex].url debe usar HTTPS"
                if (source.isDemo) errors += "$prefix.sources[$sourceIndex] no puede ser DEMO"
                validateTimestamp(source.consultedAt, "$prefix.sources[$sourceIndex].consultedAt", errors)
                validateTimestamp(source.cutoffAt, "$prefix.sources[$sourceIndex].cutoffAt", errors)
                source.publishedAt?.let { validateTimestamp(it, "$prefix.sources[$sourceIndex].publishedAt", errors) }
            }
            response.observations.forEachIndexed { observationIndex, observation ->
                val label = "$prefix.observations[$observationIndex]"
                if (observation.id.isBlank()) errors += "$label.id es obligatorio"
                if (observation.eventId != event.id) errors += "$label.eventId no coincide con event.id"
                if (observation.subjectId.isBlank()) errors += "$label.subjectId es obligatorio"
                if (observation.sourceId !in sourceIds) errors += "$label.sourceId no existe en response.sources"
                if (observation.variable != request.question.target.variable) {
                    errors += "$label.variable no coincide con la variable objetivo '${request.question.target.variable}'"
                }
                if (observation.value?.isFinite() == false) errors += "$label.value no es finito"
                if (observation.value != null && SigmaEngine.normalizeValue(
                        observation.value,
                        observation.unit,
                        request.question.target.unit,
                    ) == null
                ) {
                    errors += "$label.unit '${observation.unit}' no es compatible con '${request.question.target.unit}'"
                }
                validateTimestamp(observation.completedAt, "$label.completedAt", errors)
                validateTimestamp(observation.consultedAt, "$label.consultedAt", errors)
                validateTimestamp(observation.cutoffAt, "$label.cutoffAt", errors)
            }
            if (response.observations.isEmpty()) warnings += "$prefix no contiene observaciones"
            response.factors.flatMap { it.sourceIds }.forEach { sourceId ->
                if (sourceId !in sourceIds) errors += "$prefix contiene un factor con sourceId inexistente: $sourceId"
            }

            if (cutoffAt != null && errors.none { it.startsWith(prefix) }) {
                runCatching { SigmaEngine.validateSources(response.sources, request.cutoffAt) }
                    .onSuccess { validation -> errors += validation.errors.map { "$prefix: $it" } }
                    .onFailure { errors += "$prefix contiene fechas de fuente inválidas" }
                runCatching { SigmaEngine.validateTemporal(response.observations, request.cutoffAt) }
                    .onSuccess { validation -> errors += validation.errors.map { "$prefix: $it" } }
                    .onFailure { errors += "$prefix contiene fechas de observación inválidas" }
            }
        }

        return ImportValidationResult(document = document, errors = errors.distinct(), warnings = warnings.distinct())
    }

    private fun validateTimestamp(
        value: String,
        label: String,
        errors: MutableList<String>,
    ): OffsetDateTime? = try {
        OffsetDateTime.parse(value)
    } catch (_: Exception) {
        errors += "$label debe ser una fecha ISO-8601 con zona horaria"
        null
    }
}
