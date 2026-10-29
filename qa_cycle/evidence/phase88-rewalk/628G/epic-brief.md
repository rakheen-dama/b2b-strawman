---
# Implementation Brief: Slice 628G — Portal 90-day on the seeded rows

Phase 88 · Epic 628 · branch `phase88/11G-portal-90day-seed` · worktree `/Users/rakheendama/Projects/2026/worktree-epic-628G` · base `origin/main` 8121aa0c7.
Scout: read-only research on 2026-10-29. Every product claim below is cited `file:line` and was verified by reading the source. Facts marked **(DB)** were read from the live e2e Postgres while the reproducer's stack was up (fresh `e2e-up`, seeded 09:02Z).

## Scope

E2E specs plus portal plumbing. **No product code**: no `portal/app|components|lib|hooks`, no backend, no `compose/seed` (628F owns the seed; hotspot rule, 628F brief OD-10), and no `qa/testplan/demos/*.md` (628H owns the scenario amendments).

- **In scope:**
  - `portal/e2e/tests/portal-client-90day/{day-00-07,day-14-30,day-45-75,day-85-90}.spec.ts`: soft-skips removed, strict assertions, in-spec firm-API setup where the seed has no row.
  - `portal/package.json`: per-suite scripts.
  - Likely, subject to the orchestrator's ruling on the ODs:
    - NEW `portal/e2e/helpers/firm-api.ts` (mock-IDP Alice token, seed lookups, deposit / info-request builders);
    - `portal/playwright.portal.config.ts` (dev webServer env);
    - `portal/e2e/tests/responsive/portal-pages.spec.ts` (wrong default email, `waitForTimeout`);
    - `portal/e2e/tests/login-bare.spec.ts` (cap comment, 624I follow-up);
    - an ESLint rule twin for the portal (OD-8).
- **Verification:**
  - **E2E-PORTAL**: portal 90-day + responsive + `frontend/e2e/tests/portal/portal-data-isolation.spec.ts`, plus the opt-in `login-bare`;
  - **PORTAL-GATE**.
- **Out of scope:**
  - scenario wording (628H);
  - seed edits (628F only);
  - backend `@Profile` changes for a PSP;
  - the deadline-sync product gap;
  - the profile-edit product gap.
  These are routed as findings / follow-ups.

## Tasks (verbatim from `tasks/phase88-demo-ready-root-cause-closure.md`)

**Slice row 628G:** "`portal/e2e/tests/portal-client-90day/*` (soft-skips removed), `portal/package.json` | Portal 90-day on the seeded rows; E2E-PORTAL 0 failed / 0 soft-skipped. **Hand-off from 628F:** the boot seed now provides REQ SENT to contact@acme.example.com on Website Redesign ("Certified ID copy", FILE_UPLOAD), a portal-visible Statement of Account on Website Redesign, INV-0001 SENT + a DRAFT on Acme, and cadence BIWEEKLY — names in `frontend/e2e/helpers/seed.ts`; **no Acme trust deposit** (R-10 — Day 21 `/trust` needs in-spec setup via the firm API or a scenario call; the seed is 628F-only); magic-link budget: the seed's request send mints 1 token for the Acme contact (cap 3 / 5 min) before `globalSetup` and REGRESSION."

**628.16 — Portal 90-day on the seeded rows**
- Files: `$P/e2e/tests/portal-client-90day/{day-00-07,day-14-30,day-45-75,day-85-90}.spec.ts`. Soft-skips are removed, because the Day 3 request, the Day 60 statement and the Day 85 cadence now exist. Assertions are strict. `login-bare` from 624D stays. Also `$P/package.json` per-suite scripts.
- Tests: **E2E-PORTAL 0 failed / 0 soft-skipped**; `frontend/e2e/tests/portal/portal-data-isolation.spec.ts` green.
- Pattern: 617B (`globalSetup` single exchange; "exit 0 ≠ evidence").
- Note: the Day 85 digest cadence is read-only on the portal page; the firm sets it (§5 row).

**628.17 — PORTAL-GATE + E2E-PORTAL**
- Tests: **PORTAL-GATE**; **E2E-PORTAL** run log.
- Note: closes the portal seed ×3 and the carried default-config set (**PACK-01, PROJ-02/03, CUST-01/02, P-1**).

**Gate glossary** (task file line 11):
- **PORTAL-GATE** = `cd portal && pnpm lint && pnpm format:check && pnpm test && pnpm build`.
- **E2E-PORTAL** = `cd portal && pnpm test:e2e:portal-client-90day` + `responsive` + `frontend/e2e/tests/portal/portal-data-isolation.spec.ts`.
- **MARKERS** = `.claude/markers/verify-{backend,frontend,portal}.json` per touched area (`.claude/markers/README.md`).
- **Review**: every PR gets CodeRabbit (bounded wait; `DEFERRED` only on a genuine no-show) plus the two feature-dev:code-reviewer agents.

**Epic 628 Architecture Decision that applies:** "Strict assertions suite-wide; seed additions go into the e2e seed, not the specs; Playwright amendments authorised in-slice (D13, R10)."

### What 628G must do for the carried default-config set

628F already closed all six. The evidence is `qa_cycle/evidence/phase88-rewalk/628F/gate-final/default.txt`: a fresh stack @1563e023e, 159 passed / 0 failed / 18 skipped.

| Item | Original defect (R10 §B / §D) | 628F disposition (evidence line in `gate-final/default.txt`) | What 628G must do |
|---|---|---|---|
| **PACK-01** | `packs.spec.ts:94` `getByText('Power of Attorney')` strict mode (4 matches) | Rewritten. `packs.spec.ts:105` "Seeded legal-za pack is installed…" ✓ (l.155) and `:138` "Uninstall blocked when template edited, edit block cleared after revert" ✓ (l.160). The B1 hand-off found test 2 had never run at baseline. | Cite. Optionally re-observe with `pnpm test:e2e:settings` (OD-13). |
| **PROJ-02** | `project-tasks.spec.ts:119` grouped-tab locator | `openGroupedTab` consumed. Tests 1–7 ✓ (l.136–181). | Cite / re-observe `pnpm test:e2e:projects`. |
| **PROJ-03** | `project-time.spec.ts:131` grouped-tab locator | Tests 1–7 ✓ (l.132–148). | Same. |
| **CUST-01** | `customer-crud.spec.ts:47` `getByLabel('Name')` not exact | Create / custom fields / edit / search / list ✓ (l.46–84). | Cite / re-observe `pnpm test:e2e:customers`. |
| **CUST-02** | `customer-lifecycle.spec.ts:99` header smart action replaced the dropdown | Real flows ✓ (l.28–68). Two tests are `628F-BLOCKER` fixmes (DORMANT→OFFBOARDING, OFFBOARDING→OFFBOARDED; product bugs, reproduced). | Cite. The two blockers are product follow-ups, not 628G work. |
| **P-1** | `pnpm test:e2e -- projects` matched every spec (the checkout path contains `/Projects/`) | `frontend/package.json:17-20` path-anchored `test:e2e:{projects,customers,settings,navigation}`. The default config `testIgnore`s the legal suites. `AUTOMATION-NOTES.md:34-50` documents it. | Cite. Add the portal analogue: per-suite scripts, `--project`-based, so the path regex is not involved (OD-10). |
| **Portal seed ×3** (R10 §C1–C3) | Day 3 request, Day 60 SoA and Day 85 cadence had no seed rows | Seeded by 628F (Steps 10–12) | **628G's actual work**: consume the rows with strict specs. |

The register row (`tasks/phase88-gap-register.md:99`) "U22 Playwright suites | carried six, PACK-01, PROJ-02/03, CUST-01/02, portal seed ×3, P-1" closes with this slice. The PR body should give the table above, with 628F evidence paths for the default-config six and 628G run logs for the portal three.

## Prior hand-offs relevant to 628G (digest)

- **628F R-5:** "seed rows are read-only for default specs. State-changing tests create their own run-unique entities; shared settings changed by a test are restored in `afterAll`; never assert global counts." `frontend/e2e/helpers/seed.ts:4-7` extends this to "the legal suites, the portal 90-day suite and `scripts/regression-test.sh` read the same rows".
- **628F R-10:** no Acme deposit in the seed. 628G handles Day 21 by in-spec setup or a scenario call.
- **628F R-12, the builder rule 628G should inherit:**
  - No soft paths and no lint-evading swaps. Removing `.catch` but keeping a soft `if (await x.isVisible())` on an absent target is FORBIDDEN.
  - A bare `isVisible()` probe is allowed only for a genuinely conditional target, and only after an `a.or(b)` wait.
  - No `waitForTimeout`.
  - Every test title describes what the test does.
- **628F R-3:** "real flow where the product supports it; retitle where the check is legitimate but over-claims; `test.fixme` only for a reproduced product blocker".
- **628F R-4:** each blocker MUST be reproduced in a run, with a screenshot or log under the slice evidence dir, before its `test.fixme(true, "<slice>-BLOCKER: … — <file:line>")`.
- **628F brief OD-10 risk:** an Acme deposit gives Acme a trust balance, so a later legal-lifecycle Day 60 interest run includes Acme.
  - The allocation assertions filter by client id and survive (`frontend/e2e/tests/legal-lifecycle/day-60-interest.spec.ts:122-130`).
  - The fixme'd Bob test asserts `"Interest Credit"` rows `toHaveCount(2)` across the whole transactions page (`:183-189`).
  - So on a shared stack, **E2E-PORTAL runs after E2E-LEGAL**.
- **628F follow-up:** `e2e-up.sh` only warns on a seed failure. Always check that `E2E Boot-Seed Complete!` is in `docker compose -f compose/docker-compose.e2e.yml logs seed`.
- **624I gate run notes** (`qa_cycle/evidence/phase88-rewalk/624I/gate.md:58-73,99-103`):
  1. A clean worktree has no visual baselines. `toHaveScreenshot` then fails with "A snapshot doesn't exist … writing actual", so gates run with `--update-snapshots=all` and delete the PNGs afterwards.
  2. Two caps apply to the same address; the sequence and cap mechanics are in the magic-link section below.
  3. The 624I portal suites ran against a production portal build on :3002 with `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081`. The KC `svc.sh` portal was stopped and later restored.
  4. Follow-up handed to 628G: "Extend the comments in `login-bare.spec.ts:13-15` / `:96-97` to cover both caps … 628G, which edits the portal suites, may take it first."
- **Baseline before 628F** (617C / 624I): portal 90-day **5 passed / 3 failed / 1 skipped / 3 did not run**. The three failures were C1 Day 3, C2 Day 60 and C3 Day 85, all soft-assert `toBeTruthy`. The one skip was "Day 45 pay" (no Pay button). Did-not-run: Day 7, Day 75 and Day 90 (serial abort). Responsive: 21 passed. Isolation: 2 passed. Login-bare: 1 passed.
- **The 5 "passes" were vacuous.** They were screenshots behind soft guards; for example "Info-request detail view renders" clicked the Home card into an empty `/requests` (R10 §C1).
- **The reproducer** is measuring the post-628F baseline in parallel. Use its numbers as the before-state.

## Ground truth on the fresh seeded stack (DB, read-only, 2026-10-29 09:0xZ, tenant `tenant_7d218705360b`)

- `portal.portal_requests`: `REQ-0001 | SENT | total_items 1 | submitted_items 0`.
- `portal.portal_invoices`: `INV-0001 | SENT | payment_url IS NULL | 8625.00`. The DRAFT is not in the portal read model, as designed.
- `portal.portal_documents`: `statement-of-account-website-redesign-2026-10-29.pdf | scope PROJECT | application/pdf | 3210 bytes`. The tenant `documents` row has `visibility=PORTAL`, `UPLOADED`.
- `portal.portal_deadline_view`: **0 rows**.
- `portal.portal_trust_balance`: **0 rows**.
- `magic_link_tokens` for `contact@acme.example.com`: **already 3** at 09:04:47Z (seed 1 + reproducer runs), which demonstrates how fast the per-contact cap fills.
- Tenant mapping: `public.org_schema_mapping`, columns `(id, <clerk org id>, schema_name, created_at, shard)` → `e2e-test-org | tenant_7d218705360b`. The schema name changes on every fresh stack, so read it from this table.

## Census table (every guard; classification; replacement)

**Classes:**
- **(a)** The target exists in the product and the seed (or an earlier day of the same run) provides the data → strict assertion.
- **(b)** The target exists but the data needs in-spec setup (how is stated in the row).
- **(c)** The target is absent from the product → a `628G-BLOCKER` fixme with reproduced evidence, delete the dead branch, or retitle (OD per row).
- **(d)** Legitimate guard (env opt-in, error contract, re-throw).

**Patterns grep'd:** `test.skip(`, `test.fixme(`, `testInfo.skip`, `isVisible(…).catch`, `.catch(() => …)`, `if (!x) return`, `annotations.push`, try/catch-swallow, `expect.soft`, conditional `if (await …isVisible…)`, `waitForTimeout`.

**Pattern counts per file** (`grep -c` of the pattern set): day-00-07 29, day-14-30 8, day-45-75 33, day-85-90 27, login-bare 2, responsive 3. `waitForTimeout` appears 30× across the 4 day files and once in responsive. These are non-guard waits; delete them all (628F R-12, AUTOMATION-NOTES "Never use `waitForTimeout` to wait for UI").

**Verification method for "exists" claims:** each one was confirmed by reading the portal route or component, and the backend controller or service, cited in the row.

### `portal/e2e/tests/portal-client-90day/day-00-07.spec.ts` (226 lines)

| # | Line(s) | Current code (verbatim) | Guarded target | Class | Replacement |
|---|---|---|---|---|---|
| G1 | 28-29 | `const PORTAL_EMAIL =\n  process.env.PORTAL_CONTACT_EMAIL \|\| "sipho.portal@example.com";` | Default narrator. `sipho.portal@example.com` does not exist on the e2e stack (contact is `contact@acme.example.com`, `compose/seed/seed.sh:127`). | a (wrong default) | `process.env.PORTAL_CONTACT_EMAIL \|\| SEED.contactEmail` (same in all 4 day files; `login-bare.spec.ts:41` already defaults to the seed contact). |
| G2 | 34-39 | `test.beforeAll(async () => {\n    test.skip(\n      SKIP,\n      "SKIP_PORTAL_BASELINES=true — scaffold-only run (Epic 500A). …",\n    );\n  });` | env opt-out | d | Keep. Reword the message to drop the 500A/500B history. |
| G3 | 41-51 | `const jwt = await getPortalJwt(PORTAL_EMAIL);\n    if (!jwt) {\n      test.skip(\n        true,\n        \`Could not obtain portal JWT for ${PORTAL_EMAIL}. Stack may not be running or contact not seeded — deferred to slice 500B execution.\`,\n      );\n      return;\n    }\n    await loginAsPortalContact(page, jwt);` | Portal auth (globalSetup session, `helpers/auth.ts:122-136`) | a | Hard fail: `expect(jwt, \`portal JWT for ${PORTAL_EMAIL} — stack up, contact seeded, magic-link caps not hit?\`).toBeTruthy(); await loginAsPortalContact(page, jwt!);`. Pattern: the responsive spec throws (`responsive/portal-pages.spec.ts:76-84`). Same fix in all 4 day files. |
| G4 | 61-65 | `const main = page.locator("main");\n    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(true, "Portal /home not rendered — seed may be missing");\n      return;\n    }` | `/home` (`portal/app/(authenticated)/home/page.tsx:38-55`, h1 "Home" :43-45) | a | `await expect(page.getByRole("heading", { level: 1, name: "Home" })).toBeVisible();` |
| G5 | 70-78 | `expect\n      .soft(\n        await page\n          .getByText(/info request\|pending\|awaiting/i)\n          .first()\n          .isVisible({ timeout: 5000 })\n          .catch(() => false),\n      )\n      .toBeTruthy();` | "Pending info requests" card + count (`home/page.tsx:57-99`; the count is SENT/IN_PROGRESS with `submittedItems < totalItems`, :64-69) | a (b under OD-1(B)) | `const card = page.getByRole("link", { name: /Pending info requests/ }); await expect(card).toBeVisible(); await expect(card).toContainText(String(expectedPending));` where `expectedPending` is computed from `GET /portal/requests` with the session JWT (OD-1). Also assert the "Recent fee notes" card lists `INV-0001` (`home/page.tsx:176-231`, link text = invoice number). |
| G6 | 93-106 | `const infoRequestLink = page\n      .getByRole("link", { name: /info request\|view/i })\n      .first();\n    const hasLink = await infoRequestLink\n      .isVisible({ timeout: 5000 })\n      .catch(() => false);\n\n    if (!hasLink) {\n      test.skip(\n        true,\n        "No pending info-request rendered on /home — firm-side prerequisite not met",\n      );\n      return;\n    }` | The regex hit the Home card `<Link href="/requests">` (R10 §C1). The real detail link is `requests/page.tsx:79-80` `href={\`/requests/${r.id}\`}` | a (OD-1(A)) / b (OD-1(B)) | Click the Home card → `toHaveURL(/\/requests$/)`, h1 "Documents & requests" (`requests/page.tsx:52-54`) → click the row link for the target request (`getByRole("link").filter({ hasText: requestNumber })`) → `toHaveURL(new RegExp(\`/requests/${requestId}$\`))`. |
| G7 | 126-134 | `if (\n      !(await infoRequestLink.isVisible({ timeout: 5000 }).catch(() => false))\n    ) {\n      test.skip(true, "No pending info request to submit against");\n      return;\n    }` | same as G6 | a / b | Go straight to the detail: `page.goto(\`/requests/${requestId}\`)`. The scenario's 3.1 says "click the same pending info request". Either route is fine; a direct goto keeps Day 3 independent of Day 0's navigation. |
| G8 | 141-155 | `const fileInput = page.locator('input[type="file"]').first();\n    const hasFileInput = await fileInput\n      .isVisible({ timeout: 3000 })\n      .catch(() => false);\n\n    if (hasFileInput) {\n      …\n      await fileInput.setInputFiles({ name: "id-copy.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-1.4\\n%fake-pdf-for-scaffold\\n") });\n      await page.waitForTimeout(800);\n    }` | FILE_UPLOAD input (`requests/[id]/page.tsx:192-203`, sr-only label "Upload file for {item.name}" :194-196) | a | `await page.getByLabel(\`Upload file for ${itemName}\`).setInputFiles({ name: "id-copy.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-1.4\\n…\\n%%EOF\\n") });` — unconditional. |
| G9 | 158-163 | `const responseField = page\n      .getByRole("textbox", { name: /response\|note\|message/i })\n      .first();\n    if (await responseField.isVisible({ timeout: 2000 }).catch(() => false)) {\n      await responseField.fill("ID copy attached as requested");\n    }` | A free-text field. **Absent**: the item card renders only a file input for FILE_UPLOAD, else "Unsupported response type" (`requests/[id]/page.tsx:192-217`); the submit sends `textResponse: null` (:152-155) | c (dead branch) | Delete. Scenario 3.3 is conditional ("If the request has a free-text field"), so nothing is lost. |
| G10 | 165-172 | `const submitBtn = page\n      .getByRole("button", { name: /submit\|send\|complete/i })\n      .first();\n    if (await submitBtn.isVisible({ timeout: 3000 }).catch(() => false)) {\n      await submitBtn.click();\n      …\n    }` | "Upload and submit" button (`requests/[id]/page.tsx:204-211`; disabled until a file is chosen) | a | `const submit = page.getByRole("button", { name: "Upload and submit" }); await expect(submit).toBeEnabled(); await submit.click();` |
| G11 | 175-183 | `expect\n      .soft(\n        await page\n          .getByText(/submitted\|awaiting review\|received/i)\n          .first()\n          .isVisible({ timeout: 5000 })\n          .catch(() => false),\n      )\n      .toBeTruthy();` | Post-submit state: the item card shows "Submitted — status: SUBMITTED" (`:188-191`); the header shows "1/1 submitted • status IN_PROGRESS" (`:95-100`; `InformationRequest.markInProgress`, `PortalInformationRequestService.java:310`) | a | `await expect(page.getByText(\`Submitted — status: SUBMITTED\`)).toBeVisible({ timeout: 15_000 }); await expect(page.getByText(/1\/1 submitted • status IN_PROGRESS/)).toBeVisible();` then 3.5: `page.goto("/home")` → pending card count = `expectedPending - 1`. The upload PUTs to LocalStack via a presigned URL (`AWS_S3_PRESIGNER_ENDPOINT=http://localhost:4567`, `compose/docker-compose.e2e.yml:88`; bucket CORS `AllowedOrigins:["*"]`, `compose/data/s3/init-s3.sh`), so it is reachable from the host browser. |
| G12 | 202-209 | `const main = page.locator("main");\n    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(\n        true,\n        "/deadlines not rendered — deadlines module not enabled for this tenant or 404",\n      );\n      return;\n    }` | `/deadlines` page (`deadlines/page.tsx`; h1 "Diary dates"; module `deadlines`, which the e2e tenant has: the Home "Upcoming deadlines" card rendered in 617C) | a (the page) | `await expect(page.getByRole("heading", { level: 1, name: "Diary dates" })).toBeVisible();` |
| G13 | 211-219 | `expect\n      .soft(\n        await page\n          .getByText(/deadline\|due\|upcoming/i)\n          .first()\n          .isVisible({ timeout: 5000 })\n          .catch(() => false),\n      )\n      .toBeTruthy();` | A **populated** deadline list (scenario 7.3 / 7.4: "renders non-empty for the prerequisite-seeded tenants"). **Not producible:** **(DB)** `portal_deadline_view` has 0 rows; the reasons are in the "Deadlines" note below. The digest CTA is `portalHomeUrl`, not `/deadlines` (`templates/email/portal-weekly-digest.html:72`). | c | **OD-4.** Either assert the real empty state (`getByTestId("deadline-list-empty")` + "No deadlines in this view", `components/deadlines/deadline-list.tsx:236-246`) with an honest title, or `test.fixme(true, "628G-BLOCKER: …")` with the intended steps. |

**Deadlines: why no populated list can be produced** (`backend/.../customerbackend/service/DeadlinePortalSyncService.java`):
- `:36-44`: COURT_DATE, PRESCRIPTION and FILING sources are **Dormant** ("`CourtDateScheduledEvent` … do not yet exist").
- `:123-130`: TODO.
- The only wired source is CUSTOM_FIELD_DATE, via `FieldDateApproachingEvent` (`:94`). That event is emitted only by `automation/FieldDateScannerJob.java:70` (`@Scheduled(cron = "${app.automation.field-date-scan-cron:0 0 6 * * *}")`) at exactly 14, 7 or 1 days out (`THRESHOLDS`), and only for fields with `portalVisibleDeadline=true`.
- There is no on-demand trigger: the only `/internal/*` controllers are digest, members, orgs, audit, billing and portal-resync.

### `portal/e2e/tests/portal-client-90day/day-14-30.spec.ts` (89 lines)

| # | Line(s) | Current code (verbatim) | Guarded target | Class | Replacement |
|---|---|---|---|---|---|
| G14 | 28-33 | `test.beforeAll(async () => {\n    test.skip(\n      SKIP,\n      "SKIP_PORTAL_BASELINES=true — scaffold-only run …",\n    );\n  });` | env opt-out | d | Keep. |
| G15 | 35-45 | same JWT soft-skip as G3 | portal auth | a | Same hard fail as G3. |
| G16 | 55-62 | `const main = page.locator("main");\n    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(\n        true,\n        "/trust not rendered — non-legal-za tenant (profile-gated skip is expected)",\n      );\n      return;\n    }` | `/trust` (`trust/page.tsx:70-85`, h1 "Trust statement"; module `trust_accounting` is enabled on legal-za) | a | The tenant is legal-za: assert, never skip. With exactly one Acme trust matter, `/trust` auto-redirects to `/trust/{matterId}` (`trust/page.tsx:63-68`) → `await expect(page).toHaveURL(new RegExp(\`/trust/${wrId}$\`))`. |
| G17 | 65-72 | `const bodyText =\n      (await page\n        .locator("body")\n        .textContent()\n        .catch(() => "")) \|\| "";\n    if (/something went wrong\|internal server error/i.test(bodyText)) {\n      throw new Error("/trust rendered an error page — investigate backend");\n    }` | try-swallow plus a negative text check | a | Replace with positive strict assertions (next row). Optionally `await expect(page.getByText(/something went wrong/i)).toHaveCount(0)`. |
| G18 | 74-82 | `expect\n      .soft(\n        await page\n          .getByText(/trust\|balance\|deposit\|ledger/i)\n          .first()\n          .isVisible({ timeout: 5000 })\n          .catch(() => false),\n      )\n      .toBeTruthy();` | Balance card + first-deposit row (`trust/[matterId]/page.tsx:187-204`; `components/trust/balance-card.tsx:52-60` `aria-label="Current balance"`; `components/trust/transaction-list.tsx:151` `<table aria-label="Trust transactions">` with the Description column :174-176). **(DB)** `portal_trust_balance` is empty: there is no deposit. | **b** | **OD-2.** In-spec firm-API deposit in `beforeAll`: `POST /api/trust-accounts/{accountId}/transactions/deposit` (MANAGE_TRUST, Alice). Body: `{ clientId: acmeId, matterId: wrId, amount: 1500, reference: \`E2E-PORTAL-${RUN}\`, description: \`Portal Day 21 deposit ${RUN}\`, transactionDate: today }`. It is RECORDED immediately and synced to the portal synchronously after commit (`TrustTransactionService.java:218-302`; `TrustLedgerPortalSyncService.java:133-150,254-292`). Then assert: `getByLabel("Current balance")` visible; `getByRole("table", { name: "Trust transactions" }).getByRole("row").filter({ hasText: description })` visible and containing `DEPOSIT`. Optional 21.1/21.2: Mailpit subject `"E2E Test Organization: Trust account activity"` (`PortalEmailNotificationChannel.java:150-176,304-327`; link `/trust/{matterId}?orgId=e2e-test-org`, **no token minted**). Replay the link path against baseURL. |

### `portal/e2e/tests/portal-client-90day/day-45-75.spec.ts` (230 lines)

| # | Line(s) | Current code (verbatim) | Guarded target | Class | Replacement |
|---|---|---|---|---|---|
| G19 | 31-36 | `test.skip(SKIP, "SKIP_PORTAL_BASELINES=true — …")` in beforeAll | env | d | Keep. |
| G20 | 38-48 | JWT soft-skip, as G3 | auth | a | Hard fail. |
| G21 | 56-63 | `if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(\n        true,\n        "/invoices not rendered — tenant may not have invoices seeded",\n      );\n      return;\n    }` | `/invoices` (`invoices/page.tsx`, h1 "Fee notes") | a | `await expect(page.getByRole("heading", { level: 1, name: "Fee notes" })).toBeVisible();` |
| G22 | 65-72 | `const invoiceLink = page\n      .getByRole("link", { name: /invoice\|due\|unpaid\|sent/i })\n      .first();\n    if (await invoiceLink.isVisible({ timeout: 5000 }).catch(() => false)) {\n      await invoiceLink.click();\n      …\n    }` | Seeded INV-0001 SENT. **(DB)** It is in `portal_invoices`. Desktop table `aria-label="Fee Note list"`, link text = invoice number (`invoices/page.tsx:146,176-181`). The mobile card copy is `md:hidden`, and `getByRole` skips hidden nodes. | a | `await page.getByRole("link", { name: SEED.feeNoteSentNumber, exact: true }).click(); await expect(page.getByRole("heading", { level: 1, name: "INV-0001" })).toBeVisible();` Then assert 45.2 content: "Line Items" h2, the "Download PDF" button (`invoices/[id]/page.tsx:113-131,214`), and the status badge. |
| G23 | 87-93 | `if (!(await invoiceLink.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(true, "No payable invoice rendered");\n      return;\n    }` | same | a | same |
| G24 | 99-106 | `const payBtn = page.getByRole("button", { name: /pay\|checkout/i }).first();\n    if (!(await payBtn.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(\n        true,\n        "Pay action not rendered — invoice may already be paid, or PSP not configured",\n      );\n      return;\n    }` | "Pay Now" is an `<a href={paymentUrl}>` and renders only when `status==="SENT" && paymentUrl` (`invoices/[id]/page.tsx:92,142-161`). **(DB)** `payment_url IS NULL`. The e2e profile has **no PSP**: `MockPaymentGateway` is `@Profile({"local","dev","keycloak","test"})` (`integration/payment/MockPaymentGateway.java:34`), `MockPaymentController` is `@Profile({"local","dev","keycloak"})` (`:40`), and `application-e2e.yml` has no payment config. NoOp gateway → the page shows "Contact {orgName} to arrange payment" (`:194-200`). | c (environment; Payments is a CLAUDE.md §6 mandate exemption) | **OD-3.** |
| G25 | 116-124 | `expect\n      .soft(\n        await page\n          .getByText(/paid\|success\|receipt\|confirmation/i)\n          …\n      .toBeTruthy();` | Paid state | c | OD-3. |
| G26 | 141-145 | `if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(true, "/matters not rendered");\n      return;\n    }` | `/matters`, h1 "Your matters" (`matters/page.tsx:75-76`) | a | `await expect(page.getByRole("heading", { level: 1, name: "Your matters" })).toBeVisible();` |
| G27 | 151-155 | `const matterLink = main.locator('a[href^="/matters/"]').first();\n    if (!(await matterLink.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(true, "No matter rendered to drill into");\n      return;\n    }` | The Website Redesign card. **`.first()` is wrong**: `frontend/e2e/tests/projects/project-crud.spec.ts:52-55` creates matters for Acme Corp, so Acme can have several portal matters. | a | `await page.locator("main").getByRole("link").filter({ hasText: SEED.matter }).click(); await expect(page.getByRole("heading", { level: 1, name: SEED.matter })).toBeVisible();` (`matters/[id]/page.tsx:154-156`). If Acme already has a "Website Redesign …"-prefixed matter, use `getByRole("link", { name: new RegExp(\`^${SEED.matter}\\\\b\`) })` or navigate by id (`websiteRedesignId()` via the firm API). |
| G28 | 162-166 | `const docsTab = page.getByRole("tab", { name: /document/i }).first();\n    if (await docsTab.isVisible({ timeout: 3000 }).catch(() => false)) {\n      await docsTab.click();\n      …\n    }` | A "Documents" tab. **Absent**: the matter page renders sections (`<h2>Documents</h2>`, `matters/[id]/page.tsx:196-202`), not tabs | c (dead branch) | Delete. Assert `getByRole("heading", { level: 2, name: "Documents" })`. Scenario 60.2's "Documents tab" wording is 628H's call (see Findings). |
| G29 | 169-177 | `expect\n      .soft(\n        await page\n          .getByText(/Statement of Account\|financial statement\|\\.pdf/i)\n          …\n      .toBeTruthy();` | Seeded SoA. File name `statement-of-account-website-redesign-YYYY-MM-DD.pdf` (`verticals/legal/statement/StatementService.java:64,130,385-387`; **(DB)** confirmed). `/Statement of Account/i` can never match the hyphenated slug; only `/\.pdf/` did. | a | `const download = page.getByRole("button", { name: /^Download statement-of-account-website-redesign-\d{4}-\d{2}-\d{2}\.pdf$/ }); await expect(download).toBeVisible();` (`components/document-list.tsx:115-120` aria-label; the mobile copy is `md:hidden`). 60.4 download: `const presign = page.waitForResponse(r => r.url().includes("/presign-download") && r.ok()); await download.click(); const { presignedUrl } = await (await presign).json(); const pdf = await request.get(presignedUrl); expect(pdf.ok()).toBe(true); expect((await pdf.body()).subarray(0,5).toString()).toBe("%PDF-");`. The client opens the URL with `window.open` (`document-list.tsx:32-35`, `lib/api/documents.ts`). The download is audited as `portal.document.downloaded` (`portal/PortalQueryService.java:189-196`), which Day 90 uses. |
| G30 | 195-202 | `if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(\n        true,\n        "/deadlines not rendered — deadlines module not enabled for this tenant",\n      );\n      return;\n    }` | `/deadlines` page | a (the page) | h1 "Diary dates". |
| G31 | 205-212 | `const deadlineLink = page\n      .getByRole("link", { name: /deadline\|due\|upcoming/i })\n      .first();\n    if (await deadlineLink.isVisible({ timeout: 5000 }).catch(() => false)) {\n      await deadlineLink.click();\n      …\n    }` | A deadline row. Rows are `<button aria-label="View details for {label}">` (`deadline-list.tsx:271-276`), not links. **(DB)** There are 0 deadlines and none can be produced (G13). | c | OD-4. |
| G32 | 215-221 | `const markReadBtn = page\n      .getByRole("button", { name: /mark read\|dismiss\|acknowledge/i })\n      .first();\n    if (await markReadBtn.isVisible({ timeout: 3000 }).catch(() => false)) {\n      await markReadBtn.click();\n      …\n    }` | A mark-read control. **Absent**: `grep -rni "mark.*read\|markRead\|unread"` over `portal/{app,components,lib,hooks}` returns 0 hits. The detail panel (`components/deadlines/deadline-detail-panel.tsx:83-170`) shows Label / Due date / Status / Description / Source only, with no documents (scenario 75.3/75.6) | c | OD-4: `628G-BLOCKER` fixme with the intended steps. |

### `portal/e2e/tests/portal-client-90day/day-85-90.spec.ts` (190 lines)

| # | Line(s) | Current code (verbatim) | Guarded target | Class | Replacement |
|---|---|---|---|---|---|
| G33 | 32-37 | `test.skip(SKIP, …)` beforeAll | env | d | Keep. |
| G34 | 39-49 | JWT soft-skip | auth | a | Hard fail. |
| G35 | 57-61 | `if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(true, "/profile not rendered");\n      return;\n    }` | `/profile`, h1 "Profile" | a | h1 assertion. |
| G36 | 64-81 | `const nameField = page\n      .getByRole("textbox", { name: /name\|display/i })\n      .first();\n    if (await nameField.isVisible({ timeout: 3000 }).catch(() => false)) {\n      const currentValue = (await nameField.inputValue().catch(() => "")) \|\| "";\n      …\n      if (await saveBtn.isVisible({ timeout: 2000 }).catch(() => false)) {\n        await saveBtn.click();\n        …\n      }\n    }` | An editable profile. **Absent**: `profile/page.tsx` is display-only (a `<dl>` with Name / Email / Role / Client, :125-153) and the backend `customerbackend/controller/PortalProfileController.java:16,26` exposes only `GET /portal/me` | c | **OD-5.** Either retitle to "Day 85: Profile shows the client's contact details" with strict read-only assertions (Email `contact@acme.example.com`, Client `Acme Corp`, Role badge), or a `628G-BLOCKER` fixme with the intended edit steps. |
| G37 | 68 | `(await nameField.inputValue().catch(() => "")) \|\| ""` | swallow | c | Removed with G36. |
| G38 | 74-80 | `const saveBtn = …; if (await saveBtn.isVisible({ timeout: 2000 }).catch(() => false)) { … }` | Save button (absent) | c | Removed with G36. |
| G39 | 98-102 | `if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(true, "/settings/notifications not rendered");\n      return;\n    }` | `/settings/notifications`, h1 "Notification Preferences" (`settings/notifications/page.tsx`, h1 above `:229`) | a | h1 assertion. |
| G40 | 106-129 | `const cadenceCombobox = page\n      .getByRole("combobox", { name: /cadence\|frequency\|digest/i })\n      .first();\n    if (await cadenceCombobox.isVisible({ timeout: 3000 }).catch(() => false)) {\n      … biweeklyOption … isVisible({ timeout: 2000 }).catch(() => false) …\n    } else {\n      const biweeklyRadio = page\n        .getByRole("radio", { name: /biweekly\|bi-weekly\|every other/i })\n        .first();\n      if (await biweeklyRadio.isVisible({ timeout: 2000 }).catch(() => false)) { … }\n    }` | A client cadence control. **Absent by design**: cadence is firm-level (ADR-258). The page renders it read-only via `cadenceLabel()` (`settings/notifications/page.tsx:62-70,236-239`); the client can only toggle "Weekly digest" etc. (`:240-270`, switches `role="switch"`). This is register §5 row "Day 85 digest cadence (portal e2e title) \| W \| the portal page is read-only; the firm sets cadence" (`tasks/phase88-gap-register.md:169`), with scenario wording owned by 628H. | c (by design) | Delete the control interaction. **OD-6:** retitle. |
| G41 | 131-135 | `const saveBtn = page.getByRole("button", { name: /save\|update/i }).first();\n    if (await saveBtn.isVisible({ timeout: 2000 }).catch(() => false)) { … }` | Save (absent: toggles auto-save, `:178-190`) | c | Delete. |
| G42 | 139-147 | `expect\n      .soft(\n        await page\n          .getByText(/biweekly\|bi-weekly\|every other/i)\n          …\n      .toBeTruthy();` | Seeded BIWEEKLY → "Your firm sends bi-weekly digests." (`page.tsx:66-67`; `GET /portal/notification-preferences` → `firmDigestCadence`, `PortalNotificationPreferenceService.java:80`) | a | `await expect(page.getByText("Your firm sends bi-weekly digests.", { exact: true })).toBeVisible(); await expect(page.getByRole("switch", { name: /Weekly digest/ })).toBeVisible();` Assert absence of a cadence control too: `toHaveCount(0)` on `getByRole("combobox")` and `getByRole("radio")` inside main. |
| G43 | 166-170 | `if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {\n      test.skip(true, "/home not rendered");\n      return;\n    }` | It went to `/home`. The activity trail is the separate `/activity` route (`app/(authenticated)/activity/page.tsx`, h1 "Activity", tabs "Your actions" / "Firm actions", `<ul data-testid="activity-list">`) | a | `page.goto("/activity")`, h1 "Activity". |
| G44 | 175-183 | `expect\n      .soft(\n        await page\n          .getByText(/activity\|history\|recent\|timeline/i)\n          …\n      .toBeTruthy();` | "Your actions" rows for this run's Day 3 and Day 60. Summaries come from `portal/PortalActivityEventResponse.java` `summaryFor`: `"portal.request_item.submitted" → "You submitted an information request item"`, `"portal.document.downloaded" → "You downloaded a document"`, `"portal.document.upload_initiated" → "You started uploading a document"`, `"portal.invoice.paid" → "You paid a fee note"`. Rows render `{summary}`, actor "You", and a relative time. | a (data from Day 3 / Day 60 of the same run) | `const list = page.getByTestId("activity-list"); await expect(list.getByText("You submitted an information request item").first()).toBeVisible(); await expect(list.getByText("You downloaded a document").first()).toBeVisible();` 90.4's Day 45 pay / Day 75 mark-read / Day 85 profile-update rows do not exist (OD-3/4/5); note this in the PR. A stronger per-run check: compare the count before and after via `GET /portal/activity?tab=MINE` with the session JWT. |

### Other portal e2e files

| # | File:line | Current code (verbatim) | Target | Class | Action |
|---|---|---|---|---|---|
| G45 | `login-bare.spec.ts:57-62` | `test.beforeAll(async () => {\n    test.skip(\n      SKIP,\n      "SKIP_PORTAL_BASELINES=true — login-bare disabled. …",\n    );\n  });` | env | d | Keep. |
| G46 | `login-bare.spec.ts:148-152` | `try {\n      await page.goto(path);\n    } catch (e) {\n      throw new Error(redactToken(e instanceof Error ? e.message : String(e)));\n    }` | Re-throw with the token redacted | d | Keep. |
| G47 | `login-bare.spec.ts:153-155` | `await expect(mattersHeading.or(linkExpired).first()).toBeVisible();` then strict checks | the sanctioned `a.or(b)` wait | d | Keep. Optional OD-11: comment fix `:13-15`, `:96-97` (both caps). |
| G48 | `responsive/portal-pages.spec.ts:63-74` | `test.beforeAll(async () => {\n  if (SKIP) {\n    test.info().annotations.push({\n      type: "skipped-reason",\n      description: "SKIP_PORTAL_BASELINES=true",\n    });\n  }\n  test.skip(\n    SKIP,\n    "Portal baseline capture disabled via SKIP_PORTAL_BASELINES=true. …",\n  );\n});` | env | d | Keep. |
| G49 | `responsive/portal-pages.spec.ts:60-61` | `const PORTAL_EMAIL =\n  process.env.PORTAL_CONTACT_EMAIL \|\| "alice.portal@example.com";` | Wrong default (not seeded). It hard-fails (`:76-84` throws), so it is not a soft skip. | a (wrong default) | Default to the seed contact. Also drop `waitForTimeout(500)` at `:100`: assert the page h1 per route before the snapshot. |
| G51 | `helpers/auth.ts:49-56` | `try {\n    const all = JSON.parse(raw) as Record<string, PortalSession>;\n    return all[email] ?? null;\n  } catch {\n    return null;\n  }` | Env JSON parse | d | Keep. A null falls through to a real exchange. |
| G52 | `helpers/auth.ts:106-111` | `} catch {\n    // Network failures … should not escape — the contract is Promise<PortalSession \| null>; callers handle null\n    return null;\n  }` | Error contract | d | Keep. The **callers** must hard-fail on null (G3/G15/G20/G34). |
| G53 | `helpers/global-setup.ts` `if (!email) return;` | `const email = process.env.PORTAL_CONTACT_EMAIL;\n  if (!email) return;` | Opt-in single exchange | d | Keep, so login-bare spends only its own request. OD-9: have the package scripts supply `PORTAL_CONTACT_EMAIL` for 90-day / responsive. |
| G54 | `playwright.portal.config.ts` `webServer: process.env.SKIP_PORTAL_BASELINES === "true" ? undefined : …` | env | d | Keep. OD-9: pass `NEXT_PUBLIC_PORTAL_API_URL` in the dev branch. |
| G55 | `playwright.portal.config.ts` `...(LOGIN_BARE ? [{ name: "login-bare", … }] : [])` | opt-in project | d | Keep. |
| G56 | `frontend/e2e/tests/portal/portal-data-isolation.spec.ts:33` | `test.skip(!KGOSI_EMAIL, "KGOSI_EMAIL not set — run with portal contact emails");` | Env opt-in; it is in the default config, so E2E-DEFAULT reports it as 2 skipped (`628F/gate-final/default.txt:115-116`) | d | Keep. E2E-PORTAL runs it with `KGOSI_EMAIL=contact@acme.example.com`. It is outside the 628G file list; no change needed. |

(G50 is intentionally unused; the responsive `waitForTimeout` is folded into G49.)

### Census summary

- **Total 55 guards.**
  - **(a) 28**: G1, G3–G8, G10–G12, G15–G17, G20–G23, G26, G27, G29, G30, G34, G35, G39, G42–G44, G49. G5/G6/G7 become (b) under OD-1(B).
  - **(b) 1**: G18 (the Day 21 deposit).
  - **(c) 12**: G9, G13, G24, G25, G28, G31, G32, G36, G37, G38, G40, G41. Of these:
    - 3 are dead branches to delete (G9, G28, G41);
    - 1 is Payments-environment (G24/G25 pair);
    - 1 is deadlines product gap (G13/G31/G32);
    - 1 is profile-edit gap (G36–G38);
    - 1 is cadence by design (G40).
  - **(d) 14**: G2, G14, G19, G33, G45–G48, G51–G56.
- **Plus 31 `waitForTimeout` calls** (30 in the day files + 1 in responsive), all deleted.
- **Tests:** 12 in the 90-day project (day-00-07 ×4, day-14-30 ×1, day-45-75 ×4, day-85-90 ×3), 21 responsive, 1 login-bare (opt-in), 2 isolation.

## Open Decisions (the orchestrator rules; recommendation marked ★)

**OD-1: Day 0 / Day 3 request source.**
- **(A)** Consume the seeded REQ-0001. Day 0 views it; Day 3 uploads and submits it.
  - It is what the task row literally says ("the Day 3 request … now exist[s]").
  - Consequences:
    1. The suite is **fresh-stack-only**. After submit, REQ-0001 is IN_PROGRESS (`PortalInformationRequestService.java:310`). The seed treats IN_PROGRESS as open and skips it (`compose/seed/lib/information-requests.sh` "SENT / IN_PROGRESS → skip"), so even `e2e-reseed.sh` does not restore a pending request.
    2. It **breaks E2E-DEFAULT if E2E-DEFAULT runs after it on the same stack.** `frontend/e2e/tests/information-requests/information-request-crud.spec.ts:70-79` ("View information requests list") and `:137-153` ("Sent request detail …") require a seeded row with status "Sent".
  - Therefore 628I must run E2E-PORTAL **last**.
- **(B) ★** In `day-00-07` `beforeAll`, create and send a **run-unique** request to the Acme contact on Website Redesign through the firm API, and drive Day 0 and Day 3 against it.
  - Honours R-5 (seed rows are read-only for the portal suite too, per `seed.ts:4-7`) and keeps REQ-0001 SENT for E2E-DEFAULT.
  - Re-runnable on a populated stack.
  - Cost: +1 magic-link token per run. `InformationRequestEmailEventListener.java:73-86` mints via `generateTokenOnly`; if the per-contact cap is already full, the mint throws, is caught, and the email goes out without a deep link. **The send itself never fails.**
  - Day 0's expected pending count = the pending count from `GET /portal/requests` (session JWT) taken in `beforeAll` after the send. Never hard-code 1.
- Both options require the Day 3 steps (upload → submit → "Submitted — status: SUBMITTED" → Home pending −1).

**OD-2: Day 21 trust deposit.**
- **(a) ★** In-spec firm-API deposit in `day-14-30` `beforeAll` (G18 row). It is RECORDED and portal-synced synchronously, with no approval step.
  - Optional: verify the Mailpit nudge "E2E Test Organization: Trust account activity" and follow its `/trust/{matterId}?orgId=…` path (scenario 21.1–21.2; no token minted).
  - Assert on the run-unique description row, never on a global balance (balances accumulate across runs).
  - Coupling: on a shared stack, run E2E-PORTAL after E2E-LEGAL (lifecycle Day 60 interest, 628F OD-10 risk).
- **(b)** Assert the zero-balance `/trust` state ("No trust balance is recorded for this matter." / "No transactions yet"). This is a scenario call, because scenario 21.3/21.4 expects a deposit.

**OD-3: Day 45 "pays invoice" on a stack with no PSP** (Payments is a CLAUDE.md §6 mandate exemption).
- **(a) ★** Keep "Day 45: Client opens invoice detail view", made strict (G22). Retitle the pay test to what it can verify, e.g. "Day 45: SENT fee note offers no online payment without a PSP".
  - Assert `getByText("Contact E2E Test Organization to arrange payment")` (`invoices/[id]/page.tsx:194-200`; `orgName` from branding).
  - Assert `getByRole("link", { name: "Pay Now" })` `toHaveCount(0)`.
  - Assert `getByTestId("invoice-payment-inline-banner")` `toHaveCount(0)`.
  - The PR body records scenario 45.4–45.8 and E.9 as "Payments exemption: e2e has no PSP — `MockPaymentGateway.java:34` @Profile excludes e2e".
- **(b)** `test.fixme(true, "PAYMENTS-EXEMPT: e2e profile has no PSP — integration/payment/MockPaymentGateway.java:34, MockPaymentController.java:40")`, keeping the intended Pay Now → mock checkout → PAID steps below it.
- **(c)** Add `e2e` to the two `@Profile`s, plus `docteams.payment.mock.checkout-base-url`. This is a backend product change, outside 628G and outside "no product code". Not recommended.

**OD-4: Deadlines (Day 7, Day 75).**
- The facts: there are no portal deadlines on e2e and none can be created on demand (G13 note). There is no mark-read control (G32), the detail panel shows no documents, and the digest CTA goes to `/home` (`portal-weekly-digest.html:72`).
- **(a) ★**
  - **Day 7** → retitle "Day 7: Diary dates page renders for the client (no portal-visible deadlines on the e2e tenant)". Assert h1 "Diary dates" and `getByTestId("deadline-list-empty")` "No deadlines in this view". Log the populated-list gap as a 628G finding.
  - **Day 75** → `test.fixme(true, "628G-BLOCKER: no portal deadline can be produced on demand (COURT_DATE/PRESCRIPTION/FILING portal sync dormant — customerbackend/service/DeadlinePortalSyncService.java:36-44,123-130) and the portal has no mark-read control or deadline documents — components/deadlines/deadline-detail-panel.tsx:83-170")`. Keep the intended steps below it. Reproduce first (R-4): create a court date on Website Redesign via `POST /api/court-dates` (MANAGE_LEGAL, `verticals/legal/courtcalendar/CourtCalendarController.java:31,64-65`) and show the portal `/deadlines` stays empty, with a screenshot and log under `qa_cycle/evidence/phase88-rewalk/628G/builder/blockers/`.
- **(b)** Both tests become `628G-BLOCKER` fixmes.
- **(c)** Day 7 drives the real digest: `POST /internal/portal/digest/run-weekly?orgId=e2e-test-org&targetEmail=contact@acme.example.com` with `X-API-KEY: e2e-test-api-key` (`portal/notification/PortalDigestInternalController.java`), read Mailpit, follow the CTA.
  - The CTA goes to `/home`, not `/deadlines`.
  - BIWEEKLY plus `digestLastSentAt` means a second run within 12 days is **skipped** (`PortalDigestScheduler.java:58-59,212-221`), so it is not re-runnable on one stack.
  - Not recommended for 628G.

**OD-5: Day 85 "Client updates profile".** The product is read-only (G36).
- **(a) ★** Retitle "Day 85: Profile shows the client's contact details" and assert the read-only fields strictly (R-3: "retitle where the check is legitimate but over-claims"). Log "profile edit absent (FE display-only `profile/page.tsx:125-153`; BE GET-only `PortalProfileController.java:16,26`)" as a 628G finding for 628H/629: scenario 85.1–85.2 versus the product.
- **(b)** `628G-BLOCKER` fixme with the intended edit steps.

**OD-6: Day 85 cadence title.**
- **★** Retitle now to "Day 85: Notifications page shows the firm's bi-weekly digest cadence (read-only)", citing register §5 (`tasks/phase88-gap-register.md:169`). The scenario text stays 628H's job.
- Alternative: keep the title and only fix the assertions. That is dishonest per R-12 ("every test title describes what it does").

**OD-7: Visual snapshots** (`toHaveScreenshot` on every day test and 21 responsive tests; no committed baselines; `portal/e2e/screenshots/**` is tracked only for `.gitkeep` + README).
- **(a) ★** Keep the calls (they are the scenario's 📸). Put the strict content assertions **before** each snapshot so a test is non-vacuous even though the visual diff isn't a gate. Run with `--update-snapshots=all` (617C/624I precedent) and delete the PNGs afterwards.
- **(b)** Commit baselines. They are platform-implicit (README "GAP-011") and would churn.
- **(c)** Replace with `testInfo.attach` screenshots.

**OD-8: ESLint `no-isvisible-catch` for the portal.**
- Context:
  - Portal `pnpm lint` (`eslint`, no args) already lints `portal/e2e/**`: the flat config has `files: ["**/*.{ts,tsx,js,jsx,mjs,cjs}"]` and globalIgnores only `.next/out/build`.
  - The portal has no local plugin today (`portal/eslint.config.mjs`).
  - The frontend rule lives at `frontend/e2e/eslint/no-isvisible-catch.mjs`, registered through `frontend/eslint-rules/index.mjs` as plugin `kazi-frontend-local` and scoped by `files: ["e2e/**/*.ts"]` (`frontend/eslint.config.mjs:27-38`).
- **(a) ★**
  1. Byte-identical copy at `portal/e2e/eslint/no-isvisible-catch.mjs`.
  2. NEW `portal/eslint-rules/index.mjs`, plugin `kazi-portal-local`.
  3. `portal/eslint.config.mjs` block `{ files: ["e2e/**/*.ts"], plugins: {…}, rules: { "kazi-portal-local/no-isvisible-catch": "error" } }`.
  4. NEW `portal/__tests__/eslint-rules/no-isvisible-catch.test.ts`: wiring probes through the real config (pattern `portal/__tests__/eslint-rules/no-terminology-imports.test.ts`), plus a drift check that `fs.readFileSync` of both copies is equal (628B R-1 "no import" precedent).
  5. Add `frontend/e2e/eslint/no-isvisible-catch.mjs` to the CI portal path-filter (`.github/workflows/ci.yml:46-49`; 628B did the same for the scanner files).
- **(b)** Relative import from `../frontend/...`: cross-app; 628B R-1 rejected this.
- **(c)** No rule; the reviewer enforces it. That leaves the 628E/628F class of regression open in the portal.
- (a) touches files outside the row's file list, so it needs explicit authorisation.

**OD-9: Plumbing defaults.**
- **★** Spec default email → the seed contact (G1, G49).
- **★** The package scripts set `PORTAL_CONTACT_EMAIL=${PORTAL_CONTACT_EMAIL:-contact@acme.example.com}` inline for 90-day and responsive. This works because pnpm runs scripts under `sh`.
- **★** `playwright.portal.config.ts`: the dev webServer branch also passes `env: { NODE_OPTIONS: "", NEXT_PUBLIC_PORTAL_API_URL: process.env.NEXT_PUBLIC_PORTAL_API_URL || process.env.BACKEND_URL || "http://localhost:8081" }`, mirroring the LOGIN_BARE branch. Today, unless `NEXT_PUBLIC_PORTAL_API_URL` is exported, the dev portal calls `lib/api-client.ts:4-5` default **:8080, the KC backend**; the e2e JWT then gets a 401 → `/login`.
- **★** `globalSetup` keeps its env opt-in, so login-bare is unaffected.

**OD-10: `portal/package.json` per-suite scripts** (the 628.16 deliverable; the P-1 analogue).
- ★ Keep `test:e2e:login-bare` and `test:e2e:portal-client-90day`.
- ★ Add `test:e2e:responsive` (`--project=chromium`).
- ★ Add `test:e2e:portal` (`--project=chromium --project=portal-client-90day`): one invocation, so **one** `globalSetup` exchange covers both.
- Exact text is in the File Plan.
- `--project` selection is immune to the P-1 path-regex trap. Never pass a bare positional filter such as `portal` (the main checkout path `/Users/rakheendama/Projects/…` contains `Projects`, and positional filters are absolute-path regexes).
- The frontend isolation spec runs with the path-anchored form; adding a frontend script is outside the row.

**OD-11: login-bare comment fix** (624I follow-up). ★ Take it: comments only, at `login-bare.spec.ts:13-15` and `:96-97` (both caps), plus the 429 message.

**OD-12: Meaning of "0 soft-skipped".**
- ★ Zero runtime or conditional skips: no `test.skip(cond)` on data, no `if (!x) return`, no swallowed `isVisible`.
- Declared `test.fixme(true, "628G-BLOCKER: …")` (reproduced product gaps) and env opt-ins (G2/G14/G19/G33/G45/G48/G56) are allowed and are listed in the PR body. Playwright still prints fixmes as "skipped".
- 628F accepted the same reading for E2E-DEFAULT (18 skipped = BLOCKER fixmes + env). Please confirm.

**OD-13: Carried set re-observation.** ★ Cite the 628F evidence. On the 628G fresh stack, **before** any portal run, also run `pnpm test:e2e:settings`, `pnpm test:e2e:projects` and `pnpm test:e2e:customers` (≈2 min). This re-observes PACK-01, PROJ-02/03 and CUST-01/02 for the PR body, and under OD-1(A) protects REQ-0001 ordering.

**OD-14: Day 90 activity assertions.** ★ `/activity` "Your actions": this run's "You submitted an information request item" and "You downloaded a document". Optionally compare MINE counts before and after through the API. The missing pay / mark-read / profile events follow from OD-3/4/5 and go in the PR body.

**OD-15: Follow-ups to log** (628G does not fix them):
1. The seed's request idempotency treats IN_PROGRESS as open, so a consumed REQ never comes back. This contradicts its own comment "a portal Day 3 run consumed it → create + send", because portal submit leaves IN_PROGRESS, not COMPLETED.
2. Portal deadlines: the COURT_DATE/PRESCRIPTION sync is dormant.
3. The portal profile is read-only, against scenario 85.1–85.2.
4. The e2e profile has no PSP.
5. The portal digest CTA goes to `/home`, while scenario 7.2 expects `/deadlines`.
6. Scenario 60.2 says "Documents tab", but the product renders a section.
7. The exchange double-POST under `next dev` (624D, still open).

## File Plan

**Modify (core, in the row):**
- `portal/e2e/tests/portal-client-90day/day-00-07.spec.ts`: G1–G13; OD-1, OD-4 (Day 7).
- `portal/e2e/tests/portal-client-90day/day-14-30.spec.ts`: G14–G18; OD-2.
- `portal/e2e/tests/portal-client-90day/day-45-75.spec.ts`: G19–G32; OD-3, OD-4 (Day 75).
- `portal/e2e/tests/portal-client-90day/day-85-90.spec.ts`: G33–G44; OD-5, OD-6, OD-14.
- `portal/package.json`: OD-10 scripts.

**Create (needed by OD-1(B)/OD-2 — a portal-local helper; do not import across apps):**
- `portal/e2e/helpers/firm-api.ts` (sketch below).

**Modify (adjacent; needs the OD ruling):**
- `portal/playwright.portal.config.ts`: OD-9 dev-webServer env; update its header comment, which says the per-email cap is "3 / 15 min" (also mention the per-contact 3 / 5 min).
- `portal/e2e/tests/responsive/portal-pages.spec.ts`: G49 default email; replace `waitForTimeout` with an h1 assertion.
- `portal/e2e/tests/login-bare.spec.ts`: OD-11 comments only.
- `portal/e2e/helpers/global-setup.ts` / `auth.ts`: comment-only touch-ups if the cap text changes.
- `portal/e2e/screenshots/portal-v2/portal-client-90day/README.md`: stale (three KC tenants, Phase 68). Optionally rewrite to the e2e procedure.

**Create/modify for OD-8(a) only:**
- `portal/e2e/eslint/no-isvisible-catch.mjs`
- `portal/eslint-rules/index.mjs`
- `portal/eslint.config.mjs`
- `portal/__tests__/eslint-rules/no-isvisible-catch.test.ts`
- `.github/workflows/ci.yml` (portal path filter)

**Evidence:**
- `qa_cycle/evidence/phase88-rewalk/628G/{baseline-main,builder,builder/blockers,gate-final}/`: small txt/png only. Do not commit traces or videos. `*.log` is gitignored, so name logs `*.txt`.

**Do NOT touch:**
- `compose/seed/**`, `scripts/regression-test.sh`
- `frontend/**` (the isolation spec stays as-is)
- `portal/{app,components,lib,hooks}`
- backend
- `qa/testplan/demos/*.md`

### `portal/package.json` scripts (OD-10 + OD-9 proposal)

```jsonc
"test:e2e:login-bare": "LOGIN_BARE=1 playwright test --config=playwright.portal.config.ts --project=login-bare",
"test:e2e:portal-client-90day": "PORTAL_CONTACT_EMAIL=${PORTAL_CONTACT_EMAIL:-contact@acme.example.com} playwright test --config=playwright.portal.config.ts --project=portal-client-90day",
"test:e2e:responsive": "PORTAL_CONTACT_EMAIL=${PORTAL_CONTACT_EMAIL:-contact@acme.example.com} playwright test --config=playwright.portal.config.ts --project=chromium",
"test:e2e:portal": "PORTAL_CONTACT_EMAIL=${PORTAL_CONTACT_EMAIL:-contact@acme.example.com} playwright test --config=playwright.portal.config.ts --project=chromium --project=portal-client-90day",
```

Keep `login-bare` WITHOUT `PORTAL_CONTACT_EMAIL`. Otherwise `globalSetup` spends a second request on the same address (config comment; `login-bare.spec.ts:13-16`).

### `portal/playwright.portal.config.ts` dev webServer (OD-9)

```ts
        : {
            command: "NODE_OPTIONS= pnpm dev",
            url: "http://localhost:3002",
            reuseExistingServer: !process.env.CI,
            timeout: 120_000,
            env: {
              NODE_OPTIONS: "",
              // lib/api-client.ts defaults to :8080 (KC backend) — the e2e JWT would 401 there.
              NEXT_PUBLIC_PORTAL_API_URL:
                process.env.NEXT_PUBLIC_PORTAL_API_URL ||
                process.env.BACKEND_URL ||
                "http://localhost:8081",
            },
          },
```

Note on `reuseExistingServer`: a KC `svc.sh` portal already on :3002 (`pnpm dev`, pointing at :8080) would be silently **reused**, and every page would 401 → `/login`. Always free :3002 first (How to run).

### `portal/e2e/helpers/firm-api.ts` (NEW, sketch — verify shapes on the stack)

```ts
import { expect } from "@playwright/test";

/**
 * Phase 88 628G — firm-side setup for the portal suites (in-spec rows the boot seed does not carry).
 * Portal-local on purpose: the portal does not import from frontend/e2e (628B R-1). Mirrors
 * frontend/e2e/helpers/{api,seed}.ts. Seed rows are read-only (628F R-5): helpers here CREATE
 * run-unique rows; they never edit or consume the seed's.
 */
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";
const ORG_SLUG = process.env.PORTAL_ORG_SLUG || "e2e-test-org";

/** The boot seed (compose/seed/seed.sh Steps 5–12; names = frontend/e2e/helpers/seed.ts). */
export const SEED = {
  org: "e2e-test-org",
  orgName: "E2E Test Organization",
  client: "Acme Corp",
  contactEmail: "contact@acme.example.com",
  matter: "Website Redesign",
  trustAccount: "Mathebula & Partners Trust Account",
  feeNoteSentNumber: "INV-0001",
  infoRequestItem: "Certified ID copy",
  statementFile: /^statement-of-account-website-redesign-\d{4}-\d{2}-\d{2}\.pdf$/,
} as const;

/** A per-run suffix for names/references (portal runs are re-runnable on a populated stack). */
export const RUN_ID = Date.now().toString(36).slice(-5);

let alice: Promise<string> | undefined;
/** Alice (owner) bearer token from the mock IDP — only userId + orgSlug are read (compose/mock-idp/src/index.ts:33-52). */
function aliceToken(): Promise<string> {
  alice ??= (async () => {
    const res = await fetch(`${MOCK_IDP_URL}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId: "user_e2e_alice", orgSlug: ORG_SLUG }),
    });
    if (!res.ok) throw new Error(`mock IDP /token → ${res.status}`);
    return (await res.json()).access_token as string;
  })();
  return alice;
}

/** A firm API call that must answer 2xx; returns the parsed body (null when empty). */
export async function firmApiOk<T>(
  path: string,
  init: { method?: string; body?: unknown } = {},
): Promise<T> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: init.method ?? "GET",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${await aliceToken()}`,
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
  const text = await res.text();
  expect(res.ok, `${init.method ?? "GET"} ${path} → ${res.status} ${text}`).toBe(true);
  return (text ? JSON.parse(text) : null) as T;
}

async function idWhere<T extends { id: string }>(
  path: string,
  pick: (r: T) => boolean,
  what: string,
): Promise<string> {
  const body = await firmApiOk<T[] | { content: T[] }>(path);
  const hit = (Array.isArray(body) ? body : body.content).find(pick);
  expect(hit, `seed row ${what} via GET ${path} — is the stack seeded?`).toBeDefined();
  return hit!.id;
}

export const acmeId = () =>
  idWhere<{ id: string; name: string }>("/api/clients?size=200", (c) => c.name === SEED.client, SEED.client);
export const websiteRedesignId = () =>
  idWhere<{ id: string; name: string }>("/api/matters", (m) => m.name === SEED.matter, SEED.matter);
export const trustAccountId = () =>
  idWhere<{ id: string; accountName: string }>(
    "/api/trust-accounts", (a) => a.accountName === SEED.trustAccount, SEED.trustAccount);
export const acmeContactId = async () =>
  idWhere<{ id: string; email: string }>(
    `/api/clients/${await acmeId()}/portal-contacts`, (c) => c.email === SEED.contactEmail, SEED.contactEmail);

/** UTC today, YYYY-MM-DD (containers run UTC). */
export const todayUtc = () => new Date().toISOString().slice(0, 10);

/**
 * OD-2: a RECORDED trust deposit for Acme on Website Redesign. No approval step; the portal
 * read model syncs AFTER_COMMIT (TrustLedgerPortalSyncService.onTrustTransactionRecorded) and the
 * contact gets a "Trust account activity" email (no magic-link token).
 */
export async function recordAcmeDeposit(amount: number, description: string, reference: string) {
  return firmApiOk<{ id: string }>(
    `/api/trust-accounts/${await trustAccountId()}/transactions/deposit`,
    {
      method: "POST",
      body: {
        clientId: await acmeId(),
        matterId: await websiteRedesignId(),
        amount,
        reference,
        description,
        transactionDate: todayUtc(),
      },
    },
  );
}

/**
 * OD-1(B): a run-unique SENT information request to the Acme contact on Website Redesign with one
 * FILE_UPLOAD item. Sending mints one magic-link token (cap 3 / contact / 5 min; a capped mint is
 * caught server-side — the request is still SENT).
 */
export async function sendAcmeInfoRequest(itemName: string) {
  const created = await firmApiOk<{ id: string }>("/api/information-requests", {
    method: "POST",
    body: {
      clientId: await acmeId(),
      matterId: await websiteRedesignId(),
      portalContactId: await acmeContactId(),
      items: [
        {
          name: itemName,
          description: "Upload a certified copy of your ID document",
          responseType: "FILE_UPLOAD",
          required: true,
          fileTypeHints: "PDF, JPG, PNG",
          sortOrder: 0,
        },
      ],
    },
  });
  return firmApiOk<{ id: string; requestNumber: string; status: string }>(
    `/api/information-requests/${created.id}/send`,
    { method: "POST" },
  );
}

/** A portal API GET as the signed-in contact (token from helpers/auth.ts getPortalJwt). */
export async function portalApiGet<T>(path: string, portalJwt: string): Promise<T> {
  const res = await fetch(`${BACKEND_URL}${path}`, { headers: { Authorization: `Bearer ${portalJwt}` } });
  expect(res.ok, `GET ${path} → ${res.status}`).toBe(true);
  return (await res.json()) as T;
}
```

The request body mirrors `compose/seed/lib/information-requests.sh` (628F, proven on the stack). `POST /send` with an empty body is what the seed does. If the backend needs `dueDate`, the seed shows it does not.

Read-model fallback: if the new request does not appear in `GET /portal/requests` within `expect.poll` (15 s), call `POST /internal/portal/resync/e2e-test-org` with `X-API-KEY: e2e-test-api-key` (`customerbackend/controller/PortalResyncController.java:13`; `application-e2e.yml` `internal.api.key`), which is what the seed's Step 13 does. Prefer `expect.poll` alone if it suffices; record which one was needed.

### Spec sketches (★ options; the builder verifies every locator on the live stack before trusting it)

Every day file shares this header and auth:

```ts
import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";
import { SEED, RUN_ID /* + builders */ } from "../../helpers/firm-api";

const PORTAL_EMAIL = process.env.PORTAL_CONTACT_EMAIL || SEED.contactEmail;
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";

test.describe.serial("Day … — Portal Client Lifecycle (…)", () => {
  test.beforeAll(() => {
    test.skip(SKIP, "SKIP_PORTAL_BASELINES=true — portal e2e disabled (no seeded stack).");
  });

  test.beforeEach(async ({ page }) => {
    const jwt = await getPortalJwt(PORTAL_EMAIL);
    expect(
      jwt,
      `portal JWT for ${PORTAL_EMAIL}: stack up, contact seeded, and neither magic-link cap hit ` +
        `(3 tokens/contact/5 min, DB; 3 request-link calls/email/15 min, in-memory)`,
    ).toBeTruthy();
    await loginAsPortalContact(page, jwt!);
  });
  // …
});
```

`loginAsPortalContact` lands on `/login` first, then writes localStorage. Every test therefore does `page.goto(<route>)` and then **a web-first h1 assertion**, with no `waitForLoadState` + `waitForTimeout`.

**day-00-07 (OD-1(B), OD-4(a)):**

```ts
  const ITEM = `${SEED.infoRequestItem} ${RUN_ID}`;
  let requestId: string;
  let requestNumber: string;
  let pendingBefore: number;

  test.beforeAll(async () => {
    if (SKIP) return;
    ({ id: requestId, requestNumber } = await sendAcmeInfoRequest(ITEM));
    const jwt = await getPortalJwt(PORTAL_EMAIL);
    expect(jwt).toBeTruthy();
    // The read model is fed by the send's events: poll until this request is listed.
    await expect
      .poll(async () =>
        (await portalApiGet<{ id: string }[]>("/portal/requests", jwt!)).some((r) => r.id === requestId),
      )
      .toBe(true);
    const all = await portalApiGet<{ status: string; submittedItems: number; totalItems: number }[]>(
      "/portal/requests", jwt!);
    pendingBefore = all.filter(
      (r) => (r.status === "SENT" || r.status === "IN_PROGRESS") && r.submittedItems < r.totalItems,
    ).length; // same rule as home/page.tsx:64-69
  });

  test("Day 0: Client lands on /home and sees the pending info request", async ({ page }) => {
    await page.goto("/home");
    await expect(page.getByRole("heading", { level: 1, name: "Home" })).toBeVisible();
    const pending = page.getByRole("link", { name: /Pending info requests/ });
    await expect(pending).toContainText(String(pendingBefore));
    await expect(page.getByRole("link", { name: SEED.feeNoteSentNumber, exact: true })).toBeVisible(); // Recent fee notes
    await expect(page).toHaveScreenshot("day-00-home-landing.png", { fullPage: true, animations: "disabled" });
  });

  test("Day 0: Info-request detail shows the requested upload slot", async ({ page }) => {
    await page.goto("/home");
    await page.getByRole("link", { name: /Pending info requests/ }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Documents & requests" })).toBeVisible();
    await page.getByRole("link").filter({ hasText: requestNumber }).click();
    await expect(page).toHaveURL(new RegExp(`/requests/${requestId}$`));
    await expect(page.getByRole("heading", { level: 1, name: SEED.matter })).toBeVisible();
    await expect(page.getByText(/0\/1 submitted • status SENT/)).toBeVisible();
    await expect(page.getByText(ITEM, { exact: true })).toBeVisible();
    await expect(page.getByLabel(`Upload file for ${ITEM}`)).toBeVisible();
    await expect(page.getByRole("button", { name: "Upload and submit" })).toBeDisabled();
    await expect(page).toHaveScreenshot("day-00-info-request-detail.png", { fullPage: true, animations: "disabled" });
  });

  test("Day 3: Client uploads the document and submits the info request", async ({ page }) => {
    await page.goto(`/requests/${requestId}`);
    await page.getByLabel(`Upload file for ${ITEM}`).setInputFiles({
      name: "id-copy.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"),
    });
    await page.getByRole("button", { name: "Upload and submit" }).click();
    await expect(page.getByText("Submitted — status: SUBMITTED")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/1\/1 submitted • status IN_PROGRESS/)).toBeVisible();
    await expect(page).toHaveScreenshot("day-03-info-request-submitted.png", { fullPage: true, animations: "disabled" });
    // 3.5 — no longer pending on Home
    await page.goto("/home");
    await expect(page.getByRole("link", { name: /Pending info requests/ })).toContainText(String(pendingBefore - 1));
  });

  test("Day 7: Diary dates page renders for the client (no portal-visible deadlines on the e2e tenant)", async ({ page }) => {
    await page.goto("/deadlines");
    await expect(page.getByRole("heading", { level: 1, name: "Diary dates" })).toBeVisible();
    await expect(page.getByTestId("deadline-list-empty")).toContainText("No deadlines in this view");
    await expect(page).toHaveScreenshot("day-07-deadlines-list.png", { fullPage: true, animations: "disabled" });
  });
```

Caveats for the builder:
- "0/1 submitted • status SENT" depends on the exact characters (`•`, spaces, ` `?). Read the rendered text once and pin it exactly. Prefer `toContainText` on the `<p>` under the h1.
- `toContainText(String(n))` on the card: the card's text is "Pending info requests{n}". If n is 1 and the string contains other digits, the check is ambiguous. Prefer the `<p>` inside the card: `pending.locator("p")` `toHaveText(String(n))`.

**day-14-30 (OD-2(a)):**

```ts
  const DESC = `Portal Day 21 deposit ${RUN_ID}`;
  let wrId: string;
  test.beforeAll(async () => {
    if (SKIP) return;
    wrId = await websiteRedesignId();
    await recordAcmeDeposit(1500, DESC, `E2E-PORTAL-${RUN_ID}`);
  });

  test("Day 21 [legal-za]: Client views the trust balance and the first deposit", async ({ page }) => {
    await page.goto("/trust");
    await expect(page).toHaveURL(new RegExp(`/trust/${wrId}$`)); // single trust matter → auto-redirect (trust/page.tsx:63-68)
    await expect(page.getByLabel("Current balance")).toBeVisible();
    const row = page.getByRole("table", { name: "Trust transactions" }).getByRole("row").filter({ hasText: DESC });
    await expect(row).toContainText("DEPOSIT");
    await expect(row).toContainText(/1[\s  ]?500,00/); // en-ZA ZAR (packages/shared/src/format.ts:35-49)
    await expect(page).toHaveScreenshot("day-21-trust-balance.png", { fullPage: true, animations: "disabled" });
  });
```

- Optional 21.1/21.2: before `/trust`, wait for the Mailpit message to `SEED.contactEmail` with subject `"E2E Test Organization: Trust account activity"` that is not in a pre-deposit baseline (`helpers/mailpit.ts` `listMessageIds` + `waitForNewEmail`). Extract the `/trust/<id>?orgId=…` href and `page.goto(pathname+search)`.
- If Acme ever gets a second trust matter (it does not today), `/trust` shows the `MatterSelector` instead of redirecting. Then click the Website Redesign entry.

**day-45-75 (OD-3(a), OD-4(a)):**

```ts
  test("Day 45: Client opens the seeded fee note", async ({ page }) => {
    await page.goto("/invoices");
    await expect(page.getByRole("heading", { level: 1, name: "Fee notes" })).toBeVisible();
    await page.getByRole("link", { name: SEED.feeNoteSentNumber, exact: true }).click();
    await expect(page.getByRole("heading", { level: 1, name: SEED.feeNoteSentNumber })).toBeVisible();
    await expect(page.getByRole("heading", { level: 2, name: "Line Items" })).toBeVisible();
    await expect(page.getByRole("button", { name: `Download ${SEED.feeNoteSentNumber} as PDF` })).toBeVisible();
    await expect(page).toHaveScreenshot("day-45-invoice-detail.png", { fullPage: true, animations: "disabled" });
  });

  test("Day 45: SENT fee note offers no online payment without a PSP", async ({ page }) => {
    // Payments mandate exemption: the e2e profile has no PSP (MockPaymentGateway.java:34 @Profile excludes e2e)
    await page.goto("/invoices");
    await page.getByRole("link", { name: SEED.feeNoteSentNumber, exact: true }).click();
    await expect(page.getByText(`Contact ${SEED.orgName} to arrange payment`)).toBeVisible();
    await expect(page.getByRole("link", { name: "Pay Now" })).toHaveCount(0);
    await expect(page.getByTestId("invoice-payment-inline-banner")).toHaveCount(0);
    await expect(page).toHaveScreenshot("day-45-invoice-paid.png" /* rename → day-45-invoice-no-psp.png */, { fullPage: true, animations: "disabled" });
  });

  test("Day 60 [legal-za]: Client downloads the Statement of Account", async ({ page, request }) => {
    await page.goto("/matters");
    await expect(page.getByRole("heading", { level: 1, name: "Your matters" })).toBeVisible();
    await page.locator("main").getByRole("link").filter({ hasText: SEED.matter }).click(); // NOT .first() — Acme has other matters
    await expect(page.getByRole("heading", { level: 1, name: SEED.matter })).toBeVisible();
    await expect(page.getByRole("heading", { level: 2, name: "Documents" })).toBeVisible();
    const download = page.getByRole("button", {
      name: /^Download statement-of-account-website-redesign-\d{4}-\d{2}-\d{2}\.pdf$/,
    });
    await expect(download).toBeVisible();
    const presigned = page.waitForResponse((r) => r.url().includes("/presign-download") && r.ok());
    await download.click(); // window.open(presignedUrl)
    const { presignedUrl } = await (await presigned).json();
    const pdf = await request.get(presignedUrl);
    expect(pdf.ok()).toBe(true);
    expect((await pdf.body()).subarray(0, 5).toString("latin1")).toBe("%PDF-");
    await expect(page).toHaveScreenshot("day-60-document-downloaded.png", { fullPage: true, animations: "disabled" });
  });

  test("Day 75 [legal-za]: Client marks the deadline nudge read and downloads its document", async ({ page }) => {
    test.fixme(true, "628G-BLOCKER: …"); // OD-4 text; reproduce first (R-4)
    // Intended real steps (run once the product surfaces portal deadlines + mark-read):
    await page.goto("/deadlines");
    await expect(page.getByRole("heading", { level: 1, name: "Diary dates" })).toBeVisible();
    await page.getByRole("button", { name: /^View details for / }).first().click();
    const panel = page.getByRole("dialog");
    await panel.getByRole("button", { name: /mark (as )?read/i }).click();
    // …reload → read state persists; download the related document
  });
```

- The window.open popup: if Playwright reports an unhandled popup, add `const popup = page.waitForEvent("popup"); … (await popup).close();`.
- If the h1 matcher on the fee-note page fails because of the badge, use `exact: true` on the name. The h1 contains only the number; the badge is a sibling.

**day-85-90 (OD-5(a), OD-6, OD-14):**

```ts
  test("Day 85: Profile shows the client's contact details", async ({ page }) => {
    await page.goto("/profile");
    await expect(page.getByRole("heading", { level: 1, name: "Profile" })).toBeVisible();
    await expect(page.getByText(SEED.contactEmail, { exact: true })).toBeVisible();
    await expect(page.getByText(SEED.client, { exact: true })).toBeVisible();
    await expect(page.getByRole("textbox")).toHaveCount(0); // read-only today (finding OD-15 #3)
    await expect(page).toHaveScreenshot("day-85-profile-updated.png" /* rename → day-85-profile.png */, { fullPage: true, animations: "disabled" });
  });

  test("Day 85: Notifications page shows the firm's bi-weekly digest cadence (read-only)", async ({ page }) => {
    await page.goto("/settings/notifications");
    await expect(page.getByRole("heading", { level: 1, name: "Notification Preferences" })).toBeVisible();
    await expect(page.getByText("Your firm sends bi-weekly digests.", { exact: true })).toBeVisible();
    await expect(page.getByRole("switch", { name: /Weekly digest/ })).toBeVisible();
    await expect(page.locator("main").getByRole("combobox")).toHaveCount(0);
    await expect(page.locator("main").getByRole("radio")).toHaveCount(0);
    await expect(page).toHaveScreenshot("day-85-notifications-biweekly.png", { fullPage: true, animations: "disabled" });
  });

  test("Day 90: Activity trail shows this quarter's client actions", async ({ page }) => {
    await page.goto("/activity");
    await expect(page.getByRole("heading", { level: 1, name: "Activity" })).toBeVisible();
    await expect(page.getByRole("tab", { name: "Your actions" })).toHaveAttribute("aria-selected", "true");
    const list = page.getByTestId("activity-list");
    await expect(list.getByText("You submitted an information request item").first()).toBeVisible();
    await expect(list.getByText("You downloaded a document").first()).toBeVisible();
    await expect(page).toHaveScreenshot("day-90-activity-trail.png", { fullPage: true, animations: "disabled" });
  });
```

- The Profile `<dd>` values may render twice (one hidden copy, if a responsive layout exists). The page has none today (`profile/page.tsx:120-155`), but check.
- `getByRole("switch", { name: /Weekly digest/ })`: the input is inside a `<label htmlFor>` whose text is "Weekly digest" + description. The accessible name includes both, hence the regex.
- Day 90 depends on Day 3 and Day 60 from earlier files in the same run (`workers: 1`, files run alphabetically). If an earlier day fails, Day 90 fails too, and that is correct.
- **Snapshot renames** (day-45-invoice-paid → no-psp, day-85-profile-updated → profile): keep the file names stable unless the orchestrator wants renames. Baselines are uncommitted, so a rename costs nothing.

## Reference Patterns (full code)

### R-A. Strict spec with in-spec API setup + `628F-BLOCKER` fixmes — `frontend/e2e/tests/customers/customer-lifecycle.spec.ts` (628F, full file)

Shows: run-unique names (`RUN_ID`), API builders for setup, UI drives the behaviour under test, web-first assertions, `expect.poll` on API state, and the BLOCKER fixme convention (fixme as first statement, reason string with product file:line, intended real steps kept below).

```ts
/**
 * CUST-02: Customer Lifecycle — Playwright E2E Tests
 *
 * Lifecycle state machine: PROSPECT default, the firm-UI transitions that exist (header "Start
 * Onboarding", Compliance › dormancy check "Mark as Dormant"), checklist auto-activation and the
 * lifecycle guards. DORMANT → OFFBOARDING and OFFBOARDING → OFFBOARDED have no working firm UI
 * (628F-BLOCKER fixmes below).
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
 *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
 *
 * Run:
 *   cd frontend/e2e
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test customers/customer-lifecycle --reporter=list
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import {
  api,
  clientStatus,
  createActiveClient,
  createClient,
  settleChecklists,
} from "../../helpers/api";
import { startClientOnboarding } from "../../helpers/legal-depth-ii";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

const RUN_ID = Date.now().toString(36).slice(-4);

function transitionViaApi(clientId: string, targetStatus: string): Promise<Response> {
  // Phase 87 S2 (ADR-337): API path
  return api(`/api/clients/${clientId}/transition`, {
    method: "POST",
    body: { targetStatus, notes: "E2E test transition" },
  });
}

// ═══════════════════════════════════════════════════════════════════
//  CUST-02: Customer Lifecycle State Machine
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("CUST-02: Customer Lifecycle", () => {
  const LIFECYCLE_NAME = `LC Cust ${RUN_ID}`;
  const LIFECYCLE_EMAIL = `lc-${RUN_ID}@example.com`;
  let customerId: string;

  test("New customer defaults to PROSPECT", async ({ page }) => {
    const customer = await createClient(LIFECYCLE_NAME, LIFECYCLE_EMAIL);
    customerId = customer.id;
    expect(customer.lifecycleStatus).toBe("PROSPECT");

    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${customerId}`);
    await expect(
      page.getByRole("heading", { level: 1, name: LIFECYCLE_NAME, exact: true })
    ).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Prospect");
  });

  test("PROSPECT -> ONBOARDING", async ({ page }) => {
    await loginAs(page, "bob");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${customerId}`);
    await expect(
      page.getByRole("heading", { level: 1, name: LIFECYCLE_NAME, exact: true })
    ).toBeVisible({
      timeout: 10_000,
    });

    // The header smart action of a PROSPECT is "Start Onboarding" (confirm dialog → ONBOARDING)
    await expect(page.getByTestId("smart-primary-action")).toHaveText("Start Onboarding");
    await startClientOnboarding(page);

    await page.reload();
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Onboarding", { timeout: 10_000 });
    expect(await clientStatus(customerId)).toBe("ONBOARDING");
  });

  test("ONBOARDING -> ACTIVE (via checklist completion)", async () => {
    expect(await clientStatus(customerId)).toBe("ONBOARDING");
    await settleChecklists(customerId);
    // Settling the last item auto-transitions the client (its billing address is on file)
    await expect.poll(() => clientStatus(customerId)).toBe("ACTIVE");
  });

  test("PROSPECT blocked from creating project", async () => {
    const prospect = await createClient(
      `Prospect Guard ${RUN_ID}`,
      `prospect-guard-${RUN_ID}@example.com`
    );

    // Phase 87 S1 (ADR-337): API path
    const projectRes = await api("/api/matters", {
      method: "POST",
      body: {
        name: `Should Fail ${RUN_ID}`,
        description: "This should be blocked by lifecycle guard",
        // Phase 87 S1 (ADR-337): API field (CreateMatterRequest.clientId)
        clientId: prospect.id,
      },
    });
    // CustomerLifecycleGuard: CREATE_PROJECT is blocked for PROSPECT (InvalidStateException → 400)
    expect(projectRes.status).toBe(400);
    expect(await projectRes.text()).toContain("PROSPECT");
  });

  test("Fee note blocked for a client without a billing address", async () => {
    // A PROSPECT may be billed (GAP-L-60); what blocks the fee note is the missing billing address
    // (INVOICE_GENERATION structural prerequisites → 422 with an address_line1 violation)
    const prospect = await createClient(
      `ProspectInv ${RUN_ID}`,
      `prospect-inv-${RUN_ID}@example.com`,
      { withAddress: false }
    );

    const invoiceRes = await api("/api/invoices", {
      method: "POST",
      body: {
        // Phase 87 S3 (ADR-337): request body
        clientId: prospect.id,
        currency: "ZAR",
        timeEntryIds: [],
      },
    });
    expect(invoiceRes.status).toBe(422);
    expect(await invoiceRes.text()).toContain("address_line1");
  });

  test("ACTIVE -> DORMANT", async ({ page }) => {
    expect(await clientStatus(customerId)).toBe("ACTIVE");

    // The firm-UI ACTIVE → DORMANT path: Compliance › dormancy check › "Mark as Dormant". The client
    // has no matters, documents, fee notes or time, so it is always a dormancy candidate.
    await loginAs(page, "alice");
    await page.goto(`${BASE}/compliance`);
    await page.getByRole("button", { name: "Check for Dormant Clients" }).click();
    const row = page
      .getByRole("row")
      .filter({ has: page.getByRole("link", { name: LIFECYCLE_NAME, exact: true }) });
    await expect(row).toBeVisible({ timeout: 15_000 });
    const markDormant = row.getByRole("button", { name: "Mark as Dormant" });
    await markDormant.click();
    // The candidate list is client state and is not re-fetched after marking: assert on the client
    await expect.poll(() => clientStatus(customerId), { timeout: 15_000 }).toBe("DORMANT");

    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${customerId}`);
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Dormant", { timeout: 10_000 });
  });

  test("DORMANT -> OFFBOARDING", async ({ page }) => {
    test.fixme(
      true,
      "628F-BLOCKER: no firm-UI DORMANT→OFFBOARDING transition (header action 'Edit' has no handler, overflow menu has no lifecycle items; LifecycleTransitionDropdown orphaned since #1395) — components/customers/client-header-card-with-lifecycle.tsx:70, app/(app)/org/[slug]/clients/[id]/page.tsx:434-470, components/customers/client-overflow-menu.tsx:101-177"
    );
    // Intended real steps (run once the product exposes the transition):
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${customerId}`);
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Dormant", { timeout: 10_000 });
    await page.getByTestId("client-overflow-trigger").click();
    // Phase 88 628A (ADR-357): label → "Offboard Client"
    await page.getByRole("menuitem", { name: "Offboard Client" }).click();
    const confirm = page.getByRole("alertdialog");
    await confirm.getByRole("button", { name: "Begin Offboarding" }).click();
    await expect(confirm).toBeHidden({ timeout: 15_000 });
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Offboarding", {
      timeout: 15_000,
    });
  });

  test("OFFBOARDING -> OFFBOARDED", async ({ page }) => {
    test.fixme(
      true,
      "628F-BLOCKER: header 'Complete Offboarding' button has no onClick (no target status computed for OFFBOARDING) — components/customers/client-header-card-with-lifecycle.tsx:70, app/(app)/org/[slug]/clients/[id]/page.tsx:434-470"
    );
    // Intended real steps (run once the product wires the header action):
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${customerId}`);
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Offboarding", {
      timeout: 10_000,
    });
    await page.getByTestId("smart-primary-action").click();
    const confirm = page.getByRole("alertdialog", { name: "Complete Offboarding" });
    await confirm.getByRole("button", { name: "Complete Offboarding" }).click();
    await expect(confirm).toBeHidden({ timeout: 15_000 });
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Offboarded", {
      timeout: 15_000,
    });
  });

  test("OFFBOARDED blocked from project creation", async () => {
    // Self-contained (the UI path to OFFBOARDED is blocked above): ACTIVE → OFFBOARDING → OFFBOARDED
    const offboardedId = await createActiveClient(
      `Offboarded Guard ${RUN_ID}`,
      `offboarded-guard-${RUN_ID}@example.com`
    );
    expect((await transitionViaApi(offboardedId, "OFFBOARDING")).ok).toBe(true);
    expect((await transitionViaApi(offboardedId, "OFFBOARDED")).ok).toBe(true);
    expect(await clientStatus(offboardedId)).toBe("OFFBOARDED");

    // Phase 87 S1 (ADR-337): API path
    const projectRes = await api("/api/matters", {
      method: "POST",
      body: {
        name: `Should Fail Offboarded ${RUN_ID}`,
        description: "Blocked by lifecycle guard",
        // Phase 87 S1 (ADR-337): API field (CreateMatterRequest.clientId)
        clientId: offboardedId,
      },
    });
    expect(projectRes.status).toBe(400);
    expect(await projectRes.text()).toContain("OFFBOARDED");
  });

  test("Invalid: PROSPECT -> ACTIVE (skip) rejected via API", async () => {
    const prospect = await createClient(`SkipTest ${RUN_ID}`, `skip-${RUN_ID}@example.com`);

    // PROSPECT may only move to ONBOARDING (LifecycleStatus.ALLOWED_TRANSITIONS)
    const res = await transitionViaApi(prospect.id, "ACTIVE");
    expect(res.status).toBe(400);
    expect(await clientStatus(prospect.id)).toBe("PROSPECT");
  });
});
```

### R-B. Portal helpers (current, full source)

#### `portal/e2e/helpers/auth.ts`
```ts
import type { Page } from "@playwright/test";

/**
 * Epic 499B — Portal auth helpers used by Playwright visual-baseline specs.
 *
 * Ports the magic-link / exchange flow (originally in the firm-side e2e
 * portal specs, since removed) into the portal's own e2e tree so the portal baselines can run without
 * reaching into the firm-side e2e directory.
 *
 * Epic 617B: the session is written to the localStorage keys the portal
 * actually reads (`portal/lib/auth.ts`: `portal_jwt` + `portal_customer`,
 * plus the `portal_last_org_id` routing hint). The previous keys
 * (`portal-auth-token` cookie, `portal-token` / `portal-auth-token`
 * localStorage) had no reader, so every spec landed on /login.
 *
 * Magic links are capped at 3 per contact per 5 minutes
 * (MagicLinkService.MAX_TOKENS_PER_5_MINUTES) and request-link calls at 3 per
 * email per 15 minutes across firms (PortalLoginRateLimiter; in-memory,
 * cleared only by restarting the backend; the 4th call is a 429). The portal
 * JWT lives 1 h (PortalJwtService.SESSION_TTL). `exchangePortalSession`
 * sends `orgId: ORG_SLUG`, the request-link fast path (ADR-351). Playwright
 * restarts the worker after every failed test (which drops module state), so
 * `global-setup.ts` exchanges once per run for PORTAL_CONTACT_EMAIL and hands
 * the session to every worker via `process.env[PORTAL_SESSIONS_ENV]`. Other
 * emails fall back to one exchange per worker.
 */

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const ORG_SLUG = process.env.PORTAL_ORG_SLUG || "e2e-test-org";
const PORTAL_BASE_URL =
  process.env.PLAYWRIGHT_BASE_URL || "http://localhost:3002";
const PORTAL_ORIGIN = new URL(PORTAL_BASE_URL).origin;

/** Env var global-setup.ts uses to pass pre-exchanged sessions to workers. */
export const PORTAL_SESSIONS_ENV = "PORTAL_E2E_SESSIONS";

export interface PortalSession {
  token: string;
  clientId: string;
  clientName: string;
  email: string;
  orgId: string;
}

const sessions = new Map<string, Promise<PortalSession | null>>();
const byToken = new Map<string, PortalSession>();

function sessionFromGlobalSetup(email: string): PortalSession | null {
  const raw = process.env[PORTAL_SESSIONS_ENV];
  if (!raw) return null;
  try {
    const all = JSON.parse(raw) as Record<string, PortalSession>;
    return all[email] ?? null;
  } catch {
    return null;
  }
}

/**
 * Request a magic link for `email` and exchange it for a portal session.
 * Returns `null` when either endpoint fails, the response shape is unexpected,
 * or a network/DNS/timeout error is thrown (fetch rejects on network failures).
 */
export async function exchangePortalSession(
  email: string,
): Promise<PortalSession | null> {
  try {
    const linkRes = await fetch(`${BACKEND_URL}/portal/auth/request-link`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, orgId: ORG_SLUG }),
    });
    if (!linkRes.ok) return null;
    // MagicLinkResponse(message, magicLink) — magicLink is
    // "/auth/exchange?token=…&orgId=…" (dev/test/e2e profiles only).
    const { magicLink } = await linkRes.json();
    const token = magicLink?.split("token=").pop()?.split("&")[0];
    if (!token) return null;

    const exchangeRes = await fetch(`${BACKEND_URL}/portal/auth/exchange`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, orgId: ORG_SLUG }),
    });
    if (!exchangeRes.ok) return null;
    // PortalTokenResponse(token, clientId, clientName)
    const data = await exchangeRes.json();
    if (!data.token) return null;
    return {
      token: data.token,
      clientId: data.clientId,
      clientName: data.clientName,
      email,
      orgId: ORG_SLUG,
    };
  } catch {
    // Network failures (DNS, timeout, connection refused) should not escape —
    // the contract is Promise<PortalSession | null>; callers handle null
    // (getPortalJwt returns null, global-setup.ts throws).
    return null;
  }
}

/**
 * Returns a portal JWT for `email`: the global-setup session when there is
 * one, else a magic-link exchange done at most once per worker (the result —
 * including a failure — is cached).
 */
export async function getPortalJwt(email: string): Promise<string | null> {
  let pending = sessions.get(email);
  if (!pending) {
    const preExchanged = sessionFromGlobalSetup(email);
    pending = preExchanged
      ? Promise.resolve(preExchanged)
      : exchangePortalSession(email);
    sessions.set(email, pending);
  }
  const session = await pending;
  if (!session) return null;
  byToken.set(session.token, session);
  return session.token;
}

/**
 * Authenticate the Playwright browser as a portal contact by writing the
 * localStorage keys `portal/lib/auth.ts` reads (`portal_jwt`,
 * `portal_customer`, `portal_last_org_id`). Navigates to the portal origin
 * first so the writes land on the correct origin.
 */
export async function loginAsPortalContact(
  page: Page,
  jwt: string,
): Promise<void> {
  const session = byToken.get(jwt);
  if (!session) {
    throw new Error("loginAsPortalContact: jwt not issued by getPortalJwt");
  }
  await page.goto(PORTAL_ORIGIN + "/login");
  await page.evaluate((s) => {
    localStorage.setItem("portal_jwt", s.token);
    localStorage.setItem(
      "portal_customer",
      JSON.stringify({
        id: s.clientId,
        name: s.clientName,
        email: s.email,
        orgId: s.orgId,
      }),
    );
    localStorage.setItem("portal_last_org_id", s.orgId);
  }, session);
}
```

#### `portal/e2e/helpers/global-setup.ts`
```ts
import { PORTAL_SESSIONS_ENV, exchangePortalSession } from "./auth";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const ORG_SLUG = process.env.PORTAL_ORG_SLUG || "e2e-test-org";

/**
 * Epic 617B — exchange one portal magic link per Playwright run.
 *
 * The backend caps magic links twice: 3 per contact per 5 minutes
 * (MagicLinkService; a capped request still answers 200 but sends nothing)
 * and 3 request-link calls per email per 15 minutes across firms
 * (PortalLoginRateLimiter; in-memory, cleared only by restarting the backend;
 * a 4th call is a 429 before any lookup). Playwright restarts the worker after
 * each failed test, so a per-worker exchange runs out of links on any run
 * with 3+ failures. Exchanging here (once, in the
 * runner process) and passing the session through the environment lets every
 * worker reuse it. Env vars set in globalSetup are inherited by the workers.
 *
 * When PORTAL_CONTACT_EMAIL is set, a failed exchange is fatal: the run fails
 * here with a clear error instead of every spec skipping with "Could not
 * obtain portal JWT", which would read like a data skip.
 *
 * The exchange sends `orgId` (PORTAL_ORG_SLUG): the request-link fast path
 * (ADR-351). The email-only path is covered by e2e/tests/login-bare.spec.ts.
 *
 * When PORTAL_CONTACT_EMAIL is unset this is a no-op; specs that fall back to
 * their default contact exchange once per worker and their own `getPortalJwt`
 * guards report a failure.
 */
export default async function globalSetup(): Promise<void> {
  if (process.env.SKIP_PORTAL_BASELINES === "true") return;
  const email = process.env.PORTAL_CONTACT_EMAIL;
  if (!email) return;
  const session = await exchangePortalSession(email);
  if (!session) {
    throw new Error(
      `Portal globalSetup: magic-link exchange failed for ${email} ` +
        `(org ${ORG_SLUG}, backend ${BACKEND_URL}). Check the backend is up, ` +
        `the contact is seeded, and neither magic-link cap has been hit by ` +
        `a recent run: 3 per contact per 5 min, or 3 requests per email ` +
        `per 15 min (in-memory; restart the backend to clear it).`,
    );
  }
  process.env[PORTAL_SESSIONS_ENV] = JSON.stringify({ [email]: session });
}
```

#### `portal/e2e/helpers/mailpit.ts`
```ts
/**
 * Phase 88 624D — Mailpit helper for portal Playwright specs.
 *
 * A subset of `frontend/e2e/helpers/mailpit.ts`, ported so the portal suite
 * does not reach into the firm-side e2e tree. Defaults to the e2e stack's
 * Mailpit API (:8026), matching the portal helpers' `BACKEND_URL` default
 * (:8081). Override with MAILPIT_URL (the KC dev stack uses :8025).
 *
 * There is deliberately no `clearMailbox`: wiping the mailbox destroys other
 * runs' evidence. Callers take an id baseline with `listMessages` and then
 * wait for a message that is not in it.
 */
const MAILPIT_URL = process.env.MAILPIT_URL || "http://localhost:8026";

export interface MailpitMessage {
  ID: string;
  Subject: string;
  Created: string;
}

export interface MailpitMessageDetail {
  ID: string;
  Subject: string;
  Text: string;
  HTML: string;
  Created: string;
}

/** Every message addressed to `recipient` (Mailpit `to:` search). */
export async function listMessages(
  recipient: string,
): Promise<MailpitMessage[]> {
  const res = await fetch(
    `${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${recipient}`)}`,
  );
  if (!res.ok) {
    throw new Error(`Mailpit search failed: ${res.status}`);
  }
  const data: { messages?: MailpitMessage[] } = await res.json();
  return data.messages ?? [];
}

/** Ids of every message addressed to `recipient`. */
export async function listMessageIds(recipient: string): Promise<string[]> {
  return (await listMessages(recipient)).map((m) => m.ID);
}

async function getMessageDetail(id: string): Promise<MailpitMessageDetail> {
  const res = await fetch(`${MAILPIT_URL}/api/v1/message/${id}`);
  if (!res.ok) {
    throw new Error(`Mailpit get message failed: ${res.status}`);
  }
  return res.json();
}

/**
 * Polls until a message to `recipient` arrives whose id is not in `exclude`
 * (and whose subject contains `subject`, when given), then returns its detail.
 */
export async function waitForNewEmail(
  recipient: string,
  opts: {
    exclude: string[];
    subject?: string;
    timeout?: number;
    interval?: number;
  },
): Promise<MailpitMessageDetail> {
  const { exclude, subject, timeout = 30_000, interval = 1_000 } = opts;
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const match = (await listMessages(recipient)).find(
      (m) =>
        !exclude.includes(m.ID) && (!subject || m.Subject.includes(subject)),
    );
    if (match) {
      return getMessageDetail(match.ID);
    }
    await new Promise((r) => setTimeout(r, interval));
  }
  throw new Error(
    `Timed out waiting for a new email to ${recipient}` +
      `${subject ? ` ("${subject}")` : ""} (${timeout}ms)`,
  );
}

/**
 * Returns `pathname + search` of the `/auth/exchange` link in a portal
 * magic-link email, so it can be replayed against the Playwright baseURL.
 * The HTML href encodes `&` as `&amp;`; the Text part ends the link with `)`.
 */
export function extractExchangePath(mail: MailpitMessageDetail): string {
  const href =
    mail.HTML?.match(/href="([^"]*\/auth\/exchange\?[^"]+)"/)?.[1]?.replace(
      /&amp;/g,
      "&",
    ) ?? mail.Text?.match(/(https?:\/\/[^\s)]+\/auth\/exchange\?[^\s)]+)/)?.[1];
  if (!href) {
    throw new Error(`No /auth/exchange link in "${mail.Subject}"`);
  }
  const url = new URL(href);
  return url.pathname + url.search;
}
```

### R-C. `frontend/e2e/helpers/api.ts` (628F) — lines 1-72 verbatim (core); remaining exports listed

Portal cannot import this (cross-app; 628B R-1, portal `tsconfig.json` includes `**/*.ts` so `next build` would type-check the frontend file under the portal's `@/*` alias). The portal-local `firm-api.ts` sketch above mirrors it. Token source: `frontend/e2e/fixtures/auth.ts:33-46` `getApiToken` → `POST ${MOCK_IDP_URL}/token` with `{ userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: "e2e-test-org", orgRole: "org:owner" }` (the mock IDP reads only `userId` + `orgSlug`, `compose/mock-idp/src/index.ts:33-52`; token `exp` = 24 h).

```ts
/**
 * Backend API helpers for default-config spec setup (Phase 88 628F).
 *
 * Setup only: a spec drives the behaviour it is named after through the UI and uses these to build
 * its own run-unique entities (628F R-5 — the boot-seed rows in `seed.ts` are read-only). `apiOk`
 * and the builders assert the answer, so a refused setup step fails the test where it happened.
 * Tokens come from the mock IDP via `fixtures/auth.ts` `getApiToken` — the same token `loginAs`
 * puts in the browser.
 */
import { expect } from "@playwright/test";
import { getApiToken } from "../fixtures/auth";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";

export type SeedUser = NonNullable<Parameters<typeof getApiToken>[0]>;

export interface ApiInit {
  method?: string;
  /** JSON-serialised when present. */
  body?: unknown;
}

/** A raw backend call as `user` (JSON body). */
export async function api(
  path: string,
  init: ApiInit = {},
  user: SeedUser = "alice"
): Promise<Response> {
  return fetch(`${BACKEND_URL}${path}`, {
    method: init.method ?? "GET",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${await getApiToken(user)}`,
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
}

/** A backend call that must answer 2xx; returns the parsed JSON body (or null when empty). */
export async function apiOk<T = Record<string, unknown>>(
  path: string,
  init: ApiInit = {},
  user: SeedUser = "alice"
): Promise<T> {
  const res = await api(path, init, user);
  const text = await res.text();
  expect(res.ok, `${init.method ?? "GET"} ${path} → ${res.status} ${text}`).toBe(true);
  return (text ? JSON.parse(text) : null) as T;
}

/** A created / read backend entity: its id plus whatever fields the caller asserts on. */
export type ApiEntity = { id: string } & Record<string, unknown>;

/**
 * A backend call whose status the caller asserts (a specific 2xx, or a refusal); returns the status
 * and the parsed JSON body (null when empty or not JSON).
 */
export async function apiStatus<T = ApiEntity>(
  path: string,
  init: ApiInit = {},
  user: SeedUser = "alice"
): Promise<{ status: number; data: T }> {
  const res = await api(path, init, user);
  const text = await res.text();
  let data = null as T;
  try {
    data = (text ? JSON.parse(text) : null) as T;
  } catch {
    // A non-JSON error body: the caller asserts on the status
  }
  return { status: res.status, data };
}
```

Remaining exports (lines 74-292): `memberNamed`, `settleChecklists(clientId)`, `createClient(name,email,{withAddress})`, `clientStatus`, `createActiveClient(name,email)`, `createMatter(name,clientId?)`, `addMatterMember`, `createTask`, `todayUtc()`, `createMatterRate`, `logTime`, `createDraftFeeNote`, `addFeeNoteLineViaApi`, `approveAndSendFeeNote`. Seed lookups: `frontend/e2e/helpers/seed.ts` (`SEED` names, `acmeId()` = `GET /api/clients?size=200` by name, `websiteRedesignId()` = `GET /api/matters` by name; bodies may be a bare array or a Spring page `{content}`).

## Conventions (verbatim)

There is **no `portal/CLAUDE.md`**. Portal e2e follows the frontend e2e conventions below. The portal lints `e2e/**` with its own flat config, which has no isVisible rule until OD-8. Portal Prettier uses defaults (no `.prettierrc`), and `format:check` covers `e2e/`.

### `frontend/e2e/AUTOMATION-NOTES.md` › Assertion rules (lines 648-696, verbatim)

#### (quoted) Assertion rules (Phase 88 628E — lint-enforced)

`kazi-frontend-local/no-isvisible-catch` (`e2e/eslint/no-isvisible-catch.mjs`) fails `pnpm lint` on:

- `.catch(…)` (or a two-argument `.then(…, onRejected)`) on `isVisible`, `isHidden`, `isEnabled`,
  `isDisabled`, `isChecked`, `isEditable` — directly or after one-argument `.then(…)` / `.finally(…)` links
  (`x.isVisible().then(f).catch(r)`);
- `isVisible({ timeout })` / `isHidden({ timeout })` — Playwright ignores the option.

It applies to every file under `e2e/**` (`eslint.config.mjs`; Phase 88 628F). The one exception is the two
KC-only demo recording scripts (`e2e/tests/demo-full-90day.spec.ts`, `demo-recording.spec.ts`) — an explicit
`ignores` entry, since they cannot run on the mock stack (628F R-2; follow-up: convert under KC-STACK or
delete).

Why (R10 §0 M1): these checks reject only when they cannot answer. `isVisible` / `isHidden` do not wait at
all (no match → `false` / `true` immediately after a navigation) and reject only on a strict-mode violation —
the locator matched two or more elements. `isEnabled` / `isDisabled` / `isChecked` / `isEditable` wait for
the element to appear (up to their `timeout`) but not for the state, and also reject when it never appears
or is the wrong kind of element. `getByText(/vat/i).isVisible().catch(() => false)` therefore returns
`false` exactly when the text is on screen twice. In the legal lifecycle suite 272 such guards hid flows the
product never had: the tests "passed" having done nothing.

Write instead:

| Intent                                                | Write                                                                                                                                                                                               |
| ----------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| element must be there                                 | `await expect(page.getByRole("button", { name: "Add Line" })).toBeVisible()`                                                                                                                        |
| text may match several nodes                          | `await expect(page.getByText("VAT").first()).toBeVisible()` — or scope it: `section.getByRole("row", { name: /VAT/ })`                                                                              |
| element must be absent (RBAC)                         | `await expect(locator).toHaveCount(0)` / `.not.toBeVisible()` — never `expect(await x.isVisible()).toBe(false)`                                                                                     |
| column/table check                                    | `getByRole("columnheader", { name: "Revenue", exact: true }).first()`                                                                                                                               |
| dialog closes on success                              | `await expect(dialog).toBeHidden({ timeout: 15_000 })` — never "press Escape if still open"                                                                                                         |
| genuinely optional UI (state may differ between runs) | wait for either state first, then branch: `await expect(a.or(b).first()).toBeVisible(); if (await a.isVisible()) { … }`                                                                             |
| step depends on a feature that may be off             | it is not optional on the legal-za stack — assert it. `test.skip` only for an explicit opt-in (env flag) or a documented environment limitation; a reproduced product bug is a `test.fixme` (below) |

Never guard a flow step with `if (visible) { act }`: a dead locator then turns the step into a silent no-op.
A bare `if (await loc.isVisible())` is allowed only right after a deterministic wait for either state (the
lint cannot see that — the reviewer checks it). The one other acceptable bare state read is on
server-rendered state that a prior awaited action has already settled (e.g. `fixtures/keycloak-auth.ts`:
once `fill` on the Keycloak username field resolves, the server-rendered form is complete, so the password
field is either there or not); the code must carry a comment saying why the state cannot change after that
point. Never use `waitForTimeout` to wait for UI — use a web-first `expect`.

**Product blockers.** A flow blocked by a product defect you have reproduced is
`test.fixme(true, "628E-BLOCKER: <short> — <product file:line>")` as the first statement of that test, with
the intended real steps kept below it, so removing the fixme yields a real test. The reason string is the
grep tag later slices use (`grep -rnE "628[EF]-BLOCKER" e2e/tests`): `628E-BLOCKER` for the legal suites,
`628F-BLOCKER` for the default-config sweep — new blockers take the tag of the slice that reproduced them.
Reproduce first (a run, with a screenshot/log as evidence), then fixme. The depth-ii precedent — a
`// FIXME(<suite>:<tag>)` comment citing file:line above `test.fixme(...)` — is equivalent.

**628G tag:** new blockers reproduced in this slice use `628G-BLOCKER`. AUTOMATION-NOTES says "new blockers take the tag of the slice that reproduced them". If 628G adds a portal blocker, the grep in that paragraph (`628[EF]-BLOCKER`) and its tag list can be widened in a portal-side note. `frontend/e2e/AUTOMATION-NOTES.md` is outside the row, so the PR body records the tag instead.

### `frontend/CLAUDE.md` › ESLint (Playwright bullet) and Anti-Patterns (lines 194, 206-219, verbatim)

- Playwright: `kazi-frontend-local/no-isvisible-catch` (`e2e/eslint/no-isvisible-catch.mjs`) bans `.catch()` on `isVisible()` & co. and the ignored `isVisible({ timeout })` in every file under `e2e/**` (except the two KC-only demo recording scripts, 628F R-2) — use `expect(locator).toBeVisible()`; see `e2e/AUTOMATION-NOTES.md` › Assertion rules

#### (quoted) Anti-Patterns — Never Do This

- Never use `@radix-ui/react-*` packages — use the unified `radix-ui` package instead
- Never put `"use client"` on a component unless it uses hooks, event handlers, or browser APIs
- Never call `getAuthContext()` in client components — it's server-only; use `useAuth()` hook instead
- Never access `params` without `await` — Next.js 16 params are Promises
- Never import from `@clerk/nextjs` — Clerk has been fully removed; use `lib/auth/` abstraction
- Never use `npm` — this project uses `pnpm`
- Never use olive/neutral/zinc/gray color classes — use the **slate** scale instead
- Never use indigo for accents — use **teal** instead
- Never import `motion` in server components — it's client-only. Only import in `"use client"` files.
- Never pass functions or component references as props from Server Components to `"use client"` components — Next.js 16 throws a runtime serialization error. Pass serializable data (strings, objects) instead.
- Never add a runtime terminology/vocabulary substitution layer (`useTerminology`, `t("Project")`, `<TerminologyText>`, a terminology map) — write the legal word (ADR-338)
- Never place two `<DialogTrigger asChild>` / `<AlertDialogTrigger asChild>` Radix siblings adjacent in the same parent JSX — they collide under React 19 + Radix `Slot` reconciliation and only one trigger wires up its `onClick`. See _Dialog Trigger Composition_ below.

### Root `CLAUDE.md` rules that bite here (verbatim excerpts)

- "PASS" means the agent **ran** the verification end-to-end (browser interaction → backend log → Mailpit / DB / file artefact). Inferring PASS from "code looks right" or "unit test green" is forbidden.
- Agents must reproduce a bug locally before writing the fix. … If you can't reproduce, the spec is wrong; report up, don't fix-and-pray.
- Editing `qa/testplan/demos/*.md` to make a scenario "match the product" is a **product decision**, not an agent decision.
- WONT_FIX is reserved for: explicit mandate exemptions (KYC, Payments), feature gaps that are documented as such, or genuine duplicate findings.
- Frontend → `pnpm lint && pnpm build && pnpm test` all green. Same for portal. (Memory: CI also runs `format:check` — include it.)
- One fix per PR. If scope expands during a fix … **stop and re-spec**.

### 628F builder rules inherited (from `qa_cycle/evidence/phase88-rewalk/628F/epic-brief.md:2282-2296`, verbatim)

- **R-3 (OD-3) — general rule accepted:** real flow where the product supports it; retitle where the check is legitimate but over-claims; `test.fixme` only for a reproduced product blocker. Specific: roles 2–3 re-scoped as recommended (system cards + Capability Reference; custom-role CRUD create→toggle→save→delete); automation-triggers retitled to what they check + follow-up "automation firing not covered"; invoice-lifecycle "VOID releases time entries" → **rewrite as a real flow** (time entry created via API setup, fee note created from it, approve, void via UI, assert the entry is unbilled again via API/UI); if the product refuses, reproduce and fixme as 628F-BLOCKER — do not delete; customer-lifecycle :227 → retitle "Fee note blocked for a client without a billing address" + assert 422 + `address_line1`; CUST-01 "pagination" → "Client list renders rows"; template-management real create + edit on the clone; custom-fields "value persists" → **real set → reload → assert** (product supports it). Every rename old → new listed in the PR body.
- **R-4 (OD-4) — accepted.** Each blocker MUST be reproduced in a run (screenshot/log under `qa_cycle/evidence/phase88-rewalk/628F/builder/`) before its `test.fixme(true, "628F-BLOCKER: … — <file:line>")`. document-generation tests 1–2 re-targeted to the matter page; one fixme'd "Generate a document from the client page" test.
- **R-5 (OD-5) — seed rows are read-only for default specs.** State-changing tests create their own run-unique entities; shared settings changed by a test are restored in `afterAll`; never assert global counts.
- **R-12 — no product code** (`frontend/app|components|lib|hooks`, backend, portal). No `qa/testplan/demos/*.md`. No soft paths, no lint-evading swaps (`.catch` removed but the soft `if (await x.isVisible())` kept on an absent target is FORBIDDEN — a bare `isVisible()` probe is allowed only for a genuinely conditional (b) target whose state the test cannot know, and must be preceded by an `a.or(b)` wait). No `waitForTimeout`. Every test title describes what it does.

## Integration Points

**Seed** (`compose/seed/seed.sh`, a POSIX sh container that runs at the end of `e2e-up.sh`). APIs only; idempotent on reseed. ~3.8 s.

| Row | Exact value | Source |
|---|---|---|
| Org | `e2e-test-org`, name "E2E Test Organization", profile `legal-za` | `seed.sh:8-10,39` |
| Members | alice (owner) `user_e2e_alice`, bob (admin), carol (member) | `seed.sh:69-76` |
| Client | **Acme Corp**, `contact@acme.example.com`, 1 Long Street, Cape Town 8001 ZA, ACTIVE | `seed.sh:118-214` |
| Portal contact | `contact@acme.example.com`, auto-provisioned from the client email (`PortalContactAutoProvisioner.java:80`) | R10 §C |
| Matter | **Website Redesign** (client Acme), GET-first | `seed.sh:216-240` |
| Trust account | **"Mathebula & Partners Trust Account"**, GENERAL, primary, Standard Bank 051001 / 0123456789, `requireDualApproval:false` | `lib/trust.sh` |
| Fee notes | INV-0001 **SENT** (notes "E2E seed fee note — sent", R 8 625,00) + 1 DRAFT ("E2E seed fee note — draft", R 3 450,00) on Acme / Website Redesign | `lib/invoices.sh`; `628F/builder/seed-rows.txt` |
| Info request | REQ-0001 **SENT** to the Acme contact on Website Redesign; item **"Certified ID copy"** (FILE_UPLOAD, required, hints "PDF, JPG, PNG"). **The send mints 1 magic-link token.** | `lib/information-requests.sh` |
| SoA | One statement on Website Redesign, `shareWithClient:true`, `notifyClient:false` (no email, no token); file `statement-of-account-website-redesign-<today>.pdf`, PORTAL-visible | `lib/statements.sh`; `StatementService.java:130,170-171,385-387` |
| Digest cadence | `PATCH /api/settings/portal-digest-cadence {"portalDigestCadence":"BIWEEKLY"}` | `seed.sh:262-264` |
| Portal resync | `POST /internal/portal/resync/e2e-test-org`, `X-API-KEY: e2e-test-api-key` (last step) | `seed.sh:266-272` |
| No Acme trust deposit | (R-10) | — |

**Firm API used by 628G** (Alice bearer from the mock IDP `POST http://localhost:8090/token {userId, orgSlug}`):
- `GET /api/clients?size=200`: array or `{content}`.
- `GET /api/matters`: array.
- `GET /api/trust-accounts`: array, `accountName`.
- `GET /api/clients/{id}/portal-contacts`: array, `{id,email}`.
- `POST /api/trust-accounts/{accountId}/transactions/deposit`:
  - `@RequiresCapability("MANAGE_TRUST")`, `TrustTransactionController.java:60-66`.
  - Body `RecordDepositRequest(UUID clientId, UUID matterId, BigDecimal amount, String reference, String description, LocalDate transactionDate)` (`TrustTransactionService.java:92-98`).
  - Validation: amount > 0, reference non-blank (`:1417-1428`); module `trust_accounting`.
  - Status `RECORDED` immediately.
  - Publishes `TrustTransactionRecordedEvent` (`:286-298`) → `TrustLedgerPortalSyncService.onTrustTransactionRecorded` (`:133-150`, AFTER_COMMIT, synchronous; non-approved statuses are filtered by `PORTAL_STATUSES = RECORDED, APPROVED` `:77`; the description is sanitised: an `[internal]` prefix drops it, 140-char cap, `PortalTrustDescriptionSanitiser`).
  - Also `PortalEmailNotificationChannel.onTrustTransactionRecorded` (`:150-176`) → email subject `"<org>: Trust account activity"`, link `/trust/{matterId}?orgId=…` (`:304-327`), gated on the contact's `trustActivityEnabled` (default on). **No token.**
- `POST /api/information-requests` + `POST /api/information-requests/{id}/send`:
  - Body as in `compose/seed/lib/information-requests.sh`.
  - The send → `InformationRequestEmailEventListener` mints a token via `MagicLinkService.generateTokenOnly` (`:73-86`); a cap failure is caught and the email goes without a deep link.
- `POST /api/court-dates` (MANAGE_LEGAL; `CourtCalendarController.java:31,64`): **only** to reproduce the OD-4 blocker (the court date never reaches the portal).
- `POST /internal/portal/resync/{orgId}` and `POST /internal/portal/digest/run-weekly?orgId=&targetEmail=&dryRun=` with `X-API-KEY: e2e-test-api-key`.

**Portal API** (Bearer = the portal session JWT from `getPortalJwt`):
- `GET /portal/requests`: list; fields `id, requestNumber, status, matterId, matterName, totalItems, submittedItems, …`.
- `GET /portal/requests/{id}`.
- `POST /portal/requests/{id}/items/{itemId}/upload` → `{documentId, uploadUrl, expiresAt}`, then a browser PUT to S3 (:4567), then `POST …/submit {documentId, textResponse:null}` → audit `portal.document.upload_initiated` + `portal.request_item.submitted`.
- `GET /portal/invoices`.
- `GET /portal/matters`, `/portal/matters/{id}/documents`.
- `GET /portal/documents/{id}/presign-download` → `{presignedUrl}`; audits `portal.document.downloaded`.
- `GET /portal/trust/summary`, `/portal/trust/matters/{id}/transactions`, `/portal/trust/movements?limit=1`.
- `GET /portal/deadlines?from&to[&status]`.
- `GET /portal/notification-preferences` → `firmDigestCadence`.
- `GET /portal/activity?tab=MINE|FIRM|ALL`.
- `GET /portal/me`.
- The portal app base is `NEXT_PUBLIC_PORTAL_API_URL` (`portal/lib/api-client.ts:4-5`, default **:8080**). A 401 clears auth and redirects to `/login`.

**Portal UI anchors** (all verified in source):

| Route | Anchors |
|---|---|
| `/home` | h1 "Home". Card links "Pending info requests" (href `/requests`, count in `<p>`), "Upcoming deadlines", "Last trust movement" (href `/trust`). "Recent fee notes" lists invoice-number links. |
| `/requests` | h1 "Documents & requests". Row links `/requests/{id}` showing requestNumber, matterName, status, "n/m submitted". |
| `/requests/{id}` | Mono requestNumber; h1 matterName; "{s}/{t} submitted • status {STATUS}"; per item: title + "required" + "Accepts: …"; sr-only label "Upload file for {name}"; button "Upload and submit"; done state "Submitted — status: {status}". |
| `/trust` | h1 "Trust statement"; auto-redirects to `/trust/{matterId}` when exactly one matter. |
| `/trust/{id}` | "Back to trust"; "Trust balance" card with `aria-label="Current balance"`; h2 "Transactions" + `table[aria-label="Trust transactions"]` (Date, Type, Description, Amount, Running balance); h2 "Statements". |
| `/invoices` | h1 "Fee notes"; `table[aria-label="Fee Note list"]`. |
| `/invoices/{id}` | h1 invoice number; "Download PDF" (aria "Download {n} as PDF"); "Pay Now" only with `paymentUrl`; otherwise "Contact {orgName} to arrange payment"; h2 "Line Items". |
| `/matters` | h1 "Your matters"; MatterCard links `/matters/{id}`. |
| `/matters/{id}` | h1 name; h2 Tasks / Documents / Comments; doc download button aria "Download {fileName}". |
| `/deadlines` | h1 "Diary dates"; empty `data-testid="deadline-list-empty"` "No deadlines in this view"; rows are buttons "View details for {label}"; detail `role="dialog"`. |
| `/profile` | h1 "Profile"; "Contact Information"; dt Name / Email / Role / Client (read-only). |
| `/settings/notifications` | h1 "Notification Preferences"; cadence text "Your firm sends weekly digests." / "Your firm sends bi-weekly digests." / "Your firm currently has digests turned off."; switches "Weekly digest", "Trust activity", "Deadline reminders", "Action-required notifications". |
| `/activity` | h1 "Activity"; tabs "Your actions" / "Firm actions"; `ul[data-testid="activity-list"]`. |

## How to run

**Stack ports:**
- Mock-auth e2e: backend **:8081**, frontend **:3001**, mock IDP **:8090**.
- Mailpit API **:8026** (SMTP :1026).
- Postgres **:5433** (`docker exec e2e-postgres psql -U postgres -d app`).
- LocalStack S3 **:4567**.
- The portal is NOT in the e2e compose; Playwright's `webServer` serves it on **:3002** (dev) or, for login-bare, as a prod build.

**Fresh stack (gate-grade):**

```bash
cd /Users/rakheendama/Projects/2026/worktree-epic-628G
docker builder prune -f                       # the Docker VM disk fills (memory)
bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh
docker compose -f compose/docker-compose.e2e.yml logs seed | grep -E "E2E Boot-Seed Complete!|FAIL"   # must show Complete!, no FAIL
NODE_OPTIONS="" /opt/homebrew/bin/pnpm install --frozen-lockfile   # the worktree has no node_modules
```

`e2e-reseed.sh` does NOT wipe, so it is not gate-grade.

**Free :3002 (the KC dev portal may hold it):**

```bash
lsof -nP -iTCP:3002 -sTCP:LISTEN            # note the PID/cwd; at scout time :3002 was free
# if the main-checkout svc.sh portal holds it:
bash /Users/rakheendama/Projects/2026/b2b-strawman/compose/scripts/svc.sh stop portal
# … runs …
# restore ONLY if it was running before:
bash /Users/rakheendama/Projects/2026/b2b-strawman/compose/scripts/svc.sh start portal
bash /Users/rakheendama/Projects/2026/b2b-strawman/compose/scripts/svc.sh status   # portal row: yes yes 3002
```

`reuseExistingServer: !CI`: a leftover dev portal on :3002 that points at :8080 would be reused silently, so every page would 401 → `/login`.

### Magic-link budget: the truth (code, not comments)

| Cap | Value | Counted | Cleared by | Source |
|---|---|---|---|---|
| Per **contact** | 3 tokens / 5 min (`count >= 3` → refuse) | DB rows in `<tenant>.magic_link_tokens`, any origin (request-link **and** info-request sends) | Time only (a backend restart does NOT clear it) | `MagicLinkService.java:29,112-117` |
| Per **email** | 3 request-link calls / 15 min from the first call (Caffeine `expireAfterWrite`; the 4th → **429** before any lookup) | In-memory | `docker restart e2e-backend`, or 15 min | `PortalLoginRateLimiter.java:39-41,44-50,62-71`; defaults `docteams.portal.auth.email-rate-limit.{max-requests:3,window-minutes:15}`, not overridden in `application-e2e.yml` |

- When capped, request-link still answers 200 but sends nothing (`PortalAuthService.java:130-141`). `globalSetup` then throws a clear error. Isolation's `getPortalJwt` returns null, so `expect(jwt).toBeTruthy()` fails. login-bare times out waiting for Mailpit.
- The 628F hand-off's "cap 3 / 5 min" is the per-contact cap. The config and login-bare comments' "3 / 15 min" is the per-email limiter. **Both are true.**

**Spenders for `contact@acme.example.com`:**

| Spender | Tokens | Request-link calls |
|---|---|---|
| Seed REQ send (at e2e-up end) | 1 | 0 |
| Isolation spec (2 tests × `getPortalJwt`) | 2 | 2 |
| 90-day / responsive `globalSetup` (once per invocation) | 1 | 1 |
| OD-1(B) in-spec send | 1 | 0 |
| login-bare | 1 | 1 |
| REGRESSION portal section | 1 | 1 |
| Default-config specs | 0 | 0 (IREQ specs send to their own clients, `information-request-crud.spec.ts:10-12`) |

**Recommended E2E-PORTAL order** on one fresh stack (after E2E-LEGAL / E2E-DEFAULT if they share it: OD-1(A), OD-2):

1. **Isolation** (2 calls, 2 tokens; with the seed token that makes ≤3 in 5 min, OK):
   ```bash
   cd /Users/rakheendama/Projects/2026/worktree-epic-628G/frontend
   KGOSI_EMAIL=contact@acme.example.com BACKEND_URL=http://localhost:8081 PLAYWRIGHT_BASE_URL=http://localhost:3001 \
     NODE_OPTIONS="" /opt/homebrew/bin/pnpm test:e2e e2e/tests/portal/ --reporter=list 2>&1 | tee /tmp/628G/e2e-isolation.txt
   ```
   Path-anchored (P-1); default config; API-only. Expect **2 passed**.
2. **Wait** until ≥5 min have passed since the newest token (`docker exec e2e-postgres psql -U postgres -d app -Atc "select now()-max(created_at) from <tenant>.magic_link_tokens"`; tenant from `public.org_schema_mapping`). Then run **90-day + responsive in ONE invocation**: one `globalSetup` call, so the per-email count is 3/3; tokens: globalSetup 1 + OD-1(B) 1.
   ```bash
   cd /Users/rakheendama/Projects/2026/worktree-epic-628G/portal
   export NODE_OPTIONS="" NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 \
          MAILPIT_URL=http://localhost:8026 PORTAL_ORG_SLUG=e2e-test-org PORTAL_CONTACT_EMAIL=contact@acme.example.com \
          PLAYWRIGHT_BASE_URL=http://localhost:3002
   /opt/homebrew/bin/pnpm exec playwright test --config=playwright.portal.config.ts \
     --project=chromium --project=portal-client-90day --reporter=list --update-snapshots=all 2>&1 | tee /tmp/628G/e2e-portal.txt
   ```
   This equals `pnpm test:e2e:portal -- --update-snapshots=all` once OD-10 lands. Expect 21 responsive passed, plus the 90-day tests passed except the declared fixmes. Snapshot updates are explained under OD-7.
3. **login-bare**: run `docker restart e2e-backend` (clears the per-email limiter) and wait for `curl -sf localhost:8081/actuator/health`. Make sure ≥5 min have passed since the newest token. Then, in a **new shell WITHOUT `PORTAL_CONTACT_EMAIL`**:
   ```bash
   cd /Users/rakheendama/Projects/2026/worktree-epic-628G/portal
   NODE_OPTIONS="" NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 \
     /opt/homebrew/bin/pnpm test:e2e:login-bare --reporter=list 2>&1 | tee /tmp/628G/e2e-login-bare.txt
   ```
   This self-serves `pnpm run build && pnpm run start` on :3002 (production; `next dev` double-POSTs the exchange). Free :3002 first. Expect **1 passed**.
4. **Non-vacuity checks (617B, "exit 0 ≠ evidence"):**
   - Open 2–3 written PNGs with Read and confirm they are authenticated pages, not `/login`. Candidates: `portal/e2e/screenshots/portal-v2/portal-client-90day/day-00-home-landing.png`, `day-21-trust-balance.png`, `day-60-document-downloaded.png`.
   - Grep the backend log for the globalSetup exchange: `docker logs e2e-backend 2>&1 | grep "Exchanged magic link"`.
   - Read back the DB rows the run created: REQ IN_PROGRESS with 1 submitted, the trust txn, and audit rows `portal.request_item.submitted` / `portal.document.downloaded`.
5. **Tree hygiene:**
   ```bash
   git -C /Users/rakheendama/Projects/2026/worktree-epic-628G status --short
   git clean -n portal/e2e/screenshots          # review
   git clean -f portal/e2e/screenshots          # removes only untracked PNGs
   ```
   `.gitkeep` and the README are tracked and stay. `portal/test-results/` and `playwright-report/` are gitignored (`portal/.gitignore:16-19`). A frontend isolation run is API-only; still check `git status` for `frontend/test-results` / `documentation/screenshots` (the legal configs dirty those; restore with `git checkout -- frontend/test-results documentation/screenshots` if touched).

**Re-runs on the same stack:**
- With OD-1(B) and OD-2(a), the 90-day suite is re-runnable, but it needs the cap waits above.
- With OD-1(A) it is fresh-stack-only.
- After a capped run, `docker restart e2e-backend` clears only the per-email limiter; the per-contact cap needs 5 min.

**Baseline before the change:** the reproducer agent is measuring main post-628F. Record its numbers as `628G/baseline-main/`. The expectation is that the 3 soft-assert failures (C1/C2/C3) likely now pass vacuously or partially, Day 45 pay is skipped, and Day 7 / 75 / 90 depend on serial aborts.

## Build & Verify

**PORTAL-GATE** (from the worktree; mirrors CI `.github/workflows/ci.yml:162-195` lint → format:check → test → build):

```bash
cd /Users/rakheendama/Projects/2026/worktree-epic-628G
NODE_OPTIONS="" /opt/homebrew/bin/pnpm install --frozen-lockfile
cd portal && rm -rf .next
NODE_OPTIONS="" /opt/homebrew/bin/pnpm lint            # script "lint": "eslint" — lints e2e/** too
NODE_OPTIONS="" /opt/homebrew/bin/pnpm format:check    # "prettier --check ." — covers e2e/**; fix with `pnpm format`
NODE_OPTIONS="" /opt/homebrew/bin/pnpm test            # "vitest run" — excludes e2e/** (vitest.config.mts)
NODE_OPTIONS="" /opt/homebrew/bin/pnpm build           # "next build" — type-checks every **/*.ts incl. e2e/ (tsconfig include)
SKIP_PORTAL_BASELINES=true NODE_OPTIONS="" /opt/homebrew/bin/pnpm exec playwright test --config=playwright.portal.config.ts --list   # parse check
```

- Last recorded PORTAL-GATE: **44 files / 243 tests** (628B, PR #1704), with 4 lint warnings (624I). 628F did not touch the portal.
- If OD-8(a) lands, the vitest count rises; state the new count.
- `next build` can break in a worktree that also holds `backend/target` (628C memory). If the build fails oddly, check that first.
- **Marker:** `.claude/markers/verify-portal.json` (format in `.claude/markers/README.md`): `{commit, command, exit:0, ts, summary}`. Written only after a clean PORTAL-GATE, and at most 24 h before merge.
- If OD-8(a) touches `.github/workflows/ci.yml`: that file is not an area path, so no extra marker is needed.
- `frontend/` is untouched, so no frontend marker.

**E2E-PORTAL** = the run order above (isolation, then 90-day + responsive, then login-bare). Save the summaries to `qa_cycle/evidence/phase88-rewalk/628G/gate-final/{e2e-isolation,e2e-portal,e2e-login-bare}.txt`. Acceptance: **0 failed**; skips only the env opt-ins and the declared `628G-BLOCKER` fixmes (OD-12); isolation 2 passed; login-bare 1 passed. Every Playwright summary line is cited verbatim in the PR body.

**OD-13 optional** (before the portal runs, same fresh stack):

```bash
cd frontend && export PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS=""
/opt/homebrew/bin/pnpm test:e2e:settings; /opt/homebrew/bin/pnpm test:e2e:projects; /opt/homebrew/bin/pnpm test:e2e:customers
```

628F expected counts: settings 29/0/1, projects 25/0/0, customers 15/0/5 (the 628F-BLOCKER fixmes).

## Environment

- pnpm: `/opt/homebrew/bin/pnpm`. Always prefix with `NODE_OPTIONS=""`, because the user shell sets `--openssl-legacy-provider`, which breaks Next.
- Use `SHELL=/bin/bash` for docker builds and `e2e-up.sh` (a zoxide alias breaks `cd`).
- Postgres on the e2e stack: `docker exec e2e-postgres psql -U postgres -d app`. The tenant schema changes per fresh stack: `select * from public.org_schema_mapping;`.
- Mailpit e2e API: `http://localhost:8026/api/v1/search?query=to:contact@acme.example.com`. The KC dev Mailpit is :8025; do not confuse them.
- Playwright browsers are cached globally. The portal `@playwright/test` is ^1.59.1.
- Run long commands in the **foreground** with explicit timeouts ≤ 600000 ms (memory: background jobs die at the 10-min ceiling). A full 90-day + responsive run is ~1 min; login-bare includes a prod build (~2–3 min).
- macOS sleep kills long runs: keep a `caffeinate -is` hold during gate runs.
- Do not commit in the main checkout (`/Users/rakheendama/Projects/2026/b2b-strawman`). Work only in the worktree; confirm with `pwd` before the first write.
- PR hygiene:
  - PR ≤300 files.
  - Evidence goes in a post-merge docs commit if large.
  - Merge via a single plain `gh pr merge <N> … --match-head-commit <full-sha>` (the hook text-matches).
  - The review audit trail is two `## Verdict: APPROVE @<sha>` lines + `## CodeRabbit:`.
---

---

## ORCHESTRATOR RULINGS (binding — these override any conflicting text above)

Baseline evidence (fresh stack, main 8121aa0c7) is in `/tmp/628G/baseline/` (90day-run2.json, trace summary, shots/, tokens.txt). Summary: the 90-day suite went 8 pass / 2 fail (Day 7 hidden `<option>`, Day 60 hidden responsive duplicate) / 1 skip (Day 45 pay) / 1 not-run (Day 75). All 8 passes are vacuous: Day 0 detail clicks the sidebar link, Day 3 uploads nothing and matches "0/1 submitted", Day 21 matches a heading on the empty state, Day 85 matches static text, Day 90 matches the sidebar. Seeded REQ-0001, SoA and BIWEEKLY cadence ARE visible in the portal UI. Copy the baseline summary into `qa_cycle/evidence/phase88-rewalk/628G/baseline-main/` (text files only: `cp /tmp/628G/baseline/*.txt /tmp/628G/baseline/*.json` there — no PNGs).

- **R-1 (OD-1) = B, plus a read-only seed check.** `day-00-07` `beforeAll` creates and sends a run-unique request (FILE_UPLOAD item) to the Acme contact on Website Redesign via the firm API. Day 0 and Day 3 drive that request. Day 0 ALSO asserts read-only that seeded REQ-0001 ("Certified ID copy") is listed for the client. It never opens it for submit and never consumes it. The Day 0 pending count comes from the API after the send, never hard-coded. Day 3 = real upload, then submit, then the SUBMITTED status, then the Home pending count drops by 1.
- **R-2 (OD-2) = a.** In-spec firm-API deposit, with a run-unique description. Assert the row by its description, never by a global balance. Take the optional Mailpit "Trust account activity" nudge check ONLY if it is deterministic: select the newest matching message created after the deposit timestamp. Otherwise skip that sub-step, with a comment (not a runtime skip).
- **R-3 (OD-3) = a.** Retitle and assert the no-PSP state: contact text present, Pay Now count 0, banner count 0. The PR body records scenario 45.4–45.8 / E.9 as the **Payments mandate exemption** (CLAUDE.md §6) with the `MockPaymentGateway.java:34` @Profile evidence. No backend changes.
- **R-4 (OD-4) = a.** Day 7 is retitled to the empty-state check. Day 75 becomes `test.fixme(true, "628G-BLOCKER: …")`, with the intended steps kept beneath it. **Reproduce first:** create a court date via the firm API, then show that portal `/deadlines` stays empty. Save a log plus one screenshot under `qa_cycle/evidence/phase88-rewalk/628G/builder/blockers/`.
- **R-5 (OD-5) = a.** Retitle to a strict read-only profile check, and record the finding.
- **R-6 (OD-6):** retitle as recommended, citing register §5. Scenario prose is 628H's job, so do NOT edit `qa/testplan/**`.
- **R-7 (OD-7) = a.** Strict content assertions go before every `toHaveScreenshot`. Run with `--update-snapshots=all` (no `--` separator through pnpm), then delete the written PNGs. Never commit PNGs.
- **R-8 (OD-8) = a — AUTHORISED** (same-bug-class guard, orchestrator §7 call): portal copy of `no-isvisible-catch` + `portal/eslint-rules/index.mjs` + eslint.config wiring + wiring/drift test + CI portal path-filter line. Portal `pnpm lint` must then pass over `portal/e2e/**`.
- **R-9 (OD-9):** accepted as written: the seed-contact default, script-supplied `PORTAL_CONTACT_EMAIL`, and a dev webServer env that defaults the API URL to :8081.
- **R-10 (OD-10):** accepted as written. Add `test:e2e:responsive` and `test:e2e:portal`; never use positional path filters.
- **R-11 (OD-11):** accepted. Comments and message only.
- **R-12 (OD-12): CONFIRMED.** Zero runtime or conditional skips. Allowed: declared `test.fixme(true, "628G-BLOCKER: …")` for reproduced product gaps, and env opt-ins. List every one in the PR body. Also delete all 31 `waitForTimeout` calls.
- **R-13 (OD-13):** accepted. Before the portal runs, run `test:e2e:settings`, `test:e2e:projects` and `test:e2e:customers` on the fresh stack. Cite their summary lines and the 628F evidence for PACK-01, PROJ-02/03, CUST-01/02 and P-1.
- **R-14 (OD-14):** accepted.
- **R-15 (OD-15):** list all 7 follow-ups in the PR body under "Follow-ups (not fixed here)", plus:
  - (8) isolation spec test 1 only asserts `matters.length > 0` and doesn't prove isolation;
  - (9) a responsive snapshot arg `lg/x.png` is written as `lg-x.png`, so the committed `sm/md/lg` dirs are dead;
  - (10) `isVisible({timeout})` doesn't wait (racy guards everywhere).
- **R-16 — No product code.** Do not touch `portal/app`, `portal/components`, `portal/lib`, `backend/**` or `frontend/{app,components,lib}`. If a target is genuinely broken (not just absent), reproduce it, add a `628G-BLOCKER` fixme with evidence, and continue. If more than 2 tests would need new BLOCKERs beyond Day 75, STOP and report instead of opening the PR.
- **R-17 — You MUST run the specs you convert** (628F R-11). Bring up a fresh stack FROM THIS WORKTREE first. The current e2e LocalStack bind-mounts a deleted worktree's path, so it must be rebuilt: `cd /Users/rakheendama/Projects/2026/worktree-epic-628G && bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh` (nohup + marker if > 10 min). Then follow the brief's E2E-PORTAL run order and magic-link waits. Do the KC portal dance: `svc.sh stop portal` before, and **always** `svc.sh start portal` after (run from the main checkout `/Users/rakheendama/Projects/2026/b2b-strawman`). Restore `portal/next-env.d.ts` in both checkouts if `next dev` rewrote it.
  - **Mutation check:** temporarily break one strict assertion in Day 3 and one in Day 21, and confirm each fails. Revert, and record both in the PR body.
  - **Re-runnability:** run the 90-day + responsive invocation TWICE on the same stack (respecting the 5-min token window). Both must be 0 failed.
- **R-18 — Evidence (text only, no PNGs)** goes under `qa_cycle/evidence/phase88-rewalk/628G/builder/`: run logs with Playwright summary lines, blockers/, and the mutation-check log. Keep the PR well under 300 files.
- **R-19 — PORTAL-GATE** from `portal/`: lint, build, test, and `format:check` (prettier), all green. Frontend code is untouched; the CI yml edit needs no FE gate. Write the portal verify result summary into the PR Test plan. Do NOT write `.claude/markers/*`; the orchestrator owns markers.
- **R-20 — PR:** title `Slice 628G: portal 90-day on the seeded rows + portal soft-skip ban (628.16–628.17)`. The body includes the census summary (before→after guard counts), the rulings R-1..R-20, every Playwright summary line verbatim, the BLOCKER and env-opt-in list, the Payments exemption, the carried-set closure citations, and the follow-ups.
