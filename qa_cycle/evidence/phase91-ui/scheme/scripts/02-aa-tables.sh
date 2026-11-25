#!/usr/bin/env bash
# 661.4: compile the repo's colour maths (frontend/__tests__/theme/color-math.ts) with the
# frontend's own tsc and run 02-aa-tables.cjs against the prototype branch's CSS.
# Usage: bash 02-aa-tables.sh <prototype worktree> <scratch dir>
set -euo pipefail
WT="$1"; TMP="$2"; HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$TMP"
"$WT/frontend/node_modules/.bin/tsc" "$WT/frontend/__tests__/theme/color-math.ts" \
  --outDir "$TMP" --module commonjs --target es2022 --esModuleInterop --types node --skipLibCheck \
  --typeRoots "$WT/frontend/node_modules/@types"
echo "prototype HEAD: $(git -C "$WT" rev-parse HEAD)"
echo "color-math.ts sha256: $(shasum -a 256 "$WT/frontend/__tests__/theme/color-math.ts" | cut -d' ' -f1)"
node "$HERE/02-aa-tables.cjs" "$TMP/color-math.js" "$WT" "$HERE/aa-tables"
