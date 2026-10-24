# Escape

**Escape** is a Minecraft **26.2** Fabric horror mod about a player-shaped
hunter. Every five minutes one random player is marked. Two minutes later the
hunter disappears - but only if that player is still alive to see it.

It is a clean-room implementation for Minecraft 26.2. It is not a port of an
existing mod and it contains no code or assets from one.

## What the entity does

- Spawns 10-24 blocks away from its target, on solid ground.
- Uses the **target player's skin** (downloaded by the vanilla skin system).
- Wears **iron chestplate, iron leggings, iron boots** and a **dragon head**
  instead of a helmet, so the player's face is still visible.
- Holds an **iron axe** and swings it with player-style melee attacks.
- Has **20 health**, the same as an unarmoured player, plus the armour points
  from the iron armour.
- Lands **critical hits** for extra damage, with the vanilla crit particles and
  sound. A crit happens while falling or with a 35% chance per hit.
- Is bound to **one random player** for the whole encounter and ignores other
  players. It will not switch targets, even if somebody else hits it.
- **Hunts** that player: it pathfinds, breaks line of sight, catches up when the
  target runs, and teleports closer if the target gets more than 64 blocks away
  or blocks the path completely.
- **Avoids traps**: lava, fire, soul fire, campfires, magma blocks, cactus,
  powder snow, sweet berry bushes, wither roses, dripstone, cobwebs and primed
  TNT. If it does fall into a trap it climbs out; if it is held in lava, fire,
  powder snow or a cobweb for one second it teleports to a safe block nearby.
- Disappears after **2 minutes** if the player survives, and dies normally if
  the player kills it. Both outcomes end the encounter.

The encounter schedule is a five-minute cycle: two minutes with the hunter,
three minutes without it. The first encounter starts one minute after the
server starts (configurable).

## Requirements

- Minecraft `26.2`
- Fabric Loader `0.20.1` or newer
- Fabric API `0.161.0+26.2` or newer
- Java `25`

## Install

1. Install Fabric Loader for Minecraft 26.2.
2. Put Fabric API in `mods/`.
3. Put `escape-1.0+26.2.jar` in `mods/`.
4. Start the game. The mod works in singleplayer and on a dedicated server.

The jar is published as a release asset together with a source archive and the
checksums of every asset.

## Build

```bash
./build.sh -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2
```

The jar is written to `build/libs/escape-1.0+26.2.jar`.

Run the unit tests and the jar verification:

```bash
./build.sh -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2
bash scripts/verify-jar.sh 26.2
```

The icon can be rebuilt from the vanilla iron axe texture:

```bash
python3 scripts/make-icon.py
```

## Configuration

The server reads `config/escape.json` on startup and on `/escape reload`.

```json
{
  "enabled": true,
  "cycleSeconds": 300,
  "activeSeconds": 120,
  "firstDelaySeconds": 60,
  "disableInCreative": true,
  "announce": true,
  "maxHealth": 20.0,
  "movementSpeed": 0.30,
  "attackDamage": 9.0,
  "critChance": 0.35,
  "critMultiplier": 1.5,
  "retrySeconds": 30
}
```

- `cycleSeconds` is the length of the full cycle (default 300 = 5 minutes).
- `activeSeconds` is how long the hunter stays (default 120 = 2 minutes).
- `firstDelaySeconds` is the grace period after server start.
- `disableInCreative` skips creative and spectator players when choosing a
  target.
- `maxHealth`, `movementSpeed`, `attackDamage` and the crit values are applied
  when the encounter spawns.
- `retrySeconds` is used when no eligible player is online.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/escape` | everyone | Show the encounter status and countdown |
| `/escape status` | everyone | Same as `/escape` |
| `/escape help` | everyone | List the commands |
| `/escape start [player]` | gamemaster | Start an encounter now for yourself, a player, or a random player from console |
| `/escape stop` | gamemaster | Remove the current hunter |
| `/escape reload` | gamemaster | Reload `config/escape.json` and reset the cycle |

## Test matrix

| Check | Result |
| --- | --- |
| Gradle build on Linux, Temurin JDK 25 | PASS |
| JUnit cycle and config tests | PASS (10/10) |
| `scripts/verify-jar.sh 26.2` | PASS |
| Dedicated server, Fabric Loader 0.20.1 + Fabric API 0.161.0+26.2 | PASS (`Done (0.189s)`) |
| Console `/escape status`, `/escape help`, `/escape reload`, `/escape stop` | PASS |
| Console `/summon escape:escape` (entity construction and tick) | PASS |
| Client launch under Xvfb with Mesa llvmpipe | PASS (mod loaded, resources reloaded, no registration or model-layer error; terminated after 240 s while still on the software renderer) |
| In-world renderer with a real target skin | MANUAL (needs a real client and a player) |
| Two-player target selection, PvP and trap escape | MANUAL (needs a real client and a player) |

The automated checks ran on Linux with Temurin JDK 25 against Minecraft 26.2
and Fabric API 0.161.0+26.2. The in-world checks need a real client because the
dedicated server has no renderer and the encounter needs a real player target.

## License

MIT. See `LICENSE`. The vanilla iron axe texture is only read at development
time to draw the icon; no Mojang asset is redistributed.
