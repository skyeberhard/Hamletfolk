#!/usr/bin/env bash
# Run ON the GoodeCraft dev server, as the skylar user (see goodecraft/docs/skylar-server.md): builds the current branch against production's
# pinned Paper API, installs it on the private test server, starts it, and prints what the first start says about Hamletfolk.
#
#   ssh in (Connect-GoodeCraft-Dev.cmd), then:   cd ~/src/Hamletfolk && git pull && bash tools/dev-server-check.sh
#
# Optional: put a copy of production's settlements.json at ~/settlements-prod-copy.json first (Owen can send it) and it is run through the
# migration instead of starting empty. Nothing here touches production.
set -euo pipefail

BRANCH=claude/minecraft-npc-settlement-mod-h6nhem
REPO="$HOME/src/Hamletfolk"
PIN="$HOME/src/GoodeCraft/tools/hamletfolk-paper.gradle"
DEV="$HOME/dev-server"
DATA="$DEV/plugins/Hamletfolk"
LOG="$DEV/logs/latest.log"

[[ -f "$PIN" ]] || { echo "Missing $PIN: update the GoodeCraft checkout first."; exit 1; }

echo "== stopping the test server"
skylar-server stop || true

echo "== building $(git -C "$REPO" rev-parse --short HEAD) against the pinned API"
git -C "$REPO" fetch origin "$BRANCH" >/dev/null 2>&1 || true
"$REPO/gradlew" -p "$REPO" --no-daemon --max-workers=1 --init-script "$PIN" build
JAR="$REPO/paper/build/libs/Hamletfolk-0.1.0-SNAPSHOT.jar"
sha256sum "$JAR"

echo "== installing"
mkdir -p "$DEV/plugins"
rm -f "$DEV"/plugins/Hamletfolk*.jar
cp "$JAR" "$DEV/plugins/"
if [[ -f "$HOME/settlements-prod-copy.json" ]]; then
    mkdir -p "$DATA"
    [[ -f "$DATA/settlements.json" ]] && cp "$DATA/settlements.json" "$DATA/settlements.json.before-check"
    cp "$HOME/settlements-prod-copy.json" "$DATA/settlements.json"
    echo "   using the production copy of settlements.json"
fi

echo "== starting (this takes a minute or two)"
: > "$LOG" 2>/dev/null || true
skylar-server start
for _ in $(seq 1 90); do
    if grep -q 'Done (' "$LOG" 2>/dev/null; then break; fi
    sleep 4
done

echo "== what the log says about Hamletfolk, CoreProtect and WorldGuard"
grep -iE 'hamletfolk|coreprotect|worldguard|exception|severe' "$LOG" | head -40 || true
echo
echo "Look for: no stack traces; 'Keeping the village out of places WorldGuard protects' and 'Logging the village's building to CoreProtect'"
echo "only if those plugins are installed on the dev server (they probably are not: a missing plugin is fine, a warning is not)."
echo "Then: connect to 127.0.0.1:25566, /op yourself, and walk docs/PLAYTEST.md section 5."
