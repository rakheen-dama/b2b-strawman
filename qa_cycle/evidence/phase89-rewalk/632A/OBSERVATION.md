# 632A post-merge observation (Keycloak dev stack)

- **Observed:** 2026-11-04, 22:55–23:12 SAST
- **Head:** `e099f966b` (632A, PR #1727) on `main`
- **Stack:** `./mvnw -q clean compile -DskipTests` on main, then the old backend was killed. It was an external PID 1420 with etime 6d07h, i.e. stale. The backend was started with `svc.sh start backend`. The new Java listener on :8080 is **PID 12410**, started 22:55:52 SAST (svc wrapper PID 12253). The frontend was also restarted because the old dev server had been up 6d05h. The new :3000 listener is PID 13275 (svc PID 13231). Gateway (334) and portal (9468) were not touched. `svc.sh status` shows all four healthy.
- **Login:** `thandi@mathebula-test.local` (owner, Mathebula & Partners, `tenant_5039f2d497cf`) via Keycloak on :3000, driven by the Playwright MCP.
- **Viewports:** 1440x900 and 1024x800.
- **Playwright hook:** `compose/scripts/check-playwright-port.sh` blocks :3000 unless `/tmp/.e2e-keycloak-mode` exists. I created that marker, which is the hook's documented Keycloak-mode switch. I did not edit the hook.

## Boot log (`boot-log.txt`)
```
Reconciled template pack legal-za to v11 for tenant tenant_56399d06a773 (0 new templates, 8 refreshed, 0 re-pinned after the reconcile block)
Reconciled template pack legal-za to v11 for tenant tenant_5039f2d497cf (0 new templates, 8 refreshed, 0 re-pinned after the reconcile block)
```
These are the only two tenant schemas in `public.org_schema_mapping`. `pack_install.pack_version = 11` on both. The 8 refreshed rows on each schema (`updated_at` 20:56 UTC) are the 4 letterhead templates plus the 4 C1 templates. See `db.txt`.

## G88-F16 — letterhead on the four client-facing templates: **PASS**
Org values come from Settings > Firm > General and from `org_settings`:
- logo `org/tenant_5039f2d497cf/branding/logo.png` (renders as a solid brand-colour square)
- 12 Rissik Street, 3rd Floor, Johannesburg 2001, South Africa
- +27 11 555 0100 / accounts@mathebula-test.local / https://mathebula-test.local
- **Tax Registration Number blank**, Tax Label "VAT" (`f16-09-settings-tax-number-blank-1440.png`)

| Template | Where | Result | Evidence |
|---|---|---|---|
| statement-of-account | Lerato Mthembu RAF matter > Statements > Generate SoA > Preview & Save. Really generated: `generated_documents` d0f83ef4, 22:59 SAST. | Letterhead (logo, name, address, Tel, Email, Web). No "VAT Reg:" or "VAT Registration No". No "File reference:" because the matter has none; in the DB that label sits inside `conditionalBlock(matter.file_reference isNotEmpty)`. | `f16-01-soa-preview-dialog-1440.png`, `f16-soa-generated.pdf` / `.txt`, `f16-02-soa-generated-pdf-p1.png`, `f16-01-soa-preview-generic-dialog-1024.png` |
| matter-closure-letter | Generate Document > Matter Closure Letter, on active Lerato and on closed Sipho. Preview plus server PDF download; not saved. | Letterhead present. No VAT/File-reference labels. | `f16-03-closure-letter-preview-1440.png`, `f16-03-closure-letter-preview-1024.png`, `f16-05-closure-letter-closed-matter-preview-1440.png`, `f16-closure-letter*.pdf` / `.txt`, `f16-04-closure-letter-pdf-p1.png`, `f16-closure-letter-closed-matter-pdf-p1.png` |
| client-trust-statement | Client Sipho Dlamini > Generate Document. Preview plus PDF download. | Letterhead present. No VAT/File-reference labels. | `f16-06-client-trust-statement-preview-1440.png` / `-1024.png`, `f16-client-trust-statement.pdf` / `.txt`, `f16-client-trust-statement-pdf-p1.png` |
| trust-receipt | Client Sipho Dlamini > Generate Document. Preview plus PDF download. | Letterhead present. No VAT/File-reference labels. | `f16-08-trust-receipt-preview-1440.png` / `-1024.png`, `f16-trust-receipt.pdf` / `.txt`, `f16-trust-receipt-pdf-p1.png` |

**Conditional VAT line, positive case:** I set Tax Registration Number = `4123456789` through the Settings UI and re-previewed all four templates. Each one showed `VAT Registration No: 4123456789` under the letterhead contact lines (`f16-10-taxset-{trust-receipt,client-trust-statement,closure-letter,soa}-1440.png`). I then **reverted the field to blank through the UI** and confirmed it in `db.txt`. A text grep for `VAT Reg|File reference|Registration` returns 0 hits in all five PDFs downloaded while the tax number was blank.

## C1 — required-party warnings step: **PASS**
- I used the Khumalo Property Holdings conveyancing matter (aa61195f). `seller_name` and `purchaser_name` are blank in its custom fields, so no field was edited.
- Generate Document > Offer to Purchase > Next: Preview showed step 2 with a **"Required field warnings"** panel listing `project.customFields.seller_name` and `project.customFields.purchaser_name`. Raw keys are shown instead of labels, which is the known accepted ruling. Buttons were "Download anyway" and "Save anyway".
- "Save anyway" showed "Document saved successfully" and "Send for Acceptance". The matter Documents tab lists `offer-to-purchase-…-2026-11-04.pdf`. The `generated_documents` row 85dbcea1 has `warnings` = both fields, "Field not populated: …". The 2026-10-29 OTP row from before this slice has null warnings.
- Evidence: `c1-01-otp-dialog-1440.png`, `c1-02-otp-preview-step-1440.png`, `c1-03-otp-required-field-warnings-1440.png` / `-1024.png`, `c1-04-otp-saved-anyway-1440.png`, `c1-05-matter-documents-list-1440.png`, `db.txt`.
- Deed of Transfer, Bond Cancellation and Letter of Demand were not walked in the browser. Their `required_context_fields` are populated on both schemas (`db.txt`).

## C6 — SoA default period: **PASS**
- **Lerato, first statement:** with no prior statement the defaults were 2026-11-01 → 2026-11-04 (`c6-01-first-statement-default-1440.png`). I generated it.
- **Lerato, reopened after that same-day statement** (periodEnd 2026-11-04): the defaults were **2026-11-04 → 2026-11-04**. periodEnd+1 = 11-05 is clamped to today, so the range is not inverted (`c6-02-reopen-after-today-statement-1440.png` / `-1024.png`, `c6-04-lerato-reopen-1024.png`).
- **Sipho (closed matter, latest statement periodEnd 2026-10-29):** the defaults were **2026-10-30 → 2026-11-04** (`c6-03-prior-statement-oct29-default-1440.png` / `-1024.png`).
- The end date equals today's SA date (2026-11-04) in every case.

## Deviations / side effects
- New data created through the UI: 1 SoA on Lerato (internal draft, not shared or emailed) and 1 OTP on Khumalo.
- The org tax number was set and then reverted. Net state is blank, as before.
- Closure letter, trust statement and trust receipt were preview plus server PDF download only, not saved to documents.

## Observations outside 632A scope (not fixed)
1. **SoA in the generic "Generate Document" menu renders hollow.** Reference, Period ("—"), Generated, Matter and Opened are all blank (`f16-01-soa-preview-generic-dialog-1024.png`). The statement context is only built by the dedicated "Generate Statement of Account" dialog. Offering SoA in the generic menu yields a document with empty fields.
2. **Matter Closure Letter from the generic menu has blank Date, Reason for closure and Summary of Account fields**, even on a closed matter (Sipho). The closure context only exists inside the closure flow.
3. **Trust Receipt and Client Trust Statement bodies still contain literal placeholders** such as `[Amount to be inserted]`, `[Reference to be inserted]`, `[Purpose to be inserted]` and `[Transaction details to be inserted]`. Template content gap; pre-existing.
4. **The C1 warnings panel sits below the A4 preview inside the dialog's 60vh scroll area.** At 1440x900 the user has to scroll to see it, though the red "Save anyway" button is visible.
5. The OTP body still reads "meaning that accepts the property…" with the party missing. This is the known G88-F12 (Voetstoots clause party).
6. The generic matter menu also lists "Standard Engagement Letter" and "Matter Summary Report" alongside the legal-za templates.
