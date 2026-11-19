# Invisible walls on the four sides, from d below to h above where it runs.
#   /execute positioned <x> <y> <z> run function vortex:arena/barrier_walls {r:31,d:4,h:40}
$fill ~-$(r) ~-$(d) ~-$(r) ~$(r) ~$(h) ~-$(r) minecraft:barrier replace minecraft:air
$fill ~-$(r) ~-$(d) ~$(r) ~$(r) ~$(h) ~$(r) minecraft:barrier replace minecraft:air
$fill ~-$(r) ~-$(d) ~-$(r) ~-$(r) ~$(h) ~$(r) minecraft:barrier replace minecraft:air
$fill ~$(r) ~-$(d) ~-$(r) ~$(r) ~$(h) ~$(r) minecraft:barrier replace minecraft:air
