# Fuente de datos — auditoría previa a la ingesta

## Candidato A: hamzas/nba-games
- URL: https://huggingface.co/datasets/hamzas/nba-games
- Archivo: games.csv (aprox. 4,5 MB), 29.800 filas; actualización declarada 2025-02-13.
- Campos compatibles: GAME_ID, GAME_DATE_EST, HOME_TEAM_ID, VISITOR_TEAM_ID, PTS_home, PTS_away.
- Procedencia declarada: NBA.com/stats, vía dataset original Nathan Lauga.
- **Estado legal: PENDIENTE**. La disponibilidad pública no concede licencia comercial ni para apuestas.
- No descargar automáticamente ni distribuir en el repositorio hasta revisar derechos de origen.

## Candidato B: roham/every-game-ever
- URL: https://github.com/roham/every-game-ever
- Describe Parquet de fechas, equipos y marcadores; incluye documentos de procedencia y límites.
- El código se declara MIT; ello no certifica derechos de todos los datos subyacentes.
- **Estado legal: PENDIENTE**. Revisar docs/DATA-LICENSE.md y fuente de origen.

## Política de incorporación
1. Registrar URL, versión, SHA-256, fecha de descarga, licencia y restricciones.
2. Verificar duplicados, marcadores válidos, identificadores y cobertura por temporada.
3. Mantener datos originales fuera de Git; no subir información con derechos dudosos.
4. No publicar predicciones comerciales hasta resolver el uso permitido.
5. Para medir ROI se necesitan cuotas históricas y timestamps con derechos de uso.
