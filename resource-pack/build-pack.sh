#!/usr/bin/env bash
# Build resource-pack/pack.zip from the resource-pack folder.
# After running, commit + push the zip, then update RESOURCE_PACK_SHA1 in
# silk-monad/minecraft-server/docker-compose.yml with the SHA-1 printed below
# and restart the server.

set -euo pipefail
cd "$(dirname "$0")"

OUT="pack.zip"
rm -f "$OUT"

# Zip the contents (NOT the resource-pack/ folder itself) so the .mcmeta is
# at the zip root, as Minecraft expects.
zip -r -X "$OUT" pack.mcmeta assets/ \
  -x "*.DS_Store" "README.md" "build-pack.sh" >/dev/null

SHA1=$(shasum -a 1 "$OUT" | awk '{print $1}')
SIZE=$(wc -c <"$OUT" | tr -d ' ')

echo "Built  : $OUT ($SIZE bytes)"
echo "SHA-1  : $SHA1"
echo "URL    : https://raw.githubusercontent.com/zero-point-module/silk-monad/main/resource-pack/pack.zip"
echo
echo "Next   : update RESOURCE_PACK_SHA1 in silk-monad/minecraft-server/docker-compose.yml to:"
echo "         $SHA1"
echo "         then: git add -A && git commit && git push && (cd ../minecraft-server && docker compose up -d --force-recreate)"
