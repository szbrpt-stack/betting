# Guía de Despliegue: PropsBR Cloud Engine

Sigue estos pasos para poner tu servidor en internet y conectar la aplicación.

## 1. Preparar el Repositorio
1. Crea un repositorio **privado** en GitHub.
2. Sube únicamente el contenido de la carpeta `backend-data-engine/`.
   - Asegúrate de incluir: `main.py`, `harvester.py`, `requirements.txt`, `Procfile` y la carpeta `db/`.

## 2. Desplegar en Render.com (Gratis)
1. Crea una cuenta en [Render.com](https://render.com/).
2. Haz clic en **"New"** -> **"Web Service"**.
3. Conecta tu cuenta de GitHub y selecciona el repositorio de PropsBR.
4. Configura los siguientes campos:
   - **Runtime**: `Python 3`.
   - **Build Command**: `pip install -r requirements.txt`.
   - **Start Command**: `gunicorn main:app -w 4 -k uvicorn.workers.UvicornWorker --bind 0.0.0.0:$PORT`.
5. Haz clic en **"Advanced"** -> **"Add Environment Variable"**:
   - `PYTHON_VERSION`: `3.10.0` (o superior).
6. Presiona **"Create Web Service"**.

## 3. Conectar la App
1. Render te dará una URL (ej: `https://propsbr-backend-abc.onrender.com/`).
2. Abre la App PropsBR en tu móvil.
3. Ve a **Configuración** (icono de engranaje).
4. Pega la URL completa en el campo **"Backend URL"**.
5. Presiona **"GUARDAR"**.
6. En la pantalla principal, presiona **"SINCRONIZAR PROPSBR CLOUD"**.

## 4. Verificar
- Verás cómo el contador de **Matches** sube a +20 y los **Teams** a +30.
- Entra en un partido como **Manchester United vs City** para ver los xG reales.
