# Slice 626G — reproduction (before/)

Date: 2026-10-28 (~02:14–02:18 UTC). Stack: e2e mock-auth (frontend :3001, backend :8081, Mailpit :8026, LocalStack :4567, Postgres e2e-postgres).
Tenant schema: `tenant_7d218705360b` (confirmed through `public.org_schema_mapping`). Template pack `legal-za` v9 is installed.
Actor: Alice (owner), using the Playwright MCP browser. REST was used only for Mailpit, for read-only SELECTs, and for one read-only S3 GET of the generated PDF.

## Verdicts

| Gap | Verdict |
|---|---|
| G-B16 acceptance email subject says "engagement letter" for an OTP | **REPRODUCED** |
| G-B18 OTP conveyancing clauses don't resolve (`<!-- clause not found -->`, empty Conditions of Sale) | **REPRODUCED** |

## Steps (UI)

1. Opened `/org/e2e-test-org/matters/02b5a264-61b9-4932-9b74-f844cd85afaa` (existing matter "Khumalo Property Holdings — Erf 1234 Sandton Transfer mvr15e76ef9a", type CONVEYANCING, client Khumalo Property Holdings mvr15e76ef9a).
2. Generate Document → "Offer to Purchase". The dialog opened **straight on the preview**. It had no "Select Clauses" step and no "Step 1 of 2" (screenshot 01).
3. Preview: the **Conditions of Sale** heading is followed directly by the `<hr/>` and then the signature block. Its body is empty (screenshots 01, 02).
4. Save to Documents → "Document saved successfully" → Send for Acceptance → Recipient = Khumalo Property Holdings mvr15e76ef9a (khumalo.property.mvr15e76ef9a@example.com), default expiry → Send (screenshots 03, 04).
5. Control: on the same matter, Generate Document → "Engagement Letter — Conveyancing". This dialog shows **"Step 1 of 2 — Select Clauses"** with 5 clauses (Fees — Tariff Basis, Trust Account Deposits, FICA Consent, Data Protection (POPIA), Termination of Mandate) (screenshot 06). Then Next: Preview → Save → Send for Acceptance to the same contact.

## G-B16 — observed vs expected

- Expected (after fix): `Please review offer to purchase for acceptance: …`
- **Observed subject on the OTP request** (Mailpit ID `aqH5aaZBX8PuS543BKuQDJ`, 2026-10-28T02:15:58Z, to khumalo.property.mvr15e76ef9a@example.com, from noreply@kazi.app):
  `E2E Test Organization -- Please review engagement letter for acceptance: offer-to-purchase-khumalo-property-holdings-erf-1234-sandton-transfer-mvr15e76ef9a-2026-10-28.pdf`
- The body is generic and does not mention "engagement letter": "Document for Your Acceptance / Hi Khumalo Property Holdings mvr15e76ef9a, / E2E Test Organization has sent you a document that requires your acceptance. / offer-to-purchase-…-2026-10-28.pdf / Please review the document and confirm your acceptance by clicking the button below. / Review & Accept (http://localhost:3002/accept/anCo8mJm…) / This link will expire on 27 November 2026." Full text is in `mailpit-otp-acceptance.txt`, HTML in `mailpit-otp-acceptance.html`, raw JSON in `mailpit-otp-acceptance-aqH5aaZBX8PuS543BKuQDJ.json`.
- **Control (engagement letter)**, Mailpit ID `mHxYYTdv6yHygbvBQrTUQh` (02:17:22Z): `E2E Test Organization -- Please review engagement letter for acceptance: engagement-letter-conveyancing-khumalo-property-holdings-erf-1234-sandton-transfer-mvr15e76ef9a-2026-10-28.pdf`. The subject is identical apart from the file name, so the "engagement letter" wording is literal and ignores the document type.
- Source pointer (not a diagnosis, for the builder): `backend/.../acceptance/AcceptanceNotificationService.java:111` has the literal `" -- Please review engagement letter for acceptance: " + doc.getFileName()`. The **reminder** subject at `:107` has the same literal: `"Reminder: " + orgName + " -- Please review engagement letter for acceptance"`.

## G-B18 — observed vs expected

- Expected: the Conditions of Sale section renders the conveyancing clauses (voetstoots, occupation date, suspensive bond, transfer duty, FICA, rates clearance, jurisdiction).
- **Observed preview HTML** (iframe srcdoc, `otp-preview.html`):
  ```
  <hr/><h3>Conditions of Sale</h3><!-- clause not found: voetstoots --><!-- clause not found: occupation-date --><!-- clause not found: suspensive-bond --><!-- clause not found: transfer-duty-liability --><!-- clause not found: fica-compliance --><!-- clause not found: rates-clearance --><!-- clause not found: jurisdiction-za --><hr/><p>Signed at ___…
  ```
  The preview is a server action rendered into the iframe srcdoc, so no browser-visible XHR carried it.
- **Observed generated PDF** (`otp-generated.pdf`, S3 key `org/tenant_7d218705360b/generated/offer-to-purchase-khumalo-property-holdings-erf-1234-sandton-transfer-mvr15e76ef9a-2026-10-28.pdf`, text in `otp-generated.pdf.txt`, render in `05-otp-generated-pdf-page1.png`): "Conditions of Sale" is followed immediately by "Signed at _________________ on this …". **No clause text at all.**

## DB diagnostics (read-only)

Full output is in `db-1-clauses-packs.txt`, `db-2-otp-clauseblocks.txt` and `db-3-created-ids.txt`.

- OTP template row: `880f4fd6-649c-416a-89ca-b9b9875cd4ed`, name "Offer to Purchase", slug `offer-to-purchase`, pack_id `legal-za`, pack_template_key `offer-to-purchase`, category `OTHER`, source PLATFORM, primary_entity_type PROJECT, `acceptance_eligible = t`.
- OTP content `clauseBlock` nodes (all `{"slug": …}` only, **no clauseId attr**), under heading "Conditions of Sale": `voetstoots`, `occupation-date`, `suspensive-bond`, `transfer-duty-liability`, `fica-compliance`, `rates-clearance`, `jurisdiction-za`.
- **The clauses exist in the tenant**: all 7 slugs are present in `tenant_7d218705360b.clauses` (source SYSTEM, pack_id `conveyancing-za-clauses`, active). There are 10 conveyancing clauses in total (also body-corporate-clearance, cost-of-cancellation, sectional-title-levies).
- **The OTP has 0 `template_clauses` rows.** Only these templates have associations: engagement-letter-general 5, standard-engagement-letter 12, engagement-letter-litigation 6, engagement-letter-conveyancing 5.
- pack_install (CLAUSE): `conveyancing-za-clauses` v1, `legal-za-clauses` v1, `standard-clauses` v1 (each item_count 0). DOCUMENT_TEMPLATE: `legal-za` v9, `common` v3, `compliance-za` v1.
- Supporting code facts, offered as a root-cause hypothesis for the builder and not verified beyond reading:
  - `ClauseResolver.loadTemplateDefaults` loads clauses only from `template_clauses`.
  - `PdfRenderingService.renderTemplateToHtml` builds the clause map from those resolved clauses only.
  - `TiptapRenderer`'s slug fallback (`slugIndex`) is built from that same map. A slug-only clauseBlock whose clause exists in the tenant but isn't associated with the template therefore emits `<!-- clause not found: slug -->` (`TiptapRenderer.java:212`).
  - `backend/src/main/resources/clause-packs/conveyancing-za-clauses/pack.json` ends with `"templateAssociations": []`, while legal-za-clauses associates its clauses with the three engagement letters.
  - That also explains why the OTP dialog skips the "Select Clauses" step: the template has no associated clauses.

## Data created (reusable by later observers)

| What | id |
|---|---|
| OTP generated_document | `8af7c134-c104-436e-980c-875a81c16bdf` (document_id `f86a96e2-df59-46a4-85d1-792179f52574`) |
| OTP acceptance_request | `ece2aefd-ff63-4ff4-a16a-754fabd45844` (SENT, token `anCo8mJmHWNG6f5OqccvUC5Ra718mMMXwbadqRtiTcg`, portal_contact `8193e134-f650-41dd-9998-b6a20748f394`) |
| Control EL-Conveyancing generated_document | `99eaa3f5-3ad7-4352-9317-8d5fe02db25c` (document_id `653a2ac0-c3cf-41a2-9a8f-0002a041d8ae`) |
| Control EL acceptance_request | `7944c9af-ff8b-4eb5-9305-96c17461a30d` (SENT) |
| Mailpit | OTP `aqH5aaZBX8PuS543BKuQDJ`, control `mHxYYTdv6yHygbvBQrTUQh` |

Matter used: `02b5a264-61b9-4932-9b74-f844cd85afaa`. Customer: `55b2176f-c2d0-442a-902e-c865930b2f13`.

## Other oddities noticed (not chased)

- The email subject (and body) identify the document by its **slugged PDF file name** (`offer-to-purchase-…-2026-10-28.pdf`) rather than a human title.
- The OTP PDF has a **blank trailing page 2** (`05-otp-generated-pdf-page2.png`).
- OTP "Seller:", "Purchase Price (ZAR):" and "Occupation Date:" render as empty labels. That is probably unset matter fields, but the template shows bare labels with no placeholder.
- The OTP template category is `OTHER` even though it is `acceptance_eligible`. The subject fix can't key off category alone.
- The Send-for-Acceptance dialog opens with the "Help: Portal contacts" button focused (`[active]`) rather than the Recipient field.
