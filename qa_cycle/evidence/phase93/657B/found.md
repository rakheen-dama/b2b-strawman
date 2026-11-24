# 657B — found outside the slice's own scope

Neither finding is fixed in 657B (root `CLAUDE.md` §7, phase rule "a defect found outside the slice goes to found.md";
decision D-188). Both need their own spec.

## F-657B-1 (OP-11) — trust take-on rows keep the client reference after the client is anonymised (not fixed)

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
