# 626C reproduction: Letterhead settings form under Firm › General (G-B5 / G-C7, unit U8)

**Result: REPRODUCED.** There is no letterhead input anywhere in the app. The backend stores a fully populated letterhead (written by the 626A observer through the API), but no UI shows or edits it.

- Date: 2026-10-27, e2e mock-auth stack (frontend :3001 built from main before 626C, backend :8081)
- Tenant schema: `tenant_7d218705360b` (the only row in `public.org_schema_mapping`)
- Evidence dir: `qa_cycle/evidence/phase88-rewalk/626C/before/`

## Navigation and URL
Sidebar › FIRM (expand) › Settings → `http://localhost:3001/org/e2e-test-org/settings/firm`. Page title is "Firm — General · Settings | Kazi". The General tab also has its own URL, `?tab=general`.
Firm tabs: General, Roles, Audit trail, Data protection, Data requests, Compliance, Features, Subscription.

## Firm › General as Alice (owner): what's on the page
Cards from top to bottom. All are full-width and single-column; at 1440 each is 856px wide, starting at x=544.
1. **Currency**: Default Currency (combobox, ZAR)
2. **Tax Configuration**: Tax Registration Number and Tax Label (2-column grid), Tax-inclusive pricing (switch)
3. **Branding**: Logo (a test logo is shown, with Remove and Upload Logo), Brand Color (swatch + hex text, `#0f766e`), Document Footer Text (textarea, 0/500, empty)
4. A shared **Save Settings** button for cards 1–3 (`GeneralSettingsForm`; it does PUT /api/settings and PATCH /api/settings/tax)
5. **Client Portal**: Portal digest cadence (Weekly). This is a separate section that saves on its own.
6. **Org Documents** (0), with an Upload Document button

Labels I found programmatically: Default Currency, Tax Registration Number, Tax Label, Tax-inclusive pricing, Logo, Brand Color, Document Footer Text, Portal digest cadence.
I searched the tabpanel text for address, phone, website, bank, branch code, account, signator, letterhead, postal and city: **none matched**. The one "email" hit is the Client Portal copy ("portal digest emails"). There are no address, phone, email, website, banking or signatory inputs.
Screenshots: `01-alice-firm-general-1440.png`, `02-alice-firm-general-1024.png`. Accessibility snapshot: `snapshots/alice-firm-general-snapshot.yml`.

## Other settings pages I checked (as Alice)
Firm: roles, data-protection, compliance, features, subscription. Matters. Documents: templates, clauses, acceptance, email. Fees & Trust: tariffs, tax, time-recording, fee-runs, collections. Connections.
- None of them has a letterhead, address, phone, website, bank or signatory input.
- Data protection has "Name" and "Email" fields, but they are for the Information Officer, not the letterhead.
- **Documents › Templates** contains a second **Branding** card with the same Logo, Brand Color and Document Footer Text fields. The page text matched "letterhead" only because of a template named "626B probe — letterhead keys", which is data, not a form (`03-alice-documents-templates-1440.png`).
- Tax config also appears twice: in Firm › General and in Fees & Trust › Tax, which adds a "Registration Label" field.
- Code grep `grep -rniE "letterhead|bankingDetails|signatory" frontend/app frontend/components frontend/lib` returned **0 matches**. The frontend `OrgSettings` type (`frontend/lib/types/settings.ts`) has no `letterhead` field.

## Role picture
| User | Role | Firm › General in UI | GET /api/settings | GET /api/settings/letterhead | TEAM_OVERSIGHT |
|---|---|---|---|---|---|
| Alice | owner | full form (as above) | 200 | 200 | yes |
| Bob | admin | identical full form, nothing disabled (`04-bob-firm-general-1440.png`) | 200 | 200 | yes |
| Carol | member | only the General and Subscription tabs; General shows the H1 and the Org Documents card, with no settings cards (`05-carol-firm-general-1440.png`) | 200 | **403** "Insufficient permissions for this operation" | no |

- Carol's FIRM nav group shows only Team and Settings. Bob sees Team, Reports, Audit trail and Settings.
- **Frontend gate today:** `frontend/app/(app)/org/[slug]/settings/firm/_tabs/general.tsx`. It sets `isAdmin = caps.isAdmin || caps.isOwner`. Non-admins get an early return (H1 + Org Documents only), and `/api/settings` is fetched only on the admin branch.
- **Backend gate:** `@RequiresCapability("TEAM_OVERSIGHT")` on GET and PATCH `/api/settings/letterhead` (OrgSettingsController ~L191–198).
- With the default roles, admin/owner and TEAM_OVERSIGHT line up. A custom role could hold TEAM_OVERSIGHT without being admin, or the reverse, so the two gates can diverge.
- Capability JSON: `api-{alice,bob,carol}-capabilities.json`.

## Surprising finding (outside 626C scope, flagged for the orchestrator)
**Ungated `GET /api/settings` (no `@RequiresCapability`) returns the full `letterhead` object to Carol (member), including bank account number, branch code and signatory.** The dedicated `GET /api/settings/letterhead` returns 403 to the same user, so the gating is inconsistent and the banking details leak through the general endpoint (`api-carol-settings.json`). The UI does not render this for Carol, but the API does return it. Whether that is acceptable (letterhead details are printed on every fee note anyway) is a product/security call, not this slice's.

## Current letterhead values (API = DB, identical)
- **Address:** 12 Rivonia Road / Illovo Point, 3rd Floor / Illovo / Sandton / 2196 / South Africa
- **Contact:**
  - Phone: +27 11 555 0142
  - Email: info@ndlovu-attorneys.co.za
  - Website: https://www.khumalo-attorneys.co.za (the email and website domains differ; that is how the earlier observer seeded them)
- **Bank:**
  - Bank: First National Bank
  - Branch code: 250655
  - Account number: 62812345678
  - Account type: Cheque
  - Payment reference note: "Please use your fee note number as the payment reference."
- **Signatory:** Thandiwe Khumalo, Director
- **Branding:** brand_color `#0f766e`, logo_s3_key `org/tenant_7d218705360b/branding/logo.png`, document_footer_text empty, updated_at 2026-10-27 19:23:55Z

Files: `api-alice-settings-letterhead.json`, `api-alice-settings.json` (top-level key `letterhead` sits next to `brandColor`, `logoUrl` and the rest), `db-org_settings-letterhead.txt`.
DTO field names (LetterheadSettingsResponse / UpdateLetterheadSettingsRequest): addressLine1, addressLine2, addressLine3, city, postalCode, country, phone, email, website, bankName, bankBranchCode, bankAccountNumber, bankAccountType, paymentReferenceNote, signatoryName, signatoryDesignation.
DB columns: `letterhead_*` in snake_case. Max lengths:
- 255: address lines 1–3, email, website, signatory name, signatory designation
- 100: city, country, bank name
- 50: phone, account number
- 30: account type
- 20: postal code, branch code
- text: payment reference note

## Layout notes for the builder (1024×768)
- The app sidebar (240px) and the settings sub-nav (240px) are both always visible, so the tabpanel is only **440px wide** (x=544). No horizontal page overflow was found (`scrollWidth` check).
- The tab strip (715px) runs past the visible width. Compliance, Features and Subscription are clipped off the right edge.
- The 2-column Tax grid already squeezes to 187px per input with wrapped helper text.
- Recommendation: a new full-width card placed after Branding, using single-column fields, or 2-column only for short pairs (city/postal, branch code/account type), and switching to stacked layout below `lg`.
- Each card ends at the shared "Save Settings" button. A letterhead card should either have its own save button (like Client Portal) or be added explicitly to the shared form.

## Token recipe (works)
```bash
curl -s -X POST http://localhost:8090/token -H 'Content-Type: application/json' \
  -d '{"userId":"user_e2e_alice","orgId":"org_e2e_test","orgSlug":"e2e-test-org","orgRole":"org:owner"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])'
```
- Bob: `user_e2e_bob` / `org:admin`. Carol: `user_e2e_carol` / `org:member`. Source: `frontend/e2e/fixtures/auth.ts`.
- Use it as `Authorization: Bearer <jwt>` against :8081. Fresh JWTs are in `/tmp/626C/{alice,bob,carol}.jwt`.
- Browser role switch: clear cookies, go to /mock-login, pick the user in the "Select User" combobox, then Sign In.

## Console and state
- Browser console: 0 errors and 0 warnings on Firm › General.
- No application or DB state was changed: GET calls and psql SELECT only.
- Playwright snapshot files I created in `.playwright-mcp/` were copied out where needed and then deleted. The browser is closed.
