# Cambios

## 0.1.3
- Con el cajón abierto ya no se dibuja el escritorio debajo: sin iconos «fantasma» a través del fondo translúcido y menos trabajo de dibujo.
- La barra de arrastre dice «Info» en vez de «Información de la app», que no cabía en una línea.
- Durante el arrastre se oculta la barra de búsqueda que quedaba debajo de la barra de destinos.

## 0.1.2
- La barra Quitar/Info/Desinstalar ocupa una franja propia (que en reposo es la barra «Buscar apps») y ya no tapa la primera fila de iconos. En 0.1.1, mantener pulsado y soltar un icono de esa fila podía quitarlo del inicio.
- Deslizar arriba/abajo funciona también desde el dock y los puntos de página.
- El cajón se cierra deslizando hacia abajo aunque el gesto empiece sobre la lista.
- Barra de búsqueda arriba del escritorio.
- Widgets: si no se pueden cargar se muestra un recuadro provisional en vez de borrarlos; solo se borran si su app se desinstaló.
- Añadir un widget ya no falla si Android cerró el inicio mientras se pedía permiso o se configuraba.
- Un fallo pasajero al listar apps (lista vacía, perfil de trabajo en pausa) ya no borra iconos ni carpetas.
- Si una actualización cambia la actividad de inicio de una app, su icono se queda en su sitio (o en su carpeta).
- Restablecer y cambiar la cuadrícula a la vez aplica ambas cosas.
- Sin límite de páginas al recolocar iconos; soltar durante un cambio de página ya no los manda al borde.
- Un `layout.json` dañado se aparta como `layout.json.bad` en vez de sobrescribirse.

## 0.1.1
- Variante normal sin el servicio de insignias: Google Play Protect bloqueaba su instalación desde el gestor de archivos.
- Variante «insignias» para instalar con adb.

## 0.1.0
- Primera versión: escritorio con páginas, dock, cajón con búsqueda, carpetas, widgets, atajos de apps, insignias, fondo del sistema y ajustes. Sin permiso de internet.
