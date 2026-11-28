# An invisible ceiling over an arena, centred on where it runs.
#   /execute positioned <x> <y> <z> run function vortex:arena/barrier_ceiling {r:30,h:40}
# r = half size (30 for a 60x60 duel, 35 for the 70x70 crystal pit, 8 for the sumo platform), h = height above.
$fill ~-$(r) ~$(h) ~-$(r) ~$(r) ~$(h) ~$(r) minecraft:barrier replace minecraft:air
