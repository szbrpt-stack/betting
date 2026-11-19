# Conector seguro S2S Sigma → OpenAI

Este servicio mantiene `OPENAI_API_KEY` fuera del APK. La aplicación Android solo conoce la URL HTTPS del servicio.

## Ejecución local

1. Instala Node.js 20 o posterior.
2. Ejecuta `npm install` en esta carpeta.
3. Define `OPENAI_API_KEY` en el entorno del proceso. No uses el archivo `.env.example` para una clave real.
4. Opcionalmente define `OPENAI_MODEL`; el valor predeterminado es `gpt-5-mini`.
5. Ejecuta `npm start`.

Rutas:

- `GET /health`
- `POST /v1/discover-events`
- `POST /v1/research-question`

Para un teléfono físico se necesita desplegar este servicio detrás de HTTPS. Ese despliegue no está incluido ni autorizado. Antes de exponerlo públicamente también se debe añadir autenticación de usuario o atestación de aplicación, límites por usuario y registro de abuso; una credencial estática dentro del APK no es una solución segura.

El servicio usa Responses API, la herramienta `web_search`, Structured Outputs y exige fuentes web reales. SIGMA-Q permanece en Android y realiza el cálculo determinista.
