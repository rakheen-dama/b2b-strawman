# 628F B1 hand-off (Builder 1 of 2)

Branch `phase88/11F-default-config-seed-scripts`, pushed. Commits: `d4ab98428` (main B1 commit, 47 files), `e406073dd` (restores two DP-01 test titles). No PR opened.
Evidence: `qa_cycle/evidence/phase88-rewalk/628F/builder/` (committed).

## Done (R-1 B1 scope)
- **Seed** (`compose/seed/`, POSIX sh; `sh -n` clean; shellcheck on the touched files is clean; `bash -n scripts/regression-test.sh` passes)
  - `seed.sh`: Step 7 resolves Website Redesign GET-first (no duplicate matter on reseed). New Steps 8–12 source `lib/common.sh` and call `seed_trust_account`, `seed_boot_fee_notes`, `seed_information_request`, `seed_statement`, then `PATCH portal-digest-cadence BIWEEKLY`. The portal resync moved last (Step 13). The banner is extended.
  - `lib/common.sh`: NEW `api_patch_json`, NEW `check_2xx` (2xx only; echoes the body on failure). No existing signature changed.
  - `lib/invoices.sh`: NEW `seed_boot_fee_notes`, idempotent by notes marker. Fee note B is advanced DRAFT→APPROVED→SENT with `overrideWarnings:false` (no 422). The rich-seed Acme lookup is narrowed to `.notes == "Legacy migration completed work"`.
  - NEW `lib/trust.sh`, `lib/information-requests.sh` (re-creates the request when only COMPLETED/CANCELLED remain), `lib/statements.sh`.
  - Idempotency was proven by running the full seed twice in `docteams-e2e-seed` (busybox) against a populated stack: the second run skips every row.
  - NOTE: shellcheck on *all* of `lib/*.sh` is NOT clean on main either. The brief said "baseline clean", which is wrong. There are pre-existing warnings in documents.sh, reset.sh and tasks.sh, all untouched.
- **Config / scripts**
  - `playwright.config.ts`: `LEGAL_SUITES` is added to the testIgnore of both chromium projects. `KC_ONLY_SCRIPTS` (demo-full-90day, demo-recording) is added to the mock testIgnore.
  - `package.json`: added `test:e2e:projects|customers|settings|navigation`.
  - `debug-kc-invite.spec.ts` deleted (R-2).
  - `eslint.config.mjs`: the rule `files:` is widened to legal + helpers + fixtures + `e2e/tests/{customers,projects,settings,navigation}/**/*.ts`. Added `ignores: [demo-full-90day, demo-recording]` with the R-2 comment.
- **Rule vitest** (`__tests__/eslint-rules/no-isvisible-catch.test.ts`): the enforced pins now include probes in customers/projects/settings/navigation. The "not (yet) enforced" pins now cover documents probe, demo-full-90day, demo-recording and components. 55/55 pass.
  - **B2 must flip:** move `e2e/tests/documents/__probe__.spec.ts` into the enforced list. Add `e2e/tests/__probe__.spec.ts` and `e2e/__probe__.ts`. Keep demo-* as NOT enforced (they stay in `ignores`, R-2). Rename the negative block to "not enforced outside e2e/" with components/app/lib probes.
- **NEW `e2e/helpers/seed.ts`**: exports `SEED` names, `seedToken`, `apiGet`, `acmeId()` and `websiteRedesignId()`. It documents the R-5 read-only rule.
- **depth-ii `day-14:142-143`** (R-7): the per-item `{ name, requiresDocument }` table plus `toHaveCount(0|1)` before "Mark Complete". It is green in the E2E-LEGAL run.
- **Lint census oracle (`--rule` on all of `e2e/`) after B1**: 129 problems in 15 files. That is 88 swallowed + 41 timeoutIgnored, of which demo-* account for 22.
  - **B2's remaining work, excluding the demo scripts: 67 swallowed + 40 timeoutIgnored in 13 files.**
    - document-generation 34, template-management 34, notification-bell 11, information-request-crud 6, notification-preferences 5, invoice-lifecycle 4, profitability 3, reports 3, billing-runs 2, invoice-crud 2, automation-crud 1, automation-triggers 1, compliance-overview 1. Each count is problems = swallowed + timeoutIgnored.
  - `pnpm lint`: 0 errors (82 pre-existing warnings, none in B1 files). `pnpm format:check`: green.
- **Static `--list`:**
  - default mock: **176 tests / 40 files**, 0 paths under `legal-lifecycle/` or `legal-depth-ii/`. Two matches for the string "legal-" remain; both are titles containing "legal-za".
  - The KC-mode list also has 0 legal paths.
  - Per-suite scripts: projects 25/4, customers 20/3, settings 30/8, navigation 16/2.
  - Legal configs are unchanged: 104 and 43.

## Stack / seed / E2E-LEGAL
- Fresh `e2e-down && e2e-up` was run from the worktree root. The seed log is `builder/seed-log.txt` (renamed from seed.log because `*.log` is gitignored) and contains `E2E Boot-Seed Complete!`.
- **Seed duration ≈ 3.8 s** (06:30:12.86 → 06:30:16.69 UTC), far below the R-9 90 s limit.
- Seed rows were read through the API (`builder/seed-rows.txt`):
  - one trust account, "Mathebula & Partners Trust Account" (Standard Bank 051001 / 0123456789, GENERAL, primary);
  - Acme fee notes INV-0001 SENT (R 8 625) and 1 DRAFT;
  - REQ-0001 SENT to contact@acme.example.com on Website Redesign;
  - Website Redesign statements totalElements 1;
  - portalDigestCadence BIWEEKLY;
  - exactly one Website Redesign matter.
- **E2E-LEGAL (once, same fresh stack, before any default specs):** lifecycle **101 passed / 0 failed / 3 skipped**; depth-ii **39 / 0 / 4**. The depth-ii run already included the R-7 day-14 change. Summaries: `builder/e2e-legal-{lifecycle,depth-ii}.txt`.

## Per-dir results (`pnpm test:e2e:<dir> --reporter=list`, final two consecutive runs; `builder/e2e-<dir>.txt`)
| dir | run 1 | run 2 |
|---|---|---|
| customers | 15 passed / 0 failed / 5 skipped | 15 / 0 / 5 (+ run 3 after title-only fix: 15/0/5) |
| projects | 25 / 0 / 0 | 25 / 0 / 0 |
| settings | 29 / 0 / 1 | 29 / 0 / 1 |
| navigation | 16 / 0 / 0 | 16 / 0 / 0 |

The skips are only the 628F-BLOCKER fixmes listed below. There is no runtime `test.skip` and no `waitForTimeout` left in the four dirs.

## 628F-BLOCKERs (all reproduced; repro specs were temporary, kept at /tmp/628F/b1/repro/, not committed)
1. **customer-lifecycle "DORMANT -> OFFBOARDING".** There is no firm-UI path. The DORMANT header "Edit" is dead, and the overflow menu has no lifecycle item.
   - Repro: `builder/blockers/customer-lifecycle-offboarding.txt` + `dormant-no-offboard.png`.
2. **customer-lifecycle "OFFBOARDING -> OFFBOARDED".** The header "Complete Offboarding" is dead: clicking it opens 0 alertdialogs and the status stays OFFBOARDING.
   - Repro: same txt + `offboarding-dead-button.png`.
3. **customer-data-protection "Download customer data export".** "Export Ready" shows `files · NaN undefined` and "Download Archive" has no href. The backend `ExportResult(exportId,status,estimatedFiles)` does not match the frontend `StandaloneExportResult(downloadUrl,fileCount,totalSizeBytes)`.
   - Repro: `data-export-ready-no-href.{txt,png}`.
4. **customer-data-protection "Anonymize customer preview shows entity counts" + "Execute anonymization".** The client page crashes with `TypeError … 'portalContacts'`. The frontend expects a nested `affectedEntities`, but the backend `AnonymizationPreview` is flat.
   - Repro: `anonymize-preview-crash.{txt,png}` + the raw API response.
   - NEW, not in OD-4.
5. **roles-capabilities "Create, edit and delete a custom role".** The Create Role dialog is 839 px tall in a 1280×720 viewport with no scroll, so the title and the "Create" button are unreachable (`components/roles/role-dialog.tsx:105`).
   - Repro: `role-dialog-overflow.{txt,png}`.
   - NEW. **Orchestrator call:** this could instead run real at a taller viewport (`test.use({ viewport })`) with the defect logged as a follow-up. I chose fixme per R-3/R-4.

OD-4 #3 (client "Generate Document") and #4 (dead "Edit"/"Activate Client" class) are B2/document-generation territory. #4 is partly evidenced by blocker 1 (the dead DORMANT "Edit").

## Renames (old → new)
- customers/customer-crud: "Customer list pagination" → "Client list renders rows" (R-3)
- customers/customer-lifecycle: "PROSPECT blocked from creating invoice" → "Fee note blocked for a client without a billing address" (R-3; asserts 422 + `address_line1`)
- projects/project-time: "7. My Work shows cross-project entries" → "7. My Work lists today's time entries"
- settings/roles-capabilities: "View role capabilities" → "View system role cards and the capability reference"; "Edit role capability" → "Create, edit and delete a custom role" (R-3)
- debug-kc-invite.spec.ts deleted ("debug: extract and inspect invite link")

## Census conversions, judgement calls and deviations
- **Real flows are now driven.** Highlights:
  - CUST-01 (dialog-scoped exact labels; step-2 ID/Passport saved and checked via API; edit through the overflow menu; real search via `?search=`)
  - CUST-02 (Start Onboarding header action; checklist settle → auto ACTIVE; ACTIVE→DORMANT via Compliance › dormancy check; OFFBOARDED guard self-contained via API)
  - DP-01 tests 4–5 on own clients
  - PROJ-01..03 (openGroupedTab; edit via "Edit Matter" menu item; real tab content checks; archived-matter time logging refused with "archived")
  - BUDGET-01 (Fees → Fee estimate panel values)
  - CF-01 (create a field; the step-2 intake field; real set → reload → assert, plus an API check)
  - TAG-01 (inline-create on own matter + reload)
  - SET-01 (save → reload → toHaveValue)
  - SET-03 (create rate; VAT line on seeded INV)
  - DP-02 (one page, no inner tabs; retention edit persisted; DSAR logged)
  - CMD-01 (toPass opener)
- **PACK-01 test 2 was wrong all along** (it never ran at baseline):
  - `locator("span").filter({ has: <card-rooted locator> })` never matched. Now `uninstallBtn.locator("..")`.
  - `POST /api/templates/{id}/reset` answers 400 for PACK templates ("Only ORG_CUSTOM templates can be reset"). The revert now PUTs the content back without the `_e2e_modified` marker, which makes the hash match again. The afterAll uses the same revert.
  - The current gate on the stack: `canUninstall:false, "5 templates have been used to generate documents"`, with no "edited" reason.
- **R-5 / shared state:**
  - general-settings restores brand colour and footer in afterAll (UI), verified back to #1B3A4B / null.
  - data-protection-settings restores the Client retention days in afterAll (verified 1825).
  - DSAR, tag and anonymize tests use run-unique own entities.
  - The Information Officer test (pre-existing) still sets a run-unique officer name and is not restored; no spec reads it.
  - The Acme Corp **export** (blocked anyway) and **anonymize preview** use Acme read-only.
- **Hidden duplicate DOM:**
  - Once, `getByTestId("client-name")` resolved to 2 `<h1>`s for 15 s, and `getByPlaceholder("Search clients…")` resolved to 2 inputs. A hidden copy of the page subtree was left by a re-render.
  - Mitigation: assert visible role headings, scope the search box via `getByRole("search")`, and use `.filter({ visible: true })` on client-row/clients-footer testids.
  - Possible product follow-up (React/Next hidden subtree); not investigated further.
- **PROJ-02:** a "Companion Task" is created in beforeAll. When the default filter yields zero tasks, the panel shows "No tasks yet" without the filter chips, so a DONE/CANCELLED task cannot be revealed. This is a product follow-up (`components/tasks/task-list-panel.tsx:304`).
- **sidebar-navigation NAV-01 and rate-cards** (no census sites, but vacuous `.flex-1`/body checks) were made real: an `#main-content` h1, rates heading + member row, and the denied text.
- Out of scope in my dirs: `frontend/e2e/README.md` (optional) was not touched. AUTOMATION-NOTES and `frontend/CLAUDE.md` are B2's.

## Stack state for B2
- The e2e stack is up, from the worktree build, seeded, with E2E-LEGAL already run once. My four dirs have run about 4×, which left run-unique clients, matters, tags, tax rates, custom fields and DSARs behind. No custom roles were created, because the role CRUD test is fixme'd.
- Acme Corp has extra rows: compliance export records from the blocked export test's earlier iterations, and nothing else mutated.
- The legal-za "Power of Attorney" template is reverted (no edit marker).
- Tree hygiene is done; the working tree is clean except the untracked `tasks/insights/*` that are not ours.
- If B2 wipes the stack: re-run E2E-LEGAL first (R-11).
