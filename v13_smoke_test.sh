#!/data/data/com.termux/files/usr/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/.v13_classes"
rm -rf "$OUT"; mkdir -p "$OUT"
SOURCES=$(find "$ROOT/app/src/main/java/com/neuronis/jarvis/core" -name '*.kt' -print)
kotlinc $SOURCES "$ROOT/tools/V13Smoke.kt" -include-runtime -d "$OUT/v13.jar"
java -jar "$OUT/v13.jar"
