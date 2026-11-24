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
