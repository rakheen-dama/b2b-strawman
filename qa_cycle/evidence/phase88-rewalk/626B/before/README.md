# 626B: brand colour and letterhead context keys in generated documents — BEFORE

Captured 2026-10-27 between 19:47 and 19:56 UTC on the E2E mock-auth stack (:3001 / :8081). Per the brief, the backend was built from 99420af96, which is merged 626A. The `e2e-backend` image is `sha256:21fe1acb47ec…`, created 19:22 UTC; the commit cannot be read from the image labels. Logged in as Alice (owner), org `e2e-test-org`, tenant `tenant_7d218705360b`.

No product code was changed. All SQL was read-only SELECTs. The writes listed under "State left changed" were made through the UI or the API.

Auth for the API: mock-IDP `POST http://localhost:8090/token` with `{"userId":"user_e2e_alice","orgId":"e2e-test-org","orgSlug":"e2e-test-org","orgRole":"owner"}`, then `.access_token` used as a Bearer token. This is the recipe from `compose/seed/lib/common.sh` and 626A/after.

## Precondition: the letterhead is filled
- `GET /api/settings/letterhead` returns 200 with all 16 fields filled: 12 Rivonia Road / Illovo Point, 3rd Floor / Illovo / Sandton / 2196 / South Africa; +27 11 555 0142; info@ndlovu-attorneys.co.za; https://www.khumalo-attorneys.co.za; First National Bank / 250655 / 62812345678 / Cheque / reference note; Thandiwe Khumalo, Director. See `00a-…`.
- `GET /api/settings` shows `brandColor: "#0f766e"`, a presigned `logoUrl`, and the same `letterhead` object (`00b-…json`).
- The DB `org_settings` row has the same values plus `logo_s3_key = org/tenant_7d218705360b/branding/logo.png`. Flyway is at V7 (`00c-…`).

## Steps
1. **R1, brand colour.** Opened matter "Khumalo Property Holdings — Erf 1234 Sandton Transfer mvr15e76ef9a" (`02b5a264-…`). Clicked Generate Document › "Engagement Letter — Conveyancing", kept the 5 default clauses, clicked Next: Preview (`01-…png`), then Download PDF → `r1-engagement-letter-conveyancing.pdf`. Inspected the PDF with:
   - Ghostscript `txtwrite` (text dump) and `png16m` at 80 dpi (page images);
   - a pixel scan of an 80 dpi `ppmraw` render for brand teal `#0f766e`, heading navy `#1a1a2e` and logo orange;
   - zlib decompression of the content streams to list every fill and stroke colour and the fill colour of each text run.
2. **R3, variable picker.** Opened Settings › Documents › Templates › New (Primary Entity Type = Project) and clicked Insert variable. Took a screenshot of the Organization group (`02-…png`), then searched for "org.", "address", "bank", "signatory" and "principal" (`03-…png`, `r3-variable-picker-ui-searches.txt`). Called `GET /api/templates/variables?entityType=PROJECT|CUSTOMER|INVOICE` and saved the org group from each (`r3-api-variables-org-*.json`), plus the full PROJECT response.
3. **R2, context keys.** Tried to create the probe template in the UI form: name "626B probe — letterhead keys", org.name inserted through the picker (`04-…png`). Create Template was **rejected by the backend**: "Generated slug '626b-probe-letterhead-keys' … does not match pattern ^[a-z][a-z0-9-]*$" (`05-…png`; `DocumentTemplate.java:166` — a name that starts with a digit cannot be saved from the UI). The picker also cannot insert the letterhead keys (R3), so the probe was created through **`POST /api/templates`** instead:
   - same name, explicit `slug: probe-626b-letterhead-keys`, category ENGAGEMENT_LETTER, entity PROJECT;
   - the body uses real Tiptap `{"type":"variable","attrs":{"key":"org.…"}}` nodes, the same form as the `template-packs/legal-za/*.json` files;
   - request and response: `r2-probe-template-create-*.{json,txt}`.

   Then, in the UI on the Khumalo matter: Generate Document › "626B probe — letterhead keys" › preview (`06-…png`) › Download PDF → `r2-probe-letterhead-keys.pdf`. Also captured the preview HTML through `POST /api/templates/{id}/preview` (`r2-probe-preview-api.html`, `…-meta.json`).

   Extra probe: a read-only preview of the pack "Matter Closure Letter", which uses `org.principal_attorney` (`r2-matter-closure-letter-preview-api.html`).
4. **R4, statement of account banking details.** On the same matter: Fees · Statements of account › Generate Statement of Account. Default period 2026-10-01 to 2026-10-27; "share on portal" and "email" both left **unchecked** (internal draft). Clicked Preview & Save (`07-…png`) and saved the preview HTML (`r4-statement-of-account-preview.html`).
   - The dialog's Download PDF button gave no download event in Playwright: first "element is not stable", then a force-click with no event after 60 s, and no backend request was seen. This was not investigated.
   - Instead, the PDF was fetched through `GET /api/generated-documents/0f59ac2d-…/download`, which returns a 302 to the LocalStack presigned URL; the host followed it → `r4-statement-of-account-khumalo.pdf`.
5. **R5, logo.** Collected the openhtmltopdf logo-fetch lines from the backend log for all three renders, plus the value `org.logoUrl` resolved to (`r5-logo-backend-log.txt`).

## Observations (REPRODUCED = defect present)

| # | Check | Observed | Verdict |
|---|---|---|---|
| R1 | Heading colour in a Tiptap PDF, with the brand set to `#0f766e` | Engagement letter: all 6 heading runs are filled `0.10196 0.10196 0.18039` = **`#1a1a2e`**: H1 "E2E Test Organization", H2 "Engagement Letter — Conveyancing", H3 "Transaction Details", "Scope of Services", "Terms and Conditions", "Acceptance". The PDF has **0** brand-teal pixels and 3,889 + 292 navy pixels. The colour `0.0588 0.4627 0.4314` (brand) appears nowhere among the fill or stroke operators. The `<hr>` is the user-agent default inset border (`#1d1d1d` / `#333`), not brand. The backend log shows `com.openhtmltopdf.css-parse WARNING:: … var(-brand-color,#1a1a2e) is not a border width, style, or color at line 20. Skipping declaration.` on every render (R1, R2, R4). The probe PDF and the SoA PDF are the same (0 teal pixels; headings `#1a1a2e`). `document-default.css:51-55` hard-codes `h1, h2, h3 { color: #1a1a2e }`. The only brand hook is `.header { border-bottom: 3px solid var(--brand-color, #1a1a2e) }` (line 20); it is skipped, and nothing defines `--brand-color` anyway. | **REPRODUCED** |
| R2 | Letterhead `org.*` keys resolve in the template context | Probe `6ce14be3-8750-45a5-a44b-d33295e832d1`, previewed and generated against the Khumalo matter. **Only `org.name` resolves** ("E2E Test Organization"). Empty: `org.address`, `org.addressLines`, `org.phone`, `org.email`, `org.website`, `org.bankingDetails.{bankName,branchCode,accountNumber,accountType,referenceNote}`, `org.signatory.{name,designation}`, `org.principal_attorney`. Identical in the UI preview, the API preview HTML and the PDF text dump. As controls, `org.brandColor` resolves to `#0f766e` (so the brand colour is in the context, just never applied) and `org.logoUrl` resolves to an `<img>`. The preview `validationResult` says `{"allPresent":true,"fields":[]}`: the unknown or empty keys are not flagged. The pack "Matter Closure Letter" renders "Yours faithfully," then an **empty `<p></p>`** where `org.principal_attorney` sits, then the firm name. 3 pack templates use that key (matter-closure-letter, deed-of-transfer, bond-cancellation-instruction). | **REPRODUCED** |
| R3 | The variable picker offers the letterhead `org.*` keys | The UI Organization group has exactly `org.name`, `org.defaultCurrency`, `org.brandColor`, `org.documentFooterText`, `org.logoUrl`. Searching "bank" and "principal" finds nothing. "address" finds only `customer.*` and custom fields. "signatory" finds only `project.customFields.opposing_attorney`, a label match. The API returns the same 5 org keys for PROJECT, CUSTOMER and INVOICE. Source: `VariableMetadataRegistry.buildOrgGroup()` at `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java:320-333` (main 3065a0fb1). | **REPRODUCED** |
| R4 | SoA banking placeholder | Code: `StatementOfAccountContextBuilder.java:226-228` (main 3065a0fb1) does `orgMap.putIfAbsent("bankingDetails", "");` with the comment "add bankingDetails placeholder for the SoA template". Pack `statement-of-account.json` ends with H3 "Payment Instructions", then `{variable org.bankingDetails}`, then `{variable org.documentFooterText}`. Observed on a freshly generated SoA (`0f59ac2d-d0cb-46c7-991b-397771da1474`): the HTML is `<h3>Payment Instructions</h3><p></p><p></p>` and the PDF text ends at "Payment Instructions". **No banking text renders**, although FNB / 250655 / 62812345678 / Cheque is on file. | **REPRODUCED** (UI-observed + code) |
| R5 | Logo in generated PDFs | R1 engagement letter: no logo, because the template has no `org.logoUrl` variable, so no fetch was even attempted (0 "IO problem" lines). Probe (R2) and SoA (R4) do contain `org.logoUrl`. The context resolves it to the **host-facing presigned LocalStack URL** `http://localhost:4567/docteams-dev/org/tenant_7d218705360b/branding/logo.png?X-Amz-…` (Credential/Signature redacted, expiry 3600 s), emitted as `<img class="letterhead-logo" …>`. openhtmltopdf inside the container fails 3× per render with `IO problem for http://localhost:4567/… => java.net.ConnectException:: Connection refused (connect failed)`. All three PDFs have `image_xobjects=0` and 0 logo-orange pixels. The same URL **does** load in the host browser's HTML preview (`naturalWidth=120`, visible in `06-…png`). | **REPRODUCED** (logo missing from every PDF; the root cause is the URL being unreachable from the container) |

## Other observations (outside 626B's fix scope; listed for triage)
- **A template name starting with a digit cannot be created from the UI.** The slug is derived from the name and must match `^[a-z]…`, and the form surfaces the raw backend message (`05-…png`). The probe therefore lives under the explicit slug `probe-626b-letterhead-keys`.
- **The preview `validationResult` ignores unknown `org.*` keys** (`allPresent: true`), so a template author gets no warning that `org.address` and the other letterhead keys resolve empty.
- **`org.taxRegistrationNumber`** is in the runtime context and used by 2 pack templates, but is not offered by the picker either. It surfaced while doing R3 and is not in the 626B key list.
- **The SoA dialog's Download PDF** gave no download event under Playwright (see step 4). This was not investigated: it may be a harness problem or a real one. The same PDF is reachable through the generated-documents download API.
- **The SoA prints "VAT Reg:" with an empty value** and "File reference:" empty. These are pre-existing and were seen in 626A's fee note too.
- **The HTML preview iframe** applies the same `h1–h3 { color:#1a1a2e }` rule, so the headings look black there as well (`06-…png`).

## State left changed on the stack
- **New template** `6ce14be3-8750-45a5-a44b-d33295e832d1`: "626B probe — letterhead keys", slug `probe-626b-letterhead-keys`, ORG_CUSTOM, PROJECT, ENGAGEMENT_LETTER. **Left in place for 626B observers.**
- **New `generated_documents` rows** on the Khumalo matter:
  - `62b5c699-0e9e-43de-906d-50063720381d`: engagement letter, created by Download PDF, no `document_id`;
  - `6448a4e4-11b3-4435-aa44-3af8a2d5c19b`: probe, created by Download PDF, no `document_id`;
  - `0f59ac2d-d0cb-46c7-991b-397771da1474`: statement of account, an internal draft saved to the matter's documents (`document_id eed30b82-622a-491f-aaa8-a3d4d519ba05`), not shared and not emailed.
- Letterhead, brand colour and logo were not changed.

## Files
- `00a-alice-get-letterhead.txt`, `00b-alice-get-settings.json`, `00c-db-letterhead-columns.txt`: precondition.
- `01-engagement-letter-preview-dialog.png`, `r1-engagement-letter-conveyancing.pdf`, `.txt`, `-p1..p3.png`, `r1-pixel-scan.txt`, `r1-backend-log.txt`: R1.
- `04-probe-create-form.png`, `05-probe-create-slug-error.png`, `06-probe-preview-dialog.png`, `r2-probe-template-create-request.json`, `r2-probe-template-create-response.txt`, `r2-probe-preview-api.html`, `r2-probe-preview-api-meta.json`, `r2-probe-letterhead-keys.pdf`, `.txt`, `-p1.png`, `r2-pixel-scan.txt`, `r2-backend-log.txt`, `r2-matter-closure-letter-preview-api.html`: R2.
- `02-variable-picker-org-group-project.png`, `03-variable-picker-search-org.png`, `r3-variable-picker-ui-searches.txt`, `r3-api-variables-org-{PROJECT,CUSTOMER,INVOICE}.json`, `r3-api-variables-PROJECT-full.json`: R3.
- `07-soa-preview-dialog.png`, `r4-statement-of-account-preview.html`, `r4-statement-of-account-khumalo.pdf`, `.txt`, `-p1..p2.png`, `r4-pixel-scan.txt`, `r4-backend-log.txt`: R4.
- `r5-logo-backend-log.txt`: R5.

Presigned `X-Amz-Credential` and `X-Amz-Signature` values are redacted in every saved file; a grep confirms none remain.
