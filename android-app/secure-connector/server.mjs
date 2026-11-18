import http from "node:http";
import { createHash } from "node:crypto";
import OpenAI from "openai";

const port = Number(process.env.PORT ?? 8787);
const model = process.env.OPENAI_MODEL ?? "gpt-5-mini";
const apiKey = process.env.OPENAI_API_KEY?.trim();
const client = apiKey ? new OpenAI({ apiKey }) : null;

const participantSchema = {
  type: "object",
  additionalProperties: false,
  required: ["id", "name", "kind", "role"],
  properties: {
    id: { type: "string" },
    name: { type: "string" },
    kind: { type: "string", enum: ["TEAM", "INDIVIDUAL", "CONSTRUCTOR"] },
    role: { type: ["string", "null"] },
  },
};

const discoverySchema = {
  type: "object",
  additionalProperties: false,
  required: ["events"],
  properties: {
    events: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["id", "sportId", "competitionId", "competitionName", "name", "participants", "startsAt", "timeZone", "venue", "status", "format", "context"],
        properties: {
          id: { type: "string" },
          sportId: { type: "string", enum: ["basketball", "football", "tennis", "motorsport", "baseball", "hockey", "other"] },
          competitionId: { type: "string" },
          competitionName: { type: "string" },
          name: { type: "string" },
          participants: { type: "array", minItems: 1, items: participantSchema },
          startsAt: { type: "string" },
          timeZone: { type: "string" },
          venue: { type: ["string", "null"] },
          status: { type: "string", enum: ["SCHEDULED", "LIVE", "FINISHED", "POSTPONED", "CANCELLED"] },
          format: { type: "string", enum: ["TEAM", "INDIVIDUAL", "MULTI_PARTICIPANT"] },
          context: { type: ["string", "null"] },
        },
      },
    },
  },
};

const researchSchema = {
  type: "object",
  additionalProperties: false,
  required: ["observations", "factors"],
  properties: {
    observations: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["id", "subjectId", "variable", "value", "unit", "observationPeriod", "completedAt"],
        properties: {
          id: { type: "string" },
          subjectId: { type: "string" },
          variable: { type: "string" },
          value: { type: ["number", "null"] },
          unit: { type: "string" },
          observationPeriod: { type: "string" },
          completedAt: { type: "string" },
        },
      },
    },
    factors: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["family", "description", "evidenceState"],
        properties: {
          family: { type: "string", enum: ["PARTICIPANTS", "OFFICIALS", "PUBLIC", "TRAVEL", "SCHEDULE_AND_REST", "PHYSICAL_ENVIRONMENT", "TACTICS_AND_STRATEGY", "LOAD_AND_RECOVERY", "COMPETITION_FORMAT", "OBSERVABLE_BEHAVIOR", "OPERATIONS_AND_EQUIPMENT", "RANDOMNESS"] },
          description: { type: "string" },
          evidenceState: { type: "string", enum: ["VALID", "STALE", "CONFLICTING", "MISSING"] },
        },
      },
    },
  },
};

function sourceId(url) {
  return `web-${createHash("sha256").update(url).digest("hex").slice(0, 16)}`;
}

function webSources(response, consultedAt, cutoffAt) {
  const seen = new Map();
  for (const item of response.output ?? []) {
    if (item.type !== "web_search_call") continue;
    for (const source of item.action?.sources ?? []) {
      if (!source?.url?.startsWith("https://")) continue;
      seen.set(source.url, {
        id: sourceId(source.url),
        name: source.title || new URL(source.url).hostname,
        url: source.url,
        publishedAt: null,
        consultedAt,
        cutoffAt,
        isDemo: false,
      });
    }
  }
  const sources = [...seen.values()];
  if (sources.length === 0) throw serviceError("NO_WEB_SOURCES", "OpenAI no devolvió fuentes web verificables.", 502);
  return sources;
}

async function createStructuredResponse({ input, schema, name }) {
  if (!client) {
    const error = new Error("Falta OPENAI_API_KEY en el servidor.");
    error.status = 503;
    error.code = "OPENAI_AUTHENTICATION";
    throw error;
  }
  return client.responses.create({
    model,
    tools: [{ type: "web_search" }],
    tool_choice: "auto",
    include: ["web_search_call.action.sources"],
    input,
    text: { format: { type: "json_schema", name, strict: true, schema } },
  });
}

async function discoverEvents(body) {
  const { query, requestedAt, timeZone } = body ?? {};
  if (!query?.text?.trim() || !requestedAt || !timeZone) throw serviceError("BAD_REQUEST", "query.text, requestedAt y timeZone son obligatorios.", 400);
  const response = await createStructuredResponse({
    name: "s2s_event_discovery",
    schema: discoverySchema,
    input: [
      { role: "system", content: "Investiga eventos deportivos públicos y reales. Usa búsqueda web. No inventes eventos, horarios ni participantes. Devuelve solo eventos respaldados por fuentes. Interpreta fechas relativas usando requestedAt y timeZone." },
      { role: "user", content: JSON.stringify({ query, requestedAt, timeZone }) },
    ],
  });
  const parsed = JSON.parse(response.output_text);
  const sources = webSources(response, requestedAt, requestedAt);
  return {
    events: parsed.events.map((event) => ({
      ...event,
      retrievedAt: requestedAt,
      isDemo: false,
      sources,
    })),
  };
}

async function researchQuestion(body) {
  const request = body?.request;
  if (!request?.event || !request?.question || !request?.cutoffAt) throw serviceError("BAD_REQUEST", "La solicitud de investigación está incompleta.", 400);
  const response = await createStructuredResponse({
    name: "s2s_research_evidence",
    schema: researchSchema,
    input: [
      { role: "system", content: "Investiga evidencia deportiva pública y real con búsqueda web. Extrae solamente observaciones numéricas completadas antes de cutoffAt, conserva exactamente la variable y unidad solicitadas cuando sean compatibles y no produzcas probabilidades ni pronósticos. Si faltan datos, devuelve menos observaciones y factores MISSING; nunca inventes valores." },
      { role: "user", content: JSON.stringify(request) },
    ],
  });
  const parsed = JSON.parse(response.output_text);
  const sources = webSources(response, request.requestedAt, request.cutoffAt);
  const sourceIds = sources.map((source) => source.id);
  return {
    requestId: request.id,
    observations: parsed.observations.map((observation) => ({
      ...observation,
      eventId: request.eventId,
      sourceId: sourceIds[0],
      consultedAt: request.requestedAt,
      cutoffAt: request.cutoffAt,
    })),
    sources,
    factors: parsed.factors.map((factor) => ({ ...factor, sourceIds })),
    receivedAt: new Date().toISOString(),
    isDemo: false,
  };
}

function serviceError(code, message, status = 500) {
  return Object.assign(new Error(message), { code, status });
}

async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 1_000_000) throw serviceError("BODY_TOO_LARGE", "La solicitud excede 1 MB.", 413);
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw serviceError("INVALID_JSON", "El cuerpo no contiene JSON válido.", 400);
  }
}

function send(response, status, payload) {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  response.end(JSON.stringify(payload));
}

const server = http.createServer(async (request, response) => {
  try {
    if (request.method === "GET" && request.url === "/health") {
      send(response, 200, { status: "ok", openaiConfigured: Boolean(apiKey), model });
      return;
    }
    if (request.method !== "POST") throw serviceError("NOT_FOUND", "Ruta no encontrada.", 404);
    const body = await readJson(request);
    if (request.url === "/v1/discover-events") send(response, 200, await discoverEvents(body));
    else if (request.url === "/v1/research-question") send(response, 200, await researchQuestion(body));
    else throw serviceError("NOT_FOUND", "Ruta no encontrada.", 404);
  } catch (error) {
    const auth = error?.status === 401 || error?.status === 403;
    const code = auth ? "OPENAI_AUTHENTICATION" : error?.code || "RESEARCH_FAILED";
    const status = Number.isInteger(error?.status) && error.status < 500 ? error.status : 502;
    const message = auth ? "OpenAI rechazó OPENAI_API_KEY en el servidor." : error?.message || "La investigación falló.";
    send(response, status, { code, message });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`S2S Sigma secure connector listening on :${port} with ${model}`);
});
