#!/usr/bin/env bash
# Copy the Lucide icons we use out of the `lucide-static` npm package, recoloured
# into a light-mode and a dark-mode variant, into docs/public/icons/. The site
# references them via VitePress's { light, dark } feature-icon support.
#
# To add an icon: add its Lucide name to ICONS and re-run `npm run icons`.
# To restyle: change LIGHT/DARK and re-run.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"                 # docs/icons
SRC="$DIR/../../node_modules/lucide-static/icons"    # the npm package
OUT="$DIR/../public/icons"
mkdir -p "$OUT"

LIGHT="#1f6f78"   # brand teal — for light-mode card backgrounds
DARK="#63c7cf"    # brighter teal — for dark-mode card backgrounds
ICONS="layers square-function zap"

for name in $ICONS; do
  [ -f "$SRC/$name.svg" ] || { echo "missing icon: $name" >&2; exit 1; }
  sed "s/currentColor/$LIGHT/g" "$SRC/$name.svg" > "$OUT/$name-light.svg"
  sed "s/currentColor/$DARK/g"  "$SRC/$name.svg" > "$OUT/$name-dark.svg"
done
echo "wrote $(ls "$OUT"/*.svg | wc -l | tr -d ' ') files for $(echo $ICONS | wc -w | tr -d ' ') Lucide icons"
