# 626E: fee-note preview, SoA category labels, closure-letter arithmetic, trust-gate message — BEFORE

Captured 2026-10-27 between 23:42 and 23:50 UTC on the E2E mock-auth stack (:3001 / :8081).
- The backend container was started at about 23:25 UTC from main (`d7718c7e0`, Slice 626D merged; legal-za pack v8).
- Logged in as Alice (owner), org `e2e-test-org`, tenant `tenant_7d218705360b`.
- No product code was changed and no container was rebuilt or restarted. All SQL was read-only SELECTs. Data was created through the UI and REST API.

## Auth for the API
```bash
TOKEN=$(curl -s -X POST http://localhost:8090/token -H 'Content-Type: application/json' \
  -d '{"userId":"user_e2e_alice","orgId":"e2e-test-org","orgSlug":"e2e-test-org","orgRole":"owner"}' | jq -r .access_token)
```
Use it as `Authorization: Bearer $TOKEN` against `http://localhost:8081`.

PDF text dumps use Ghostscript `txtwrite` because `pdftotext` is not installed. Page PNGs use `png16m` at 80 dpi.

## Data created for this slice (re-use for the post-fix observer)
| What | id | Notes |
|---|---|---|
| Client (existing) | `513b4d65-951b-466d-9994-06ac6a721ff4` | Sipho Ndlovu mvr15e76ef9a; address 12 Rivonia Road, Sandton 2196 |
| **M1** matter "626E closure arithmetic repro" | `b9174723-4c60-402d-9055-301221368b32` | ref `626E-closure`. **CLOSED by override** at 23:48:26; closure log `cfa6b1be-35b6-472d-a48d-1986393be3b1` |
| M1 task | `8fe7c6f4-7108-41c1-8e65-1288f14f804c` | still open; this is the second failing gate |
| M1 time entries | `3df3a9c7-…` (3h @ R2 500 = R7 500, 2026-10-27), `2b72e0e3-…` (1h @ R2 500 = R2 500, 2026-10-28) | |
| M1 disbursements | `533e2910-…` SHERIFF_FEES R450 + VAT R67.50 (STANDARD_15); `309619cc-…` DEEDS_OFFICE_FEES R1 250 (ZERO_RATED_PASS_THROUGH) | both APPROVED, BILLED |
| **INV-0003** (M1) | `f44a9510-24d2-4f1d-83e9-bb8b86f5e212` | **PAID**: time R7 500 + sheriff R450, VAT R1 192.50, total R9 142.50 (EFT, ref 626E-INV-0003) |
| **INV-0004** (M1) | `cb2b4992-502f-4bb5-9d59-ea1947ab2e37` | **SENT**: time R2 500 + deeds R1 250, VAT R375.00, total R4 125.00 |
| Trust deposit (M1) | `ad9adba4-3a5e-462b-9d58-028f565a6da0` | **R38 000.00** into trust account `d599a34a-9479-483a-a268-8b21c146324b` (Mathebula & Partners Trust Account), ref 626E-DEP-001 |
| **M2** matter "626E draft fee note repro" | `2cb472bd-ab3e-41f3-8ad8-91ae08a7ff2a` | ACTIVE, ref `626E-draft-f` |
| M2 time / disbursement | `c59be114-…` 1.5h @ R2 500 = R3 750; `674fa1f3-…` SHERIFF_FEES R300 + VAT R45 | |
| **DRAFT fee note** (M2) | `852969e7-8cc1-4bca-83dd-7de636e74d8d` | created in the UI via Client › Fee notes › New fee note. Subtotal R4 050, VAT R607.50, total R4 657.50 |
| Closure letter (M1) | generated doc `31f37d54-9cbc-4db5-a6b7-43d23bd735ef` | closure-time SoA `126546f1-57f2-4f5a-b78a-40005d524fb6` |
| SoA (M1, manual) | generated doc `843341e7-d8da-4582-b67b-00f991b98f08` | period 2026-10-01 to 2026-10-28, internal draft |

Pre-existing SENT fee notes, used read-only:
- **INV-0001** `ed1c7f17-1673-4c52-b194-c778c0763d5a`;
- **INV-0002** `7382c448-cff0-431b-8d11-5ee0825679ce`.

`GET /api/matters/{id}/closure/evaluate` still works on the **closed** M1. The observer can re-read the trust-gate message without reopening it. To regenerate a closure letter post-fix, reopen M1 (`POST /api/matters/{id}/closure/reopen`) and close it again with override.

## Results

### 1. Fee-note preview (G-C7 d/e): REPRODUCED
`GET /api/invoices/{id}/preview` (the fee note page's **Preview** button, which opens a new tab at `:3001/api/invoices/{id}/preview`) returns the **Thymeleaf `templates/invoice-preview.html`** output. It does not use the firm's legal-za `fee-note-za` Tiptap template.
- **How we know it is Thymeleaf.** The markup carries that template's classes: `.header`, `.header-left`, `.invoice-number`, `.meta-line`, `.status-badge`. Its strings are the Thymeleaf ones: "Tax Number", "Fee Note: DRAFT", "BILL TO".
- **Files:**
  - DRAFT `852969e7`: `01a-…html` / `01a-…txt`, browser screenshot `02-draft-fee-note-preview-browser.png`;
  - SENT INV-0004: `01d-…html/.txt`, screenshot `03-sent-INV-0004-preview-browser.png`;
  - INV-0002: `01-…`; INV-0001: `01b-…`; INV-0003 (PAID): `01e-…`;
  - INV-0003 captured while it was still DRAFT: `01c-…`.
- **Money format.** Plain decimals with **no currency symbol and no grouping**: "2500.00", "3750.00", "Subtotal: 4050.00", "607.50", "**Total (ZAR)** 4657.50". It is *not* "R1,200.00" or "ZAR 1200.00" as the spec guessed. The only currency marker is the "Total (ZAR)" label.
- **Labels.** "VAT — Standard (15.00%)" and "Zero-rated (0.00%)" in the summary. Per line the VAT column reads "VAT — Standard 15.00%". "Tax Number: 4123456789" is used instead of "VAT Registration No". Quantities print as "1.5000" and "1.0000".
- **No letterhead.**
  - 0 `<img>` tags, so no logo.
  - No firm address: no "Fredman" in any preview.
  - No Tel, Email or Web.
  - No banking: no "Standard Bank".
  - No client address, only name and email.
  - No matter reference, no "tax invoice" (s20 VAT Act) wording, and no footer.
- **The preview differs from the sent PDF.** Comparison for INV-0004: preview `01d-…txt` versus Generate Document › Fee Note › Download PDF (`04-…dialog.png`, `05-sent-INV-0004-fee-note.pdf/.txt/-p1.png`). The PDF has:
  - the logo (1 image XObject);
  - "88 Fredman Drive, Illovo Point, 3rd Floor, Sandown 2196, South Africa", Tel, Email, Web, and "VAT Registration No: 4123456789";
  - the client address "12 Rivonia Road / Sandton 2196 / ZA" and "Matter Reference: 626E-closure";
  - amounts in the form "R2 500,00 / R375,00 / R2 875,00", "Subtotal (excl. VAT): R3 750,00", "VAT: R375,00", "Total Due: R4 125,00";
  - the banking block Standard Bank / 051001 / 0012345678 / Cheque / "Payment reference: INV-0004";
  - the s20 VAT Act tax-invoice sentence and the footer.

  The preview has **none** of these.

### 2. SoA disbursement category (G-D14): REPRODUCED
The M1 statement of account was produced via the UI (Generate Statement of Account › Preview & Save). Files: `10-soa-preview-dialog.png`; PDF `11-soa-M1.pdf`, `11-soa-M1.txt`, `-p1..p3.png`.

The Disbursements table's Category column prints the raw enums:
```
October     DEEDS_OFFICE_FEES office search              Pretoria           R1 250,00    R0,00
October     SHERIFF_FEES                of summons       Johannesburg       R450,00      R67,50
```
The labels "Sheriff fees" and "Deeds office fees" are expected. The UI's own unbilled-items picker already shows "Sheriff Fees" and "Deeds Office Fees", and the fee-note line descriptions show "Sheriff fees: …". The closure-time SoA (`33-closure-soa-M1.txt`) shows the same raw enums.

### 3. Closure-letter arithmetic (N-4): REPRODUCED
M1 was closed in the UI: Close Matter, then Continue, then Override failing gates with a justification, with "Generate closure letter" and "Generate Statement of Account" left checked (`30-…png`, `31-…png`). Files: `32-closure-letter-M1.pdf/.txt/-p1.png`. The letter's "Summary of Account" prints:
```
Total fees billed: 13267.50
Total disbursements: 1767.50
Duration (months): 0
```
Expected values, from read-only SQL (`34-closure-expected-values-sql.txt`) over SENT/PAID/PARTIALLY_PAID `invoice_lines` on M1, grouped by `line_type`:

| line_type | excl. VAT | VAT | incl. VAT |
|---|---|---|---|
| TIME | 10 000.00 | 1 500.00 | 11 500.00 |
| DISBURSEMENT | 1 700.00 | 67.50 | 1 767.50 |
| total | 11 700.00 | 1 567.50 | 13 267.50 (= INV-0003 9 142.50 + INV-0004 4 125.00) |

How the printed figures are made up:
- **"Total fees billed" 13267.50** is the sum of `Invoice.total` (`MatterClosureContextBuilder.totalFeesBilled`). It therefore **includes VAT (1 567.50) and the disbursements (1 767.50)**. Fees excl. VAT are 10 000.00 (11 500.00 incl. VAT).
- **"Total disbursements" 1767.50** is the sum of `legal_disbursements.amount + vat_amount` for APPROVED+BILLED rows. That is 1 700.00 + 67.50, which **includes VAT**.
- **Double count.** A reader adding the two lines gets 13 267.50 + 1 767.50 = **15 035.00**. The amount actually billed is **13 267.50**, so the disbursements (R1 767.50) are counted twice.
- **No VAT line** is printed. "Payments received" (R9 142.50) and the balance owing (R4 125.00) are not printed either.
- **Unformatted.** Both values are `BigDecimal.toPlainString()`: no "R", no space grouping, a decimal point rather than a comma. The rest of the pack prints "R13 267,50".

Supporting evidence from before this session:
- The Apex closure letter `17063e24-…` (generated 18:41 under pack v7) prints "Total fees billed: 4025.00" and "Total disbursements: 4025.00" (`35-…txt`).
- That matter's only fee note, INV-0001, is a single R3 500 + R525 VAT counsel-fee **disbursement** line. The whole invoice is therefore reported both as "fees" and as "disbursements".

### 4. Trust-balance gate message (G-C12): REPRODUCED (wording differs from the spec)
Sources: the M1 gate report in the UI (`30-close-matter-gate-report.png`), `GET …/closure/evaluate` (`40-closure-evaluate-M1.json`), and the persisted closure log (`36-closure-log-M1.json`). All three show exactly:
```
Matter trust balance is R38000.00. Transfer to client or office before closure.
```
- The amount is unformatted (`toPlainString()`, `TrustBalanceZeroGate.java:48`): no grouping and a decimal point. The rest of the UI renders "R38 000,00".
- **Spec claim partly false.** The text is *not* "Trust balance of R38000.00 must be zero". The defect, an unformatted amount, is real.
- The `detail.balance` of the persisted gate report is `38000.0`, a double, while live evaluate returns `38000.00`.

## Other observations (not investigated)
- **Gate grammar.** The open-task gate reads "**1 tasks remain open.**"
- **SoA Trust Activity spans matters.** The M1 SoA's "Trust Activity" lists the deposit "DEP/mvr15e76ef9a — Client deposit — RAF claim disbursements R5 000,00" and the payment "PAY/DEEDS … R1 250,00", both of which belong to matter f9059f5d. As a result:
  - the SoA prints "Closing balance: R41 750,00" and "Trust balance held: R41 750,00";
  - the matter-scoped closure gate says R38 000.00.

  Trust activity on the SoA looks client-scoped rather than matter-scoped.
- **Closure-time SoA period.** It ends on the closure date in UTC (2026-10-27). It therefore omits the 2026-10-28 time entry already billed on INV-0004: it shows fees R7 500 excl. VAT where the manual SoA shows R10 000.
- **SoA dialog clipped.** The "Generate Statement of Account" dialog is cut off on the right at a 1698 px viewport, and the PDF iframe area showed blank white when captured (`10-…png`).
- **Closure letter after 626D.** It now has the logo and the signatory ("Sipho Ndlovu / Managing Partner"). "Date: 2026-10-27" is still ISO-formatted.
