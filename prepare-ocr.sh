#!/usr/bin/env bash
# Fetch and verify build-time OCR dependencies. Recognition on the device works offline.
set -euo pipefail
cache=${1:?Set OCR dependency cache directory}
mkdir -p "$cache"
fetch() (
  local name=$1 sha=$2 url=$3 destination="$cache/$1" temp
  if [[ -f "$destination" ]] && printf '%s  %s\n' "$sha" "$destination" | sha256sum --check --status; then
    exit 0
  fi
  temp=$(mktemp "$cache/.download.XXXXXX")
  trap 'rm -f "$temp"' EXIT
  curl --fail --silent --show-error --location --retry 2 --max-time 90 --output "$temp" "$url"
  printf '%s  %s\n' "$sha" "$temp" | sha256sum --check --status || { echo "OCR dependency hash mismatch: $name" >&2; exit 1; }
  mv -f "$temp" "$destination"
)
fetch tesseract4android-4.9.0.aar bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a \
  https://jitpack.io/cz/adaptech/tesseract4android/tesseract4android/4.9.0/tesseract4android-4.9.0.aar &
jobs=( "$!" )
fetch rus.traineddata e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225 \
  https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/rus.traineddata &
jobs+=( "$!" )
fetch eng.traineddata 7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2 \
  https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/eng.traineddata &
jobs+=( "$!" )
fetch chi_sim.traineddata a5fcb6f0db1e1d6d8522f39db4e848f05984669172e584e8d76b6b3141e1f730 \
  https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/chi_sim.traineddata &
jobs+=( "$!" )
fetch jpn.traineddata 1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d \
  https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/jpn.traineddata &
jobs+=( "$!" )
fetch kor.traineddata 6b85e11d9bbf07863b97b3523b1b112844c43e713df8b66418a081fd1060b3b2 \
  https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/kor.traineddata &
jobs+=( "$!" )
fetch ukr.traineddata d59e53e2bded32f4445f124b4b00240fcac7e8044c003ab822ccb94f0b3db59b \
  https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/ukr.traineddata &
jobs+=( "$!" )
status=0
for pid in "${jobs[@]}"; do wait "$pid" || status=1; done
exit "$status"
