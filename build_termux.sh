#!/data/data/com.termux/files/usr/bin/bash
set -e
if ! command -v java >/dev/null 2>&1; then echo 'Falta Java. Instala OpenJDK 21.'; exit 1; fi
if ! command -v gradle >/dev/null 2>&1; then echo 'Falta Gradle. Instala el paquete gradle de tu Termux.'; exit 1; fi
printf '\n=== JARVIS PRO BUILD ===\n'
gradle --no-daemon :app:assembleDebug
printf '\nAPK: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"
