# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-45-recon-prescription.spec.ts >> Day 45 — Reconciliation & Prescription >> Alice: Record payment on Apex fee note (R40,250) → PAID
- Location: e2e/tests/legal-lifecycle/day-45-recon-prescription.spec.ts:160:7

# Error details

```
Error: Run-state value "apexFeeNote" was not recorded
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