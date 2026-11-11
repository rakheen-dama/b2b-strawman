# ADR-373: Detail-page hero with KPI strip over a summary rail: tile sourcing and redaction

**Status**: Accepted (supersedes [ADR-286](ADR-286-sidebar-layout-entity-detail.md) for the matter and client detail pages)

**Context**:

The matter and client detail pages are the screens a managing partner looks at longest. Both are monoliths:

- **Matter.** `matters/[id]/page.tsx` is 680 lines and makes about 25 reads. It renders `MatterHeaderCard` (`components/projects/matter-header-card.tsx`, 129 lines) above `MatterTabs`, which has 22 grouped, module-gated tabs driven by `?tab=`. The header carries `data-testid="matter-header-card"`, the `<h1 data-testid="matter-name">`, `project-status-badge`, `project-closed-at`, `header-lifecycle-actions`, the reference number, and `HeaderFactsStrip` (`matter-header-fields` → `matter-header-field-<slug>`, asserted in `day-01-03-onboarding.spec.ts`).
- **Client.** `clients/[id]/page.tsx` is 756 lines. It mounts `ClientHeaderCardWithLifecycle` → `ClientHeaderCard` (221 lines; `client-header-card`, `client-name` h1, `customer-status-badge`, `lifecycle-badge`, `client-header-actions` used by `documents/document-generation.spec.ts`, and `smart-primary-action`) above `CustomerGroupedTabs`.
- **Shared tab bar.** Both tab sets use `components/shared/grouped-tab-bar.tsx` ([ADR-287](ADR-287-grouped-tabs-dense-navigation.md), [ADR-298](ADR-298-shared-grouped-tab-bar.md)), which `e2e/helpers/grouped-tabs.ts` drives 78 times.
- **Headers show no money.** Today the money sits inside tab panels or in client components: the matter Overview strip (`components/projects/kpi-dashboard.tsx`) and the client-side `TrustBalanceCard`.

[ADR-286](ADR-286-sidebar-layout-entity-detail.md) chose a 280px collapsible metadata sidebar for entity detail pages. HEAD does not render one; both pages use header cards above the tab bar. The admincn template's detail views use a left summary rail. Founder decision 4 (2026-11-11) chose a **hero header with a 4-tile KPI strip above the existing tabs**, with tab content staying full-width.

Every tile has to respect the existing financial gates. Rulings §3.2 and the requirements' constraints say a null value renders as "—" or a hidden tile, never as 0. The server-side gate idiom `hasCapability(caps, cap)` in `lib/dashboard-home.ts` (admin ∨ owner ∨ held) is already what `matters/[id]/page.tsx` uses for its Overview tiles.

**Options Considered**:

1. **Left summary rail** — identity, status, facts and KPIs in a fixed side column beside the tabs (the template's layout, and ADR-286).
   - Pros: Metadata stays visible while scrolling long tab panels.
   - Cons: At 1024 the rail takes ≈280px from tab content that is already tight. Disbursement and trust tables needed an `@container` fix at that width (G88-M4), and `GroupedTabBar` would overflow sooner. It moves `matter-header-card` and `client-header-actions` into a new landmark, which risks the e2e anchors. The founder rejected it.
2. **Hero header with identity, status, actions, facts and a 4-tile KPI strip above a sticky tab bar; tiles are sourced from reads that already exist and gated exactly like those reads (CHOSEN).**
   - Pros: Money and risk are visible on every tab. Tab content keeps full width. The h1 and test ids stay in one header, and no new endpoint is needed.
   - Cons: The hero is taller than today's card, which pushes tab content down; a sticky tab bar mitigates this. It needs one or two extra existing reads per page.
3. **Restyle the header cards only; no KPI strip** — the cheapest visual alignment.
   - Pros: Small diff; no new reads.
   - Cons: It misses the phase objective (the money a firm cares about stays buried in tabs) and gives DetailHero nothing to do beyond a restyle.
4. **KPI strip inside the Overview tab only** — evolve the existing `kpi-dashboard.tsx` strip.
   - Pros: Reuses an existing surface and fetches.
   - Cons: It is invisible on the other 21 matter tabs and absent on clients, and it already exists in substance.

**Decision**: Option 2 — `MatterHeaderCard` and `ClientHeaderCard` are re-implemented on a shared `DetailHero` (identity, status, actions, facts and a strip of up to four compact StatCard tiles, with the same test ids and single `<h1>`), each tile fetched server-side from an existing endpoint under that endpoint's own gate.

**Rationale**:

**Rendering rule.** Tiles go through the page's `WidgetResult` idiom: **gate fails or result absent → tile omitted; read failed → `state="error"` ("—" with "Couldn't load"); a real zero → the formatted zero**. The full `DetailHero` and tile contract is in §15.2.4 of the [architecture doc](../architecture/phase90-ui-modernization.md).

1. **Matter tiles.**

   | Tile | Source | Gate | Notes |
   |---|---|---|---|
   | Unbilled WIP | `GET /api/matters/{id}/unbilled-summary` (`getMatterUnbilledSummary`, `lib/api/matters.ts`, unused today) | `PROJECT_MANAGEMENT` **and** (admin ∨ owner ∨ `FINANCIAL_VISIBILITY`) | The endpoint does no financial redaction, so the page must not call it outside this gate. `getMatterProfitability` is the wrong figure (total billable, not unbilled). |
   | Trust balance | `fetchMatterTrustBalance(id)` → `GET /api/matters/{id}/trust-balance` | `VIEW_TRUST` + `trust_accounting` module | Already a `WidgetResult` on the page. |
   | Outstanding | `fetchOutstandingSummaryAction(id)` → `outstanding-summary?matterId=` | `FINANCIAL_VISIBILITY` | Already in hand. Default currency, as the endpoint returns. |
   | Next court date | `GET /api/matters/{id}/upcoming-deadlines` (first row) | matter view + `court_calendar` module | **Lifted** from `kpi-dashboard.tsx` to the page and passed down, so it is fetched once. Hidden when the module is off. "None scheduled" when the module is on and the list is empty. |

   The hero extraction also removes the duplicate `getMatterBudget` call (page and `KPIDashboard`), a visual-only refactor that brings `page.tsx` well below 680 lines without touching tab-panel internals.
2. **Client tiles.**

   | Tile | Source | Gate | Notes |
   |---|---|---|---|
   | Open matters | count of `status === "ACTIVE"` in `listClientMatters(id)` | matter visibility | Already in hand. |
   | Trust balance | `GET /api/trust-accounts/{primary}/client-ledgers/{clientId}` | `VIEW_TRUST` + module | One extra server read for the hero (the trust-accounts list, then the primary account's client ledger). `TrustBalanceCard` keeps its own client fetch. De-duplicating the two reads is deferred (open question, architecture §15.12). Primary account only, the same figure the card shows today. |
   | Outstanding | `getDebtorDetail(id)` → sum of `outstandingInvoices[].balanceDue` | admin ∨ owner ∨ `INVOICING` | Already in hand. Summed in the org default currency only; other currencies are noted, never added (rulings §6). |
   | FICA status | `GET /api/clients/{id}/fica-status` (`getClientFicaStatus`, `lib/api/clients.ts`) | ungated | Rendered with a status token **plus an icon and label** ([ADR-370](ADR-370-chart-kit-and-palette.md)), never colour alone. |

3. **Redaction is inherited, never re-derived.** The hero never computes a figure the API would hide. It calls an endpoint only when the user passes that endpoint's gate, using the server idiom that already guards the Overview tiles. The client trust tile and the matter outstanding tile mirror existing gates. This differs from the dashboard KPI row, which relies on backend role redaction and prerequisite P1 (rulings R2). A hidden tile shrinks the strip (fewer tiles, same order); it never renders 0.
4. **Layout and selectors.**
   - The hero contains: identifier chip (reference or case number), the single `<h1>` keeping `matter-name` / `client-name`, the status and lifecycle badges with their test ids, a subtitle line (client or fee earner and dates), the actions slot (`header-lifecycle-actions`, `client-header-actions` and `smart-primary-action` unchanged), and `HeaderFactsStrip` with its `matter-header-fields` hooks.
   - The KPI strip is 4×1 at 1440 and 2×2 at 1024.
   - `GroupedTabBar` is restyled once for both pages, with tab names and order unchanged. It becomes sticky below the hero. Each page keeps its own URL mechanics (matters use `history.replaceState`, clients use `router.replace`; rulings §3.5).

**Consequences**:

- Positive:
  - Trust, outstanding and WIP are visible from any tab. Partners see the money without opening Overview or Trust.
  - No new endpoint ([ADR-372](ADR-372-read-only-chart-series-endpoints.md) is for chart series only). The hero slices are frontend-only and run FE-GATE plus the legal suites.
  - On the matter page, upcoming deadlines are lifted and the duplicate budget fetch is dropped, so each is fetched once.
- Negative:
  - Extra server reads on page load: `unbilled-summary` for gated matter viewers; on the client page, `fica-status` and one extra server read for the hero's trust tile, which the page waits for before rendering.
  - `TrustBalanceCard` keeps its own client fetch, so the client ledger is read twice per client page (hero on the server, card in the browser). The dedupe is deferred (open question, architecture §15.12).
  - The client trust tile shows the primary account only. A firm with several trust accounts sees the same partial figure the card shows today. A cross-account per-client total would need the list endpoint's `trustBalance` exposed per id, which is out of scope.
  - `matter-header-card.test.tsx`, `client-header-card.test.tsx` and `kpi-dashboard.test.tsx` are rewritten around DetailHero while still asserting the same test ids. Tile copy ("Unbilled WIP", "Next court date", …) is new UI text on a new component, not a rename of existing copy.
  - ADR-286's sidebar decision no longer applies to these two pages. Any future entity page follows this ADR.

**Related**: [ADR-286](ADR-286-sidebar-layout-entity-detail.md) (superseded here), [ADR-287](ADR-287-grouped-tabs-dense-navigation.md) / [ADR-298](ADR-298-shared-grouped-tab-bar.md) (tab bar kept, restyled), [ADR-367](ADR-367-design-system-layering.md) (StatCard in `@b2mash/ui`; DetailHero in `frontend/components`), [ADR-370](ADR-370-chart-kit-and-palette.md) (status tokens, micro presets), [ADR-372](ADR-372-read-only-chart-series-endpoints.md) (the only new endpoints, none used here), [ADR-361](ADR-361-client-ledger-running-balance-server-side.md) (client-ledger balance semantics); [architecture §15](../architecture/phase90-ui-modernization.md).
