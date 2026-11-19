"""Question-driven event discovery with daily SQLite persistence.

An injected research function returns structured, source-backed answers.
No sports API, model API, scraping, or background polling is required here.
"""
from __future__ import annotations

import json
import sqlite3
from datetime import date, datetime, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

from .sigma_q import question_catalog, timestamp


class DiscoveryError(ValueError):
    pass


class DailyDiscovery:
    def __init__(self, path: str | Path):
        self.path = str(path)
        with sqlite3.connect(self.path) as db:
            db.execute("""CREATE TABLE IF NOT EXISTS daily_answers (
                day TEXT NOT NULL, timezone TEXT NOT NULL, query TEXT NOT NULL,
                payload TEXT NOT NULL, saved_at TEXT NOT NULL,
                PRIMARY KEY(day, timezone, query))""")
            db.execute("""CREATE TABLE IF NOT EXISTS event_analyses (
                event_id TEXT NOT NULL, question_id TEXT NOT NULL,
                subject_id TEXT NOT NULL, cutoff TEXT NOT NULL,
                payload TEXT NOT NULL, saved_at TEXT NOT NULL,
                PRIMARY KEY(event_id, question_id, subject_id, cutoff))""")

    @staticmethod
    def _day(value: str) -> str:
        try:
            return date.fromisoformat(value).isoformat()
        except ValueError as exc:
            raise DiscoveryError("Invalid ISO day") from exc

    @staticmethod
    def _zone(value: str) -> ZoneInfo:
        try:
            return ZoneInfo(value)
        except (KeyError, ValueError) as exc:
            raise DiscoveryError("Invalid timezone") from exc

    @classmethod
    def _verify(cls, answer: dict, day: str, zone: ZoneInfo) -> dict:
        if not isinstance(answer, dict) or not isinstance(answer.get("events"), list):
            raise DiscoveryError("Research answer must contain events array")
        verified = []
        seen = set()
        for event in answer["events"]:
            if not isinstance(event, dict):
                raise DiscoveryError("Invalid event")
            if not isinstance(event.get("id"), str) or not event["id"].strip():
                raise DiscoveryError("Event ID missing")
            if event["id"] in seen:
                raise DiscoveryError("Duplicate event ID")
            seen.add(event["id"])
            starts = timestamp(event["starts_at"])
            if starts.astimezone(zone).date().isoformat() != day:
                raise DiscoveryError("Event is outside requested local day")
            if event.get("sport") is None or not isinstance(event.get("participants"), list):
                raise DiscoveryError("Sport and participants required")
            if not isinstance(event.get("sources"), list) or not event["sources"]:
                raise DiscoveryError("At least one source required per event")
            for source in event["sources"]:
                if not isinstance(source, dict) or not isinstance(source.get("url"), str) or not source["url"].startswith(("https://", "http://")):
                    raise DiscoveryError("Source URL missing")
                timestamp(source["retrieved_at"])
            question_catalog(event)
            verified.append(event)
        return {"events": verified, "query": answer.get("query"), "day": day,
                "timezone": str(zone), "count": len(verified)}

    def discover(self, *, day: str, timezone_name: str, research, refresh: bool = False) -> dict:
        """research(prompt) must return a structured JSON-compatible dictionary.

        If cached, returns the daily answer without invoking research again.
        """
        day = self._day(day)
        zone = self._zone(timezone_name)
        query = f"¿Qué eventos deportivos hay el {day} en la zona {timezone_name}? Devuelve JSON con events: id, sport, starts_at ISO con zona, participants, sources con url y retrieved_at. No inventes eventos; devuelve lista vacía si no puedes verificarlos."
        with sqlite3.connect(self.path) as db:
            if not refresh:
                row = db.execute("SELECT payload FROM daily_answers WHERE day=? AND timezone=? AND query=?",
                                 (day, timezone_name, query)).fetchone()
                if row:
                    return dict(json.loads(row[0]), cache_hit=True)
        raw = research(query)
        verified = self._verify(raw, day, zone)
        verified["query"] = query
        saved_at = datetime.now(timezone.utc).isoformat()
        with sqlite3.connect(self.path) as db:
            db.execute("INSERT OR REPLACE INTO daily_answers VALUES (?,?,?,?,?)",
                       (day, timezone_name, query, json.dumps(verified, ensure_ascii=False), saved_at))
        return dict(verified, cache_hit=False)

    def questions(self, event: dict) -> list[dict]:
        return question_catalog(event)

    def save_analysis(self, analysis: dict) -> None:
        """Persist only audited SIGMA-Q responses, never arbitrary narrative."""
        required = ("event_id", "question_id", "as_of", "status", "target",
                    "observed", "prediction", "evidence", "audit")
        if any(key not in analysis for key in required):
            raise DiscoveryError("Incomplete SIGMA-Q analysis")
        timestamp(analysis["as_of"])
        if analysis["status"] not in {"descriptive_only", "conditional",
                                       "quantified_validated", "insufficient_evidence"}:
            raise DiscoveryError("Invalid analysis status")
        if analysis["status"] != "quantified_validated" and analysis["prediction"].get("event_probability") is not None:
            raise DiscoveryError("Unvalidated probability forbidden")
        if not analysis["audit"].get("formula_version"):
            raise DiscoveryError("Formula version required")
        subject = analysis.get("subject_id")
        if not subject:
            raise DiscoveryError("subject_id required for analysis persistence")
        with sqlite3.connect(self.path) as db:
            db.execute("INSERT OR REPLACE INTO event_analyses VALUES (?,?,?,?,?,?)",
                       (analysis["event_id"], analysis["question_id"], subject,
                        analysis["as_of"], json.dumps(analysis, ensure_ascii=False),
                        datetime.now(timezone.utc).isoformat()))

    def get_analysis(self, event_id: str, question_id: str, subject_id: str, cutoff: str):
        with sqlite3.connect(self.path) as db:
            row = db.execute("""SELECT payload FROM event_analyses
                WHERE event_id=? AND question_id=? AND subject_id=? AND cutoff=?""",
                (event_id, question_id, subject_id, cutoff)).fetchone()
        return json.loads(row[0]) if row else None
