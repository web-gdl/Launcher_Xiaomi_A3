#!/usr/bin/env bash
# Descarga las herramientas para build.sh (sin Gradle ni Android Studio).
set -euo pipefail
cd "$(dirname "$0")"
JADX_VER=1.5.1
echo "- android.jar (API 36)"
[ -f android-36.jar ] || curl -fL -o android-36.jar \
  "https://raw.githubusercontent.com/Sable/android-platforms/master/android-36/android.jar"
echo "- jadx $JADX_VER (incluye D8/R8 y apksig)"
if [ ! -f "jadx-$JADX_VER-all.jar" ]; then
  curl -fL -o jadx.zip "https://github.com/skylot/jadx/releases/download/v$JADX_VER/jadx-$JADX_VER.zip"
  unzip -o -j jadx.zip "lib/jadx-$JADX_VER-all.jar" -d . && rm -f jadx.zip
fi
echo "- aapt2 (de apktool, binario Linux x86-64)"
if [ ! -f aapt2 ]; then
  curl -fL -o apktool.jar "https://github.com/iBotPeaches/Apktool/releases/download/v2.11.1/apktool_2.11.1.jar"
  unzip -o -j apktool.jar prebuilt/linux/aapt2_64 -d . && mv aapt2_64 aapt2 && chmod +x aapt2 && rm -f apktool.jar
fi
echo "Listo. Ahora: ./build.sh"
