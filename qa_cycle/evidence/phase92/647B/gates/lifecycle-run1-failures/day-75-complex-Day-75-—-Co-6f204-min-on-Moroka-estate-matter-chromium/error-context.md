# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-75-complex.spec.ts >> Day 75 — Complex Engagement & Adverse Parties >> Alice: Log 360min on Moroka estate matter
- Location: e2e/tests/legal-lifecycle/day-75-complex.spec.ts:224:7

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: getByRole('tabpanel')
Expected pattern: /Prepare Liquidation & Distribution account\s*6h/
Received string:  "FromToTotal Time1hBillable1hNon-billable0mContributors1Entries1By TaskTaskBillableTotalEntriesReport estate to Master of High Court1h1h1By MemberMemberBillableNon-billableTotalAlice Owner1h0m1h"
Timeout: 15000ms

Call log:
  - Expect "toContainText" with timeout 15000ms
  - waiting for getByRole('tabpanel')
    16 × locator resolved to <div tabindex="0" role="tabpanel" data-state="active" class="pt-6 outline-none" data-orientation="horizontal" id="radix-_r_3j_-content-time" aria-labelledby="radix-_r_3j_-trigger-time">…</div>
       - unexpected value "FromToTotal Time1hBillable1hNon-billable0mContributors1Entries1By TaskTaskBillableTotalEntriesReport estate to Master of High Court1h1h1By MemberMemberBillableNon-billableTotalAlice Owner1h0m1h"

```

# Page snapshot

```yaml
- generic [ref=e1]:
  - link "Skip to content" [ref=e2] [cursor=pointer]:
    - /url: "#main-content"
  - generic [ref=e3]:
    - generic [ref=e6]:
      - generic [ref=e7]:
        - generic [ref=e9]: Kazi
        - generic [ref=e12]: E2E Test Organization
        - list [ref=e13]:
          - listitem [ref=e14]:
            - button "Search, Command K" [ref=e15]:
              - img [ref=e16]
              - generic [ref=e19]: Search...
              - generic [ref=e20]: ⌘K
      - navigation "Main navigation" [ref=e22]:
        - generic [ref=e24]:
          - button "Today" [expanded] [ref=e25]:
            - text: Today
            - img [ref=e26]
          - list [ref=e30]:
            - listitem [ref=e31]:
              - link "Home" [ref=e32] [cursor=pointer]:
                - /url: /org/e2e-test-org/dashboard
                - img [ref=e33]
                - generic [ref=e36]: Home
            - listitem [ref=e37]:
              - link "My work" [ref=e38] [cursor=pointer]:
                - /url: /org/e2e-test-org/my-work
                - img [ref=e39]
                - generic [ref=e42]: My work
            - listitem [ref=e43]:
              - link "Diary" [ref=e44] [cursor=pointer]:
                - /url: /org/e2e-test-org/diary
                - img [ref=e45]
                - generic [ref=e47]: Diary
        - generic [ref=e49]:
          - button "Matters" [expanded] [ref=e50]:
            - text: Matters
            - img [ref=e51]
          - list [ref=e55]:
            - listitem [ref=e56]:
              - link "Matters" [ref=e57] [cursor=pointer]:
                - /url: /org/e2e-test-org/matters
                - img [ref=e58]
                - generic [ref=e62]: Matters
            - listitem [ref=e63]:
              - link "New matter" [ref=e64] [cursor=pointer]:
                - /url: /org/e2e-test-org/matters/new
                - img [ref=e65]
                - generic [ref=e66]: New matter
            - listitem [ref=e67]:
              - link "Conflict check" [ref=e68] [cursor=pointer]:
                - /url: /org/e2e-test-org/conflict-check
                - img [ref=e69]
                - generic [ref=e73]: Conflict check
            - listitem [ref=e74]:
              - link "Adverse parties" [ref=e75] [cursor=pointer]:
                - /url: /org/e2e-test-org/legal/adverse-parties
                - img [ref=e76]
                - generic [ref=e81]: Adverse parties
            - listitem [ref=e82]:
              - link "AI reviews" [ref=e83] [cursor=pointer]:
                - /url: /org/e2e-test-org/ai/reviews
                - img [ref=e84]
                - generic [ref=e87]: AI reviews
        - generic [ref=e89]:
          - button "Clients" [expanded] [ref=e90]:
            - text: Clients
            - img [ref=e91]
          - list [ref=e95]:
            - listitem [ref=e96]:
              - link "Clients" [ref=e97] [cursor=pointer]:
                - /url: /org/e2e-test-org/clients
                - img [ref=e98]
                - generic [ref=e102]: Clients
            - listitem [ref=e103]:
              - link "FICA & compliance" [ref=e104] [cursor=pointer]:
                - /url: /org/e2e-test-org/compliance
                - img [ref=e105]
                - generic [ref=e108]: FICA & compliance
            - listitem [ref=e109]:
              - link "Data requests" [ref=e110] [cursor=pointer]:
                - /url: /org/e2e-test-org/compliance/requests
                - img [ref=e111]
                - generic [ref=e114]: Data requests
        - button "Fees" [ref=e117]:
          - text: Fees
          - img [ref=e118]
        - button "Trust" [ref=e122]:
          - text: Trust
          - img [ref=e123]
        - button "Firm" [ref=e127]:
          - text: Firm
          - img [ref=e128]
      - generic [ref=e130]:
        - list [ref=e131]:
          - listitem [ref=e132]:
            - link "Platform Admin" [ref=e133] [cursor=pointer]:
              - /url: /platform-admin/access-requests
              - img [ref=e134]
              - generic [ref=e136]: Platform Admin
        - list [ref=e137]:
          - listitem [ref=e138]:
            - link "Notifications" [ref=e139] [cursor=pointer]:
              - /url: /org/e2e-test-org/notifications
              - img [ref=e140]
              - generic [ref=e143]: Notifications
        - generic [ref=e144]:
          - generic [ref=e145]: AO
          - generic [ref=e146]:
            - paragraph [ref=e147]: Alice Owner
            - paragraph [ref=e148]: alice@e2e-test.local
    - generic [ref=e149]:
      - banner [ref=e150]:
        - button "Toggle menu" [ref=e151]:
          - img
          - generic [ref=e152]: Toggle menu
        - navigation "Breadcrumb" [ref=e153]:
          - link "E2E Test Organization" [ref=e154] [cursor=pointer]:
            - /url: /org/e2e-test-org/dashboard
          - generic [ref=e155]:
            - img [ref=e156]
            - link "Matters" [ref=e158] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
          - generic [ref=e159]:
            - img [ref=e160]
            - generic "Estate Late Peter Moroka mwkvvpzx1671" [ref=e162]
        - generic [ref=e163]:
          - button "Search, Command K" [ref=e164]:
            - img [ref=e165]
            - generic [ref=e168]: Search
            - generic: ⌘K
          - button "Notifications" [ref=e170]:
            - img
            - generic [ref=e171]: 10 unread notifications
            - generic [ref=e172]: "10"
          - generic [ref=e174]: e2e-test-org
          - button "AO" [ref=e176]
      - main [ref=e177]:
        - generic [ref=e180]:
          - generic [ref=e181]:
            - link "Back to Matters" [ref=e182] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
              - img [ref=e183]
              - text: Back to Matters
            - generic [ref=e185]:
              - button "Generate Document" [ref=e186]:
                - img
                - text: Generate Document
                - img
              - button "Generate Statement of Account" [ref=e187]:
                - img
                - text: Generate Statement of Account
              - button "More actions" [ref=e188]:
                - img
                - generic [ref=e189]: More actions
          - generic [ref=e190]:
            - generic [ref=e191]:
              - generic [ref=e192]:
                - heading "Estate Late Peter Moroka mwkvvpzx1671" [level=1] [ref=e193]
                - generic [ref=e194]:
                  - generic [ref=e195]: Active
                  - generic [ref=e196]: ESTATES
                - generic [ref=e197]:
                  - link "Moroka Family Trust mwkvvpzx1671" [ref=e198] [cursor=pointer]:
                    - /url: /org/e2e-test-org/clients/e5d76bb7-eb26-46bb-adbf-e6e02cf99a96
                  - text: ·Alice Owner·Opened 17 Nov 2026
              - generic [ref=e200]:
                - button "Close Matter" [ref=e201]:
                  - img
                  - text: Close Matter
                - button "Complete Matter" [ref=e202]:
                  - img
                  - text: Complete Matter
                - button [ref=e203]:
                  - img
            - generic [ref=e204]:
              - group "Unbilled WIP" [ref=e206]:
                - generic [ref=e208]:
                  - generic [ref=e209]: Unbilled WIP
                  - generic [ref=e210]: R 15 000,00
                - generic [ref=e211]: 6,0h · 1 entry
              - group "Trust balance" [ref=e213]:
                - generic [ref=e215]:
                  - generic [ref=e216]: Trust balance
                  - generic [ref=e217]: R 241 500,00
              - group "Outstanding" [ref=e219]:
                - generic [ref=e221]:
                  - generic [ref=e222]: Outstanding
                  - generic [ref=e223]: R 125,00
                - generic [ref=e224]: 0 overdue
              - group "Next court date" [ref=e226]:
                - generic [ref=e228]:
                  - generic [ref=e229]: Next court date
                  - generic [ref=e230]:
                    - text: —
                    - generic [ref=e231]: Not available
                - generic [ref=e232]: None scheduled
          - generic [ref=e233]:
            - tablist [ref=e238]:
              - tab "Details" [ref=e239] [cursor=pointer]:
                - text: Details
                - img [ref=e240]
              - tab "Overview" [ref=e242] [cursor=pointer]
              - tab "Work" [ref=e243] [cursor=pointer]:
                - text: Work
                - img [ref=e244]
              - tab "Fees · Time recording" [active] [selected] [ref=e246] [cursor=pointer]:
                - text: Fees
                - generic [ref=e247]: · Time recording
                - img [ref=e248]
              - tab "Client" [ref=e250] [cursor=pointer]:
                - text: Client
                - img [ref=e251]
              - tab "Diary" [ref=e253] [cursor=pointer]
              - tab "Activity" [ref=e254] [cursor=pointer]:
                - text: Activity
                - img [ref=e255]
            - tabpanel [ref=e257]:
              - generic [ref=e258]:
                - generic [ref=e259]:
                  - generic [ref=e260]: From
                  - textbox [ref=e261]
                  - generic [ref=e262]: To
                  - textbox [ref=e263]
                - generic [ref=e264]:
                  - generic [ref=e265]:
                    - paragraph [ref=e266]: Total Time
                    - paragraph [ref=e267]: 1h
                  - generic [ref=e268]:
                    - paragraph [ref=e269]: Billable
                    - paragraph [ref=e270]: 1h
                  - generic [ref=e271]:
                    - paragraph [ref=e272]: Non-billable
                    - paragraph [ref=e273]: 0m
                  - generic [ref=e274]:
                    - paragraph [ref=e275]: Contributors
                    - paragraph [ref=e276]: "1"
                  - generic [ref=e277]:
                    - paragraph [ref=e278]: Entries
                    - paragraph [ref=e279]: "1"
                - generic [ref=e280]:
                  - heading "By Task" [level=3] [ref=e281]
                  - table [ref=e284]:
                    - rowgroup [ref=e285]:
                      - row "Task Billable Total Entries" [ref=e286]:
                        - columnheader "Task" [ref=e287]
                        - columnheader "Billable" [ref=e288]
                        - columnheader "Total" [ref=e289]
                        - columnheader "Entries" [ref=e290]
                    - rowgroup [ref=e291]:
                      - row "Report estate to Master of High Court 1h 1h 1" [ref=e292]:
                        - cell "Report estate to Master of High Court" [ref=e293]
                        - cell "1h" [ref=e294]
                        - cell "1h" [ref=e295]
                        - cell "1" [ref=e296]
                - generic [ref=e297]:
                  - heading "By Member" [level=3] [ref=e298]
                  - table [ref=e301]:
                    - rowgroup [ref=e302]:
                      - row "Member Billable Non-billable Total" [ref=e303]:
                        - columnheader "Member" [ref=e304]
                        - columnheader "Billable" [ref=e305]
                        - columnheader "Non-billable" [ref=e306]
                        - columnheader "Total" [ref=e307]
                    - rowgroup [ref=e308]:
                      - row "Alice Owner 1h 0m 1h" [ref=e309]:
                        - cell "Alice Owner" [ref=e310]
                        - cell "1h" [ref=e311]
                        - cell "0m" [ref=e312]
                        - cell "1h" [ref=e313]
  - generic [ref=e314]:
    - heading "Command Palette" [level=2] [ref=e315]
    - paragraph [ref=e316]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e317]
```

# Test source

```ts
  137 |     await linkAdverseParty(page, {
  138 |       party: PILLAY,
  139 |       client: siphoClient(),
  140 |       relationship: "WITNESS",
  141 |       description: "Managing agent of the adjoining property",
  142 |     });
  143 |   });
  144 | 
  145 |   // ── 75.7-75.10: Conflict stress test ──────────────────────────────
  146 |   // The surname alone is too short a fuzzy match for the client record ("Sipho Ndlovu <run>"); it
  147 |   // matches both of Sipho's matters by name
  148 |   test('Bob: Conflict stress test — search "Ndlovu" flags Sipho\'s two matters (Potential Conflict)', async ({
  149 |     page,
  150 |   }) => {
  151 |     await loginAs(page, "bob");
  152 |     const result = await runConflictCheck(page, "Ndlovu");
  153 |     await expect(result.getByRole("heading", { level: 3 })).toHaveText("Potential Conflict");
  154 |     for (const matter of [siphoMatter(), siphoPropertyMatter()]) {
  155 |       const match = result.getByRole("row").filter({ hasText: matter });
  156 |       await expect(match, matter).toHaveCount(1);
  157 |       await expect(match).toContainText("MATTER NAME");
  158 |     }
  159 |   });
  160 | 
  161 |   test('Bob: Conflict stress test — search "Road Accident Fund" finds the adverse party on the RAF matter', async ({
  162 |     page,
  163 |   }) => {
  164 |     await loginAs(page, "bob");
  165 |     const result = await runConflictCheck(page, RAF);
  166 |     await expect(result.getByRole("heading", { level: 3 })).toHaveText("Conflict Found");
  167 |     const match = result.getByRole("row").filter({ hasText: siphoMatter() });
  168 |     await expect(match).toHaveCount(1);
  169 |     await expect(match).toContainText(RAF);
  170 |     await expect(match).toContainText("OPPOSING PARTY");
  171 |   });
  172 | 
  173 |   test('Bob: Conflict stress test — search "Mokoena" finds the adverse party on the property matter', async ({
  174 |     page,
  175 |   }) => {
  176 |     await loginAs(page, "bob");
  177 |     const result = await runConflictCheck(page, "Mokoena");
  178 |     await expect(result.getByRole("heading", { level: 3 })).toHaveText(
  179 |       /^(Potential Conflict|Conflict Found)$/
  180 |     );
  181 |     const match = result.getByRole("row").filter({ hasText: siphoPropertyMatter() });
  182 |     await expect(match).toHaveCount(1);
  183 |     await expect(match).toContainText(MOKOENA);
  184 |     await expect(match).toContainText("OPPOSING PARTY");
  185 | 
  186 |     // Screenshot: conflict adverse party detection
  187 |     await captureScreenshot(page, "day-75-conflict-adverse-detected");
  188 |   });
  189 | 
  190 |   // ── 75.11-75.15: Estate progression (Moroka) ─────────────────────
  191 |   test("Alice: Progress Moroka estate — claim and complete two estate tasks", async ({ page }) => {
  192 |     await loginAs(page, "alice");
  193 |     await openMatter(page, morokaMatter());
  194 |     await openGroupedTab(page, "work", "tasks");
  195 |     const panel = page.getByRole("tabpanel");
  196 |     const tasks = [
  197 |       "Advertise for creditors (Gazette + local newspaper)",
  198 |       "Open estate bank account",
  199 |     ];
  200 |     const taskRow = (task: string) =>
  201 |       panel
  202 |         .getByRole("row")
  203 |         .filter({ has: page.getByRole("button", { name: `Open task detail for ${task}` }) });
  204 |     for (const task of tasks) {
  205 |       const row = taskRow(task);
  206 |       await expect(row, `task "${task}"`).toHaveCount(1, { timeout: 15_000 });
  207 |       // Unassigned OPEN task: Claim (→ In Progress, assigned to Alice), then Done
  208 |       await row.getByRole("button", { name: "Claim" }).click();
  209 |       await expect(row).toContainText("In Progress", { timeout: 15_000 });
  210 |       await row.getByRole("button", { name: "Done" }).click();
  211 |       // A done task leaves the default (open + in-progress) view
  212 |       await expect(row).toHaveCount(0, { timeout: 15_000 });
  213 |     }
  214 |     // …and is listed under the "Done" filter
  215 |     const doneChip = panel.locator("button[aria-pressed]").filter({ hasText: /^Done$/ });
  216 |     await doneChip.click();
  217 |     await expect(doneChip).toHaveAttribute("aria-pressed", "true");
  218 |     for (const task of tasks) {
  219 |       await expect(taskRow(task), `done task "${task}"`).toHaveCount(1, { timeout: 15_000 });
  220 |       await expect(taskRow(task)).toContainText("Done");
  221 |     }
  222 |   });
  223 | 
  224 |   test("Alice: Log 360min on Moroka estate matter", async ({ page }) => {
  225 |     await loginAs(page, "alice");
  226 |     await openMatter(page, morokaMatter());
  227 |     await logTimeOnTask(page, {
  228 |       task: "Prepare Liquidation & Distribution account",
  229 |       hours: 6,
  230 |       minutes: 0,
  231 |       date: isoDate(),
  232 |       description: "Preparation of Liquidation & Distribution account",
  233 |       rate: "2 500,00",
  234 |     });
  235 |     // Fees → Time recording shows the six hours against the task
  236 |     await openGroupedTab(page, "finance", "time");
> 237 |     await expect(page.getByRole("tabpanel")).toContainText(
      |                                              ^ Error: expect(locator).toContainText(expected) failed
  238 |       /Prepare Liquidation & Distribution account\s*6h/,
  239 |       { timeout: 15_000 }
  240 |     );
  241 |   });
  242 | 
  243 |   test("Alice: Send information request to Moroka (beneficiary details)", async ({ page }) => {
  244 |     await loginAs(page, "alice");
  245 |     await openClient(page, morokaClient());
  246 |     await openGroupedTab(page, "compliance", "requests");
  247 |     const panel = page.getByRole("tabpanel");
  248 |     await panel.getByRole("button", { name: "New Request" }).click();
  249 |     const dialog = page.getByRole("dialog", { name: "Create Information Request" });
  250 |     await expect(dialog).toBeVisible();
  251 |     // Ad-hoc request to the client's (auto-provisioned) portal contact
  252 |     await expect(dialog.getByTestId("add-ad-hoc-item-button")).toBeVisible({ timeout: 15_000 });
  253 |     await dialog.getByTestId("add-ad-hoc-item-button").click();
  254 |     await dialog
  255 |       .getByTestId("ad-hoc-item-name-0")
  256 |       .fill("Beneficiary details — full names, ID numbers and bank confirmations");
  257 |     // Pressed from the keyboard: on the suite's 1280×720 viewport the dialog is taller than the
  258 |     // screen and does not scroll, so its footer buttons sit off-screen (follow-up:
  259 |     // components/ui/dialog.tsx:56-70 has no max-height / overflow-y-auto)
  260 |     await dialog.getByTestId("create-request-send-now").press("Enter");
  261 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  262 |     const request = panel.locator("tbody tr");
  263 |     await expect(request).toHaveCount(1, { timeout: 15_000 });
  264 |     await expect(request.getByRole("link", { name: /^REQ-\d+$/ })).toBeVisible();
  265 |     await expect(request.getByRole("cell", { name: "Sent", exact: true })).toBeVisible();
  266 |     await expect(request).toContainText("0/1 accepted");
  267 |   });
  268 | 
  269 |   // ── 75.16-75.18: Apex 2nd matter (Commercial template) ────────────
  270 |   test("Alice: Create 2nd Commercial matter for Apex (annual returns)", async ({ page }) => {
  271 |     await loginAs(page, "alice");
  272 |     const id = await createTemplateMatter(page, {
  273 |       template: /^Commercial \(Corporate & Contract\)/,
  274 |       name: apexAnnualReturns(),
  275 |       client: apexClient(),
  276 |     });
  277 |     recordMatterId("apexAnnualReturns", id);
  278 |     await openGroupedTab(page, "work", "tasks");
  279 |     await expect(
  280 |       page
  281 |         .getByRole("tabpanel")
  282 |         .getByRole("button", { name: "Open task detail for Due diligence review" })
  283 |     ).toBeVisible({ timeout: 15_000 });
  284 |   });
  285 | });
  286 | 
```