import requests
from bs4 import BeautifulSoup
import logging
from datetime import datetime

logger = logging.getLogger(__name__)

class ForebetAggregator:
    def __init__(self):
        self.base_url = "https://www.forebet.com/en/football-tips-and-predictions-for-today"
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
        }

    def fetch_today_predictions(self):
        """
        Extrae predicciones de Forebet para el día actual.
        Para propósitos educativos: captura 1X2 y Marcador Correcto.
        """
        logger.info("Conectando con Forebet...")
        try:
            response = requests.get(self.base_url, headers=self.headers, timeout=15)
            if response.status_code != 200:
                logger.error(f"Error al acceder a Forebet: {response.status_code}")
                return []

            soup = BeautifulSoup(response.text, 'html.parser')
            predictions = []

            # Buscamos los contenedores de partidos
            rows = soup.select('.predict')
            for row in rows:
                try:
                    home_team = row.select_one('.homeTeam').text.strip()
                    away_team = row.select_one('.awayTeam').text.strip()

                    # Probabilidades 1X2
                    probs = row.select('.fp')
                    # Típicamente: index 0=1, 1=X, 2=2
                    p1 = float(probs[0].text.strip()) if len(probs) > 0 else 0
                    px = float(probs[1].text.strip()) if len(probs) > 1 else 0
                    p2 = float(probs[2].text.strip()) if len(probs) > 2 else 0

                    # Marcador Correcto Proyectado
                    correct_score = row.select_one('.ex_sc').text.strip() if row.select_one('.ex_sc') else "0-0"

                    predictions.append({
                        "home": home_team,
                        "away": away_team,
                        "forebet_1": p1 / 100.0,
                        "forebet_x": px / 100.0,
                        "forebet_2": p2 / 100.0,
                        "forebet_score": correct_score,
                        "source": "Forebet",
                        "timestamp": datetime.now().isoformat()
                    })
                except Exception as e:
                    continue

            logger.info(f"Se han extraído {len(predictions)} predicciones de Forebet.")
            return predictions
        except Exception as e:
            logger.error(f"Fallo crítico en ForebetAggregator: {e}")
            return []

if __name__ == "__main__":
    agg = ForebetAggregator()
    print(agg.fetch_today_predictions()[:2]) # Ver primeros 2
