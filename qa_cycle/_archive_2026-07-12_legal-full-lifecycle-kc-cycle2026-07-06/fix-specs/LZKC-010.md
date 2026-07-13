# Fix Spec: LZKC-010 — "Invoice Cover Letter" renders "Invoice Number:" blank

## Problem
Day 28 / 28.5 and Day 30 / 30.8: the generated Invoice Cover Letter PDF renders "Invoice Number:" blank for INV-0001 (and QA also observed "Total Amount:" blank in the sent artefact).

## Root Cause (verified)
- Template `backend/src/main/resources/template-packs/common/invoice-cover-letter.json:37` uses placeholder key **`invoice.number`**, but the context builder populates **`invoice.invoiceNumber`** (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceContextBuilder.java:63`). `TiptapRenderer.resolveVariable` (`template/TiptapRenderer.java:276-289`) returns `""` for missing keys → blank. The accounting-za template uses the correct spelling (`template-packs/accounting-za/invoice-za.json:51`).
- "Total Amount" (`invoice.total`, template line 45): research indicates the context key `total` IS populated (`InvoiceContextBuilder.java:71`) — but QA observed it blank in the PDF. **Dev must reproduce first (CLAUDE.md §4)** and check whether `total` is nested under the `invoice` map in the context (if it's top-level, the `invoice.total` path also misses → same class of fix).

**NOT clustered with LZKC-018** — code inspection disproved the suspected shared mechanism: this is a placeholder-name typo inside a correctly-dispatched builder; LZKC-018 is a builder-dispatch failure (see LZKC-018.md).

## Fix
1. `common/invoice-cover-letter.json:37`: change `invoice.number` → `invoice.invoiceNumber`.
2. Reproduce the blank Total Amount; if `total` is not reachable at `invoice.total`, align key or template accordingly.
3. **Seeding caveat:** template content is seeded from the classpath pack into per-tenant DB rows (`DocumentTemplate`, JSONB). Verify whether template-pack reconciliation re-applies content changes to existing tenants at startup; if it is idempotent-by-presence (like automation packs, `packs/PackInstallService.java:138-143`), the fix needs a reconciliation bump/migration for existing tenants — dev must confirm which mechanism template packs use and cover the existing QA tenant.

## Scope
Backend only (template JSON + possible seeder/migration follow-through)
Files to modify: `backend/src/main/resources/template-packs/common/invoice-cover-letter.json`
Files to create: possibly 1 tenant migration/reconciliation bump (see caveat)
Migration needed: maybe (existing-tenant template refresh)

## Verification
Regenerate the Invoice Cover Letter for INV-0001 (or a scratch invoice) on the Keycloak stack: PDF shows "Invoice Number: INV-000x" and a populated Total Amount. Unit test: render cover-letter template against `InvoiceContextBuilder` output and assert non-blank fields.

## Estimated Effort
S (< 30 min) — M if existing-tenant template refresh needs a migration
