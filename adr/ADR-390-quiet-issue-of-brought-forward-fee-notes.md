# ADR-390: Brought-forward fee notes are issued quietly: SENT without send's side effects

**Status**: Accepted

**Context**:

Kazi has no accounts-receivable ledger; what a client owes is the sum of its outstanding fee notes (`invoices` with status SENT or PARTIALLY_PAID). The requirements (E6) take on debtor opening balances as one brought-forward fee note per row — a single MANUAL line "Balance brought forward as at {cutover}" with the source reference and the source issue and due dates — that reaches the outstanding state so aging, statements, dashboards, the CashDigest and (if enabled) collections treat it as ordinary, with **no** email, payment link, in-app notification, Xero sync or automation trigger, and portal visibility defaulting to *visible*.

At HEAD the only route to SENT is `InvoiceTransitionService.send()`: `validateInvoiceSend` (branding, client fields, tax number), `markSent()` from APPROVED, `generatePaymentLink` (`:259`), the `invoice.sent` audit, `InvoiceSentEvent` (`:285`) — whose listeners email the client the fee-note PDF (`InvoiceEmailEventListener`, `:45`), notify staff (`NotificationEventHandler`, `:271`), push to Xero (`AccountingSyncEventListener`, `:50`) and trigger `INVOICE_STATUS_CHANGED` automations — and `InvoiceSyncEvent` (`:320`) for the portal. Reaching APPROVED first consumes the firm's number sequence and publishes `InvoiceApprovedEvent` (notification + Xero). `createDraft` first runs `validateInvoicePrerequisites`: legacy status ACTIVE, the lifecycle guard, and the `INVOICE_GENERATION` prerequisite, which at HEAD requires address line 1, city, country and an email or portal contact — the tax number moved to send time (`StructuralPrerequisiteCheck.java:77-78`; the requirements' scouting note predates this).

**Options Considered**:

1. **Call `approve` and `send` with flags that suppress each side effect.**
   - Pros: One path to SENT.
   - Cons: A `quiet` flag threaded through approval, numbering, validation, payment links and four event listeners; every future listener on `InvoiceSentEvent` must remember to honour it; the firm's sequence is still consumed.
2. **Store brought-forward balances outside `invoices` (an opening-AR table).**
   - Pros: No interaction with the invoice lifecycle.
   - Cons: Aging, statements of account, client detail, dashboards, the CashDigest and collections all read `invoices`; every one would need a second source; payments against an opening balance would need their own model.
3. **Insert invoices directly in SENT with SQL-level defaults, bypassing the entity.**
   - Pros: Quick.
   - Cons: Bypasses the entity's guarded transitions and the customer snapshot; no audit; easy to produce a fee note other code cannot handle.
4. **A dedicated quiet issue path: an entity transition DRAFT → SENT legal only for a take-on batch, reached from one service callable only from take-on, that publishes only the portal sync event (chosen).**
   - Pros: Every side effect of approve and send is absent by construction, not by flag; the result is an ordinary SENT fee note every reader already understands; the normal path is untouched; portal visibility is a deliberate event.
   - Cons: A second way into SENT, guarded by a non-null batch id, a CHECK and ArchUnit; the shared draft-snapshot builder must be extracted from `createDraft`.

**Decision**: Brought-forward fee notes are created by `BroughtForwardFeeNoteService.issue`, callable only from the take-on package, which applies the legacy-status and lifecycle checks but not the `INVOICE_GENERATION` prerequisite, builds the draft with the same customer snapshot as `createDraft`, adds one untaxed MANUAL line "Balance brought forward as at {date} — fee note {number}", and calls `Invoice.issueBroughtForward` (DRAFT → SENT, source number, source issue and due dates, `take_on_batch_id`), auditing `invoice.created` and `invoice.brought_forward_issued` without amounts and publishing only `InvoiceSyncEvent` "SENT" with no payment URL; nothing else of approve or send runs.

**Rationale**:

A brought-forward fee note is a fact about the past: the previous system generated it, numbered it, sent it and collected nothing yet. That is why the gates and side effects of *generating and sending* do not apply to it, and why the answer is a separate path rather than a suppressed send. The `INVOICE_GENERATION` prerequisite exists so a fee note Kazi generates can be rendered and delivered; applying it here would refuse to record a debt the client really owes because an address field is blank. It is not weakened anywhere else: `createDraft` still runs it first, and the quiet path is reachable only from a take-on commit. The validation warns about a missing address so the firm knows what it needs before it bills that client anew. The source number is kept so client and auditor recognise the fee note, and the firm's sequence is not consumed; a collision with an existing number is a row error (FD-20). The line is untaxed because the outstanding amount is VAT-inclusive and VAT was accounted for when the previous system issued the fee note.

Portal visibility follows the requirements' default: the client owes the money, so the portal shows it (once the client has a portal contact), without a payment link because none was generated (FD-9). Downstream needs no change: the CashDigest counts SENT notes in outstanding and aging and, with a historic issue date, not in "billed this week"; collections scans only when `collections_enabled` (default false) and sends only through the human-approved gate; payments move them to PARTIALLY_PAID or PAID as usual.

**After the take-on, events are real — except Xero.** Quiet applies to the *issue*, not to the fee note's later life. A payment received in Kazi, or a Void of a wrongly imported balance (Void stays available, FD-27), is a genuine post-cutover event, so `InvoicePaidEvent`, `InvoicePartiallyPaidEvent` and `InvoiceVoidedEvent` keep their listeners: staff notifications, `INVOICE_STATUS_CHANGED` automations, the collections listener and the portal sync. The exception is the accounting sync: `AccountingSyncEventListener.onInvoiceVoided` (`:69`) and the other invoice triggers all call `AccountingSyncService.enqueueInvoicePush` (`:91`), which would push a void or a later state of an invoice Xero never received from Kazi (and that the firm's accounting system already holds under its own record). The guard sits in `enqueueInvoicePush` — an invoice with `take_on_batch_id` is never enqueued — rather than in each listener, so every present and future trigger is covered (FD-28). Suppressing the staff and automation events too was rejected: a firm's "invoice paid" automation or a collections stop-on-payment rule must see a real payment whatever the fee note's origin. The CHECK `chk_invoice_brought_forward_status` keeps a batch-linked fee note from ever being a draft or approved-unsent; `issueBroughtForward` sets the batch id and SENT in one update so the CHECK holds at every flush.

**Consequences**:

- Positive:
  - A debtor take-on sends nothing — no email, PDF, payment link, notification, Xero push or automation.
  - Imported debts appear wherever outstanding fee notes appear, with no reader changed.
  - The normal fee-note gates and lifecycle are unchanged.
- Negative:
  - A second route into SENT that reviewers must keep narrow.
  - Brought-forward fee notes have no PDF of their own; a statement of account is the client-facing document for them.
  - Imported debtors are not pushed to Xero, where they presumably already are — not at issue, and not when later paid or voided in Kazi; the firm records those in its accounting system as it did before (FD-28).
  - A later payment or void of a brought-forward fee note does notify staff and run automations, which is intended.
  - Number collisions between the old system and Kazi's own sequence are possible later if formats overlap; the unique index refuses them, but the firm must then choose a different sequence prefix.

**Related**: [ADR-385](ADR-385-take-on-architecture.md), [ADR-389](ADR-389-imported-client-lifecycle-entry-path.md) (clients created without send-side effects for the same reasons); [architecture §17.3.10, §17.5.4](../architecture/phase93-client-trust-take-on.md); founder decisions FD-9, FD-20, FD-21, FD-26, FD-27, FD-28; slice 658A.
