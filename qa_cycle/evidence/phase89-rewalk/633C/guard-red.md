# 633C — raw-enum guard proof: plant → red → remove → green (builder, 2026-11-05)

Worktree `/Users/rakheendama/Projects/2026/worktree-epic-633C`, branch
`phase89/04C-portal-deadlines-copy-guard`, after the copy fixes (the guard is green on the real
tree: `Tests 4 passed (4)`). Guard = `portal/__tests__/no-raw-enum-copy.test.ts` + allow-list
`portal/__tests__/raw-enum-allow-list.json` (StatusBadge, InvoiceStatusBadge — "label map — …").

```bash
cd /Users/rakheendama/Projects/2026/worktree-epic-633C/portal
cat > components/__raw-enum-guard-proof.tsx <<'TSX'
export function RawEnumGuardProof({ item }: { item: { status: string } }) {
  return <p>{item.status}</p>;
}
TSX
NODE_OPTIONS="" pnpm exec vitest run __tests__/no-raw-enum-copy.test.ts > /tmp/guard-red-633C.log 2>&1; echo GUARD_PLANTED=$?
rm components/__raw-enum-guard-proof.tsx
NODE_OPTIONS="" pnpm exec vitest run __tests__/no-raw-enum-copy.test.ts > /tmp/guard-green-633C.log 2>&1; echo GUARD_CLEAN=$?
git status --short components/ | grep proof     # no output
```

Exit codes: **`GUARD_PLANTED=1`**, **`GUARD_CLEAN=0`**; `git status` shows no proof file.

Planted run (red):

```
 FAIL  __tests__/no-raw-enum-copy.test.ts > no raw backend enum is rendered to a client (Phase 89 633C, §14.3.15, B9) > zero raw enum renders outside the allow-list
AssertionError: expected [ Array(1) ] to deeply equal []

- Expected
+ Received

- []
+ [
+   "components/__raw-enum-guard-proof.tsx:2 [jsx-child]: {item.status}",
+ ]

 ❯ __tests__/no-raw-enum-copy.test.ts:201:7
     × zero raw enum renders outside the allow-list 4ms
 Test Files  1 failed (1)
      Tests  1 failed | 3 passed (4)
```

After removal (green):

```
 Test Files  1 passed (1)
      Tests  4 passed (4)
```

The same plant was also run at HEAD before any source change (`red-at-head.md` §2): the planted line
appeared as a 6th hit next to the five real ones.

Scope notes (documented in the guard's header): the detector sees bare `.status` /
`.transactionType` / `.sourceEntity` JSX renders (parens, `!`, `as`, optional chaining) and
`.replace(/_/g, …)` / `.replaceAll("_", …)` on one; it does **not** see template literals or helper
calls such as `formatStatus(d.status)` — which is why the deadline status badges ("UPCOMING",
"DUE SOON", `deadline-list.tsx:299`, `deadline-detail-panel.tsx:152`) are not flagged (D2 — proposed
`G89-` follow-up, not bundled).
