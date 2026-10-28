# Imágenes del README

`render.py` dibuja las imágenes de `docs/screenshots/` a partir del diseño del launcher:
mismas medidas (dp) y colores que el código (`res/values/colors.xml`, `HomeActivity`, `CellLayout`,
`BubbleTextView`, `AllAppsContainer`, `Folder`…), a 360×800 dp con densidad 2 → **720×1600 px**,
la pantalla del Xiaomi 24044RN32L.

**No son capturas del teléfono.** Las apps, sus iconos y el widget de reloj son de ejemplo:

- Los iconos son glifos genéricos de [Material Icons](https://github.com/google/material-design-icons) (Google, Apache 2.0).
- La letra es [Roboto](https://github.com/googlefonts/roboto) (Apache 2.0).
- La barra de estado y la de navegación (3 botones) imitan las del sistema.

## Regenerarlas

Requisitos: Python 3 con Playwright (Chromium) y Pillow, y `npm` para bajar iconos y letra:

```bash
cd /tmp
npm pack @material-design-icons/svg@0.14.13 && mkdir -p mdi && tar -xzf material-design-icons-svg-0.14.13.tgz -C mdi
npm pack @fontsource/roboto@5.1.0 && tar -xzf fontsource-roboto-5.1.0.tgz
cd -   # vuelve a la raíz del repositorio
MDI_DIR=/tmp/mdi/package/filled ROBOTO_DIR=/tmp/package/files python3 docs/preview/render.py
```

Genera `00-portada.png` (1280×640, sirve también como imagen para compartir el repositorio)
y las cuatro pantallas, y las reduce a 256 colores (~140 KB cada una).
