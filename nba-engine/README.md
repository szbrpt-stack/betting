# S2S Sigma — NBA Engine

Prueba de concepto para validar modelos NBA sin depender de API keys comerciales.

## Objetivo

1. Evaluar procedencia, licencias y calidad de datasets descargables antes de utilizarlos.
2. Consolidar partidos históricos de NBA con identificadores estables y fechas.
3. Calcular variables utilizando exclusivamente partidos anteriores.
4. Entrenar modelos baseline de victoria local y total de puntos.
5. Evaluar por divisiones cronológicas y comparar con baselines triviales.

## Estado

- Esta rama no modifica el backend de fútbol existente ni el despliegue de Render.
- No se han incorporado datos de terceros ni entrenado un modelo NBA real.
- No conectar ni escribir en Supabase hasta revisar el esquema existente y los derechos de los datos.

## Próximos pasos

- Añadir pipeline offline y tests.
- Verificar licencia de cada fuente y sus datos subyacentes.
- Ejecutar entrenamiento reproducible y publicar métricas fuera de muestra.
