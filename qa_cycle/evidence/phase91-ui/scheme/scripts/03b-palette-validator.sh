#!/usr/bin/env bash
# 661.4: the dataviz skill's validate_palette.js for chart-1..5 against each candidate's canvas
# (--background) and card (--card), light; plus dark chart slots vs the (unchanged) dark canvas
# and card. Hexes come from aa-tables.json (02-aa-tables.sh output, the repo colour maths).
# Re-run of 03-palette-validator.sh (which failed: the validator is an ES module and node 20 loads
# a .js file as CommonJS; see 03-palette-validator.failed-esm.txt). Identical except node runs with
# --experimental-default-type=module (the validator checks argv[1] ends with validate_palette.js,
# so it cannot be copied to .mjs).
# Usage: bash 03b-palette-validator.sh <path to validate_palette.js>
set -uo pipefail
V="$1"; HERE="$(cd "$(dirname "$0")" && pwd)"; J="$HERE/aa-tables.json"
echo "validator: $V"
echo "validator sha256: $(shasum -a 256 "$V" | cut -d' ' -f1)"
LIGHT=$(node -e 'console.log(require(process.argv[1]).chart.light.join(","))' "$J")
DARK=$(node -e 'console.log(require(process.argv[1]).chart.dark.join(","))' "$J")
run() { # label palette mode surface [extra]
  echo; echo "===== $1 — surface $4 ($3) ====="
  node --experimental-default-type=module "$V" "$2" --mode "$3" --surface "$4" ${5:-}; echo "exit=$?"
}
for id in today a1 a2 b c; do
  for s in background card; do
    hex=$(node -e 'const j=require(process.argv[1]);console.log(j.schemes[process.argv[2]].tokens[process.argv[3]])' "$J" "$id" "$s")
    run "$id $s" "$LIGHT" light "$hex"
  done
done
for s in background card; do
  hex=$(node -e 'const j=require(process.argv[1]);console.log(j.dark.tokens[process.argv[2]])' "$J" "$s")
  run "dark (every candidate) $s" "$DARK" dark "$hex"
done
echo; echo "===== light, first three slots, --pairs all (card) ====="
node --experimental-default-type=module "$V" "$(echo "$LIGHT" | cut -d, -f1-3)" --mode light --surface "#fdfeff" --pairs all; echo "exit=$?"
