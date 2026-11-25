/**
 * Helpers for the Phase 93 take-on capstone (`tests/legal-lifecycle/take-on.spec.ts`, slice 660A,
 * tasks 660.1–660.3).
 *
 * - `takeOnDates()` — the cutover date C (the last day of the month TWO months back, by the SA
 *   business date, D-224 / A-11) and the first post-cutover month M (the month after C, wholly in
 *   the past, FU-6a), computed ONCE per run. Refuses to start within 30 minutes of SA midnight at a
 *   month end, the `exitMonth()` guard shifted one month further back.
 * - `firstMonthStatementCsv()` — the first statement (660.2), Standard Bank CSV: the opening
 *   balance at C (= the take-on bank balance), the two cutover items, one deposit, one payment,
 *   the Fund interest and the service fee.
 * - `renderFixture()` — reads a committed fixture from `qa/fixtures/take-on/`, replaces its date
 *   tokens (`{{C}}`, `{{C-472}}`, `{{C+10}}`) with ISO dates and writes the rendered copy to the
 *   run's evidence dir; returns the `setInputFiles` payload.
 * - `mailpitTotal()` — the e2e Mailpit's message count (:8026, not the dev stack's :8025).
 * - the wizard steps the spec repeats: Radix option pick, upload-first, add a trust source,
 *   map-and-save, dry run, the cards, the position form.
 *
 * Shared helpers (money matchers, firm sessions, evidence screenshots) are IMPORTED from
 * `./trust-balanced-month`, not copied. Its `evidenceShot` writes under that module's evidence
 * dir, so `evidenceDir()` here points `EXIT_MONTH_EVIDENCE_DIR` at this run's take-on dir before
 * that module fixes its own: one run, one directory.
 */
import { expect, type Locator, type Page } from "@playwright/test";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { apiOk } from "./api";
import { exactText } from "./trust-balanced-month";

/** The committed fixture set (660.1). */
export const FIXTURE_DIR = path.resolve(__dirname, "../../../qa/fixtures/take-on");

/** The e2e stack's Mailpit (compose/docker-compose.e2e.yml maps 8025 → 8026). */
const MAILPIT_URL = process.env.MAILPIT_URL || "http://localhost:8026";

// ── Evidence ─────────────────────────────────────────────────────────────────────────────────

let runEvidenceDir: string | undefined;

/**
 * This run's evidence directory: `TAKE_ON_EVIDENCE_DIR`, else `<os tmpdir>/take-on-<timestamp>/`
 * (outside the repo; `qa_cycle/evidence/phase93-exit/` is filled by copying from the chosen run).
 * Fixed on first use. Also becomes `EXIT_MONTH_EVIDENCE_DIR` (unless set), so the imported
 * `evidenceShot` writes its screenshots here too.
 */
export function evidenceDir(): string {
  if (!runEvidenceDir) {
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    runEvidenceDir = process.env.TAKE_ON_EVIDENCE_DIR
      ? path.resolve(process.env.TAKE_ON_EVIDENCE_DIR)
      : path.join(os.tmpdir(), `take-on-${stamp}`);
    fs.mkdirSync(path.join(runEvidenceDir, "csv"), { recursive: true });
    fs.mkdirSync(path.join(runEvidenceDir, "downloads"), { recursive: true });
    process.env.EXIT_MONTH_EVIDENCE_DIR ??= runEvidenceDir;
  }
  return runEvidenceDir;
}

/** Appends one line to the run's `evidence-log.txt` (Mailpit counts, ids, read figures). */
export function logEvidence(label: string, value: unknown): void {
  const line = `${new Date().toISOString()} ${label} ${
    typeof value === "string" ? value : JSON.stringify(value)
  }\n`;
  fs.appendFileSync(path.join(evidenceDir(), "evidence-log.txt"), line);
}

// ── Dates (660.2, D-224) ─────────────────────────────────────────────────────────────────────

const MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

const pad = (n: number) => String(n).padStart(2, "0");
const iso = (d: Date) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
const parseIso = (s: string) => {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d));
};

/** "30 September 2026" — the backend's sentence date (`IntegrityMessages.date`, d MMMM yyyy). */
export function longDate(isoDate: string): string {
  const d = parseIso(isoDate);
  return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
}

/** `isoDate` plus `days` (negative = before). */
export function addDays(isoDate: string, days: number): string {
  const d = parseIso(isoDate);
  d.setUTCDate(d.getUTCDate() + days);
  return iso(d);
}

/** Whole days from `from` to `to` (both ISO). */
export function daysBetween(from: string, to: string): number {
  return Math.round((parseIso(to).getTime() - parseIso(from).getTime()) / 86_400_000);
}

export interface TakeOnDates {
  /** Today by the SA business date (ISO). */
  today: string;
  /** The cutover date C: the last day of the month two months back (ISO). */
  c: string;
  /** C as the backend writes it: "30 September 2026". */
  cLong: string;
  /** C + n days (ISO): the fixtures' `{{C+n}}` / `{{C-n}}`. */
  cPlus(n: number): string;
  /** The first post-cutover month M (the month after C). */
  month: {
    year: number;
    /** 1–12 */
    month: number;
    lastDay: number;
    /** Day n of M (ISO); n > lastDay is clamped to the last day. */
    d(n: number): string;
    /** "October 2026" */
    name: string;
  };
}

/**
 * C and M, computed once per run (660.2). C = the last day of the month two months back by the SA
 * business date, so M (the month after C) is the previous full calendar month and no statement
 * line is in the future (FU-6a). Refuses to start within 30 minutes of SA midnight at a month end:
 * a run that straddles it would move C.
 */
export function takeOnDates(now: Date = new Date()): TakeOnDates {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Africa/Johannesburg",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const get = (type: string) => Number(parts.find((p) => p.type === type)!.value);
  const [y, m, day, hh, mm] = [get("year"), get("month"), get("day"), get("hour"), get("minute")];
  const daysInCurrent = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const minuteOfDay = hh * 60 + mm;
  if ((day === daysInCurrent && minuteOfDay >= 23 * 60 + 30) || (day === 1 && minuteOfDay < 30)) {
    throw new Error(
      "take-on: refusing to start within 30 minutes of SA midnight at a month end (660.2)"
    );
  }
  const today = `${y}-${pad(m)}-${pad(day)}`;
  // Date.UTC(y, m - 2, 0) = the last day of month (m - 2), 1-based: two months back
  const c = iso(new Date(Date.UTC(y, m - 2, 0)));
  const first = parseIso(addDays(c, 1));
  const year = first.getUTCFullYear();
  const month = first.getUTCMonth() + 1;
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return {
    today,
    c,
    cLong: longDate(c),
    cPlus: (n) => addDays(c, n),
    month: {
      year,
      month,
      lastDay,
      d: (n) => `${year}-${pad(month)}-${pad(Math.min(n, lastDay))}`,
      name: `${MONTHS[month - 1]} ${year}`,
    },
  };
}

// ── Fixtures (660.1) ─────────────────────────────────────────────────────────────────────────

export interface UploadFile {
  name: string;
  mimeType: string;
  buffer: Buffer;
}

const MIME: Record<string, string> = {
  ".csv": "text/csv",
  ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
};

/**
 * A committed fixture as `setInputFiles` takes it. Text fixtures have their date tokens replaced
 * (`{{C}}`, `{{C±n}}` → ISO); the rendered copy is written to the run's `csv/`. Binary fixtures
 * (the XLSX) are passed through byte for byte.
 */
export function renderFixture(name: string, d: TakeOnDates): UploadFile {
  const ext = path.extname(name);
  const raw = fs.readFileSync(path.join(FIXTURE_DIR, name));
  let buffer = raw;
  if (ext === ".csv") {
    const text = raw
      .toString("utf8")
      .replace(/\{\{C([+-]\d+)?\}\}/g, (_, offset?: string) =>
        d.cPlus(offset ? Number(offset) : 0)
      );
    if (/\{\{/.test(text)) throw new Error(`renderFixture: unreplaced token in ${name}`);
    buffer = Buffer.from(text, "utf8");
  }
  fs.writeFileSync(path.join(evidenceDir(), "csv", name), buffer);
  return { name, mimeType: MIME[ext] ?? "application/octet-stream", buffer };
}

// ── The first post-cutover statement (660.2, B-1 option B — D-259) ───────────────────────────

/** The statement file name (as uploaded and as kept in the evidence). */
export const FIRST_MONTH_CSV = "first-month-statement.csv";

/**
 * G's first statement, M day 1 to the last day (Standard Bank CSV; `StandardBankCsvParser`
 * needs "STANDARD BANK" in the first line). The zero "Opening Balance" row is not a line: it gives
 * the opening, 418 500,00 = the take-on bank balance B (no OPENING_DIFFERS_FROM_TAKE_ON). Lines:
 * the two cutover items (auto-matched to the take-on's deposit in transit / unpresented cheque),
 * one deposit (recorded as an unidentified deposit), one payment (the Fund sweep), the Fund
 * interest and the service fee. Closing 403 477,40.
 */
export function firstMonthStatementCsv(d: TakeOnDates, accountNumber: string): UploadFile {
  const m = d.month;
  const rows = [
    `Standard Bank Trust Account - Acc ${accountNumber}`,
    "Date,Description,Reference,Amount,Balance",
    `${m.d(1)},Opening Balance,,0.00,418500.00`,
    `${m.d(2)},EFT B-0001,EFT B-0001,5000.00,423500.00`,
    `${m.d(5)},PAY-778,PAY-778,-20000.00,403500.00`,
    `${m.d(10)},CASH DEPOSIT BRANCH 0045,,1000.00,404500.00`,
    `${m.d(20)},LPFF INTEREST TRANSFER,,-1250.00,403250.00`,
    `${m.d(31)},INTEREST,,312.40,403562.40`,
    `${m.d(31)},SERVICE FEE,,-85.00,403477.40`,
  ];
  const text = rows.join("\n") + "\n";
  fs.writeFileSync(path.join(evidenceDir(), "csv", FIRST_MONTH_CSV), text);
  return { name: FIRST_MONTH_CSV, mimeType: "text/csv", buffer: Buffer.from(text, "utf8") };
}

// ── Mailpit ──────────────────────────────────────────────────────────────────────────────────

export interface MailpitCount {
  at: string;
  total: number;
  latest: { id: string; subject: string; created: string } | null;
}

/** The e2e Mailpit's total and its latest message (R-3: every take-on step is silent). */
export async function mailpitTotal(): Promise<MailpitCount> {
  const res = await fetch(`${MAILPIT_URL}/api/v1/messages?limit=1`);
  expect(res.ok, `Mailpit ${MAILPIT_URL} → ${res.status}`).toBe(true);
  const j = (await res.json()) as {
    total: number;
    messages?: { ID: string; Subject: string; Created: string }[];
  };
  const m = j.messages?.[0];
  return {
    at: new Date().toISOString(),
    total: j.total,
    latest: m ? { id: m.ID, subject: m.Subject, created: m.Created } : null,
  };
}

// ── Wizard steps ─────────────────────────────────────────────────────────────────────────────

/** Opens a Radix select and picks the option whose whole text is `option`. */
export async function pickOption(page: Page, trigger: Locator, option: string): Promise<void> {
  await trigger.click();
  const item = page.getByRole("option", { name: exactText(option) });
  await expect(item).toBeVisible({ timeout: 15_000 });
  await item.click();
  await expect(trigger).toContainText(option);
}

/** The take-on tab (Settings → Matters → Take-on), or one batch's wizard on it. */
export async function openTakeOn(page: Page, batchId?: string): Promise<void> {
  const query = batchId ? `?tab=take-on&batch=${batchId}` : "?tab=take-on";
  await page.goto(`/org/e2e-test-org/settings/matters${query}`);
  await expect(page.getByTestId(batchId ? "take-on-wizard" : "take-on-tab")).toBeVisible({
    timeout: 60_000,
  });
}

export interface UploadFirst {
  kebab: "clients" | "matters" | "trust-position" | "debtor-balances";
  batchName: string;
  file: UploadFile;
  trust?: { account: string; section: string };
  cutover?: string;
}

/**
 * Take-on tab → Start {dataset} → the upload form → Upload. Returns the batch id from the URL.
 * An upload that comes back with warnings must be continued explicitly (none is expected).
 */
export async function uploadFirst(page: Page, o: UploadFirst): Promise<string> {
  await openTakeOn(page);
  await page.getByTestId(`take-on-start-${o.kebab}`).click();
  await expect(page.getByTestId("take-on-new-upload")).toBeVisible();
  if (o.trust) {
    await pickOption(page, page.getByTestId("take-on-trust-account"), o.trust.account);
    await pickOption(page, page.getByTestId("take-on-section"), o.trust.section);
  }
  if (o.cutover) await page.getByTestId("take-on-cutover-date").fill(o.cutover);
  await page.getByTestId("take-on-batch-name").fill(o.batchName);
  await page.getByTestId("take-on-file-input").setInputFiles(o.file);
  await page.getByTestId("take-on-upload-submit").click();
  await expect(page).toHaveURL(/[?&]batch=[0-9a-f-]{36}/, { timeout: 60_000 });
  await expect(page.getByTestId("take-on-upload-server-error")).toHaveCount(0);
  await expect(page.getByTestId("take-on-wizard")).toBeVisible({ timeout: 60_000 });
  const batchId = new URL(page.url()).searchParams.get("batch");
  if (!batchId) throw new Error(`no batch id in ${page.url()}`);
  return batchId;
}

/** Trust Upload step: add the file for `section` and wait until the section reads uploaded. */
export async function addTrustSource(
  page: Page,
  kebab: string,
  sectionLabel: string,
  file: UploadFile
): Promise<void> {
  await pickOption(page, page.getByTestId("take-on-add-source-section"), sectionLabel);
  await page.getByTestId("take-on-add-source-file").setInputFiles(file);
  await page.getByTestId("take-on-add-source").click();
  await expect(
    page.locator(`[data-testid="take-on-trust-section-${kebab}"][data-uploaded="true"]`)
  ).toBeVisible({ timeout: 60_000 });
}

/**
 * The Mapping step for the source on screen: every `expected` field must already be suggested
 * from its header (the fixtures use the catalogue labels), the date format is set to yyyy-mm-dd,
 * then Save.
 */
export async function mapAndSave(page: Page, expected: Record<string, string>): Promise<void> {
  const mapping = page.getByTestId("take-on-mapping");
  await expect(mapping).toBeVisible({ timeout: 60_000 });
  for (const [key, header] of Object.entries(expected)) {
    await expect(page.getByTestId(`take-on-map-${key}`), `suggested mapping of ${key}`).toHaveText(
      exactText(header),
      { timeout: 15_000 }
    );
  }
  const dateFormat = page.getByTestId("take-on-date-format");
  if (!((await dateFormat.textContent()) ?? "").includes("2025-12-31")) {
    await pickOption(page, dateFormat, "2025-12-31 (yyyy-mm-dd)");
  }
  await page.getByTestId("take-on-save-mapping").click();
}

/** Validation step: run the dry run (the batch is MAPPED) and wait for the cards. */
export async function runDryRun(page: Page): Promise<void> {
  await expect(page.getByTestId("take-on-validation")).toBeVisible({ timeout: 60_000 });
  const validate = page.getByTestId("take-on-validate");
  await expect(validate).toHaveText("Run dry run", { timeout: 30_000 });
  await validate.click();
  await expect(page.getByTestId("take-on-cards")).toBeVisible({ timeout: 90_000 });
  await expect(validate).toHaveText("Run the dry run again", { timeout: 90_000 });
}

export interface Cards {
  total: number;
  valid: number;
  error: number;
  duplicate: number;
  blocks: number;
}

/** The five Validation cards (Rows · Valid · Errors · Duplicates · Blocks), exactly. */
export async function expectCards(page: Page, c: Cards): Promise<void> {
  for (const [id, n] of Object.entries(c)) {
    await expect(
      page.getByTestId(`take-on-card-${id}`).locator('[data-slot="stat-card-value"]'),
      `card ${id}`
    ).toHaveText(String(n), { timeout: 30_000 });
  }
}

/** One validation row (file row number). */
export function takeOnRow(page: Page, n: number): Locator {
  return page.getByTestId(`take-on-row-${n}`);
}

/** The messages (last cell's list items) of validation row `n`. */
export function rowMessages(page: Page, n: number): Locator {
  return takeOnRow(page, n).locator("td").last().locator("li");
}

/** Status filter → `label`; asserts exactly `rows` are listed. */
export async function filterRows(page: Page, label: string, rows: number[]): Promise<void> {
  await pickOption(page, page.getByTestId("take-on-filter-status"), label);
  const listed = page.locator('[data-testid="take-on-rows"] [data-testid^="take-on-row-"]');
  await expect
    .poll(
      async () =>
        (
          await listed.evaluateAll((els) =>
            els
              .map((e) => e.getAttribute("data-testid") ?? "")
              .filter((t) => /^take-on-row-\d+$/.test(t))
          )
        ).map((t) => Number(t.replace("take-on-row-", ""))),
      { message: `rows under the "${label}" filter`, timeout: 30_000 }
    )
    .toEqual(rows);
}

/** The trust position form's figures (ZAR, decimal point). */
export interface Position {
  bank: string;
  od: string;
  op: string;
  cashbook: string;
  fund: string;
  invested: string;
}

/** Fills the trust position form (C is a month end: the mid-month tick stays off). */
export async function fillPosition(page: Page, p: Position): Promise<void> {
  const form = page.getByTestId("take-on-position-form");
  await expect(form).toBeVisible({ timeout: 60_000 });
  const set = async (id: string, v: string) => {
    const el = page.getByTestId(id);
    await el.fill("");
    await el.fill(v);
  };
  await set("take-on-position-bank-balance", p.bank);
  await set("take-on-position-outstanding-deposits", p.od);
  await set("take-on-position-outstanding-payments", p.op);
  await set("take-on-position-cashbook", p.cashbook);
  await set("take-on-position-fund-ledger", p.fund);
  await set("take-on-position-invested-total", p.invested);
  await expect(page.getByTestId("take-on-position-mid-month")).toHaveAttribute(
    "data-state",
    "unchecked"
  );
}

/** A take-on batch as `GET /api/take-on/batches/{id}` answers it (read-only; the fields asserted). */
export interface BatchView {
  id: string;
  status: string;
  validatedAt: string | null;
  allowedActions: string[];
  counts: Record<string, number>;
  reportId?: string | null;
}

/** Read-only GET of one batch (as Bob, who prepares every take-on). */
export async function getBatch(batchId: string): Promise<BatchView> {
  return apiOk<BatchView>(`/api/take-on/batches/${batchId}`, {}, "bob");
}

/**
 * "Save position" — saves the figures and runs the dry run (D-183: the save moves the batch to
 * MAPPED, the form then validates). Done when the batch is VALIDATED with a new `validatedAt` and
 * the form is idle again.
 */
export async function savePosition(page: Page, batchId: string): Promise<void> {
  const before = (await getBatch(batchId)).validatedAt;
  const save = page.getByTestId("take-on-position-save");
  await save.click();
  await expect
    .poll(
      async () => {
        const b = await getBatch(batchId);
        return b.status === "VALIDATED" && b.validatedAt !== before;
      },
      { message: "the position save re-ran the dry run", timeout: 120_000 }
    )
    .toBe(true);
  await expect(save).toHaveText("Save position", { timeout: 60_000 });
  await expect(save).toBeEnabled({ timeout: 60_000 });
  await expect(page.getByTestId("take-on-cards")).toBeVisible({ timeout: 60_000 });
}

/** The live equation indicator row `id` (`eq1|items|eq2|investments|deficit`). */
export async function expectEquation(
  page: Page,
  id: "eq1" | "items" | "eq2" | "investments" | "deficit",
  holds: boolean,
  difference: string
): Promise<void> {
  await expect(page.getByTestId(`take-on-equation-${id}`)).toHaveAttribute(
    "data-holds",
    String(holds)
  );
  await expect(page.getByTestId(`take-on-equation-${id}-difference`)).toContainText(
    new RegExp(`Difference R[\\s\\u00a0\\u202f]?${difference.replace(/ /g, "[\\s\\u00a0\\u202f]")}`)
  );
}
