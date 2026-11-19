package com.s2s.sigma

import com.s2s.sigma.data.ResearchImportContract
import com.s2s.sigma.domain.SigmaEngine
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchImportContractTest {
    private fun example() = File("../examples/s2s-research-import-v1.json").readText()

    @Test
    fun exampleFileMatchesContractAndProducesDescriptiveAnalysis() {
        val validation = ResearchImportContract.decodeAndValidate(example())
        assertTrue(validation.errors.joinToString("\n"), validation.valid)
        val item = validation.document!!.investigations.single()

        val analysis = SigmaEngine.analyze(item.request, item.response)

        assertEquals(3, analysis.statistics.sampleSize)
        assertEquals(113.0, analysis.statistics.mean!!, 0.0001)
        assertFalse(analysis.isDemo)
    }

    @Test
    fun rejectsObservationWithVariableDifferentFromQuestionTarget() {
        val document = ResearchImportContract.decodeAndValidate(example()).document!!
        val item = document.investigations.single()
        val invalid = document.copy(
            investigations = listOf(
                item.copy(
                    response = item.response.copy(
                        observations = item.response.observations.mapIndexed { index, observation ->
                            if (index == 0) observation.copy(variable = "different_metric") else observation
                        },
                    ),
                ),
            ),
        )

        val validation = ResearchImportContract.validate(invalid)

        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.contains("variable no coincide") })
    }

    @Test
    fun rejectsUnknownSchemaVersion() {
        val document = ResearchImportContract.decodeAndValidate(example()).document!!
        val validation = ResearchImportContract.validate(document.copy(schemaVersion = 99))
        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.contains("schemaVersion") })
    }
}
