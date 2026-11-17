"""SIGMA-Q v1: sport-neutral, evidence-gated descriptive analysis.

No live fetching, AI inference or predictive probabilities. All inputs are
operator-supplied and source-attested; results are reproducible.
"""
from __future__ import annotations

from datetime import datetime, timezone
from math import isfinite
from statistics import mean, median, stdev

SPORT_METRICS = {
    "basketball": {"points": "points", "rebounds": "rebounds", "assists": "assists"},
    "football": {"goals": "goals", "shots": "shots"},
    "tennis": {"aces": "aces", "double_faults": "double_faults", "games_won": "games"},
    "baseball": {"runs": "runs", "hits": "hits"},
    "motorsport": {"finish_position": "position", "laps_completed": "laps"},
    "combat": {"rounds_completed": "rounds", "duration_seconds": "seconds"},
}
OPERATORS = {"gt", "gte", "lt", "lte", "eq", "between", "none"}


def timestamp(value: str) -> datetime:
    dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if dt.tzinfo is None:
        raise ValueError("Timestamps must include a timezone")
    return dt.astimezone(timezone.utc)


def question_catalog(event: dict) -> list[dict]:
    """Generate reusable sport-specific question definitions for one event."""
    sport = event["sport"]
    if sport not in SPORT_METRICS:
        raise ValueError("Unsupported sport")
    if not event.get("id") or not event.get("participants"):
        raise ValueError("Event ID and participants required")
    if len(set(event["participants"])) != len(event["participants"]):
        raise ValueError("Duplicate participants")
    timestamp(event["starts_at"])
    questions = []
    for participant in event["participants"]:
        for metric, unit in SPORT_METRICS[sport].items():
            questions.append({
                "question_id": f"{sport}.{metric}.recent",
                "event_id": event["id"], "sport": sport,
                "subject_id": participant,
                "target": {"variable": metric, "unit": unit, "operator": "none",
                           "threshold": None},
                "scope": {"recent_completed_n": 10, "venue": "any"},
                "text": f"¿Cuál es el promedio y dispersión de {metric} de {participant} "
                        f"en sus últimos 10 eventos comparables anteriores a {event['starts_at']}?",
            })
    return questions


def _qualifies(value: float, operator: str, threshold) -> bool:
    if operator == "none":
        raise ValueError("none has no threshold test")
    if operator == "between":
        if not isinstance(threshold, (tuple, list)) or len(threshold) != 2:
            raise ValueError("between requires two thresholds")
        lo, hi = threshold
        if not all(isinstance(x, (int, float)) and isfinite(x) for x in (lo, hi)) or lo > hi:
            raise ValueError("Invalid interval")
        return lo <= value <= hi
    if not isinstance(threshold, (int, float)) or not isfinite(threshold):
        raise ValueError("Finite numeric threshold required")
    return {"gt": lambda: value > threshold, "gte": lambda: value >= threshold,
            "lt": lambda: value < threshold, "lte": lambda: value <= threshold,
            "eq": lambda: value == threshold}[operator]()


def analyze(event: dict, question: dict, observations: list[dict], *, as_of: str,
            min_observations: int = 5) -> dict:
    """Only pre-cutoff completed observations count; no predictive claims."""
    cutoff = timestamp(as_of)
    starts = timestamp(event["starts_at"])
    if cutoff > starts:
        raise ValueError("Pregame cutoff must not exceed event start")
    if min_observations < 2:
        raise ValueError("min_observations must be >=2")
    if question["event_id"] != event["id"] or question["sport"] != event["sport"]:
        raise ValueError("Question/event mismatch")
    subject = question["subject_id"]
    if subject not in event["participants"]:
        raise ValueError("Subject not in event")
    target = question["target"]
    metric = target["variable"]
    if metric not in SPORT_METRICS.get(event["sport"], {}):
        raise ValueError("Unsupported sport/metric")
    if target["unit"] != SPORT_METRICS[event["sport"]][metric]:
        raise ValueError("Metric unit mismatch")
    operator = target["operator"]
    if operator not in OPERATORS:
        raise ValueError("Unsupported operator")
    threshold = target.get("threshold")
    if operator != "none":
        _qualifies(0.0, operator, threshold)
    elif threshold is not None:
        raise ValueError("No threshold permitted for none")
    scope = question["scope"]
    window = scope["recent_completed_n"]
    if not isinstance(window, int) or isinstance(window, bool) or window < 1:
        raise ValueError("Invalid window")
    venue = scope.get("venue", "any")
    if venue not in {"any", "home", "away"}:
        raise ValueError("Invalid venue")
    valid = []
    seen = set()
    for row in observations:
        if row["subject_id"] != subject:
            continue
        if row.get("event_id") == event["id"]:
            raise ValueError("Target event appears in observations")
        if row.get("sport") != event["sport"]:
            raise ValueError("Observation sport mismatch")
        if row.get("status") != "completed":
            continue
        when = timestamp(row["completed_at"])
        if when >= cutoff:
            continue
        if venue != "any" and row.get("venue") != venue:
            continue
        key = row["event_id"]
        if key in seen:
            raise ValueError("Duplicate completed event")
        seen.add(key)
        if not row.get("source_url") or not row.get("source_id"):
            continue
        if row.get("unit") != target["unit"]:
            raise ValueError("Observation unit mismatch")
        value = row.get("value")
        if not isinstance(value, (int, float)) or isinstance(value, bool) or not isfinite(value):
            continue
        if value < 0 or (metric == "finish_position" and value < 1):
            raise ValueError("Invalid observed metric")
        valid.append((when, float(value), row))
    valid.sort(key=lambda r: r[0], reverse=True)
    selected = valid[:window]
    values = [x[1] for x in selected]
    n = len(values)
    descriptive = n >= min_observations
    observed_rate = (sum(_qualifies(x, operator, threshold) for x in values) / n
                     if descriptive and operator != "none" else None)
    status = "descriptive_only" if descriptive else "insufficient_evidence"
    avg = mean(values) if descriptive else None
    return {
        "event_id": event["id"], "question_id": question["question_id"],
        "as_of": as_of, "status": status, "target": target,
        "answer": {
            "headline": (f"Promedio observado: {avg:.2f} {target['unit']} ({n} eventos)"
                         if descriptive else "Evidencia insuficiente para cuantificar"),
            "measurable_result": {"value": avg, "unit": target["unit"],
                                  "kind": "observed_mean" if descriptive else None},
        },
        "observed": {"sample_n": n, "requested_n": window,
                     "mean": avg, "median": median(values) if descriptive else None,
                     "sample_std": stdev(values) if descriptive else None,
                     "observed_hit_rate": observed_rate},
        "prediction": {"point_estimate": None, "event_probability": None,
                       "interval": None, "calibrated": False},
        "drivers": [], "scenarios": [],
        "evidence": [{"source_id": x[2]["source_id"], "url": x[2]["source_url"],
                      "event_id": x[2]["event_id"], "completed_at": x[2]["completed_at"]}
                     for x in selected],
        "validation": {"method": None, "sample_n": None, "metrics": {}, "baseline": None},
        "limitations": ["Solo estadística descriptiva; no equivale a predicción validada",
                        "No se han ajustado calidad del rival ni condiciones latentes"]
                       + ([f"Cobertura incompleta: {n}/{window}"] if n < window else []),
        "audit": {"cutoff": as_of, "formula_version": "sigma-q/1",
                  "source_snapshot_ids": [x[2]["source_id"] for x in selected]},
    }
