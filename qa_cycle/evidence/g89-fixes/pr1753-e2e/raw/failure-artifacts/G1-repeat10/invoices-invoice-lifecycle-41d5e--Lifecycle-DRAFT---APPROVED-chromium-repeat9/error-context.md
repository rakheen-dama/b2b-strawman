# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: invoices/invoice-lifecycle.spec.ts >> INV-02: Invoice Lifecycle >> DRAFT -> APPROVED
- Location: e2e/tests/invoices/invoice-lifecycle.spec.ts:88:7

# Error details

```
Error: expect(locator).toHaveCount(expected) failed

Locator:  getByRole('button', { name: 'Approve' })
Expected: 0
Received: 1
Timeout:  5000ms

Call log:
  - Expect "toHaveCount" with timeout 5000ms
  - waiting for getByRole('button', { name: 'Approve' })
    9 × locator resolved to 1 element
      - unexpected value "1"

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - link "Skip to content" [ref=e2] [cursor=pointer]:
    - /url: "#main-content"
  - generic [ref=e3]:
    - complementary [ref=e4]:
      - generic [ref=e6]: Kazi
      - generic [ref=e10]: E2E Test Organization
      - button "Search, Command K" [ref=e12]:
        - img [ref=e13]
        - text: Search...
        - generic [ref=e16]: ⌘K
      - navigation "Main navigation" [ref=e17]:
        - generic [ref=e18]:
          - button "Today" [expanded] [ref=e19]:
            - generic [ref=e20]: Today
            - img [ref=e21]
          - generic [ref=e23]:
            - link "Home" [ref=e24] [cursor=pointer]:
              - /url: /org/e2e-test-org/dashboard
              - img [ref=e25]
              - text: Home
            - link "My work" [ref=e28] [cursor=pointer]:
              - /url: /org/e2e-test-org/my-work
              - img [ref=e29]
              - text: My work
            - link "Diary" [ref=e32] [cursor=pointer]:
              - /url: /org/e2e-test-org/diary
              - img [ref=e33]
              - text: Diary
        - generic [ref=e36]:
          - button "Matters" [expanded] [ref=e37]:
            - generic [ref=e38]: Matters
            - img [ref=e39]
          - generic [ref=e41]:
            - link "Matters" [ref=e42] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
              - img [ref=e43]
              - text: Matters
            - link "New matter" [ref=e47] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters/new
              - img [ref=e48]
              - text: New matter
            - link "Conflict check" [ref=e49] [cursor=pointer]:
              - /url: /org/e2e-test-org/conflict-check
              - img [ref=e50]
              - text: Conflict check
            - link "Adverse parties" [ref=e54] [cursor=pointer]:
              - /url: /org/e2e-test-org/legal/adverse-parties
              - img [ref=e55]
              - text: Adverse parties
            - link "AI reviews" [ref=e60] [cursor=pointer]:
              - /url: /org/e2e-test-org/ai/reviews
              - img [ref=e61]
              - text: AI reviews
        - generic [ref=e65]:
          - button "Clients" [expanded] [ref=e66]:
            - generic [ref=e67]: Clients
            - img [ref=e68]
          - generic [ref=e70]:
            - link "Clients" [ref=e71] [cursor=pointer]:
              - /url: /org/e2e-test-org/clients
              - img [ref=e72]
              - text: Clients
            - link "FICA & compliance" [ref=e76] [cursor=pointer]:
              - /url: /org/e2e-test-org/compliance
              - img [ref=e77]
              - text: FICA & compliance
            - link "Data requests" [ref=e80] [cursor=pointer]:
              - /url: /org/e2e-test-org/compliance/requests
              - img [ref=e81]
              - text: Data requests
        - button "Fees" [ref=e86]:
          - generic [ref=e87]: Fees
          - img [ref=e88]
        - button "Trust" [ref=e92]:
          - generic [ref=e93]: Trust
          - img [ref=e94]
        - button "Firm" [ref=e98]:
          - generic [ref=e99]: Firm
          - img [ref=e100]
      - link "Platform Admin" [ref=e104] [cursor=pointer]:
        - /url: /platform-admin/access-requests
        - img [ref=e105]
        - text: Platform Admin
      - link "Notifications" [ref=e109] [cursor=pointer]:
        - /url: /org/e2e-test-org/notifications
        - img [ref=e110]
        - text: Notifications
      - generic [ref=e114]:
        - generic [ref=e115]: AO
        - generic [ref=e116]:
          - paragraph [ref=e117]: Alice Owner
          - paragraph [ref=e118]: alice@e2e-test.local
    - generic [ref=e119]:
      - banner [ref=e120]:
        - navigation "Breadcrumb" [ref=e121]:
          - link "E2E Test Organization" [ref=e122] [cursor=pointer]:
            - /url: /org/e2e-test-org/dashboard
          - generic [ref=e123]:
            - img [ref=e124]
            - link "Fee notes" [ref=e126] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices
          - generic [ref=e127]:
            - img [ref=e128]
            - generic "INV-0033" [ref=e130]
        - generic [ref=e131]:
          - generic [ref=e133]: e2e-test-org
          - button "AO" [ref=e135]
          - button "Notifications" [ref=e137]:
            - img
            - generic [ref=e138]: 36 unread notifications
            - generic [ref=e139]: "36"
      - main [ref=e140]:
        - generic [ref=e143]:
          - generic [ref=e144]:
            - link "Back to fee notes" [ref=e145] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices
              - img [ref=e146]
              - text: Back to fee notes
            - button "Generate Document" [ref=e148]:
              - img
              - text: Generate Document
              - img
          - generic [ref=e149]:
            - generic [ref=e150]:
              - generic [ref=e151]:
                - generic [ref=e152]:
                  - 'heading "INV-0033 Help: Fee note numbering" [level=1] [ref=e153]':
                    - text: INV-0033
                    - 'button "Help: Fee note numbering" [ref=e154] [cursor=pointer]':
                      - img [ref=e155]
                  - generic [ref=e158]: Approved
                - generic [ref=e159]:
                  - generic [ref=e160]: "Client: InvLife Client ph38"
                  - generic [ref=e161]: "Issued: 11 Nov 2026"
                  - generic [ref=e162]: "Due: 11 Dec 2026"
                  - generic [ref=e163]: "Currency: ZAR"
              - generic [ref=e164]:
                - button "Preview" [ref=e165]:
                  - img
                  - text: Preview
                - button "Send fee note" [ref=e166]
                - button "Void" [ref=e167]
            - generic [ref=e168]:
              - heading "Fee note details" [level=2] [ref=e169]
              - generic [ref=e170]:
                - generic [ref=e171]:
                  - term [ref=e172]: Due Date
                  - definition [ref=e173]: 11 Dec 2026
                - generic [ref=e174]:
                  - term [ref=e175]: Notes
                  - definition [ref=e176]: Lifecycle test ph38
            - generic [ref=e177]:
              - heading "Line Items" [level=2] [ref=e179]
              - table [ref=e181]:
                - rowgroup [ref=e182]:
                  - row "Description Matter Qty Rate Amount Tax" [ref=e183]:
                    - columnheader "Description" [ref=e184]
                    - columnheader "Matter" [ref=e185]
                    - columnheader "Qty" [ref=e186]
                    - columnheader "Rate" [ref=e187]
                    - columnheader "Amount" [ref=e188]
                    - columnheader "Tax" [ref=e189]
                - rowgroup [ref=e190]:
                  - row "Lifecycle line ph38 — 2 R 750,00 R 1 500,00 VAT — Standard (15%) R 225,00" [ref=e191]:
                    - cell "Lifecycle line ph38" [ref=e192]:
                      - generic [ref=e193]: Lifecycle line ph38
                    - cell "—" [ref=e194]
                    - cell "2" [ref=e195]
                    - cell "R 750,00" [ref=e196]
                    - cell "R 1 500,00" [ref=e197]
                    - cell "VAT — Standard (15%) R 225,00" [ref=e198]:
                      - generic [ref=e199]: VAT — Standard (15%) R 225,00
            - generic [ref=e201]:
              - generic [ref=e202]:
                - generic [ref=e203]: Subtotal
                - generic [ref=e204]: R 1 500,00
              - generic [ref=e205]:
                - generic [ref=e206]: VAT — Standard (15%)
                - generic [ref=e207]: R 225,00
              - generic [ref=e208]:
                - generic [ref=e209]: Total
                - generic [ref=e210]: R 1 725,00
          - generic [ref=e211]:
            - img [ref=e212]
            - heading "No custom fields configured" [level=2] [ref=e215]
            - paragraph [ref=e216]: Custom fields let you track additional information specific to your workflow.
            - link "Configure Fields" [ref=e217] [cursor=pointer]:
              - /url: /org/e2e-test-org/settings/matters?tab=custom-fields
          - generic [ref=e218]:
            - heading "Generated Documents" [level=2] [ref=e219]
            - paragraph [ref=e221]: No documents generated yet
          - generic [ref=e222]:
            - heading "Collection activity" [level=2] [ref=e223]
            - paragraph [ref=e225]: No collection activity for this fee note yet.
          - generic [ref=e226]:
            - heading "Audit trail" [level=2] [ref=e227]
            - list [ref=e229]:
              - listitem [ref=e230]:
                - button "INFO Fee note approved Alice Owner just now" [ref=e231]:
                  - generic [ref=e232]: INFO
                  - generic [ref=e233]: Fee note approved
                  - generic [ref=e235]: Alice Owner
                  - generic [ref=e236]: just now
              - listitem [ref=e237]:
                - button "INFO Fee note updated Alice Owner just now" [ref=e238]:
                  - generic [ref=e239]: INFO
                  - generic [ref=e240]: Fee note updated
                  - generic [ref=e242]: Alice Owner
                  - generic [ref=e243]: just now
              - listitem [ref=e244]:
                - button "INFO Fee note created Alice Owner just now" [ref=e245]:
                  - generic [ref=e246]: INFO
                  - generic [ref=e247]: Fee note created
                  - generic [ref=e249]: Alice Owner
                  - generic [ref=e250]: just now
  - generic [ref=e251]:
    - heading "Command Palette" [level=2] [ref=e252]
    - paragraph [ref=e253]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e254]
```

# Test source

```ts
  6   |  *
  7   |  * Prerequisites:
  8   |  *   1. E2E stack running: bash compose/scripts/e2e-up.sh
  9   |  *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
  10  |  *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
  11  |  *
  12  |  * Run:
  13  |  *   cd frontend/e2e
  14  |  *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test invoices/invoice-lifecycle --reporter=list
  15  |  */
  16  | import { test, expect, type Page } from "@playwright/test";
  17  | import {
  18  |   addFeeNoteLineViaApi,
  19  |   api,
  20  |   apiOk,
  21  |   approveAndSendFeeNote,
  22  |   createActiveClient,
  23  |   createDraftFeeNote,
  24  |   createMatter,
  25  |   createMatterRate,
  26  |   createTask,
  27  |   logTime,
  28  |   memberNamed,
  29  |   type FeeNote,
  30  | } from "../../helpers/api";
  31  | import { loginAs } from "../../fixtures/auth";
  32  | 
  33  | const ORG = "e2e-test-org";
  34  | const BASE = `/org/${ORG}`;
  35  | 
  36  | const RUN_ID = Date.now().toString(36).slice(-4);
  37  | const CLIENT_PREFIX = "InvLife Client";
  38  | 
  39  | /**
  40  |  * This file's own ACTIVE client (628F R-5): fee notes are never raised on the boot seed's Acme Corp,
  41  |  * whose fee notes the portal suite and REGRESSION read — and "the first ACTIVE client" is not
  42  |  * deterministic while sibling spec files create clients in parallel.
  43  |  */
  44  | async function createOwnClient(): Promise<string> {
  45  |   return createActiveClient(
  46  |     `${CLIENT_PREFIX} ${RUN_ID}`,
  47  |     `${CLIENT_PREFIX.toLowerCase().replace(/ /g, "-")}-${RUN_ID}@example.com`
  48  |   );
  49  | }
  50  | 
  51  | /** A DRAFT fee note with one manual line (so it can be approved). */
  52  | async function createDraftWithLine(customerId: string): Promise<FeeNote> {
  53  |   const draft = await createDraftFeeNote(customerId, `Lifecycle test ${RUN_ID}`);
  54  |   return addFeeNoteLineViaApi(draft.id, `Lifecycle line ${RUN_ID}`, 2, 750);
  55  | }
  56  | 
  57  | /** Voids the open (SENT) fee note through the header button and its native confirm(). */
  58  | async function voidInUi(page: Page): Promise<void> {
  59  |   // Void confirms with window.confirm (components/invoices/use-invoice-detail.ts) — accept it
  60  |   page.once("dialog", (dialog) => dialog.accept());
  61  |   await page.getByRole("button", { name: "Void", exact: true }).click();
  62  |   await expect(page.getByRole("button", { name: "Void", exact: true })).toHaveCount(0, {
  63  |     timeout: 15_000,
  64  |   });
  65  |   await expect(page.getByText("Void", { exact: true }).first()).toBeVisible();
  66  | }
  67  | 
  68  | async function timeEntry(taskId: string, entryId: string): Promise<{ invoiceId: string | null }> {
  69  |   const entries = await apiOk<{ id: string; invoiceId: string | null }[]>(
  70  |     `/api/tasks/${taskId}/time-entries`
  71  |   );
  72  |   const entry = entries.find((e) => e.id === entryId);
  73  |   expect(entry, `time entry ${entryId}`).toBeDefined();
  74  |   return entry!;
  75  | }
  76  | 
  77  | // ═══════════════════════════════════════════════════════════════════
  78  | //  INV-02: Invoice Lifecycle Transitions
  79  | // ═══════════════════════════════════════════════════════════════════
  80  | test.describe.serial("INV-02: Invoice Lifecycle", () => {
  81  |   let customerId: string;
  82  |   let invoiceId: string;
  83  | 
  84  |   test.beforeAll(async () => {
  85  |     customerId = await createOwnClient();
  86  |   });
  87  | 
  88  |   test("DRAFT -> APPROVED", async ({ page }) => {
  89  |     const invoice = await createDraftWithLine(customerId);
  90  |     invoiceId = invoice.id;
  91  | 
  92  |     await loginAs(page, "alice");
  93  |     await page.goto(`${BASE}/invoices/${invoiceId}`);
  94  |     await expect(page.getByText("Draft", { exact: true }).first()).toBeVisible({
  95  |       timeout: 10000,
  96  |     });
  97  | 
  98  |     // Click Approve button
  99  |     const approveBtn = page.getByRole("button", { name: "Approve" });
  100 |     await expect(approveBtn).toBeVisible({ timeout: 5000 });
  101 |     await approveBtn.click();
  102 |     // APPROVED offers Send fee note instead of Approve (invoice-header-actions.tsx)
  103 |     await expect(page.getByRole("button", { name: "Send fee note" })).toBeVisible({
  104 |       timeout: 15_000,
  105 |     });
> 106 |     await expect(approveBtn).toHaveCount(0);
      |                              ^ Error: expect(locator).toHaveCount(expected) failed
  107 | 
  108 |     // Verify invoice number assigned (INV-XXXX)
  109 |     await expect(page.getByText(/INV-\d+/).first()).toBeVisible({ timeout: 10000 });
  110 |   });
  111 | 
  112 |   test("APPROVED -> SENT", async ({ page }) => {
  113 |     await loginAs(page, "alice");
  114 |     await page.goto(`${BASE}/invoices/${invoiceId}`);
  115 |     await expect(page.getByText("Approved", { exact: true }).first()).toBeVisible({
  116 |       timeout: 10_000,
  117 |     });
  118 | 
  119 |     // Phase 87 S5 (ADR-344): label
  120 |     await page.getByRole("button", { name: "Send fee note" }).click();
  121 |     // Once SENT the header offers Record Payment and no longer Send (invoice-header-actions.tsx)
  122 |     await expect(page.getByRole("button", { name: "Record Payment" })).toBeVisible({
  123 |       timeout: 15_000,
  124 |     });
  125 |     await expect(page.getByRole("button", { name: "Send fee note" })).toHaveCount(0);
  126 |     await expect(page.getByText("Sent", { exact: true }).first()).toBeVisible();
  127 | 
  128 |     expect((await apiOk<FeeNote>(`/api/invoices/${invoiceId}`)).status).toBe("SENT");
  129 |   });
  130 | 
  131 |   test("SENT -> PAID (record payment)", async ({ page }) => {
  132 |     // Verify the invoice is actually in SENT status via API first
  133 | 
  134 |     // The previous test left the fee note SENT (serial chain)
  135 |     const invoice = await apiOk<FeeNote>(`/api/invoices/${invoiceId}`);
  136 |     expect(invoice.status).toBe("SENT");
  137 | 
  138 |     await loginAs(page, "alice");
  139 |     await page.goto(`${BASE}/invoices/${invoiceId}`);
  140 |     await expect(page.getByText("Sent", { exact: true }).first()).toBeVisible({ timeout: 10000 });
  141 | 
  142 |     // Click "Record Payment" button (in the header actions)
  143 |     const paymentBtn = page.getByRole("button", { name: /Record Payment/i });
  144 |     await expect(paymentBtn).toBeVisible({ timeout: 5000 });
  145 |     await paymentBtn.click();
  146 | 
  147 |     // (Phase 88, ADR-347) The inline payment form records the money that moved: amount
  148 |     // (defaults to the balance due — the full total on a SENT fee note), method (defaults to
  149 |     // EFT) and an optional reference.
  150 |     const form = page.getByTestId("record-payment-form");
  151 |     await expect(form).toBeVisible({ timeout: 5000 });
  152 |     await expect(form.getByLabel("Amount", { exact: true })).toHaveValue(
  153 |       String(Number(invoice.total))
  154 |     );
  155 |     await expect(form.getByTestId("payment-method-select")).toHaveText("EFT");
  156 | 
  157 |     // Fill the optional payment reference
  158 |     const refInput = form.getByPlaceholder(/CHK-12345|Wire transfer/i);
  159 |     await expect(refInput).toBeVisible();
  160 |     await refInput.fill(`PAY-${RUN_ID}`);
  161 | 
  162 |     // Click "Confirm Payment" — records the default amount (full balance) by EFT
  163 |     const confirmBtn = form.getByRole("button", { name: /Confirm Payment/i });
  164 |     await expect(confirmBtn).toBeVisible();
  165 |     await confirmBtn.click();
  166 |     await expect(form).toBeHidden({ timeout: 10000 });
  167 | 
  168 |     // Verify status shows Paid
  169 |     await page.reload();
  170 |     await expect(page.getByText("Paid", { exact: true }).first()).toBeVisible({ timeout: 10000 });
  171 | 
  172 |     // (Phase 88, ADR-347) A full payment marks PAID: amount_paid = total, balance due 0, one
  173 |     // EFT payment for the whole total carrying the reference.
  174 |     const paid = await apiOk<
  175 |       FeeNote & {
  176 |         amountPaid: number;
  177 |         balanceDue: number;
  178 |         payments: { paymentMethod: string; paymentReference: string; amount: number }[];
  179 |       }
  180 |     >(`/api/invoices/${invoiceId}`);
  181 |     expect(paid.status).toBe("PAID");
  182 |     expect(Number(paid.amountPaid)).toBe(Number(paid.total));
  183 |     expect(Number(paid.balanceDue)).toBe(0);
  184 |     expect(paid.payments).toHaveLength(1);
  185 |     expect(paid.payments[0]).toMatchObject({
  186 |       paymentMethod: "EFT",
  187 |       paymentReference: `PAY-${RUN_ID}`,
  188 |     });
  189 |     expect(Number(paid.payments[0].amount)).toBe(Number(paid.total));
  190 |   });
  191 | 
  192 |   test("VOID a sent invoice", async ({ page }) => {
  193 |     // Create a separate invoice for voiding, approved and sent via the API
  194 |     const invoice = await createDraftWithLine(customerId);
  195 |     await approveAndSendFeeNote(invoice.id);
  196 | 
  197 |     await loginAs(page, "alice");
  198 |     await page.goto(`${BASE}/invoices/${invoice.id}`);
  199 |     await expect(page.getByText("Sent", { exact: true }).first()).toBeVisible({ timeout: 10000 });
  200 |     await voidInUi(page);
  201 | 
  202 |     expect((await apiOk<FeeNote>(`/api/invoices/${invoice.id}`)).status).toBe("VOID");
  203 |   });
  204 | 
  205 |   test("VOID releases time entries", async ({ page }) => {
  206 |     // A fee note billed from logged time: voiding it returns the entry to unbilled
```