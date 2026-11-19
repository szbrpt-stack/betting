# S2S Sigma para Android

Cliente Android nativo, oscuro y orientado a evidencia para recorrer el flujo:

`Deporte → Competición → Evento → Preguntas SIGMA-Q → Investigación → Resultado`

La aplicación ofrece dos modos separados:

- **DEMO** usa exclusivamente `MockResearchGateway` y marca todos los datos sintéticos.
- **IA Real** usa `OpenAIResearchGateway` contra un conector HTTPS. El conector emplea Responses API, búsqueda web y Structured Outputs; SIGMA-Q conserva localmente el cálculo y la validación determinista.

IA Real nunca cae automáticamente a DEMO. Los fallos de configuración, autenticación, conexión y formato se muestran al usuario y no producen eventos inventados.

Además, la pantalla de Inicio permite importar investigaciones JSON generadas externamente. La importación no cambia el modo DEMO u OPENAI: valida, muestra una vista previa, conserva la procedencia en Room y entrega las observaciones directamente al motor determinista SIGMA-Q.

## Importar una investigación JSON

1. En Inicio, pulsa **Importar investigación JSON**.
2. Selecciona un archivo compatible con el contrato v1.
3. Revisa evento, participantes, preguntas, observaciones, fuentes y errores de validación.
4. Pulsa **Importar y abrir evento**.
5. Selecciona una pregunta y ejecuta SIGMA-Q. Para preguntas importadas no se realiza una consulta de red: se analiza la evidencia contenida en el documento.

El archivo de referencia exacto está en [`examples/s2s-research-import-v1.json`](examples/s2s-research-import-v1.json). El contrato raíz contiene `schemaVersion`, `documentId`, `provenance`, `event` e `investigations`; cada investigación contiene un `ResearchRequest` y su `ResearchResponse` correspondiente.

Los identificadores deben ser estables, las fechas deben usar ISO-8601 con zona horaria, las fuentes deben usar HTTPS y cada observación debe coincidir con la variable/unidad de su pregunta y referenciar una fuente declarada. PDF todavía no se interpreta ni se importa.

## Requisitos verificados en el entorno de desarrollo

- Android Studio con JDK 21.
- Android SDK Platform 36 y Build Tools 36.0.0.
- Gradle 8.13 (wrapper incluido).
- Android Gradle Plugin 8.13.2.
- Kotlin/Compose plugin 2.2.10.

Las demás versiones están fijadas en `app/build.gradle.kts`: Compose BOM 2025.10.01, Navigation Compose 2.9.5 y Room 2.8.5.

## Abrir y ejecutar

1. Abre la carpeta `android-app/` en Android Studio.
2. Espera a que finalice la sincronización de Gradle.
3. Selecciona un emulador o dispositivo con Android 8.0 (API 26) o superior.
4. Ejecuta la configuración `app`.

En PowerShell, desde `android-app/`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\malam\AppData\Local\Android\Sdk'
.\gradlew.bat assembleDebug testDebugUnitTest
```

Para compilar y ejecutar las pruebas instrumentadas de navegación/UI y Room:

```powershell
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

La segunda orden requiere un emulador o dispositivo conectado.

## Arquitectura

- `data/`: contratos serializables universales y fixtures DEMO.
- `data/ResearchImport.kt`: contrato versionado, decodificación y validación de investigaciones externas.
- `research/`: interfaz `ResearchGateway` y `MockResearchGateway` reemplazable.
- `research/OpenAIResearchGateway.kt`: cliente del conector seguro, sin claves privadas en Android.
- `domain/`: validaciones de fuentes, corte temporal, unidades y motor descriptivo SIGMA-Q.
- `storage/`: Room/SQLite para competiciones, eventos, favoritos, investigaciones, fuentes, resultados y caché diaria.
- `ui/`: MVVM, Navigation Compose, diez pantallas obligatorias, barra inferior y tema Material 3.

El motor conserva la regla del repositorio: una frecuencia o media observada no se presenta como probabilidad futura. Sin validación fuera de muestra, `estimate` e `uncertaintyInterval` permanecen nulos y el dictamen se limita a `DESCRIPTIVE` o `INSUFFICIENT`.

## Pruebas

Las pruebas JVM cubren:

- contrato de navegación;
- filtros por deporte, competición, participante y estado;
- contratos JSON y eventos con múltiples participantes;
- validación de fuentes y corte temporal;
- datos faltantes;
- normalización de unidades compatibles;
- estadística descriptiva;
- abstención sin modelo validado.
- importación JSON válida e inválida y análisis del archivo de referencia.

`RoomPersistenceTest` cubre persistencia básica y `ImportPersistenceTest` verifica que evento, preguntas y observaciones importadas sobreviven al cierre y reapertura de Room.

## Activar investigación real

1. Ejecuta o despliega `secure-connector/` detrás de HTTPS.
2. Configura `OPENAI_API_KEY` únicamente en el entorno del servidor. Opcionalmente define `OPENAI_MODEL`.
3. En Perfil, activa **Modo IA Real** e introduce la URL HTTPS del conector. No introduzcas una API key en Android.
4. Busca una consulta, por ejemplo `¿Qué partidos de la NBA hay hoy?`.

Antes de exponer el conector públicamente se deben añadir autenticación de usuario o atestación, cuotas y límites de abuso. No uses una credencial estática embebida en el APK.

## Alcance de seguridad

El módulo es autónomo dentro de `android-app/`. No modifica `nba-engine/`, `backend-data-engine/`, servicios de producción, infraestructura ni la rama `main`. `secure-connector/` está preparado pero no desplegado.
