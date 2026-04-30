# Fix Spec: BUG-CYCLE26-07 — seed `proposal.content_json` with a minimal Tiptap doc at create time when none is supplied

## Problem

After BUG-CYCLE26-06 landed (PR #1173 / squash `5365e48a`), the next gate inside `ProposalService.sendProposal()` now fires for every proposal created via the matter-level "+ New Engagement Letter" dialog:

```
InvalidStateException: "Proposal content must not be empty"
```

Every such proposal has `content_json = '{}'::jsonb` because the dialog only collects Title / Customer / Fee Model / Hourly Rate Note / Expiry — it never sends a `contentJson` payload to `POST /api/proposals`. Gate-2 has existed since Mar 1, 2026 (commit `4023f683` Epic 232B) but was previously masked by gate-1 (the now-fixed prereq).

Evidence:
- `qa_cycle/checkpoint-results/day-07.md` §"Cycle 17 Retest 2026-04-27 SAST" → table row 7.8c ("New dialog message reads `Proposal content must not be empty`") and §"New finding (informational, NOT a BUG-CYCLE26-06 reopen)" (lines 258-267).
- `qa_cycle/checkpoint-results/cycle17-retest-7.8-after-send.yml` paragraph `e187` — captured DOM of the post-Send dialog showing the new error string.
- `qa_cycle/checkpoint-results/day-07.md` §"Cycle 14 (2026-04-27) — Day 7 fresh walk on main 3e018078" → §"Checkpoints (Day 7)" 7.1 (line 178) — exhaustive list of dialog inputs (Title / Customer locked / Fee Model / Hourly Rate Note / Expiry); no content/template/Tiptap input.

QA position: Day 7 7.8 onwards is blocked again, but on a different root cause than BUG-CYCLE26-06. Triaged as a separate scaffold gap.

## Root Cause (verified)

### Gate-2 location

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalService.java:617-621`

```java
// 2. Validate content is not empty
if (proposal.getContentJson() == null || proposal.getContentJson().isEmpty()) {
  throw new InvalidStateException(
      "Invalid proposal content", "Proposal content must not be empty");
}
```

This gate is correct and should stay — `tiptapRenderer.render(proposal.getContentJson(), ...)` in `ProposalPortalSyncEventHandler.java:97-98` requires a non-empty Tiptap document tree (root node `{"type":"doc","content":[...]}`) to produce HTML for the portal sync. Sending with an empty map would render an empty `<body>` to the portal.

### Why content_json is empty

The proposal create path:

1. **Frontend dialog** — `frontend/components/proposals/create-proposal-dialog.tsx:137-187` (`onSubmit`) builds the request body and forwards to `createProposalAction(slug, {...})`. The dialog has no Tiptap editor, no template picker, no `contentJson` field anywhere in the form schema (`frontend/lib/schemas/proposal.ts`).
2. **Server action** — `frontend/app/(app)/org/[slug]/proposals/actions.ts:36-56` (`createProposalAction`) defines `CreateProposalRequest` (line 20-34) with no `contentJson` key and forwards to `POST /api/proposals`.
3. **Backend controller** — `backend/.../proposal/ProposalController.java:186-214` accepts `contentJson` as an optional field of `CreateProposalRequest` (line 212).
4. **Backend service** — `backend/.../proposal/ProposalService.java:104-182` (`createProposal`). Line 156: `if (contentJson != null) proposal.setContentJson(contentJson);`. When the frontend omits the field, the service never sets it. The entity field default in `Proposal.java:95` is `Map.of()` (empty immutable map), and `Proposal.java:94` declares the column `nullable = false`. So the row is persisted with `content_json = '{}'::jsonb` — non-null but empty, which is exactly what the gate-2 isEmpty() check rejects.

### Why this never broke before

The org-level `/proposals` page uses the same dialog, but cycle 14 verified on the matter-level path. The org-level path has been blocked since L-48 introduced the "GET /api/customers 404" issue (the pre-fix path Day 7 cycle 1 reported), so no post-fix end-to-end Send through this codepath has ever succeeded against an INDIVIDUAL customer with a portal_contact. Gate-1 (BUG-CYCLE26-06) would have caught it first anyway.

### Downstream consumers of content_json

- `ProposalPortalSyncEventHandler.java:97-98` — `tiptapRenderer.render(proposal.getContentJson(), renderContext, Map.of(), null)` — this is the only consumer at send-time. It walks the Tiptap node tree to produce HTML for the portal read-model.
- `TiptapRenderer.renderNode()` (`backend/.../template/TiptapRenderer.java:111-220`) starts at `case "doc" -> renderChildren(...)` (line 124). A doc with empty `content: []` produces an empty `<body>`. That is fine for portal sync — the portal sees a header + acceptance buttons but no body copy. Acceptable for MVP.
- `ProposalVariableResolver.java:33-54` — builds the variable context (client_name, fee_model, expiry_date, etc.). Only consulted by TiptapRenderer when the doc references variables.

### Conclusion

The bug is **not** in gate-2 (it's a legitimate guard against empty portal sync output). The bug is that the matter-level "+ New Engagement Letter" dialog promises a complete proposal-create flow (dialog title is "New Engagement Letter") but produces an unsendable artefact because no part of the create pipeline supplies a default content_json shell.

## Decision: Option 1 (backend default-seed at create time) — with a refinement

**Pick option 1, but seed at `createProposal()` time, not at `sendProposal()` time.** Rationale below.

### Rejected: Option 2 (frontend seeds default doc)

- **Two callers, two seed sites**. Both the org-level `/proposals` dialog and the matter-level `+ New Engagement Letter` dialog share `CreateProposalDialog` and `createProposalAction`, but a future API consumer (a script, a third-party automation, a portal-side workflow) calling `POST /api/proposals` directly would silently re-introduce the bug. Server-side default is the durable fix.
- **No backend test coverage**. A frontend-only seed lives outside the backend integration test surface. Backend tests would still need their own seed pattern, leading to two parallel default shapes that drift.
- **Adds frontend complexity for no gain**. The current dialog is a thin scaffold; seeding a Tiptap doc client-side requires importing or hand-rolling the doc shape in TypeScript. Backend already has the canonical shape (every proposal-template JSON file under `template-packs/legal-za/` follows it).

### Rejected: Option 3 (new "Generate engagement letter" step before Send)

- This is **GAP-L-49 (Sprint 3 deferred)** — clause-driven authoring + LSSA tariff fee block. QA explicitly classified L-49 as "feature, not a fix" and the cycle-17 retest re-confirmed Sprint 3 deferral.
- It is a multi-day build: new route or panel, Tiptap editor wiring (`@tiptap/react` + extensions), template/clause picker, variable preview, save-draft semantics, autosave, edit-after-create. Easily 8-12 hours, well past the "M (30 min – 2 hr)" QA cycle budget.
- Building it under a bug ticket would mask the work as a fix and defeat the Sprint 3 plan.

### Why option 1 — and seed at create time, not at send time

Two sub-options inside option 1:

**1A. Seed at `sendProposal()` (lazy)** — when content_json is empty at Send time, build the minimal doc on the fly.
  - Pro: never persists "blank-shell" data; user can still author content between create and send.
  - Con: hides the fact that the proposal has no content from the user up until the moment they click Send. The entity is in a "send-impossible" state for its entire DRAFT life; only at Send do we paper over it. Surprises tests that introspect content_json mid-flight.
  - Con: the gate-2 message is the actual safety net — wrapping the gate in a "if empty, build one and try again" makes the gate cosmetic. Better to keep gate-2 honest and ensure the entity is sane from creation.

**1B. Seed at `createProposal()` (eager) — RECOMMENDED**
  - When `contentJson` is null OR `isEmpty()` at create time, populate it with a minimal Tiptap doc derived from `(title, feeModel, hourlyRateNote, retainerAmount, retainerCurrency, expiresAt, customer.name)` resolved at that point.
  - Pro: every persisted proposal is in a sendable state from the moment it exists. Gate-2 keeps its current honest behavior — it would now only trigger if a future code path explicitly mutated content_json back to empty.
  - Pro: a future "edit content" UI (when L-49 lands) overwrites the seed via `updateProposal()` — the existing `if (contentJson != null) proposal.setContentJson(contentJson);` path (line 313) covers that.
  - Pro: backend tests can assert that a freshly-created proposal has a non-empty document, so any regression of this fix surfaces immediately at the unit test layer.
  - Con: persists a derivative of the form fields. Acceptable — the seed is purely a convenience artefact; users can replace it once L-49 lands.

Pick **1B**.

## Fix

### Step 1 — New service: `ProposalContentSeeder`

Create `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalContentSeeder.java`. Single-method `@Service` with no Spring dependencies (pure function — easier to unit test).

Responsibilities:
- Build a minimal Tiptap doc tree from a small parameter object (title, feeModel, hourlyRateNote, fixedFeeAmount, fixedFeeCurrency, retainerAmount, retainerCurrency, retainerHoursIncluded, contingencyPercent, contingencyDescription, expiresAt, customerName).
- Output shape (Java `Map<String, Object>` mirroring the JSON nodes in `template-packs/legal-za/engagement-letter-litigation.json`):
  ```
  {
    "type": "doc",
    "content": [
      { "type": "heading", "attrs": {"level": 2}, "content": [{"type":"text","text": <title> }] },
      { "type": "paragraph", "content": [{"type":"text","text":"Dear "},{"type":"variable","attrs":{"key":"client_name"}},{"type":"text","text":","}] },
      { "type": "heading", "attrs": {"level": 3}, "content": [{"type":"text","text":"Fee Arrangement"}] },
      { "type": "paragraph", "content": [{"type":"text","text": <fee summary line> }] },
      // optional: hourly rate note paragraph if HOURLY + non-blank
      // optional: expiry paragraph if expires_at is non-null
      { "type": "paragraph", "content": [{"type":"text","text":"This proposal is subject to our standard terms and conditions. Please contact us if you have any questions."}] }
    ]
  }
  ```
  Use `LinkedHashMap` (not `Map.of`) so JSON serialization preserves order and so the maps stay mutable in case a downstream caller wants to extend them.
- Fee summary line by `feeModel`:
  - `HOURLY` → `"Fees will be charged on an hourly basis."` (then if `hourlyRateNote` non-blank, append a paragraph: `"Rate: <hourlyRateNote>"`)
  - `FIXED` → `"Fixed fee: <currency> <amount>"` (uses `fixedFeeAmount.toPlainString()` and `fixedFeeCurrency` defaulting to "ZAR")
  - `RETAINER` → `"Monthly retainer: <currency> <amount>"` (plus `"Hours included: <retainerHoursIncluded>"` if set)
  - `CONTINGENCY` → `"Contingency: <contingencyPercent>%"` (plus description paragraph if set)
- Use `{{client_name}}` Tiptap `variable` node (resolved by `ProposalVariableResolver`) so the rendered HTML interpolates the customer's name at portal-sync time. Do NOT inline the customer name as raw text — the seeder must not depend on `Customer` lookup.

Method signature:
```java
public Map<String, Object> buildDefaultContent(
    String title,
    FeeModel feeModel,
    String hourlyRateNote,
    BigDecimal fixedFeeAmount,
    String fixedFeeCurrency,
    BigDecimal retainerAmount,
    String retainerCurrency,
    BigDecimal retainerHoursIncluded,
    BigDecimal contingencyPercent,
    String contingencyDescription,
    Instant expiresAt)
```

### Step 2 — Wire seeder into `ProposalService.createProposal`

In `ProposalService.java:156`, replace:

```java
if (contentJson != null) proposal.setContentJson(contentJson);
```

with:

```java
Map<String, Object> effectiveContentJson =
    (contentJson != null && !contentJson.isEmpty())
        ? contentJson
        : proposalContentSeeder.buildDefaultContent(
            title,
            feeModel,
            hourlyRateNote,
            fixedFeeAmount,
            fixedFeeCurrency,
            retainerAmount,
            retainerCurrency,
            retainerHoursIncluded,
            contingencyPercent,
            contingencyDescription,
            expiresAt);
proposal.setContentJson(effectiveContentJson);
```

Inject `ProposalContentSeeder proposalContentSeeder` via constructor (follow existing constructor-injection pattern at `ProposalService.java` constructor — backend convention per `backend/CLAUDE.md` "Never use `@Autowired` on fields").

Do NOT change `updateProposal()` (line 313) — explicit caller-supplied empty JSON via PUT is a different intent (clearing content; out of scope here, gated by `requireDraft()` so safe). Do NOT change `sendProposal()` gate-2 — it remains the safety net.

### Step 3 — Unit test the seeder

Create `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalContentSeederTest.java`. No Spring context (pure pojo), no DB.

Cases:
1. `HOURLY` with non-blank rate note → doc has heading, greeting paragraph with `client_name` variable, fee paragraph, rate-note paragraph, terms paragraph; `tiptapRenderer.render(doc, ctx, Map.of(), null)` produces non-empty body containing the rate note. (Wire `TiptapRenderer` via the package-private constructor with the production CSS string captured from `templates/document-default.css` — see existing renderer tests for the pattern.)
2. `FIXED` with amount + currency → fee paragraph contains `"Fixed fee: ZAR 5000.00"`.
3. `RETAINER` with hoursIncluded null → no hours-included paragraph.
4. `CONTINGENCY` with description → description paragraph rendered.
5. `expiresAt` null → no expiry paragraph emitted.
6. All-null fee values for HOURLY → still produces sendable doc with at least heading + greeting + fee summary + terms.
7. Output map structure: `doc.get("type").equals("doc")`, `((List) doc.get("content")).size() >= 4`, root content list mutable.

### Step 4 — Update `ProposalServiceIntegrationTest` (or create one) to assert on send-after-create round-trip

Find the existing integration test for createProposal+sendProposal (or the closest analog). Add a regression test:

- Create a proposal via `proposalService.createProposal(...)` with `contentJson = null` and the same INDIVIDUAL-customer + portal_contact fixture used by BUG-CYCLE26-06's verification test.
- Assert `proposal.getContentJson()` is non-null and `!isEmpty()`.
- Call `sendProposal(...)` against an ACTIVE customer with an ACTIVE portal_contact + email + address.
- Assert no exception; status transitions to SENT.

This locks in the fix end-to-end and detects the regression the moment someone removes the seeder wire-up.

### Step 5 — Manual `address_line1` precondition note (no code change)

The fix-spec for BUG-CYCLE26-06 retains the `address_line1` structural check. Sipho's row already has `address_line1='12 Loveday St'` per cycle-17 retest. No action needed; Day 7 verification needs no DB update.

## Scope

- **Backend only.**
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalService.java` — inject seeder, replace line 156 wiring (per Step 2).
- Files to create:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalContentSeeder.java` (Step 1).
  - `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalContentSeederTest.java` (Step 3).
  - Optionally extend or add an integration test under `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/` (Step 4).
- **Migration needed: no.** `proposal.content_json` already exists with `nullable = false` and default `Map.of()` semantics; existing rows with `content_json = '{}'::jsonb` from prior cycles remain in DRAFT and can be deleted/overwritten via the proposal UI; we are not changing the schema.
- **Frontend: zero changes.** The dialog stays as-is. (Future L-49 work will replace this minimal seed with template-driven authoring.)
- **Restart needed.** Java change — `bash compose/scripts/svc.sh restart backend` after merge.

## Verification

### Backend test layer (Dev runs before pushing)

1. `./mvnw test -Dtest=ProposalContentSeederTest` — all cases green.
2. `./mvnw test -Dtest=ProposalServiceIntegrationTest` (or whichever class houses the create+send roundtrip) — green, including the new send-after-default-seed assertion.
3. `./mvnw test -Dtest='Proposal*'` — full proposal-package suite still green (no regressions on the existing `proposalSend_companyCustomer_noPortalContact_stillRequiresContactFields` test from PR #1173).

### QA browser-driven re-walk (Day 7, post-merge)

Pre-state: existing proposal `0781c5ad-…` will still have `content_json = '{}'::jsonb` from cycle 14 (created before this fix). To exercise the fix, **create a fresh proposal** via the matter-level dialog after backend restart. Two paths to validate:

1. **Path A — New proposal post-fix (must succeed end-to-end)**:
   - Sign in as Thandi (Owner), navigate to RAF matter detail.
   - Click `+ New Engagement Letter` → fill Title `RAF Engagement v2 — Sipho`, Fee Model `Hourly`, Hourly Rate Note `R850/hr per LSSA 2024/2025`, Expiry `Day+7`. Click Create Proposal.
   - DB: `SELECT content_json FROM tenant_5039f2d497cf.proposals WHERE id = <new-id>` → returns a non-empty doc with `type=doc` root and at least 4 content children including a `variable` node with `attrs.key='client_name'`.
   - On the proposal detail page, click `Send Proposal` → select Sipho recipient → Send.
   - **Expected**: dialog closes cleanly; proposal status flips to SENT; `sent_at` populated; `portal_contact_id` set; Mailpit gets one outbound acceptance email; portal `/accept/{token}` renders the customer name in the body (via `client_name` variable interpolation).
   - This is **7.8** (PASS), **7.9** (PASS), **7.10** (PASS — re-verifies L-51 subject), **7.11** (PASS — re-verifies L-50 portal host).

2. **Path B — Old proposal `0781c5ad-…` (must still hit gate-2 because it pre-dates the seeder)**:
   - Open `/proposals/0781c5ad-…` → click Send Proposal → Sipho → Send.
   - Expected: gate-2 still fires (`Proposal content must not be empty`) because the row was created before the seeder was wired in.
   - This is the regression confirmation — gate-2 stays honest, and only newly-created proposals benefit from the default seed.
   - QA may delete `0781c5ad-…` (DELETE through API while DRAFT) or simply leave it as a historical artefact; not blocking.

### QA evidence to capture

- DB row dump showing `content_json` populated for the new proposal.
- Snapshot of the `Send Proposal` dialog after Send (no error, dialog closed) for Path A.
- Mailpit message ID + portal URL host for 7.10/7.11.
- Optional: snapshot of Path B still hitting gate-2 (proves we didn't gut the gate).

### Day 7 checkpoints to re-walk

- 7.8 (Send for Acceptance) — must PASS via Path A.
- 7.9 (status=SENT, acceptance URL generated) — must PASS.
- 7.10 (Mailpit subject contains action keywords — re-verifies L-51) — must PASS.
- 7.11 (email body URL points to portal `:3002` — re-verifies L-50) — must PASS.

If all four pass, BUG-CYCLE26-07 is VERIFIED and Day 7 7.8 is unblocked. Day 8 can begin.

## Estimated Effort

**S (< 30 min)** for Dev. Three files, one wiring change, no schema work, no frontend touch. The seeder is pure Java pojo logic (no Spring beans except `@Service` annotation), unit test is in-memory, integration test reuses existing fixtures from PR #1173. No restart drama since the change is contained to backend code.

A staff engineer would approve this scope: it's the minimal root-cause fix that keeps gate-2 honest, doesn't pre-empt the L-49 Sprint 3 feature work, and locks in regression coverage.
