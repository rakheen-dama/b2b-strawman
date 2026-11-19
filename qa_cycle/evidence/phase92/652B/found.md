# 652B — found

- **F-652B-1** — A REVERSAL of a transfer leg (zero real cash effect) is still offered in the step-4 manual-match pane and would be refused by the backend's sign check ("Transaction type is not compatible with bank statement line sign"). The pane excludes TRANSFER_OUT/IN and UNIDENT_ALLOC_OUT/IN by type code (652B D-2), but a REVERSAL's cash effect is derived from its original, which the transaction payload does not carry. The fully correct filter needs the derived cash effect on the wire or a server-side filter (beyond R9). Rare in practice. Suggested owner: follow-up.
- **F-652B-2** — Scout side observation, not investigated: after auto-match a statement line shows Unmatched while its DB row stores the deposit as a suggested match. Hypothesis only — needs reproduction before any fix. Suggested owner: follow-up.
- **F-652B-3** — CF-2 from 652A found.md (the step-4 pane lists transactions dated after the period end; no period-end date bound) remains open — 652B D-1 kept it out of this slice. Suggested owner: follow-up.

## F-652B-4 — Record Transfer dialog takes raw client / matter UUIDs

- Source: 652B OBS (`obs/README.md`), seen while recording the internal transfer for check (c).
- Defect: the Record Transfer dialog asks for the source / target client and matter as free-text UUID fields instead of pickers; the OBS had to read the ids from the database to fill it.
- Not absorbed: outside 652B (pane filter only). Suggested owner: Phase 91 UI modernization / follow-up.

## F-652B-5 — the zero-bank-movement list is hard-coded on the frontend (reviewer B delta Mediums, PR #1816 @7a21eb55a)

- `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx:60-70` (`NO_BANK_MOVEMENT_TYPES`): the type registry sent to the frontend (`TrustTransactionTypeInfo`, `frontend/lib/types/trust.ts:97-109`) carries no cashbook sign, so the four codes are hard-coded and can go stale if a new zero-cashbook type is added server-side.
- The comment at `:60` says "registry `cashbookSign` 0", but that field is not on the wire. Reword it to "backend cashbook sign 0 (not exposed by the registry)".
- The `satisfies readonly TrustTransactionType[]` on the list checks nothing, because `TrustTransactionType` is `string` (`trust.ts:79`).
- Suggested follow-up: expose a `bankMovement` (or cashbook-sign) flag on the type registry endpoint, derive the exclusion from it, and drop the list. This also closes F-652B-1 if the flag covers a reversal's derived effect.
- Not absorbed: recorded post-merge so the reviewed head did not move. Owner: follow-up.
