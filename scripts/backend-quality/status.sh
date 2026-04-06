#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib.sh
source "$SCRIPT_DIR/lib.sh"

bq::ensure_state_file

jq -r '
  ["Ticket","Status","Agent","Exit","Note"],
  (.tickets | to_entries | sort_by(.key)[] | [
    .key,
    (.value.status // "pending"),
    (.value.agent // "-"),
    ((.value.exitCode // "-")|tostring),
    (.value.note // "-")
  ]) | @tsv
' "$BACKEND_QUALITY_STATE_FILE" | column -t -s $'\t'

echo
echo "State file: $BACKEND_QUALITY_STATE_FILE"
