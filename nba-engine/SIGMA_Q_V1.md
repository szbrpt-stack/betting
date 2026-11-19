# SIGMA-Q v1 — Contrato operativo de preguntas y viabilidad

Estado: especificación para implementación y validación, no modelo entrenado ni probabilidades certificadas.

## Pregunta canónica
Para {event_id} con fecha de corte {as_of}, estima {target} de {subject} bajo {conditions} usando únicamente evidencia disponible antes de {as_of}. Entrega una magnitud cuantitativa, incertidumbre validada cuando sea posible, factores de mayor sensibilidad, fuentes, cálculos y estado de viabilidad. Si faltan datos, ofrece el mejor resultado descriptivo comprobable y explica qué falta para cuantificar el pronóstico.

## QuestionSpec
- question_id, version, sport, event_id, as_of, subject_id
- target: outcome_type [binary|count|continuous|rank|duration], variable, operator [gt|gte|lt|lte|eq|between|none], threshold, unit
- scope: season, recent_completed_n, home_away_or_equivalent, opponent_strength, event_phase
- required_factors: form, efficiency, opponent, availability, fatigue, environment, matchup
- applicable_factors: declared by sport plugin; never pretend all factors apply to all sports
- output: point_estimate, predictive_interval, event_probability, scenarios, evidence, validation, status

## Deterministic research algorithm
1. Resolve event and cutoff; confirm schedule, identities, participants and event status.
2. Compile required evidence and freshness for sport/target. Keep immutable source snapshots and exact retrieval dates.
3. Select comparable completed pre-cutoff events; calculate coverage, missingness, opponent and condition adjustments.
4. Estimate sport-specific conditional outcome distribution; use shrinkage and parameter uncertainty as appropriate.
5. Validate by rolling-origin out-of-sample splits and compare with naive/current-season baselines.
6. Check calibration, interval coverage, group-wise error, sample sizes and distribution drift.
7. Evaluate base, optimistic and adverse plausible scenarios and report which inputs change estimates most.
8. Respond with direct measurable outcome and evidence. If predictive validation fails, return descriptive metric, never fabricated future probability.

## Viability statuses
- quantified_validated: predictive distribution passed predefined sport/target-specific validation gates; provide P(event), prediction interval, calibration report and sample size.
- conditional: measurable estimate or scenario available, but prediction not sufficiently validated; probability=null.
- descriptive_only: observed frequencies/means only; probability=null.
- insufficient_evidence: target cannot be reliably measured; all unsupported numbers=null.
No universal 0-100 score or invented confidence percentage.

## Example NBA template
'¿Cuál es la distribución de puntos de {team_id} contra {opponent_id} en {event_id}, y qué probabilidad validada tiene de superar {threshold} puntos, considerando ritmo, ORtg/DRtg, localía, descanso, rotación y disponibilidad antes de {as_of}?'
Metric formulas: points_per_game = sum(points)/n; observed_hit_rate = sum(points>threshold)/n; ORtg=100*points/possessions when possession definition and data are consistent; model_probability=P(Y>threshold|X) only after out-of-sample validation. Observed hit rate != predictive probability.
For example, 11/15 = 73.33% descriptive frequency, NOT 73.33% future probability.

## Example cross-sport targets
Football: goals, shot quality where supported, probability of win/draw/loss from calibrated goal distribution.
Tennis: sets, service/return performance, match outcome distribution conditional on surface.
Baseball: runs, starting pitchers/bullpen, game outcome distribution.
Motorsport: finishing position distribution with field size, qualifying, weather and reliability.
Combat: duration and outcome distributions conditional on verified category and athlete history.

## Mobile AnalysisResponse
{
  "event_id":"string",
  "question_id":"string",
  "as_of":"ISO8601",
  "target":{"variable":"string","operator":"gt","threshold":110,"unit":"points"},
  "status":"conditional",
  "answer":{"headline":"string","measurable_result":{"value":null,"unit":"points","kind":"estimate"}},
  "observed":{"sample_n":15,"eligible_n":15,"mean":null,"observed_hit_rate":null},
  "prediction":{"point_estimate":null,"event_probability":null,"interval":null,"calibrated":false},
  "drivers":[],
  "scenarios":[],
  "evidence":[],
  "validation":{"method":null,"sample_n":null,"metrics":{},"baseline":null},
  "limitations":[],
  "audit":{"cutoff":"ISO8601","formula_version":"sigma-q/1","source_snapshot_ids":[]}
}
Numbers above are schema placeholders, not live sports facts.

## Release criteria
- Unit tests for cutoff/leakage, identity, deduplication, home/away, event type, missing data, units, threshold operators.
- Integration tests with fixtures for NBA, soccer, tennis and multi-entrant racing.
- Statistical gates are predefined by sport and target before evaluation, including calibration, error vs baseline, predictive interval coverage, data freshness and drift.
- Fail closed: if missingness, conflicting sources, drift or inadequate calibration exceeds gates, downgrade status rather than asserting viability.
- The AI produces narrative only from validated structured output, not unsupported raw numerical claims.
