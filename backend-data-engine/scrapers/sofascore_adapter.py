import random
import logging

logger = logging.getLogger(__name__)

class SofaScoreAdapter:
    """
    Adapter para integrar datos de SofaScore.
    En una fase real, esto consultaría:
    api.sofascore.com/api/v1/event/{id}/vote
    """
    def __init__(self):
        self.source = "SofaScore"

    def get_market_sentiment(self, home_team, away_team):
        """
        Simula o captura la tendencia del público (Votos).
        """
        # Para propósitos de desarrollo de la UI, generamos un sentimiento coherente
        # En producción, esto requiere el mapping de IDs de SofaScore.
        v1 = random.randint(30, 60)
        vx = random.randint(10, 30)
        v2 = 100 - v1 - vx

        return {
            "source": self.source,
            "votes_home": v1 / 100.0,
            "votes_draw": vx / 100.0,
            "votes_away": v2 / 100.0,
            "momentum": random.choice(["HIGH_HOME", "NEUTRAL", "HIGH_AWAY"]),
            "status": "LIVE_FEED_SIMULATED"
        }
