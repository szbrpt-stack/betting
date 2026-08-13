from fastapi import FastAPI, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import create_engine, text
from harvester import PropsBRHarvester
import os
from datetime import datetime
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("PropsBR-Aggregator")

app = FastAPI(title="PropsBR Meta-Aggregator", version="1.5.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///db/propsbr_warehouse.db")
# Asegurar que el path de la DB sea absoluto
if DATABASE_URL.startswith("sqlite:///"):
    db_rel_path = DATABASE_URL.replace("sqlite:///", "")
    db_abs_path = os.path.abspath(os.path.join(os.path.dirname(__file__), db_rel_path))

    # Crear carpeta db si no existe
    os.makedirs(os.path.dirname(db_abs_path), exist_ok=True)
    DATABASE_URL = f"sqlite:///{db_abs_path}"

engine = create_engine(DATABASE_URL)
harvester = PropsBRHarvester(db_url=DATABASE_URL)

@app.get("/api/v1/full-catalog")
async def get_full_catalog():
    with engine.connect() as conn:
        matches_raw = conn.execute(text("SELECT * FROM matches")).mappings().all()
        matches = []
        for row in matches_raw:
            m = dict(row)
            # Nesting for Android DTO compatibility
            m["forebet_prediction"] = {
                "score": m.get("forebet_score"),
                "prob_1": m.get("forebet_prob_1"),
                "prob_x": m.get("forebet_prob_x"),
                "prob_2": m.get("forebet_prob_2")
            }
            m["sofa_sentiment"] = {
                "votes_home": m.get("sofa_votes_home"),
                "votes_draw": m.get("sofa_votes_draw"),
                "votes_away": m.get("sofa_votes_away")
            }
            matches.append(m)

        teams = [dict(row) for row in conn.execute(text("SELECT * FROM teams")).mappings().all()]
        competitions = [dict(row) for row in conn.execute(text("SELECT * FROM competitions")).mappings().all()]
        player_stats = [dict(row) for row in conn.execute(text("SELECT * FROM player_match_stats")).mappings().all()]

    return {
        "timestamp": int(datetime.now().timestamp() * 1000),
        "matches": matches,
        "teams": teams,
        "competitions": competitions,
        "player_stats": player_stats
    }

@app.get("/api/v1/sync")
async def sync_data(last_updated: int = Query(0)):
    return await get_full_catalog()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", 8000)))
