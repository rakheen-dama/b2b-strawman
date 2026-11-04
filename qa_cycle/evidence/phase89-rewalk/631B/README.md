# 631B evidence — SA-pinned `formatComplianceDateWithTime` (C3)

## Red (reproduce-first, HEAD 562cc7d2b, production code untouched)
`631B-red.txt` — `frontend/__tests__/lib/format-compliance-date.test.ts` run three ways (default zone, `TZ=UTC`,
`TZ=Pacific/Kiritimati`); every invocation: **6 failed | 3 passed (9)**, e.g.
`expected '03 Nov 2026, 23:30' to be '04 Nov 2026, 01:30'` (UTC), `expected '04 Nov 2026, 13:30' to be '04 Nov 2026, 01:30'` (Kiritimati),
`expected '03 Nov 2026, 18:30' to be '04 Nov 2026, 01:30'` (New York). The in-test `vi.stubEnv("TZ", …)` sweep makes it red on a SAST laptop too.

## Green (after the `packages/shared/src/format.ts` fix)
| Invocation | Result |
|---|---|
| `pnpm vitest run __tests__/lib/format-compliance-date.test.ts` (default zone) | 9 passed (9) |
| `TZ=UTC pnpm vitest run …` | 9 passed (9) |
| `TZ=Pacific/Kiritimati pnpm vitest run …` | 9 passed (9) |

## Callers (no call-site edits)
```
frontend/app/(app)/org/[slug]/compliance/requests/[id]/page.tsx:8:import { isOverdue, formatLocalDate, formatComplianceDateWithTime } from "@/lib/format";
frontend/app/(app)/org/[slug]/compliance/requests/[id]/page.tsx:84:          <span>Requested: {formatComplianceDateWithTime(request.requestedAt)}</span>
frontend/app/(app)/org/[slug]/compliance/requests/[id]/page.tsx:93:                {formatComplianceDateWithTime(request.completedAt)}
packages/shared/src/format.ts:133:export function formatComplianceDateWithTime(isoString: string): string {
```
(plus the new test file). The portal does not call it.

## Gates
- FE-GATE (`frontend`: lint, typecheck, format:check, test, build; clean `.next`): lint 0 errors / 79 warnings (baseline), typecheck ok,
  format:check clean, vitest **442 files / 3614 passed / 2 skipped**, build ok.
- PORTAL-GATE (`portal`: same five steps): lint 0 errors / 4 warnings (baseline), typecheck ok, format:check clean, vitest **45 files / 256 passed**, build ok.
- `prettier --config frontend/.prettierrc.json --check packages/shared/src/format.ts`: clean.
- No backend changes, so no backend verify.

## C3 — PASS (observed)
:3001 (e2e mock-auth stack; `e2e-frontend` container runs in UTC). Alice › Data requests › New Request (Acme Corp, Access Request) →
request `8cb6481f-4c74-4371-b350-9bfd75541d58`.

| Line | DB (`tenant_7d218705360b.data_subject_requests`) | Before (pre-fix image) | After (image rebuilt from this worktree) |
|---|---|---|---|
| Requested | `requested_at = 2026-11-04 19:03:10.060037+00` | `Requested: 04 Nov 2026, 19:03` (UTC wall clock) | `Requested: 04 Nov 2026, 21:03` (SAST, +2 h) |
| Completed | `completed_at = 2026-11-04 19:09:15.215264+00` | — | `Completed: 04 Nov 2026, 21:09` (SAST, +2 h) |

Same browser for before and after, so the change comes from the server render, not the browser zone. After: Start Processing → Complete, then a fresh reload.

Screenshots:
- `before-01-requested-utc-1024.png`, `before-01-requested-utc-1440.png`
- `after-01-requested-sast-1024.png`, `after-01-requested-sast-1440.png`
- `after-02-completed-sast-1024.png`, `after-02-completed-sast-1440.png`
