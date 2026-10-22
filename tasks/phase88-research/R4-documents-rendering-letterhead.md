# R4 — Document generation / rendering / branding cluster: root-cause dossier

Scope: G-B5, G-C7, G-B6, G-C4, G-B18, G-C12 (SoA category key), G-D14 (closure-letter formatting), G-B16.
Read-only research on `main` @ 50b88ed5c (2026-10-22). All paths relative to repo root. Status markers: **CONFIRMED** = read in source; **HYPOTHESIS** = inferred, not executed.

---

## 0. Architecture map (two renderers, three pipelines)

| Pipeline | Entry | Context | Renderer | Format hints | Persists row? |
|---|---|---|---|---|---|
| Template documents (engagement letters, fee note, OTP, closure letter) | `DocumentTemplateController.generateDocument` `:172` / `previewTemplate` `:153` → `GeneratedDocumentService.generateDocument` `:173` → `PdfRenderingService.generatePdf` `:97` | `ProjectContextBuilder` / `CustomerContextBuilder` / `InvoiceContextBuilder` + `TemplateContextHelper.buildOrgContext` `:82` | `TiptapRenderer.render` `:85` + `document-default.css` + OpenHTMLToPDF (`PdfRenderingService.htmlToPdf` `:247`) | `VariableMetadataRegistry` via `PdfRenderingService.buildFormatHints` `:219` | **always** (`createRecord` `:230`) |
| Statement of Account | `StatementService` → `renderHtml` `:362` → `tiptapRenderer.render(..., StatementOfAccountContextBuilder.formatHints())` | `StatementOfAccountContextBuilder.build` (own namespaces + `buildOrgContext`) | same TiptapRenderer | **own** `FORMAT_HINTS` map `StatementOfAccountContextBuilder.java:77-96` (bypasses registry) | yes |
| Fee-note **draft Preview** button | `InvoiceController.preview` `:79` → `InvoiceService.renderPreview` `:220` → `InvoiceRenderingService.renderPreview` `:66` | Thymeleaf `Context` (invoice entity, groupedLines, tax labels) | **Thymeleaf** `resources/templates/invoice-preview.html` (legacy, Epic 85B) | none — `#numbers.formatDecimal` | no |

Frontend: `frontend/app/api/invoices/[id]/preview/route.ts` proxies the draft preview; `components/invoices/use-invoice-detail.ts:238` opens it in a new tab. Template previews render in `components/documents/a4-preview-wrapper.tsx` (iframe `srcDoc`), used by `GenerateDocumentDialog.tsx:287`, `TemplatePreviewDialog.tsx`, `editor/PreviewPanel.tsx`, `legal/statement-of-account-dialog.tsx:283`.

---

## 1. Rendering context — what templates can see (Q1)

### 1.1 `org.*` (CONFIRMED — `TemplateContextHelper.buildOrgContext` `template/TemplateContextHelper.java:82-125`)

| Key | Source | Exists? |
|---|---|---|
| `org.name` | `Organization.name` via `OrgSchemaMapping` (`resolveOrgName` `:127`) | yes |
| `org.defaultCurrency` | `OrgSettings.defaultCurrency` | yes |
| `org.brandColor` | `BrandingSettings.brandColor` (`settings/BrandingSettings.java:26`) | yes — **but nothing consumes it** (see 1.3) |
| `org.documentFooterText` | `BrandingSettings.documentFooterText` `:29` | yes |
| `org.taxRegistrationNumber` | `TaxSettings.taxRegistrationNumber` `:29` | yes |
| `org.logoUrl` | presigned URL from `BrandingSettings.logoS3Key` `:23`; rendered as `<img class="letterhead-logo">` by `VariableFormatter.formatImage` `:106-116` when the hint is `image` (`VariableMetadataRegistry.java:332`) | yes |
| `org.address*`, `org.phone`, `org.email`, `org.website` | — | **does not exist** |
| `org.bankingDetails` | only in SoA: `StatementOfAccountContextBuilder.java:228` `orgMap.putIfAbsent("bankingDetails", "")` — hard-coded empty placeholder | **no backing field** |
| `org.principal_attorney` | referenced by `matter-closure-letter.json`; `MatterClosureContextBuilder.java:41` says "no backing OrgSettings field yet and intentionally stays blank" | **does not exist** |

`OrgSettings` embeddables (CONFIRMED `settings/OrgSettings.java`): `BrandingSettings` (logoS3Key, brandColor, documentFooterText), `PackStatusSettings`, `DataRequestSettings`, `TaxSettings` (taxInclusive, taxRegistrationNumber, taxRegistrationLabel, taxLabel), `ExpenseSettings`, `TimeReminderSettings`, `CollectionsSettings`, `BillingSettings`, `DataProtectionSettings` (jurisdiction, retention, informationOfficerName/Email), `PortalSettings`. **No firm address, phone, email, bank account, or signatory fields anywhere** — `provisioning/Organization.java` has only `name` (`:26`).

### 1.2 Entity context

- `project.*` (`ProjectContextBuilder.java:68-80`): id, name, description, createdAt, **referenceNumber**, priority, workType, customFields (+ aliases `reference_number`, `matter_type`…).
- `customer.*` (`CustomerContextBuilder.populatePromotedCustomerFields` `:170-185`, reused by Project/Invoice builders): taxNumber, **addressLine1/2, city, stateProvince, postalCode, country**, contactName/Email/Phone, entityType, registrationNumber. Invoice builder additionally sets `customer.address` = `invoice.getCustomerAddress()` (`InvoiceContextBuilder.java:139`) and top-level `customerVatNumber` `:153`.
- `invoice.*` (`InvoiceContextBuilder.java:60-85`): invoiceNumber, status, issueDate, **dueDate**, subtotal, taxAmount, total, currency, notes, poNumber, taxType, billingPeriod*; `lines[]` with description/quantity/unitPrice/amount/taxAmount/taxRateName/lineTotal.
- Registry hints (CONFIRMED `VariableMetadataRegistry.java:257-261`): invoice.issueDate/dueDate `date`; subtotal/taxAmount/total `currency`. Loop-table columns carry their own `format` attr (`TiptapRenderer.renderLoopTable` `:292-315`).

### 1.3 Gap-by-gap: variable exists vs. template doesn't use it

| Gap item | Verdict |
|---|---|
| Logo in engagement letter (G-B5) | **Variable exists, template doesn't use it.** `engagement-letter-{litigation,general,conveyancing}.json` reference only `org.name` ×3, `customer.name` ×2, `project.name`, `org.documentFooterText` (+ 2 custom fields for litigation). `fee-note-za.json`, `statement-of-account.json`, `matter-closure-letter.json`, `common/invoice-cover-letter.json` reference `org.logoUrl` — hence the logo shows on the fee note but not the engagement letter. **Same code path**, different template content (CONFIRMED by key grep). |
| Brand colour (G-B5, G-C7a) | **Variable exists, no mechanism applies it.** `document-default.css:20` uses `var(--brand-color, #1a1a2e)` on `.header`, but (a) nothing defines `--brand-color` — `TiptapRenderer.render` `:101` injects only `defaultCss + templateCss`, no `:root{--brand-color}`; (b) no Tiptap node emits `class="header"`; (c) `h1,h2,h3` are hard-coded `#1a1a2e` (`document-default.css:52`); (d) HYPOTHESIS: OpenHTMLToPDF does not implement CSS custom properties, so even a defined var would only affect the HTML preview. `rg "brand-color|--brand"` in backend Java → 0 hits. |
| Firm address / contact letterhead (G-C7a) | **Variable does not exist** (1.1). Needs new OrgSettings fields. |
| Banking details (G-C7b) | **Variable does not exist.** `fee-note-za.json` prints the literal "Banking details are available on request or as provided in your engagement letter." (flattened text, Payment Details section); SoA template binds `org.bankingDetails` to the empty placeholder. |
| Client address (G-C7a) | **Variable exists, fee-note template doesn't use it** (`customer.addressLine1…`, `customer.address`). The capstone client had an address (Moroka "8 Rissik St", days-08-21.md 8.2). |
| "VAT Registration No:" blank (G-C7c) | Template binds `org.taxRegistrationNumber` (exists). HYPOTHESIS (data): the walk never filled Settings › Tax › tax registration number (no such step in days-01-07/08-21 evidence; the *client* VAT 9123456789 did render at 47.2). |
| "Matter Reference:" blank | Template binds `project.referenceNumber` (exists, `Project.java:109`). HYPOTHESIS (data): matters created from templates don't populate `referenceNumber` (cf. G-B9 no case-number rendering); worth a one-line DB check on the capstone tenant. |
| "Due Date:" blank | Template binds `invoice.dueDate` (exists). CONFIRMED: `Invoice.applyDefaults` `invoice/Invoice.java:220-227` only fills a default due date for **APPROVED** invoices during batch send; a draft or single-send fee note has `dueDate = null` unless the user typed one. So the label prints blank by design of the data, not the renderer. |

### 1.4 Null handling
`VariableFormatter.format` `:33` returns `""` for null → labels render with nothing after them. There is no "hide label when empty" node; `conditionalBlock` exists (`TiptapRenderer.java:181`, operators incl. `isNotEmpty`) but the fee-note template doesn't wrap the optional lines in one.

---

## 2. Fee-note vs engagement-letter templates (Q2)

- `template-packs/legal-za/fee-note-za.json` (flattened): `{{org.logoUrl}}` / FEE NOTE / From `{{org.name}}` / VAT Registration No `{{org.taxRegistrationNumber}}` / To `{{customer.name}}` / Client VAT Number `{{customerVatNumber}}` / Matter `{{project.name}}` / Matter Reference `{{project.referenceNumber}}` / Fee Note Number `{{invoice.invoiceNumber}}` / Issue Date `{{invoice.issueDate}}` / Due Date `{{invoice.dueDate}}` / Currency `{{invoice.currency}}` / loopTable `lines` (quantity:number, unitPrice/taxAmount/lineTotal:currency) / Subtotal/VAT/Total Due `{{invoice.subtotal|taxAmount|total}}` / Payment Details literal (banking on request) / s20 VAT Act note / `{{org.documentFooterText}}`. `cssFile: null`, no custom CSS.
- `legal-za/engagement-letter-litigation.json`: H1 `{{org.name}}` (plain heading, no logo), Matter Details (customer.name, project.name, customFields.case_number/court_name), scope bullets, 5 `clauseBlock`s (legal-za-mandate-scope … data-protection), signature `{{org.name}}`, footer.
- Both go through the **same** `PdfRenderingService` → `TiptapRenderer` path (fee note is `primaryEntityType: INVOICE` → `InvoiceContextBuilder`; letters are `PROJECT` → `ProjectContextBuilder`; both call `buildOrgContext`). The only difference is the template JSON. Attribution: logo variable + `img.letterhead-logo` CSS were added in LZKC-007 (`eeece14c1`, 2026-07-08) to fee-note/SoA/closure/cover-letter only; the three engagement letters were not touched.
- Pack delivery caveat (CONFIRMED `seeder/AbstractPackSeeder.java:143-153`, `db/BASELINE.md:75`): installed packs are **never re-applied** — editing a pack JSON reaches only freshly provisioned tenants (e2e stack after `e2e-down/up`). Existing tenants need an explicit reconcile step or migration. `DocumentTemplateService.resetToDefault` `:488` only resets clones.

---

## 3. Draft fee-note "Preview" and money formatting (Q3)

- **Where**: `InvoiceRenderingService.renderPreview` `invoice/InvoiceRenderingService.java:66-141` → Thymeleaf `resources/templates/invoice-preview.html`. CONFIRMED raw numbers: `:281` `#numbers.formatDecimal(line.quantity, 1, 4)` → "1.5000"; `:282` `(line.unitPrice, 1, 2)` → "1200.00"; `:283/:293/:304/:309/:314/:324` same for amount/subtotal/tax/total; `:323` prints `'Total (' + currency + ')'` instead of a symbol. No `<img>` / logo (template has `invoice.orgName` h1 at `:231` only). This is the Thymeleaf pipeline the memory rule "Tiptap not Thymeleaf" is about — it predates the Tiptap renderer (Epic 85B `2db18dfe5`) and was never migrated.
- **Backend canonical money formatter**: `template/VariableFormatter.formatCurrency` `:76-85` — `NumberFormat.getCurrencyInstance(Locale.of("en","ZA"))` → "R1 200,00" (JDK CLDR; the SoA iframe showed exactly this, days-36-60.md 30.4). Second, divergent implementation: `portal/notification/PortalEmailNotificationChannel.formatAmount` `:445+` hard-codes `R` + space grouping + comma decimal (comment `:440-443` explains it distrusts `getCurrencyInstance` on small-ICU hosts). Gate message `TrustBalanceZeroGate.java:48-49` prints `"R%s".formatted(balance.toPlainString())` → "R38000.00" (G-D14 third item) — same class, no formatter used.
- **Frontend canonical**: `packages/shared/src/format.ts:35` `formatCurrency(amount, currency="ZAR")` → `Intl.NumberFormat("en-ZA", {style:"currency"})` (with `frontend/lib/intl-polyfill.ts` to force full-ICU on Node; doc comment at `:27` still says "R 1,250.00" but the app observes "R 18 400,00" — `lib/clients-list.ts:221`). `frontend/CLAUDE.md` mandates it ("don't reimplement locally").
- **Tariff dialog (G-C7e)** CONFIRMED: `components/legal/tariff-line-dialog.tsx:23-25` and `components/legal/tariff-item-browser.tsx:20-22` each define a local `formatZAR = \`R ${amount.toFixed(2)}\`` → "R 195.00". `__tests__/legal/tariff-invoice.test.tsx:190` pins `"R 850.00"` and must move with the fix.
- Fix shape: replace `#numbers.formatDecimal` in `invoice-preview.html` with a `VariableFormatter`-backed helper exposed on the Thymeleaf context (e.g. pre-format strings in `InvoiceRenderingService` or register a small `@Component` util), add `org.logoUrl` (reuse `TemplateContextHelper.buildOrgContext`) — or retire the Thymeleaf preview and point the Preview button at the Tiptap fee-note template (`POST /api/templates/{feeNoteId}/preview`). The latter removes the second renderer and the duplicated formatting rules in one move; `InvoicePreviewIntegrationTest` / `InvoicePreviewTaxIntegrationTest` pin labels ("Total (ZAR)", "VAT (15.00%)") not numbers, so either is feasible.

---

## 4. G-B6 — which endpoint persists `generated_documents` (Q4)

CONFIRMED: `GeneratedDocumentService.generateDocument` `template/GeneratedDocumentService.java:173-270` **always** uploads to S3 (`:222-224`) and `createRecord`s a `GeneratedDocument` (`:230-238`) before the `saveToDocuments` branch (`:259-265`, which only adds the linked `Document` + `linkToDocument`). The controller `DocumentTemplateController.generateDocument` `:172-204` returns the PDF bytes when `saveToDocuments=false` and a 201 otherwise. The dialog's "Download PDF" (`components/templates/GenerateDocumentDialog.tsx:166-173`) calls `generateDocumentAction(templateId, entityId, false, …)` → so every download leaves a row with `document_id = NULL`. The list endpoint `GeneratedDocumentController.listGeneratedDocuments` `:40` returns all rows; `GeneratedDocumentsList.tsx` renders them identically (no use of `documentId`; the only differentiator is the acceptance badge).

The same mechanism is deliberate elsewhere: `InvoiceEmailEventListener.java:98-104` generates the fee-note attachment with `saveToDocuments=false` "so the same rendered PDF that was persisted" is emailed, and `InvoiceSentDocumentPersistenceIntegrationTest.java:289-298` pins the row count — this is the "two Fee Note rows" seen at 38.3. `DocumentGenerationIntegrationTest.java:200-240` also asserts rows (S3 key, context snapshot) after `saveToDocuments=false` generations, and `:316-330` relies on clause snapshots stored on unsaved generations.

**Recommendation**: do **not** stop persisting on download (it is the audit/attachment trail and three tests pin it). Instead: (a) expose `documentId` is already in `GeneratedDocumentListResponse` (`:909-920`) — render a "Not saved" badge / hide-unsaved toggle in `GeneratedDocumentsList.tsx` and in the matter Documents tab; (b) optionally dedupe in the dialog: when the user clicks "Save to Documents" after "Download PDF" in the same session, pass the earlier generated-document id and `linkToDocument` it instead of regenerating (new `PATCH /api/generated-documents/{id}/save` or `saveToDocuments` with `existingId`). Product question: should download-only generations appear in the matter's list at all?

---

## 5. G-C4 — SoA preview scaler runaway (Q5)

CONFIRMED mechanism (`components/documents/a4-preview-wrapper.tsx:18-31`): the wrapper observes **its own** `containerRef` (which has `p-6`, i.e. 24 px padding each side) and sets `scale = clientWidth / 794`; it then renders a child with `width: A4_WIDTH * scale` = **clientWidth** (`:48-50`). `clientWidth` includes padding, so the child is always 48 px wider than the content box → overflow by 48 px on every pass.

Why the SoA dialog loops but the Generate dialog does not (HYPOTHESIS, CSS-Grid sizing reasoning — not executed):
- Both use `DialogContent` which is `display:grid` (`components/ui/dialog.tsx:70`) with an implicit `auto` column.
- In `GenerateDocumentDialog.tsx:280` the grid item is the `overflow-y-auto` div itself (a scroll container → automatic minimum size **0**), so the overflowing child cannot widen the track; the wrapper's `clientWidth` stays fixed and the 48 px just overflows/clips.
- In `statement-of-account-dialog.tsx:243` the grid item is the `<form className="space-y-4">` (not a scroll container); its minimum contribution is its min-content width = the sized child (scaled A4 + 48 px). The `auto` track grows to that, which grows the wrapper's `clientWidth` by 48 px, the ResizeObserver fires, scale grows, repeat — the form widens without bound (the FIXME comment in `day-30-45-soa-writeoff.spec.ts:205-213` recorded ≈568 000 px scrollWidth).
- `TemplatePreviewDialog.tsx` / `PreviewPanel.tsx` mount the wrapper in plain block flow → stable.

Fix shape (small, in the wrapper so all consumers benefit): read `entry.contentRect.width` from the `ResizeObserver` entries (content box, excludes padding) instead of `clientWidth`, or size the outer div `width: 100%` and only compute height — then the fixed point is exact and no consumer layout can feed back. Belt-and-braces: `min-w-0 overflow-hidden` on the wrapper. `__tests__/components/documents/a4-preview-wrapper.test.tsx` stubs `ResizeObserver` (lines 6-11, 50-57) and only asserts srcDoc/testids/disconnect, so it needs a new case that feeds a padded container and asserts the inner width ≤ container width. Then un-`fixme` `day-30-45-soa-writeoff.spec.ts:214`.

---

## 6. G-B18 — OTP clause associations (Q6)

Association model (CONFIRMED): clauses live in `clause-packs/*/pack.json` → `ClausePackSeeder.applyPack` `clause/ClausePackSeeder.java:85-118` seeds `Clause` rows and then `createTemplateAssociations` `:120-171` turns `templateAssociations[{templatePackId, templateKey, clauseSlugs[], requiredSlugs[]}]` into `TemplateClause` rows (templateId, clauseId, sortOrder, required) by looking the template up with `documentTemplateRepository.findByPackIdAndPackTemplateKey` `:129`. Generation: `ClauseResolver.resolveClauses` `clause/ClauseResolver.java:52-57` → with no explicit selection `loadTemplateDefaults` `:59-83` reads `TemplateClause` rows; `TiptapRenderer` `clauseBlock` `:142-179` renders a clause only if it is in the resolved set (UUID or `slug` fallback via `slugIndex`), otherwise emits `<!-- clause not found: slug -->`. The dialog's clause step is driven by `GET /api/templates/{id}/clauses` (same `TemplateClause` rows) — empty list ⇒ step skipped.

Root cause (CONFIRMED): `backend/src/main/resources/clause-packs/conveyancing-za-clauses/pack.json:288` — `"templateAssociations": []`. The pack seeds 10 clauses (`voetstoots, occupation-date, suspensive-bond, transfer-duty-liability, fica-compliance, sectional-title-levies, body-corporate-clearance, rates-clearance, cost-of-cancellation, jurisdiction-za`) and `legal-za/offer-to-purchase.json` embeds 7 `clauseBlock`s (`voetstoots, occupation-date, suspensive-bond, transfer-duty-liability, fica-compliance, rates-clearance, jurisdiction-za`), but no row ties them to the template. By contrast `legal-za-clauses/pack.json:304` associates the three engagement letters, and `standard-clauses/pack.json:345` the common letter. Seeding order is fine (template packs install before clause packs: `TenantProvisioningService.java:234-235`, `PackReconciliationRunner.java:129-130`), and cross-pack `templatePackId: "legal-za"` is supported.

Fix shape: add one `templateAssociations` entry for `{templatePackId:"legal-za", templateKey:"offer-to-purchase", clauseSlugs:[the 7 embedded slugs (+ optionally sectional-title-levies, body-corporate-clearance, cost-of-cancellation as optional)], requiredSlugs:[voetstoots, suspensive-bond, transfer-duty-liability, fica-compliance]}` (required set is a product call). Existing tenants: `ClausePackSeeder.isPackAlreadyApplied` `:74-77` matches on packId only → bump `version` has no effect; ship an explicit reconcile (override `reconcileExistingPack` to upsert associations idempotently — `createTemplateAssociations` already skips existing rows `:151-153`) or accept fresh-tenant-only. Tests: `ClausePackSeederIntegrationTest` (`creates_template_associations` `:73` covers standard-clauses only — add a conveyancing case), `ConveyancingTemplatePackTest`, `ClauseGenerationIntegrationTest`, then un-`fixme` `day-14-conveyancing.spec.ts:336`.

---

## 7. G-C12 (SoA category) and G-D14 (closure letter) (Q7)

### SoA "SHERIFF_FEES" (CONFIRMED)
`StatementOfAccountContextBuilder.build` puts `disbursements.entries = toMapList(agg.disbursementLines())` `:209-210`; `toMapList` `:267-269` is `objectMapper.convertValue(List<DisbursementStatementDto>)`, and `DisbursementStatementDto.category` is the raw `DisbursementCategory` enum (`disbursement/dto/DisbursementStatementDto.java:17`) → serialised as `"SHERIFF_FEES"`. The template column is `{"header":"Category","key":"category"}` (statement-of-account.json, disbursements loopTable). `DisbursementCategory` has no label method (`DisbursementCategory.java:11-29`); a humaniser already exists but is private: `InvoiceCreationService.humanizeCategoryLabel` `:1135-1146` ("Sheriff fees"); the frontend has a label map at `frontend/lib/legal/disbursement-defaults.ts:37` ("Sheriff Fees"). Fix: add `DisbursementCategory.label()` (move the humaniser there), emit `categoryLabel` in the DTO/map, point the template column at it (or overwrite `category` in `toMapList` post-processing). Tests: `StatementOfAccountContextBuilderTest`, `StatementRenderingIntegrationTest`.

### Closure letter "2820.00" and double count (CONFIRMED `verticals/legal/closure/MatterClosureContextBuilder.java`)
- Raw formatting: `:107-108` `matterMap.put("total_fees_billed", totalFeesBilled(projectId).toPlainString())` / `total_disbursements … toPlainString()`. The closure letter is a `PROJECT` template, so hints come from `PdfRenderingService.buildFormatHints` → `VariableMetadataRegistry`, which has **no `matter.*`/`closure.*` group** (grep: 0 hits) → `VariableFormatter.format(value, null)` just escapes the string. (The SoA avoided this by shipping its own hint map, `StatementService.renderHtml` `:362-374`.)
- Double count: `totalFeesBilled` `:116-122` sums `Invoice.getTotal()` (VAT-inclusive grand total, which includes disbursement lines billed on the fee note) over SENT/PAID invoices, while `totalDisbursementsBilled` `:124-137` separately sums APPROVED+BILLED `LegalDisbursement.amount + vatAmount`. "Total fees billed: 2820.00" = 1 800 fees + 270 VAT + 750 sheriff fee; "Total disbursements: 750.00" repeats the 750. Fix shape: fees = Σ invoice **line amounts excl. VAT where the line is not a disbursement line** (or Σ `invoice.subtotal` − Σ billed-disbursement amounts), print VAT separately, and pass `BigDecimal`s with a closure-specific hint map (mirror SoA: `Map.of("matter.total_fees_billed","currency", "matter.total_disbursements","currency")` merged into `PdfRenderingService.generatePdf(…, extraContext)` or a `formatHints` overload). Also `TrustBalanceZeroGate.java:48` should format via `VariableFormatter.format(balance, "currency", locale)`. Tests: `MatterClosureServiceIntegrationTest`, `MatterClosureEmailIntegrationTest`, `TrustBalanceZeroGateTest`, `day-75-85-closure.spec.ts`.
- Note: the closure letter also binds `org.principal_attorney` (nonexistent) and `org.logoUrl` (works) — the signatory field belongs in the same new letterhead embeddable (§10).

---

## 8. G-B16 — acceptance email subject (Q8)

CONFIRMED `acceptance/AcceptanceNotificationService.sendAcceptanceEmail` `:66-71` receives the `GeneratedDocument doc`; subject built at `:98-106`: `orgName + " -- Please review engagement letter for acceptance: " + doc.getFileName()` (and `"Reminder: … engagement letter …"` `:98-101`). Available names: `doc.getTemplateId()` → `DocumentTemplate.name` ("Offer to Purchase") and `.category` (`TemplateCategory`: ENGAGEMENT_LETTER, …, OTHER); `doc.getContextSnapshot().get("template_name")` (`GeneratedDocumentService.buildContextSnapshot` `:775-781`) with no extra query. Constraints: `AcceptanceNotificationServiceSubjectTest.java:58-110, 114-163` (GAP-L-51) pins "engagement letter" for the engagement-letter case, and the 90-day scenario 4.3b (`qa/testplan/demos/legal-za-90day-keycloak.md:261`) pins the exact subject. Fix shape that keeps both: `docLabel = category == ENGAGEMENT_LETTER ? "engagement letter" : lowercase(templateName)`, subject `"… Please review " + docLabel + " for acceptance: " + fileName`; add an OTP case to the subject test. Changing the engagement-letter wording itself would be a scenario amendment (Quality Gate 6). The email bodies (`resources/templates/email/acceptance-request.html`, `-reminder.html`) say "document" generically — no change needed.

---

## 9. Blast radius — tests (Q9)

Backend (`backend/src/test/java/io/b2mash/b2b/b2bstrawman/…`):
- Renderer/context: `template/TiptapRendererTest`, `VariableFormatterTest`, `PdfRenderingServiceTest`, `ConditionalBlockRenderTest`, `ClauseRenderingIntegrationTest`, `ProjectContextBuilderTest`, `InvoiceContextBuilderTest`, `CustomerContextBuilderTest`, `TemplateContextHelperResolveDropdownTest`, `VisualRegressionTest` (renders `common/engagement-letter`), `InvoiceCoverLetterRenderIntegrationTest`.
- Generation/persistence: `template/DocumentGenerationIntegrationTest` (pins rows on unsaved generations), `ClauseGenerationIntegrationTest`, `DocumentTemplateControllerTest`, `GeneratedDocumentControllerTest`, `TemplatePreviewControllerTest`, `TemplateValidationIntegrationTest`, `DocumentAuditNotificationTest`, `invoice/InvoiceSentDocumentPersistenceIntegrationTest` (pins fee-note row count on send), `acceptance/EngagementLetterWithoutProposalIntegrationTest`.
- Draft preview: `invoice/InvoicePreviewIntegrationTest`, `InvoicePreviewTaxIntegrationTest`, `InvoiceTerminologyLegalZaTest` (pins "Fee Note" wording via `invoiceTerm`).
- Packs/seeding: `template/TemplatePackSeederTest`, `packs/ConveyancingTemplatePackTest` (template count `EXPECTED_LEGAL_ZA_TEMPLATE_COUNT`), `packs/PackInstallBackfillTest`, `clause/ClausePackSeederIntegrationTest`, `clause/ClauseResolverTest`, `provisioning/TenantProvisioningServiceTest`.
- SoA/closure/acceptance: `verticals/legal/statement/StatementOfAccountContextBuilderTest`, `StatementRenderingIntegrationTest`, `StatementServicePortalDocumentTest`; `verticals/legal/closure/MatterClosureServiceIntegrationTest`, `MatterClosureEmailIntegrationTest`, `gates/TrustBalanceZeroGateTest`; `acceptance/AcceptanceNotificationServiceSubjectTest`, `AcceptanceCertificateServiceTest`.
- Settings (if a new embeddable lands): anything under `settings/` plus the OrgSettings snapshot pins from the 2026-06 embeddable refactor (#1433/#1434) — check `settings/*Test` and `SettingsResponse` consumers.

Frontend (`frontend/__tests__/…`): `components/documents/a4-preview-wrapper.test.tsx`, `legal/statement-of-account-dialog.test.tsx`, `templates/GenerateDocumentDialog.test.tsx`, `templates/generation-clause-step.test.tsx`, `templates/GenerateDocumentDropdown.test.tsx`, `legal/tariff-invoice.test.tsx` (pins "R 850.00"), `legal/tariff-schedule.test.tsx`. E2E: `e2e/tests/legal-depth-ii/day-14-conveyancing.spec.ts` (fixme :336), `day-30-45-soa-writeoff.spec.ts` (fixme :214), `day-75-85-closure.spec.ts`, `documents/document-generation.spec.ts` (no persisted-row pin on download), `legal-lifecycle/day-30-billing.spec.ts`, `demo-full-90day.spec.ts`.

---

## 10. Root causes (summary)

1. **Template content gaps, not renderer gaps** (G-B5, G-C7a client address): engagement-letter JSONs never got the `org.logoUrl` node that LZKC-007 added to the fee note; fee note omits `customer.addressLine1…`.
2. **Brand colour has no delivery mechanism**: `org.brandColor` is in context but no CSS/variable substitution exists; `--brand-color` is referenced and never defined; headings hard-code `#1a1a2e`.
3. **Firm letterhead / banking / signatory data model does not exist** (OrgSettings has branding + tax reg no only; `org.bankingDetails` and `org.principal_attorney` are dangling template keys).
4. **Blank labels are data-dependent** (due date null until approval defaults; matter reference and firm VAT number not populated in the walk) plus no `conditionalBlock` around optional lines.
5. **Second renderer**: the draft Preview is Thymeleaf with `#numbers.formatDecimal`, no locale/currency, no logo; two more ad-hoc money formatters (`TrustBalanceZeroGate`, tariff dialogs) bypass the canonical ones.
6. **Always-persist generation** is by design (attachment/audit trail); the UI just cannot tell saved from unsaved rows.
7. **A4 scaler measures padding-inclusive width** and feeds back whenever the host grid item is not a scroll container.
8. **Conveyancing clause pack ships empty `templateAssociations`**.
9. **SoA serialises the category enum raw**; **closure letter passes `toPlainString()` with no hint map and double-counts disbursements**.
10. **Acceptance subject is a literal** (pinned by GAP-L-51 test + scenario 4.3b).

## 11. Recommended fix shapes (one PR each, per Quality Gate 7)

| # | Gap(s) | Shape | Needs migration/UI? |
|---|---|---|---|
| F1 | G-B18 | `conveyancing-za-clauses/pack.json` association + seeder reconcile for existing tenants + test | no schema |
| F2 | G-C4 | `a4-preview-wrapper.tsx` use `contentRect.width` (+ `min-w-0 overflow-hidden`), new unit case, un-fixme SoA spec | no |
| F3 | G-C12 (SoA) | `DisbursementCategory.label()` + `categoryLabel` in SoA entries + template column | pack JSON only (fresh tenants) |
| F4 | G-D14 (letter) | closure builder: BigDecimals + closure hint map, fees excl. VAT and excl. disbursement lines, VAT line; `TrustBalanceZeroGate` via `VariableFormatter` | no |
| F5 | G-B16 | subject uses category/template name; extend subject test | no (keeps 4.3b) |
| F6 | G-C7d/e | draft preview → reuse `VariableFormatter` + `org.logoUrl` (or retire Thymeleaf preview for the Tiptap fee-note preview); tariff dialogs → `formatCurrency` from `@/lib/format`; move `"R 850.00"` pin | no |
| F7 | G-B6 | "Not saved" badge / filter on `documentId == null` in `GeneratedDocumentsList` + Documents tab; optional link-existing on Save | no |
| F8 | G-B5/G-C7a-c | **New `LetterheadSettings` embeddable** on `OrgSettings` (firm address lines/city/postal/country, phone, email, website, bank name/branch code/account no/account type/reference note, principal attorney / signatory) with `@AttributeOverride` columns → **tenant Flyway migration** (next-free V number) + `SettingsResponse`/`UpdateLetterheadSettingsRequest` + Settings › Documents › Branding (or Firm) form; `buildOrgContext` exposes `org.address`, `org.phone`, `org.email`, `org.bankingDetails`, `org.principal_attorney`; brand colour applied by `TiptapRenderer.render` injecting `:root{--brand-color:…}` **and** concrete rules (`h1,h2,h3{color}` + `.letterhead` border) since OpenHTMLToPDF lacks CSS vars (verify with `PdfRenderingServiceTest`/`VisualRegressionTest`); then template pack bump: engagement letters get `{{org.logoUrl}}` + letterhead block, fee note gets client address block, real banking block replacing the "on request" literal, `conditionalBlock`s around optional labels. Existing tenants need a reconcile for the template content. | **yes**: migration + settings UI + pack reconcile |

## 12. Related gaps
G-C5 (SoA save emails client, portal `/projects/` link) and G-D14's email half share `PortalDocumentNotificationHandler`, not this cluster. G-B9 (case number not shown) overlaps "Matter Reference" data. G-C12's other two parts (disbursement breadcrumb UUID; icon-only buttons in `GeneratedDocumentsList.tsx:309-355` have `title` but only the first has `aria-label` `:292`) are frontend polish outside the renderer. G-B7 (X-Frame-Options on the acceptance PDF) is unrelated.

## 13. Product questions
1. Which letterhead fields are in scope for demo-grade: address + phone + email + bank (name, branch code, account no, type) + signatory? Where in Settings (Documents › Branding vs Firm › General)?
2. Brand colour: apply to headings + rule lines only, or also table headers? Default when unset stays `#1a1a2e`?
3. Should download-only generations appear in the matter's Generated documents list (hide vs badge)? Should the fee-note send attachment row remain visible?
4. OTP required clauses: which of the 7 embedded slugs are mandatory; include the 3 unembedded clauses as optional?
5. Closure letter "Total fees billed": fees excl. VAT with a separate VAT line, or VAT-inclusive with "(incl. VAT)" label?
6. Acceptance subject for non-engagement templates: "Please review Offer to Purchase for acceptance" acceptable? (4.3b wording for engagement letters is preserved.)
7. Pack content changes: ship a one-off reconcile for existing tenants, or accept fresh-tenant-only for the demo stack?

## 14. Tests to run
Backend full `./backend/scripts/verify.sh` before merge (Quality Gate 1); inner loop: `-Dtest='*Tiptap*,*VariableFormatter*,*PdfRendering*,*DocumentGeneration*,*ClauseGeneration*,*ClausePackSeeder*,*ConveyancingTemplatePack*,*StatementOfAccount*,*StatementRendering*,*MatterClosure*,*TrustBalanceZeroGate*,*AcceptanceNotificationServiceSubject*,*InvoicePreview*,*InvoiceSentDocumentPersistence*,*TemplatePackSeeder*'`. Frontend: full `pnpm lint && pnpm build && pnpm test` + `format:check`; Playwright (`PLAYWRIGHT_BASE_URL=http://localhost:3001`, legal-depth-ii config, after `e2e-down/up` so pack JSON changes seed): `day-14-conveyancing`, `day-30-45-soa-writeoff`, `day-75-85-closure`, `documents/document-generation`, `legal-lifecycle/day-30-billing`. Observed verification per Gate 3: browser preview + downloaded PDF (`pdfimages`/text grep for `#1B3A4B` fill and `<img>`), Mailpit subject for an OTP acceptance request.
