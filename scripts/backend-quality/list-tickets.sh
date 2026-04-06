#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TICKETS_FILE="$SCRIPT_DIR/tickets.tsv"

if [[ ! -f "$TICKETS_FILE" ]]; then
  echo "Missing tickets file: $TICKETS_FILE" >&2
  exit 1
fi

printf '%-8s %-4s %-36s %-18s %s\n' "Ticket" "Wave" "Branch" "Depends On" "Title"
while IFS=$'\t' read -r id wave branch title deps scope acceptance; do
  [[ -z "$id" ]] && continue
  printf '%-8s %-4s %-36s %-18s %s\n' "$id" "$wave" "$branch" "${deps:--}" "$title"
done < "$TICKETS_FILE"
