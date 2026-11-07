# G89 Batch D: KC browser verification (PR #1747, `72f5f3d4d`)

- **Walked:** 2026-11-07, 02:43–02:50 SAST.
- **Stack:** Keycloak dev stack. Firm app :3000 (org `mathebula-partners`), portal :3002, Mailpit :8025.
- **Tenant schema:** `tenant_5039f2d497cf`.
- **Backend build:** JVM PID 26276 started 02:42:54 SAST, after the merge commit (02:42:05). Two behaviours prove the new code is live:
  - the global V4 columns `portal_trust_transaction.transaction_date` / `recorded_at` exist and are populated;
  - the email reads "Deposit".
- **Driver:** Playwright MCP at 1440×900. Thandi (Owner) was in the default context; Lerato (portal) was in the isolated `lerato` context.
- **Lerato's sign-in:** magic link from Mailpit `h4bJn6P5XijMZS5Lho9s3c`.
- **Method:** every write went through the UI. SQL was read-only and used for evidence only.

## Result

| Row / gap | Status | Observation (exact UI text) | Evidence |
|---|---|---|---|
| **F23**: portal Firm-actions feed hides internal drafts | **PASS** | **1. Generate as internal draft.** Thandi, on the RAF matter, used Generate Document › "Matter Summary Report" › "Save to Documents". The dialog showed "Document saved successfully". This created `generated_documents 5439af3b-…` → `documents 5f4907ba-…` with `visibility = INTERNAL`, and audit `document.generated` `157926eb-…` (entity_id = 5439af3b).<br>**2. Portal before sharing.** Lerato, Activity › **Firm actions**, showed only `Trust refund approved · Bob Ndlovu · 1 hour ago`. There was **no** "Document generated for you" entry.<br>**3. Share.** Thandi, in Work › Generated documents › row "Matter Summary Report", clicked "Share with client". The dialog read "The client will be able to see Matter Summary Report when they sign in to the client portal." "Email the client now" was left unticked. After confirming, the toast read "Shared with client", the row showed the badge "Client portal", and `visibility = PORTAL` with audit `document.visibility_changed` `08d8ab9f-…`.<br>**4. Portal after sharing.** Lerato reloaded Firm actions and saw `Document generated for you · Thandi Mathebula · 3 minutes ago` above `Trust refund approved`. This is the same audit row, now surfaced at read time. | `F23-01-saved-internal.png`, `F23-02-portal-firm-actions-internal-hidden.png`, `F23-03-share-dialog.png`, `F23-04-shared.png`, `F23-05-portal-firm-actions-after-share.png`, `db-02-f23-before-share.txt`, `db-03-f23-after-share.txt` |
| **D6** (633C): trust email type humanised and dated by transaction date | **PASS** | Thandi recorded a deposit on the RAF matter: R 5 000,00, Transaction Date `2026-10-15`, ref `DEP/LERATO-g89d`. The firm Transactions list shows `15 Oct 2026 · DEP/LERATO-g89d · Deposit · R 5 000,00 · RECORDED`.<br>Mailpit `QkA55gTkNQepzfKNaKaL9w` ("Mathebula & Partners: Trust account activity", to lerato.mthembu@email.co.za, sent 02:48:24 SAST) reads `Date 15 Oct 2026 · Type Deposit · Amount R 5 000,00`. Its link is `/trust/e0f25d3f-…?orgId=mathebula-partners`.<br>Pre-fix comparison: `iFD6qrwhDcvC8Nx9Tx4nFA` (Batch A, before #1747) reads `Date 7 Nov 2026 · Type DEPOSIT`. | `D6-01-deposit-form.png`, `D6-02-deposit-recorded.png`, `D6-03-mailpit-trust-email.png`, `mailpit-D6-trust-email.txt` |
| **F39**: portal trust date is the transaction date | **PASS** | **Trust page (`/trust/e0f25d3f-…`).** The page reads `Trust balance R 5 000,00 · As of 7 Nov 2026`. Rows, newest first:<br>`07 Nov 2026 · Refund · R 10 055,21 · R 5 000,00`<br>`31 Oct 2026 · Interest credited · R 55,21 · R 15 055,21`<br>`15 Oct 2026 · Deposit · G89 Batch D verify - back-dated deposit -g89d · R 5 000,00 · R 15 000,00`<br>`01 Oct 2026 · Deposit · R 10 000,00 · R 10 000,00`<br>**The recompute rewrote the older rows.** Before this deposit, all three older rows had `transaction_date 2026-11-07` (the V4 interim backfill). They now carry their transaction dates: the deposit 01 Oct, the interest credit 31 Oct, the refund 07 Nov.<br>**Order and running balance.** Rows are ordered by transaction date, and the running balance reads in sequence: 10 000 → 15 000 → 15 055,21 → 5 000.<br>**Firm match.** The firm ledger gives the matter R 10 000 + 5 000 + 55,21 − 10 055,21 = R 5 000,00, which is what the portal shows.<br>**Home tile.** It reads `Last trust movement R 10 055,21 · 07 Nov 2026`: the latest row by transaction date (the 07 Nov refund), not the just-recorded back-dated deposit. That follows the documented order (`transaction_date DESC, recorded_at DESC, id DESC`); see note 2. | `F39-01-portal-trust.png`, `F39-02-portal-home-last-trust-movement.png`, `db-01-trust-before.txt`, `db-04-trust-after.txt` |
| **D2** (633C): portal deadline badges are labels | **PASS** | **List (`/deadlines`).** The court date shows `Hearing — Gauteng High Court · Court date · Due soon · 13 Nov 2026`. The prescription shows `Prescription deadline · Prescription · Upcoming · 26 Nov 2026`. A third row shows `Case management — Gauteng High Court · Court date · Upcoming · 20 Dec 2026`. Each badge span's textContent is "Due soon" / "Upcoming" with `text-transform: none`.<br>**Detail panels.** Court: `STATUS Due soon`. Prescription: `STATUS Upcoming`.<br>The DB holds `DUE_SOON` / `UPCOMING`. | `D2-01-portal-deadlines-list.png`, `D2-02-portal-deadline-detail-court.png`, `D2-03-portal-deadline-detail-prescription.png`, `db-05-d2-deadlines.txt` |
| **Console** | **PASS** (no app errors) | Portal: `favicon.ico` 404, one `401 /portal/auth/exchange` (note 4), and the Next `scroll-behavior: smooth` warning.<br>Firm: Keycloak `favicon.ico` 404 and the Radix "Missing `Description` … for {DialogContent}" warning ×2.<br>The backend log has no ERROR lines since restart. | `console-lerato-portal.log`, `console-thandi.log` |

## Notes / unexpected (none blocks Batch D)

1. **The brief's navigation differs from the UI.** The "Share/Portal" visibility control for a generated document is not on the matter's Documents tab. That tab has no visibility column and only Download / Review-with-AI. The control is at Work › **Generated documents** › "Share with client" (ADR-350, INTERNAL → PORTAL).
   - The portal Activity page has only two tabs, "Your actions" and "Firm actions". There is no ALL tab, so only Firm actions was observed.
   - The ALL-tab query (`findActivityForPortalContact`) was therefore not exercised in the browser. It is covered by the PR's integration test only.
2. **The home tile shows the latest movement by transaction date, not the latest recorded.** After the back-dated deposit, "Last trust movement" still shows the 07 Nov refund (R 10 055,21), not the R 5 000 deposit just recorded. This matches the commit's stated ordering.
   - Meanwhile `portal_trust_balance.last_transaction_at` = 00:48:24Z, the new deposit's recorded instant. That column is not rendered.
   - Flag this only if product wants the tile to mean "most recently recorded".
3. **The F23 share sent no email.** "Email the client now" was left unticked, so no share email reached Lerato. Only the magic-link email and the trust email were sent during the walk.
4. **The magic-link exchange 401 is the same dev-mode artefact as Batch A.** The backend logged "Exchanged magic link for portal JWT … customer ec988df3-…" at 00:45:10Z. The second, HMR re-run of the exchange got 401, and the session loaded signed in.
5. **Firm actions shows no trust deposit or interest events.** Only "Trust refund approved" appears. This is out of scope for Batch D, recorded only as an observation.
6. **Trust navigation.** Portal "Trust statement" goes straight to the single matter ledger `/trust/e0f25d3f-…`.

## Data created (tenant `tenant_5039f2d497cf`)

| What | Id | Detail |
|---|---|---|
| Generated document | `5439af3b-7e98-4af0-b004-bb5c5455b62d` | "Matter Summary Report", RAF matter, PDF 1.4 KB, generated by Thandi 02:44:36 SAST |
| Document | `5f4907ba-aa0b-4c21-9124-671d3fc44ac1` | `matter-summary-report-lerato-mthembu-raf-claim-mva-2025-11-03-2026-11-07.pdf`. Saved INTERNAL, then shared, and now **PORTAL**. |
| Audit events | `157926eb-4578-4c7e-8d96-47e7ad7bd5f4`, `08d8ab9f-57b9-483c-b82b-9931cfd4c63d` | `document.generated`, `document.visibility_changed` |
| Trust deposit | `07b8f69b-d4cf-4c4a-8500-4fbca6ee2884` | Lerato, RAF matter, R 5 000,00, transaction_date 2026-10-15, `DEP/LERATO-g89d`, RECORDED, description "G89 Batch D verify - back-dated deposit -g89d" |
| Portal magic link | Mailpit `h4bJn6P5XijMZS5Lho9s3c` | consumed |
| Trust email | Mailpit `QkA55gTkNQepzfKNaKaL9w` | deposit notification to Lerato |

**Net effect on the tenant:**
- The RAF matter trust balance went from R 0,00 to **R 5 000,00**.
- Lerato's client total went from R 2 000,00 to **R 7 000,00**: R 5 000 on the matter and R 2 000 unmattered. This figure is derived from the firm ledger SQL in `db-04`; I did not open the firm client card in the UI.
- Portal rows on the matter were recomputed: all four now carry their firm transaction dates.
- `REF/LERATO-g89a-over` is still AWAITING_APPROVAL, carried over from Batch A.
