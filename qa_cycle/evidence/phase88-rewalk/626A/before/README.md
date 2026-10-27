# 626A / task 626.1 — reproduce G-B5 / G-C7 (a–c): letterhead on generated PDFs — BEFORE

Captured 2026-10-27 ~18:46–18:50 UTC on the E2E mock-auth stack (:3001 / :8081), which was built from main 1c2447f12 (code ≈ 977419fa8). Logged in as Alice (owner), org `e2e-test-org`, tenant `tenant_7d218705360b`. No product code was changed. No SQL writes were made; all SQL was read-only SELECTs.

## Steps
1. Went to Settings › Firm › General. Uploaded a 120×120 PNG logo (`kazi-test-logo.png`, a teal/orange checkerboard) and changed the brand colour from `#1b3a4b` to `#0f766e`, then clicked Save Settings. After a reload both values were still there (`01-...png`; the API shows `brandColor: "#0f766e"` and a presigned `logoUrl`, see `api-settings-alice-after-branding-change.json`). This page has no fields for firm address, banking details or signatory.
2. Engagement letter: opened matter "Khumalo Property Holdings — Erf 1234 Sandton Transfer" (client address on file: 12 Rivonia Road, Sandton 2196). Clicked Generate Document › "Engagement Letter — Conveyancing", kept the 5 default clauses, clicked Next: Preview (`02-...png`), then Download PDF → `engagement-letter-conveyancing.pdf`.
3. Fee note: opened INV-0001 (SENT, client Apex Holdings mvr15e76ef9a, client address on file: 12 Rivonia Road, Sandton 2196) (`03-...png`). Opened the HTML Preview tab (`04-...png`). Then clicked Generate Document › "Fee Note" › Download PDF (`05-...png`) → `fee-note-INV-0001.pdf`.
4. Inspected both PDFs:
   - Text dumps (`*.txt`) made with Ghostscript `txtwrite`.
   - Page renders (`*-pN.png`) made with Ghostscript `png16m` at 80 dpi.
   - `pixel-scan.txt`: a pixel scan for the brand teal and the logo orange, plus a decompression of the PDF content streams to count image XObjects and list the fill colours used.
5. API probes with a mock-IDP token (Alice): `GET /api/settings/letterhead` returns 404 "No static resource" (`api-settings-letterhead-alice.txt`). `GET /api/settings` returns no letterhead, address, banking or signatory keys (`api-settings-alice-*.json`).
6. Backend log lines from the PDF renders: `backend-log-pdf-render.txt` (the presigned signature is redacted).
7. Migration-number evidence: `migrations.md`.

## Observations (REPRODUCED = gap present)

| Check | Engagement letter PDF | Fee note PDF | Verdict |
|---|---|---|---|
| Logo present | No. 0 image XObjects, 0 logo-orange pixels. The template has no `org.logoUrl` variable (DB `has_logo_var=f`). | No. 0 image XObjects. The template *does* contain `org.logoUrl`, but the renderer's fetch of the presigned URL `http://localhost:4567/...logo.png` from inside the backend container failed with `ConnectException: Connection refused` (3×, see log). | REPRODUCED |
| Heading colour | `#1a1a2e` (dark navy/black). 0 brand-teal pixels. | `#1a1a2e`. 0 brand-teal pixels. | REPRODUCED. Brand colour is not applied. The only brand hook in `document-default.css` is `.header { border-bottom: 3px solid var(--brand-color, #1a1a2e) }`, which openhtmltopdf rejects ("var(-brand-color,#1a1a2e) is not a ... color ... Skipping declaration"). |
| Firm address | Absent. The letter shows only the org name as an H1. | Absent. Shows "From: E2E Test Organization" and an empty "VAT Registration No:". | REPRODUCED (no field exists for it) |
| "Banking details are available on request" | n/a | Present, on page 2: "Banking details are available on request or as provided in your engagement letter." | REPRODUCED |
| Client address | Absent ("Dear Khumalo…", "Client: Khumalo…" only; the word "Sandton" appears only inside the matter name). | Absent: "To: Apex Holdings mvr15e76ef9a" plus the client VAT number. The HTML preview shows the client email but no postal address. | REPRODUCED. The client address is on file but is not rendered. |
| Signatory | Firm name only: "Yours faithfully," at the bottom of page 2, then "E2E Test Organization" plus a rule pushed alone onto an otherwise blank page 3. No attorney name or capacity. | None | REPRODUCED |
| Letterhead API | — | — | `GET /api/settings/letterhead` → **404**. REPRODUCED (no API) |

## Other observations (not in 626A scope; listed for triage)
- **Orphan last page:** the engagement letter's page 3 holds only the firm-name signature line. This comes from a page-break or orphan problem in the sign-off block.
- **Logo URL unreachable in Docker:** the logo URL handed to the PDF renderer is the host-facing presigned LocalStack URL (`localhost:4567`), which the backend container cannot reach. On a deployed stack with a reachable S3 endpoint the fee-note logo might render. The fix design should embed the logo bytes (or use a data URI or an internal fetch) rather than rely on a URL the backend may not be able to reach.
- **Brand colour never reaches the documents:** `TemplateContextHelper` puts `org.brandColor` into the context, but `document-default.css` only refers to it through a CSS custom property, which openhtmltopdf does not support. The engagement-letter templates do not use the `.header` class at all.
- **Generic labels left in the legal profile:** the invoice "Generate Document" menu shows "Invoice Cover Letter", the matter menu shows "Project Summary Report", and the mock-login page header reads "DocTeams".
- **Fee note PDF content:** the table's VAT column wraps the amount "R525,00" across two lines (narrow column), and "Matter Reference:" and "VAT Registration No:" print with empty values.
- **State left changed on the stack:** this run left the e2e tenant with the logo uploaded and brand colour `#0f766e`. It was `#1b3a4b` with no logo before.

## Files
- `01-settings-firm-general-logo-brand-colour.png`: Settings › Firm › General after reload, showing the logo and `#0f766e`.
- `02-engagement-letter-preview-dialog.png`: Generate dialog, step 2 preview.
- `03-fee-note-INV-0001-detail.png`, `04-fee-note-INV-0001-html-preview.png`, `05-fee-note-generate-dialog.png`
- `engagement-letter-conveyancing.pdf`, `.txt`, `-p1..p3.png`
- `fee-note-INV-0001.pdf`, `.txt`, `-p1..p2.png`
- `pixel-scan.txt`: brand-teal and logo-orange pixel counts, image-XObject counts, PDF fill colours.
- `backend-log-pdf-render.txt`: openhtmltopdf CSS warning, logo `ConnectException`, `Generated PDF` lines.
- `api-settings-letterhead-alice.txt` (404), `api-settings-alice-before-branding-change.json`, `api-settings-alice-after-branding-change.json`
- `kazi-test-logo.png`: the uploaded logo.
- `migrations.md`: Flyway history on both stacks and the next-free conclusion (**V7**).
