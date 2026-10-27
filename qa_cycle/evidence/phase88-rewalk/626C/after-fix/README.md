# 626C re-observation after the fix commit (PR #1687, head c25867b72)

- Date: 2026-10-27, e2e mock-auth stack. The frontend on :3001 was rebuilt from `/Users/rakheendama/Projects/2026/worktree-epic-626C` at **c25867b72** (on top of 800ce8c39). The image is `docteams-e2e-frontend` sha256:281f0ac931e7db02de40b345d07a458df7079a5dd51afd82e5c660b56c78be3c, and the container was started at 21:33:14Z.
- Rebuild commands: `docker compose -f compose/docker-compose.e2e.yml build frontend`, then `docker compose -f compose/docker-compose.e2e.yml up -d --no-deps frontend`. The backend, LocalStack and Postgres were not touched.
- New build confirmed in the DOM. The Letterhead card is a `DIV` with className `rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950`. It has no `shadow-sm` / `bg-card` and no `[data-slot=card]` ancestor. Its fieldsets carry `aria-describedby`, which did not exist at 800ce8c39.
- Tenant `tenant_7d218705360b`. The pre-state (`00-api-alice-settings-pre.json`) equals the after/ README's final values.

## Results
| # | Check | Result | Evidence |
|---|---|---|---|
| A | **D1 closed.** At 1440 the Branding, Letterhead and Client Portal cards compute identically: bg `rgb(255,255,255)`, border 1px with the same slate-200 lab colour, radius 8px, padding 24px, `box-shadow: none`, x 544 and width 856. The close-ups show no tint or shadow difference. At 1024 the layout is still intact: the card is 440 wide, all 16 controls sit in one column (x 569, w 390) and `scrollWidth` = 1024 | PASS | `01-card-computed-styles-1440.json`, `02-closeup-branding-vs-letterhead-1440.png`, `03-closeup-letterhead-vs-client-portal-1440.png`, `04-layout-1024.json`, `05-alice-general-1024-full.png` |
| B | **Regression smoke.** All 16 fields were pre-filled and equal the API, and "Save letterhead" was disabled while pristine. I changed Phone to `+27 11 555 0200` and saved: "Letterhead saved." appeared and the button went back to disabled. After a reload the phone was `+27 11 555 0200`. I then reverted it to `+27 11 555 0199` and saved. The final `GET /api/settings` letterhead is identical to the after/ README's final values. The only difference in the whole settings JSON is the presigned logo URL timestamp | PASS | `08-prefill-and-pristine-button.json`, `09-phone-0200-saved-1440.png`, `10-phone-0200-after-reload-1440.png`, `17-form-after-revert-reload.json`, `19-api-alice-settings-final.json`, `20-db-letterhead-final.txt` |
| C | **Stale alert clears.** I triggered a real server 400 from the UI on the first try. Email was a 65-character local part `aaaa…a@ndlovu-attorneys.co.za`: zod v4's email regex has no local-part length limit, but Hibernate `@Email` caps the local part at 64. The page showed the backend message "email must be a valid email" (zod's text would be "Invalid email address"), set `aria-invalid` on Email, and showed "Fix the highlighted fields and save again." The API was unchanged. After I edited a *different* field (Phone), the form alert disappeared. The Email field error correctly stayed while the value was still invalid. Restoring the email and saving succeeded | PASS | `12-server-400-email-field-1440.png`, `12b-server-400-contact-fieldset-1440.png`, `11-network-server-400-attempt.txt` (server action POST, no request tampering), `16-api-after-rejected-save.json`, `14-after-editing-phone-alert-state.json`, `15-alert-cleared-after-edit-1440.png` |
| D | **Accessibility.** There are 4 fieldsets, and each `aria-describedby` resolves to exactly one element: a `<p>` inside the fieldset with that group's description. Address → `_R_elbsnqbt9iv5ubtb_-address-description` ("The firm address printed in the letterhead."); Contact, Banking and Signatory follow the same pattern. The ids are unique, and the a11y tree shows the groups Address, Contact, Banking and Signatory | PASS | `06-fieldset-aria-describedby.json`, `07-a11y-snapshot-letterhead-form.yml` |
| E | **Console:** 0 errors and 0 warnings for the whole session, with no hydration warnings | PASS | `18-console-all-warnings.txt` |

## Defects
None. D1 from after/ is closed.

Note: the backend does not log validation 400s, so `13-backend-log-server-400.txt` shows only the successful `changedFields=[phone]` saves. The 400 is proven by the backend-authored field message and by the unchanged API state (`16-…json`).

## Final letterhead values (unchanged from after/)
addressLine1 `88 Fredman Drive` · addressLine2 `Illovo Point, 3rd Floor` · addressLine3 **null** · city `Sandown` · postalCode `2196` · country `South Africa` · phone `+27 11 555 0199` · email `accounts@ndlovu-attorneys.co.za` · website `https://www.ndlovu-attorneys.co.za` · bankName `Standard Bank` · bankBranchCode `051001` · bankAccountNumber `0012345678` · bankAccountType `Cheque` · paymentReferenceNote `Please use your fee note number as the payment reference.` · signatoryName `Sipho Ndlovu` · signatoryDesignation `Managing Partner`.
Branding is unchanged. DB `updated_at` is 2026-10-27 21:36:05Z (my revert save).

## Cleanup
Evidence was saved straight into this folder. I deleted the 11 auto-snapshot `.yml` files I created in `.playwright-mcp/` (timestamps 21-33-46 to 21-36-12); the 1162 pre-existing files there were left alone. The browser is closed.
