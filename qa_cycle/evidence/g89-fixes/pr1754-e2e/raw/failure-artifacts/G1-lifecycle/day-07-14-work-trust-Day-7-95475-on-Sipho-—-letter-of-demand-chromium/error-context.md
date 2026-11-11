# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-07-14-work-trust.spec.ts >> Day 7 — First Week of Work >> Bob: Log 120min on Sipho — letter of demand
- Location: e2e/tests/legal-lifecycle/day-07-14-work-trust.spec.ts:78:7

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: getByRole('tabpanel')
Expected substring: "Bob Admin"
Received string:    "FromToTotal Time1h 30mBillable1h 30mNon-billable0mContributors1Entries1By TaskTaskBillableTotalEntriesInitial consultation & case assessment1h 30m1h 30m1By MemberMemberBillableNon-billableTotalCarol Member1h 30m0m1h 30m"
Timeout: 5000ms

Call log:
  - Expect "toContainText" with timeout 5000ms
  - waiting for getByRole('tabpanel')
    9 × locator resolved to <div tabindex="0" role="tabpanel" data-state="active" class="pt-6 outline-none" data-orientation="horizontal" id="radix-_r_1i_-content-time" aria-labelledby="radix-_r_1i_-trigger-time">…</div>
      - unexpected value "FromToTotal Time1h 30mBillable1h 30mNon-billable0mContributors1Entries1By TaskTaskBillableTotalEntriesInitial consultation & case assessment1h 30m1h 30m1By MemberMemberBillableNon-billableTotalCarol Member1h 30m0m1h 30m"

```

# Page snapshot

```yaml
- generic [ref=e1]:
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
              - img [ref=e44]
              - text: Matters
            - link "New matter" [ref=e48] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters/new
              - img [ref=e49]
              - text: New matter
            - link "Conflict check" [ref=e50] [cursor=pointer]:
              - /url: /org/e2e-test-org/conflict-check
              - img [ref=e51]
              - text: Conflict check
            - link "Adverse parties" [ref=e55] [cursor=pointer]:
              - /url: /org/e2e-test-org/legal/adverse-parties
              - img [ref=e56]
              - text: Adverse parties
            - link "AI reviews" [ref=e61] [cursor=pointer]:
              - /url: /org/e2e-test-org/ai/reviews
              - img [ref=e62]
              - text: AI reviews
        - generic [ref=e66]:
          - button "Clients" [expanded] [ref=e67]:
            - generic [ref=e68]: Clients
            - img [ref=e69]
          - generic [ref=e71]:
            - link "Clients" [ref=e72] [cursor=pointer]:
              - /url: /org/e2e-test-org/clients
              - img [ref=e73]
              - text: Clients
            - link "FICA & compliance" [ref=e77] [cursor=pointer]:
              - /url: /org/e2e-test-org/compliance
              - img [ref=e78]
              - text: FICA & compliance
            - link "Data requests" [ref=e81] [cursor=pointer]:
              - /url: /org/e2e-test-org/compliance/requests
              - img [ref=e82]
              - text: Data requests
        - button "Fees" [ref=e87]:
          - generic [ref=e88]: Fees
          - img [ref=e89]
        - button "Trust" [ref=e93]:
          - generic [ref=e94]: Trust
          - img [ref=e95]
        - button "Firm" [ref=e99]:
          - generic [ref=e100]: Firm
          - img [ref=e101]
      - link "Notifications" [ref=e105] [cursor=pointer]:
        - /url: /org/e2e-test-org/notifications
        - img [ref=e106]
        - text: Notifications
      - generic [ref=e110]:
        - generic [ref=e111]: BA
        - generic [ref=e112]:
          - paragraph [ref=e113]: Bob Admin
          - paragraph [ref=e114]: bob@e2e-test.local
    - generic [ref=e115]:
      - banner [ref=e116]:
        - navigation "Breadcrumb" [ref=e117]:
          - link "E2E Test Organization" [ref=e118] [cursor=pointer]:
            - /url: /org/e2e-test-org/dashboard
          - generic [ref=e119]:
            - img [ref=e120]
            - link "Matters" [ref=e122] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
          - generic [ref=e123]:
            - img [ref=e124]
            - generic "Ndlovu v RAF mwc51syce90d" [ref=e126]
        - generic [ref=e127]:
          - generic [ref=e129]: e2e-test-org
          - button "BA" [ref=e131]
          - button "Notifications" [ref=e133]:
            - img
            - generic [ref=e134]: 7 unread notifications
            - generic [ref=e135]: "7"
      - main [ref=e136]:
        - generic [ref=e139]:
          - generic [ref=e140]:
            - link "Back to Matters" [ref=e141] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
              - img [ref=e142]
              - text: Back to Matters
            - generic [ref=e144]:
              - button "Generate Document" [ref=e145]:
                - img
                - text: Generate Document
                - img
              - button "Generate Statement of Account" [ref=e146]:
                - img
                - text: Generate Statement of Account
              - button "More actions" [ref=e147]:
                - img
                - generic [ref=e148]: More actions
          - generic [ref=e150]:
            - generic [ref=e151]:
              - heading "Ndlovu v RAF mwc51syce90d" [level=1] [ref=e152]
              - generic [ref=e153]:
                - generic [ref=e154]: Active
                - generic [ref=e155]: LITIGATION
              - generic [ref=e156]:
                - generic [ref=e157]:
                  - term [ref=e158]:
                    - generic [ref=e159]: Case no.
                  - definition [ref=e160]: 2026/12345
                - generic [ref=e161]:
                  - term [ref=e162]:
                    - generic [ref=e163]: ·
                    - generic [ref=e164]: Court
                  - definition [ref=e165]: Gauteng Division, Pretoria
                - generic [ref=e166]:
                  - term [ref=e167]:
                    - generic [ref=e168]: ·
                    - generic [ref=e169]: Opposing Party
                  - definition [ref=e170]: Road Accident Fund
              - link "Sipho Ndlovu mwc51syce90d" [ref=e172] [cursor=pointer]:
                - /url: /org/e2e-test-org/clients/bb15a775-ac38-48fe-8aef-fcfcc5f56dde
            - generic [ref=e173]:
              - button "Close Matter" [ref=e174]:
                - img
                - text: Close Matter
              - button "Complete Matter" [ref=e175]:
                - img
                - text: Complete Matter
              - button [ref=e176]:
                - img
          - generic [ref=e177]:
            - tablist [ref=e178]:
              - tab "Details" [ref=e179] [cursor=pointer]:
                - text: Details
                - img [ref=e180]
              - tab "Overview" [ref=e182] [cursor=pointer]
              - tab "Work" [ref=e183] [cursor=pointer]:
                - text: Work
                - img [ref=e184]
              - tab "Fees · Time recording" [active] [selected] [ref=e186] [cursor=pointer]:
                - text: Fees
                - generic [ref=e187]: · Time recording
                - img [ref=e188]
              - tab "Client" [ref=e191] [cursor=pointer]:
                - text: Client
                - img [ref=e192]
              - tab "Diary" [ref=e194] [cursor=pointer]
              - tab "Activity" [ref=e195] [cursor=pointer]:
                - text: Activity
                - img [ref=e196]
            - tabpanel [ref=e198]:
              - generic [ref=e199]:
                - generic [ref=e200]:
                  - generic [ref=e201]: From
                  - textbox [ref=e202]
                  - generic [ref=e203]: To
                  - textbox [ref=e204]
                - generic [ref=e205]:
                  - generic [ref=e206]:
                    - paragraph [ref=e207]: Total Time
                    - paragraph [ref=e208]: 1h 30m
                  - generic [ref=e209]:
                    - paragraph [ref=e210]: Billable
                    - paragraph [ref=e211]: 1h 30m
                  - generic [ref=e212]:
                    - paragraph [ref=e213]: Non-billable
                    - paragraph [ref=e214]: 0m
                  - generic [ref=e215]:
                    - paragraph [ref=e216]: Contributors
                    - paragraph [ref=e217]: "1"
                  - generic [ref=e218]:
                    - paragraph [ref=e219]: Entries
                    - paragraph [ref=e220]: "1"
                - generic [ref=e221]:
                  - heading "By Task" [level=3] [ref=e222]
                  - table [ref=e225]:
                    - rowgroup [ref=e226]:
                      - row "Task Billable Total Entries" [ref=e227]:
                        - columnheader "Task" [ref=e228]
                        - columnheader "Billable" [ref=e229]
                        - columnheader "Total" [ref=e230]
                        - columnheader "Entries" [ref=e231]
                    - rowgroup [ref=e232]:
                      - row "Initial consultation & case assessment 1h 30m 1h 30m 1" [ref=e233]:
                        - cell "Initial consultation & case assessment" [ref=e234]
                        - cell "1h 30m" [ref=e235]
                        - cell "1h 30m" [ref=e236]
                        - cell "1" [ref=e237]
                - generic [ref=e238]:
                  - heading "By Member" [level=3] [ref=e239]
                  - table [ref=e242]:
                    - rowgroup [ref=e243]:
                      - row "Member Billable Non-billable Total" [ref=e244]:
                        - columnheader "Member" [ref=e245]
                        - columnheader "Billable" [ref=e246]
                        - columnheader "Non-billable" [ref=e247]
                        - columnheader "Total" [ref=e248]
                    - rowgroup [ref=e249]:
                      - row "Carol Member 1h 30m 0m 1h 30m" [ref=e250]:
                        - cell "Carol Member" [ref=e251]
                        - cell "1h 30m" [ref=e252]
                        - cell "0m" [ref=e253]
                        - cell "1h 30m" [ref=e254]
  - generic [ref=e255]:
    - heading "Command Palette" [level=2] [ref=e256]
    - paragraph [ref=e257]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e258]: Kazi — Practice management, built for Africa
```

# Test source

```ts
  1   | /**
  2   |  * Days 7-14 — First Work + Trust Deposits
  3   |  *
  4   |  * Day 7: Carol, Bob and Alice log billable time on the tasks of the Sipho and Moroka matters (the
  5   |  * Day 0 rate cards apply), Bob comments on a task, Alice schedules Sipho's motion in the Diary.
  6   |  * Day 14: trust deposits for Moroka (R250,000) and QuickCollect (R45,000), Moroka's client ledger,
  7   |  * the Road Accident Fund adverse party and the conflict check that finds it, more of Carol's time,
  8   |  * and the trust dashboard.
  9   |  *
  10  |  * Records come from the lifecycle run (helpers/legal-lifecycle.ts): Days 0–3 must have run on the
  11  |  * same stack. Days 30–90 bill the unbilled time logged here and draw on these trust balances.
  12  |  *
  13  |  * Phase 88 628E: rewritten strict — every step is real or asserted (no `isVisible().catch` guards).
  14  |  *
  15  |  * Prerequisites:
  16  |  *   1. E2E stack running: bash compose/scripts/e2e-up.sh
  17  |  *   2. Days 0-3 tests completed (rates, trust account, 4 clients, template matters)
  18  |  *
  19  |  * Run:
  20  |  *   E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
  21  |  */
  22  | import { test, expect } from "@playwright/test";
  23  | import { loginAs } from "../../fixtures/auth";
  24  | import { captureScreenshot } from "../../helpers/screenshot";
  25  | import { openGroupedTab } from "../../helpers/grouped-tabs";
  26  | import { BASE, isoDate, openMatter, selectNativeOption } from "../../helpers/legal-depth-ii";
  27  | import {
  28  |   clientId,
  29  |   currentLifecycleRun,
  30  |   logTimeOnTask,
  31  |   morokaClient,
  32  |   morokaMatter,
  33  |   openTrustTransactions,
  34  |   qcClient,
  35  |   qcMatter,
  36  |   randPattern,
  37  |   recordTrustDeposit,
  38  |   recordValue,
  39  |   runConflictCheck,
  40  |   siphoMatter,
  41  |   TRUST_ACCOUNT,
  42  |   trustTransactionRow,
  43  | } from "../../helpers/legal-lifecycle";
  44  | 
  45  | const ADVERSE_PARTY = "Road Accident Fund";
  46  | const morokaDepositRef = () => `DEP/MOROKA/${currentLifecycleRun()}`;
  47  | const qcDepositRef = () => `DEP/QC/${currentLifecycleRun()}`;
  48  | 
  49  | // ═══════════════════════════════════════════════════════════════════
  50  | //  DAY 7 — First Week of Work
  51  | // ═══════════════════════════════════════════════════════════════════
  52  | test.describe.serial("Day 7 — First Week of Work", () => {
  53  |   // ── Carol: My Work, log time on Sipho matter ──────────────────────
  54  |   test("Carol: Navigate to My Work page", async ({ page }) => {
  55  |     await loginAs(page, "carol");
  56  |     await page.goto(`${BASE}/my-work`);
  57  |     await expect(page.getByRole("heading", { level: 1, name: "My Work" })).toBeVisible({
  58  |       timeout: 15_000,
  59  |     });
  60  |   });
  61  | 
  62  |   test("Carol: Log 90min time on Sipho matter", async ({ page }) => {
  63  |     await loginAs(page, "carol");
  64  |     // Carol (Member) sees the matter because Day 1 added her to its team (Phase 88 619E,
  65  |     // ADR-346: Members see only matters they are on).
  66  |     await openMatter(page, siphoMatter());
  67  |     await logTimeOnTask(page, {
  68  |       task: "Initial consultation & case assessment",
  69  |       hours: 1,
  70  |       minutes: 30,
  71  |       date: isoDate(),
  72  |       description: "Research case law — personal injury precedents",
  73  |       rate: "550,00",
  74  |     });
  75  |   });
  76  | 
  77  |   // ── Bob: Log time on Sipho (letter of demand) ─────────────────────
  78  |   test("Bob: Log 120min on Sipho — letter of demand", async ({ page }) => {
  79  |     await loginAs(page, "bob");
  80  |     await openMatter(page, siphoMatter());
  81  |     await logTimeOnTask(page, {
  82  |       task: "Letter of demand",
  83  |       hours: 2,
  84  |       minutes: 0,
  85  |       date: isoDate(),
  86  |       description: "Draft letter of demand to Road Accident Fund",
  87  |       rate: "1 200,00",
  88  |     });
  89  | 
  90  |     // Fees → Time recording summarises the matter's time by member: Carol's and Bob's entries
  91  |     await openGroupedTab(page, "finance", "time");
  92  |     const timePanel = page.getByRole("tabpanel");
  93  |     await expect(timePanel).toContainText("Carol Member", { timeout: 15_000 });
> 94  |     await expect(timePanel).toContainText("Bob Admin");
      |                             ^ Error: expect(locator).toContainText(expected) failed
  95  |     await expect(timePanel).toContainText("Letter of demand");
  96  |   });
  97  | 
  98  |   test("Bob: Add internal comment on the Sipho letter-of-demand task", async ({ page }) => {
  99  |     const comment = `Reviewed claim documents — need additional medical records from Dr. Nkosi (${currentLifecycleRun()})`;
  100 |     await loginAs(page, "bob");
  101 |     await openMatter(page, siphoMatter());
  102 |     await openGroupedTab(page, "work", "tasks");
  103 |     // Internal comments live on the task detail sheet (Comments tab)
  104 |     await page
  105 |       .getByRole("tabpanel")
  106 |       .getByRole("button", { name: "Open task detail for Letter of demand" })
  107 |       .click();
  108 |     const sheet = page.getByRole("dialog", { name: "Task Detail" });
  109 |     await expect(sheet).toBeVisible({ timeout: 15_000 });
  110 |     await sheet.getByRole("tab", { name: "Comments" }).click();
  111 |     await sheet.getByLabel("Add a comment").fill(comment);
  112 |     await sheet.getByLabel("Visibility").selectOption("INTERNAL");
  113 |     await sheet.getByRole("button", { name: "Post Comment" }).click();
  114 |     await expect(sheet.getByText(comment)).toBeVisible({ timeout: 15_000 });
  115 |     await expect(sheet.getByLabel("Add a comment")).toHaveValue("");
  116 |   });
  117 | 
  118 |   // ── Alice: Create court date for Sipho ────────────────────────────
  119 |   test("Alice: Create court date for Sipho (Motion, 30 days out)", async ({ page }) => {
  120 |     await loginAs(page, "alice");
  121 |     // Phase 87 S6b (ADR-344): /court-calendar → /diary
  122 |     await page.goto(`${BASE}/diary`);
  123 |     await page.getByRole("button", { name: "New court date" }).click();
  124 |     const dialog = page.getByRole("dialog", { name: "Schedule Court Date" });
  125 |     await expect(dialog).toBeVisible();
  126 |     await selectNativeOption(dialog.getByLabel("Matter"), siphoMatter());
  127 |     await dialog.getByLabel("Type").selectOption("MOTION");
  128 |     await dialog.getByLabel("Date").fill(isoDate(30));
  129 |     await dialog.getByLabel("Court Name").fill("Gauteng Division, Pretoria");
  130 |     await dialog.getByLabel(/^Description/).fill("Motion — Ndlovu v RAF");
  131 |     await dialog.getByRole("button", { name: "Schedule Court Date" }).click();
  132 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  133 | 
  134 |     // The matter's Diary tab lists the motion as Scheduled
  135 |     await openMatter(page, siphoMatter());
  136 |     // "Diary" is a single-tab group: a plain tab trigger, no sub-tab menu (grouped-tab-bar.tsx)
  137 |     const diaryTab = page.getByTestId("tab-group-schedule");
  138 |     await diaryTab.click();
  139 |     await expect(diaryTab).toHaveAttribute("aria-selected", "true");
  140 |     const motion = page.getByTestId("court-date-list").getByRole("row").filter({
  141 |       hasText: "Motion",
  142 |     });
  143 |     await expect(motion).toHaveCount(1, { timeout: 15_000 });
  144 |     await expect(motion).toContainText("Scheduled");
  145 |     await expect(motion).toContainText("Gauteng Division, Pretoria");
  146 | 
  147 |     // Screenshot: court calendar entry
  148 |     await captureScreenshot(page, "day-07-court-calendar-entry-scheduled");
  149 |     await captureScreenshot(page, "court-calendar-scheduled", { curated: true });
  150 |   });
  151 | 
  152 |   // ── Alice: Log time on Moroka estate ──────────────────────────────
  153 |   test("Alice: Log 60min on Moroka estate matter", async ({ page }) => {
  154 |     await loginAs(page, "alice");
  155 |     await openMatter(page, morokaMatter());
  156 |     await logTimeOnTask(page, {
  157 |       task: "Report estate to Master of High Court",
  158 |       hours: 1,
  159 |       minutes: 0,
  160 |       date: isoDate(),
  161 |       description: "Report to Master of the High Court — initial filing",
  162 |       rate: "2 500,00",
  163 |     });
  164 |   });
  165 | 
  166 |   // ── My Work lists today's entry ───────────────────────────────────
  167 |   test("Alice: My Work lists today's Moroka time entry", async ({ page }) => {
  168 |     await loginAs(page, "alice");
  169 |     await page.goto(`${BASE}/my-work`);
  170 |     await expect(page.getByRole("heading", { level: 1, name: "My Work" })).toBeVisible({
  171 |       timeout: 15_000,
  172 |     });
  173 |     await expect(
  174 |       page.getByText(`${morokaMatter()} — Report to Master of the High Court — initial filing`)
  175 |     ).toBeVisible({ timeout: 15_000 });
  176 | 
  177 |     await captureScreenshot(page, "day-07-my-work-legal-active");
  178 |   });
  179 | });
  180 | 
  181 | // ═══════════════════════════════════════════════════════════════════
  182 | //  DAY 14 — Trust Deposits & Conflict Detection
  183 | // ═══════════════════════════════════════════════════════════════════
  184 | test.describe.serial("Day 14 — Trust Deposits & Conflict Detection", () => {
  185 |   // ── 14.1-14.8: Trust deposit R250,000 for Moroka ──────────────────
  186 |   test("Alice: Navigate to Trust Accounting", async ({ page }) => {
  187 |     await loginAs(page, "alice");
  188 |     await page.goto(`${BASE}/trust-accounting`);
  189 |     await expect(page.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeVisible({
  190 |       timeout: 15_000,
  191 |     });
  192 |     // Day 0 created the account, so the overview offers its actions
  193 |     await expect(page.getByRole("link", { name: "Client Ledgers" })).toBeVisible();
  194 |     await expect(page.getByRole("link", { name: "Record Transaction" })).toBeVisible();
```