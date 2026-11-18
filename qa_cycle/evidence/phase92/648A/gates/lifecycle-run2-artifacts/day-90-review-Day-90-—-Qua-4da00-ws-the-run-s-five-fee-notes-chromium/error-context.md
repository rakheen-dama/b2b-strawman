# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-90-review.spec.ts >> Day 90 — Quarter Review >> Alice: Fee notes list shows the run's five fee notes
- Location: e2e/tests/legal-lifecycle/day-90-review.spec.ts:94:7

# Error details

```
Error: Run-state value "siphoFeeNote2" was not recorded
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
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
            - generic "Fee notes" [ref=e158]
        - generic [ref=e159]:
          - button "Search, Command K" [ref=e160]:
            - img [ref=e161]
            - generic [ref=e164]: Search
            - generic: ⌘K
          - button "Notifications" [ref=e166]:
            - img
            - generic [ref=e167]: 12 unread notifications
            - generic [ref=e168]: "12"
          - generic [ref=e170]: e2e-test-org
          - button "AO" [ref=e172]
      - main [ref=e173]:
        - generic [ref=e176]:
          - generic [ref=e177]:
            - generic [ref=e178]:
              - 'heading "Fee notes Help: Fee note lifecycle" [level=1] [ref=e179]':
                - text: Fee notes
                - 'button "Help: Fee note lifecycle" [ref=e180] [cursor=pointer]':
                  - img [ref=e181]
              - generic [ref=e184]: "6"
            - button "New fee note" [ref=e185]:
              - img
              - text: New fee note
          - generic [ref=e186]:
            - link "Fee notes" [ref=e187] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices
            - link "Fee runs" [ref=e188] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices/billing-runs
          - generic [ref=e189]:
            - generic [ref=e190]:
              - paragraph [ref=e191]: Total outstanding
              - paragraph [ref=e192]: R 14 232,63
            - generic [ref=e193]:
              - paragraph [ref=e194]: Overdue
              - paragraph [ref=e195]: R 0,00
            - generic [ref=e196]:
              - paragraph [ref=e197]: Approved, not sent
              - paragraph [ref=e198]: R 0,00
            - generic [ref=e199]:
              - paragraph [ref=e200]: Paid This Month
              - paragraph [ref=e201]: R 40 250,00
          - generic [ref=e202]:
            - generic [ref=e203]: "Filter:"
            - link "All" [ref=e204] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices
            - link "Draft" [ref=e205] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=DRAFT
            - link "Approved" [ref=e206] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=APPROVED
            - link "Sent" [ref=e207] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=SENT
            - link "Partially Paid" [ref=e208] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=PARTIALLY_PAID
            - link "Paid" [ref=e209] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=PAID
            - link "Void" [ref=e210] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=VOID
          - table [ref=e212]:
            - rowgroup [ref=e213]:
              - row "Fee note Client Status Issue Date Due Date Total Currency" [ref=e214]:
                - columnheader "Fee note" [ref=e215]
                - columnheader "Client" [ref=e216]
                - columnheader "Status" [ref=e217]
                - columnheader "Issue Date" [ref=e218]
                - columnheader "Due Date" [ref=e219]
                - columnheader "Total" [ref=e220]
                - columnheader "Currency" [ref=e221]
            - rowgroup [ref=e222]:
              - row "Draft QuickCollect Services mwldl2wf9a27 Draft — — R 1 610,00 ZAR" [ref=e223]:
                - cell "Draft" [ref=e224]:
                  - link "Draft" [ref=e225] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/82a367b1-134b-41b9-a22b-09cac752c7b9
                - cell "QuickCollect Services mwldl2wf9a27" [ref=e226]
                - cell "Draft" [ref=e227]:
                  - generic [ref=e229]: Draft
                - cell "—" [ref=e230]
                - cell "—" [ref=e231]
                - cell "R 1 610,00" [ref=e232]
                - cell "ZAR" [ref=e233]
              - row "INV-0002 Moroka Family Trust mwldl2wf9a27 Partially Paid 18 Nov 2026 18 Dec 2026 R 8 625,00 Balance R 125,00 ZAR" [ref=e234]:
                - cell "INV-0002" [ref=e235]:
                  - link "INV-0002" [ref=e236] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/120d88d2-b1d8-4f29-bec3-c94fba33b718
                - cell "Moroka Family Trust mwldl2wf9a27" [ref=e237]
                - cell "Partially Paid" [ref=e238]:
                  - generic [ref=e240]: Partially Paid
                - cell "18 Nov 2026" [ref=e241]
                - cell "18 Dec 2026" [ref=e242]
                - cell "R 8 625,00 Balance R 125,00" [ref=e243]:
                  - text: R 8 625,00
                  - generic [ref=e244]: Balance R 125,00
                - cell "ZAR" [ref=e245]
              - row "INV-0004 Apex Holdings mwldl2wf9a27 Paid 18 Nov 2026 18 Dec 2026 R 40 250,00 ZAR" [ref=e246]:
                - cell "INV-0004" [ref=e247]:
                  - link "INV-0004" [ref=e248] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/d020a3e5-b0d7-4b16-b337-1f9d39a787a8
                - cell "Apex Holdings mwldl2wf9a27" [ref=e249]
                - cell "Paid" [ref=e250]:
                  - generic [ref=e252]: Paid
                - cell "18 Nov 2026" [ref=e253]
                - cell "18 Dec 2026" [ref=e254]
                - cell "R 40 250,00" [ref=e255]
                - cell "ZAR" [ref=e256]
              - row "INV-0003 Sipho Ndlovu mwldl2wf9a27 Sent 18 Nov 2026 18 Dec 2026 R 5 482,63 ZAR" [ref=e257]:
                - cell "INV-0003" [ref=e258]:
                  - link "INV-0003" [ref=e259] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/95e08846-d076-4e2e-8371-b5b33a434847
                - cell "Sipho Ndlovu mwldl2wf9a27" [ref=e260]
                - cell "Sent" [ref=e261]:
                  - generic [ref=e263]: Sent
                - cell "18 Nov 2026" [ref=e264]
                - cell "18 Dec 2026" [ref=e265]
                - cell "R 5 482,63" [ref=e266]
                - cell "ZAR" [ref=e267]
              - row "INV-0001 Acme Corp Sent 18 Nov 2026 18 Dec 2026 R 8 625,00 ZAR" [ref=e268]:
                - cell "INV-0001" [ref=e269]:
                  - link "INV-0001" [ref=e270] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/b96a2075-6218-4862-9a5d-f35e872b1a60
                - cell "Acme Corp" [ref=e271]
                - cell "Sent" [ref=e272]:
                  - generic [ref=e274]: Sent
                - cell "18 Nov 2026" [ref=e275]
                - cell "18 Dec 2026" [ref=e276]
                - cell "R 8 625,00" [ref=e277]
                - cell "ZAR" [ref=e278]
              - row "Draft Acme Corp Draft — — R 3 450,00 ZAR" [ref=e279]:
                - cell "Draft" [ref=e280]:
                  - link "Draft" [ref=e281] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/95fa7572-17db-4fe2-8994-959bc901ec2c
                - cell "Acme Corp" [ref=e282]
                - cell "Draft" [ref=e283]:
                  - generic [ref=e285]: Draft
                - cell "—" [ref=e286]
                - cell "—" [ref=e287]
                - cell "R 3 450,00" [ref=e288]
                - cell "ZAR" [ref=e289]
  - generic [ref=e290]:
    - heading "Command Palette" [level=2] [ref=e291]
    - paragraph [ref=e292]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e293]
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
      |                                  ^ Error: Run-state value "siphoFeeNote2" was not recorded
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