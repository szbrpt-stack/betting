package com.s2s.sigma.domain

import com.s2s.sigma.data.*
import java.time.OffsetDateTime
import kotlin.math.pow
import kotlin.math.sqrt

data class ValidationResult(val valid: Boolean, val errors: List<String>)

object SigmaEngine {
    fun validateSources(sources: List<EvidenceSource>, cutoffAt: String): ValidationResult {
        val cutoff = OffsetDateTime.parse(cutoffAt).toInstant()
        val errors = buildList {
            if (sources.isEmpty()) add("No hay fuentes disponibles")
            sources.forEach { source ->
                if (source.id.isBlank()) add("Fuente sin identificador estable")
                if (!source.url.startsWith("https://")) add("La fuente ${source.id} no usa una URL HTTPS trazable")
                if (OffsetDateTime.parse(source.consultedAt).toInstant() > cutoff) {
                    add("La fuente ${source.id} fue consultada después del corte")
                }
                source.publishedAt?.let {
                    if (OffsetDateTime.parse(it).toInstant() > cutoff) add("La fuente ${source.id} se publicó después del corte")
                }
            }
            if (sources.map { it.id }.distinct().size != sources.size) add("Existen fuentes duplicadas")
        }
        return ValidationResult(errors.isEmpty(), errors)
    }

    fun validateTemporal(observations: List<Observation>, cutoffAt: String): ValidationResult {
        val cutoff = OffsetDateTime.parse(cutoffAt).toInstant()
        val errors = buildList {
            observations.forEach { observation ->
                if (OffsetDateTime.parse(observation.completedAt).toInstant() >= cutoff) {
                    add("${observation.id}: observación no anterior al corte")
                }
                if (OffsetDateTime.parse(observation.consultedAt).toInstant() > cutoff) {
                    add("${observation.id}: consulta posterior al corte")
                }
                if (observation.value?.isFinite() == false) add("${observation.id}: valor no finito")
            }
            if (observations.map { it.id }.distinct().size != observations.size) add("Observaciones duplicadas")
        }
        return ValidationResult(errors.isEmpty(), errors)
    }

    fun normalizeValue(value: Double, fromUnit: String, toUnit: String): Double? = when {
        fromUnit == toUnit -> value
        fromUnit == "minutes" && toUnit == "seconds" -> value * 60.0
        fromUnit == "seconds" && toUnit == "minutes" -> value / 60.0
        else -> null
    }

    fun descriptiveStatistics(
        observations: List<Observation>,
        unit: String,
        requestedSampleSize: Int = 10,
        minimumSampleSize: Int = 3,
    ): DescriptiveStatistics {
        val values = observations.mapNotNull { observation ->
            observation.value?.let { normalizeValue(it, observation.unit, unit) }
        }.take(requestedSampleSize)
        if (values.size < minimumSampleSize) {
            return DescriptiveStatistics(values.size, requestedSampleSize, unit = unit)
        }
        val mean = values.average()
        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.lastIndex / 2] + sorted[sorted.size / 2]) / 2.0
        } else sorted[sorted.size / 2]
        val std = sqrt(values.sumOf { (it - mean).pow(2) } / (values.size - 1))
        return DescriptiveStatistics(
            sampleSize = values.size,
            requestedSampleSize = requestedSampleSize,
            mean = mean,
            median = median,
            sampleStandardDeviation = std,
            minimum = values.minOrNull(),
            maximum = values.maxOrNull(),
            unit = unit,
        )
    }

    fun analyze(
        request: ResearchRequest,
        response: ResearchResponse,
        minimumSampleSize: Int = 3,
    ): SigmaAnalysis {
        val sourceValidation = validateSources(response.sources, request.cutoffAt)
        val temporalValidation = validateTemporal(response.observations, request.cutoffAt)
        val compatible = response.observations.filter {
            it.variable == request.question.target.variable &&
                normalizeValue(it.value ?: return@filter false, it.unit, request.question.target.unit) != null
        }
        val statistics = if (temporalValidation.valid) {
            descriptiveStatistics(compatible, request.question.target.unit, minimumSampleSize = minimumSampleSize)
        } else {
            DescriptiveStatistics(0, 10, unit = request.question.target.unit)
        }
        val missing = buildList {
            if (!sourceValidation.valid) addAll(sourceValidation.errors)
            if (!temporalValidation.valid) addAll(temporalValidation.errors)
            if (compatible.size < minimumSampleSize) add("Muestra compatible insuficiente: ${compatible.size}/$minimumSampleSize")
            if (response.factors.none { it.family == SigmaFactorFamily.LOAD_AND_RECOVERY }) add("Carga y recuperación no documentadas")
            if (response.factors.none { it.family == SigmaFactorFamily.PHYSICAL_ENVIRONMENT }) add("Entorno físico no documentado")
        }
        val verdict = if (sourceValidation.valid && temporalValidation.valid && statistics.mean != null) {
            SigmaVerdict.DESCRIPTIVE
        } else {
            SigmaVerdict.INSUFFICIENT
        }
        return SigmaAnalysis(
            id = "analysis-${request.id}",
            eventId = request.eventId,
            question = request.question,
            statistics = statistics,
            estimate = null,
            uncertaintyInterval = null,
            factors = response.factors,
            scenarios = listOf(
                "Escenario base: se conservan únicamente observaciones comparables anteriores al corte.",
                "Escenario adverso: la evidencia faltante podría cambiar la interpretación descriptiva.",
            ),
            sources = response.sources,
            cutoffAt = request.cutoffAt,
            limitations = buildList {
                if (response.isDemo) add("Datos sintéticos DEMO; no representan eventos deportivos reales.")
                else add("La evidencia web fue inyectada por IA y validada con reglas deterministas de SIGMA-Q.")
                add("No existe un modelo predictivo validado fuera de muestra.")
                add("La media observada no equivale a una probabilidad futura.")
            },
            missingData = missing,
            verdict = verdict,
            validatedModel = false,
            isDemo = response.isDemo,
        )
    }
}
