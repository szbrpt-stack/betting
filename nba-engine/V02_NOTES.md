# NBA v0.2 — walk-forward por temporadas

Uso personal y experimental. Ejecutar:
```bash
python download_games.py --out data/games.csv
python -m nba_engine.v02 --csv data/games.csv --out outputs/v02
```

**Metodología:** se entrena en temporadas anteriores y se evalúa en la temporada siguiente; luego se amplía la ventana de entrenamiento. No se usa el conjunto de prueba para ajustar hiperparámetros. Los partidos del mismo día se procesan como lote, sin filtrar resultados del día al calcular variables.

**Variables actuales:** puntos a favor/en contra de los últimos 10 partidos, porcentaje de victorias y descanso. No llamamos a estas variables eficiencia por posesión ni ritmo de juego; para esas métricas necesitamos posesiones o estadísticas de tiro/rebote/pérdidas con calidad verificada.

**Limitaciones:** la definición de temporada octubre-septiembre es aproximada y puede incluir pretemporada/playoffs según la fuente. Falta un control de calidad de cobertura, IDs históricos y disponibilidad temporal. Sin cuotas históricas no hay evaluación de valor frente al mercado.

**Criterio:** revisar cada temporada individualmente, Brier/log loss/MAE, y solo después ampliar variables.
