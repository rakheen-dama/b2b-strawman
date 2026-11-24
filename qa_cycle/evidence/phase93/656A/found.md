# 656A — found outside the slice's own scope

F-656A-1 and F-656A-3 are not fixed in 656A (root `CLAUDE.md` §7, phase rule "a defect found outside the slice goes to found.md"); F-656A-2 was fixed in review round 1 (D-178).

## F-656A-1 (scout C-9) — the lifecycle refusal shows the raw lifecycle enum (not fixed — pre-existing copy)

A matters row whose client is PROSPECT, OFFBOARDING or OFFBOARDED is an ERROR row with the text the guard itself
produces: `CustomerLifecycleGuard.throwBlocked` →
`"Cannot create " + action.label() + " for client in " + status + " lifecycle status"`, e.g.
"Cannot create matter for client in PROSPECT lifecycle status". `status` is the raw `LifecycleStatus` enum name, and
the phase rule is "no raw enum on any screen".

The spec (§17.3.6, AC-4) says to use the guard's own text and not re-type it, so 656A keeps it word for word
(`MattersDatasetHandler` reads it from the thrown `InvalidStateException`'s detail, both in the dry run and as the
failure reason of a row whose client is offboarded between validation and commit). The same text already reaches the
screen on the ordinary path: creating a matter for a prospect client in the UI shows it as the error detail.

Fix (for a later slice, outside take-on): render the status with its display label (e.g. "Prospect") inside
`CustomerLifecycleGuard.throwBlocked`. That changes every caller's copy (matters, tasks, fee notes, documents, time
entries, custom fields) and the tests that pin it, so it needs its own spec.

Pinned by: `MattersTakeOnIntegrationTest.aClientWhoseLifecycleRefusesAMatter_orAnAmbiguousIdNumber_isAnError` and
`…aClientOffboardedBetweenValidationAndCommit_failsThatRowWithTheGuardsText`.

## F-656A-2 (scout C-13) — a matters row keeps the client's email and ID number after the client is anonymised — FIXED (review round 1, D-178)

A matters take-on row stores the client reference the file gave — client email, client previous-system ID, client
ID number — in `raw_values` and `mapped_values` (plus the derived `clientId`). Client anonymisation
(`DataAnonymizationService` → `TakeOnRowRepository.redactForClient`) redacts only the rows whose
`result_entity_id` / `duplicate_of_id` is the client, or whose `dedup_key` is one of the client's email keys. A matters
row's `result_entity_id` is the **matter**, and its `dedup_key` is `ref:{matter reference}` or
`name:{clientId}:{matter name}`, so none of the three conditions matches: after the client is anonymised its matters
rows still hold its email address and ID number (and the matter names), until the batch's rows are purged.

This is a POPIA residual of the kind RG-9 tracks. Possible fixes (product/architecture call, not 656A's):
(a) extend `redactForClient` to also redact `dataset = 'MATTERS'` rows whose `mapped_values->>'clientId'` is the client
(the resolved id is stored on every resolved row, D-158); unresolved (ERROR) rows would still need a value match on the
email / ID number; or (b) drop the raw client reference from a matters row once it is committed.

Evidence: by reading — `TakeOnRowRepository.redactForClient` (WHERE clause) against the matters row shape that
`MattersDatasetHandler.mapAndValidate` writes. Not reproduced by a test in 656A (the fix is out of scope).

**FIXED** in review round 1 (CodeRabbit, POPIA): fix (a) — `redactForClient` also redacts `dataset = 'MATTERS'` rows whose
`mapped_values ->> 'clientId'` is the anonymised client, with the same redaction as the other arms; a matters row
resolved to another client is untouched (D-178). Unresolved (ERROR) rows carry no `clientId` and are unchanged — they
never matched the client. The test fails on the old query (row 2's `raw_values` still held the
client's values) and passes on the new one. Pinned by
`MattersTakeOnIntegrationTest.anonymisingAClient_redactsTheMattersRowsResolvedToIt_andLeavesAnotherClientsRowsAlone`
(clients take-on → matters take-on resolved by email, previous-system ID and ID number → `POST /api/clients/{id}/anonymize`).

## F-656A-3 (review round 1, Agent B) — parked columns merged onto a matter survive the client's anonymisation (not fixed — product/legal call)

A matters take-on keeps the file's parked columns on the created matter as `customFields.takeon_parked` (D-163). When
the client is anonymised, `DataAnonymizationService.dropTakeOnParkedColumns` drops the **client's** own
`takeon_parked` (D-122), but nothing touches the client's matters, so the parked columns on those matters (free text
from the previous system that may hold personal information) remain.

Not fixed in 656A: anonymisation does not change matter records today (matter names and descriptions are retained), so
whether it should is a product/legal decision, not this slice's (D-181, DEFAULT). Possible fix (reversible): at
anonymisation, also drop `takeon_parked` from every matter whose `customer_id` is the client.

Evidence: by reading — `DataAnonymizationService.dropTakeOnParkedColumns` (client only) against
`MattersDatasetHandler.mergeParked`. Not reproduced by a test (out of scope).

## OBS-656A-1 (OBS gate, 2026-11-24) — the matter header shows the stored work-type code (e.g. `COMMERCIAL_LITIGATION`), not a label (pre-existing render; every take-on matter reaches it)

**Found by:** the 656A OBS run on a fresh E2E stack built from `580ae0388` (`obs/README.md`, check 4).

**Steps:** Settings → Matters → Take-on → Matters → upload `obs/fixtures/matters-656a.csv` (row 4: Work type
`commercial litigation`) → map → validate → commit. Open the matter "Supply contract dispute" (any tab).

**Expected:** a human label, as the take-on validation table shows it ("Work type: Commercial litigation",
`matterValueText` → `humaniseWorkType`) and as the phase rule requires ("no raw enum on any screen").

**Actual:** the matter hero shows `COMMERCIAL_LITIGATION` next to the Active badge (`obs/d-40-matter-details-0.png`).
The same happens for every imported matter with a work type: `CONVEYANCING` (`d-41`), `LICENSING` (`d-42`). The DOM
scan of `main` finds the token (`obs-log.txt`, second step-d pass: `"raw":["COMMERCIAL_LITIGATION"]`, `["CONVEYANCING"]`,
`["LICENSING"]`). psql: `projects.work_type` is stored canonical (`COMMERCIAL_LITIGATION`, `psql-03-matters-created.txt`),
which is correct (`WorkTypes.normalizeForStorage`, field groups match on it).

**Not introduced by 656A — control:** a matter created through the ordinary New Matter dialog with work type
"Commercial litigation" shows the same `COMMERCIAL_LITIGATION` in its header (`obs/g-01-ui-new-matter-dialog.png`,
`obs/g-02-ui-created-matter-header.png`, log `[ui-created matter header]`). Root cause by reading:
`frontend/components/projects/matter-header-card.tsx:94` renders `{workType}` raw; unchanged by 656A (last touched in
641B, #1771; the raw render dates from 50aec5407, 2026-05). The Matters list's work-type column already uses
`humaniseWorkType` (`components/matters/matter-columns.tsx`). 656A makes it reachable at scale: every taken-on matter
with a free-text work type lands on this header.

**Fix (for the orchestrator to dispatch; outside 656A's diff):** render `humaniseWorkType(workType)` in
`MatterHeaderCard` (one line), with a vitest for a canonical multi-word value. The 656A validation table is correct.
