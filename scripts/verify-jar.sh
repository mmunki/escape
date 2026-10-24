#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

VERSION="${1:-26.2}"
JAR="${2:-build/libs/escape-1.1+${VERSION}.jar}"

if [ ! -f "$JAR" ]; then
    echo "error: jar not found: $JAR" >&2
    exit 1
fi

echo "verifying $JAR"

require_entry() {
    if ! unzip -Z1 "$JAR" | grep -qx "$1"; then
        echo "error: missing jar entry: $1" >&2
        exit 1
    fi
}

require_entry "fabric.mod.json"
require_entry "assets/escape/icon.png"
require_entry "assets/escape/lang/en_us.json"
require_entry "tw/mmunki/escape/EscapeMod.class"
require_entry "tw/mmunki/escape/Escape.class"
require_entry "tw/mmunki/escape/EscapeConfig.class"
require_entry "tw/mmunki/escape/EscapeSettings.class"
require_entry "tw/mmunki/escape/client/EscapeClient.class"
require_entry "tw/mmunki/escape/client/EscapeEntityRenderer.class"
require_entry "tw/mmunki/escape/client/EscapeRenderState.class"
require_entry "tw/mmunki/escape/command/EscapeCommands.class"
require_entry "tw/mmunki/escape/core/EscapeCycle.class"
require_entry "tw/mmunki/escape/entity/EscapeEntities.class"
require_entry "tw/mmunki/escape/entity/EscapeEntity.class"
require_entry "tw/mmunki/escape/entity/goal/EscapeHuntGoal.class"
require_entry "tw/mmunki/escape/core/EscapeLifetime.class"
require_entry "tw/mmunki/escape/core/TargetSelector.class"
require_entry "tw/mmunki/escape/core/HuntPlanner.class"
require_entry "tw/mmunki/escape/core/PvpStateMachine.class"
require_entry "tw/mmunki/escape/core/DeathLootTable.class"
require_entry "tw/mmunki/escape/entity/goal/EscapeHazardGoal.class"
require_entry "tw/mmunki/escape/server/EscapeManager.class"

python3 - "$JAR" "$VERSION" <<'PY'
import json
import sys
import zipfile

jar_path, version = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(jar_path) as jar:
    metadata = json.loads(jar.read("fabric.mod.json"))
    icon = jar.read("assets/escape/icon.png")

assert metadata["id"] == "escape", metadata["id"]
assert metadata["version"] == "1.1", metadata["version"]
assert metadata["environment"] == "*", metadata["environment"]
assert metadata["depends"]["java"] == ">=25"
assert metadata["depends"]["fabricloader"] == ">=0.20.1"
assert metadata["depends"]["minecraft"] == f">={version} <{version.rsplit('.', 1)[0]}.{int(version.rsplit('.', 1)[1]) + 1}"
assert "tw.mmunki.escape.EscapeMod" in metadata["entrypoints"]["main"]
assert "tw.mmunki.escape.client.EscapeClient" in metadata["entrypoints"]["client"]
assert icon[:8] == b"\x89PNG\r\n\x1a\n"
assert int.from_bytes(icon[16:20], "big") == 128
assert int.from_bytes(icon[20:24], "big") == 128
print("fabric.mod.json and icon: ok")
PY

for entrypoint in \
    tw.mmunki.escape.EscapeMod \
    tw.mmunki.escape.client.EscapeClient; do
    if ! javap -classpath "$JAR" "$entrypoint" | grep -q "public ${entrypoint}();"; then
        echo "error: $entrypoint has no public no-arg constructor" >&2
        exit 1
    fi
done
echo "entrypoints: ok"

if ! javap -classpath "$JAR" tw.mmunki.escape.entity.EscapeEntity \
        | grep -q "extends net.minecraft.world.entity.monster.Monster"; then
    echo "error: EscapeEntity is not a Monster" >&2
    exit 1
fi
if ! javap -classpath "$JAR" tw.mmunki.escape.entity.EscapeEntity \
        | grep -q "net.minecraft.world.entity.player.Player"; then
    echo "error: EscapeEntity does not reference Player" >&2
    exit 1
fi
echo "entity classes: ok"

sha256sum "$JAR"
echo "verification passed"
