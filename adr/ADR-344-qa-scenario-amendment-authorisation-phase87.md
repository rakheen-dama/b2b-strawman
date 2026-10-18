# ADR-344: Scenario-Amendment Authorisation for the Phase 87 Capstone — URL, Label and Navigation Amendments Only, Behaviour Steps Frozen, Amended in the Same PR as the IA Change

**Status**: Accepted

**Context**:

CLAUDE.md §6 makes editing `qa/testplan/demos/*.md` a product decision: "Editing … to make a scenario 'match the product' is a product decision, not an agent decision. It must be flagged, justified, and authorized — not auto-applied to dispose of a bug." Phase 87 changes the product's URLs, labels and navigation on purpose — the IA change *is* the product decision — so the scenarios and Playwright configs that drive the current IA by URL and label will go red by design. The requirements authorise their amendment "because the IA change is the product decision" and ask this ADR to bound that authorisation. Facts at HEAD `41cb211fb` (`.arch-context-frontend.md` §12, `.arch-context.md` §2.3, §9.5):

- Scenario docs: `qa/testplan/demos/legal-za-90day-keycloak.md` (601 lines, 318 `- [ ]` steps, 17 steps naming a URL or label Phase 87 changes, **12 stale "Pipeline" references** to the CRM board deleted in Epic 596) and `legal-za-full-lifecycle-keycloak.md` (952 lines, 335 steps, 23 such steps, 0 Pipeline). Navigation is phrased by sidebar label (≈ 50 "Navigate to **Clients**" steps each); steps 0.23/0.24/0.59/90.3 assert the sidebar list verbatim ("Matters, Clients, Fee Notes … Trust Accounting, Court Calendar, Conflict Check, Tariffs"). The authorised-amendment annotation style exists: `legal-za-full-lifecycle-keycloak.md:214` "(LZKC-029 amendment, user-authorized 2026-07-13 option (a): …)".
- Playwright: `frontend/e2e/playwright.legal-lifecycle.config.ts` (8 specs: `day-00-firm-setup … day-90-review`) and `playwright.legal-depth-ii.config.ts` (4 specs); both **refuse non-mock auth** since Epic 606 (`E2E_AUTH_MODE=mock`, run on the :3001 mock stack). Navigation is by URL: 133 `page.goto()` vs 28 `getByRole("link")`; literals `/projects` ×32, `/customers` ×12, `/settings/` ×16, `/invoices` ×11, `/conflict-check` ×8, `/legal/disbursements` ×7, `/trust-accounting/*` ≈ 15, `/court-calendar` ×3, `/my-work` ×3.
- Inherited DEFERRED baseline (Phase 86 closure §7.4, "capstone PARTIAL"): last mock-stack run lifecycle **63 pass / 4 fail / 6 skip / 19 not run**, depth-ii **2 / 2 / 11 / 7**; zero failures caused by 86; #1579–#1581 since fixed two. Still open, explicitly *not* to be disposed of by spec edits: `day-00-firm-setup.spec.ts:326` (trust-settings locator hits a Card div), `day-30-billing.spec.ts:32` + `:159` (spec expects an inline fee-note dialog; `create-invoice-button.tsx` is a client-picker popover), `day-45-recon-prescription.spec.ts:41` (bank CSV upload not implemented), `day-90-review.spec.ts:61` (knock-on) + `:177` (profitability strict multi-match); depth-ii `day-05-disbursements.spec.ts:51` (its own pre-existing issue once #1579 cleared `:33`) — **seven failing spec locations in five files**, the one count used throughout this ADR and the architecture doc (§12.14 #7). `ai-core-verification-keycloak.md` is DEFERRED — step V1 needs a human-entered funded Anthropic key (`SetApiKeyDialog`, no env path, ≈ R10–R30). Screenshot baselines were never committed; `frontend/test-results/` (9 files) is still tracked.
- `marketing/decks/exec-deck.html` (171 lines) has **no `<img>`, no `url()`, no screenshot references**; `marketing/` has no image assets. The capstone *adds* a screenshot set rather than refreshing one.
- Other scenario rewordings listed by the 86 closure (`portal-client-90day` three-tenant wording + Days 7/75 `/deadlines` prerequisite; `admin-audit-30day` step 0.8 `deal.won`; `ai-core-verification:67,77`) still await user authorisation and are **not** covered here except where a Phase 87 URL/label change touches them.

**Options Considered**:

1. **No amendments; let the specs go red and fix them "later".**
   - Pros:
     - No product decision taken by an agent; §6 trivially satisfied.
   - Cons:
     - Violates CLAUDE.md §1/§3 ("never merged red, never skipped").
     - The merge-gate hook blocks every IA PR.
     - The capstone cannot be observed.
     - The 12 stale Pipeline refs and the 17 + 23 URL/label steps rot further.
2. **Authorise URL, label and navigation amendments only; behaviour steps frozen; amendments land in the same PR as the IA change that breaks them (CHOSEN)** — permitted edits: route paths (`/projects`→`/matters`, `/customers`→`/clients`, `/calendar`|`/court-calendar`→`/diary`, settings paths per the [ADR-341](ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md) redirect table), sidebar/zone/tab/button labels (`Projects`→`Matters`, `Invoices`→`Fee notes`, `Court Calendar`→`Diary`, `Action Items`→`Tasks`), navigation paths ("Settings > Trust Accounting (Finance group)" → "Settings > Fees & Trust > Trust accounting"), the sidebar-list assertions in steps 0.23/0.24/0.59/90.3, Playwright `page.goto()` literals and label locators, and deletion of the 12 stale Pipeline steps with an annotation citing Epic 596 / [ADR-336](ADR-336-engagement-letter-document-plus-acceptance.md). Frozen: amounts, dates, outcomes, assertions on data, Mailpit emails, trust balances, audit rows, DB states, step order and any step that exercises behaviour. Each amendment carries an inline annotation in the LZKC-029 style naming this ADR and the slice PR.
   - Pros:
     - Exactly the scope the IA change makes necessary and no more.
     - A reviewer can diff a scenario and classify every hunk as URL/label/nav or not.
     - Amending in the IA PR keeps every PR green on the specs it touches (CLAUDE.md §1) and keeps the scenario and the product in one commit — the audit trail §6 asks for.
     - Pre-existing failures stay visible: the PR must show the open-failure set is unchanged or smaller, with run logs, not hidden by an edit.
   - Cons:
     - Judgement calls remain at the edge: removing the Expenses tab ([ADR-340](ADR-340-disbursement-is-the-face-of-matter-costs.md)) or folding the prescription tab into the diary ([ADR-339](ADR-339-attorney-week-navigation-and-one-diary.md)) changes what a step *does*, not just what it is called — such steps are amended only as a stated consequence of the named ADR and called out in the PR as structural, not label.
     - Every IA PR carries Playwright + scenario edits, so IA PRs are larger and need the mock e2e stack run before merge.
3. **Blanket authorisation for Phase 87** — any scenario edit an agent judges necessary.
   - Pros:
     - Fastest path to green; no per-hunk classification.
   - Cons:
     - Is precisely the loophole §6 forbids: the seven open spec-drift failure locations (five files) (`day-30:32`, `day-45:41`, …) could be "fixed" by rewriting the step to match the popover or deleting the CSV upload.
     - The DEFERRED AI-core walk could be marked PASS by softening V1.
     - Destroys the evidentiary value of the 2026-07 verified lineage (QA cycle 2026-07-12: 7 fixes + one authorised amendment, LZKC-029).
4. **Rewrite the scenarios from scratch against the new shell.**
   - Pros:
     - Clean legal-native scripts with no horizontal residue; no classification of hunks.
   - Cons:
     - Discards 653 verified steps, every LZKC amendment and the cross-references bug reports use.
     - A rewrite is weeks of QA work in an IA phase.
     - New scripts would need their own full observed run before they prove anything (`feedback_test_plan_format`: step-by-step execution scripts, verified).
     - Hides whether the IA change broke behaviour, because nothing is diffed against the old script.

**Decision**: Option 2 — Phase 87 authorises amending `legal-za-90day-keycloak.md`, `legal-za-full-lifecycle-keycloak.md`, the `legal-lifecycle` and `legal-depth-ii` Playwright configs/specs, and (for Phase 87 URL/label changes only) the portal and admin-audit scenarios, for URL, label and navigation changes only, each amendment annotated and landed in the same PR as the IA change that requires it; behaviour steps are frozen; the inherited DEFERRED items stay DEFERRED unless fixed by their own one-fix PRs.

**Rationale**:

1. **The IA change is the product decision; the label edit is its record.** §6 exists so that a scenario is not quietly bent to a bug. Here the product moved on purpose and the ADRs ([ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md), [ADR-339](ADR-339-attorney-week-navigation-and-one-diary.md), [ADR-341](ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md)) are the evidence `file:line` §6 asks for; an amendment that cites them is authorised by construction, and one that cannot is not.
2. **Frozen behaviour is what makes the capstone a regression test.** If amounts, outcomes and assertions do not move, a green 90-day run through the new shell proves the shell did not change what the firm can do — the single most valuable fact the phase can report to a design partner.
3. **Same-PR amendment is the only way to satisfy "never merged red".** The merge-gate hook enforces green; an IA PR that breaks `page.goto("/org/x/projects")` must carry the `/matters` edit or it cannot merge. Splitting amendments into a trailing PR guarantees a red window.
4. **Pre-existing failures are owned, not papered.** CLAUDE.md §1 ("pre-existing baseline failures are not free passes") and §9 require the PR description to name the seven open failing spec locations (five files), show by run log that the set did not grow, and leave them for their own one-fix PRs (`day-30:32` and `day-00:326` are product decisions; `day-45:41` is a documented gap).
5. **Capstone definition.** The capstone is the 90-day legal-za lifecycle **observed on the Keycloak stack** (`compose/scripts/svc.sh`, ports 3000/8080/8443) through the new shell, driven by Playwright MCP in the browser (memory: QA drives the browser, not REST; Mailpit is the only REST exception), with evidence paths (logs, Mailpit message IDs, screenshots) in the closure report; Playwright specs additionally run on the :3001 mock stack because the configs require it (T1). The AI-core walk stays DEFERRED until a human enters a key — it is never inferred PASS (§3). The screenshot set captured during the Keycloak run is committed under `marketing/decks/` and referenced by `exec-deck.html` `<img>` tags; `frontend/test-results/` is untracked in the same PR.

**Consequences**:

- Positive:
  - A bounded, reviewable amendment class: URL / label / nav / sidebar-list / stale-feature-step deletion, each annotated "(ADR-344 amendment, PR #n: …)".
  - The 12 Pipeline steps leave the 90-day doc with their deletion recorded.
  - The 17 + 23 URL/label steps and the four sidebar-list assertions are rewritten to the six zones.
  - Playwright literals follow the redirect tables rather than relying on redirects — specs assert the canonical URL, so a redirect regression is still caught by the breadcrumb/route tests, not masked.
  - The closure report carries route-count (100 → n), settings-page-count (44 → ≤ 25) and nav-item deltas, the open-failure set before/after, and the screenshot set.
- Negative:
  - IA PRs are heavier: code + scenario + spec edits + a mock-stack Playwright run + (for the capstone slice) the Keycloak-stack observed walk.
  - The seven inherited failing spec locations (five files) remain red in the Playwright configs until their own PRs land.
  - The capstone run reports them as `pre-existing`, which is honest but not green — "ALL_GREEN" is not claimable for Phase 87 unless they are fixed first or formally re-triaged by the user.
  - Structural amendments (Expenses tab gone, prescription tab folded into Diary, tab ids `customers` → `clients` / `projects` → `matters` — covered by a client-side `LEGACY_TAB_IDS` alias map so old `?tab=` links resolve while specs assert the new ids) sit at the edge of "label" and must be called out individually.
  - A reviewer may push one back to a product decision.
  - Portal and admin-audit scenario rewordings already queued by the 86 closure remain unauthorised by this ADR.
  - Only their Phase 87 URL/label hunks are in scope.
- Follow-ups:
  - One-fix PRs for `day-00:326`, `day-30:32/:159`, `day-45:41`, `day-90:61/:177` (orchestrator-scheduled; product decisions for two of them).
  - Human-keyed AI-core walk; commit Playwright screenshot baselines once the shell is stable.
  - User authorisation for the non-87 rewordings listed in the 86 closure §7.8(b).
- Related:
  - [ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md) / [ADR-339](ADR-339-attorney-week-navigation-and-one-diary.md) / [ADR-341](ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md) (the changes that make amendments necessary and the redirect tables they cite), [ADR-340](ADR-340-disbursement-is-the-face-of-matter-costs.md) (the one structural tab change), [ADR-336](ADR-336-engagement-letter-document-plus-acceptance.md) (why Pipeline steps are deleted), [ADR-333](ADR-333-mcp-sole-conversational-ai-surface.md) (AI-core scenario lineage), CLAUDE.md §1, §3, §6, §8, §9.
