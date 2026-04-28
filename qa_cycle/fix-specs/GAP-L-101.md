# Fix Spec: GAP-L-101 — Retention UI on matter detail page

## Problem

Per-matter retention metadata persists at the DB layer (`projects.retention_clock_started_at`
populated on close, computed `end_date = clock_start + legal_matter_retention_years` per ADR-249),
and the backend already exposes both fields on the matter-detail GET response — but **the matter
detail page in the UI surfaces nothing**. Attorneys / firm admins have no way to see when the
retention clock started, when this closed matter is scheduled to be deleted, or whether the
firm-wide retention period has been configured at all without leaving the matter and going to
Settings → Data Protection.

The component that *should* surface this — `frontend/components/legal/retention-card.tsx` — exists
and is wired into the Overview tab, but it self-gates on three conditions simultaneously
(`status === "CLOSED"` AND `retentionClockStartedAt != null` AND `retentionEndsOn != null`) and
renders nothing when any one fails. On the cycle-55 / cycle-57 test tenant `org_settings.legal_matter_retention_years`
is **NULL**, so even though the matter is CLOSED with a stamped clock, `retentionEndsOn` is null
(`ProjectService.computeRetentionEndsOn` returns null deliberately when the setting is unconfigured)
and the card hides entirely. Result: 100 % of QA cycles since cycle 1 reported the same gap
(`OBS-Day60-RetentionShape` → `OBS-Day85-NoMatterRetentionUI`), reconfirmed cycle-57.

This is a compliance-relevant disclosure: a closed-matter UI that goes silent on retention is the
exact opposite of what POPIA/AML reviewers expect to see — they expect a visible clock + scheduled
deletion date the moment a matter goes CLOSED.

Evidence: `qa_cycle/checkpoint-results/day-85.md §85.3`; `qa_cycle/checkpoint-results/cycle55-day85-1.3-retention-clock.txt`;
`OBS-Day85-NoMatterRetentionUI` (cycle 55 + cycle 57 reconfirm).

## Root Cause

Two layers, one of which is already correct and one which over-gates the UI.

1. **Backend already exposes the data correctly.** `ProjectController.getProject` (line 196) calls
   `projectService.computeRetentionEndsOn(pwr.project())` and threads both
   `retentionClockStartedAt` and `retentionEndsOn` into `ProjectResponse.from(..., retentionEndsOn)`
   (lines 480–510). The detail-endpoint payload already carries:
   - `retentionClockStartedAt: Instant | null` — the canonical retention anchor (ADR-249); set on
     first ACTIVE→COMPLETED or ACTIVE→CLOSED transition; preserved across reopens
   - `retentionEndsOn: LocalDate | null` — server-computed `clock_start + years`, null whenever
     the matter isn't CLOSED, the clock isn't stamped, OR the org setting is null/zero
   - `status` — used to distinguish "clock not running yet" from "clock running"
   No backend change is required.

2. **Frontend `RetentionCard` is too aggressive about hiding.** `frontend/components/legal/retention-card.tsx`
   lines 53–59:

   ```tsx
   if (status !== "CLOSED" || retentionClockStartedAt == null) {
     return null;
   }
   if (retentionEndsOn == null) {
     return null;
   }
   ```

   The first guard is correct (no clock → no card). The second is over-tight — when
   `retentionEndsOn == null` the card has *meaningful* information to show: "Clock running since
   {date} — retention period not configured by this org. Set legalMatterRetentionYears in Data
   Protection settings to compute the deletion date." Hiding is the worst behaviour because the
   firm has no signal that *something* needs configuring. We need a 3-state card: (a) clock not
   yet running (closed-not-yet, or COMPLETED but never CLOSED), (b) clock running but org
   unconfigured → end-date unknown, (c) clock running + end-date computed (the existing happy
   path).

3. **There is no signal pre-close either.** ACTIVE / COMPLETED matters carry a clear default
   ("retention will start when the matter is closed: 5 years from close, or your firm's setting")
   that staff would benefit from seeing during day-to-day work, especially during the close
   workflow. The current card returns null for non-CLOSED matters, so attorneys close the matter
   without any preview of the consequence.

## Fix

**Frontend-only**. The backend payload is already complete (`retentionClockStartedAt`,
`retentionEndsOn`, `status` on the matter-detail GET response). Change is two files:

### Step 1 — Expand `RetentionCard` to a 3-state component

`frontend/components/legal/retention-card.tsx` is rewritten to render in **three** states instead
of self-hiding. Component name + props stay the same to avoid call-site churn (`OverviewTab`
already passes `status`, `retentionClockStartedAt`, `retentionEndsOn`, `slug` per `overview-tab.tsx`
lines 560–565).

Logic:

| `status` | `retentionClockStartedAt` | `retentionEndsOn` | Render |
|----------|---------------------------|-------------------|--------|
| `CLOSED` | non-null                  | non-null          | **State C — Active**: end-date + days remaining (existing copy, unchanged) |
| `CLOSED` | non-null                  | null              | **State B — Unconfigured**: clock-start date + warning that org's retention period isn't configured + deep-link to data-protection settings |
| `CLOSED` | null                      | (any)             | **State A — Pre-clock**: clock not yet stamped (rare race; e.g. matter closed via legacy path before ADR-249 landed) — show "retention clock not yet stamped — re-close from the actions menu to start" |
| `COMPLETED` | non-null               | (any)             | **State A — Pre-clock**: stamped at completion (ADR-249 minimal-slice) but matter not yet CLOSED — show "Retention will begin when matter is CLOSED. Default: 5 years (firm setting overrides)." |
| Any other | (any)                    | (any)             | **null** (don't render — matter is still ACTIVE, no retention signal yet) |

The card title stays "Retention period". Visual treatment:
- State C: existing teal/amber stripe + days-remaining strong text (no change)
- State B: amber/warning border, second line nudges to settings page; days-remaining suppressed
- State A: muted/neutral border; second line is informational, no CTA needed

Code shape (replace `frontend/components/legal/retention-card.tsx` lines 48–89):

```tsx
export function RetentionCard({
  status,
  retentionClockStartedAt,
  retentionEndsOn,
  slug,
}: RetentionCardProps) {
  // ACTIVE / DRAFT / pre-completion: nothing to surface yet
  if (status !== "CLOSED" && status !== "COMPLETED") {
    return null;
  }

  const clockStartedDate =
    retentionClockStartedAt != null
      ? formatLocalDate(retentionClockStartedAt.slice(0, 10))
      : null;

  // State A — clock not yet stamped (rare) or stamped but not CLOSED
  if (retentionClockStartedAt == null) {
    return (
      <Card data-testid="retention-card" data-state="pre-clock">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <ShieldAlert className="size-4 text-slate-500" aria-hidden="true" />
            Retention period
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-slate-600 dark:text-slate-400">
          <p>
            Retention clock not yet stamped. Close this matter from the actions
            menu to start the retention period (5 years by default; your firm's
            setting overrides).
          </p>
        </CardContent>
      </Card>
    );
  }

  // State A.2 — clock stamped at completion, but matter not yet CLOSED
  if (status === "COMPLETED") {
    return (
      <Card data-testid="retention-card" data-state="completed-pending-close">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <ShieldAlert className="size-4 text-slate-500" aria-hidden="true" />
            Retention period
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-slate-600 dark:text-slate-400">
          <p>
            Retention will begin when this matter moves to <strong>Closed</strong>.
            Anchor preview: <strong>{clockStartedDate}</strong>. Default retention
            period is 5 years; your firm's setting overrides.
          </p>
        </CardContent>
      </Card>
    );
  }

  // State B — clock running but org's retention period is unconfigured
  if (retentionEndsOn == null) {
    return (
      <Card data-testid="retention-card" data-state="unconfigured">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <ShieldAlert
              className="size-4 text-amber-600 dark:text-amber-400"
              aria-hidden="true"
            />
            Retention period
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-slate-600 dark:text-slate-400">
          <p>
            Retention clock started on{" "}
            <strong className="text-slate-900 dark:text-slate-100">
              {clockStartedDate}
            </strong>
            . Your firm's matter-retention period isn't configured yet, so the
            scheduled deletion date can't be computed.
          </p>
          <Link
            href={`/org/${slug}/settings/data-protection`}
            className="inline-flex text-xs font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
          >
            Configure retention period →
          </Link>
        </CardContent>
      </Card>
    );
  }

  // State C — fully configured, end-date computed (existing happy path)
  const daysRemaining = daysRemainingUntil(retentionEndsOn);
  const formattedEndDate = formatLocalDate(retentionEndsOn);
  const remainingLabel =
    daysRemaining === 0 ? "0 days — pending deletion" : `${daysRemaining} days remaining`;

  return (
    <Card data-testid="retention-card" data-state="active">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <ShieldAlert className="size-4 text-amber-600 dark:text-amber-400" aria-hidden="true" />
          Retention period
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2 text-sm text-slate-600 dark:text-slate-400">
        <p>
          Clock started on{" "}
          <strong className="text-slate-900 dark:text-slate-100">{clockStartedDate}</strong>.
          This closed matter will be permanently deleted on{" "}
          <strong className="text-slate-900 dark:text-slate-100">{formattedEndDate}</strong> (
          <span data-testid="retention-card-days-remaining">{remainingLabel}</span>).
        </p>
        <Link
          href={`/org/${slug}/settings/data-protection`}
          className="inline-flex text-xs font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
        >
          View data-protection settings
        </Link>
      </CardContent>
    </Card>
  );
}
```

Note: State C copy is *additive* to the existing version — it now also surfaces the clock-start
date alongside the end-date. The existing tests against `retention-card-days-remaining` and
`data-testid="retention-card"` continue to pass (selectors unchanged on State C).

### Step 2 — Update `OverviewTab` JSDoc + JSX gate

`frontend/components/projects/overview-tab.tsx` lines 67–73 currently say:

```ts
/**
 * GAP-OBS-Day60-RetentionShape — retention clock anchor + computed end date
 * for the per-matter RetentionCard. Both null on non-closed matters; the
 * card self-gates on `status === "CLOSED" && retentionClockStartedAt != null`.
 */
retentionClockStartedAt: string | null;
retentionEndsOn: string | null;
```

Update the JSDoc to reflect the new 3-state behaviour:

```ts
/**
 * GAP-L-101 — retention clock anchor + computed end date for the per-matter
 * RetentionCard. The card now self-gates into a 3-state surface:
 *  - State A (pre-clock): COMPLETED or no anchor → informational copy
 *  - State B (unconfigured): CLOSED + anchor + null end-date → warning + settings deep-link
 *  - State C (active): CLOSED + anchor + end-date → existing days-remaining display
 * Returns null (not rendered) for ACTIVE/DRAFT.
 */
```

JSX call site at lines 556–565 stays unchanged (component swallows the new states internally).
The existing comment block referencing `GAP-OBS-Day60-RetentionShape` should be updated to mention
GAP-L-101.

### Step 3 — Tests

Update `frontend/components/legal/__tests__/retention-card.test.tsx`. The existing test file
covers State C (the happy path); add three new test cases:

1. `renders State A (pre-clock) when status is CLOSED but clock is null` — assert the card
   renders, the copy contains "not yet stamped", `data-state="pre-clock"`, no Link to settings.
2. `renders State A.2 (completed-pending-close) when status is COMPLETED with clock set` —
   assert the card renders, copy contains "begin when this matter moves to", clock-start date
   is rendered, no settings link, `data-state="completed-pending-close"`.
3. `renders State B (unconfigured) when CLOSED with clock but null end-date` — assert the card
   renders, `data-state="unconfigured"`, copy contains "isn't configured yet" and the
   clock-start date, the settings deep-link `/org/{slug}/settings/data-protection` is rendered,
   and `retention-card-days-remaining` is **not** present.
4. `renders nothing for ACTIVE / DRAFT statuses` — pre-existing assertion; keep, but move so all
   the negative tests are grouped.

Existing positive State C test stays — selectors unchanged so it should pass without
modification (the new clock-start prefix is additive, not breaking).

### Step 4 — Visibility / role gating

**All members see the card** (no admin gate). Justifications:

- Retention end-date is matter-scoped data the matter team needs to plan client communication
  and deletion timelines (attorneys + paralegals + admins all touch this).
- The settings deep-link (`/org/{slug}/settings/data-protection`) is itself role-gated on the
  destination side — non-admins clicking through land on the page with read-only or empty-
  state UX (existing behaviour, not part of this fix).
- The data is already exposed via the matter-detail GET (any member with view access to the
  matter receives `retentionClockStartedAt` + `retentionEndsOn` in the JSON response today —
  hiding the card client-side gives the false impression of secrecy).

The card is visible to anyone with view access to the matter (i.e. anyone who can already see
the matter detail page). No new capability check.

## Scope

Frontend only. No backend, no migration, no API contract change.

Files to modify:
- `frontend/components/legal/retention-card.tsx` — replace the body of `RetentionCard()` with the
  3-state implementation; keep function signature, props interface, file-level docstring,
  `daysRemainingUntil` helper, and import block unchanged.
- `frontend/components/projects/overview-tab.tsx` — update the JSDoc on the
  `retentionClockStartedAt` / `retentionEndsOn` props (lines 67–73) and the inline comment block
  on the JSX call site (lines 556–559) to reference GAP-L-101 and the 3-state behaviour. JSX
  itself is unchanged.
- `frontend/components/legal/__tests__/retention-card.test.tsx` — add 3 new test cases (State A,
  State A.2, State B) alongside the existing State C / null-render coverage.

Files to create: none.

Migration needed: **no**.
Backend changes: **no** — payload is already complete (`Project.retentionClockStartedAt` +
`ProjectService.computeRetentionEndsOn` + `ProjectResponse.from(..., retentionEndsOn)` already
ship every field the new card needs).

## Verification

QA will, on a fresh `bugfix_cycle_2026-04-26-slice1` frontend (after Dev pushes the fix; HMR will
pick up frontend changes — no service restart needed):

1. Authenticate as Bob (admin) via Keycloak; navigate to RAF matter `cc390c4f-…` detail page →
   Overview tab.
2. **State B verification (current test tenant — `org_settings.legal_matter_retention_years` is
   NULL on cycle-57 dataset)**: assert the right rail now shows a "Retention period" card
   (`data-testid="retention-card"`, `data-state="unconfigured"`) with copy referencing the
   matter's actual `retention_clock_started_at` (cycle-55 evidence: `2026-04-27`) and a "Configure
   retention period →" link to `/org/mathebula-partners/settings/data-protection`. Assert the
   card does NOT render `retention-card-days-remaining` (no end-date can be computed yet).
3. **State C verification**: from a separate browser tab, navigate to Settings → Data Protection
   and configure `legalMatterRetentionYears` (use 5 — POPIA / LSSA standard for legal records).
   Save. Reload the RAF matter Overview tab. Assert the card now renders with `data-state="active"`,
   shows clock-start `2026-04-27` AND end-date `2031-04-26` AND a `1825 days remaining`-style
   counter inside `[data-testid="retention-card-days-remaining"]`. Hovering over the days
   remaining shows the existing copy unchanged.
4. **State A verification (pre-CLOSED)**: navigate to a still-ACTIVE matter (e.g. the residual
   `Cycle19 Verify` matter from BUG-CYCLE26-07, or create a fresh ACTIVE matter on a non-Sipho
   customer). Assert no retention card is rendered (status is ACTIVE → returns null, no signal
   yet — correct).
5. **State A.2 verification (COMPLETED-not-CLOSED)**: complete a matter via the lifecycle action
   (without going through full closure). Assert a muted `data-state="completed-pending-close"`
   card renders with copy "Retention will begin when this matter moves to Closed" and the
   clock-start preview date.
6. **Unit-test sanity**: from frontend root, `pnpm test components/legal/__tests__/retention-card.test.tsx`
   passes all 4 new + 1 existing assertion.
7. **Lint / build gate**: `pnpm run lint` and `pnpm run build` both green.
8. **Cross-vertical regression**: log in as a non-legal tenant (e.g. accounting-za demo seed if
   present, or any tenant whose only matter type is not MATTER). Assert the RetentionCard does
   not render on those projects (the `status === "COMPLETED"` / `"CLOSED"` gate handles this
   today — no work needed beyond confirming no regression).
9. **Isolation invariant**: `grep -ic "moroka|EST-2026|estate"` on the captured matter-detail YAML
   for Sipho's RAF matter returns 0 (the new card surfaces matter-scoped data, not cross-tenant).

## Estimated Effort

**S** — under 30 minutes. The component already exists and is wired in; the change is replacing
the early-return with a switch over three terminal states. No new props, no new component, no API
plumbing, no backend work, no migration. Most of the time is in the 3 new Vitest cases (~50 LOC
total). Risk is low; rollback (revert the PR) is trivial since props and call site are unchanged.

## Severity / Demo Impact

- **Day 90 blocker**: NO — Day 90 final walk PASS already landed (cycle 57). This addresses a
  reconfirmed observation (`OBS-Day85-NoMatterRetentionUI`) tracked since cycle 1, not a new
  regression.
- **Demo impact**: HIGH for compliance-driven demos. Reviewers (POPIA, LSSA, BTO) frequently ask
  "how does the system show retention timelines for closed matters" — pre-fix, the answer is
  "the data is in the DB but no UI surfaces it"; post-fix, "the matter detail page shows a clock
  start, scheduled deletion date, and days remaining the moment the matter goes Closed". This
  is a structurally important compliance affordance for the legal-ZA vertical.
- **E.10 (Isolation gate)**: NOT AT RISK. This is matter-scoped data already shipped on the
  matter-detail GET. Isolation invariant `grep -ic "moroka|EST-2026|estate"` was 0 on cycle-57
  Sipho-side matter detail walk and stays 0 — the new card consumes the same response payload
  that was already isolation-clean.
- **E.11 (Audit/closure compliance)**: directly improves — staff get a visible retention timeline
  on the close workflow's destination page.
- **E.13 (Closure UX)**: directly improves — the close action's *consequence* is now visible
  immediately on the matter detail post-close, not buried in DB.

## Defer / Now Decision

**FIX NOW (slice 1).** Already decided per orchestrator scope. Rationale:

- Effort is **S** — single small frontend change, no backend, no migration, ≤30 minutes of dev
  work + 3 focused unit-test cases.
- The data is already on the wire (`retentionClockStartedAt`, `retentionEndsOn`, `status`); the
  only thing missing is rendering the unconfigured / pre-CLOSED states.
- Compliance-relevant — demo reviewers expect to see a retention clock on closed matters in any
  legal-vertical practice-management system. Pre-fix UX has "data exists but no UI" which is
  the exact failure mode that prompts compliance escalations.
- No migration, no frontend↔backend contract change, no cross-cutting concerns. Risk is low;
  rollback is trivial.
- Triaged across 3 cycles (1, 55, 57) as a persistent OBS — fixing it closes the loop on Sprint-2
  followup tagged in the cycle-1 walk notes.

## Notes

- This fix does NOT introduce a per-matter retention override — that's still a future product
  decision (would need a `projects.retention_years_override` column + UI). The card is purely
  read-only over org-level configuration.
- The `legal_matter_retention_years` setting itself is already exposed via
  `frontend/app/(app)/org/[slug]/settings/data-protection/page.tsx` for admins to configure; the
  card's State-B deep-link points there. No work needed on the destination page.
- **Default value clarification** (informational): `OrgSettings.DEFAULT_LEGAL_MATTER_RETENTION_YEARS = 5`
  exists as a Java-side fallback for the `getLegalMatterRetentionYears()` getter, but
  `getRawLegalMatterRetentionYears()` (used by `ProjectService.computeRetentionEndsOn`)
  deliberately returns `Optional.empty()` when unset so the UI can distinguish "configured
  with value 5" from "unconfigured". This fix preserves that distinction — State B copy says
  "isn't configured yet" rather than implying any specific default behaviour.
- **GAP-L-96 interaction** (informational, do NOT bundle): GAP-L-96 (insert a `retention_policies`
  MATTER row at close) is a separate, deferred concern. The retention sweeper doesn't act on
  MATTER yet, so no automated purge will fire on the State-C end-date until that lands. The card
  copy says "permanently deleted on" rather than e.g. "queued for review" because, by ADR-249,
  the contract is auto-purge once the sweeper integrates — surfacing the deletion date at the
  staff level *now* drives the right operational behaviour (staff can manually offboard records
  before the sweeper catches up).
- Engagement-letter / matter-closure terminology in the existing card copy is unchanged — this
  fix is structural (3-state rendering), not lexical.
