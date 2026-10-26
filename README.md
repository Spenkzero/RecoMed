# RecoMed

RecoMed es un proyecto educativo de recordatorios de medicamentos. Incluye una aplicación nativa para Android (Kotlin) y una página web promocional estática.

## Contenido

- `android/`: proyecto Android Studio instalable mediante compilación APK.
- `web/`: página promocional adaptable a celulares y computadoras.
- `LICENSE`: licencia MIT para el código del proyecto.
- `PRIVACY.md`: explicación básica de privacidad.

## Funciones de la app Android

- Registrar un medicamento, dosis o indicación y hora del recordatorio.
- Activar o desactivar recordatorios.
- Eliminar medicamentos registrados.
- Mostrar notificaciones de recordatorio.
- Guardar la configuración localmente en el dispositivo.
- Programar recordatorios diarios.

**Importante:** RecoMed es una herramienta de recordatorio, no una indicación médica. La persona debe seguir la receta y las indicaciones de su profesional de salud. La aplicación inicial no sincroniza datos ni reemplaza la consulta médica.

## Cómo abrir y compilar la app

1. Instala Android Studio y un JDK 17.
2. Abre la carpeta `android/` desde Android Studio.
3. Espera a que Gradle sincronice el proyecto.
4. Conecta un teléfono Android con depuración USB activada o inicia un emulador.
5. Pulsa **Run** para instalar la app, o ejecuta desde la terminal:

   ```bash
   cd android
   ./gradlew assembleDebug
   ```

   El APK de depuración, cuando la compilación termine correctamente, queda en:
   `android/app/build/outputs/apk/debug/app-debug.apk`

   Si el proyecto no incluye el wrapper de Gradle generado por Android Studio, abre el proyecto en Android Studio y permite que genere/ configure Gradle Wrapper, o usa una instalación local de Gradle.

## Permisos y alarmas

En Android 13 o posterior se solicita permiso para publicar notificaciones. En Android 12 o posterior el sistema puede requerir que se habiliten las alarmas exactas desde Ajustes. RecoMed explica ese paso dentro de la app cuando corresponde. Los fabricantes pueden limitar procesos en segundo plano; conviene probar el comportamiento en el dispositivo de destino.

## Publicar en GitHub

El código fuente está en [github.com/Spenkzero/RecoMed](https://github.com/Spenkzero/RecoMed) y la web publicada en [spenkzero.github.io/RecoMed](https://spenkzero.github.io/RecoMed/). Para publicar los cambios locales:

1. Confirma los cambios que quieras publicar en la rama `main`.
2. Sube los commits al repositorio existente:

   ```bash
   git push origin main
   ```

3. La acción **Publish promotional website** despliega automáticamente el contenido de `web/` en GitHub Pages. En **Settings → Pages**, la fuente de publicación debe ser **GitHub Actions**. Revisa la pestaña **Actions** y espera a que la ejecución finalice correctamente antes de compartir la web.

## Base del contenido promocional

La web usa la misión, visión, valores, ubicación, método de recolección de datos y análisis FODA del documento de presentación compartido. El PDF no aportaba información utilizable en las secciones de costos y conclusiones; por eso la página no inventa cifras ni atribuye resultados de encuestas.
