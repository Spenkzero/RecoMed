# Sitio web de RecoMed

Página promocional estática, adaptable a celulares y computadoras. No necesita backend ni compilación. El workflow `.github/workflows/pages.yml` publica automáticamente la carpeta `web/` al hacer push a `main` (debes habilitar GitHub Actions/Pages en el repositorio).

La página enlaza al repositorio oficial de RecoMed en GitHub. Las vistas de las funciones son ilustrativas; para usar la aplicación, descarga el APK desde el botón de la página.

El logo compartido por todas las páginas está en `images/recomed-logo.png`; mantenlo dentro de `web/` para que también se muestre cuando el sitio esté publicado en otros dispositivos.

El botón de descarga sirve `downloads/recomed-android.apk`. Al publicar una versión nueva de la app, reemplaza ese archivo por el APK actualizado antes de desplegar la web. El APK de depuración incluido es para instalación manual y no para distribución en Google Play.

La app consulta `version.json` para avisar de nuevas versiones. Al publicar una actualización, aumenta `versionCode` y `versionName` en `android/app/build.gradle.kts` y en `version.json`, y reemplaza el APK antes de desplegar el sitio. La app descarga el APK solo tras la confirmación del usuario y deja la instalación final en manos de Android.
