#!/usr/bin/env bash
# Render every *.d2 to docs/public/diagrams/*.svg, then post-process:
# strip the base64 WOFF fonts d2 embeds (a big per-diagram decode cost that
# delays text painting) and point the text classes at the system font stack —
# which also matches the VitePress site font and shrinks each SVG ~5x.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"   # docs/diagrams
OUT="$DIR/../public/diagrams"          # docs/public/diagrams
mkdir -p "$OUT"

for f in "$DIR"/*.d2; do
  name="$(basename "$f" .d2)"
  svg="$OUT/$name.svg"
  d2 --theme 0 --pad 24 "$f" "$svg"
  # 1) delete the embedded @font-face blocks (each holds one base64 WOFF)
  # 2) append a style that maps d2's text classes to a system font stack
  perl -0777 -i -pe '
    s/\@font-face\s*\{[^}]*\}//g;
    s#</svg>#<style>.text,.text-bold,.text-italic{font-family:ui-sans-serif,system-ui,-apple-system,"Segoe UI",Roboto,Helvetica,Arial,sans-serif}.text-bold{font-weight:700}.text-italic{font-style:italic}</style></svg>#;
  ' "$svg"
done
echo "rendered + de-fonted $(ls "$OUT"/*.svg | wc -l | tr -d ' ') diagrams"
