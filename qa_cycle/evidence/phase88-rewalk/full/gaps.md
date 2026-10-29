# 629.11 — Gap register — Phase 88 end-of-phase re-walk (629A + 629B + 629C) — 2026-10-30

This is the authoritative register for Epic 629 (629B ruling O-10, `exit-checkpoints.md:3`), so 629C may re-rate any gap; each re-rating below states its reason. Written by 629C builder 1 at HEAD `20559c3d6`; builder 2 added the 629.9 suite rows (G88-F48, G88-F49) and the final arithmetic. The product tree is byte-identical to the 628I gate `cb10c5d63`: `git diff --stat cb10c5d63..HEAD -- backend frontend portal gateway packages compose` is empty. Every HIGH file:line below was re-read at HEAD on 2026-10-30.

**Scales and classes.**
- **Severity** uses the master-doc scale BLOCKER / HIGH / MEDIUM / LOW (`qa/testplan/demo-readiness-keycloak-master.md`).
- **Classes:**
  - **product bug**.
  - **scenario mismatch needing authorisation**, which comes in two kinds:
    - *design proven*: the product behaviour is deliberate, shown by file:line;
    - *missing feature — design intent unproven*: the code shows only absence. Root CLAUDE.md §6: absence is not intent.
  - **process**.
  - **HYPOTHESIS (unreproduced)**: listed, and not counted as a confirmed FAIL (ruling O-6).
- **Owner rule:** the 629 re-walk files gaps and never fixes them. A gap that a closed slice introduced or did not cover is owned by "new — follow-up phase (attribution: <slice>)". No owner cell names a closed slice as the owner.
- **"Inside register §5"** (ruling O-9) means one of:
  - §5 rows 1–15;
  - §5a rows 1–18, authorised 2026-10-29 and applied (row 14 by `7ea0175ac`, rows 1–13 and 15–18 by #1712 `45dcc7a6f`);
  - §5b rows 19–20, documented product proposals (`tasks/phase88-gap-register.md` §5–§5b).

  Everything else is "outside §5".

**Evidence paths** are relative to `qa_cycle/evidence/phase88-rewalk/full/` unless a path is shown in full. Each row links the day file that holds the full observed / expected text instead of copying it. The README index (`README.md` § "Candidate gaps (629A + 629B) — index") has the long-form rows for G88-F1 … F34.

## Summary

**Exit test: FAIL — not demo-ready** (ruling O-9; stated plainly, not softened).
- **0 HIGH → FAIL:** 3 HIGH: G88-F12 and G88-F33 (both re-verified at HEAD), and **G88-F48** (the 629.9 suite run; re-verified at HEAD and in the e2e DB). Rows below.
- **0 FAIL outside register §5 → FAIL:** 59 of the 71 scored FAIL rows are outside §5. Of the 71, 37 are 90-day rows and 34 are portal rows.
  - Inside §5 (12 rows):
    - 7 sit in §5a rows 1, 2, 4, 5, 6, which are applied (O-12 reconciliation, below);
    - 5 sit in §5b rows 19/20, which are documented.
  - Outside §5 (59 rows):
    - 11 are product-bug FAILs (G88-F3 ×2, G-B8, G88-M6, **G88-F12**, G88-F27, G88-F29, G88-F31, **G88-F33** ×2, G88-F16);
    - 45 are scenario mismatches still awaiting founder authorisation (G88-F1 ×27, G88-F37 ×10, G88-F32 ×3, G88-F11, G88-F14, G88-F22, G88-F25, G88-F34);
    - 3 are portal exit aggregates (P-E.1, P-E.2a, P-E.3) that fail on the rows above.
  - The computed 90-day exit FAILs (E.1, E.2b, E.3, E.8 and now **E.11**, `exit-checkpoints.md`) are also outside §5.
  - **629.9 suite:** the E2E-LEGAL lifecycle leg FAILs with 4 failing tests, all outside §5:
    - G88-F48 HIGH (`day-60-interest.spec.ts:96`) and its cascade (`day-90-review.spec.ts:95`);
    - G88-F49 LOW (`day-07-14-work-trust.spec.ts:167`, `:306`).

    The other five legs equal their 628I baselines (`e2e-runs.md`).

**Open register: 63 open gaps + 1 hypothesis.** BLOCKER 0 · **HIGH 3** · **MEDIUM 15** · **LOW 45**, plus G88-F36 (LOW, HYPOTHESIS — not counted).

| Severity | product bug | scenario mismatch needing authorisation | process | ADR conflict (founder) | Total |
|---|---|---|---|---|---|
| HIGH | 3 (F12, F33, F48) | 0 | 0 | 0 | **3** |
| MEDIUM | 14 (F3, F16, F19, F23, F38, F39, F40, F43, F44, G-B8, M2, M4, M6, M13) | 1 (F32) | 0 | 0 | **15** |
| LOW | 32 (F2, F4, F5, F6, F9, F10, F13, F15, F17, F18, F20, F24, F26, F27, F29, F30, F31, F41, F42, F45, F46, F49, M5, M7, M9, M10, M11, M14, G-D10a, G-D10c, G-D17 residual, G-D20 residual) | 11 (F1, F7, F11, F14, F21, F22, F25, F28, F34, F37, F47) | 1 (F35) | 1 (G-D10b) | **45** |
| **Total** | **49** | **12** | **1** | **1** | **63** |

These counts leave out:
- **HYPOTHESIS:** G88-F36 (LOW).
- **Not open:** G88-F8 (retired alias), G88-M1 (closed by §5a row 1), G-D5 (DEFERRED — not observed).
- **Open register items cited by label (ruling O-5):** N-9 … N-12, and SB-11 (= §5b row 19).
- **Suite rows:** none left out. G88-F48 and G88-F49 are counted above; the `day-90-review.spec.ts:95` cascade has no own id.

The 629A + 629B new ids alone (F1 … F34, F8 retired) still read HIGH 2 · MEDIUM 5 · LOW 26, as in the README.

**629C changes to earlier ratings:**
- (1) **G88-F1 split.** The ten rows whose code shows only absence (6.5a, 12.2a, P-0.Ca, P-0.5a, P-0.7a, P-0.8a, P-3.2b, P-45.8a, P-60.6b, P-90.5b) move to **G88-F37**, "missing feature — design intent unproven" (ruling O-8; README review pass 4). Severity is unchanged (LOW).
- (2) **G-D10 residual split** into a / b / c (ruling O-10).
- (3) **G-D5 → DEFERRED — not observed.**
- (4) **@mention: "no gap" (README index, row "§5a rows 2, 4, 5, 6") → G88-F36 HYPOTHESIS.** The 629A reading "mention parsing exists … observed at 6.2b" holds only for a mention at the end of the text. The regex swallows the next word (row below).

No severity was raised or lowered.

**Gap ids:** 629C assigned **G88-F35 … G88-F49**. F35–F47 are builder 1's; F48–F49 come from the 629.9 suite run. The next free id is **G88-F50**.

## Register

| ID | Severity | Step(s) | Class | Observed (one line) | Owner | Evidence | Status |
|---|---|---|---|---|---|---|---|
| **G88-F12** | **HIGH** | 14.8b (FAIL); cited at P-E.3 | product bug (legal content) | The OTP "Voetstoots (As Is)" clause names the **firm** as the party not liable for defects. Re-verified at HEAD: `backend/src/main/resources/clause-packs/conveyancing-za-clauses/pack.json:33-37` — `customer.name` at `:34` "accepts the property…", **`org.name` at `:36`** "shall not be liable". The "Seller:" line is empty: `template-packs/legal-za/offer-to-purchase.json:54-55` renders `project.customFields.seller_name`, and `seller_name` exists only in three template packs (offer-to-purchase, deed-of-transfer, power-of-attorney-transfer) and in **no** field pack (`grep -rn seller_name backend/src/main/resources/field-packs` → 0). The OTP cannot be shown to a conveyancer as-is | new — follow-up phase (attribution: U10 626G associated the clause with the OTP and did not cover the binding; Seller line pre-existing) | days-08-21.md (14.8b); README index | open |
| **G88-F33** | **HIGH** (CONFIRMED by the orchestrator 2026-10-29) | P-60.5a, P-CP60.1 (FAIL); P-60.6a (PASS row, overwritten file); P-E.2a, P-E.3 | product bug (document storage — integrity + confidentiality) | Two Statements of Account for one matter and one period end share one S3 object. The Day-75 INTERNAL closure SoA (6 086 B) overwrote the Day-30 SoA shared to the portal (5 855 B); both `documents` rows carry the same `s3_key`, and Sipho's portal Download served the internal closure statement. Re-verified at HEAD: `StatementService.java:130-133` (`buildFileName(…)` → `s3Key = "org/" + tenantId + "/generated/" + fileName` → `storageService.upload`); `:384-388` `buildFileName` = slug + slugified matter name + **`periodEnd`**. The generic generator follows the same pattern: `GeneratedDocumentService.java:245-246` uploads to `"org/" + tenantId + "/generated/" + pdfResult.fileName()`, and `PdfRenderingService.java:329-334` names the file slug + entity name + **`LocalDate.now()`**. **Scope note (README open question):** statement keys collide on matter + period end, so re-issued statements and closure SoAs collide, not only same-day ones; generic templates collide on entity + generation day. The fix and its test must cover both. **68.2 vs P-60.5a:** W6b's 68.2 saw the shared Day-30 SoA before the overwrite, and that file now serves different content, so 68.2's PASS describes bytes that no longer exist | new — follow-up phase (attribution: StatementService / GeneratedDocumentService file naming, pre-Phase 88; exposed by the same-period closure SoA) | portal-90day.md (P-60.5a); logs/portal90-db-W8b-60.txt; logs/portal90-P-60.4-soa-download.pdf vs logs/days36-60-30.6-soa-download.pdf | open |
| G88-F1 | LOW | **27 FAIL rows.** 90-day (18): 0.5a, 0.15a, 0.28a, 0.29a, 0.41a, 1.3a, 3.8a, 4.1a, 8.1a, 12.1a, 14.7a, 22.1a, 23.3a, 35.1a, 30.3-soa-a, 30.5-soa-a, 45.10a, 85.18b. Portal (9): P-0.Aa, P-0.1a, P-0.2a, P-0.3a, P-0.4a, P-3.4a, P-21.1a, P-21.2a, P-E.5b. Also cited by P-E.3 (path half) | scenario mismatch needing authorisation (**design proven** — wording / behaviour drift outside §5) | Each row cites the file:line that shows the product's deliberate label, transition or route. The design-intent-unproven rows were moved to G88-F37 (629C re-rating 1) | new — founder §6 | README index row G88-F1 (file:line per row); day-00.md, days-01-07.md, days-08-21.md, days-22-35.md, days-36-60.md, days-76-90.md, portal-90day.md | open |
| G88-F2 | LOW | 0.27 / CP-AD-2; 90.4 | product bug (UI) | Team "Joined" column hard-coded "—" (`member-list.tsx:75,124`) | new — follow-up phase | day-00.md; days-76-90.md | open |
| G88-F3 | MEDIUM | 0.47b, 0.48b (FAIL) | product bug | Field-group membership is invisible, and the Edit Field Group picker is a silent no-op: the frontend sends `fieldDefinitionIds: []` (`custom-fields-content.tsx:281-287`, `FieldGroupDialog.tsx:74,163`), and the backend ignores membership (`UpdateFieldGroupRequest.java:8-13`, `FieldGroupService.java:111-140`) | new — follow-up phase | day-00.md | open |
| G88-F4 | LOW | 0.48a, 0.56, 4.5, 33.5; 80.2, 80.5a, 85.17 | product bug (layout) | Settings, task-sheet, profitability, My work and audit tables overflow their wrappers at 1440; columns are cut or behind an inner scroll. Visible in wow 5 | new — follow-up phase (attribution: 628B (628.5) container-query work did not cover these tables) | day-00.md, days-01-07.md, days-22-35.md, days-76-90.md | open |
| G88-F5 | LOW | 1.8 (side) | product bug (copy) | New Client labels the activation prerequisite "Address Line 1 (optional)" (`create-customer-dialog.tsx:440-441` vs `StructuralPrerequisiteCheck.java:65-70`) | new — follow-up phase | days-01-07.md | open |
| G88-F6 | LOW | 3.3b, 14.4 (side) | product bug (UI) | Matter Custom fields shows "Field Groups" twice (`matter-fields-tab.tsx:53-54` + `FieldGroupSelector.tsx:101-102`) | new — follow-up phase | days-01-07.md, days-08-21.md | open |
| G88-F7 | LOW | TEAM-sipho, TEAM-estate, TEAM-raf, TEAM-nomvula (unscored NOTE rows) | scenario mismatch needing authorisation (design proven: ADR-346 / 619 matter teams) | The scenario has no matter-team steps | new — founder §6 | days-01-07.md, days-08-21.md, days-22-35.md, days-61-75.md | open |
| G88-F8 | — | 4.6b | — | **Retired:** alias of G-B8. The row is kept so the numbering stays stable | — | days-01-07.md | superseded (by G-B8) |
| G88-F9 | LOW | 5.9 (side) | product bug (UI) | Disbursement detail shows the linked trust transaction as a raw UUID (`legal/disbursements/[id]/detail-client.tsx:270-284`) | new — follow-up phase | days-01-07.md | open |
| G88-F10 | LOW | 5.12 (side) | product bug (UI) | Matter Fees › Disbursements keeps a MATTER column reading "—" (`project-disbursements-tab.tsx:82-88`) | new — follow-up phase | days-01-07.md | open |
| G88-F11 | LOW | 12.3a (FAIL) | scenario mismatch needing authorisation (design proven) | Account balance R 53 750,00 ≠ the doc's R50,000 because of §5 row 5 (Sipho keeps R 3 750). Cashbook = one sum over the account (`TrustTransactionRepository.java:106-126`); client-ledger total = sum of ledger cards (`ClientLedgerCardRepository.java:28-31`) | new — founder §6 (G88-M16 family) | days-08-21.md | open |
| G88-F13 | LOW | 15.1 (side) | product bug (copy) | The fee-estimate empty state / help promise a fixed vs T&M choice that the dialog does not offer (`empty-states.json:70`, `help.json:26-27`) | new — follow-up phase | days-08-21.md | open |
| G88-F14 | LOW | 15.3b (FAIL) | scenario mismatch needing authorisation (design proven) | Doc "~R11,000" vs R 9 850,00 = Σ(hours × the scenario's own rates) (`TimeEntryRepository.java:372-387`, `ProjectBudgetService.java:168-179`) | new — founder §6 | days-08-21.md | open |
| G88-F15 | LOW | 33.2 (side); 80.2, 80.4 | product bug (rounding) | Matter Profitability Margin % is rounded twice (`ReportService.java:437-441` 2 dp HALF_UP, then `toFixed(1)`): RAF 59.0 % vs 58.9 % | new — follow-up phase | days-22-35.md, days-76-90.md | open |
| G88-F16 | MEDIUM | 30.4-soa / 30.6-soa (side); P-60.5b (FAIL); 75.9 | product bug (document content) | SoA, closure letter and closure SoA have no firm letterhead block; the SoA prints blank "VAT Reg:" / "File reference:" labels (`template-packs/legal-za/statement-of-account.json:16,78`; `matter-closure-letter.json:6,11,111-135`) | new — follow-up phase (attribution: 626D (626.8) reconciled fee-note + engagement-letter letterheads, not the SoA / closure letter) | days-36-60.md, days-61-75.md, portal-90day.md | open |
| G88-F17 | LOW | 38.2 (side); 45.9, 58.1 | product bug (UI copy) | Fee-note audit rows for created / approved / sent all read "Fee note" (`AuditEventTypeRegistry.java:124-128`, wildcard `invoice.*`) | new — follow-up phase (attribution: 625C (D12)) | days-36-60.md | open |
| G88-F18 | LOW | 45.6 (side) | product bug (UI layout) | Write-off dialog close "×" drawn outside the card (`components/ui/dialog.tsx:56,69-76`; `disbursement-write-off-dialog.tsx:104-105`) | new — follow-up phase (attribution: none — shared dialog wrapper) | days-36-60.md | open |
| G88-F19 | MEDIUM | 68.2 (side) | product bug (portal read model) | Portal matter Tasks lists only edited tasks (2 of 9): template instantiation publishes no `PortalTaskCreatedEvent` (`ProjectTemplateService.java:589-609`; projection fed only by `TaskService.java:1082`) | new — follow-up phase (attribution: none — template path predates Phase 88) | days-61-75.md; logs/days61-75-db-68.2-portal-readmodel.txt | open |
| G88-F20 | LOW | 68.2 (side); P-60.2 | product bug (client-facing copy) | Portal task Status badge prints raw "OPEN" / "DONE" (`portal/components/status-badge.tsx:47-49,65-67`) | new — follow-up phase (attribution: 624H humanised the matter badge only) | days-61-75.md, portal-90day.md | open |
| G88-F21 | LOW | APPROVE-72 (NOTE) | scenario mismatch needing authorisation (design proven) | No second-member approval step for the 72.1 trust PAYMENT; the recorder cannot self-approve (`TrustTransactionService.java:784-790`) | new — founder §6 | days-61-75.md | open |
| G88-F22 | LOW | 75.1a (FAIL) | scenario mismatch needing authorisation (design proven) | 75.1 completes Sipho's matter, but the file never resolves its 9 template tasks; Complete refuses while tasks are open (`ProjectService.java:924-933`) | new — founder §6 | days-61-75.md | open |
| G88-F23 | MEDIUM | APPROVE-75, PRE-75-refund (side); P-CP21.2 | product bug (trust accounting UI) | Client ledger "Running Balance" is wrong: REFUND is treated as an inflow, and the running balance walks the rows in the wrong order (`trust-accounting/client-ledgers/[clientId]/page.tsx:44-45,190-205`; `transactions/page.tsx:48-49`; `ClientLedgerService.java:51,157`) | new — follow-up phase (attribution: Epic 448B (#945)) | days-61-75.md; logs/days61-75-db-APPROVE-75.txt | open |
| G88-F24 | LOW | 80.2 (side); 88.2 / 88.3 (wow 7 frame) | product bug (UI copy) | Activity feed prints raw "status to DONE" / "IN_PROGRESS" (`ActivityMessageFormatter.java:421-431`) | new — follow-up phase (attribution: U5 formatter, 625A/625B) | days-76-90.md | open |
| G88-F25 | LOW | 80.5b (FAIL) | scenario mismatch needing authorisation (design proven) | My work Upcoming covers tomorrow … today + 14 (`my-work/page.tsx:148-150`, `todays-diary.ts:58-69`, `diary-types.ts:114`); the walk's court date is D+45 | new — founder §6 | days-76-90.md | open |
| G88-F26 | LOW | 85.2 (side) | product bug (audit facets) | Actor facet groups by `(actor_id, actor_type)` (`AuditEventRepository.java:467-482`), but the list filters by `actorId` only (158 vs 162; "System 4" carries Thandi's id) | new — follow-up phase (attribution: 625C/625D audit facets) | days-76-90.md | open |
| G88-F27 | LOW | 90.1b (FAIL; scores E.8) | product bug (terminology) | Disbursement detail "**Invoiced** · Yes" (`legal/disbursements/[id]/detail-client.tsx:291`); the ADR-357 guard regex `\b(Invoices?)\b` misses "Invoiced" | new — follow-up phase (attribution: U21 628A–628D census + guard) | days-76-90.md; screens/90.1b-disbursement-invoiced-yes.png | open |
| G88-F28 | LOW | PRE-P-request (NOTE) | scenario mismatch needing authorisation | Portal prerequisites say the firm lifecycle produces "info requests"; the 90-day file never sends one (`portal-client-90day-keycloak.md:39-41`) | new — founder §6 | portal-90day.md | open |
| G88-F29 | LOW | P-0.9a (FAIL); P-3.4b | product bug (client-facing copy) | Portal request list / detail print raw "SENT" / "IN_PROGRESS" / "SUBMITTED" (`requests/[id]/page.tsx:99,190`, `requests/page.tsx:93`) | new — follow-up phase (attribution: 628G walk / U21 census did not cover portal request status copy) | portal-90day.md | open |
| G88-F30 | LOW | P-0.8a (FAIL, with G88-F37) | product bug (UI) | Portal request detail h1 = `detail.matterName`, empty for a client-level request (`requests/[id]/page.tsx:92-93`, `requests/page.tsx:89`) | new — follow-up phase | portal-90day.md | open |
| G88-F31 | LOW | P-21.3a (FAIL); P-21.1b | product bug (client-facing raw identifiers) | Portal trust page "Matter d42337ad" + raw REFUND / PAYMENT / DEPOSIT (`portal/lib/api/trust.ts:107-110`, `balance-card.tsx:30`, `transaction-list.tsx:181`) | new — follow-up phase (attribution: 628G walk / U21 census did not cover portal trust pages) | portal-90day.md | open |
| G88-F32 | MEDIUM (CONFIRMED) | P-45.6b, P-CP45.3b, P-E.9b (FAIL) | scenario mismatch needing authorisation (missing feature — design intent unproven) | No payment receipt: the paid fee note's only download is the pre-payment PDF (`PortalInvoiceController.java:114-120`); §5a row 17 kept "receipt available for download" | new — founder §6 | portal-90day.md; logs/portal90-db-W8b-45.txt | open |
| G88-F34 | LOW | P-90.1 (FAIL); P-90.2, P-CP90.1, P-E.8d (DEFERRED) | scenario mismatch needing authorisation (missing feature — design intent unproven) | No final / quarter digest exists; only the weekly Monday digest (`PortalDigestScheduler.java:105,303`) | new — founder §6 | portal-90day.md | open |
| G88-F35 | LOW (ruling O-11) | 629.10 (E.11e) | process | 5 code PRs have no committed verify marker: #1671, #1672 (backend), #1673, #1677 (portal), #1676 (frontend). Each passed the live hook: the transcripts show the marker written to the main checkout's `.claude/markers/` minutes before the merge. The post-merge docs commit, made from a `worktree-docs-624X` checkout, never `git add`-ed it, so it cannot be audited (root CLAUDE.md §8). **Mitigation:** the 624I gate markers on main `bd32f07ff` contain all five (BE 5743 tc / 0 failing / 26 skip; FE 417/3150/2; PORTAL 44/233; committed in `12de7d3e4`). No after-the-fact markers were written | process — orchestrator (`/epic_v2` docs-commit step) | markers-audit.md § "Process gaps"; logs/629C-markers.tsv; logs/629C-o11-merge-transcripts.txt | open |
| G88-F36 | LOW (PROPOSED) | 6.2b, 33.1-ab (both PASS — the mention was at the end of the text) | **HYPOTHESIS (unreproduced)** — product bug if reproduced | The @mention regex swallows the next word. `NotificationService.java:817` `MENTION_PATTERN = "@(\\w+(?:\\s+\\w+)?)"` is greedy. Each `find()` (`:823-828`) yields one token, e.g. "@Bob please review" → `"bob please"`. A member matches only on an exact first-name or full-name token (`:838-846`), so an un-assigned mentionee who has not commented before gets no notification; other routes still notify assignees, uploaders and prior commenters. No test references `resolveMentionedMemberIds` / `MENTION_PATTERN` (`grep -rln` over `backend/src/test/java` → 0). Supersedes the README's "@mentions: no gap" note, which tested only end-of-text mentions. **Not counted** in the exit arithmetic (O-6) | new — follow-up fix PR (red-first integration test) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java:817,823-828,838-846`; days-01-07.md 6.2b; days-22-35.md 33.1-ab | hypothesis |
| G88-F37 | LOW | **10 FAIL rows** (moved from G88-F1; 629C re-rating 1): 6.5a, 12.2a; P-0.Ca, P-0.5a, P-0.7a, P-0.8a, P-3.2b, P-45.8a, P-60.6b, P-90.5b | scenario mismatch needing authorisation (**missing feature — design intent unproven**) | The code shows only that the feature is absent: 6.5 no document label (`DocumentController.java:116-123` `UploadInitRequest(fileName, contentType, size)`); 12.2 no deposit source field (`TrustTransactionService.java:92-98` `RecordDepositRequest(clientId, matterId, amount, reference, description, transactionDate)`; `RecordDepositDialog.tsx:126-213`); P-0.Ca no "Client contact" label (the create-UI half is design-proven, `PortalContactAutoProvisioner.java:42-44`); P-0.5a / P-0.7a Home count tiles, not item lists (`home/page.tsx:57-97,81-83,153-170`); P-0.8a no requested-by / due date (`requests/[id]/page.tsx:88-108`); P-3.2b no upload progress (`:210`); P-45.8a no Due/Paid filter (`invoices/page.tsx`); P-60.6b no download-success indicator (`document-list.tsx:32-34`); P-90.5b activity entries carry no deep link (`activity/page.tsx:133-152`). Founder decides: build, or amend the scenario | new — founder §6 | days-01-07.md 6.5a; days-08-21.md 12.2a; portal-90day.md (per-row file:line) | open |
| G88-F38 (SB-1) | MEDIUM | suite: lifecycle skip `frontend/e2e/tests/legal-lifecycle/day-07-14-work-trust.spec.ts:292` (628E-BLOCKER fixme) | product bug (crash on a core legal check) | The conflict-check result crashes on an unlinked adverse-party match: `components/legal/conflict-check-result.tsx:141` calls `match.relationship.replace(…)`, and `relationship` is null (read at HEAD) | new — follow-up phase (attribution: reproduced by 628E) | fixme text at the spec line; `qa_cycle/evidence/phase88-rewalk/628I/gate.md:23-31` | open |
| G88-F39 (SB-2) | MEDIUM | suite: lifecycle skip `day-60-interest.spec.ts:160` (628E-BLOCKER) | product bug | A DRAFT interest run cannot be approved by a second member: the wizard is session-local (`components/trust/InterestRunWizard.tsx:173`), the runs table has no actions (`trust-accounting/interest/page.tsx:135-186`), and the backend blocks self-approval (`InterestService.java:455`) | new — follow-up phase (attribution: reproduced by 628E) | fixme text; 628I/gate.md:23-31 | open |
| G88-F40 (SB-3) | MEDIUM | suite: lifecycle skip `day-90-review.spec.ts:123` (628E-BLOCKER) | product bug (Section 35 trust reports absent) | No trust report definitions are seeded: `TrustReportPackSeeder.java:38` `seedForTenant` has no caller (grep at HEAD: only its own class references it). This is the 627E N-2 PARTIAL remainder | new — follow-up phase (attribution: 627E (N-2 PARTIAL), reproduced by 628E) | fixme text; 628I/gate.md:23-31 | open |
| G88-F41 (SB-4) | LOW | suite: default skip `customers/customer-lifecycle.spec.ts:158` (628F-BLOCKER) | product bug | The firm UI has no DORMANT→OFFBOARDING transition: the header "Edit" has no handler, and `LifecycleTransitionDropdown` has been orphaned since #1395 (`client-header-card-with-lifecycle.tsx:70`) | new — follow-up phase (attribution: reproduced by 628F) | fixme text; 628I/gate.md:23-31 | open |
| G88-F42 (SB-5) | LOW | suite: default skip `customer-lifecycle.spec.ts:179` (628F-BLOCKER) | product bug | The header "Complete Offboarding" button has no onClick (`client-header-card-with-lifecycle.tsx:70`; `clients/[id]/page.tsx:434-470`) | new — follow-up phase (attribution: reproduced by 628F) | fixme text | open |
| G88-F43 (SB-6) | MEDIUM | suite: default skip `customers/customer-data-protection.spec.ts:48` (628F-BLOCKER) | product bug (POPIA data export) | Data export "Export Ready" shows "files · NaN undefined", and "Download Archive" has no href: the API answers `ExportResult(exportId, status, estimatedFiles)` (`datarequest/ExportResult.java:6`), which the UI does not read | new — follow-up phase (attribution: reproduced by 628F) | fixme text | open |
| G88-F44 (SB-7/8) | MEDIUM | suite: default skips `customer-data-protection.spec.ts:76` and `:107` (628F-BLOCKER; one defect, two tests) | product bug (POPIA anonymisation) | The Anonymize preview crashes the client page (TypeError reading `portalContacts`): `anonymize-customer-dialog.tsx:148-154` reads `preview.affectedEntities.*` (`lib/types/data-protection.ts:100-114`), a shape the API does not return | new — follow-up phase (attribution: reproduced by 628F) | fixme text | open |
| G88-F45 (SB-9) | LOW | suite: default skip `settings/roles-capabilities.spec.ts:54` (628F-BLOCKER) | product bug (layout) | The Create Role dialog is 839 px tall at 1280×720 and does not scroll (`components/roles/role-dialog.tsx:105`, `DialogContent` with no max-h / overflow). Same class as the shared-dialog issues (`components/ui/dialog.tsx:56-70`) | new — follow-up phase (attribution: reproduced by 628F) | fixme text | open |
| G88-F46 (SB-10) | LOW | suite: default skip `documents/document-generation.spec.ts:86` (628F-BLOCKER) | product bug | The client overflow "Generate Document" pushes `?action=generate-document`, and nothing reads it (`client-overflow-menu.tsx:120`; the client page no longer mounts `GenerateDocumentDropdown`, Epic 560A) | new — follow-up phase (attribution: reproduced by 628F) | fixme text | open |
| G88-F47 | LOW | E.11c | scenario mismatch needing authorisation | The scenario's `cd frontend && pnpm typecheck` names a script that does not exist (`frontend/package.json:5-21`: dev, build, start, lint, format, format:check, test, test:watch, test:e2e*); 617C found the same (`qa_cycle/evidence/phase87-capstone/exit-checkpoints.md:22`). `next build` does type-check — `frontend/next.config.ts` sets no `typescript.ignoreBuildErrors` — but with `experimental.useTypeScriptCli: false` (`:11-16`) it uses the API checker, which skips `__tests__/` and `*.test.*` / `*.spec.*`. So it is a partial, **unverified substitute** for a whole-project `tsc --noEmit` | new — founder §6 (amend E.11c or add the script) | `frontend/package.json:5-21`; `frontend/next.config.ts:11-16` | open |
| G-B8 (617C) | MEDIUM | 4.6b (FAIL) | product bug (incomplete fix) | The feed shows the acceptance events but no source-PORTAL marker (DTO `actorName` only, `activity-feed-client.tsx:38-39`) | new — follow-up phase (attribution: U5 625A/B fixed the events, not the source half) | days-01-07.md | open |
| G88-M2 | MEDIUM | TEAM-sipho, TEAM-estate, TEAM-raf, TEAM-nomvula | product bug | The matter Team tab shows org roles only, with no lead marker | new — follow-up phase (attribution: mid `gaps.md` routed it to 628A, now Done) | days-01-07.md, days-08-21.md, days-22-35.md, days-61-75.md | open |
| G88-M4 (residual) | MEDIUM | 5.12, 5.14; 45.5 | product bug (layout) | The page no longer overflows, but the disbursement tables (1249/1257 px) hide BILLING/ACTIONS behind an inner scroll at 1440 | new — follow-up phase (attribution: 628B (628.5) fixed the page overflow, not this residual) | days-01-07.md, days-36-60.md | open |
| G88-M5 | LOW | 6.1, 13.1; 62.1, 70.1 | product bug | A member's visit writes `security.access_denied` noise (`/time-summary/by-member`) | new — follow-up phase (founder to schedule; mid `gaps.md:66`) | days-01-07.md, days-08-21.md, days-61-75.md | open |
| G88-M6 | MEDIUM | 6.3b (FAIL) | product bug | The comment notification lands on the task list, not on the comment | new — follow-up phase (founder to schedule; mid `gaps.md:67`) | days-01-07.md | open |
| G88-M7 | LOW | 3.5 | product bug | The client Matters list STATUS cell is blank for an Active matter | new — follow-up phase (founder to schedule; mid `gaps.md:68`) | days-01-07.md | open |
| G88-M9 | LOW | 38.1/38.2, 45.4 | product bug | Fee-note Generated Documents / Audit trail panels stay stale after Send until reload | new — follow-up phase (attribution: mid `gaps.md:70` routed it as a 626F (626.14) add-on, which did not land) | days-36-60.md | open |
| G88-M10 | LOW | 45.3, 47.2; P-45.2 | product bug | The tariff line is stored with `invoice_lines.sort_order` 0, so it prints second, between time lines, on the PDF and the portal (visible in wow 6) | new — follow-up phase (founder to schedule; mid `gaps.md:71`) | days-36-60.md, portal-90day.md | open |
| G88-M11 | LOW | 55.1 | product bug (known follow-up) | A manual part payment cancels the open mock payment session, and no link is issued for the balance | new — follow-up phase (attribution: 624H shipped the portal half code/test-only; the firm-app + backend half was routed to a new unit, mid `gaps.md:72`) | days-36-60.md | open |
| G88-M13 | MEDIUM | 5.4, 45.10a, 61.1, 85.6 (side) | product bug (pre-existing stub) | The matter Overview "OUTSTANDING FEE NOTES" (and "Trust balance") tile reads "--" even when a SENT fee note is unpaid (`components/projects/kpi-dashboard.tsx:199-213`; visible in wow 4) | new — follow-up phase (founder to schedule; mid `gaps.md:53,73`) | days-01-07.md, days-36-60.md, days-61-75.md, days-76-90.md | open |
| G88-M14 | LOW | PRE-75-tasks | product bug (behaviour half) | The matter Tasks tab shows "No tasks yet" once every task is Done; the filter chips are gone. The "project" copy half is closed | new — follow-up phase (founder to schedule; mid `gaps.md:74`) | days-61-75.md | open |
| G-D10a (617C residual, ruling O-10) | LOW | 90.1b (FAIL; **counts against E.8**) | product bug (terminology) | Firm › Audit trail prints the stored event key in monospace under each legal label (`frontend/app/(app)/org/[slug]/settings/firm/_tabs/audit-log-client.tsx:1025` `{event.eventType}` under `{event.label}` at `:1024`). This contradicts `adr/ADR-357-…md:44` (event-key literals "are never user-visible") and `adr/ADR-342-…md:33` ("users never see it"); no file:line shows the row key is meant for users. Introduced by 616A #1632 (Phase 87) | new — follow-up phase (attribution: Phase 87 616A; not fixed by U6 625C/625D) | days-76-90.md 90.1b; screens/90.1b-audit-log-event-keys.png | open |
| G-D10b (617C residual, ruling O-10) | LOW | 90.1b (listed) | ADR conflict — founder decision | The Event type combobox shows the key next to the label (`audit-log-client.tsx:322` current value, `:342` list item). This is deliberate: 625D's "exact key" search (task row `:654`). It conflicts with ADR-357:44 / ADR-342:33. Founder: keep the key as a search affordance and amend ADR-357, or hide it | founder (amend ADR-357 or hide the key) | days-76-90.md 90.1b | open |
| G-D10c (617C residual) | LOW | 90.1b (listed) | product bug (terminology) | Settings › Matters › Naming placeholder and legend show the wire token `{customer.name}` (`frontend/components/project-naming/project-naming-settings.tsx:68,86`). It is a stored pattern token, resolved by `ProjectNameResolver.java:32` `replace("{customer.name}", …)`, so the fix needs a `{client.name}` alias plus a reconcile of stored patterns (ADR-356-shaped) | new — follow-up phase (attribution: 617C G-D10; U21 628A–D did not cover it) | days-76-90.md 90.1b; screens/90.1b-naming-customer-name-token.png | open |
| G-D17 (617C, residual) | LOW | 32.1 (N/A row, cited) | product bug (terminology) | The matter/client Fees sub-tab is still labelled "Tariffs" for member rate overrides (`frontend/lib/constants/matter-tab-groups.ts:57`, `client-tab-groups.ts:51`). The settings half is closed ("Rates") | new — follow-up phase (attribution: 628B renamed only the settings tab) | day-00.md, days-22-35.md | open |
| G-D20 (617C, residual) | LOW | 23.6 | product bug (layout) | The Diary week card title is still truncated ("Gauteng High…") | new — follow-up phase (attribution: U21 628B improved the card, not the truncation) | days-22-35.md | open |
| G-D5 (617C) | — | 80.5b (related) | — | My work's Upcoming band exists (627F #1698; `diary-types.ts:114` `UPCOMING_DIARY_DAYS = 14`). Neither the walk's court date (D+45) nor the lifecycle spec's ("30 days out", `day-07-14-work-trust.spec.ts:119`) falls inside the window, so G-D5 is **neither observed closed nor observed open**. Vitest coverage exists (`__tests__/dashboard/my-work-dashboard.test.tsx` and others) but is not a PASS (root CLAUDE.md §3). The related scenario mismatch is G88-F25 | — (re-observe with a court date inside 14 days) | days-76-90.md 80.5 | **DEFERRED — not observed** |
| G88-M1 | LOW | 0.Aa, M.6a | scenario mismatch | 0.A drop-schema name / M.6 `-d docteams` | — | session-0.md | **closed by §5a row 1** (applied #1712 `45dcc7a6f`; O-12) |
| **G88-F48** | **HIGH** (orchestrator ruling R-2; above the O-4 "new failure → MEDIUM" default because it is the trust money path — O-4: money → HIGH) | suite: lifecycle `frontend/e2e/tests/legal-lifecycle/day-60-interest.spec.ts:96` (FAIL); its cascade `day-90-review.spec.ts:95` (FAIL, no own id) | product bug (backend — trust money path / trust compliance) | Server-dated trust postings take `LocalDate.now()` in the JVM default zone. That is UTC in the containers: no `TZ`, no `user.timezone`, no firm-timezone setting (diagnosis §0; `docker exec … date` UTC, `TZ=` empty, re-checked by builder 2). The client-dated flows carry the browser-local date instead: `RecordDepositDialog.tsx:48-57`, `RecordPaymentDialog.tsx:56`, `RecordRefundDialog.tsx:56`, `RecordTransferDialog.tsx:45`. **Server-dated sites (re-read at HEAD):** fee transfer `TrustTransactionService.java:622` (the Record Fee Transfer dialog has no date field); reversal postings `:1114`, `:1139`, `:1264`, `:1281`, `:1328`, `:1334`; `TrustAccountService.java:147` (opened-date default), `:244` (closed date); `TrustInvestmentService.java:243`, `:289`. Between 00:00 and 02:00 SAST, a fee transfer is therefore dated the previous day. **Observed in run 1 (00:01–00:03 SAST, 2026-10-30):** Moroka's DEPOSIT R 250 000 is dated 2026-10-30, and its FEE_TRANSFER R 8 500 is dated 2026-10-29. Moroka's balance as of 2026-10-29, under the product's own `calculateClientBalanceAsOfDate` rules (`TrustTransactionRepository.java:135-153`), is **−8 500,00**: a negative client trust balance for one day. **Interest misallocated:** `InterestService.java:189` gives 31 days; the transaction-weighted walk is at `:245-275`; avg `:280-281` and gross `:282-286`. Balance-days = 0 × 29 + (−8 500) × 1 + 241 500 × 1 = 233 000, so avg daily = 233 000 / 31 = **R 7 516,13** and gross = 7 516,13 × 0.065 × 31 / 365 = **R 41,49**. The expected values were R 7 790,32 / R 43,01 (241 500 for one day). QuickCollect has no server-dated posting and matched exactly (R 1 451,61 / R 8,01): it is the control. Window dependency: **confirmed by the diagnostic re-run** (fresh stack, 00:33–00:37 SAST 2026-10-30, still inside the window; one change: Playwright host `TZ=UTC`): `:96` and its cascade `:95` PASSED, lifecycle 101/0/3 (`e2e-runs.md` § "Diagnostic re-run"; `logs/629C-diag-lifecycle.txt`). The defect is a browser-zone vs container-UTC mismatch; real SA browsers are SAST, so the finding stands unchanged at HIGH. Red-first recipe for the fix PR: a Playwright host zone ahead of UTC whose date has already rolled over (e.g. SAST 22:00–24:00Z) | new — follow-up fix PR (firm-timezone clock; red-first test) | e2e-runs.md § "Failures → gaps" 3; logs/629C-lifecycle.txt; logs/629C-lifecycle-date-boundary-diagnosis.md (Failure 3); logs/629C-lifecycle-db-reverify.txt; logs/629C-diag-lifecycle.txt | open |
| G88-F49 | LOW (orchestrator ruling R-2; below the O-4 MEDIUM default because it is display-only: the stored data is correct, and the view corrects itself at 02:00 SAST) | suite: lifecycle `day-07-14-work-trust.spec.ts:167` and `:306` (FAIL) | product bug (frontend server) | My Work and the dashboard compute "today" on the Next.js server, in its process zone (UTC): `app/(app)/org/[slug]/my-work/page.tsx:109` `new Date().toLocaleDateString("en-CA")` feeds `/api/my-work/time-entries?from=${today}&to=${today}` (`:111-114`); the week range `resolveMyWorkDateRange` is at `:45-71`; `dashboard/page.tsx:92` does the same. Log Time defaults to the browser date (`components/tasks/log-time-dialog.tsx:58`). An entry logged between 00:00 and 02:00 SAST is therefore missing from "Today", and "Time Today" reads 0m. **Observed in run 1:** the entries are stored as 2026-10-30 (created 2026-10-29 22:01Z). The error-context shows "Time Today 0m / 8h" and "No time recordings yet", while the weekly strip shows "Fri: 1.0 hours logged" (`:167`) and "Fri: 2.3 hours logged" (`:306`). The Upcoming link `from=2026-10-30` is `addDays(today, 1)` (`my-work/todays-diary.ts:65`), which puts the server's "today" at 2026-10-29. Window dependency: **confirmed by the diagnostic re-run** (same run, Playwright host `TZ=UTC`): `:167` and `:306` PASSED (`e2e-runs.md` § "Diagnostic re-run"; `logs/629C-diag-lifecycle.txt`). The finding stands unchanged at LOW: real SA browsers are SAST | new — follow-up phase | e2e-runs.md § "Failures → gaps" 1–2; logs/629C-lifecycle.txt; logs/629C-lifecycle-date-boundary-diagnosis.md (Failures 1–2); logs/629C-lifecycle-db-reverify.txt; logs/629C-diag-lifecycle.txt | open |

**629.9 suite rows — notes** (builder 2; source `e2e-runs.md`; orchestrator rulings R-1 … R-5).
- **Lifecycle leg: FAIL.** Run 1 is the scored result (R-1): 82 passed / 4 failed / 1 skipped / 17 did not run, against the 628I baseline of 101/0/3 on byte-identical code. The four failing titles map as follows:
  - `day-07-14-work-trust.spec.ts:167` and `:306` → G88-F49;
  - `day-60-interest.spec.ts:96` → G88-F48;
  - `day-90-review.spec.ts:95` → **cascade of G88-F48**, with no own id. It threw `Run-state value "siphoFeeNote2" was not recorded`. That value is recorded only at `day-60-interest.spec.ts:269`, inside test `:252`, which is in the same `test.describe.serial` block (`:84`) as the F48 failure and so did not run.
- **Carried 624I item `day-90-review.spec.ts:95`: dropped from the carried set.** Run 1 did not exercise its fee-notes-list assertion (cascade of G88-F48, missing `siphoFeeNote2`). The diagnostic re-run (Playwright host `TZ=UTC`, `e2e-runs.md` § "Diagnostic re-run") PASSED it, so the run-1 failure is fully explained by the cascade. Disposition cites both runs (`logs/629C-lifecycle.txt`, `logs/629C-diag-lifecycle.txt`). The former-carried `day-90-review.spec.ts:192` also PASSED there and is dropped on the same basis, so **none of the former carried six remains carried**.
- **17 run-1 did-not-run tests: all ran in the diagnostic re-run** (`TZ=UTC`, lifecycle 101/0/3). 15 are **PASS (diagnostic run, TZ=UTC)**: `day-07-14-work-trust.spec.ts:330`; `day-60-interest.spec.ts:193`, `:200`, `:229`, `:252`, `:278`, `:299`; `day-90-review.spec.ts:149`, `:160`, `:173`, `:192`, `:218`, `:232`, `:256`, `:269`. The other 2 are named `628E-BLOCKER` fixmes and were skipped as expected: `day-60-interest.spec.ts:159` (fixme `:160`, G88-F39) and `day-90-review.spec.ts:118` (fixme `:123`, G88-F40). These are diagnostic-run results; run 1 remains the scored lifecycle result (R-1).
- **Not a harness defect** (R-2). The spec dates entries with host-local `isoDate()` (`frontend/e2e/helpers/legal-depth-ii.ts:94-100`), which mirrors the product's own browser defaults (`log-time-dialog.tsx:58`, `RecordDepositDialog.tsx:48-57`), so a real SA user's browser produces the same dates. The defect is the product's server-side "today" disagreeing with its own client defaults. Pinning Playwright `timezoneId` would mask F48 / F49, not fix them.
- **The other five legs equal their 628I baselines:** depth-ii 39/0/4, default 159/0/18/0, login-bare 1/0, isolation 2/0, `test:e2e:portal` 32/0/1. Each has 0 failed, 0 flaky and 0 soft-skips, and each skip set equals its named 628I set (ruling O-14), so these legs add no gap. The lifecycle skip set differs only by the cascade: 1 named fixme skipped, and 2 named fixmes in "did not run". In the diagnostic re-run it equals the named set exactly (3 skipped: `day-07-14-work-trust.spec.ts:284`, `day-60-interest.spec.ts:159`, `day-90-review.spec.ts:118`).

## Inside register §5 — the 12 scored FAIL rows that do not count against the exit test

**O-12 reconciliation (ruling O-3).** 629A scored seven rows against step text that #1712 (`45dcc7a6f`) amended later the same day under §5a rows 1, 2, 4, 5 and 6. Those rows are recorded here as **inside §5a, authorised and applied**, and the 629A day files are left untouched as historical evidence. Each product-path `b` row passed, except 6.3b, which stays a FAIL on G88-M6.

| Row | File | §5a row (applied #1712) | Product-path row |
|---|---|---|---|
| 0.Aa | session-0.md | row 1 (G88-M1 → closed) | — (step text only) |
| M.6a | session-0.md | row 1 (G88-M1 → closed) | — |
| 3.3a | days-01-07.md | row 2 | 3.3b PASS |
| 6.2a | days-01-07.md | row 4 | 6.2b PASS |
| 6.3a | days-01-07.md | row 5 | 6.3b FAIL → G88-M6 (counted outside §5) |
| 14.13a | days-08-21.md | row 6 | 14.13b PASS |
| 33.1-aa | days-22-35.md | row 4 | 33.1-ab PASS |

**Register §5b (documented product proposals; walkers cite them, ruling 2026-10-29):** P-75.1, P-90.4d (§5a row 19, portal mark-read / deadlines), and P-85.1, P-85.2, P-90.4e (§5a row 20, read-only portal profile) — all in `portal-90day.md`. The Day 75 portal fixme `portal/e2e/tests/portal-client-90day/day-45-75.spec.ts:166` (628G-BLOCKER, the dormant deadline sync) is the suite face of §5b row 19. It is cited as **SB-11 → §5b row 19**, with no new id (ruling O-5).

## Cited open register items (not walk gaps; ruling O-5)

These are listed by label and not counted in the summary.
- **N-9…N-12** (`tasks/phase88-gap-register.md`, "Newly surfaced latent defects"): the 628I grep residuals outside the R8 census (POPIA processing-register seed copy; `TrustBoundaryGuard.java:61`; the "Unknown Project" fallbacks; `UpdateStatusActionExecutor.java:72`).
- **Suite skips that are not product gaps:**
  - `frontend/e2e/tests/legal-depth-ii/day-30-45-soa-writeoff.spec.ts:292` `FIXME(depth-ii:e2e-s3-presigner-unreachable)` — an E2E environment limitation, since the presigned host is unreachable from the frontend container;
  - the 3 opt-in null-prefetch probes (`null-prefetch-probe.spec.ts:71`);
  - the 8 `admin-audit-30day/admin-audit-lifecycle.spec.ts` 510A scaffolds (`test.skip(true, …)`, pre-Phase 88);
  - the Keycloak-only `auth/auth-change-password.spec.ts:38`;
  - the 2 env-gated `portal/portal-data-isolation.spec.ts:33` tests.

## Routed / not decided here

| Item | Routed to | Note |
|---|---|---|
| **E.2b** reading ("without visual regression") | **629D** | README open question. 629C only records it. The four frame defects (G88-F4, G88-F24, G88-M13, G88-M10) pre-date the walk |
| G-D10b audit combobox key | founder | amend ADR-357 / ADR-342, or hide the key |
| G88-F36 @mention | follow-up fix PR | reproduce red-first, with an integration test, before fixing (root CLAUDE.md §4) |
| Every "scenario mismatch needing authorisation" row (F1, F7, F11, F14, F21, F22, F25, F28, F32, F34, F37, F47) | founder §6 | an agent may not amend `qa/testplan/demos/*.md` (root CLAUDE.md §6) |
| 624I carried item "extend `login-bare.spec.ts` comments to name the per-contact cap" | closed by citation (ruling O-1) | already done by 628G #1709: `portal/e2e/tests/login-bare.spec.ts:13-22` names both caps, and the 429 message `:100-104` correctly names only the per-email cap |
| `7ea0175ac` (direct commit to main, §5a row 14) | process observation (ruling O-7) | `markers-audit.md` § "Commits without a PR" |
| Magic-link caps (README open question) | answered — no gap | 629.9 records the observed budget in `e2e-runs.md` |

## README "Open questions carried to 629C" — dispositions

| Question | Disposition |
|---|---|
| G-D10 event keys (E.8) | **Decided (O-10):** G-D10a counts against E.8 (LOW product bug), G-D10b is an ADR conflict for the founder, G-D10c is the naming token. E.8 stays FAIL on G88-F27 + G-D10a |
| E.2b reading | **Routed to 629D** |
| O-12 reconciliation | **Done here:** 7 rows inside §5a (table above); G88-M1 closed; day files untouched |
| G-D5 | **DEFERRED — not observed** (row above) |
| G88-F33 scope | **Recorded in the F33 row:** statements collide on matter + period end, generic templates on entity + generation day; the fix and its test must cover both |
| 68.2 vs P-60.5a | **Recorded in the F33 row** |
| Magic-link caps | **Answered:** handled by the 629.9 chain; no gap |
| G88-F1 "design intent unproven" rows (README review pass 4) | **Classified:** moved to G88-F37 |

## Closed — observed PASS in 629A / 629B

These are cited here so that 629D sees the whole picture. Each was observed by a walker and is not inferred from tests.

| ID | Closed at | Evidence |
|---|---|---|
| G-C4 | 30.4 / 30.6 (SoA preview + Download PDF) | days-36-60.md |
| G-C10 | 36.4 (narrative fee-note lines) | days-36-60.md |
| G-B5 / G-C7 (+ 623B 38.3b, 47.2b) | 38.3, 47.2 (fee-note letterhead / brand / banking) | days-36-60.md |
| G88-M8 | 38.3 (emailed-PDF VAT column) | days-36-60.md |
| G-C3 (terminology half) | 38.2 ("Fee note") | days-36-60.md |
| G-C9 | 45.6–45.11 (write-off UI end to end) | days-36-60.md |
| G-D6 | 45.9 (filters); 85.2 / 85.3 (named actor picker, "Matter" entity facet) | days-36-60.md, days-76-90.md |
| G-C11 | 55.1 (amount + method fields) | days-36-60.md |
| G-D1 | 68.1; P-0.1b–P-0.4b and three more W8b sign-ins (bare `/login` → Mailpit → Access Portal) | days-61-75.md, portal-90day.md |
| G-D19 | 68.2 (portal matter status "Active") | days-61-75.md |
| G-D2 | 75.2 (Close Matter on a COMPLETED matter) | days-61-75.md |
| G-D3 | 75.10 (Retention card "Scheduled deletion: 29 Oct 2031") | days-61-75.md |
| G-D14 | 75.9 (closure-letter amounts = INV-0001) | days-61-75.md |
| G88-M16 | 75.3 (all 9 gates green, after the §5a row 11 amendment) | days-61-75.md |
| G88-M15 (623B note) | 75.x (Complete no longer starts the retention clock) | days-61-75.md |
| G-D4 | 80.4 (Profitability Export CSV, 8 columns) | days-76-90.md |
| G-D21 | 80.2 (Home Debtors) | days-76-90.md |
| G-D7 | 85.7 / 85.8 (admin Bob cannot override) | days-76-90.md |
| G-D8 | 85.17 (`override_used: true` + justification in the event) | days-76-90.md |
| G-D9 | 88.2 (Estate feed 47 items = 47 audit events, templated) | days-76-90.md |
| G-D12 | 90.4 (Team "3 members · 0 pending invitations", no seat limit) | days-76-90.md |
| G-D10 (all but the three residuals above) | 90.1a (Home raw keys, My work "By Project", Reports index, New Task "project", audit labels, 14 settings tabs, breadcrumb slugs) | days-76-90.md |
| G88-M14 ("project" copy half) | PRE-75-tasks | days-61-75.md |

## Exit test — final arithmetic

Final, including the 629.9 suite rows (builder 2):
- **0 HIGH: FAIL** — **3 HIGH**: G88-F12 (OTP Voetstoots binds `org.name`), G88-F33 (SoA S3-key collision; an internal SoA was served on the portal) and **G88-F48** (server-dated trust postings in UTC; a negative client trust balance day, and the interest run misallocated).
- **0 FAIL outside register §5: FAIL**, made up of:
  - 59 of the 71 scored day-file FAIL rows;
  - the 4 failing lifecycle tests of 629.9 (G88-F48 ×1 + its cascade, G88-F49 ×2);
  - the computed 90-day exit FAILs E.1, E.2b, E.3, E.8 and **E.11** (`exit-checkpoints.md`).
- **Open register:** 63 open gaps (BLOCKER 0 · HIGH 3 · MEDIUM 15 · LOW 45) + 1 hypothesis (G88-F36, not counted).
- **Not counted as FAIL:** the 17 lifecycle tests that did not run in 629.9 run 1. They all ran in the diagnostic re-run (15 PASS, 2 named fixmes skipped; `e2e-runs.md` § "Diagnostic re-run"). That outcome does not change run 1's scored FAIL (ruling R-3), and the diagnostic run adds no FAIL and no gap.

**Result: FAIL — the legal-za 90-day scenario is not demo-ready at HEAD `20559c3d6`.**

Row counts come from `grep -nE '^\| [^|]+ \| FAIL \|'` over the nine day files:
- 90-day: 37 rows (`session-0.md` 2, `day-00.md` 7, `days-01-07.md` 9, `days-08-21.md` 8, `days-22-35.md` 4, `days-36-60.md` 3, `days-61-75.md` 1, `days-76-90.md` 3);
- portal: 34 rows (`portal-90day.md`).

These match the README Sections table (FAIL 37 and 34).
