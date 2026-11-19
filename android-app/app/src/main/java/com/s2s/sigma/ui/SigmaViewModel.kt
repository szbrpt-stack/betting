package com.s2s.sigma.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s2s.sigma.data.*
import com.s2s.sigma.domain.SigmaEngine
import com.s2s.sigma.research.DiscoveryQuery
import com.s2s.sigma.research.MockResearchGateway
import com.s2s.sigma.research.OpenAIResearchGateway
import com.s2s.sigma.research.ResearchGateway
import com.s2s.sigma.storage.AppDatabase
import com.s2s.sigma.storage.SigmaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

enum class ResearchMode { DEMO, OPENAI }

enum class ResearchPhase { PREPARING, RETRIEVING, VALIDATING, PROCESSING, COMPLETE, ERROR }

data class ResearchProgress(
    val eventId: String,
    val questionId: String,
    val phase: ResearchPhase = ResearchPhase.PREPARING,
    val running: Boolean = false,
    val sourceCount: Int = 0,
    val observationCount: Int = 0,
    val verdict: SigmaVerdict? = null,
    val error: String? = null,
)

data class DiscoveryState(
    val running: Boolean = false,
    val query: String = "",
    val resultCount: Int? = null,
    val error: String? = null,
)

data class ImportPreviewState(
    val loading: Boolean = false,
    val fileName: String? = null,
    val document: ResearchImportDocument? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val importedEventId: String? = null,
)

class SigmaViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("s2s_sigma_prefs", Context.MODE_PRIVATE)
    private val zoneId = ZoneId.of("America/Bogota")

    val sports = DemoData.sports

    private val _researchMode = MutableStateFlow(
        runCatching { ResearchMode.valueOf(prefs.getString("research_mode", ResearchMode.DEMO.name)!!) }
            .getOrDefault(ResearchMode.DEMO),
    )
    val researchMode: StateFlow<ResearchMode> = _researchMode.asStateFlow()

    private val _connectorEndpoint = MutableStateFlow(prefs.getString("connector_endpoint", "") ?: "")
    val connectorEndpoint: StateFlow<String> = _connectorEndpoint.asStateFlow()

    private val _competitions = MutableStateFlow(
        if (_researchMode.value == ResearchMode.DEMO) DemoData.competitions else emptyList(),
    )
    val competitionsState: StateFlow<List<Competition>> = _competitions.asStateFlow()
    val competitions: List<Competition> get() = _competitions.value

    private var sessionCompetitions: List<Competition> = _competitions.value

    private val _events = MutableStateFlow(
        if (_researchMode.value == ResearchMode.DEMO) DemoData.events else emptyList(),
    )
    val eventsState: StateFlow<List<SportEvent>> = _events.asStateFlow()
    val events: List<SportEvent> get() = _events.value

    private var sessionEvents: List<SportEvent> = _events.value
    private var importedDocuments: List<ResearchImportDocument> = emptyList()
    private var importedResearchByQuestion: Map<String, ImportedQuestionResearch> = emptyMap()

    private val _discoveryState = MutableStateFlow(DiscoveryState())
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private val mockGateway = MockResearchGateway()
    private val openAIGateway = OpenAIResearchGateway(
        endpointProvider = { _connectorEndpoint.value },
        nowProvider = ::now,
        timeZoneProvider = { zoneId.id },
    )
    private val activeGateway: ResearchGateway
        get() = when (_researchMode.value) {
            ResearchMode.DEMO -> mockGateway
            ResearchMode.OPENAI -> openAIGateway
        }

    private val repository = SigmaRepository(AppDatabase.get(application).sigmaDao())

    private val _favoriteIds = MutableStateFlow(DemoData.sports.filter { it.isFavorite }.map { it.id }.toSet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _analyses = MutableStateFlow<Map<String, SigmaAnalysis>>(emptyMap())
    val analyses: StateFlow<Map<String, SigmaAnalysis>> = _analyses.asStateFlow()

    private val _customQuestions = MutableStateFlow<Map<String, List<SigmaQuestion>>>(emptyMap())
    val customQuestions: StateFlow<Map<String, List<SigmaQuestion>>> = _customQuestions.asStateFlow()

    private val _progress = MutableStateFlow<ResearchProgress?>(null)
    val progress: StateFlow<ResearchProgress?> = _progress.asStateFlow()

    private val _importPreview = MutableStateFlow(ImportPreviewState())
    val importPreview: StateFlow<ImportPreviewState> = _importPreview.asStateFlow()

    init {
        viewModelScope.launch { repository.seedDemoCatalog() }
        viewModelScope.launch {
            repository.favorites.collectLatest { rows ->
                _favoriteIds.value = DemoData.sports.filter { it.isFavorite }.map { it.id }.toSet() +
                    rows.map { it.referenceId }
            }
        }
        viewModelScope.launch {
            repository.analyses.collectLatest { rows ->
                _analyses.value = rows.mapNotNull(repository::decodeAnalysis).associateBy { it.question.id }
            }
        }
        viewModelScope.launch {
            repository.imports.collectLatest { rows ->
                importedDocuments = rows.mapNotNull(repository::decodeImport)
                importedResearchByQuestion = importedDocuments
                    .flatMap { it.investigations }
                    .associateBy { it.request.question.id }
                refreshVisibleCatalog()
            }
        }
    }

    fun event(id: String) = _events.value.firstOrNull { it.id == id }
    fun competition(id: String) = _competitions.value.firstOrNull { it.id == id }
    fun sport(id: String) = sports.firstOrNull { it.id == id }
    fun questions(eventId: String): List<SigmaQuestion> {
        val imported = importedDocuments
            .filter { it.event.id == eventId }
            .flatMap { it.investigations }
            .map { it.request.question }
        val defaults = if (imported.isNotEmpty()) imported else event(eventId)?.let(DemoData::questions).orEmpty()
        return (defaults + _customQuestions.value[eventId].orEmpty()).distinctBy { it.id }
    }

    fun setResearchMode(mode: ResearchMode) {
        _researchMode.value = mode
        prefs.edit().putString("research_mode", mode.name).apply()
        sessionEvents = if (mode == ResearchMode.DEMO) DemoData.events else emptyList()
        sessionCompetitions = if (mode == ResearchMode.DEMO) DemoData.competitions else emptyList()
        refreshVisibleCatalog()
        _discoveryState.value = DiscoveryState()
        _progress.value = null
    }

    fun setConnectorEndpoint(endpoint: String) {
        _connectorEndpoint.value = endpoint.trim().trimEnd('/')
        prefs.edit().putString("connector_endpoint", _connectorEndpoint.value).apply()
    }

    fun discoverEvents(queryText: String) {
        val query = queryText.trim()
        if (query.isBlank() || _discoveryState.value.running) return
        viewModelScope.launch(Dispatchers.IO) {
            _discoveryState.value = DiscoveryState(running = true, query = query)
            try {
                val discovered = activeGateway.discoverEvents(DiscoveryQuery(text = query))
                if (_researchMode.value == ResearchMode.OPENAI) {
                    sessionEvents = discovered
                    sessionCompetitions = discovered.distinctBy { it.competitionId }.map { event ->
                        Competition(
                            id = event.competitionId,
                            sportId = event.sportId,
                            name = event.competitionName ?: event.competitionId.uppercase(),
                            isDemo = false,
                        )
                    }
                } else {
                    sessionEvents = discovered
                    sessionCompetitions = DemoData.competitions
                }
                refreshVisibleCatalog()
                repository.saveDiscoveredEvents(discovered)
                _discoveryState.value = DiscoveryState(query = query, resultCount = discovered.size)
            } catch (error: Exception) {
                _discoveryState.value = DiscoveryState(query = query, error = error.message ?: "Error de investigación")
            }
        }
    }

    fun addCustomQuestion(eventId: String, text: String, variable: String, unit: String): SigmaQuestion {
        val question = SigmaQuestion(
            id = "$eventId-custom-${text.hashCode().toUInt()}",
            eventId = eventId,
            text = text.trim(),
            target = TargetDefinition(
                variable = variable.trim(),
                unit = unit.trim(),
                horizon = event(eventId)?.name ?: "Evento seleccionado",
                evaluationCriteria = "Solo evidencia comparable anterior al corte; resultado descriptivo si es viable",
            ),
            custom = true,
        )
        _customQuestions.value = _customQuestions.value +
            (eventId to (_customQuestions.value[eventId].orEmpty() + question))
        return question
    }

    fun loadImport(uri: Uri) {
        if (_importPreview.value.loading) return
        viewModelScope.launch(Dispatchers.IO) {
            _importPreview.value = ImportPreviewState(loading = true)
            val application = getApplication<Application>()
            val fileName = application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment ?: "investigacion.json"
            val result = runCatching {
                val raw = application.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val text = reader.readText()
                    require(text.length <= 5_000_000) { "El archivo supera el límite de 5 MB." }
                    text
                } ?: error("No fue posible abrir el archivo seleccionado.")
                ResearchImportContract.decodeAndValidate(raw)
            }.getOrElse { error -> ImportValidationResult(errors = listOf(error.message ?: "No fue posible leer el archivo.")) }
            _importPreview.value = ImportPreviewState(
                fileName = fileName,
                document = result.document,
                errors = result.errors,
                warnings = result.warnings,
            )
        }
    }

    fun clearImportPreview() {
        _importPreview.value = ImportPreviewState()
    }

    suspend fun confirmImport(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val state = _importPreview.value
            require(state.errors.isEmpty()) { "La investigación contiene errores de validación." }
            val document = requireNotNull(state.document) { "No hay una investigación lista para importar." }
            repository.saveImport(document, state.fileName ?: "investigacion.json", now())
            importedDocuments = (listOf(document) + importedDocuments).distinctBy { it.documentId }
            importedResearchByQuestion = importedDocuments
                .flatMap { it.investigations }
                .associateBy { it.request.question.id }
            refreshVisibleCatalog()
            _importPreview.value = state.copy(importedEventId = document.event.id)
            document.event.id
        }
    }

    fun toggleFavorite(kind: String, id: String) {
        val newValue = id !in _favoriteIds.value
        _favoriteIds.value = if (newValue) _favoriteIds.value + id else _favoriteIds.value - id
        viewModelScope.launch { repository.setFavorite(kind, id, newValue) }
    }

    suspend fun runResearch(eventId: String, questionId: String): Result<SigmaAnalysis> = withContext(Dispatchers.IO) {
        runCatching {
            val event = requireNotNull(event(eventId)) { "Evento no encontrado" }
            val question = requireNotNull(questions(eventId).firstOrNull { it.id == questionId }) { "Pregunta no encontrada" }
            _progress.value = ResearchProgress(eventId, questionId, phase = ResearchPhase.PREPARING, running = true)
            val importedResearch = importedResearchByQuestion[questionId]
            val requestTime = now()
            val request = importedResearch?.request ?: ResearchRequest(
                id = "request-${UUID.randomUUID()}", eventId = eventId, question = question,
                requestedAt = requestTime, cutoffAt = requestTime, event = event,
            )
            _progress.value = _progress.value?.copy(phase = ResearchPhase.RETRIEVING)
            val response = importedResearch?.response ?: activeGateway.researchQuestion(request)
            if (importedResearch == null && _researchMode.value == ResearchMode.OPENAI && response.isDemo) {
                error("El conector intentó devolver datos DEMO en modo IA Real.")
            }
            _progress.value = _progress.value?.copy(
                phase = ResearchPhase.VALIDATING,
                sourceCount = response.sources.size,
                observationCount = response.observations.size,
            )
            _progress.value = _progress.value?.copy(phase = ResearchPhase.PROCESSING)
            val analysis = SigmaEngine.analyze(request, response)
            _progress.value = _progress.value?.copy(
                phase = ResearchPhase.COMPLETE,
                running = false,
                verdict = analysis.verdict,
            )
            _analyses.value = _analyses.value + (questionId to analysis)
            repository.save(request, analysis)
            analysis
        }.onFailure { error ->
            _progress.value = _progress.value?.copy(
                phase = ResearchPhase.ERROR,
                running = false,
                error = error.message ?: "La investigación falló sin detalle.",
            )
        }
    }

    private fun refreshVisibleCatalog() {
        val importedEvents = importedDocuments.map { it.event }
        _events.value = (importedEvents + sessionEvents).distinctBy { it.id }
        val importedCompetitions = importedEvents.distinctBy { it.competitionId }.map { event ->
            Competition(
                id = event.competitionId,
                sportId = event.sportId,
                name = event.competitionName ?: event.competitionId.uppercase(),
                isDemo = false,
            )
        }
        _competitions.value = (importedCompetitions + sessionCompetitions).distinctBy { it.id }
    }

    private fun now(): String = OffsetDateTime.now(zoneId).toString()
}
