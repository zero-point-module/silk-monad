#!/usr/bin/env bash
# Build the SilkMonad plugin in a Docker container (no local Java/Gradle needed).
# Output: build/libs/silkmonad-plugin-<version>.jar
# If --install is passed, also copies the jar into ../minecraft-server/data/plugins/.

set -euo pipefail
cd "$(dirname "$0")"

docker run --rm \
  -v "$PWD":/work \
  -v "$HOME/.gradle":/root/.gradle \
  -w /work \
  gradle:8.10-jdk21 \
  gradle --no-daemon shadowJar

JAR=$(ls build/libs/silkmonad-plugin-*.jar | head -n1)
echo "Built: $JAR"

if [[ "${1:-}" == "--install" ]]; then
  DEST="../minecraft-server/data/plugins/SilkMonad.jar"
  cp "$JAR" "$DEST"
  echo "Installed to: $DEST"
  echo "Restart the server: (cd ../minecraft-server && docker compose restart minecraft)"
fi
