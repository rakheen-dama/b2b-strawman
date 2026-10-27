# 626C observation: Letterhead card under Firm › General (PR #1687, head 800ce8c39)

- Date: 2026-10-27, e2e mock-auth stack. Frontend :3001 rebuilt from `/Users/rakheendama/Projects/2026/worktree-epic-626C` at **800ce8c39** → image `docteams-e2e-frontend` sha256:707ecbf73d9b…c764792. The built bundle contains the string "Save letterhead".
- Rebuild command: `docker compose -f compose/docker-compose.e2e.yml up -d --build --no-deps frontend`. This is what `e2e-rebuild.sh frontend` runs, plus `--no-deps`, so the backend, LocalStack and the logo were not touched. Backend :8081 is unchanged (it equals main).
- Tenant: `tenant_7d218705360b`. The pre-state (`00-*`) matched the before README exactly.

## Results
| # | Check | Result | Evidence |
|---|---|---|---|
| a | Hydration: the card sits after Branding + "Save Settings" and before Client Portal. It has the Address/Contact/Banking/Signatory fieldsets, all 16 fields are pre-filled with the stored values, and "Save letterhead" is disabled while untouched | PASS | `01-alice-general-1440-full.png`, `01b-letterhead-card-1440.png` |
| b | Layout. At 1024 the card is 440px wide and all 17 controls are in one column at 390px, with no clipped inputs or text and `scrollWidth`=1024. At 1440 City+Postal, Branch code+Account type and Signatory name+Designation are side by side at 395px each, and the long fields span the full width. Readable at both sizes | PASS (1 LOW cosmetic, D1) | `02-alice-general-1024-full.png`, `02b-letterhead-card-1024.png`, `03-*.png`, `03b-*.png` |
| c | Save round-trip. I changed 10 fields across all 4 groups and cleared Address line 3. "Letterhead saved." appeared and the button went back to disabled. After a reload the values persisted and line 3 was empty. `GET /api/settings` returns them with `addressLine3: null`, and the DB `letterhead_address_line3 IS NULL`. Audit `org_settings.letterhead_updated` lists exactly the 11 changed fields | PASS | `04-*.png`, `05-*.png`, `06-api-alice-settings-after-save.json`, `07-db-letterhead-after-save.txt`, `07b-db-audit-letterhead.txt` |
| d | Validation. Input: email `not-an-email` and a 21-character branch code (max 20). Inline messages appeared ("Invalid email address", "Branch code must be 20 characters or fewer"), with `aria-invalid` on both fields and focus on Email. **No non-GET request was sent.** API and DB were unchanged (same `updated_at`). No "Something went wrong". After I restored the values the errors cleared and the button was disabled again (form pristine) | PASS | `08-validation-email-branch-1440.png`, `09-api-letterhead-after-invalid-attempt.json`, `09b-db-after-invalid-attempt.txt` |
| e | Other forms. I set the footer to "626C observer footer probe" and clicked "Save Settings". It showed "Settings saved successfully." and the value persisted across a reload. The letterhead stayed untouched and pristine. I then restored the footer to empty (DB NULL, as before) | PASS | `10-save-settings-footer-1440.png`, `11-db-footer-roundtrip-and-audit.txt` |
| f | Roles. Bob (admin) sees the card and saved Designation → "Managing Partner (Bob probe)". The DB and an audit row with actor Bob Admin confirm it, and I reverted it. Carol (member) sees only the General/Subscription tabs, and General shows only Org Documents: no Letterhead card, and no letterhead values in the page HTML | PASS | `13-bob-letterhead-saved-1440.png`, `14-db-bob-probe.txt`, `16-carol-firm-general-1440.png` |
| g | Console: 0 errors and 0 warnings for the whole session as Alice, Bob and Carol, with no hydration errors | PASS | `12-`, `15-`, `17-console-*.txt` |
| h | Final state: the post-(c) values (below), with API = DB | PASS | `18-api-alice-settings-final.json`, `19-db-letterhead-final.txt` |

## Defects
- **D1 (LOW, cosmetic):** The Letterhead card uses the shadcn `Card` (`bg-card … border-slate-200/80 shadow-sm`, `py-6` plus inner `px-6`). The neighbouring cards are plain `rounded-lg border border-slate-200 bg-white p-6` with no shadow. In the close-ups the Letterhead card has a faint off-white tint (`bg-card` = lab 99.4 vs pure white), a soft drop shadow and a slightly lighter border. Radius (8px), width (856px), heading size/weight and content x (569) are identical, so it is noticeable side by side but not jarring. Repro: Firm › General at 1440, compare Branding/Client Portal with Letterhead (`03-card-style-closeup-1440.png`, `03b-…png`).

## Notes (not 626C defects)
- In the full-page and element screenshots, the sticky top bar/sidebar appears mid-page (for example over "Postal code" in `02b`). This is a Playwright stitching artifact of fixed elements, not a product layout issue. The DOM measurements in (b) are authoritative.
- Pre-existing and outside scope: the shared "Save Settings" tax call sends `taxRegistrationNumber: "$undefined"` (server-action serialisation of undefined), and the audit `org_settings.tax_configured` stores `""`. The value was already empty, so there was no data change (`11-…txt`).

## Final letterhead values (new e2e baseline for 626D)
addressLine1 `88 Fredman Drive` · addressLine2 `Illovo Point, 3rd Floor` · addressLine3 **null** · city `Sandown` · postalCode `2196` · country `South Africa` · phone `+27 11 555 0199` · email `accounts@ndlovu-attorneys.co.za` · website `https://www.ndlovu-attorneys.co.za` · bankName `Standard Bank` · bankBranchCode `051001` · bankAccountNumber `0012345678` · bankAccountType `Cheque` · paymentReferenceNote `Please use your fee note number as the payment reference.` · signatoryName `Sipho Ndlovu` · signatoryDesignation `Managing Partner`.
Branding is unchanged: brand_color `#0f766e`, logo kept, document_footer_text NULL. `updated_at` 2026-10-27 21:25:33Z (Bob's revert).

## Cleanup
I created one file in `.playwright-mcp/` (`page-2026-10-27T21-22-33-009Z.yml`, a mock-login snapshot with no evidence value) and deleted it. The other files there are pre-existing and were left alone. The browser is closed.
