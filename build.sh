#!/usr/bin/env bash
# Compila, empaqueta y firma el launcher sin Gradle ni Android Studio.
#   ./build.sh                       -> out/launcher-movil.apk            (sin insignias: se instala desde el gestor de archivos)
#   ./build.sh --variant insignias   -> out/launcher-movil-insignias.apk  (con insignias: instalar con adb, ver README)
#   ./build.sh --variant ambas       -> las dos
# Requisitos en tools/ (ejecuta ./tools/get-tools.sh): android-36.jar, aapt2,
# jadx-1.5.1-all.jar (D8 + apksig). JDK 17+.
set -euo pipefail
cd "$(dirname "$0")"
T=tools; B=build; OUT=out
JADX=$T/jadx-1.5.1-all.jar
AJAR=$T/android-36.jar
KS=keystore/release.p12
KSPASS="${KSPASS:-launchermovil}"
MINSDK=26; TARGETSDK=36

VARIANT=normal
while [ $# -gt 0 ]; do
  case "$1" in
    --variant) VARIANT="$2"; shift 2 ;;
    -h|--help) sed -n '2,6p' "$0"; exit 0 ;;
    *) echo "Opción desconocida: $1"; exit 2 ;;
  esac
done
if [ "$VARIANT" = ambas ]; then
  "$0" --variant normal && "$0" --variant insignias
  exit $?
fi
case "$VARIANT" in
  normal)    APK_NAME=launcher-movil.apk ;;
  insignias) APK_NAME=launcher-movil-insignias.apk ;;
  *) echo "Variante desconocida: $VARIANT (normal | insignias | ambas)"; exit 2 ;;
esac

for f in "$JADX" "$AJAR" "$T/aapt2"; do
  [ -f "$f" ] || { echo "Falta $f — ejecuta ./tools/get-tools.sh"; exit 1; }
done

rm -rf "$B"; mkdir -p "$B/gen" "$B/classes" "$B/dex" "$OUT" keystore

# Variante normal: sin el servicio de insignias (NotificationListenerService).
# Google Play Protect bloquea las apps instaladas desde navegador, mensajería o gestor de archivos
# que piden acceso a notificaciones, así que esta variante no lo declara.
MANIFEST=AndroidManifest.xml
if [ "$VARIANT" = normal ]; then
  MANIFEST="$B/AndroidManifest.xml"
  python3 - AndroidManifest.xml "$MANIFEST" <<'PYEOF'
import re, sys
src, dst = sys.argv[1:3]
m = open(src, encoding='utf-8').read()
m2, n = re.subn(r'\s*<!-- Insignias.*?</service>', '', m, flags=re.S)
assert n == 1, 'no se encontró el servicio de insignias en el manifiesto'
assert 'NotificationListenerService' not in m2
open(dst, 'w', encoding='utf-8').write(m2)
PYEOF
fi
echo "=== Variante: $VARIANT -> $OUT/$APK_NAME"

echo "[1/6] aapt2 compile (recursos)"
"$T/aapt2" compile --dir res -o "$B/res.zip"

echo "[2/6] aapt2 link (R.java + base.apk)"
"$T/aapt2" link -o "$B/base.apk" -I "$AJAR" --manifest "$MANIFEST" \
  --java "$B/gen" --min-sdk-version "$MINSDK" --target-sdk-version "$TARGETSDK" \
  --no-auto-version "$B/res.zip"

echo "[3/6] javac"
find src "$B/gen" -name '*.java' > "$B/sources.txt"
javac --release 8 -encoding UTF-8 -proc:none -Xlint:-options,-deprecation \
  -cp "$AJAR" -d "$B/classes" @"$B/sources.txt" 2>&1 | grep -v "^Picked up" || true
[ -f "$B/classes/com/webgdl/launcher/HomeActivity.class" ] || { echo "javac falló"; exit 1; }

echo "[4/6] d8 (classes.dex)"
find "$B/classes" -name '*.class' > "$B/classes.txt"
java -cp "$JADX" com.android.tools.r8.D8 --min-api "$MINSDK" --release \
  --lib "$AJAR" --output "$B/dex" @"$B/classes.txt" 2>&1 | grep -v "^Picked up" || true
[ -f "$B/dex/classes.dex" ] || { echo "d8 falló"; exit 1; }

echo "[5/6] empaquetar (añadir dex al base.apk)"
cp "$B/base.apk" "$B/unsigned.apk"
( cd "$B/dex" && zip -q -0 "../../$B/unsigned.apk" classes.dex )

echo "[6/6] firmar + verificar"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -storetype PKCS12 -keystore "$KS" -storepass "$KSPASS" -alias launcher \
    -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Launcher Movil" >/dev/null 2>&1
fi
java -cp "$JADX" Sign.java "$B/unsigned.apk" "$OUT/$APK_NAME" "$KS" "$KSPASS" 2>&1 | grep -v "^Picked up\|deprecat\|SignerConfig\|warning\|\^" || true
java -cp "$JADX" Sign.java verify "$OUT/$APK_NAME" 2>&1 | grep -v "^Picked up" || true
echo "OK -> $OUT/$APK_NAME"
