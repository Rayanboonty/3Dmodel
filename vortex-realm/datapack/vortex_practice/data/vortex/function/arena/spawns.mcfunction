# Two spawn markers facing each other, s blocks either side of where it runs (on the x axis).
#   /execute positioned <x> <y> <z> run function vortex:arena/spawns {s:20}
kill @e[type=minecraft:marker,tag=vortex_spawn,distance=..80]
$summon minecraft:marker ~-$(s) ~ ~ {Tags:["vortex_spawn","vortex_spawn_1"],Rotation:[-90f,0f]}
$summon minecraft:marker ~$(s) ~ ~ {Tags:["vortex_spawn","vortex_spawn_2"],Rotation:[90f,0f]}
