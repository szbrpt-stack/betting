package com.s2s.sigma.research

import com.s2s.sigma.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

enum class ResearchFailureKind {
    CONFIGURATION,
    AUTHENTICATION,
    CONNECTION,
    REMOTE,
    FORMAT,
}

class ResearchGatewayException(
    val kind: ResearchFailureKind,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class DiscoveryEnvelope(
    val query: DiscoveryQueryPayload,
    val requestedAt: String,
    val timeZone: String,
)

@Serializable
private data class DiscoveryQueryPayload(
    val text: String,
    val sportId: String? = null,
    val competitionId: String? = null,
    val status: String? = null,
)

@Serializable
private data class DiscoveryResult(val events: List<SportEvent>)

@Serializable
private data class ResearchEnvelope(val request: ResearchRequest)

internal fun interface ResearchTransport {
    suspend fun post(baseUrl: String, path: String, body: String): String
}

private class HttpResearchTransport : ResearchTransport {
    override suspend fun post(baseUrl: String, path: String, body: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw remoteFailure(status, response)
            response
        } catch (error: ResearchGatewayException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw ResearchGatewayException(
                ResearchFailureKind.CONNECTION,
                "La investigación agotó el tiempo de espera. Revisa la conexión y vuelve a intentarlo.",
                error,
            )
        } catch (error: IOException) {
            throw ResearchGatewayException(
                ResearchFailureKind.CONNECTION,
                "No fue posible conectar con el servicio seguro de investigación.",
                error,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun remoteFailure(status: Int, response: String): ResearchGatewayException {
        val parsed = runCatching {
            Json.parseToJsonElement(response).jsonObject
        }.getOrNull()
        val code = parsed?.get("code")?.jsonPrimitive?.content.orEmpty()
        val serverMessage = parsed?.get("message")?.jsonPrimitive?.content
        val authentication = status == 401 || status == 403 || code.contains("AUTH", ignoreCase = true)
        return ResearchGatewayException(
            if (authentication) ResearchFailureKind.AUTHENTICATION else ResearchFailureKind.REMOTE,
            when {
                authentication -> "OpenAI rechazó la autenticación del conector. Verifica OPENAI_API_KEY en el servidor."
                !serverMessage.isNullOrBlank() -> serverMessage.take(300)
                else -> "El servicio de investigación respondió con HTTP $status."
            },
        )
    }
}

/**
 * Cliente móvil del conector seguro. Esta clase nunca recibe ni transmite una clave de OpenAI.
 * OPENAI_API_KEY vive exclusivamente en el servidor configurado por [endpointProvider].
 */
class OpenAIResearchGateway internal constructor(
    private val endpointProvider: () -> String?,
    private val nowProvider: () -> String,
    private val timeZoneProvider: () -> String,
    private val transport: ResearchTransport,
    private val allowInsecureLocalhost: Boolean,
) : ResearchGateway {
    constructor(
        endpointProvider: () -> String?,
        nowProvider: () -> String,
        timeZoneProvider: () -> String,
    ) : this(endpointProvider, nowProvider, timeZoneProvider, HttpResearchTransport(), false)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override suspend fun discoverEvents(query: DiscoveryQuery): List<SportEvent> {
        val endpoint = validatedEndpoint()
        val payload = DiscoveryEnvelope(
            query = DiscoveryQueryPayload(query.text, query.sportId, query.competitionId, query.status?.name),
            requestedAt = nowProvider(),
            timeZone = timeZoneProvider(),
        )
        val raw = transport.post(endpoint, "/v1/discover-events", json.encodeToString(payload))
        val result = decode<DiscoveryResult>(raw)
        result.events.forEach(::validateRealEvent)
        return result.events
    }

    override suspend fun generateQuestions(event: SportEvent): List<SigmaQuestion> = DemoData.questions(event)

    override suspend fun researchQuestion(request: ResearchRequest): ResearchResponse {
        val endpoint = validatedEndpoint()
        val raw = transport.post(endpoint, "/v1/research-question", json.encodeToString(ResearchEnvelope(request)))
        val result = decode<ResearchResponse>(raw)
        if (result.isDemo) formatFailure("El conector marcó una investigación real como DEMO.")
        if (result.sources.isEmpty()) formatFailure("OpenAI no devolvió fuentes verificables.")
        if (result.sources.any { it.isDemo || !it.url.startsWith("http") }) {
            formatFailure("El conector devolvió una fuente inválida o sintética.")
        }
        return result
    }

    override suspend fun refreshEvidence(request: ResearchRequest): ResearchResponse = researchQuestion(request)

    private fun validatedEndpoint(): String {
        val endpoint = endpointProvider()?.trim().orEmpty().trimEnd('/')
        if (endpoint.isBlank()) {
            throw ResearchGatewayException(
                ResearchFailureKind.CONFIGURATION,
                "Falta la URL HTTPS del conector OpenAI. Configúrala en Perfil; la clave nunca debe ir en el APK.",
            )
        }
        val localHttp = allowInsecureLocalhost &&
            (endpoint.startsWith("http://127.0.0.1") || endpoint.startsWith("http://localhost"))
        if (!endpoint.startsWith("https://") && !localHttp) {
            throw ResearchGatewayException(
                ResearchFailureKind.CONFIGURATION,
                "La URL del conector debe usar HTTPS.",
            )
        }
        return endpoint
    }

    private inline fun <reified T> decode(raw: String): T = try {
        json.decodeFromString(raw)
    } catch (error: Exception) {
        throw ResearchGatewayException(
            ResearchFailureKind.FORMAT,
            "El conector respondió con un formato no válido; no se mostrarán datos incompletos.",
            error,
        )
    }

    private fun validateRealEvent(event: SportEvent) {
        if (event.isDemo) formatFailure("El conector devolvió un evento marcado como DEMO.")
        if (event.name.isBlank() || event.participants.isEmpty()) formatFailure("OpenAI devolvió un evento incompleto.")
        if (event.sources.isEmpty()) formatFailure("El evento ${event.name} no incluye fuentes web.")
        if (event.sources.any { it.isDemo || !it.url.startsWith("http") }) {
            formatFailure("El evento ${event.name} contiene fuentes inválidas.")
        }
    }

    private fun formatFailure(message: String): Nothing = throw ResearchGatewayException(
        ResearchFailureKind.FORMAT,
        message,
    )
}
