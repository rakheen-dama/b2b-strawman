# Fix Spec: LZKC-012 (HIGH) — Client-facing fee-note PDF is the empty cover letter; no receipt

## Problem
Day 30 / 30.8: the portal "Download PDF" and the email attachment `INV-0001.pdf` are the same 1183-byte generated **Invoice Cover Letter** (blank Invoice Number/Total Amount, no line items) — the client never receives a real fee-note document. There is also no receipt/payment-confirmation artefact after paying.

## Root Cause (verified)
1. **Send path attaches the wrong template.** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceEmailEventListener.java:68-72` selects `documentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(INVOICE).stream().findFirst()`. For a legal-za tenant the only active INVOICE-type template is `common/invoice-cover-letter` (`template-packs/common/pack.json:27-36`) — so the cover letter is generated (lines 96-102) and attached as `invoiceNumber + ".pdf"` (`InvoiceEmailService.java:110-111`).
2. **No line-item fee-note/invoice template exists for common/legal-za.** A proper line-item template exists only in the accounting-za pack (`template-packs/accounting-za/invoice-za.json` — `loopTable` over `lines` l.87-98, correct keys `invoice.invoiceNumber` l.51, `invoice.total` l.128, `org.taxRegistrationNumber`).
3. **A better selector exists but is unused.** `GeneratedDocumentService.resolveDefaultInvoiceTemplate()` (`GeneratedDocumentService.java:392-411`) prefers `invoice-za`/`invoice` slugs — the send path never calls it.
4. **Portal download mirrors the send artefact.** `customerbackend/service/PortalReadModelService.getInvoiceDownloadUrl` (lines 165-179) presigns the most recent `GeneratedDocument` of type INVOICE — i.e. the same cover letter. Fixing what gets generated at send fixes the portal automatically.
5. **Receipt:** no post-payment document generation exists anywhere in the `invoice` package — a genuine feature gap, not a wiring bug.
6. Blank cover-letter fields are LZKC-010 (separate spec, `invoice.number` key typo).

## Fix (core — makes the client receive a real fee note)
1. Create `template-packs/legal-za/fee-note-za.json`: line-item fee-note template adapted from `accounting-za/invoice-za.json` (loopTable over `lines`, subtotal/VAT/total, `org.taxRegistrationNumber`, "Fee Note" wording, matter/file reference — dev: check `InvoiceContextBuilder` for the project/matter code key and include it, per LZKC-007 evidence "no matter reference code").
2. Register it in `template-packs/legal-za/pack.json` with `primaryEntityType: INVOICE`.
3. Change the send path to use the intended selector: `InvoiceEmailEventListener.java:68-72` → call `GeneratedDocumentService.resolveDefaultInvoiceTemplate()`; extend that method's preference list (`GeneratedDocumentService.java:392-411`) to include `fee-note-za` ahead of the cover letter.
4. Existing-tenant caveat (same as LZKC-010): template packs are seeded per tenant — dev must verify pack reconciliation delivers the new template to already-provisioned tenants (if install is idempotent-by-presence like `packs/PackInstallService.java:138-143`, a reconciliation bump is needed).

## Receipt sub-item — DEFERRED-PROPOSED
Payment-receipt artefact generation (new template + hook in `PaymentReconciliationService` + portal surfacing) is a net-new feature (> 2 hr, product design needed: receipt content, numbering, visibility). Propose deferring to an epic; the High-severity core (client gets a real fee note) is covered by the fix above.

## Scope
Backend only
Files to modify: `InvoiceEmailEventListener.java`, `GeneratedDocumentService.java`, `template-packs/legal-za/pack.json`
Files to create: `template-packs/legal-za/fee-note-za.json` (+ possible reconciliation bump/migration)
Migration needed: maybe (existing-tenant template seeding)

## Verification
Re-run Day 28 send + Day 30 portal flow with a scratch fee note: Mailpit attachment `INV-000x.pdf` contains line items, amounts, totals, matter reference; portal "Download PDF" serves the same real document. Accounting-za tenant regression: still gets `invoice-za`.

## Estimated Effort
M–L (likely ~2 hr; template JSON is adapted, not authored from scratch). **Orchestrator authorization requested** given the >2h risk and the DEFERRED receipt sub-item.

## Cluster members
Interacts with (but does not include): LZKC-010 (cover-letter key typo), LZKC-007/017 (letterhead/banking), LZKC-009 site 3 (cover-letter display name).
