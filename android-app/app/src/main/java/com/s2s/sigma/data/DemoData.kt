package com.s2s.sigma.data

object DemoData {
    const val cutoff = "2026-11-17T18:00:00-05:00"
    const val consulted = "2026-11-17T17:45:00-05:00"

    val sports = listOf(
        Sport("basketball", "Baloncesto", "●", true),
        Sport("football", "Fútbol", "◆", true),
        Sport("tennis", "Tenis", "◉"),
        Sport("motorsport", "Automovilismo", "◇"),
        Sport("baseball", "Béisbol", "◍"),
        Sport("hockey", "Hockey", "⬡"),
        Sport("other", "Otros deportes", "Σ"),
    )

    val competitions = listOf(
        Competition("nba", "basketball", "NBA", "Norteamérica", "2026–27"),
        Competition("euroleague", "basketball", "Euroliga", "Europa", "2026–27"),
        Competition("acb", "basketball", "Liga ACB", "España", "2026–27"),
        Competition("laliga", "football", "LaLiga", "España", "2026–27"),
        Competition("premier", "football", "Premier League", "Inglaterra", "2026–27"),
        Competition("champions", "football", "Champions League", "Europa", "2026–27"),
        Competition("atp", "tennis", "ATP", "Internacional", "2026"),
        Competition("wta", "tennis", "WTA", "Internacional", "2026"),
        Competition("grand-slam", "tennis", "Grand Slam", "Internacional", "2026"),
        Competition("f1", "motorsport", "Fórmula 1", "Internacional", "2026"),
        Competition("motogp", "motorsport", "MotoGP", "Internacional", "2026"),
        Competition("wec", "motorsport", "WEC", "Internacional", "2026"),
        Competition("mlb", "baseball", "MLB", "Norteamérica", "2026"),
        Competition("nhl", "hockey", "NHL", "Norteamérica", "2026–27"),
    )

    private fun team(id: String, name: String, role: String) =
        Participant(id, name, ParticipantKind.TEAM, role)

    val events = listOf(
        SportEvent(
            "nba-demo-001", "basketball", "nba", "Orcas de Metro vs Halcones del Lago",
            listOf(team("metro-orcas", "Orcas de Metro", "home"), team("lake-hawks", "Halcones del Lago", "away")),
            "2026-11-18T20:00:00-05:00", "America/Bogota", "Arena Sigma", EventStatus.SCHEDULED,
            EventFormat.TEAM, "Encuentro sintético para demostrar el flujo. Ningún dato representa un partido real.", consulted,
        ),
        SportEvent(
            "euro-demo-001", "basketball", "euroleague", "Aster BC vs Vector Club",
            listOf(team("aster", "Aster BC", "home"), team("vector", "Vector Club", "away")),
            "2026-11-19T19:30:00+01:00", "Europe/Madrid", null, EventStatus.SCHEDULED,
            EventFormat.TEAM, "Sede todavía no confirmada en el conjunto DEMO.", consulted,
        ),
        SportEvent(
            "football-demo-001", "football", "laliga", "Atlético Prisma vs Unión Delta",
            listOf(team("prisma", "Atlético Prisma", "home"), team("delta", "Unión Delta", "away")),
            "2026-11-20T21:00:00+01:00", "Europe/Madrid", "Estadio Vector", EventStatus.SCHEDULED,
            EventFormat.TEAM, "Evento completamente sintético.", consulted,
        ),
        SportEvent(
            "tennis-demo-001", "tennis", "atp", "Cuartos de final — Pista central",
            listOf(
                Participant("alex-river", "Alex River", ParticipantKind.INDIVIDUAL, "player"),
                Participant("sam-north", "Sam North", ParticipantKind.INDIVIDUAL, "player"),
            ),
            "2026-11-21T15:00:00-05:00", "America/Bogota", "Centro Tenis Sigma", EventStatus.SCHEDULED,
            EventFormat.INDIVIDUAL, "Identidades y estadísticas DEMO.", consulted,
        ),
        SportEvent(
            "f1-demo-001", "motorsport", "f1", "Gran Premio Sigma",
            listOf("Nova", "Apex", "Ion", "Pulse", "Vertex").mapIndexed { index, name ->
                Participant("driver-$index", name, ParticipantKind.INDIVIDUAL, "entrant")
            },
            "2026-11-22T14:00:00-03:00", "America/Sao_Paulo", "Circuito Horizonte", EventStatus.SCHEDULED,
            EventFormat.MULTI_PARTICIPANT, "Carrera DEMO con múltiples participantes.", consulted,
        ),
        SportEvent(
            "mlb-demo-001", "baseball", "mlb", "Cometas vs Faros",
            listOf(team("comets", "Cometas", "away"), team("beacons", "Faros", "home")),
            "2026-11-17T13:00:00-05:00", "America/New_York", null, EventStatus.FINISHED,
            EventFormat.TEAM, "Resultado no incluido: solo identidad DEMO.", consulted,
        ),
    )

    fun questions(event: SportEvent): List<SigmaQuestion> {
        val first = event.participants.first().name
        val templates = when (event.sportId) {
            "basketball" -> listOf(
                Triple("total-points", "¿Cuál es el total descriptivo de puntos observado?", "points"),
                Triple("performance", "¿Qué participante tiene mejores indicadores de rendimiento?", "performance_index"),
                Triple("uncertainty", "¿Qué limita la incertidumbre de la estimación?", "points"),
            )
            "football" -> listOf(
                Triple("goal-distribution", "¿Cuál es la distribución descriptiva de goles?", "goals"),
                Triple("factors", "¿Qué factores pueden modificar el resultado?", "goals"),
            )
            "tennis" -> listOf(
                Triple("sets", "¿Cuál es la distribución descriptiva de sets ganados?", "sets"),
                Triple("serve", "¿Qué participante muestra mejores indicadores de servicio?", "service_points"),
            )
            "motorsport" -> listOf(
                Triple("finish", "¿Cuál es la distribución de posiciones de llegada?", "position"),
                Triple("reliability", "¿Qué evidencia existe sobre fiabilidad y entorno?", "laps"),
            )
            else -> listOf(Triple("performance", "¿Qué indicadores descriptivos están disponibles?", "performance"))
        }
        return templates.map { (id, text, variable) ->
            SigmaQuestion(
                id = "${event.id}-$id",
                eventId = event.id,
                text = text,
                target = TargetDefinition(
                    variable = variable,
                    unit = unitFor(variable),
                    horizon = "Evento ${event.name}",
                    evaluationCriteria = "Observaciones completadas, comparables y anteriores al corte; sujeto: $first",
                ),
            )
        }
    }

    private fun unitFor(variable: String) = when (variable) {
        "points" -> "points"
        "goals" -> "goals"
        "sets" -> "sets"
        "position" -> "position"
        "laps" -> "laps"
        else -> "index"
    }

    fun demoSources(question: SigmaQuestion) = listOf(
        EvidenceSource(
            id = "demo-methodology",
            name = "Ficha metodológica sintética S2S Sigma",
            url = "https://example.org/s2s-sigma/demo-methodology",
            publishedAt = "2026-11-16T12:00:00-05:00",
            consultedAt = consulted,
            cutoffAt = cutoff,
        ),
        EvidenceSource(
            id = "demo-observations-${question.id}",
            name = "Observaciones sintéticas identificadas como DEMO",
            url = "https://example.org/s2s-sigma/demo-observations",
            publishedAt = "2026-11-17T12:00:00-05:00",
            consultedAt = consulted,
            cutoffAt = cutoff,
        ),
    )

    fun demoObservations(event: SportEvent, question: SigmaQuestion): List<Observation> {
        val base = when (question.target.unit) {
            "points" -> listOf(104.0, 111.0, 108.0, 99.0, 115.0, 107.0, 102.0, 110.0)
            "goals" -> listOf(1.0, 2.0, 0.0, 1.0, 3.0, 1.0, 2.0, 1.0)
            "sets" -> listOf(2.0, 1.0, 2.0, 0.0, 2.0, 2.0, 1.0, 2.0)
            "position" -> listOf(4.0, 6.0, 3.0, 8.0, 5.0, 2.0, 7.0, 4.0)
            "laps" -> listOf(52.0, 58.0, 57.0, 41.0, 60.0, 55.0, 59.0, 54.0)
            else -> listOf(61.0, 64.0, 59.0, 66.0, 63.0, 62.0, 65.0, 60.0)
        }
        return base.mapIndexed { index, value ->
            Observation(
                id = "demo-observation-${question.id}-$index",
                eventId = "demo-prior-$index",
                subjectId = event.participants.first().id,
                variable = question.target.variable,
                value = value,
                unit = question.target.unit,
                observationPeriod = "Evento comparable completado ${index + 1}/8",
                completedAt = "2026-11-${(index + 1).toString().padStart(2, '0')}T20:00:00-05:00",
                sourceId = "demo-observations-${question.id}",
                consultedAt = consulted,
                cutoffAt = cutoff,
            )
        }
    }
}

fun List<Competition>.forSport(sportId: String): List<Competition> = filter { it.sportId == sportId }

fun List<SportEvent>.applyFilter(filter: EventFilter): List<SportEvent> = filter { event ->
    (filter.sportId == null || event.sportId == filter.sportId) &&
        (filter.competitionId == null || event.competitionId == filter.competitionId) &&
        (filter.status == null || event.status == filter.status) &&
        (filter.participantQuery.isBlank() || event.participants.any {
            it.name.contains(filter.participantQuery, ignoreCase = true)
        })
}
