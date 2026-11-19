# vortex_practice data pack (Minecraft 1.21.11)

Copy `vortex_practice` into `<world>/datapacks/` and run `/reload`.

| Function | What it does |
|---|---|
| `vortex:setup/gamerules` | Practice rules: no day cycle, weather, mobs, fire spread or griefing; instant respawn; noon, clear |
| `vortex:arena/barrier_ceiling {r,h}` | Invisible ceiling of half-size r, h above |
| `vortex:arena/barrier_walls {r,d,h}` | Invisible walls on four sides |
| `vortex:arena/spawns {s}` | Two facing spawn markers (`vortex_spawn_1`, `vortex_spawn_2`) |
| `vortex:arena/flat_duel` | 60x60 duel: walls, ceiling, spawns |
| `vortex:arena/crystal_pit` | 70x70 crystal pit |
| `vortex:arena/sumo` | 15x15 sumo platform |

Run the arena functions where the arena's middle is, for example
`/execute positioned 100 64 100 run function vortex:arena/flat_duel`.

The plugin does all of this by itself for the arenas it builds (walls to the sky, re-sealed after every rebuild,
blocks and crystals put back after every match). The data pack is for maps built by hand or pasted from
`schematics/` with WorldEdit / FastAsyncWorldEdit (`//schem load sumo-dojo` then `//paste`).
