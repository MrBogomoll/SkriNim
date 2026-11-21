#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_JAR:?Set path to an API 35 android.jar}"
: "${ANDROID_TOOLS:?Set path to build-tools 35.0.0}"
: "${APK_KEYSTORE:?Set path to a signing keystore}"
: "${APK_KS_PASS:?Set apksigner-compatible password source (e.g. env:APKSIGNER_PASSWORD)}"
version=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
[[ $version =~ ^[0-9]+(\.[0-9]+)*(_b\.[1-9][0-9]*)?$ ]] || { echo "Invalid versionName" >&2; exit 1; }
output="build/SkriNim-${version}.apk"
mkdir -p build
d=$(mktemp -d "$PWD/build/run.XXXXXX")
trap 'rm -rf "$d"' EXIT
mkdir "$d"/{classes,gen,dex}
ocr_cache=${OCR_CACHE_DIR:-$PWD/build/ocr-cache}
bash prepare-ocr.sh "$ocr_cache"
unzip -p "$ocr_cache/tesseract4android-4.9.0.aar" classes.jar > "$d/ocr.jar"
mkdir -p "$d/assets/tessdata" "$d/native"
cp -R assets/. "$d/assets/"
cp LICENSE "$d/assets/licenses/SkriNim.txt"
cp "$ocr_cache"/{rus,eng,chi_sim,jpn,kor,ukr}.traineddata "$d/assets/tessdata/"
unzip -q "$ocr_cache/tesseract4android-4.9.0.aar" 'jni/*/*.so' -d "$d/native"
mv "$d/native/jni" "$d/native/lib"
"$ANDROID_TOOLS/aapt2" compile --dir res -o "$d/res.zip"
"$ANDROID_TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml --java "$d/gen" -A "$d/assets" -o "$d/unsigned.apk" "$d/res.zip"
mapfile -d '' sources < <(find src "$d/gen" -name '*.java' -print0)
if [[ -n "${ECJ_JAR:-}" ]]; then
  java -jar "$ECJ_JAR" -proc:none -source 8 -target 8 -classpath "$ANDROID_JAR:$d/ocr.jar" -d "$d/classes" "${sources[@]}"
else
  javac --release 8 -proc:none -classpath "$ANDROID_JAR:$d/ocr.jar" -d "$d/classes" "${sources[@]}"
fi
(cd "$d/classes"; zip -qr "$d/classes.jar" .)
"$ANDROID_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 31 --output "$d/dex" "$d/classes.jar" "$d/ocr.jar"
(cd "$d/dex"; zip -q -0 "$d/unsigned.apk" classes.dex)
# Compressed shared libraries are extracted by the package installer (default
# manifest behavior); this preserves all four ABIs without bloating the APK.
(cd "$d/native"; zip -qr "$d/unsigned.apk" lib)
"$ANDROID_TOOLS/zipalign" -P 16 -f 4 "$d/unsigned.apk" "$d/aligned.apk"
"$ANDROID_TOOLS/apksigner" sign --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --ks "$APK_KEYSTORE" --ks-pass "$APK_KS_PASS" --out "$output" "$d/aligned.apk"
"$ANDROID_TOOLS/apksigner" verify --verbose "$output"
