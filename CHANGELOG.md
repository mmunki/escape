# Changelog

## 1.1 - Minecraft 26.2

- The hunter is now much harder and relentlessly hunts the **nearest survival
  player**, re-targeting as players move, die or disconnect.
- **No early despawn**: the active window is clamped to at least 120 seconds,
  natural despawning is disabled, the entity is persistent and its chunk is
  force-loaded for the encounter. Only being killed can end it sooner.
- Physical pursuit: swimming, water pathfinding, door opening, wall mining,
  obstruction breaking, bridging over gaps and pillaring up.
- Expert PvP brain: iron axe plus shield, critical jump attacks, strafing,
  combos, defensive blocking, smart spacing, aggressive pressure and a
  low-health retreat that heals with regeneration, speed and absorption.
- Death loot: one random rare item from a configurable table, announced in
  chat. Defaults include enchanted golden apple, netherite ingot, elytra,
  totem of undying, netherite scrap and diamond block.
- New config keys: `healThreshold`, `healCooldownSeconds`, `blockBreaking`,
  `blockPlacing`, `announceLoot`, `deathLoot`.
- Added 26 unit tests covering the 120-second rule, nearest-player targeting,
  hunt behaviour, the PvP state machine and death loot (36 tests total).

## 1.0 - Minecraft 26.2

- First release.
- Escape hunter entity with player skin, iron armour, dragon head and iron axe.
- Spawns for a random eligible player for 2 minutes every 5 minutes.
- 20 health, iron axe damage, critical hits and player-style melee combat.
- Trap avoidance: lava, fire, campfires, magma, cactus, powder snow, berry
  bushes, wither roses, dripstone, cobwebs and primed TNT.
- Teleports out of a trap after one second and catches up when the target gets
  too far away or the path is blocked.
- `/escape status|start|stop|reload` commands and `config/escape.json`.
- 128x128 icon made from the vanilla iron axe item texture.
