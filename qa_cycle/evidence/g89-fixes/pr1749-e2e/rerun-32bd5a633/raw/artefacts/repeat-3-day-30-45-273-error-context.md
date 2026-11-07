# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-30-45-soa-writeoff.spec.ts >> Day 30-45 — Statement of Account + Write-off (Phase 67) >> Alice: Generate Statement of Account with HTML preview
- Location: e2e/tests/legal-depth-ii/day-30-45-soa-writeoff.spec.ts:273:7

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: getByTestId('statement-of-account-dialog').getByTestId('statement-preview-container').locator('iframe[title="Document Preview"]').contentFrame().locator('body')
Timeout: 5000ms
- Expected substring  - 1
+ Received string     + 6

- Sheriff fee — service of process
+
+ E2E Test OrganizationStatement of AccountReference: SOA-6001bf0d-20261107Period: 9 August 2026 — 7 November 2026Generated: 7 November 2026ToSipho Ndlovu mw6gppz583c712 Rivonia Road
+ Sandton
+ 2196
+ ZAMatterMatter: Ndlovu v RAF — Depth II mw6gppz583c7Opened: 7 November 2026Professional FeesDateFee EarnerDescriptionHoursRateAmount7 November 2026Alice OwnerDrafted and served letter of demand on the Road Accident Fund (mw6gppz583c7)2R2 500,00R5 000,00Total fees (excl. VAT): R5 000,00VAT: R750,00Total fees (incl. VAT): R5 750,00DisbursementsDateCategoryDescriptionSupplierAmountVATTotal disbursements: R0,00Trust ActivityOpening balance: R0,00DepositsDateReferenceDescriptionAmountPaymentsDateTypeReferenceDescriptionAmountClosing balance: R0,00SummaryTotal fees: R0,00Total disbursements: R0,00Previous balance owing: R0,00Payments received: R0,00Closing balance owing: R0,00Trust balance held: R0,00Unbilled work in progress (not yet billed): R5 000,00
+

Call log:
  - Expect "toContainText" with timeout 5000ms
  - waiting for getByTestId('statement-of-account-dialog').getByTestId('statement-preview-container').locator('iframe[title="Document Preview"]').contentFrame().locator('body')
    9 × locator resolved to <body>…</body>
      - unexpected value "
E2E Test OrganizationStatement of AccountReference: SOA-6001bf0d-20261107Period: 9 August 2026 — 7 November 2026Generated: 7 November 2026ToSipho Ndlovu mw6gppz583c712 Rivonia Road
Sandton
2196
ZAMatterMatter: Ndlovu v RAF — Depth II mw6gppz583c7Opened: 7 November 2026Professional FeesDateFee EarnerDescriptionHoursRateAmount7 November 2026Alice OwnerDrafted and served letter of demand on the Road Accident Fund (mw6gppz583c7)2R2 500,00R5 000,00Total fees (excl. VAT): R5 000,00VAT: R750,00Total fees (incl. VAT): R5 750,00DisbursementsDateCategoryDescriptionSupplierAmountVATTotal disbursements: R0,00Trust ActivityOpening balance: R0,00DepositsDateReferenceDescriptionAmountPaymentsDateTypeReferenceDescriptionAmountClosing balance: R0,00SummaryTotal fees: R0,00Total disbursements: R0,00Previous balance owing: R0,00Payments received: R0,00Closing balance owing: R0,00Trust balance held: R0,00Unbilled work in progress (not yet billed): R5 000,00
"

```

# Page snapshot

```yaml
- generic [ref=e1]:
  - link [ref=e2] [cursor=pointer]:
    - /url: "#main-content"
    - text: Skip to content
  - generic [ref=e3]:
    - complementary [ref=e4]:
      - generic [ref=e6]: Kazi
      - generic [ref=e10]: E2E Test Organization
      - button [ref=e12]:
        - img [ref=e13]
        - text: Search...
        - generic [ref=e16]: ⌘K
      - navigation [ref=e17]:
        - generic [ref=e18]:
          - button [expanded] [ref=e19]:
            - generic [ref=e20]: Today
            - img [ref=e21]
          - generic [ref=e23]:
            - link [ref=e24] [cursor=pointer]:
              - /url: /org/e2e-test-org/dashboard
              - img [ref=e25]
              - text: Home
            - link [ref=e28] [cursor=pointer]:
              - /url: /org/e2e-test-org/my-work
              - img [ref=e29]
              - text: My work
            - link [ref=e32] [cursor=pointer]:
              - /url: /org/e2e-test-org/diary
              - img [ref=e33]
              - text: Diary
        - generic [ref=e36]:
          - button [expanded] [ref=e37]:
            - generic [ref=e38]: Matters
            - img [ref=e39]
          - generic [ref=e41]:
            - link [ref=e42] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
              - img [ref=e44]
              - text: Matters
            - link [ref=e48] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters/new
              - img [ref=e49]
              - text: New matter
            - link [ref=e50] [cursor=pointer]:
              - /url: /org/e2e-test-org/conflict-check
              - img [ref=e51]
              - text: Conflict check
            - link [ref=e55] [cursor=pointer]:
              - /url: /org/e2e-test-org/legal/adverse-parties
              - img [ref=e56]
              - text: Adverse parties
            - link [ref=e61] [cursor=pointer]:
              - /url: /org/e2e-test-org/ai/reviews
              - img [ref=e62]
              - text: AI reviews
        - generic [ref=e66]:
          - button [expanded] [ref=e67]:
            - generic [ref=e68]: Clients
            - img [ref=e69]
          - generic [ref=e71]:
            - link [ref=e72] [cursor=pointer]:
              - /url: /org/e2e-test-org/clients
              - img [ref=e73]
              - text: Clients
            - link [ref=e77] [cursor=pointer]:
              - /url: /org/e2e-test-org/compliance
              - img [ref=e78]
              - text: FICA & compliance
            - link [ref=e81] [cursor=pointer]:
              - /url: /org/e2e-test-org/compliance/requests
              - img [ref=e82]
              - text: Data requests
        - button [ref=e87]:
          - generic [ref=e88]: Fees
          - img [ref=e89]
        - button [ref=e93]:
          - generic [ref=e94]: Trust
          - img [ref=e95]
        - button [ref=e99]:
          - generic [ref=e100]: Firm
          - img [ref=e101]
      - link [ref=e105] [cursor=pointer]:
        - /url: /platform-admin/access-requests
        - img [ref=e106]
        - text: Platform Admin
      - link [ref=e110] [cursor=pointer]:
        - /url: /org/e2e-test-org/notifications
        - img [ref=e111]
        - text: Notifications
      - generic [ref=e115]:
        - generic [ref=e116]: AO
        - generic [ref=e117]:
          - paragraph [ref=e118]: Alice Owner
          - paragraph [ref=e119]: alice@e2e-test.local
    - generic [ref=e120]:
      - banner [ref=e121]:
        - navigation [ref=e122]:
          - link [ref=e123] [cursor=pointer]:
            - /url: /org/e2e-test-org/dashboard
            - text: E2E Test Organization
          - generic [ref=e124]:
            - img [ref=e125]
            - link [ref=e127] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
              - text: Matters
          - generic [ref=e128]:
            - img [ref=e129]
            - generic [ref=e131]: Ndlovu v RAF — Depth II mw6gppz583c7
        - generic [ref=e132]:
          - generic [ref=e134]: e2e-test-org
          - button [ref=e136]: AO
          - button "Notifications" [ref=e138]:
            - img
            - generic [ref=e139]: 25 unread notifications
            - generic [ref=e140]: "25"
      - main [ref=e141]:
        - generic [ref=e144]:
          - generic [ref=e145]:
            - link [ref=e146] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
              - img [ref=e147]
              - text: Back to Matters
            - generic [ref=e149]:
              - button [ref=e150]:
                - img
                - text: Generate Document
                - img
              - button [ref=e151]:
                - img
                - text: Generate Statement of Account
              - button [ref=e152]:
                - img
                - generic [ref=e153]: More actions
          - generic [ref=e155]:
            - generic [ref=e156]:
              - heading [level=1] [ref=e157]: Ndlovu v RAF — Depth II mw6gppz583c7
              - generic [ref=e158]:
                - generic [ref=e159]: Active
                - generic [ref=e160]: LITIGATION
              - link [ref=e162] [cursor=pointer]:
                - /url: /org/e2e-test-org/clients/83b95180-3988-4030-904d-6683dbe5bfca
                - text: Sipho Ndlovu mw6gppz583c7
            - generic [ref=e163]:
              - button [ref=e164]:
                - img
                - text: Close Matter
              - button [ref=e165]:
                - img
                - text: Complete Matter
              - button [ref=e166]:
                - img
          - generic [ref=e167]:
            - tablist [ref=e168]:
              - tab [ref=e169] [cursor=pointer]:
                - text: Details
                - img [ref=e170]
              - tab [ref=e172] [cursor=pointer]: Overview
              - tab [ref=e173] [cursor=pointer]:
                - text: Work
                - img [ref=e174]
              - tab [selected] [ref=e176] [cursor=pointer]:
                - text: Fees
                - generic [ref=e177]: · Statements of account
                - img [ref=e178]
              - tab [ref=e181] [cursor=pointer]:
                - text: Client
                - img [ref=e182]
              - tab [ref=e184] [cursor=pointer]: Diary
              - tab [ref=e185] [cursor=pointer]:
                - text: Activity
                - img [ref=e186]
            - tabpanel [ref=e188]:
              - generic [ref=e189]:
                - generic [ref=e190]:
                  - heading [level=3] [ref=e191]: Statements of Account
                  - button [ref=e192]:
                    - img
                    - text: Generate Statement of Account
                - table [ref=e194]:
                  - rowgroup [ref=e195]:
                    - row [ref=e196]:
                      - columnheader [ref=e197]: Generated
                      - columnheader [ref=e198]: Closing balance owing
                      - columnheader [ref=e199]: Trust balance held
                      - columnheader [ref=e200]: Actions
                  - rowgroup [ref=e201]:
                    - row [ref=e202]:
                      - cell [ref=e203]: 7 Nov 2026
                      - cell [ref=e204]: R 0,00
                      - cell [ref=e205]: R 0,00
                      - cell [ref=e206]:
                        - button [ref=e207]:
                          - img
                          - text: Download
  - generic [ref=e208]:
    - heading [level=2] [ref=e209]: Command Palette
    - paragraph [ref=e210]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e211]: Ndlovu v RAF — Depth II mw6gppz583c7
  - dialog "Generate Statement of Account" [active] [ref=e213]:
    - generic [ref=e214]:
      - generic [ref=e215]:
        - heading "Generate Statement of Account" [level=2] [ref=e216]
        - paragraph [ref=e217]: Generate a statement of account for Ndlovu v RAF — Depth II mw6gppz583c7. The PDF will be saved to this matter's documents.
      - generic [ref=e218]:
        - generic [ref=e219]:
          - generic [ref=e220]:
            - generic [ref=e221]: Period start
            - textbox "Period start" [ref=e222]: 2026-08-09
          - generic [ref=e223]:
            - generic [ref=e224]: Period end
            - textbox "Period end" [ref=e225]: 2026-11-07
        - generic [ref=e226]:
          - generic [ref=e227]:
            - checkbox "Share on the client portal" [ref=e228]
            - checkbox
            - generic [ref=e229]:
              - generic [ref=e230]: Share on the client portal
              - paragraph [ref=e231]: The client can see this statement when they sign in to the portal.
          - generic [ref=e232]:
            - checkbox "Email the client now" [ref=e233]
            - checkbox
            - generic [ref=e234]:
              - generic [ref=e235]: Email the client now
              - paragraph [ref=e236]: Also shares it, and requests an email with a link to the client's portal contact.
              - paragraph [ref=e237]: No email is sent if the client has no portal contact, or if they were already emailed about this kind of document on this matter in the last 5 minutes.
          - paragraph [ref=e238]: Save as internal draft — the client will not see it.
        - iframe [ref=e244]:
          - generic [active] [ref=f1e1]:
            - heading "E2E Test Organization" [level=1] [ref=f1e2]
            - separator [ref=f1e3]
            - heading "Statement of Account" [level=2] [ref=f1e4]
            - paragraph [ref=f1e5]:
              - strong [ref=f1e6]: "Reference:"
              - text: SOA-6001bf0d-20261107
            - paragraph [ref=f1e7]:
              - strong [ref=f1e8]: "Period:"
              - text: 9 August 2026 — 7 November 2026
            - paragraph [ref=f1e9]:
              - strong [ref=f1e10]: "Generated:"
              - text: 7 November 2026
            - separator [ref=f1e11]
            - heading "To" [level=3] [ref=f1e12]
            - paragraph [ref=f1e13]: Sipho Ndlovu mw6gppz583c7
            - paragraph [ref=f1e14]: 12 Rivonia Road Sandton 2196 ZA
            - separator [ref=f1e15]
            - heading "Matter" [level=3] [ref=f1e16]
            - paragraph [ref=f1e17]:
              - strong [ref=f1e18]: "Matter:"
              - text: Ndlovu v RAF — Depth II mw6gppz583c7
            - paragraph [ref=f1e19]:
              - strong [ref=f1e20]: "Opened:"
              - text: 7 November 2026
            - separator [ref=f1e21]
            - heading "Professional Fees" [level=3] [ref=f1e22]
            - table [ref=f1e23]:
              - rowgroup [ref=f1e24]:
                - row "Date Fee Earner Description Hours Rate Amount" [ref=f1e25]:
                  - columnheader "Date" [ref=f1e26]
                  - columnheader "Fee Earner" [ref=f1e27]
                  - columnheader "Description" [ref=f1e28]
                  - columnheader "Hours" [ref=f1e29]
                  - columnheader "Rate" [ref=f1e30]
                  - columnheader "Amount" [ref=f1e31]
              - rowgroup [ref=f1e32]:
                - row "7 November 2026 Alice Owner Drafted and served letter of demand on the Road Accident Fund (mw6gppz583c7) 2 R2 500,00 R5 000,00" [ref=f1e33]:
                  - cell "7 November 2026" [ref=f1e34]
                  - cell "Alice Owner" [ref=f1e35]
                  - cell "Drafted and served letter of demand on the Road Accident Fund (mw6gppz583c7)" [ref=f1e36]
                  - cell "2" [ref=f1e37]
                  - cell "R2 500,00" [ref=f1e38]
                  - cell "R5 000,00" [ref=f1e39]
            - paragraph [ref=f1e40]:
              - strong [ref=f1e41]: "Total fees (excl. VAT):"
              - text: R5 000,00
            - paragraph [ref=f1e42]:
              - strong [ref=f1e43]: "VAT:"
              - text: R750,00
            - paragraph [ref=f1e44]:
              - strong [ref=f1e45]: "Total fees (incl. VAT):"
              - text: R5 750,00
            - separator [ref=f1e46]
            - heading "Disbursements" [level=3] [ref=f1e47]
            - table [ref=f1e48]:
              - rowgroup [ref=f1e49]:
                - row "Date Category Description Supplier Amount VAT" [ref=f1e50]:
                  - columnheader "Date" [ref=f1e51]
                  - columnheader "Category" [ref=f1e52]
                  - columnheader "Description" [ref=f1e53]
                  - columnheader "Supplier" [ref=f1e54]
                  - columnheader "Amount" [ref=f1e55]
                  - columnheader "VAT" [ref=f1e56]
              - rowgroup
            - paragraph [ref=f1e57]:
              - strong [ref=f1e58]: "Total disbursements:"
              - text: R0,00
            - separator [ref=f1e59]
            - heading "Trust Activity" [level=3] [ref=f1e60]
            - paragraph [ref=f1e61]:
              - strong [ref=f1e62]: "Opening balance:"
              - text: R0,00
            - paragraph [ref=f1e63]:
              - strong [ref=f1e64]: Deposits
            - table [ref=f1e65]:
              - rowgroup [ref=f1e66]:
                - row "Date Reference Description Amount" [ref=f1e67]:
                  - columnheader "Date" [ref=f1e68]
                  - columnheader "Reference" [ref=f1e69]
                  - columnheader "Description" [ref=f1e70]
                  - columnheader "Amount" [ref=f1e71]
              - rowgroup
            - paragraph [ref=f1e72]:
              - strong [ref=f1e73]: Payments
            - table [ref=f1e74]:
              - rowgroup [ref=f1e75]:
                - row "Date Type Reference Description Amount" [ref=f1e76]:
                  - columnheader "Date" [ref=f1e77]
                  - columnheader "Type" [ref=f1e78]
                  - columnheader "Reference" [ref=f1e79]
                  - columnheader "Description" [ref=f1e80]
                  - columnheader "Amount" [ref=f1e81]
              - rowgroup
            - paragraph [ref=f1e82]:
              - strong [ref=f1e83]: "Closing balance:"
              - text: R0,00
            - separator [ref=f1e84]
            - heading "Summary" [level=3] [ref=f1e85]
            - paragraph [ref=f1e86]:
              - strong [ref=f1e87]: "Total fees:"
              - text: R0,00
            - paragraph [ref=f1e88]:
              - strong [ref=f1e89]: "Total disbursements:"
              - text: R0,00
            - paragraph [ref=f1e90]:
              - strong [ref=f1e91]: "Previous balance owing:"
              - text: R0,00
            - paragraph [ref=f1e92]:
              - strong [ref=f1e93]: "Payments received:"
              - text: R0,00
            - paragraph [ref=f1e94]:
              - strong [ref=f1e95]: "Closing balance owing:"
              - text: R0,00
            - paragraph [ref=f1e96]:
              - strong [ref=f1e97]: "Trust balance held:"
              - text: R0,00
            - paragraph [ref=f1e98]:
              - strong [ref=f1e99]: "Unbilled work in progress (not yet billed):"
              - text: R5 000,00
            - separator [ref=f1e100]
            - paragraph
        - generic [ref=e245]:
          - button "Close" [ref=e246]
          - button "Download PDF" [ref=e247]:
            - img
            - text: Download PDF
          - button "Regenerate" [ref=e248]:
            - img
            - text: Regenerate
      - button "Close" [ref=e249]:
        - img
        - generic [ref=e250]: Close
```

# Test source

```ts
  52  | const SHERIFF_FEE_DESCRIPTION = "Sheriff fee — service of process";
  53  | /** Attempt-scoped so a retried group's statement is checked against its own time entry. */
  54  | const timeDescription = () =>
  55  |   `Drafted and served letter of demand on the Road Accident Fund (${attemptRunId()})`;
  56  | const WRITE_OFF_REASON =
  57  |   "Client dispute — unable to recover from third party, firm absorbing the cost";
  58  | 
  59  | async function openStatementsTab(page: Page) {
  60  |   await openMatter(page, siphoMatterName());
  61  |   await openGroupedTab(page, "finance", "statements");
  62  |   const tab = page.getByTestId("project-statements-tab");
  63  |   await expect(tab).toBeVisible();
  64  |   await expect(tab.getByText(/Loading statements/)).toBeHidden({ timeout: 15_000 });
  65  |   return tab;
  66  | }
  67  | 
  68  | async function generateStatement(page: Page, tab: Locator): Promise<Locator> {
  69  |   await tab.getByTestId("open-generate-statement-btn").click();
  70  |   const dialog = page.getByTestId("statement-of-account-dialog");
  71  |   await expect(dialog).toBeVisible();
  72  |   // Period = matter-opened (approximated as 90 days ago) to today
  73  |   await dialog.getByTestId("statement-period-start-input").fill(isoDate(-90));
  74  |   await dialog.getByTestId("statement-period-end-input").fill(isoDate());
  75  |   await dialog.getByTestId("statement-generate-btn").click();
  76  |   await expect(dialog.getByTestId("statement-download-btn")).toBeVisible({ timeout: 30_000 });
  77  |   return dialog;
  78  | }
  79  | 
  80  | /** Whitespace-tolerant pattern for a printed rand amount, from its text ("R 1 500,00"). */
  81  | function amountPattern(printed: string): RegExp {
  82  |   const digits = printed.replace(/\D/g, "");
  83  |   const grouped = digits.slice(0, -2).split("").join("[\\s\\u00a0\\u202f]?");
  84  |   return new RegExp(`R\\s?${grouped},${digits.slice(-2)}`);
  85  | }
  86  | 
  87  | /** The amount printed after `label` in the preview text, as a pattern (fails if absent). */
  88  | function printedAmount(text: string, label: string): RegExp {
  89  |   const match = text.match(
  90  |     new RegExp(`${escapeRegExp(label)}:\\s*R\\s?([\\d\\s\\u00a0\\u202f]+,\\d{2})`)
  91  |   );
  92  |   expect(match, `"${label}" amount in the statement`).not.toBeNull();
  93  |   return amountPattern(match![1]);
  94  | }
  95  | 
  96  | /**
  97  |  * What the statement must show at this point of the group. First attempt: 2h at R2 500 = R5 000
  98  |  * fees excl. VAT; the Day 5 sheriff fee (R750) + deeds-office fee (R1 250) until the Day 45
  99  |  * write-off takes the sheriff fee out. A retried group adds time, so its WIP is only non-zero.
  100 |  */
  101 | interface ExpectedStatement {
  102 |   sheriffFeeListed: boolean;
  103 |   /** Disbursements table total, e.g. /R\s?2\s?000,00/. */
  104 |   disbursementsTotal: RegExp;
  105 |   /** First-attempt WIP, e.g. /R\s?7\s?000,00/. */
  106 |   unbilledWip: RegExp;
  107 | }
  108 | 
  109 | const BEFORE_WRITE_OFF: ExpectedStatement = {
  110 |   sheriffFeeListed: true,
  111 |   disbursementsTotal: /R\s?2\s?000,00/,
  112 |   unbilledWip: /R\s?7\s?000,00/,
  113 | };
  114 | 
  115 | const AFTER_WRITE_OFF: ExpectedStatement = {
  116 |   sheriffFeeListed: false,
  117 |   disbursementsTotal: /R\s?1\s?250,00/,
  118 |   unbilledWip: /R\s?6\s?250,00/,
  119 | };
  120 | 
  121 | /**
  122 |  * Fees + disbursements (Day 5) + trust activity (Day 5) + summary. Returns the printed closing
  123 |  * balance owing and trust balance held, as patterns for the saved statement's row.
  124 |  */
  125 | async function verifyStatementPreview(
  126 |   dialog: Locator,
  127 |   expected: ExpectedStatement = BEFORE_WRITE_OFF
  128 | ): Promise<{ closingBalanceOwing: RegExp; trustBalanceHeld: RegExp }> {
  129 |   const preview = dialog
  130 |     .getByTestId("statement-preview-container")
  131 |     .frameLocator('iframe[title="Document Preview"]')
  132 |     .locator("body");
  133 |   await expect(preview).toContainText("Statement of Account", { timeout: 15_000 });
  134 |   await expect(preview).toContainText(siphoMatterName());
  135 |   await expect(preview).toContainText(siphoClientName());
  136 |   // Fees: the 2h letter-of-demand entry, billed at Alice's (non-zero) rate
  137 |   await expect(preview).toContainText("Professional Fees");
  138 |   const feeRow = preview.locator("tr", { hasText: timeDescription() });
  139 |   await expect(feeRow).toHaveCount(1);
  140 |   await expect(feeRow.locator("td")).toHaveText([
  141 |     /\d/, // date
  142 |     /\S/, // fee earner
  143 |     timeDescription(),
  144 |     "2",
  145 |     /^R\s?[1-9][\d\s]*,\d{2}$/, // rate
  146 |     /^R\s?[1-9][\d\s]*,\d{2}$/, // amount
  147 |   ]);
  148 |   // Disbursements: the Day 5 sheriff fee (R750) + trust-linked deeds-office fee (R1,250)
  149 |   // (scenario 30.4; 621D S1); after the Day 45 write-off only the deeds-office fee (#1748: the
  150 |   // statement lists approved, not-written-off disbursements)
  151 |   if (expected.sheriffFeeListed) {
> 152 |     await expect(preview).toContainText(SHERIFF_FEE_DESCRIPTION);
      |                           ^ Error: expect(locator).toContainText(expected) failed
  153 |   } else {
  154 |     await expect(preview).not.toContainText(SHERIFF_FEE_DESCRIPTION);
  155 |   }
  156 |   await expect(preview).toContainText(
  157 |     new RegExp(`Total disbursements:\\s*${expected.disbursementsTotal.source}`)
  158 |   );
  159 |   // Trust activity: Sipho's client trust ledger (client-scoped) with the Day 5 deposit + payment
  160 |   const runId = currentDepthIiRun();
  161 |   await expect(preview).toContainText("Trust Activity");
  162 |   await expect(preview).toContainText(new RegExp(`Deposits[\\s\\S]*DEP/${runId}`));
  163 |   await expect(preview).toContainText(new RegExp(`Payments[\\s\\S]*PAY/DEEDS/${runId}`));
  164 |   await expect(preview).toContainText(/Trust balance held:\s*R\s?[1-9]/);
  165 |   // Summary (fee-note basis, D9): no fee note issued yet, so nothing owing; the unbilled fees +
  166 |   // disbursements are the work in progress, shown but never counted as owing
  167 |   await expect(preview).toContainText("Summary");
  168 |   await expect(preview).toContainText(/Closing balance owing:\s*R\s?0,00/);
  169 |   const wip = test.info().retry === 0 ? expected.unbilledWip : /R\s?[1-9][\d\s\u00a0\u202f]*,\d{2}/;
  170 |   await expect(preview).toContainText(
  171 |     new RegExp(`Unbilled work in progress \\(not yet billed\\):\\s*${wip.source}`)
  172 |   );
  173 |   // The printed amounts, for the saved statement's row
  174 |   const text = (await preview.textContent()) ?? "";
  175 |   return {
  176 |     closingBalanceOwing: printedAmount(text, "Closing balance owing"),
  177 |     trustBalanceHeld: printedAmount(text, "Trust balance held"),
  178 |   };
  179 | }
  180 | 
  181 | async function openSheriffFeeDetail(page: Page) {
  182 |   await openDisbursementList(page, siphoMatterName());
  183 |   const row = page
  184 |     .locator('[data-testid^="disbursement-row-"]')
  185 |     .filter({ hasText: siphoMatterName() })
  186 |     .filter({ hasText: SHERIFF_FEE_DESCRIPTION })
  187 |     .first();
  188 |   await expect(row).toContainText("Approved", { timeout: 15_000 });
  189 |   const detail = page.getByTestId("disbursement-detail");
  190 |   // Ready once its detail page has rendered
  191 |   await clickThrough(row, detail.getByText(SHERIFF_FEE_DESCRIPTION, { exact: true }));
  192 |   return detail;
  193 | }
  194 | 
  195 | /** data-testid of the statement row saved by "Save Statement" (same worker within the group). */
  196 | let savedStatementRowId = "";
  197 | 
  198 | test.describe.serial("Day 30-45 — Statement of Account + Write-off (Phase 67)", () => {
  199 |   // ── Day 0 prerequisite: a billing rate for Alice on Sipho's client ──
  200 |   // A freshly seeded tenant has no billing rates (legal-lifecycle Day 0 sets org rates), so
  201 |   // time would be billed at R0. A client-level override keeps the setup scoped to this run.
  202 |   test("[setup] Alice (Thandi): Billing rate override for Alice on Sipho's client", async ({
  203 |     page,
  204 |   }) => {
  205 |     await loginAs(page, "alice");
  206 |     await openClient(page, siphoClientName());
  207 |     await openGroupedTab(page, "finance", "rates");
  208 |     const aliceRate = page
  209 |       .getByRole("tabpanel")
  210 |       .getByRole("row")
  211 |       .filter({ hasText: "Alice Owner" });
  212 |     // A retried group keeps the first attempt's override (a second one would overlap it)
  213 |     if (test.info().retry > 0 && (await aliceRate.count()) > 0) return;
  214 |     await page.getByRole("tabpanel").getByRole("button", { name: "Add Override" }).first().click();
  215 |     // Phase 88 628A (ADR-357): label → "Add Client Rate Override"
  216 |     const dialog = page.getByRole("dialog", { name: "Add Client Rate Override" });
  217 |     await expect(dialog).toBeVisible();
  218 |     await selectNativeOption(dialog.getByLabel("Member"), "Alice Owner");
  219 |     await dialog.getByLabel("Hourly Rate").fill("2500");
  220 |     await dialog.getByLabel("Effective From").fill(isoDate(-90));
  221 |     await dialog.getByRole("button", { name: "Create Override" }).click();
  222 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  223 |     await expect(aliceRate).toContainText(/2[\s\u00a0\u202f]?500,00/, { timeout: 15_000 });
  224 |   });
  225 | 
  226 |   // ── Day 7 prerequisite: fee-earning time on Sipho's matter ────────
  227 |   test("[setup] Alice (Thandi): Log 2h on Sipho's matter — letter of demand", async ({ page }) => {
  228 |     await loginAs(page, "alice");
  229 |     await openMatter(page, siphoMatterName());
  230 |     await openGroupedTab(page, "work", "tasks");
  231 | 
  232 |     const taskRow = page.getByRole("row").filter({ hasText: "Letter of demand" }).first();
  233 |     await expect(taskRow).toBeVisible({ timeout: 15_000 });
  234 |     await taskRow.getByRole("button", { name: "Log Time" }).click();
  235 | 
  236 |     const dialog = page.getByRole("dialog", { name: "Log Time" });
  237 |     await expect(dialog).toBeVisible();
  238 |     await dialog.locator("#time-hours").fill("2");
  239 |     await dialog.locator("#time-minutes").fill("0");
  240 |     await dialog.locator("#time-date").fill(isoDate());
  241 |     await dialog.locator("#time-description").fill(timeDescription());
  242 |     await expect(dialog.locator("#time-billable")).toBeChecked();
  243 |     await dialog.getByRole("button", { name: "Log Time" }).click();
  244 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  245 | 
  246 |     // Finance → Time summarises the entry by task and by member (a retried group adds to
  247 |     // the first attempt's 2h; the entry itself is checked line-by-line in the statement)
  248 |     await openGroupedTab(page, "finance", "time");
  249 |     const timePanel = page.getByRole("tabpanel");
  250 |     const hours = test.info().retry === 0 ? "2h" : "\\d+h";
  251 |     await expect(timePanel).toContainText(new RegExp(`Letter of demand\\s*${hours}`), {
  252 |       timeout: 15_000,
```