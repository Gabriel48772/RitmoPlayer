# 🎵 RitmoPlayer - Reproductor de Música para Android

[![Release](https://img.shields.io/github/v/release/Gabriel48772/RitmoPlayer?color=06B6D4&label=Versi%C3%B3n)](https://github.com/Gabriel48772/RitmoPlayer/releases)
[![Licencia](https://img.shields.io/badge/Licencia-MIT-purple.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20%28API%2024%2B%29-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

**RitmoPlayer** es un reproductor de audio moderno, ligero y gratuito desarrollado nativamente en **Kotlin** y **Jetpack Compose** para teléfonos móviles Android. Diseñado para ofrecer la máxima fidelidad sonora con el menor consumo de batería del mercado.

---

## ✨ Características Principales

- 🔋 **Ahorro Extremo de Batería (Bajo Consumo)**:
  - Desarrollado con **AndroidX Media3 (ExoPlayer)** y decodificación de audio por hardware en segundo plano (*Audio Offload*).
  - No mantiene la CPU encendida innecesariamente; el *WakeLock* se libera de inmediato al pausar.
  - Interfaz oscura optimizada para pantallas **AMOLED** (los píxeles negros no consumen energía).

- 🎧 **Soporte Universal de Formatos de Audio**:
  - Reproduce sin conversiones: **MP3, FLAC, WAV, AAC, M4A, OGG, OPUS, WMA**.

- ⏪ **Regla Especial de Retraso / Reinicio**:
  - Si la canción va por la **mitad o más (≥ 50%)**, al presionar el botón de **Retrasar** (o *Anterior*), la canción **se reinicia automáticamente desde el inicio (0:00)** manteniendo la reproducción activa.
  - Si la canción aún no ha llegado a la mitad, retrocede 10 segundos para un ajuste fino del audio.

- 📅 **Orden por Última Fecha de Modificación**:
  - Tu música se organiza de forma automática colocando arriba las canciones **más recientemente agregadas o modificadas** a tu teléfono.
  - También incluye opciones rápidas para ordenar por título (A-Z) o por artista.

- 📁 **Selector de Ubicación / Carpetas**:
  - Puedes elegir de dónde cargar la música:
    - Ver todas las canciones del teléfono.
    - Filtrar por carpetas detectadas (ej. *Download*, *Music*, *WhatsApp Audio*).
    - **Examinar y seleccionar cualquier carpeta específica** de tu almacenamiento interno o tarjeta SD mediante el selector de carpetas del sistema (*Storage Access Framework*).

- 🔀 **Modos de Reproducción Completos**:
  - **Aleatorio (Shuffle)**: Mezcla dinámica de canciones.
  - **Repetición**: Sin repetición, Repetir toda la lista o Repetir la canción actual.
  - **Controles**: Pausar, Reproducir, Adelantar (+10s), Retrasar (-10s / Reiniciar), Siguiente y Anterior.
  - Barra de búsqueda instantánea para encontrar cualquier tema en milisegundos.

- 🎨 **Logotipo e Icono Distintivo**:
  - Icono adaptativo moderno con diseño de auriculares holográficos de neón, ecualizador dinámico y el símbolo universal de *Play*, fácilmente identificable en la pantalla de inicio de tu celular.

- 🔄 **Actualizaciones Automáticas desde GitHub (In-App Updates)**:
  - Al iniciar la app o presionar el botón de actualizar en la barra superior, la app consulta la API pública de GitHub.
  - Si publicas una nueva versión en este repositorio, la app muestra un aviso con las novedades y un botón de **"Actualizar ahora"**.
  - Descarga el archivo `.apk` directamente desde GitHub Releases e inicia la instalación en tu teléfono en un solo toque.

---

## 🚀 Instalación en tu Celular Android

Puedes descargar el archivo instalador (.apk) de cualquiera de las siguientes formas:

1. **Descarga Directa desde el Repositorio:**
   - Haz clic en [**`apk/RitmoPlayer-v1.0.3.apk`** (Versión 1.0.3)](https://github.com/Gabriel48772/RitmoPlayer/raw/main/apk/RitmoPlayer-v1.0.3.apk) para descargarlo directamente a tu teléfono móvil.

2. **O desde la sección de Releases:**
   - Ve a **[Releases](https://github.com/Gabriel48772/RitmoPlayer/releases)** y descarga **`RitmoPlayer-v1.0.3.apk`**.

Una vez descargado en tu teléfono móvil:
- Toca el archivo descargado y presiona **Instalar** (o actualizar).
  *(Si es la primera vez que instalas un APK externo, el sistema Android te solicitará conceder permiso para instalar apps desconocidas).*
- ¡Abre **RitmoPlayer**, autoriza el acceso a la música y las notificaciones para disfrutar de tus canciones favoritas!

---

### 📝 Notas de la Versión v1.0.3
- 🛠️ **Solución al Cierre Inesperado (Crash) al reproducir:**
  - Se desacopló el servicio de segundo plano a un `MusicService` dedicado con arranque inmediato en primer plano (`startForeground`), evitando el límite de tiempo de 5 segundos de Android 12+.
  - Se crearon iconos vectoriales monocromáticos compatibles al 100% con el sistema de notificaciones de Android, previniendo excepciones de visualización en la barra de estado.
- 🔔 **Reproductor en las Notificaciones de Android:**
  - Muestra título, artista y barra de progreso continua en tiempo real.
  - Controles integrados: Pausa/Reanudar, Retrasar (-10s / Reinicio si ≥ 50%), Adelantar (+10s) y Siguiente canción.
- ⭐ **Carpeta Favorita y Reproducción Continua:**
  - Selección persistente de carpeta preferida.
  - Al terminar una canción, pasa automáticamente a la siguiente de la carpeta o lista seleccionada.

---

### 📝 Notas de la Versión v1.0.2
- 🔔 **Reproductor en las Notificaciones de Android:**
  - Se implementó el servicio en primer plano con `DefaultMediaNotificationProvider` y canal de notificación dedicado.
  - La barra de notificaciones y la pantalla de bloqueo muestran la canción en reproducción, el artista, la barra interactiva de progreso temporal y los botones para **Pausar**, **Retrasar (-10s)**, **Adelantar (+10s)**, **Anterior** y **Siguiente**.
- ⭐ **Carpeta Favorita Persistente:**
  - Al seleccionar una carpeta para escuchar música, la app la recuerda y la mantiene guardada como tu carpeta favorita (con indicador de estrella ⭐) incluso después de cerrar y volver a abrir la aplicación.
- 🔁 **Reproducción Continua de la Carpeta:**
  - Al reproducir canciones de la carpeta elegida, continúa automáticamente canción tras canción sin detenerse hasta llegar a la última y reiniciar el ciclo continuamente.

---

### 📝 Notas de la Versión v1.0.1
- **Ajuste de pantalla e Insets del Sistema:** Se añadió `navigationBarsPadding()` a la barra inferior de reproducción (*MiniPlayer*) y al reproductor de pantalla completa, elevando los controles para que los botones táctiles de navegación del teléfono no interfieran con la barra de música.

---

## 🛠️ Cómo Lanzar Nuevas Versiones y Actualizaciones

Para que la app detecte una nueva versión y permita a los usuarios actualizarse automáticamente desde GitHub:

1. Incrementa la versión en `app/build.gradle.kts`:
   ```kotlin
   versionCode = 2
   versionName = "1.0.1"
   ```
2. Crea y sube un nuevo tag en Git:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
3. La acción automática de GitHub Actions compilará el `.apk` optimizado y creará un **GitHub Release** público con el archivo adjunto.
4. Cuando abras la app en tu teléfono, **RitmoPlayer** mostrará automáticamente el mensaje de actualización disponible para instalarla al instante.

---

## 🏗️ Tecnologías Utilizadas

- **Lenguaje**: Kotlin 2.x
- **Framework de UI**: Jetpack Compose & Material 3
- **Motor de Audio**: AndroidX Media3 (ExoPlayer 1.5.1 + MediaSessionService)
- **Acceso a Archivos**: MediaStore API + Storage Access Framework (SAF)
- **Gestión de Versiones**: GitHub REST API v3 + Android PackageInstaller (FileProvider)
- **Compilador y Herramientas**: Android SDK 36 (Android 16), Gradle 9.1
