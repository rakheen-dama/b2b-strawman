# ADR-353: Firm Letterhead Settings as a `LetterheadSettings` Embeddable on `OrgSettings`; Brand Colour Injected as Concrete CSS Rules by the Renderer; Template Packs Reference the Keys and Reconcile

**Status**: Accepted

**Context**:

The engagement letter and fee-note PDFs that a design partner will read first show no logo on the letter, no brand colour anywhere, no firm address, no client address and "Banking details are available on request" (G-B5, G-C7 a–c; wow moment 6). R4 §1 traced three distinct causes. (1) **Context gap:** `TemplateContextHelper.buildOrgContext:82-125` exposes only `org.name / defaultCurrency / brandColor / documentFooterText / taxRegistrationNumber / logoUrl`; `OrgSettings` (ten embeddables: Branding, PackStatus, DataRequest, Tax, Expense, TimeReminder, Collections, Billing, DataProtection, Portal) has **no** address, phone, email, bank or signatory field, and `Organization` has only `name`; `org.bankingDetails` (SoA) and `org.principal_attorney` (closure letter) are dangling keys. (2) **Brand colour has no delivery mechanism:** `document-default.css:20` uses `var(--brand-color, #1a1a2e)`, nothing defines it, no Tiptap node emits `.header`, headings hard-code `#1a1a2e`, and OpenHTMLToPDF implements no CSS custom properties. (3) **Template content:** the three engagement-letter JSONs never reference `org.logoUrl` (LZKC-007 added it only to the fee note, SoA, closure and cover letter); the fee note omits `customer.addressLine1…` and prints a banking literal. Packs seed once per tenant (`AbstractPackSeeder:143-153`). `OrgSettings` changes are double-pinned (`OrgSettingsSchemaSnapshotTest` + `BaselineSchemaParityTest`, `.arch-context.md` §10.9). Founder decision D5 fixed the field list and the location (Settings › Firm › General, beside logo and brand colour).

**Options Considered**:

1. **New `LetterheadSettings` embeddable on `OrgSettings` with `@AttributeOverride` columns in V4 (CHOSEN).**
   - Pros:
     - Follows the 2026-06 refactor's shape (one embeddable per settings family, zero-join read, snapshot-pinned).
     - `buildOrgContext` reads it in the same call that reads Branding and Tax.
     - One form under Firm › General.
     - One DTO.
     - The renderer, SoA builder and closure builder all see the keys.
   - Cons:
     - Sixteen nullable columns on `org_settings`.
     - V4 shared with other units.
2. **Separate `firm_letterhead` entity / table.**
   - Pros:
     - Keeps `org_settings` narrow.
     - Could version letterheads later.
   - Cons:
     - A second read on every PDF render and in `buildOrgContext`'s batched callers.
     - A new repository, service, controller and audit surface for data that is by definition one row per tenant.
     - The versioning use case is speculative.
3. **Template-level free text (a "letterhead" rich-text block the firm edits per template).**
   - Pros:
     - No schema.
     - Firms can lay it out freely.
   - Cons:
     - Address and bank details would be typed into every template (engagement ×3, fee note, SoA, closure, cover letter) and drift.
     - No structured banking block for the fee note's statutory content.
     - The brand colour problem is untouched.

**Decision**: Option 1 — `LetterheadSettings` (address lines 1–3, city, postal code, country, phone, email, website, bank name, branch code, account number, account type, payment reference note, signatory name, signatory designation) as an `@Embeddable` on `OrgSettings` with V4 columns; `SettingsResponse` / `UpdateLetterheadSettingsRequest`; a form on Settings › Firm › General next to logo and brand colour; `buildOrgContext` exposes `org.address` (joined lines), `org.phone`, `org.email`, `org.website`, `org.bankingDetails` (a map: `bankName`, `branchCode`, `accountNumber`, `accountType`, `referenceNote`) and `org.signatory` (`name`, `designation`); `TiptapRenderer.render` injects the brand colour as **concrete CSS rules** (`h1,h2,h3 { color }`, `hr` / rule lines, `.letterhead` border) appended after `document-default.css`, defaulting to `#1a1a2e` when unset; the legal-za template pack bumps its version so engagement letters gain `{{org.logoUrl}}` + a letterhead block, the fee note gains the client address block and a real banking block, with optional labels wrapped in `conditionalBlock`; the pack change reaches existing tenants through [ADR-356](ADR-356-pack-content-reconciliation.md).

**Rationale**:

1. **The fields are firm identity, one row per tenant** — exactly what `OrgSettings` embeddables model; the schema-per-tenant table already carries Branding (logo, colour, footer) and Tax (VAT number), so the letterhead belongs beside them and the form belongs on the page where the firm already uploads its logo (D5).
2. **Concrete rules because the PDF engine has no variables.** OpenHTMLToPDF renders CSS 2.1 plus selected CSS 3; `var()` is silently ignored, which is why the existing `--brand-color` never worked. Emitting `h1,h2,h3{color:#1B3A4B}` at render time is the only mechanism that reaches the PDF; the HTML preview gets the same rules, so preview and PDF agree. Scope is headings, rule lines and the letterhead border (D5) — not table headers, which stay neutral for legibility.
3. **Templates reference keys, never values.** The engagement letters bind `org.logoUrl`, `org.address`, `org.signatory`; the fee note binds `org.bankingDetails.*` and `customer.address*`; a firm that has not filled a field gets a `conditionalBlock`-hidden label, not a dangling "Banking details:" line (R4 §1.4).
4. **Demo-partner timeline:** the walked tenant and demo tenants must print the new blocks, so the pack change is not fresh-tenant-only; the reconcile ADR is a hard dependency.

**Consequences**:

- Positive:
  - The engagement letter and fee-note PDFs show logo, brand-coloured headings, firm address block, client address, VAT number, due date ([ADR-347](ADR-347-fee-note-due-date-and-balance-model.md)) and banking details for the walked tenant (G-B5, G-C7 a–c).
  - `org.signatory.name` backs the closure letter's signatory; `org.principal_attorney` stays as a deprecated alias of `org.signatory.name` for one release so the closure letter keeps rendering.
  - `StatementOfAccountContextBuilder:228`'s empty `bankingDetails` placeholder is removed.
- Negative:
  - `OrgSettingsSchemaSnapshotTest.EXPECTED_SNAPSHOT` is updated for the new columns (an authorised change, never a pass-making edit).
  - `OrgSettingsEmbeddableNullReloadTest` gains the embeddable.
  - `BaselineSchemaParityTest` stays pinned to V1 and is re-run.
  - `PdfRenderingServiceTest` / `VisualRegressionTest` goldens change (headings colour, letterhead block).
  - `TiptapRendererTest` gains an injected-rules case.
  - `TemplateContextHelper` tests gain the keys.
  - The settings form gets a vitest.
  - `VariableMetadataRegistry` gains the `org.*` entries so the template picker offers them (labels per [ADR-357](ADR-357-terminology-guard-extension.md)).
  - V4 is shared with [ADR-345](ADR-345-canonical-project-role-values.md), [ADR-347](ADR-347-fee-note-due-date-and-balance-model.md), [ADR-354](ADR-354-persisted-retention-end-date.md).
- Related:
  - [ADR-356](ADR-356-pack-content-reconciliation.md), [ADR-336](ADR-336-engagement-letter-document-plus-acceptance.md), memory `feedback_tiptap_not_thymeleaf` (the draft preview's Thymeleaf renderer is retired by U9 so it cannot diverge from these rules), `tasks/phase88-research/R4-documents-rendering-letterhead.md` §1, §11 F8, `tasks/phase88-gap-register.md` D5.
