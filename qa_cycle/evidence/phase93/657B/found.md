# 657B — found outside the slice's own scope

F-657B-1 was fixed in review round 1 (D-192). F-657B-2 and F-657B-3 are not fixed in 657B (root `CLAUDE.md` §7,
phase rule "a defect found outside the slice goes to found.md"; decision D-188) and need their own spec.

## F-657B-1 (OP-11) — trust take-on rows keep the client reference after the client is anonymised — FIXED (D-192)

**Status: FIXED in 657B review round 1 (D-192).** `redactForClient`'s mapped-`clientId` arm now covers
`TRUST_POSITION` as well as `MATTERS`; a redacted row in an uncommitted batch re-validates as one `ROW_REDACTED`
error. 657C still decides the committed-row / take-on report retention question (redact is the default). The
original finding follows.

A trust position take-on row stores the client reference the file gave (`clientRef` — an email, the previous system's
client id or an ID number) in `raw_values` and `mapped_values`, and from 657B also the derived `clientId` /
`clientResolvedBy` (client and matter balances, section 86(4) investments, items outstanding at cutover), plus the
client and matter ids inside the `CLIENT_LEDGER` dedup key (`{account}:CLIENT_LEDGER:{clientId}:{matterId}`, §17.2.3).

Client anonymisation (`DataAnonymizationService` → `TakeOnRowRepository.redactForClient`) redacts only rows whose
`result_entity_id` / `duplicate_of_id` is the client, matters rows whose `mapped_values ->> 'clientId'` is the client
(D-178), and rows whose `dedup_key` is one of the client's email keys. A `TRUST_POSITION` row matches none of these
arms, so after anonymisation it still holds the reference text the file gave (raw trust rows have existed since 653A;
657B adds the resolved id). The archived source file is a separate, retention-governed question (as for clients).

Fix (its own slice, a POPIA product/legal call like D-181): add a `dataset = 'TRUST_POSITION' AND mapped_values ->>
'clientId' = :clientId` arm with the same redaction, and decide whether a committed trust row (657C, `result_entity_id`
= the trust transaction) keeps its shell for the take-on report's retention period (Rule 54.9.1, seven years) — the
trust books themselves keep the client id by law, so the redaction is of the file's free text, not of the ledger link.

## F-657B-2 (P-10) — the deficit block and the FU-9 `CASHBOOK_IN_DEBIT` flag word the same statutory test differently (copy only, not fixed)

657B ships the X3 deficit block verbatim (`TrustPositionMessages.deficit`):

> "The trust creditors (R{a}) exceed the trust money held (R{b}) by R{c} at the cutover date. This is a trust deficit
> (Rules 54.14.8 and 54.14.10). Take-on cannot continue until it is resolved in the previous system."

FU-9's integrity flag (`TrustIntegrityService.cashbookInDebit`, `CASHBOOK_IN_DEBIT`, unchanged):

> "The trust banking account's cashbook is R{x} in debit as at {date}. Check that the trust money in the banking
> account, the trust investment accounts and trust cash still covers the balances of all trust creditors (Rule
> 54.14.8); if it does not, report to the Legal Practice Council immediately (Rule 54.14.10)."

Both cite Rules 54.14.8 and 54.14.10, and both say "trust creditors". The differences:

1. **"trust money held"** (X3) vs **"the trust money in the banking account, the trust investment accounts and trust
   cash"** (FU-9) — the same quantity (moneyHeld = adjusted bank + investments), named two ways.
2. **"This is a trust deficit"** (X3) — FU-9 never uses the term "trust deficit".
3. **Reporting**: FU-9 tells the firm to report to the Legal Practice Council immediately under Rule 54.14.10; the X3
   deficit block cites Rule 54.14.10 but does not say a report is due (the X3 client-in-debit block, by contrast,
   says "report … to the Council in writing … (Rule 54.14.11)").
4. **"the Council"** (X3 client-in-debit block) vs **"the Legal Practice Council"** (FU-9) — X3 never names the
   Council in full on first use, unlike its own rule for "Legal Practitioners' Fidelity Fund".

657B keeps the X3 text verbatim (binding) and does not change the FU-9 copy. Whether the deficit block should add the
Rule 54.14.10 reporting duty, and which name for "trust money held" both use, is a legal-copy decision: it belongs
with RG-13 (the trust auditor / LPC reading of Rule 54.14.10, carried from the Phase 92 F-6 opinion) and RG-10 (the
A6 check of all legal copy before release).

## F-657B-3 (architecture review M-1) — the client-lookup logic is copied from the matters dry run (not fixed)

`TrustPositionDatasetHandler` resolves a client reference with a copy of `MattersDatasetHandler`'s logic: the
lookup maps built in `loadContext` (`TrustPositionDatasetHandler.java` client loop over
`customers.findTakeOnDedupCandidates()`, vs `MattersDatasetHandler.java:199-210`) and the cascade itself
(`resolveClientOrError`, email → previous-system id via `TakeOnRowRepository.clientBySourceRef` filtered to live
clients → a single live client's ID number, with the `CLIENT_AMBIGUOUS` / `CLIENT_UNRESOLVED` errors, vs
`MattersDatasetHandler.java:329-353`). The two copies already differ in small ways (the trust rows give one
`clientRef` column, the matters rows three; the trust copy records `clientResolvedBy` as `email` /
`previousSystemId` / `idNumber`). A change to the D-157 cascade (for example a new live-client rule) must be made
twice, and 658A (debtor balances) would add a third copy.

Proposal: extract a shared `TakeOnClientResolver` in `takeon.dataset` (the lookup maps, built once per run in
`loadContext`, and one `resolve(reference, kinds)` returning the client id and how it resolved, or the
error code and text), used by the matters and trust handlers — **before 658A adds a third copy**. Behaviour-neutral
refactor; pinned by the existing `MattersTakeOnIntegrationTest` and `TrustPositionValidationIntegrationTest` cases.
