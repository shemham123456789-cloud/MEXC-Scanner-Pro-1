#!/usr/bin/env bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc "$ROOT"/app/src/main/java/com/neuronis/jarvis/core/*.kt "$ROOT"/tools/CoreSmoke.kt "$ROOT"/tools/AdaptiveSmoke.kt -include-runtime -d "$TMP/smoke.jar"
java -cp "$TMP/smoke.jar" CoreSmokeKt
java -cp "$TMP/smoke.jar" AdaptiveSmokeKt
