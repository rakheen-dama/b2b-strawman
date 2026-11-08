# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-90-review.spec.ts >> Day 90 — Quarter Review >> Alice: Fee notes list shows the run's five fee notes
- Location: e2e/tests/legal-lifecycle/day-90-review.spec.ts:95:7

# Error details

```
Error: Run-state value "apexFeeNote" was not recorded
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
            - generic "Fee notes" [ref=e126]
        - generic [ref=e127]:
          - generic [ref=e129]: e2e-test-org
          - button "AO" [ref=e131]
          - button "Notifications" [ref=e133]:
            - img
            - generic [ref=e134]: 5 unread notifications
            - generic [ref=e135]: "5"
      - main [ref=e136]:
        - generic [ref=e139]:
          - generic [ref=e140]:
            - generic [ref=e141]:
              - 'heading "Fee notes Help: Fee note lifecycle" [level=1] [ref=e142]':
                - text: Fee notes
                - 'button "Help: Fee note lifecycle" [ref=e143] [cursor=pointer]':
                  - img [ref=e144]
              - generic [ref=e147]: "3"
            - button "New fee note" [ref=e148]:
              - img
              - text: New fee note
          - generic [ref=e149]:
            - link "Fee notes" [ref=e150] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices
            - link "Fee runs" [ref=e151] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices/billing-runs
          - generic [ref=e152]:
            - generic [ref=e153]:
              - paragraph [ref=e154]: Total outstanding
              - paragraph [ref=e155]: R 8 625,00
            - generic [ref=e156]:
              - paragraph [ref=e157]: Overdue
              - paragraph [ref=e158]: R 0,00
            - generic [ref=e159]:
              - paragraph [ref=e160]: Approved, not sent
              - paragraph [ref=e161]: R 0,00
            - generic [ref=e162]:
              - paragraph [ref=e163]: Paid This Month
              - paragraph [ref=e164]: R 0,00
          - generic [ref=e165]:
            - generic [ref=e166]: "Filter:"
            - link "All" [ref=e167] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices
            - link "Draft" [ref=e168] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=DRAFT
            - link "Approved" [ref=e169] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=APPROVED
            - link "Sent" [ref=e170] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=SENT
            - link "Partially Paid" [ref=e171] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=PARTIALLY_PAID
            - link "Paid" [ref=e172] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=PAID
            - link "Void" [ref=e173] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=VOID
          - table [ref=e175]:
            - rowgroup [ref=e176]:
              - row "Fee note Client Status Issue Date Due Date Total Currency" [ref=e177]:
                - columnheader "Fee note" [ref=e178]
                - columnheader "Client" [ref=e179]
                - columnheader "Status" [ref=e180]
                - columnheader "Issue Date" [ref=e181]
                - columnheader "Due Date" [ref=e182]
                - columnheader "Total" [ref=e183]
                - columnheader "Currency" [ref=e184]
            - rowgroup [ref=e185]:
              - row "Draft Sipho Ndlovu mw734icd0b8c Draft — — R 5 482,63 ZAR" [ref=e186]:
                - cell "Draft" [ref=e187]:
                  - link "Draft" [ref=e188] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/c5a6ec30-efa8-4d05-b65d-c5bbf5f9ba5f
                - cell "Sipho Ndlovu mw734icd0b8c" [ref=e189]
                - cell "Draft" [ref=e190]:
                  - generic [ref=e192]: Draft
                - cell "—" [ref=e193]
                - cell "—" [ref=e194]
                - cell "R 5 482,63" [ref=e195]
                - cell "ZAR" [ref=e196]
              - row "INV-0001 Acme Corp Sent 8 Nov 2026 8 Dec 2026 R 8 625,00 ZAR" [ref=e197]:
                - cell "INV-0001" [ref=e198]:
                  - link "INV-0001" [ref=e199] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/ea435288-431d-4419-9a77-8e558e3fdcc3
                - cell "Acme Corp" [ref=e200]
                - cell "Sent" [ref=e201]:
                  - generic [ref=e203]: Sent
                - cell "8 Nov 2026" [ref=e204]
                - cell "8 Dec 2026" [ref=e205]
                - cell "R 8 625,00" [ref=e206]
                - cell "ZAR" [ref=e207]
              - row "Draft Acme Corp Draft — — R 3 450,00 ZAR" [ref=e208]:
                - cell "Draft" [ref=e209]:
                  - link "Draft" [ref=e210] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/43fc4a95-05c6-468c-b40f-3266a8c7272f
                - cell "Acme Corp" [ref=e211]
                - cell "Draft" [ref=e212]:
                  - generic [ref=e214]: Draft
                - cell "—" [ref=e215]
                - cell "—" [ref=e216]
                - cell "R 3 450,00" [ref=e217]
                - cell "ZAR" [ref=e218]
  - generic [ref=e219]:
    - heading "Command Palette" [level=2] [ref=e220]
    - paragraph [ref=e221]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e222]
```

# Test source

```ts
  66  | function readRunState(): LifecycleRunState {
  67  |   if (!existsSync(RUN_FILE)) {
  68  |     throw new Error(
  69  |       `No legal-lifecycle run state at ${RUN_FILE} — run day-00-firm-setup.spec.ts first`
  70  |     );
  71  |   }
  72  |   return JSON.parse(readFileSync(RUN_FILE, "utf-8")) as LifecycleRunState;
  73  | }
  74  | 
  75  | function writeRunState(state: LifecycleRunState): void {
  76  |   writeFileSync(RUN_FILE, JSON.stringify(state, null, 2));
  77  | }
  78  | 
  79  | /**
  80  |  * Starts a new lifecycle run (called by the first Day 0 test) and returns its id — the single
  81  |  * source of the run id for every lifecycle spec. Millisecond time plus 16 random bits, so ids
  82  |  * never collide across runs on a reused stack.
  83  |  */
  84  | export function startLifecycleRun(): string {
  85  |   const runId = `${Date.now().toString(36)}${randomBytes(2).toString("hex")}`;
  86  |   writeRunState({ runId, clients: {}, matters: {}, values: {} });
  87  |   return runId;
  88  | }
  89  | 
  90  | /** The id of the lifecycle run started by Day 0 (throws if Day 0 has not run). */
  91  | export function currentLifecycleRun(): string {
  92  |   return readRunState().runId;
  93  | }
  94  | 
  95  | // ── Name builders (exact, run-scoped) ────────────────────────────────────────────────────────
  96  | /** Sipho Ndlovu — individual litigation client (Day 1). */
  97  | export const siphoClient = () => `Sipho Ndlovu ${currentLifecycleRun()}`;
  98  | /** Apex Holdings — company client (Day 2). */
  99  | export const apexClient = () => `Apex Holdings ${currentLifecycleRun()}`;
  100 | /** Moroka Family Trust — the deceased-estate client (Day 2). */
  101 | export const morokaClient = () => `Moroka Family Trust ${currentLifecycleRun()}`;
  102 | /** QuickCollect Services — collections client (Day 3). */
  103 | export const qcClient = () => `QuickCollect Services ${currentLifecycleRun()}`;
  104 | 
  105 | /** Sipho's litigation matter (Litigation (Personal Injury / General) template, Day 1). */
  106 | export const siphoMatter = () => `Ndlovu v RAF ${currentLifecycleRun()}`;
  107 | /** Apex's commercial matter (Commercial (Corporate & Contract) template, Day 2). */
  108 | export const apexMatter = () => `Apex Shareholder Agreement ${currentLifecycleRun()}`;
  109 | /** Moroka's estate matter (Deceased Estate Administration template, Day 2). */
  110 | export const morokaMatter = () => `Estate Late Peter Moroka ${currentLifecycleRun()}`;
  111 | /** QuickCollect's first collections matter (Collections (Debt Recovery) template, Day 3). */
  112 | export const qcMatter = () => `QC Debt Recovery #1 ${currentLifecycleRun()}`;
  113 | /** Sipho's second matter (Day 75 — its conflict check finds his Day 1 matter). */
  114 | export const siphoPropertyMatter = () => `Ndlovu Property Dispute ${currentLifecycleRun()}`;
  115 | /** Apex's second matter (Day 75). */
  116 | export const apexAnnualReturns = () => `Apex Annual Returns ${currentLifecycleRun()}`;
  117 | 
  118 | const CLIENT_NAMES: Record<LifecycleClient, () => string> = {
  119 |   sipho: siphoClient,
  120 |   apex: apexClient,
  121 |   moroka: morokaClient,
  122 |   qc: qcClient,
  123 | };
  124 | 
  125 | /** Exact run-scoped client name for a key. */
  126 | export function lifecycleClientName(key: LifecycleClient): string {
  127 |   return CLIENT_NAMES[key]();
  128 | }
  129 | 
  130 | // ── Record setters / getters ─────────────────────────────────────────────────────────────────
  131 | /** Records a client's UUID (from `/clients/{uuid}`) for the later days. */
  132 | export function recordClientId(key: LifecycleClient, id: string): void {
  133 |   const state = readRunState();
  134 |   writeRunState({ ...state, clients: { ...state.clients, [key]: id } });
  135 | }
  136 | 
  137 | /** Client UUID recorded when the client was created (throws if it was not). */
  138 | export function clientId(key: LifecycleClient): string {
  139 |   const id = readRunState().clients[key];
  140 |   if (!id) throw new Error(`Client "${key}" was not recorded — run day-01-03-onboarding first`);
  141 |   return id;
  142 | }
  143 | 
  144 | /** Records a matter's UUID (from `/matters/{uuid}`) for the later days. */
  145 | export function recordMatterId(key: LifecycleMatter, id: string): void {
  146 |   const state = readRunState();
  147 |   writeRunState({ ...state, matters: { ...state.matters, [key]: id } });
  148 | }
  149 | 
  150 | /** Matter UUID recorded when the matter was created (throws if it was not). */
  151 | export function matterId(key: LifecycleMatter): string {
  152 |   const id = readRunState().matters[key];
  153 |   if (!id) throw new Error(`Matter "${key}" was not recorded by an earlier lifecycle day`);
  154 |   return id;
  155 | }
  156 | 
  157 | /** Records a value a later day needs (e.g. a fee-note UUID for a raw-UUID dialog field). */
  158 | export function recordValue(key: LifecycleValueKey, value: string): void {
  159 |   const state = readRunState();
  160 |   writeRunState({ ...state, values: { ...state.values, [key]: value } });
  161 | }
  162 | 
  163 | /** A value recorded by an earlier lifecycle day (throws if it was not). */
  164 | export function recordedValue(key: LifecycleValueKey): string {
  165 |   const value = readRunState().values[key];
> 166 |   if (value === undefined) throw new Error(`Run-state value "${key}" was not recorded`);
      |                                  ^ Error: Run-state value "apexFeeNote" was not recorded
  167 |   return value;
  168 | }
  169 | 
  170 | // ── Small shared helpers ─────────────────────────────────────────────────────────────────────
  171 | const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
  172 | 
  173 | /** The UUID after `/<segment>/` in the current URL (e.g. `idFromUrl(page, "clients")`). */
  174 | export function idFromUrl(page: Page, segment: string): string {
  175 |   const match = new URL(page.url()).pathname.match(new RegExp(`/${segment}/(${UUID})`));
  176 |   if (!match) throw new Error(`No /${segment}/<uuid> in ${page.url()}`);
  177 |   return match[1];
  178 | }
  179 | 
  180 | /** Group separators `formatCurrency` (en-ZA) may render: space, NBSP, narrow NBSP. */
  181 | const GROUP_SEP = "[\\s\\u00a0\\u202f]";
  182 | 
  183 | /**
  184 |  * A `Rand` amount as rendered by `formatCurrency` (en-ZA: NBSP/narrow-NBSP group separator),
  185 |  * left-anchored so it never matches the tail of a larger number: "125,00" does not match
  186 |  * "8 125,00" (neither a digit nor a digit + group separator may precede it) and "2 500,00" does
  187 |  * not match "12 500,00". A preceding "R " is fine. Not right-anchored: the cents are always two
  188 |  * digits, and a row's text runs straight into the next cell ("R 200 000,008.50%").
  189 |  */
  190 | export function randPattern(amount: string): RegExp {
  191 |   // "250 000,00" → /(?<!\d)(?<!\d[\s\u00a0\u202f])250[\s\u00a0\u202f]?000,00/
  192 |   const [whole, cents = "00"] = amount.split(",");
  193 |   const groups = whole.split(/[\s\u00a0\u202f]/).map(escapeRegExp);
  194 |   return new RegExp(
  195 |     `(?<!\\d)(?<!\\d${GROUP_SEP})${groups.join(`${GROUP_SEP}?`)},${escapeRegExp(cents)}`
  196 |   );
  197 | }
  198 | 
  199 | /**
  200 |  * Opens a new matter from a template, linked to `client`, and returns its UUID. Leaves the browser
  201 |  * on the new matter's page. The client must not be a PROSPECT (CustomerLifecycleGuard).
  202 |  */
  203 | export async function createTemplateMatter(
  204 |   page: Page,
  205 |   { template, name, client }: { template: RegExp; name: string; client: string }
  206 | ): Promise<string> {
  207 |   await page.goto(`${BASE}/matters`);
  208 |   await page.getByRole("button", { name: "New from Template" }).click();
  209 |   const dialog = page.getByRole("dialog", { name: /New from Template/ });
  210 |   await expect(dialog).toBeVisible();
  211 |   await dialog.getByRole("option", { name: template }).click();
  212 |   await dialog.getByRole("button", { name: "Next" }).click();
  213 |   await dialog.getByLabel(/Matter name/).fill(name);
  214 |   await selectNativeOption(dialog.getByLabel(/^Client/), client);
  215 |   await dialog.getByRole("button", { name: "Create Matter" }).click();
  216 |   await page.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 15_000 });
  217 |   await expect(page.getByRole("heading", { level: 1, name })).toBeVisible({ timeout: 15_000 });
  218 |   await expect(page.locator("main")).toContainText(client);
  219 |   return idFromUrl(page, "matters");
  220 | }
  221 | 
  222 | /**
  223 |  * Logs time on one task of the open matter (Work → Tasks → row "Log Time"). Time is logged per
  224 |  * task; there is no matter-level Log Time. `rate` (e.g. "2 500,00") asserts the billing-rate
  225 |  * preview, i.e. that the Day 0 rate card applies to this member.
  226 |  */
  227 | export async function logTimeOnTask(
  228 |   page: Page,
  229 |   opts: {
  230 |     task: string;
  231 |     hours: number;
  232 |     minutes: number;
  233 |     description: string;
  234 |     date: string;
  235 |     rate?: string;
  236 |   }
  237 | ): Promise<void> {
  238 |   await openGroupedTab(page, "work", "tasks");
  239 |   const taskRow = page
  240 |     .getByRole("tabpanel")
  241 |     .getByRole("row")
  242 |     .filter({ has: page.getByRole("button", { name: `Open task detail for ${opts.task}` }) });
  243 |   await expect(taskRow, `task "${opts.task}" should be listed`).toHaveCount(1, {
  244 |     timeout: 15_000,
  245 |   });
  246 |   await taskRow.getByRole("button", { name: "Log Time" }).click();
  247 | 
  248 |   const dialog = page.getByRole("dialog", { name: "Log Time" });
  249 |   await expect(dialog).toBeVisible();
  250 |   // Hours / minutes have no accessible name (the "Duration" label has no htmlFor) — product
  251 |   // follow-up: label them (components/tasks/log-time-dialog.tsx:180-205); ids until then
  252 |   await dialog.locator("#time-hours").fill(String(opts.hours));
  253 |   await dialog.locator("#time-minutes").fill(String(opts.minutes));
  254 |   await dialog.getByLabel("Date", { exact: true }).fill(opts.date);
  255 |   await dialog.getByRole("textbox", { name: /^Description/ }).fill(opts.description);
  256 |   await expect(dialog.getByRole("checkbox", { name: "Billable" })).toBeChecked();
  257 |   if (opts.rate) {
  258 |     await expect(dialog.getByTestId("rate-preview")).toContainText(
  259 |       new RegExp(`Billing rate: R[\\s\\u00a0\\u202f]?${randPattern(opts.rate).source}/hr`),
  260 |       { timeout: 15_000 }
  261 |     );
  262 |   }
  263 |   await dialog.getByRole("button", { name: "Log Time" }).click();
  264 |   await expect(dialog).toBeHidden({ timeout: 15_000 });
  265 | }
  266 | 
```