# Escape

**Escape** is a Minecraft **26.2** Fabric horror mod about a player-shaped
hunter. Every five minutes the nearest survival player is marked. The hunter
stays for at least two minutes - it can only leave early if it is killed.

It is a clean-room implementation for Minecraft 26.2. It is not a port of an
existing mod and it contains no code or assets from one.

## What the entity does

- Spawns 10-24 blocks away from an eligible player, on solid ground. Automatic
  encounters start with the player nearest to the world spawn and the hunter
  immediately locks onto the nearest survival player.
- **Always hunts the nearest player.** It re-checks the player list and moves
  to whoever is closest instead of staying bound to one random target.
- Uses the **current target player's skin** (downloaded by the vanilla skin
  system), so the skin follows the target when it changes.
- Wears **iron chestplate, iron leggings, iron boots** and a **dragon head**
  instead of a helmet, so the player's face is still visible.
- Holds an **iron axe** in the main hand and a **shield** in the off hand.
- Has **20 health**, the same as an unarmoured player, plus the armour points
  from the iron armour.
- **Never despawns early.** The configured active window is clamped to at
  least 120 seconds, natural despawning is disabled, the entity is persistent
  and its chunk is kept loaded for the whole encounter. Only being killed can
  end an encounter sooner.
- **Fights like an expert PvP player**: approaches with smart spacing, strafes
  left and right, jumps for critical hits, chains combos, raises the shield
  when the target attacks, blocks defensively, pressures relentlessly and
  retreats to heal with regeneration, speed and absorption when low.
- **Reaches the player at any cost**: it swims and pathfinds through water,
  opens wooden and iron doors, mines through walls, breaks obstruction blocks,
  bridges over gaps and pillars up to higher ground.
- **Avoids traps**: lava, fire, soul fire, campfires, magma blocks, cactus,
  powder snow, sweet berry bushes, wither roses, dripstone, cobwebs and primed
  TNT. If it does fall into a trap it climbs out; if it is held in lava, fire,
  powder snow or a cobweb for one second it teleports to a safe block nearby.
- **Drops one random rare item when killed** from a configurable loot table.
  The default table contains an enchanted golden apple, a netherite ingot,
  elytra, a totem of undying, netherite scrap and a diamond block. The drop is
  announced in chat.
- Disappears after the active window if the player survives, and dies normally
  if the player kills it. Both outcomes end the encounter.

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
3. Put `escape-1.1+26.2.jar` in `mods/`.
4. Start the game. The mod works in singleplayer and on a dedicated server.

The jar is published as a release asset together with a source archive and the
checksums of every asset.

## Build

```bash
./build.sh -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2
```

The jar is written to `build/libs/escape-1.1+26.2.jar`.

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
  "movementSpeed": 0.32,
  "attackDamage": 9.0,
  "critChance": 0.45,
  "critMultiplier": 1.5,
  "retrySeconds": 30,
  "healThreshold": 0.35,
  "healCooldownSeconds": 8,
  "blockBreaking": true,
  "blockPlacing": true,
  "announceLoot": true,
  "deathLoot": [
    "minecraft:enchanted_golden_apple",
    "minecraft:netherite_ingot",
    "minecraft:elytra",
    "minecraft:totem_of_undying",
    "minecraft:netherite_scrap",
    "minecraft:diamond_block"
  ]
}
```

- `cycleSeconds` is the length of the full cycle (default 300 = 5 minutes).
  It cannot be set below 140 seconds.
- `activeSeconds` is how long the hunter stays. It is clamped to at least
  **120 seconds**, so the hunter can never time out early.
- `firstDelaySeconds` is the grace period after server start.
- `disableInCreative` skips creative and spectator players when choosing a
  target.
- `maxHealth`, `movementSpeed`, `attackDamage` and the crit values are applied
  when the encounter spawns.
- `healThreshold` and `healCooldownSeconds` control the low-health retreat and
  heal behaviour.
- `blockBreaking` and `blockPlacing` enable tunnelling, door opening, bridging
  and pillaring.
- `announceLoot` controls the chat message when the rare item drops.
- `deathLoot` is the rare-item table. Any item id works; an unknown id falls
  back to an enchanted golden apple.
- `retrySeconds` is used when no eligible player is online.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/escape` | everyone | Show the encounter status and countdown |
| `/escape status` | everyone | Same as `/escape` |
| `/escape help` | everyone | List the commands |
| `/escape start [player]` | gamemaster | Start an encounter now for yourself, a player, or the nearest eligible player from console |
| `/escape stop` | gamemaster | Remove the current hunter (admin override) |
| `/escape reload` | gamemaster | Reload `config/escape.json` and reset the cycle |

## Test matrix

| Check | Result |
| --- | --- |
| Gradle build on Linux, Temurin JDK 25 | PASS |
| JUnit settings, cycle, lifetime, targeting, hunt, PvP and loot tests | PASS (36/36) |
| `scripts/verify-jar.sh 26.2` | PASS |
| Dedicated server, Fabric Loader 0.20.1 + Fabric API 0.161.0+26.2 | PASS (`Done (0.267s)`) |
| Console `/escape status`, `/escape help`, `/escape reload`, `/escape start`, `/escape stop` | PASS |
| Console `/summon escape:escape` (entity construction and tick) | PASS |
| Two-minute rule on a force-loaded chunk | PASS (alive at 110 s, gone at 123 s) |
| Death loot and chat announcement | PASS (`Escape dropped Netherite Scrap!`, item entity verified) |
| Client launch under Xvfb with Mesa llvmpipe | PASS in 1.0; unchanged renderer for 1.1 |
| In-world renderer with a real target skin | MANUAL (needs a real client and a player) |
| Nearest-player retargeting, block tunnelling and PvP under real combat | Unit-tested; MANUAL in a live multiplayer world |

The automated checks ran on Linux with Temurin JDK 25 against Minecraft 26.2
and Fabric API 0.161.0+26.2. The in-world PvP checks need a real client because
the dedicated server has no renderer and the encounter needs real player
targets.

## License

MIT. See `LICENSE`. The vanilla iron axe texture is only read at development
time to draw the icon; no Mojang asset is redistributed.
