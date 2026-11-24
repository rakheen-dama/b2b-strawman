# 656A — found outside the slice's own scope

Neither item is fixed in 656A (root `CLAUDE.md` §7, phase rule "a defect found outside the slice goes to found.md").

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

## F-656A-2 (scout C-13) — a matters row keeps the client's email and ID number after the client is anonymised (not fixed — POPIA residual, RG-9)

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
