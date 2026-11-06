#!/bin/bash
# Build the Vortex mod for every supported Minecraft version and put the
# jars where the launcher bundles them (crates/vortex-app/resources).
# Each version is also checked against that version's real classes
# (tools/check_mixins.py), so a hook that no longer fits stops the build
# instead of crashing someone's game.
#
#   bash mod/build-all.sh            all versions in versions/*.properties
#   bash mod/build-all.sh 1.21.1     just these
set -euo pipefail
cd "$(dirname "$0")"
OUT=../crates/vortex-app/resources
versions=("$@")
if [ ${#versions[@]} -eq 0 ]; then
  for f in versions/*.properties; do versions+=("$(basename "$f" .properties)"); done
fi
for v in "${versions[@]}"; do
  echo "== Minecraft $v"
  gradle build -Pmc="$v" -q
  python3 tools/check_mixins.py "$v"
  cp "build/libs/vortex-mods-1.0.0+$v.jar" "$OUT/vortex-mods-$v.jar"
done
ls -la "$OUT"
