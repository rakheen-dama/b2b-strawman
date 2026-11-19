# 652B — found

- **F-652B-1** — A REVERSAL of a transfer leg (zero real cash effect) is still offered in the step-4 manual-match pane and would be refused by the backend's sign check ("Transaction type is not compatible with bank statement line sign"). The pane excludes TRANSFER_OUT/IN and UNIDENT_ALLOC_OUT/IN by type code (652B D-2), but a REVERSAL's cash effect is derived from its original, which the transaction payload does not carry. The fully correct filter needs the derived cash effect on the wire or a server-side filter (beyond R9). Rare in practice. Suggested owner: follow-up.
- **F-652B-2** — Scout side observation, not investigated: after auto-match a statement line shows Unmatched while its DB row stores the deposit as a suggested match. Hypothesis only — needs reproduction before any fix. Suggested owner: follow-up.
- **F-652B-3** — CF-2 from 652A found.md (the step-4 pane lists transactions dated after the period end; no period-end date bound) remains open — 652B D-1 kept it out of this slice. Suggested owner: follow-up.

## F-652B-4 — Record Transfer dialog takes raw client / matter UUIDs

- Source: 652B OBS (`obs/README.md`), seen while recording the internal transfer for check (c).
- Defect: the Record Transfer dialog asks for the source / target client and matter as free-text UUID fields instead of pickers; the OBS had to read the ids from the database to fill it.
- Not absorbed: outside 652B (pane filter only). Suggested owner: Phase 91 UI modernization / follow-up.
