# 648A gate run @ b6afe8c69: found items (report only; the gate runner changed no source)

The aba6f46d5 findings were all **observed fixed** on this head:
- **D-1:** the client-ledger card and list return 200 after approved INV_PLACE / INV_RETURN, the lifecycle `:458` passes, and OBS a and c pass on UI = API = SQL.
- **W-1:** the register fits at 1280 and 1440 with no horizontal scroll.
- **L-1:** the type badge reads "Placed in section 86(3) investment".

## N-1 (COPY on a client document, MEDIUM): the Client Trust Statement prints "Inv place" / "Inv return"

**Kind:** an enum-derived word on a client-facing statutory document. It is reached by 648A, which now posts INV_PLACE / INV_RETURN to the client's CLIENT ledger.

**Observed:**
- `obs/k1-client-trust-statement-preview-after-s86-4-placement.txt` has the line "18 November 2026 **Inv place** INV-8C856A57 Placed in section 86(4) investment at Nedbank (…) R7 500,00 R234 000,00".
- `obs/x3-k2-client-trust-statement-after-withdrawal.txt` adds an "**Inv return**" line.

**Mechanism:** `ClientTrustStatementContext.typeLabel` (`backend/.../trustaccounting/ledger/ClientTrustStatementContext.java`, around line 140) mechanically humanises the enum: `INV_PLACE` → "Inv place". It does not use the registry's `TrustTransactionTypeLabels` (firm or client label). That works for `DEPOSIT` / `FEE_TRANSFER`, but not for the 648A types.

**Contrast:** the Statement of Account (`StatementOfAccountContextBuilder`) uses `TrustTransactionTypeLabels.label`, and its line reads "Placed in section 86(4) investment" (`obs/k1-soa-preview-after-s86-4-placement.txt`).

**Not caught by f:** the f scans cover UI surfaces. This is a generated document, and the words have no underscore.

**Disposition:** report only. The fix layer is an orchestrator call: either `typeLabel` should read the registry's client label, or 648A/648E should give the INV types client-facing words.

## N-2 (COPY, LOW): the s86(3) approval-notification noun still says "investment account"

L-1 (3a467b67b) aligned the type labels: `FIRM_INV_PLACE` now reads "Placed in section 86(3) investment". The noun added in 463e1c64a, `TrustTransactionTypeLabels.placementNoun`, keeps the old form:
- `"FIRM_INV_PLACE"` → "placement in a section 86(3) investment **account**";
- `"INV_PLACE"` → "placement in a section 86(4) investment".

So Bob's bell and notifications page list both:
- "Trust placement in a section 86(4) investment requires approval";
- "Trust placement in a section 86(3) investment account requires approval".

See `obs/10-j1-bell-open.txt` and `obs/11-j1-notifications-page.txt`.

**Disposition:** report only. This is a one-word copy decision.

## O-1 (OBSERVATION, needs a ruling): client-facing balances show the general balance only after an s86(4) placement

After the R7 500 s86(4) placement, the client's **total** in trust is unchanged at R241 500, but:
- the Client Trust Statement reads "The current balance held in trust on your behalf is R234 000,00" and does not mention the R7 500 invested;
- the SoA reads "Trust balance held: R234 000,00";
- the client-ledger page itemises the placement as "less Transfers out R 7 500,00", which leaves "Balance R 234 000,00".

This matches the slice's stated design: the `LedgerTotals` comment in 3a467b67b says "648E adds the separate 'Invested (section 86(4))' figure". It is recorded so the orchestrator can confirm that 648E's scope includes the **client documents**, not only the ledger card. It is not scored against 648A.

## P-1, P-2 (pre-existing, known)

These are unchanged from the aba6f46d5 run:
- **P-1:** the Transactions status column renders `RECORDED` / `APPROVED` / `AWAITING APPROVAL`.
- **P-2:** the F-4 `ACTIVE` badge appears on Settings › Trust accounts.

See `f-substring-recheck.txt`.

## P-4 (pre-existing): trust notification bodies print the raw amount

The body reads "A placement in a section 86(4) investment of **R7500** requires your approval."

`TrustNotificationHandler` concatenates `"R" + event.amount()` for every trust approval notification, so the fee-transfer ones have the same form. It is not 648A-specific. Report only.

## Harness notes

- **Future-date UI check (i):** the Deposit Date input's native `max` blocks the submit (`rangeOverflow`, "Value must be 2026/11/18 or earlier.") before react-hook-form/zod runs. The zod sentence "The deposit date cannot be in the future." is therefore never shown in the UI, because the browser refuses first. The server sentence was observed on the API probes.
- **Withdrawal-date min/max (c):** the OBS investment was deposited today, so min = max = today. A second, past-dated (2026-11-10) s86(3) placement was made and approved through the UI to show min ≠ max (`obs-extra.cjs`). It was left ACTIVE, and the stack was torn down afterwards.
- **Stacked-dialog measure (g):** `obs.cjs` measures the dialog's outer element, whose `overflow-y` is visible; the scrolling container is the inner div. Reachability was proven by `scrollIntoView` (647–683 inside the 720 viewport) and by the successful "Create Account" click.
