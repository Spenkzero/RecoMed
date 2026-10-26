# Sitio web de RecoMed

Página promocional estática, adaptable a celulares y computadoras. No necesita backend ni compilación. El workflow `.github/workflows/pages.yml` publica automáticamente la carpeta `web/` al hacer push a `main` (debes habilitar GitHub Actions/Pages en el repositorio).

Edita `index.html` para cambiar el enlace genérico de GitHub por la URL real del repositorio.

El logo compartido por todas las páginas está en `images/recomed-logo.png`; mantenlo dentro de `web/` para que también se muestre cuando el sitio esté publicado en otros dispositivos.

El botón de descarga sirve `downloads/recomed-android.apk`. Al publicar una versión nueva de la app, reemplaza ese archivo por el APK actualizado antes de desplegar la web. El APK de depuración incluido es para instalación manual y no para distribución en Google Play.
