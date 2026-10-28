#!/usr/bin/env python3
"""
Genera las imágenes del README: renders del diseño del launcher «Inicio».

Reproduce las medidas y colores del código (res/values/*.xml, HomeActivity, CellLayout,
BubbleTextView, AllAppsContainer, Folder…) a 360×800 dp con densidad 2 → 720×1600 px,
la resolución del Xiaomi 24044RN32L. Las apps, iconos y el widget de reloj son de ejemplo
(iconos genéricos de Material Icons, Apache 2.0); no son capturas reales.

Requisitos: Python 3 con Playwright (Chromium) y, para los iconos y la letra:
    npm pack @material-design-icons/svg@0.14.13 && tar -xzf material-design-icons-svg-0.14.13.tgz
    npm pack @fontsource/roboto@5.1.0          && tar -xzf fontsource-roboto-5.1.0.tgz
Uso:
    MDI_DIR=…/package/filled ROBOTO_DIR=…/package/files python3 docs/preview/render.py
"""
import html
import os
import re
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "screenshots"
MDI = Path(os.environ.get("MDI_DIR", "mdi/package/filled"))
ROBOTO = Path(os.environ.get("ROBOTO_DIR", "package/files"))

W, H, DPR = 360, 800, 2          # dp y densidad (720×1600 px)
STATUS, NAV = 32, 48             # barra de estado y navegación de 3 botones (dp)


# ----------------------------------------------------------------------------- recursos
def mdi_path(name):
    svg = (MDI / f"{name}.svg").read_text()
    return " ".join(re.findall(r'\sd="([^"]+)"', svg))


def drawable_path(name):
    """pathData de un vector de res/drawable (los iconos reales del launcher)."""
    xml = (ROOT / "res" / "drawable" / f"{name}.xml").read_text()
    return " ".join(re.findall(r'android:pathData="([^"]+)"', xml))


def color(name):
    """#AARRGGBB de res/values/colors.xml → rgba() de CSS."""
    xml = (ROOT / "res" / "values" / "colors.xml").read_text()
    v = re.search(rf'name="{name}">#([0-9A-Fa-f]{{8}})<', xml).group(1)
    a, r, g, b = (int(v[i:i + 2], 16) for i in (0, 2, 4, 6))
    return f"rgba({r},{g},{b},{a / 255:.3f})"


def svg(d, size, fill="#fff", vb=24):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 {vb} {vb}">'
            f'<path fill="{fill}" d="{d}"/></svg>')


# Apps de ejemplo: (glifo de Material Icons, color de fondo)
APPS = {
    "Ajustes": ("settings", "#5f6b7a"),
    "Archivos": ("folder", "#e9962a"),
    "Calculadora": ("calculate", "#4a4f57"),
    "Calendario": ("calendar_month", "#3d7cf2"),
    "Cámara": ("photo_camera", "#3b4a52"),
    "Clima": ("wb_sunny", "#f09a2d"),
    "Contactos": ("person", "#5c6bc0"),
    "Correo": ("mail", "#e5533d"),
    "Galería": ("photo_library", "#8e5cd9"),
    "Grabadora": ("mic", "#d84a5b"),
    "Libros": ("menu_book", "#2e9e6b"),
    "Linterna": ("flashlight_on", "#e0a31c"),
    "Mapas": ("map", "#34a56f"),
    "Mensajes": ("chat", "#2f80ed"),
    "Música": ("music_note", "#e3477a"),
    "Navegador": ("language", "#f08a24"),
    "Notas": ("edit_note", "#d9b020"),
    "Podcasts": ("podcasts", "#9b51e0"),
    "Radio": ("radio", "#ef6c3a"),
    "Reloj": ("schedule", "#263238"),
    "Teléfono": ("phone", "#27ae60"),
    "Tienda": ("storefront", "#1aa39a"),
    "Videollamadas": ("videocam", "#00a3d9"),
    "Vídeos": ("movie", "#e74c3c"),
}
FOLDER = ("Herramientas", ["Archivos", "Calculadora", "Grabadora", "Linterna", "Radio"])
DOCK = ["Teléfono", "Mensajes", "Navegador", "Cámara"]
# Página 1 del escritorio (4×5). La fila 0 la ocupa un widget de reloj de ejemplo (4×1).
PAGE = [
    ["Calendario", "Clima", "Correo", "Galería"],
    ["Música", "Mapas", "Notas", "Videollamadas"],
    ["Podcasts", "Tienda", "Libros", "@folder"],
    ["Contactos", "Ajustes", None, None],
]


# ----------------------------------------------------------------------------- piezas
def app_icon(name, size=48):
    glyph, bg = APPS[name]
    r = round(size * 0.29)
    return (f'<div class="icon" style="width:{size}px;height:{size}px;border-radius:{r}px;background:{bg}">'
            f'{svg(mdi_path(glyph), round(size * 0.54))}</div>')


def bubble(name, extra_style=""):
    """BubbleTextView: icono de 48 dp arriba (2 dp de margen) + nombre 12 sp con sombra."""
    return (f'<div class="bubble" style="{extra_style}">{app_icon(name)}'
            f'<div class="label">{html.escape(name)}</div></div>')


def folder_icon(title, items):
    """FolderIcon: cuadrado redondeado 12 dp (#55FFFFFF) con 4 miniaturas en 2×2."""
    inset, gap = 5, 3
    mini = (48 - inset * 2 - gap) / 2
    minis = []
    for i, n in enumerate(items[:4]):
        x = inset + (i % 2) * (mini + gap)
        y = inset + (i // 2) * (mini + gap)
        minis.append(f'<div style="position:absolute;left:{x}px;top:{y}px">{app_icon(n, mini)}</div>')
    return (f'<div class="bubble"><div class="folder-bg">{"".join(minis)}</div>'
            f'<div class="label">{html.escape(title)}</div></div>')


def status_bar():
    return f'''<div class="status"><span>9:41</span><span class="sicons">
      {svg(mdi_path("wifi"), 16)}{svg(mdi_path("signal_cellular_alt"), 16)}
      {svg(mdi_path("battery_5_bar"), 16)}<span style="margin-left:2px">62 %</span></span></div>'''


def nav_bar():
    c = "rgba(230,230,230,0.92)"
    return f'''<div class="nav">
      <svg width="18" height="18" viewBox="0 0 18 18"><path fill="{c}" d="M14 2 L3 9 L14 16 Z"/></svg>
      <svg width="18" height="18" viewBox="0 0 18 18"><circle fill="{c}" cx="9" cy="9" r="8"/></svg>
      <svg width="18" height="18" viewBox="0 0 18 18"><rect fill="{c}" x="2" y="2" width="14" height="14" rx="2"/></svg>
    </div>'''


def search_band(visible=True):
    """Franja de 56 dp: barra «Buscar apps» (44 dp, márgenes 6/16, radio 22). Se oculta al arrastrar."""
    style = "" if visible else ' style="visibility:hidden"'
    return (f'<div class="searchbar"{style}>{svg(drawable_path("ic_search"), 24)}'
            f'<span>Buscar apps</span></div>')


def clock_widget():
    return ('<div class="widget"><div class="wtime">9:41</div>'
            '<div class="wdate">miércoles, 28 de octubre</div></div>')


def workspace(hidden=None):
    cells = ['<div class="cell" style="grid-column:1 / span 4;grid-row:1">' + clock_widget() + '</div>']
    for r, row in enumerate(PAGE, start=2):
        for c, name in enumerate(row, start=1):
            if name is None or name == hidden:
                continue
            content = folder_icon(*FOLDER) if name == "@folder" else bubble(name)
            cells.append(f'<div class="cell" style="grid-column:{c};grid-row:{r}">{content}</div>')
    return f'<div class="workspace">{"".join(cells)}</div>'


def indicator(pages=2, current=0):
    dots = "".join(f'<div class="dot{" active" if i == current else ""}"></div>' for i in range(pages))
    return f'<div class="indicator">{dots}</div>'


def hotseat():
    return '<div class="hotseat">' + "".join(f'<div class="cell">{bubble(n)}</div>' for n in DOCK) + "</div>"


def home(hidden=None, search=True):
    return (f'<div class="main">{search_band(search)}{workspace(hidden)}{indicator()}{hotseat()}</div>')


# ----------------------------------------------------------------------------- estilos
def css():
    fonts = ""
    for weight in (300, 400, 500, 700):
        f = ROBOTO / f"roboto-latin-{weight}-normal.woff2"
        if f.exists():
            fonts += f"@font-face{{font-family:'Roboto';src:url('{f.as_uri()}');font-weight:{weight}}}\n"
    return fonts + f"""
* {{ box-sizing:border-box; margin:0; padding:0 }}
html, body {{ width:{W}px; height:{H}px; overflow:hidden; font-family:'Roboto',sans-serif; -webkit-font-smoothing:antialiased }}
.screen {{ position:relative; width:{W}px; height:{H}px; overflow:hidden;
  background:
    radial-gradient(circle at 18% 22%, rgba(88,110,255,.55) 0, transparent 46%),
    radial-gradient(circle at 88% 62%, rgba(214,74,160,.45) 0, transparent 50%),
    radial-gradient(circle at 30% 92%, rgba(40,190,200,.35) 0, transparent 45%),
    linear-gradient(165deg, #121735 0%, #26123f 55%, #0b0d1c 100%); }}
.status {{ position:absolute; z-index:50; top:0; left:0; right:0; height:{STATUS}px; padding:0 16px;
  display:flex; align-items:center; justify-content:space-between; color:#fff; font-size:14px; font-weight:500 }}
.sicons {{ display:flex; align-items:center; gap:4px; font-size:13px }}
.nav {{ position:absolute; z-index:50; bottom:0; left:0; right:0; height:{NAV}px;
  display:flex; align-items:center; justify-content:space-around; padding:0 64px }}
.main {{ position:absolute; top:{STATUS}px; bottom:{NAV}px; left:0; right:0; display:flex; flex-direction:column }}
.searchbar {{ flex:none; margin:6px 16px; height:44px; border-radius:22px; background:{color("search_bg")};
  display:flex; align-items:center; gap:10px; padding:0 14px; color:{color("search_hint")}; font-size:15px }}
.workspace {{ flex:1; padding:4px; display:grid; grid-template-columns:repeat(4,1fr); grid-template-rows:repeat(5,1fr) }}
.indicator {{ flex:none; height:16px; display:flex; align-items:center; justify-content:center; gap:6px }}
.dot {{ width:6px; height:6px; border-radius:50%; background:{color("page_dot")} }}
.dot.active {{ width:8px; height:8px; background:{color("page_dot_active")} }}
.hotseat {{ flex:none; height:92px; padding:8px; display:grid; grid-template-columns:repeat(4,1fr) }}
.cell {{ position:relative; min-width:0 }}
.bubble {{ display:flex; flex-direction:column; align-items:center; padding-top:2px }}
.icon {{ display:flex; align-items:center; justify-content:center; flex:none;
  box-shadow:0 1px 3px rgba(0,0,0,.25) }}
.label {{ margin-top:4px; max-width:84px; color:#fff; font-size:12px; line-height:15px; white-space:nowrap;
  overflow:hidden; text-overflow:ellipsis; text-align:center; text-shadow:0 1px 2px {color("label_shadow")} }}
.folder-bg {{ position:relative; width:48px; height:48px; border-radius:12px; background:{color("folder_ring")} }}
.widget {{ height:100%; display:flex; flex-direction:column; align-items:center; justify-content:center; color:#fff;
  text-shadow:0 1px 3px rgba(0,0,0,.4) }}
.wtime {{ font-size:58px; font-weight:300; line-height:1 }}
.wdate {{ margin-top:6px; font-size:14px; opacity:.9 }}
/* cajón */
.drawer {{ position:absolute; top:{STATUS + 8}px; left:0; right:0; bottom:0; background:{color("drawer_scrim")};
  border-radius:24px 24px 0 0; padding:12px 12px 0; display:flex; flex-direction:column }}
.dsearch {{ flex:none; height:44px; border-radius:22px; background:{color("search_bg")}; margin-bottom:10px;
  padding:0 16px; display:flex; align-items:center; color:{color("search_hint")}; font-size:16px }}
.dgrid {{ display:grid; grid-template-columns:repeat(4,1fr); row-gap:8px }}
.dgrid .cell {{ height:90px }}
/* arrastre */
.dropbar {{ position:absolute; z-index:40; top:0; left:0; right:0; height:{STATUS + 56}px; padding-top:{STATUS}px;
  background:rgba(0,0,0,.8); display:flex }}
.zone {{ flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:4px;
  color:#fff; font-size:14px; opacity:.55 }}
.zone.hot {{ opacity:1 }}
.floating {{ position:absolute; z-index:45; width:88px; height:110px; opacity:.9; transform:scale(1.08) }}
/* carpeta */
.scrim {{ position:absolute; z-index:30; inset:0; background:rgba(0,0,0,.69) }}
.folder {{ position:absolute; z-index:31; left:20px; width:320px; top:50%; transform:translateY(-50%);
  background:{color("folder_bg")}; border-radius:28px; padding:16px }}
.ftitle {{ color:#fff; font-size:18px; text-align:center; padding:10px 0 12px; margin-bottom:8px }}
.fgrid {{ display:grid; grid-template-columns:repeat(4,1fr); row-gap:8px }}
.fgrid .cell {{ height:90px }}
"""


def page(body):
    return f'<!doctype html><html><head><meta charset="utf-8"><style>{css()}</style></head>' \
           f'<body><div class="screen">{body}{status_bar()}{nav_bar()}</div></body></html>'


# ----------------------------------------------------------------------------- pantallas
def screen_home():
    return page(home())


def screen_drawer():
    names = sorted(APPS, key=lambda s: s.lower().replace("á", "a").replace("í", "i"))
    grid = "".join(f'<div class="cell">{bubble(n)}</div>' for n in names)
    # Desde 0.1.3 el escritorio se oculta con el cajón abierto: debajo solo queda el fondo de pantalla.
    return page(f'<div class="drawer"><div class="dsearch">Buscar apps</div>'
                f'<div class="dgrid">{grid}</div></div>')


def screen_drag():
    zones = [("Quitar", "ic_remove", True), ("Info", "ic_app_info", False),
             ("Desinstalar", "ic_uninstall", False)]
    bar = "".join(f'<div class="zone{" hot" if hot else ""}">{svg(drawable_path(d), 24)}'
                  f'<span>{html.escape(t)}</span></div>' for t, d, hot in zones)
    # El dedo (centro del icono) está sobre la zona «Quitar», que se resalta.
    floating = f'<div class="floating" style="left:62px;top:24px">{bubble("Notas")}</div>'
    return page(home(hidden="Notas", search=False) + f'<div class="dropbar">{bar}</div>' + floating)


def screen_folder():
    title, items = FOLDER
    grid = "".join(f'<div class="cell">{bubble(n)}</div>' for n in items)
    return page(home() + '<div class="scrim"></div>'
                f'<div class="folder"><div class="ftitle">{title}</div><div class="fgrid">{grid}</div></div>')


SCREENS = [
    ("01-inicio.png", screen_home),
    ("02-cajon.png", screen_drawer),
    ("03-arrastrar.png", screen_drag),
    ("04-carpeta.png", screen_folder),
]


def cover(paths):
    """Portada 1280×640 (también sirve como imagen para compartir el repositorio)."""
    import base64
    uri = lambda p: "data:image/png;base64," + base64.b64encode(p.read_bytes()).decode()
    phones = "".join(
        f'<img src="{uri(p)}" style="height:540px;border-radius:30px;'
        f'box-shadow:0 18px 50px rgba(0,0,0,.55);border:6px solid #1b1c22;'
        f'margin-left:{-30 if i else 0}px;transform:translateY({[10, -14, 10][i]}px);z-index:{[1, 3, 2][i]};position:relative">'
        for i, p in enumerate(paths))
    chips = "".join(f'<span class="chip">{c}</span>' for c in
                    ["~130 KB", "Sin internet", "Sin rastreo", "Solo APIs públicas", "Dominio público"])
    fonts = css().split("*")[0]
    return f'''<!doctype html><html><head><meta charset="utf-8"><style>{fonts}
* {{ box-sizing:border-box; margin:0; padding:0 }}
body {{ width:1280px; height:640px; overflow:hidden; font-family:'Roboto',sans-serif; color:#fff;
  background: radial-gradient(circle at 12% 20%, rgba(88,110,255,.35) 0, transparent 40%),
              radial-gradient(circle at 70% 90%, rgba(214,74,160,.28) 0, transparent 45%),
              linear-gradient(160deg,#0f1226,#1d1031 60%,#0a0b16); display:flex; align-items:center; padding:0 64px; gap:40px }}
.text {{ flex:1 }}
h1 {{ font-size:76px; font-weight:700; letter-spacing:-1px }}
.sub {{ margin-top:10px; font-size:30px; font-weight:400; opacity:.92; line-height:1.25 }}
.gift {{ margin-top:22px; font-size:22px; opacity:.85 }}
.chips {{ margin-top:28px; display:flex; flex-wrap:wrap; gap:10px }}
.chip {{ font-size:18px; padding:8px 14px; border-radius:20px; background:rgba(255,255,255,.12);
  border:1px solid rgba(255,255,255,.18) }}
.phones {{ display:flex; align-items:center }}
</style></head><body>
<div class="text"><h1>Inicio</h1>
<div class="sub">Launcher ligero y privado<br>para Android</div>
<div class="gift">Un regalo para la humanidad.</div>
<div class="chips">{chips}</div></div>
<div class="phones">{phones}</div>
</body></html>'''


def main():
    if not (MDI / "phone.svg").exists():
        sys.exit(f"No encuentro los iconos en {MDI} (ver la cabecera de este script)")
    OUT.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        browser = p.chromium.launch()
        ctx = browser.new_context(viewport={"width": W, "height": H}, device_scale_factor=DPR)
        pg = ctx.new_page()
        for name, fn in SCREENS:
            pg.set_content(fn(), wait_until="load")
            pg.wait_for_timeout(150)
            pg.screenshot(path=str(OUT / name))
            print("ok", name)
        ctx2 = browser.new_context(viewport={"width": 1280, "height": 640}, device_scale_factor=1)
        pg2 = ctx2.new_page()
        pg2.set_content(cover([OUT / "01-inicio.png", OUT / "02-cajon.png", OUT / "04-carpeta.png"]),
                        wait_until="load")
        pg2.wait_for_timeout(150)
        pg2.screenshot(path=str(OUT / "00-portada.png"))
        print("ok 00-portada.png")
        browser.close()
    compress()


def compress():
    """256 colores con difuminado + PNG optimizado: de ~1,2 MB a ~140 KB por imagen."""
    from PIL import Image
    for f in sorted(OUT.glob("*.png")):
        im = Image.open(f).convert("RGB")
        q = im.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
        q.save(f, "PNG", optimize=True)
        print(f"{f.name}: {f.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
