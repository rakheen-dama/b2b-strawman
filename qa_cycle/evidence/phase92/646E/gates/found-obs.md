# 646E observed gates — found items

No new defect against 646E.

Notes. Each is pre-existing and already tracked, so none is a 646E defect:
- **W1.** The Client Ledger Statement's Description column prints the firm's reversal reason ("OBS646E payee bank rejected the EFT …"). See `obs/11-g1-client-ledger-statement-apex.png`. This is out of scope per the brief.
- **F1** (649B `found.md` → 646C). The Trust Receipts & Payments report omits both halves of the reversed Apex payment pair. The REVERSED payment from 15 Nov and its RECORDED REVERSAL contra from 17 Nov are not listed (`obs/12-g2-receipts-payments.png`). The cause is `findForReceiptsPayments` filtering `status IN (RECORDED, APPROVED) AND type <> REVERSAL`.
- **NEUTRAL direction not observable through the UI.** Every REVERSAL created via the UI resolves its original, so it renders as IN or OUT. `data-direction="neutral"` is covered only by the unit tests.
