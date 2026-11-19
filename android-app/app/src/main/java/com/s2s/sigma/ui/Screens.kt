package com.s2s.sigma.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s2s.sigma.data.*
import com.s2s.sigma.ui.theme.*
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val screenPadding = 20.dp

@Composable
fun HomeScreen(
    vm: SigmaViewModel,
    onExplore: () -> Unit,
    onEvent: (String) -> Unit,
    onHistory: () -> Unit,
    onImport: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val analyses by vm.analyses.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val events by vm.eventsState.collectAsState()
    val discovery by vm.discoveryState.collectAsState()
    val filtered = remember(query, events) {
        events.filter { event ->
            query.isBlank() || event.name.contains(query, true) ||
                event.participants.any { it.name.contains(query, true) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = screenPadding, end = screenPadding, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HomeHero(
                vm = vm,
                query = query,
                onQueryChange = { query = it },
                onSearch = { vm.discoverEvents(query) },
                searching = discovery.running,
                onExplore = onExplore,
                onHistory = onHistory,
                onImport = onImport,
            )
        }
        discovery.error?.let { message -> item { ErrorPanel(message) { vm.discoverEvents(query) } } }
        discovery.resultCount?.let { count ->
            item {
                Text(
                    if (count == 0) "La consulta terminó sin eventos verificables." else "$count eventos recibidos y guardados localmente.",
                    color = if (count == 0) Warning else Success,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (query.isNotBlank()) {
            item { SectionTitle("Resultados", eyebrow = "${filtered.size} coincidencias") }
            items(filtered, key = { it.id }) { event -> EventCard(event, vm, onEvent) }
            if (filtered.isEmpty()) {
                item { EmptyState("Sin coincidencias", "Prueba con otro participante o evento.") }
            }
        } else {
            item { SectionTitle("Próximo evento", eyebrow = "Agenda de hoy", actionLabel = "Ver todos", onAction = onExplore) }
            if (events.isNotEmpty()) item { FeaturedEventCard(events.first(), vm, onEvent) }
            else item { EmptyState("Catálogo real vacío", "Escribe una consulta y pulsa buscar para investigar con OpenAI.") }
            item { SectionTitle("Tus deportes", eyebrow = "Accesos rápidos") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(vm.sports.filter { it.id in favorites }, key = { it.id }) { sport ->
                        QuickSportCard(sport, onExplore)
                    }
                }
            }
            item { SectionTitle("Investigación reciente", eyebrow = "SIGMA-Q", actionLabel = "Historial", onAction = onHistory) }
            item {
                val recent = analyses.values.lastOrNull()
                if (recent == null) {
                    SigmaCard(modifier = Modifier.fillMaxWidth(), accent = Purple) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Default.QueryStats, Purple)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Tu primer análisis comienza aquí", style = MaterialTheme.typography.titleMedium)
                                Text("Selecciona un evento y una pregunta medible.", color = SecondaryText)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Purple)
                        }
                    }
                } else {
                    AnalysisSummaryCard(recent, vm, onHistory)
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    vm: SigmaViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    onExplore: () -> Unit,
    onHistory: () -> Unit,
    onImport: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(
            Brush.linearGradient(listOf(ElevatedPanel, DarkPanel, Color(0xFF063247))),
        ).padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SigmaMark(46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("S2S Sigma", style = MaterialTheme.typography.titleLarge)
                    Text("Inteligencia deportiva trazable", style = MaterialTheme.typography.bodySmall, color = CyanSoft)
                }
                ResearchModeTag(vm)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Decisiones con evidencia,\nno con intuición.", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Explora eventos y transforma observaciones verificables en análisis comprensibles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText,
                )
            }
            SigmaSearchField(
                query,
                onQueryChange,
                "¿Qué partidos de la NBA hay hoy?",
                onSubmit = onSearch,
                loading = searching,
            )
            Button(onClick = onSearch, enabled = query.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.TravelExplore, null)
                Spacer(Modifier.width(8.dp))
                Text(if (searching) "Investigando…" else "Buscar eventos")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onExplore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Explore, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Explorar")
                }
                FilledTonalButton(onClick = onHistory) {
                    Icon(Icons.Default.History, "Historial")
                }
            }
            FilledTonalButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text("Importar investigación JSON")
            }
        }
    }
}

@Composable
private fun FeaturedEventCard(event: SportEvent, vm: SigmaViewModel, onEvent: (String) -> Unit) {
    SigmaCard(modifier = Modifier.fillMaxWidth(), onClick = { onEvent(event.id) }, accent = Cyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(vm.competition(event.competitionId)?.name ?: event.competitionId, color = Cyan, style = MaterialTheme.typography.labelLarge)
                Text(formatDateTime(event.startsAt), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            StatusPill(event.status)
        }
        Spacer(Modifier.height(4.dp))
        ParticipantMatchup(event.participants)
        HorizontalDivider(color = Border.copy(alpha = .5f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, null, tint = MutedText, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(event.venue ?: "Sede por confirmar", color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("Analizar", color = Cyan, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Default.ChevronRight, null, tint = Cyan)
        }
    }
}

@Composable
private fun QuickSportCard(sport: Sport, onClick: () -> Unit) {
    val accent = sportAccent(sport.id)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(132.dp),
        color = accent.copy(alpha = .1f),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .3f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(sportIcon(sport.id), accent)
            Text(sport.name, style = MaterialTheme.typography.titleMedium)
            Text("Explorar", style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

@Composable
fun SportsScreen(vm: SigmaViewModel, onSport: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val favorites by vm.favoriteIds.collectAsState()
    val sports = vm.sports.filter { it.name.contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        SigmaHeader("Explorar", "Deporte → competición → evento", trailing = { ResearchModeTag(vm) })
        SigmaSearchField(query, { query = it }, "Buscar deporte", Modifier.padding(horizontal = screenPadding))
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(148.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = screenPadding, end = screenPadding, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sports, key = { it.id }) { sport ->
                SportGridCard(
                    sport = sport,
                    favorite = sport.id in favorites,
                    onClick = { onSport(sport.id) },
                    onFavorite = { vm.toggleFavorite("sport", sport.id) },
                )
            }
        }
    }
}

@Composable
private fun SportGridCard(
    sport: Sport,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    val accent = sportAccent(sport.id)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 166.dp),
        color = DarkPanel,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .3f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(sportIcon(sport.id), accent)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        "Favorito",
                        tint = if (favorite) Warning else MutedText,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(sport.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ver competiciones", style = MaterialTheme.typography.bodySmall, color = accent, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun CompetitionsScreen(
    vm: SigmaViewModel,
    sportId: String,
    onBack: () -> Unit,
    onCompetition: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val competitionCatalog by vm.competitionsState.collectAsState()
    val sport = vm.sport(sportId)
    val competitions = competitionCatalog.filter { it.sportId == sportId && it.name.contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        SigmaHeader("Competiciones", sport?.name, onBack)
        SigmaSearchField(query, { query = it }, "Buscar competición", Modifier.padding(horizontal = screenPadding))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(sportIcon(sportId), sportAccent(sportId))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("${competitions.size} competiciones", style = MaterialTheme.typography.titleMedium)
                        Text("Sin mezclar disciplinas", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(competitions, key = { it.id }) { competition ->
                CompetitionCard(competition, sportAccent(sportId)) { onCompetition(competition.id) }
            }
            if (competitions.isEmpty()) item { EmptyState("Sin competiciones", "No hay coincidencias para esta disciplina.") }
        }
    }
}

@Composable
private fun CompetitionCard(competition: Competition, accent: Color, onClick: () -> Unit) {
    SigmaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.EmojiEvents, accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(competition.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelPill(competition.region ?: "Global", accent)
                    competition.season?.let { LabelPill(it, SecondaryText) }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MutedText)
        }
    }
}

@Composable
fun EventsScreen(
    vm: SigmaViewModel,
    competitionId: String,
    onBack: () -> Unit,
    onEvent: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<EventStatus?>(null) }
    val eventCatalog by vm.eventsState.collectAsState()
    val competition = vm.competition(competitionId)
    val events = eventCatalog.filter { event ->
        event.competitionId == competitionId &&
            (selectedStatus == null || event.status == selectedStatus) &&
            (query.isBlank() || event.name.contains(query, true) || event.participants.any { it.name.contains(query, true) })
    }
    Column(Modifier.fillMaxSize()) {
        SigmaHeader("Eventos", competition?.name, onBack)
        SigmaSearchField(query, { query = it }, "Participante o evento", Modifier.padding(horizontal = screenPadding))
        LazyRow(
            contentPadding = PaddingValues(horizontal = screenPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { CompactFilter("Todos", selectedStatus == null) { selectedStatus = null } }
            items(EventStatus.entries) { status ->
                CompactFilter(statusLabel(status), selectedStatus == status) { selectedStatus = status }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = screenPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(events, key = { it.id }) { event -> EventCard(event, vm, onEvent) }
            if (events.isEmpty()) item { EmptyState("No encontramos eventos", "Cambia el filtro o busca otro participante.") }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun CompactFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Cyan.copy(alpha = .14f),
            selectedLabelColor = Cyan,
        ),
    )
}

@Composable
private fun EventCard(event: SportEvent, vm: SigmaViewModel, onEvent: (String) -> Unit) {
    val favorites by vm.favoriteIds.collectAsState()
    SigmaCard(modifier = Modifier.fillMaxWidth(), onClick = { onEvent(event.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatTime(event.startsAt), style = MaterialTheme.typography.titleLarge, color = Cyan)
                Text(formatDay(event.startsAt), style = MaterialTheme.typography.labelMedium, color = SecondaryText)
            }
            Box(Modifier.width(1.dp).height(42.dp).background(Border))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.competition(event.competitionId)?.name ?: event.competitionId, style = MaterialTheme.typography.labelMedium, color = MutedText)
                Text(event.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.toggleFavorite("event", event.id) }) {
                Icon(
                    if (event.id in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                    "Favorito",
                    tint = if (event.id in favorites) Warning else MutedText,
                )
            }
        }
        ParticipantStrip(event.participants)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(event.status)
            Spacer(Modifier.width(8.dp))
            Text(event.venue ?: "Sede no confirmada", style = MaterialTheme.typography.bodySmall, color = SecondaryText, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ChevronRight, null, tint = Cyan)
        }
    }
}

@Composable
fun EventDetailScreen(vm: SigmaViewModel, eventId: String, onBack: () -> Unit, onQuestions: (String) -> Unit) {
    val event = vm.event(eventId)
    if (event == null) {
        Column { SigmaHeader("Evento", null, onBack); EmptyState("Evento no encontrado", "El evento ya no está en el catálogo local.") }
        return
    }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Resumen", "Contexto", "Estadísticas", "SIGMA-Q")
    Column(Modifier.fillMaxSize()) {
        SigmaHeader("Detalle del evento", vm.competition(event.competitionId)?.name, onBack, trailing = { ResearchModeTag(vm) })
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = screenPadding, divider = {}) {
            tabs.forEachIndexed { index, label -> Tab(tab == index, { tab = index }, text = { Text(label) }) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(screenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EventHero(event, vm)
            when (tab) {
                0 -> EventSummary(event, vm)
                1 -> SigmaCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Contexto disponible", eyebrow = "Evidencia registrada")
                    Text(event.context ?: "No hay contexto confirmado para este evento.", color = SecondaryText)
                    if (event.sources.isNotEmpty()) {
                        HorizontalDivider(color = Border.copy(alpha = .45f))
                        Text("${event.sources.size} fuentes web asociadas", color = Cyan, style = MaterialTheme.typography.labelLarge)
                        event.sources.take(4).forEach { source ->
                            Text("• ${source.name}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                2 -> EmptyState(
                    "Sin estadísticas confirmadas",
                    if (event.isDemo) "Las observaciones DEMO se incorporan únicamente al iniciar una investigación."
                    else "Las observaciones web se incorporan únicamente después de validarlas con SIGMA-Q.",
                    Icons.Default.BarChart,
                )
                else -> SigmaCard(Modifier.fillMaxWidth(), accent = Purple) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.Science, Purple)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Análisis con límites explícitos", style = MaterialTheme.typography.titleMedium)
                            Text("Fuentes, corte temporal, unidades y faltantes antes del dictamen.", color = SecondaryText)
                        }
                    }
                    Text("Sin probabilidades cuando no existe validación fuera de muestra.", color = Warning)
                }
            }
            Button(onClick = { onQuestions(event.id) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Default.Science, null)
                Spacer(Modifier.width(9.dp))
                Text("Investigar con SIGMA-Q")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EventHero(event: SportEvent, vm: SigmaViewModel) {
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(
            Brush.linearGradient(listOf(ElevatedPanel, DarkPanel)),
        ).padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelPill(vm.sport(event.sportId)?.name ?: event.sportId, sportAccent(event.sportId))
                Spacer(Modifier.weight(1f))
                StatusPill(event.status)
            }
            ParticipantMatchup(event.participants)
            Text(formatDateTime(event.startsAt), style = MaterialTheme.typography.bodyMedium, color = CyanSoft)
        }
    }
}

@Composable
private fun EventSummary(event: SportEvent, vm: SigmaViewModel) {
    SigmaCard(Modifier.fillMaxWidth()) {
        DetailRow(Icons.Default.EmojiEvents, "Competición", vm.competition(event.competitionId)?.name ?: event.competitionId)
        HorizontalDivider(color = Border.copy(alpha = .45f))
        DetailRow(Icons.Default.Schedule, "Fecha y zona", "${formatDateTime(event.startsAt)} · ${event.timeZone}")
        HorizontalDivider(color = Border.copy(alpha = .45f))
        DetailRow(Icons.Default.LocationOn, "Sede", event.venue ?: "No confirmada")
        HorizontalDivider(color = Border.copy(alpha = .45f))
        DetailRow(Icons.Default.Groups, "Formato", formatLabel(event.format))
    }
}

@Composable
fun QuestionsScreen(
    vm: SigmaViewModel,
    eventId: String,
    onBack: () -> Unit,
    onResearch: (String, String) -> Unit,
) {
    val customQuestions by vm.customQuestions.collectAsState()
    var customText by remember { mutableStateOf("") }
    var customVariable by remember { mutableStateOf("") }
    var customUnit by remember { mutableStateOf("") }
    val event = vm.event(eventId)
    val questions = remember(eventId, customQuestions) { vm.questions(eventId) }
    val groups = questions.groupBy { questionGroup(it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = screenPadding, end = screenPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SigmaHeader("Preguntas SIGMA-Q", event?.name, onBack, trailing = { ResearchModeTag(vm) }) }
        item {
            Text(
                "Selecciona el objetivo que quieres medir. Cada pregunta conserva variable, unidad, horizonte y criterio.",
                color = SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        groups.forEach { (group, groupQuestions) ->
            item { SectionTitle(group, eyebrow = "Objetivo estadístico") }
            items(groupQuestions, key = { it.id }) { question ->
                QuestionCard(question) { onResearch(eventId, question.id) }
            }
        }
        item { SectionTitle("Formula tu propia pregunta", eyebrow = "Personalizada") }
        item {
            SigmaCard(Modifier.fillMaxWidth(), accent = Purple) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ej. ¿Cuál es la variación observada de…?") },
                    minLines = 3,
                    shape = MaterialTheme.shapes.medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customVariable,
                        onValueChange = { customVariable = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Variable exacta") },
                        placeholder = { Text("points") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = customUnit,
                        onValueChange = { customUnit = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Unidad") },
                        placeholder = { Text("points") },
                        singleLine = true,
                    )
                }
                Button(
                    onClick = {
                        vm.addCustomQuestion(eventId, customText, customVariable, customUnit)
                        customText = ""
                        customVariable = ""
                        customUnit = ""
                    },
                    enabled = customText.isNotBlank() && customVariable.isNotBlank() && customUnit.isNotBlank(),
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Añadir pregunta") }
            }
        }
    }
}

@Composable
fun ImportResearchScreen(
    vm: SigmaViewModel,
    onBack: () -> Unit,
    onImported: (String) -> Unit,
) {
    val state by vm.importPreview.collectAsState()
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::loadImport)
    }
    DisposableEffect(Unit) {
        onDispose { vm.clearImportPreview() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = screenPadding, end = screenPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SigmaHeader("Importar investigación", "Contrato S2S Sigma v1", onBack) }
        item {
            Text(
                "Selecciona un JSON estructurado. El archivo se valida completamente y se muestra antes de guardar o analizar.",
                color = SecondaryText,
            )
        }
        item {
            Button(
                onClick = { launcher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.loading) "Leyendo…" else "Seleccionar archivo JSON")
            }
        }
        state.fileName?.let { fileName -> item { DetailRow(Icons.Default.Description, "Archivo", fileName) } }
        if (state.errors.isNotEmpty()) {
            item {
                SigmaCard(Modifier.fillMaxWidth(), accent = ErrorRed) {
                    Text("No se puede importar", color = ErrorRed, style = MaterialTheme.typography.titleMedium)
                    state.errors.forEach { Text("• $it", color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        state.document?.let { document ->
            item { SectionTitle("Vista previa", eyebrow = "Validación ${if (state.errors.isEmpty()) "correcta" else "fallida"}") }
            item {
                SigmaCard(Modifier.fillMaxWidth(), accent = if (state.errors.isEmpty()) Success else ErrorRed) {
                    Text(document.event.name, style = MaterialTheme.typography.titleLarge)
                    Text(document.event.competitionName ?: document.event.competitionId, color = Cyan)
                    DetailRow(Icons.Default.Groups, "Participantes", document.event.participants.joinToString(" · ") { it.name })
                    DetailRow(Icons.Default.HelpOutline, "Preguntas", document.investigations.size.toString())
                    DetailRow(
                        Icons.Default.DataObject,
                        "Observaciones",
                        document.investigations.sumOf { it.response.observations.size }.toString(),
                    )
                    DetailRow(
                        Icons.Default.Link,
                        "Fuentes",
                        document.investigations.flatMap { it.response.sources }.distinctBy { it.id }.size.toString(),
                    )
                    Text("Procedencia: ${document.provenance.generator}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(document.investigations, key = { it.request.question.id }) { item ->
                SigmaCard(Modifier.fillMaxWidth()) {
                    Text(item.request.question.text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${item.request.question.target.variable} · ${item.request.question.target.unit}",
                        color = Cyan,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "${item.response.observations.size} observaciones · ${item.response.sources.size} fuentes",
                        color = SecondaryText,
                    )
                }
            }
            if (state.warnings.isNotEmpty()) {
                item {
                    SigmaCard(Modifier.fillMaxWidth(), accent = Warning) {
                        state.warnings.forEach { Text("• $it", color = Warning, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            vm.confirmImport().onSuccess(onImported)
                        }
                    },
                    enabled = state.errors.isEmpty() && state.importedEventId == null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.Inventory, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.importedEventId == null) "Importar y abrir evento" else "Investigación importada")
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(question: SigmaQuestion, onClick: () -> Unit) {
    SigmaCard(Modifier.fillMaxWidth(), onClick = onClick, accent = if (question.custom) Purple else Cyan) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(questionIcon(question), if (question.custom) Purple else Cyan)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(question.text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LabelPill(question.target.variable.replace('_', ' '), Cyan)
                    LabelPill(question.target.unit, SecondaryText)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MutedText)
        }
        Text(question.target.evaluationCriteria, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ResearchScreen(
    vm: SigmaViewModel,
    eventId: String,
    questionId: String,
    onBack: () -> Unit,
    onResult: () -> Unit,
) {
    val progress by vm.progress.collectAsState()
    val analyses by vm.analyses.collectAsState()
    val mode by vm.researchMode.collectAsState()
    val event = vm.event(eventId)
    val question = vm.questions(eventId).firstOrNull { it.id == questionId }
    val currentProgress = progress?.takeIf { it.eventId == eventId && it.questionId == questionId }
    val completed = analyses[questionId]

    LaunchedEffect(eventId, questionId) {
        if (completed == null && currentProgress?.running != true) vm.runResearch(eventId, questionId)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = screenPadding, end = screenPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SigmaHeader("Investigación", event?.name, onBack, trailing = { ResearchModeTag(vm) }) }
        item {
            SigmaCard(Modifier.fillMaxWidth(), accent = Purple) {
                Text("PREGUNTA ACTIVA", style = MaterialTheme.typography.labelMedium, color = Purple)
                Text(question?.text ?: "Pregunta no disponible", style = MaterialTheme.typography.titleLarge)
                question?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabelPill(it.target.variable.replace('_', ' '), Cyan)
                        LabelPill(it.target.unit, SecondaryText)
                    }
                }
            }
        }
        item { ProcessingPanel(currentProgress, completed) }
        item {
            ResearchTimeline(currentProgress?.phase ?: if (completed != null) ResearchPhase.COMPLETE else ResearchPhase.PREPARING)
        }
        item {
            EvidenceSnapshot(
                sourceCount = currentProgress?.sourceCount ?: completed?.sources?.size ?: 0,
                observationCount = currentProgress?.observationCount ?: completed?.statistics?.sampleSize ?: 0,
                verdict = completed?.verdict ?: currentProgress?.verdict,
            )
        }
        item {
            Surface(
                color = Warning.copy(alpha = .08f),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, Warning.copy(alpha = .22f)),
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = Warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (mode == ResearchMode.DEMO) {
                            "MockResearchGateway procesa fixtures sintéticos. No se está navegando la web ni consultando OpenAI."
                        } else {
                            "OpenAI solo recupera y estructura evidencia web. SIGMA-Q valida y calcula localmente; no existe caída automática a DEMO."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText,
                    )
                }
            }
        }
        currentProgress?.error?.let { error ->
            item { ErrorPanel(error) { vm.runResearch(eventId, questionId) } }
        }
        if (completed != null) {
            item {
                Button(onClick = onResult, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.Insights, null)
                    Spacer(Modifier.width(9.dp))
                    Text("Abrir resultado SIGMA-Q")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun ProcessingPanel(progress: ResearchProgress?, analysis: SigmaAnalysis?) {
    val phase = progress?.phase ?: if (analysis != null) ResearchPhase.COMPLETE else ResearchPhase.PREPARING
    val target = phaseProgress(phase)
    val animated by animateFloatAsState(target, label = "research-progress")
    val title = when (phase) {
        ResearchPhase.PREPARING -> "Preparando la consulta"
        ResearchPhase.RETRIEVING -> "Recuperando evidencia"
        ResearchPhase.VALIDATING -> "Validando evidencia"
        ResearchPhase.PROCESSING -> "Procesando con SIGMA-Q"
        ResearchPhase.COMPLETE -> "Dictamen listo"
        ResearchPhase.ERROR -> "La investigación se detuvo"
    }
    val detail = when (phase) {
        ResearchPhase.COMPLETE -> "Las reglas de evidencia y abstención fueron aplicadas."
        ResearchPhase.ERROR -> progress?.error ?: "Revisa el detalle y vuelve a intentar."
        else -> "El progreso refleja el estado real del adaptador y del motor local."
    }
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(
            Brush.linearGradient(listOf(Color(0xFF0E3852), DarkPanel)),
        ).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                CircularProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 7.dp,
                    color = if (phase == ResearchPhase.ERROR) ErrorRed else if (phase == ResearchPhase.COMPLETE) Success else Cyan,
                    trackColor = Border.copy(alpha = .45f),
                    strokeCap = StrokeCap.Round,
                )
                if (phase == ResearchPhase.COMPLETE) {
                    Icon(Icons.Default.Check, null, tint = Success, modifier = Modifier.size(34.dp))
                } else {
                    Text("${(animated * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                if (progress?.running == true) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = Cyan,
                        trackColor = Border.copy(alpha = .4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResearchTimeline(phase: ResearchPhase) {
    val steps = listOf(
        "Consulta preparada" to "Evento, pregunta y fecha de corte",
        "Evidencia recibida" to "Fuentes y observaciones del adaptador activo",
        "Consistencia verificada" to "Temporalidad, unidades y trazabilidad",
        "Motor SIGMA-Q" to "Estadística descriptiva y datos faltantes",
        "Dictamen emitido" to "Resultado con limitaciones explícitas",
    )
    val current = when (phase) {
        ResearchPhase.PREPARING -> 0
        ResearchPhase.RETRIEVING -> 1
        ResearchPhase.VALIDATING -> 2
        ResearchPhase.PROCESSING -> 3
        ResearchPhase.COMPLETE -> steps.size
        ResearchPhase.ERROR -> -1
    }
    SigmaCard(Modifier.fillMaxWidth()) {
        SectionTitle("Ruta de investigación", eyebrow = "Proceso verificable")
        steps.forEachIndexed { index, (label, detail) ->
            TimelineStep(
                label = label,
                detail = detail,
                completed = current > index,
                active = current == index,
                last = index == steps.lastIndex,
            )
        }
    }
}

@Composable
private fun TimelineStep(label: String, detail: String, completed: Boolean, active: Boolean, last: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(
                    when {
                        completed -> Success
                        active -> Cyan
                        else -> Border
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (completed) Icon(Icons.Default.Check, null, tint = DeepNavy, modifier = Modifier.size(15.dp))
                else if (active) Box(Modifier.size(7.dp).clip(CircleShape).background(DeepNavy))
            }
            if (!last) Box(Modifier.width(2.dp).height(34.dp).background(if (completed) Success.copy(alpha = .55f) else Border))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(bottom = if (last) 0.dp else 14.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (completed || active) PrimaryText else MutedText)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (completed || active) SecondaryText else MutedText)
        }
    }
}

@Composable
private fun EvidenceSnapshot(sourceCount: Int, observationCount: Int, verdict: SigmaVerdict?) {
    SigmaCard(Modifier.fillMaxWidth()) {
        SectionTitle("Evidencia disponible", eyebrow = "Resumen")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Fuentes", sourceCount.toString(), Modifier.weight(1f), Icons.Default.Link)
            MetricTile("Observaciones", observationCount.toString(), Modifier.weight(1f), Icons.Default.DataObject, Aqua)
        }
        verdict?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Estado de evidencia", color = SecondaryText, modifier = Modifier.weight(1f))
                VerdictPill(it)
            }
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    SigmaCard(Modifier.fillMaxWidth(), accent = ErrorRed) {
        Text("No fue posible completar el análisis", style = MaterialTheme.typography.titleMedium, color = ErrorRed)
        Text(message, color = SecondaryText)
        OutlinedButton(onClick = { scope.launch { onRetry() } }) { Text("Reintentar") }
    }
}

@Composable
fun ResultScreen(vm: SigmaViewModel, eventId: String, questionId: String, onBack: () -> Unit) {
    val analyses by vm.analyses.collectAsState()
    val result = analyses[questionId]
    if (result == null) {
        Column { SigmaHeader("Resultado SIGMA-Q", null, onBack); EmptyState("Resultado no disponible", "Completa primero la investigación.", Icons.Default.Insights) }
        return
    }
    var detailsExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = screenPadding, end = screenPadding, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SigmaHeader("Resultado SIGMA-Q", vm.event(eventId)?.name, onBack, trailing = { ResearchModeTag(vm) }) }
        item { ResultHero(result) }
        item { SectionTitle("Indicadores principales", eyebrow = result.question.target.variable.replace('_', ' ')) }
        item { StatisticsGrid(result.statistics) }
        if (result.statistics.mean != null) {
            item { DistributionPanel(result.statistics) }
        }
        item { UncertaintyPanel(result) }
        if (result.factors.isNotEmpty()) {
            item { FactorsPanel(result.factors) }
        }
        item { SourcesPanel(result.sources, result.cutoffAt) }
        item {
            OutlinedButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Article, null)
                Spacer(Modifier.width(8.dp))
                Text(if (detailsExpanded) "Ocultar análisis detallado" else "Ver análisis detallado")
                Spacer(Modifier.weight(1f))
                Icon(if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
        }
        item {
            AnimatedVisibility(detailsExpanded) {
                DetailedAnalysis(result)
            }
        }
    }
}

@Composable
private fun ResultHero(result: SigmaAnalysis) {
    val accent = verdictColor(result.verdict)
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(
            Brush.linearGradient(listOf(accent.copy(alpha = .22f), DarkPanel, Navy)),
        ).padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Insights, null, tint = accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("DICTAMEN SIGMA-Q", style = MaterialTheme.typography.labelMedium, color = accent)
                    Text(verdictTitle(result.verdict), style = MaterialTheme.typography.headlineMedium)
                }
                VerdictPill(result.verdict)
            }
            Text(result.question.text, style = MaterialTheme.typography.bodyLarge, color = SecondaryText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelPill(result.question.target.variable.replace('_', ' '), Cyan)
                LabelPill(result.question.target.unit, SecondaryText)
            }
        }
    }
}

@Composable
private fun StatisticsGrid(stats: DescriptiveStatistics) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Media observada", stats.mean?.format(stats.unit) ?: "—", Modifier.weight(1f), Icons.AutoMirrored.Filled.ShowChart)
            MetricTile("Mediana", stats.median?.format(stats.unit) ?: "—", Modifier.weight(1f), Icons.Default.HorizontalRule, Aqua)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Dispersión", stats.sampleStandardDeviation?.format(stats.unit) ?: "—", Modifier.weight(1f), Icons.Default.SwapVert, Purple)
            MetricTile("Cobertura", "${stats.sampleSize}/${stats.requestedSampleSize}", Modifier.weight(1f), Icons.Default.DataUsage, Warning)
        }
    }
}

@Composable
private fun DistributionPanel(stats: DescriptiveStatistics) {
    SigmaCard(Modifier.fillMaxWidth()) {
        SectionTitle("Distribución observada", eyebrow = "Escala descriptiva")
        DistributionChart(stats)
        Row(Modifier.fillMaxWidth()) {
            Text("Mín. ${stats.minimum?.format(stats.unit) ?: "—"}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            Spacer(Modifier.weight(1f))
            Text("Máx. ${stats.maximum?.format(stats.unit) ?: "—"}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        }
        Text("La posición de la media y la mediana describe la muestra; no es una predicción.", style = MaterialTheme.typography.bodySmall, color = MutedText)
    }
}

@Composable
private fun DistributionChart(stats: DescriptiveStatistics) {
    val min = stats.minimum ?: return
    val max = stats.maximum ?: return
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    fun position(value: Double?) = (((value ?: min) - min) / range).toFloat().coerceIn(0f, 1f)
    Canvas(Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 8.dp)) {
        val y = size.height * .58f
        drawLine(Border, Offset(0f, y), Offset(size.width, y), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Cyan.copy(alpha = .55f), Offset(size.width * position(stats.median), y), Offset(size.width * position(stats.mean), y), strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Aqua, 8.dp.toPx(), Offset(size.width * position(stats.median), y))
        drawCircle(Cyan, 9.dp.toPx(), Offset(size.width * position(stats.mean), y))
        drawLine(Cyan, Offset(size.width * position(stats.mean), y - 24.dp.toPx()), Offset(size.width * position(stats.mean), y - 11.dp.toPx()), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun UncertaintyPanel(result: SigmaAnalysis) {
    SigmaCard(Modifier.fillMaxWidth(), accent = Warning) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Shield, Warning)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Incertidumbre no cuantificada", style = MaterialTheme.typography.titleMedium)
                Text("No existe un modelo predictivo validado fuera de muestra.", color = SecondaryText)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Block, null, tint = Warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sin probabilidad, estimación futura ni intervalo inventado.", style = MaterialTheme.typography.bodySmall, color = Warning)
        }
    }
}

@Composable
private fun FactorsPanel(factors: List<SigmaFactor>) {
    SigmaCard(Modifier.fillMaxWidth()) {
        SectionTitle("Factores relevantes", eyebrow = "Sin pesos arbitrarios")
        factors.forEachIndexed { index, factor ->
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(factorIcon(factor.family), if (index % 2 == 0) Cyan else Purple)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(factor.family.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                    Text(factor.description, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                }
                LabelPill(factor.evidenceState.name, evidenceColor(factor.evidenceState))
            }
            if (index != factors.lastIndex) HorizontalDivider(color = Border.copy(alpha = .4f))
        }
    }
}

@Composable
private fun SourcesPanel(sources: List<EvidenceSource>, cutoff: String) {
    val uriHandler = LocalUriHandler.current
    SigmaCard(Modifier.fillMaxWidth()) {
        SectionTitle("Fuentes", eyebrow = "${sources.size} referencias · corte ${shortDate(cutoff)}")
        sources.forEachIndexed { index, source ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(source.url) },
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Default.Link, null, tint = Cyan, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text(source.url, style = MaterialTheme.typography.bodySmall, color = Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Consultada ${shortDate(source.consultedAt)}", style = MaterialTheme.typography.labelMedium, color = MutedText)
                }
                if (source.isDemo) LabelPill("DEMO", Warning)
                else Icon(Icons.Default.OpenInNew, "Abrir fuente", tint = Cyan, modifier = Modifier.size(18.dp))
            }
            if (index != sources.lastIndex) HorizontalDivider(color = Border.copy(alpha = .4f))
        }
    }
}

@Composable
private fun DetailedAnalysis(result: SigmaAnalysis) {
    SigmaCard(Modifier.fillMaxWidth(), accent = Purple) {
        SectionTitle("Escenarios", eyebrow = "Lectura detallada")
        result.scenarios.forEach { Text("• $it", color = SecondaryText) }
        SectionTitle("Limitaciones y faltantes")
        (result.limitations + result.missingData).forEach { limitation ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, null, tint = Warning, modifier = Modifier.size(17.dp).padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text(limitation, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            }
        }
    }
}

@Composable
fun AnalysisHubScreen(vm: SigmaViewModel, onResult: (String, String) -> Unit) {
    val analyses by vm.analyses.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = screenPadding, end = screenPadding, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SigmaHeader("Análisis", "Dictámenes disponibles") }
        item {
            Box(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brush.linearGradient(listOf(Purple.copy(alpha = .2f), DarkPanel))).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Analytics, Purple)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("${analyses.size} análisis", style = MaterialTheme.typography.headlineSmall)
                        Text("Con evidencia y limitaciones trazables", color = SecondaryText)
                    }
                }
            }
        }
        if (analyses.isEmpty()) item { EmptyState("Aún no hay análisis", "Explora un evento y ejecuta una pregunta SIGMA-Q.", Icons.Default.QueryStats) }
        items(analyses.values.toList(), key = { it.id }) { analysis ->
            AnalysisResultCard(analysis, vm) { onResult(analysis.eventId, analysis.question.id) }
        }
    }
}

@Composable
fun HistoryScreen(vm: SigmaViewModel, onResult: (String, String) -> Unit) {
    val analyses by vm.analyses.collectAsState()
    var verdictFilter by remember { mutableStateOf<SigmaVerdict?>(null) }
    val filtered = analyses.values.filter { verdictFilter == null || it.verdict == verdictFilter }
    Column(Modifier.fillMaxSize()) {
        SigmaHeader("Historial", "Investigaciones guardadas localmente")
        LazyRow(
            contentPadding = PaddingValues(horizontal = screenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { CompactFilter("Todos", verdictFilter == null) { verdictFilter = null } }
            items(SigmaVerdict.entries) { verdict ->
                CompactFilter(verdict.name, verdictFilter == verdict) { verdictFilter = verdict }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filtered.isEmpty()) item { EmptyState("Historial vacío", "Los resultados aparecerán aquí después de una investigación.", Icons.Default.History) }
            items(filtered, key = { it.id }) { analysis ->
                AnalysisResultCard(analysis, vm) { onResult(analysis.eventId, analysis.question.id) }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun AnalysisResultCard(analysis: SigmaAnalysis, vm: SigmaViewModel, onClick: () -> Unit) {
    SigmaCard(Modifier.fillMaxWidth(), onClick = onClick, accent = verdictColor(analysis.verdict)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Insights, verdictColor(analysis.verdict))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.event(analysis.eventId)?.name ?: analysis.eventId, style = MaterialTheme.typography.labelMedium, color = Cyan)
                Text(analysis.question.text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MutedText)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            VerdictPill(analysis.verdict)
            Spacer(Modifier.width(8.dp))
            Text(shortDate(analysis.cutoffAt), style = MaterialTheme.typography.bodySmall, color = MutedText, modifier = Modifier.weight(1f))
            Text("${analysis.sources.size} fuentes", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
        }
    }
}

@Composable
fun ProfileScreen(vm: SigmaViewModel) {
    val favorites by vm.favoriteIds.collectAsState()
    val mode by vm.researchMode.collectAsState()
    val endpoint by vm.connectorEndpoint.collectAsState()
    var tempEndpoint by remember(endpoint) { mutableStateOf(endpoint) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = screenPadding, end = screenPadding, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SigmaHeader("Perfil", "Preferencias y metodología") }
        item {
            Box(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Brush.linearGradient(listOf(ElevatedPanel, DarkPanel))).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Cyan.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Text("Σ", style = MaterialTheme.typography.headlineLarge, color = Cyan, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("S2S Sigma", style = MaterialTheme.typography.titleLarge)
                        Text("Perfil de investigación local", color = SecondaryText)
                    }
                    ResearchModeTag(vm)
                }
            }
        }
        item { SectionTitle("Preferencias", eyebrow = "Aplicación") }
        item {
            SigmaCard(Modifier.fillMaxWidth()) {
                SettingsRow(Icons.Default.Language, "Idioma", "Español")
                HorizontalDivider(color = Border.copy(alpha = .4f))
                SettingsRow(Icons.Default.Schedule, "Zona horaria", "America/Bogota")
                HorizontalDivider(color = Border.copy(alpha = .4f))
                SettingsRow(Icons.Default.Star, "Favoritos", "${favorites.size} elementos")
            }
        }
        item { SectionTitle("Motor de investigación", eyebrow = "OpenAI + SIGMA-Q") }
        item {
            SigmaCard(Modifier.fillMaxWidth(), accent = if (mode == ResearchMode.OPENAI) Cyan else Warning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (mode == ResearchMode.OPENAI) Icons.Default.AutoAwesome else Icons.Default.Science, if (mode == ResearchMode.OPENAI) Cyan else Warning)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (mode == ResearchMode.OPENAI) "Modo IA Real" else "Modo DEMO", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (mode == ResearchMode.OPENAI && endpoint.isBlank()) "Configuración incompleta: falta la URL del conector."
                            else if (mode == ResearchMode.OPENAI) "OpenAI con búsqueda web mediante conector HTTPS."
                            else "MockResearchGateway usa únicamente datos sintéticos identificados.",
                            color = SecondaryText,
                        )
                    }
                    Switch(
                        checked = mode == ResearchMode.OPENAI,
                        onCheckedChange = { vm.setResearchMode(if (it) ResearchMode.OPENAI else ResearchMode.DEMO) },
                    )
                }
                HorizontalDivider(color = Border.copy(alpha = .4f))
                Text("URL HTTPS del conector", style = MaterialTheme.typography.labelMedium, color = Cyan)
                OutlinedTextField(
                    value = tempEndpoint,
                    onValueChange = { tempEndpoint = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("https://api.tu-dominio.com") },
                    supportingText = { Text("No introduzcas aquí una API key. OPENAI_API_KEY vive solo en el servidor.") },
                    singleLine = true,
                )
                Button(
                    onClick = { vm.setConnectorEndpoint(tempEndpoint) },
                    enabled = tempEndpoint.trim().trimEnd('/') != endpoint,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Guardar URL") }
                Surface(
                    color = (if (endpoint.isBlank()) Warning else Success).copy(alpha = .08f),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, (if (endpoint.isBlank()) Warning else Success).copy(alpha = .25f)),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Shield, null, tint = if (endpoint.isBlank()) Warning else Success, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (endpoint.isBlank()) "La app bloqueará las consultas reales y mostrará un error; nunca usará datos Mock como reemplazo."
                            else "La app enviará consultas y contexto, pero ninguna clave privada, a este endpoint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText,
                        )
                    }
                }
            }
        }
        item { SectionTitle("Metodología", eyebrow = "SIGMA-Q") }
        item {
            SigmaCard(Modifier.fillMaxWidth(), accent = Purple) {
                DetailRow(Icons.AutoMirrored.Filled.FactCheck, "Evidencia trazable", "Fuentes, consulta y fecha de corte", Purple)
                DetailRow(Icons.Default.Scale, "Abstención científica", "Sin probabilidades cuando falta validación", Purple)
                DetailRow(Icons.Default.DataUsage, "Estadística transparente", "Unidades, muestra y faltantes explícitos", Purple)
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, tint = MutedText)
    }
}

@Composable
private fun ResearchModeTag(vm: SigmaViewModel) {
    val mode by vm.researchMode.collectAsState()
    if (mode == ResearchMode.DEMO) DemoTag() else LabelPill("IA REAL", Cyan)
}

@Composable
private fun ParticipantMatchup(participants: List<Participant>) {
    if (participants.size == 2) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ParticipantIdentity(participants[0], Modifier.weight(1f), Alignment.Start)
            Text("VS", style = MaterialTheme.typography.labelLarge, color = MutedText, modifier = Modifier.padding(horizontal = 10.dp))
            ParticipantIdentity(participants[1], Modifier.weight(1f), Alignment.End)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${participants.size} participantes", style = MaterialTheme.typography.titleMedium)
            ParticipantStrip(participants)
        }
    }
}

@Composable
private fun ParticipantIdentity(participant: Participant, modifier: Modifier, alignment: Alignment.Horizontal) {
    Column(modifier, horizontalAlignment = alignment) {
        Avatar(participant.name)
        Spacer(Modifier.height(7.dp))
        Text(participant.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (alignment == Alignment.End) TextAlign.End else TextAlign.Start)
        participant.role?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MutedText) }
    }
}

@Composable
private fun ParticipantStrip(participants: List<Participant>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        participants.take(4).forEachIndexed { index, participant ->
            Box(Modifier.offset(x = (-index * 7).dp)) { Avatar(participant.name, 34.dp) }
        }
        if (participants.size > 4) {
            Box(Modifier.offset(x = (-28).dp).size(34.dp).clip(CircleShape).background(ElevatedPanel), contentAlignment = Alignment.Center) {
                Text("+${participants.size - 4}", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.width(if (participants.size > 1) 0.dp else 8.dp))
        Text(
            participants.joinToString(" · ") { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan.copy(alpha = .32f), Purple.copy(alpha = .26f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""), style = MaterialTheme.typography.labelLarge, color = PrimaryText)
    }
}

@Composable
private fun AnalysisSummaryCard(analysis: SigmaAnalysis, vm: SigmaViewModel, onClick: () -> Unit) {
    AnalysisResultCard(analysis, vm, onClick)
}

private fun sportIcon(id: String): ImageVector = when (id) {
    "basketball" -> Icons.Default.SportsBasketball
    "football" -> Icons.Default.SportsSoccer
    "tennis" -> Icons.Default.SportsTennis
    "motorsport" -> Icons.Default.SportsMotorsports
    "baseball" -> Icons.Default.SportsBaseball
    "hockey" -> Icons.Default.SportsHockey
    else -> Icons.Default.Interests
}

private fun sportAccent(id: String): Color = when (id) {
    "basketball" -> Color(0xFFFFA45B)
    "football" -> Aqua
    "tennis" -> Color(0xFFD6ED63)
    "motorsport" -> ErrorRed
    "baseball" -> Cyan
    "hockey" -> Purple
    else -> SecondaryText
}

private fun questionIcon(question: SigmaQuestion): ImageVector = when {
    "uncertainty" in question.id -> Icons.Default.Shield
    "factor" in question.id || "reliability" in question.id -> Icons.Default.Tune
    "performance" in question.id || "serve" in question.id -> Icons.AutoMirrored.Filled.CompareArrows
    else -> Icons.Default.QueryStats
}

private fun questionGroup(question: SigmaQuestion): String = when {
    "uncertainty" in question.id || "factor" in question.id || "reliability" in question.id -> "Contexto e incertidumbre"
    "performance" in question.id || "serve" in question.id -> "Comparación de rendimiento"
    else -> "Distribución y magnitud"
}

private fun factorIcon(family: SigmaFactorFamily): ImageVector = when (family) {
    SigmaFactorFamily.PARTICIPANTS -> Icons.Default.Groups
    SigmaFactorFamily.OFFICIALS -> Icons.Default.Gavel
    SigmaFactorFamily.PUBLIC -> Icons.Default.Campaign
    SigmaFactorFamily.TRAVEL -> Icons.Default.Flight
    SigmaFactorFamily.SCHEDULE_AND_REST -> Icons.Default.Bedtime
    SigmaFactorFamily.PHYSICAL_ENVIRONMENT -> Icons.Default.Cloud
    SigmaFactorFamily.TACTICS_AND_STRATEGY -> Icons.Default.Schema
    SigmaFactorFamily.LOAD_AND_RECOVERY -> Icons.Default.HealthAndSafety
    SigmaFactorFamily.COMPETITION_FORMAT -> Icons.Default.AccountTree
    SigmaFactorFamily.OBSERVABLE_BEHAVIOR -> Icons.Default.Visibility
    SigmaFactorFamily.OPERATIONS_AND_EQUIPMENT -> Icons.Default.Build
    SigmaFactorFamily.RANDOMNESS -> Icons.Default.Casino
}

private fun evidenceColor(state: EvidenceState): Color = when (state) {
    EvidenceState.VALID -> Success
    EvidenceState.STALE -> Warning
    EvidenceState.CONFLICTING -> ErrorRed
    EvidenceState.MISSING -> MutedText
    EvidenceState.DEMO -> Purple
}

private fun verdictColor(verdict: SigmaVerdict): Color = when (verdict) {
    SigmaVerdict.SUPPORTED -> Success
    SigmaVerdict.CONDITIONAL -> Warning
    SigmaVerdict.DESCRIPTIVE -> Cyan
    SigmaVerdict.INSUFFICIENT -> ErrorRed
}

private fun verdictTitle(verdict: SigmaVerdict): String = when (verdict) {
    SigmaVerdict.SUPPORTED -> "Evidencia sustentada"
    SigmaVerdict.CONDITIONAL -> "Resultado condicionado"
    SigmaVerdict.DESCRIPTIVE -> "Lectura descriptiva"
    SigmaVerdict.INSUFFICIENT -> "Evidencia insuficiente"
}

private fun phaseProgress(phase: ResearchPhase): Float = when (phase) {
    ResearchPhase.PREPARING -> .12f
    ResearchPhase.RETRIEVING -> .32f
    ResearchPhase.VALIDATING -> .58f
    ResearchPhase.PROCESSING -> .82f
    ResearchPhase.COMPLETE -> 1f
    ResearchPhase.ERROR -> 0f
}

private fun formatDateTime(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.forLanguageTag("es-CO"))).replaceFirstChar { it.uppercase() }
}.getOrDefault(value.replace('T', ' '))

private fun formatTime(value: String): String = runCatching { OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("—")
private fun formatDay(value: String): String = runCatching { OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CO"))).uppercase() }.getOrDefault("FECHA")
private fun shortDate(value: String): String = runCatching { OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))) }.getOrDefault(value)
private fun Double.format(unit: String) = String.format(Locale.US, "%.2f %s", this, unit)
private fun statusLabel(status: EventStatus) = when (status) {
    EventStatus.SCHEDULED -> "Programados"
    EventStatus.LIVE -> "En vivo"
    EventStatus.FINISHED -> "Finalizados"
    EventStatus.POSTPONED -> "Aplazados"
    EventStatus.CANCELLED -> "Cancelados"
}
private fun formatLabel(format: EventFormat) = when (format) {
    EventFormat.TEAM -> "Equipos"
    EventFormat.INDIVIDUAL -> "Participantes individuales"
    EventFormat.MULTI_PARTICIPANT -> "Múltiples participantes"
}
