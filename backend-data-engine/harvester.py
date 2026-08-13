import os
import sys
from sqlalchemy import create_engine, text
from datetime import datetime, timedelta
import logging
import random

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

class PropsBRHarvester:
    def __init__(self, db_url="sqlite:///db/propsbr_warehouse.db"):
        self.engine = create_engine(db_url)
        self.init_db()

    def init_db(self):
        schema_path = os.path.join(os.path.dirname(__file__), "db/schema.sql")
        if os.path.exists(schema_path):
            with open(schema_path, "r") as f:
                schema_sql = f.read()
            with self.engine.connect() as conn:
                for statement in schema_sql.split(";"):
                    if statement.strip():
                        conn.execute(text(statement))
                conn.commit()
            logger.info("Base de datos Cloud v2.0 inicializada.")

    def seed_data(self):
        now_ms = int(datetime.now().timestamp() * 1000)
        with self.engine.connect() as conn:
            # 1. Competiciones
            leagues = [
                ('L01', 'Premier League', 'England', 'League'),
                ('L02', 'La Liga', 'Spain', 'League'),
                ('C01', 'CONCACAF Central Cup', 'International', 'Cup')
            ]
            for lid, name, country, ltype in leagues:
                conn.execute(text(f"INSERT OR REPLACE INTO competitions (competition_id, name, country, type, last_updated) VALUES ('{lid}', '{name}', '{country}', '{ltype}', {now_ms})"))

            # 2. Equipos
            teams = [('PA', 'Plaza Amador'), ('XEL', 'Xelajú MC'), ('MCU', 'Man Utd'), ('MCI', 'Man City'), ('RMA', 'Real Madrid'), ('BAR', 'Barcelona')]
            for tid, name in teams:
                conn.execute(text(f"INSERT OR REPLACE INTO teams (team_id, name, last_updated) VALUES ('{tid}', '{name}', {now_ms})"))

            # 3. Partidos con Meta-Predicciones
            matches = [
                ('M01', 'C01', 'PA', 'XEL', 1, 1.85, 1.10, "1-1", 0.40, 0.30, 0.30, 0.45, 0.25, 0.30),
                ('M02', 'L01', 'MCU', 'MCI', 2, 1.45, 2.10, "1-2", 0.25, 0.25, 0.50, 0.30, 0.20, 0.50),
                ('M03', 'L02', 'RMA', 'BAR', 3, 1.95, 1.80, "2-1", 0.45, 0.25, 0.30, 0.60, 0.15, 0.25)
            ]

            for mid, cid, home, away, days, hxg, axg, f_sc, f1, fx, f2, s1, sx, s2 in matches:
                match_date = (datetime.now() + timedelta(days=days)).strftime('%Y-%m-%dT%H:%M:%S')
                conn.execute(text(f"""
                    INSERT OR REPLACE INTO matches (
                        match_id, competition_id, season, match_date, home_team_id, away_team_id,
                        status, home_xg, away_xg, forebet_score, forebet_prob_1, forebet_prob_x, forebet_prob_2,
                        sofa_votes_home, sofa_votes_draw, sofa_votes_away, last_updated
                    )
                    VALUES ('{mid}', '{cid}', 2024, '{match_date}', '{home}', '{away}', 'SCHEDULED',
                            {hxg}, {axg}, '{f_sc}', {f1}, {fx}, {f2}, {s1}, {sx}, {s2}, {now_ms})
                """))

            conn.commit()
            logger.info("Knowledge Base Meta-Aggregator poblada.")

    def run_daily_sync(self):
        self.seed_data()

if __name__ == "__main__":
    harvester = PropsBRHarvester()
    harvester.run_daily_sync()
