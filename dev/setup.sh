#!/bin/bash
# Builds Link and puts the jar and each server's link.json in place. Run it again after
# changing Link, then restart the servers: docker compose restart lobby game
set -euo pipefail
cd "$(dirname "$0")"

(cd .. && ./gradlew -q :LinkHytale:shadowJar)
JAR=$(ls -t ../LinkHytale/build/libs/link-hytale-*.jar | head -1)

for server in lobby game; do
  mkdir -p "$server/mods/Link_Link"
  rm -f "$server"/mods/link-hytale-*.jar
  cp "$JAR" "$server/mods/"
  cp "config/$server.link.json" "$server/mods/Link_Link/link.json"
done

# The game server reuses the lobby's download instead of fetching 3 GB again.
if [ -f lobby/Server/HytaleServer.jar ]; then
  for f in Server Assets.zip .server-version; do
    [ -e "lobby/$f" ] && rm -rf "game/$f" && cp -R "lobby/$f" "game/$f"
  done
fi
echo "Link $(basename "$JAR") installed on lobby and game."
