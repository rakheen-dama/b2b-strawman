/**
 * Helpers for the Phase 92 exit month (`tests/legal-lifecycle/trust-balanced-month.spec.ts`,
 * architecture §16.9.6, slice 652E).
 *
 * - `exitMonth()` — the previous calendar month by the South African business date, computed ONCE
 *   per run (OQ #12, default accepted 2026-11-18).
 * - the three Standard Bank CSV builders — literal first lines (`StandardBankCsvParser.canParse`
 *   needs "STANDARD BANK"), each with its `Opening Balance` row (not imported since 652G), and the
 *   Kazi-generated investment references (`INV-…` / `INV-WD-…`) copied in after the postings.
 * - money matchers: one normaliser for every ZAR rendering the run meets — the firm UI and portal
 *   (`formatCurrency`, en-ZA: "R 1 250,00" with NBSP / narrow-NBSP grouping, "-R 172,50"), the
 *   StatCard minus ("−R 172,50") and the backend sentences (Java en-ZA: "R1 250,00").
 * - firm sessions (one browser context per user, so a wizard can stay open across tests), the
 *   portal login (copied from `portal/e2e/helpers/auth.ts`, which a frontend spec cannot import),
 *   report-runner readers and the step-21 database check.
 */
import {
  expect,
  type Browser,
  type BrowserContext,
  type Locator,
  type Page,
  type TestInfo,
} from "@playwright/test";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { getApiToken } from "../fixtures/auth";
import { escapeRegExp } from "./legal-depth-ii";

/** Evidence for the exit month (652.31). */
export const EVIDENCE_DIR = path.resolve(__dirname, "../../../qa_cycle/evidence/phase92-exit");

// ── The exit month (OQ #12) ──────────────────────────────────────────────────────────────────

export interface ExitMonth {
  year: number;
  /** 1–12 */
  month: number;
  lastDay: number;
  /**
   * The ISO date of §16.9.6's day `n`. "31" reads as the last day, "30" as the day before and
   * "29" as two before (only matters in a short month).
   */
  d(n: number): string;
  /** "October 2026" */
  monthName: string;
  /** The backend's sentence date for day `n` ("31 October 2026", `IntegrityMessages.date`). */
  words(n: number): string;
}

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

/**
 * The exit month: the previous calendar month by the SA business date. Refuses to start within
 * 30 minutes of SA midnight at a month boundary (a run that straddles it would change the month).
 */
export function exitMonth(now: Date = new Date()): ExitMonth {
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
      "trust-balanced-month: refusing to start within 30 minutes of SA midnight at a month end (OQ #12)"
    );
  }
  const year = m === 1 ? y - 1 : y;
  const month = m === 1 ? 12 : m - 1;
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const dayOf = (n: number) => (n >= 29 ? lastDay - (31 - n) : n);
  const d = (n: number) =>
    `${year}-${String(month).padStart(2, "0")}-${String(dayOf(n)).padStart(2, "0")}`;
  const monthName = `${MONTHS[month - 1]} ${year}`;
  const words = (n: number) => `${dayOf(n)} ${monthName}`;
  return { year, month, lastDay, d, monthName, words };
}

// ── The three statements (§16.9.6, literal) ──────────────────────────────────────────────────

/** References Kazi generates for the s86(4) investments: `INV-` / `INV-WD-` + 8 hex, upper case. */
export interface GeneratedRefs {
  invA: string;
  invB: string;
  invWdA: string;
}

/** The statement file names (the Section 35 pack names a Fund sweep by its file and line). */
export const CSV_NAMES = {
  g: "month-end-trust-account.csv",
  iA: "investment-ndlovu.csv",
  iB: "investment-khumalo.csv",
} as const;

const row = (...cols: string[]) => cols.join(",");
const lines = (rows: string[]) => rows.join("\n") + "\n";

/** G — `month-end-trust-account.csv`: twenty data rows (the Opening Balance row + nineteen lines). */
export function monthEndTrustAccountCsv(m: ExitMonth, r: GeneratedRefs): string {
  return lines([
    "Standard Bank Trust Account - Acc 0123450001",
    "Date,Description,Reference,Amount,Balance",
    row(m.d(1), "Opening Balance", "", "0.00", "0.00"),
    row(m.d(1), "DEPOSIT NDLOVU FAMILY TRUST", "DEP-A-001", "100000.00", "100000.00"),
    row(m.d(2), "DEPOSIT KHUMALO HOLDINGS", "DEP-B-001", "40000.00", "140000.00"),
    row(m.d(5), "EFT PAYMENT SHERIFF PRETORIA", "PAY-A-001", "-12500.00", "127500.00"),
    row(m.d(6), "TRANSFER TO BUSINESS ACC", "FT-A-001", "-8500.00", "119000.00"),
    row(m.d(7), "EFT REFUND KHUMALO HOLDINGS", "REF-B-001", "-5000.00", "114000.00"),
    row(m.d(9), "DEPOSIT KHUMALO HOLDINGS", "DEP-B-002", "3000.00", "117000.00"),
    row(m.d(12), "UNPAID DEPOSIT RETURNED", "REV-DEP-B-002", "-3000.00", "114000.00"),
    row(m.d(13), "EFT PAYMENT COUNSEL", "PAY-A-002", "-2000.00", "112000.00"),
    row(m.d(15), "EFT RETURNED COUNSEL", "REV-PAY-A-002", "2000.00", "114000.00"),
    row(m.d(15), "TRANSFER TO INVESTMENT 62000000001", r.invA, "-50000.00", "64000.00"),
    row(m.d(16), "TRANSFER TO INVESTMENT 62000000002", r.invB, "-20000.00", "44000.00"),
    row(m.d(20), "CASH DEPOSIT BRANCH 0045", "", "7500.00", "51500.00"),
    row(m.d(22), "ACB CREDIT UNKNOWN", "", "1250.00", "52750.00"),
    row(m.d(30), "TRANSFER FROM INVESTMENT 62000000001", r.invWdA, "50380.00", "103130.00"),
    row(m.d(30), "MONTHLY SERVICE FEE", "", "-115.00", "103015.00"),
    row(m.d(30), "CASH DEPOSIT FEE", "", "-57.50", "102957.50"),
    row(m.d(31), "CREDIT INTEREST", "", "1234.56", "104192.06"),
    row(m.d(31), "LPFF INTEREST TRANSFER", "", "-1119.56", "103072.50"),
    row(m.d(31), "TRANSFER FROM BUSINESS ACC", "", "57.50", "103130.00"),
  ]);
}

/** I-A — `investment-ndlovu.csv`: four movement lines, the last on the 30th. */
export function investmentNdlovuCsv(m: ExitMonth, r: Pick<GeneratedRefs, "invA" | "invWdA">) {
  return lines([
    "Standard Bank Trust Investment Account - Acc 62000000001",
    "Date,Description,Reference,Amount,Balance",
    row(m.d(15), "Opening Balance", "", "0.00", "0.00"),
    row(m.d(15), "TRANSFER FROM TRUST", r.invA, "50000.00", "50000.00"),
    row(m.d(29), "INTEREST CAPITALISED", "", "400.00", "50400.00"),
    row(m.d(29), "LPFF 5PCT S86(4)", "", "-20.00", "50380.00"),
    row(m.d(30), "TRANSFER TO TRUST", r.invWdA, "-50380.00", "0.00"),
  ]);
}

/** I-B — `investment-khumalo.csv`: two movement lines. */
export function investmentKhumaloCsv(m: ExitMonth, r: Pick<GeneratedRefs, "invB">): string {
  return lines([
    "Standard Bank Trust Investment Account - Acc 62000000002",
    "Date,Description,Reference,Amount,Balance",
    row(m.d(16), "Opening Balance", "", "0.00", "0.00"),
    row(m.d(16), "TRANSFER FROM TRUST", r.invB, "20000.00", "20000.00"),
    row(m.d(31), "INTEREST NET OF LPFF 5PCT", "", "95.00", "20095.00"),
  ]);
}

/** What `setInputFiles` takes; also writes the file, as generated, to the evidence dir. */
export function csvFile(name: string, text: string) {
  fs.mkdirSync(path.join(EVIDENCE_DIR, "csv"), { recursive: true });
  fs.writeFileSync(path.join(EVIDENCE_DIR, "csv", name), text);
  return { name, mimeType: "text/csv", buffer: Buffer.from(text, "utf8") };
}

/** Writes a text artefact (e.g. the refused complete's response) to the evidence dir. */
export function writeEvidence(name: string, text: string): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
  fs.writeFileSync(path.join(EVIDENCE_DIR, name), text);
}

/** A full-page screenshot into `qa_cycle/evidence/phase92-exit/screenshots/`. */
export async function evidenceShot(page: Page, name: string): Promise<void> {
  await page.screenshot({
    path: path.join(EVIDENCE_DIR, "screenshots", `${name}.png`),
    fullPage: true,
  });
}

// ── Money ────────────────────────────────────────────────────────────────────────────────────

/** Space, NBSP and narrow NBSP: the separators an en-ZA rendering may use. */
const SPACE = "[\\s\\u00a0\\u202f]";

/**
 * The regex source of a §16.9.6 figure ("101 630,00", "-172,50") as any ZAR rendering of the run
 * shows it: "R 101 630,00" (firm UI, NBSP), "R101 630,00" (backend sentence), "R 101,630.00"
 * (an ICU without the en-ZA grouping), "-R 172,50" / "−R 172,50". A positive figure never matches
 * a negative rendering.
 */
function randSource(amount: string): string {
  const negative = /^[-−]/.test(amount.trim());
  const [whole, cents] = amount.trim().replace(/^[-−]/, "").split(",");
  if (!whole || !cents || !/^\d{2}$/.test(cents)) {
    throw new Error(`randSource: "${amount}" is not a figure like "1 250,00"`);
  }
  const groups = whole.split(/[\s  ]+/).filter(Boolean);
  const body = `${groups.map(escapeRegExp).join(`[\\s\\u00a0\\u202f,.]?`)}[,.]${cents}`;
  return `${negative ? "[-−]" : "(?<![-−])"}R${SPACE}?${body}(?!\\d)`;
}

/** The whole text is exactly this figure (surrounding whitespace allowed). */
export function randExact(amount: string): RegExp {
  return new RegExp(`^${SPACE}*${randSource(amount)}${SPACE}*$`);
}

/** The text contains this figure as a whole amount ("R 1 250,00" never matches "R 11 250,00"). */
export function randIn(amount: string): RegExp {
  return new RegExp(`(?<![\\d,.])${randSource(amount)}`);
}

/**
 * Cents of a displayed ZAR amount or of a §16.9.6 figure: every rendering carries two decimals,
 * so the digits are the cents and a minus sign anywhere makes it negative.
 */
export function cents(text: string): number {
  const digits = text.replace(/\D/g, "");
  if (!digits) throw new Error(`cents: no amount in "${text}"`);
  return (/[-−]/.test(text) ? -1 : 1) * Number(digits);
}

/** An exact, whitespace-tolerant text matcher (for labels and cells). */
export function exactText(text: string): RegExp {
  return new RegExp(`^${SPACE}*${escapeRegExp(text)}${SPACE}*$`);
}

// ── Sessions ─────────────────────────────────────────────────────────────────────────────────

export type FirmUser = "alice" | "bob";

/**
 * A browser context signed in as `user` (mock IDP cookie, as `loginAs` sets it), with the
 * project's `use` options (base URL, SA time zone, viewport). One context per user lets Alice's
 * reconciliation wizard stay open while Bob approves in his own session (§16.9.6 steps 9–15).
 */
export async function firmSession(
  browser: Browser,
  testInfo: TestInfo,
  user: FirmUser
): Promise<BrowserContext> {
  const use = testInfo.project.use;
  const context = await browser.newContext({
    baseURL: use.baseURL,
    timezoneId: use.timezoneId,
    viewport: use.viewport ?? { width: 1280, height: 720 },
    deviceScaleFactor: use.deviceScaleFactor,
    userAgent: use.userAgent,
    locale: use.locale,
  });
  await context.addCookies([
    {
      name: "mock-auth-token",
      value: await getApiToken(user),
      domain: "localhost",
      path: "/",
      httpOnly: false,
      sameSite: "Lax",
    },
  ]);
  return context;
}

// ── Portal (copied from portal/e2e/helpers/auth.ts — not importable from frontend/) ──────────

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const ORG_SLUG = "e2e-test-org";

/**
 * The portal origin (ruling 652E D-3): `PORTAL_BASE_URL`, no default. The e2e compose stack has no
 * portal service, so an unset variable FAILS the portal step with how to start it — never a skip.
 */
export function portalBaseUrl(): string {
  const url = process.env.PORTAL_BASE_URL;
  if (!url) {
    throw new Error(
      "PORTAL_BASE_URL is not set — start the portal against :8081 (see " +
        "qa_cycle/evidence/phase92-exit/README.md) and run with PORTAL_BASE_URL=http://localhost:3002"
    );
  }
  return new URL(url).origin;
}

/**
 * Signs `page` (a context on the portal origin) in as the portal contact `email`: request-link →
 * exchange → the localStorage keys `portal/lib/auth.ts` reads. One login per contact per run:
 * magic links are capped at 3 per contact per 5 minutes.
 */
export async function portalLogin(page: Page, email: string): Promise<void> {
  const linkRes = await fetch(`${BACKEND_URL}/portal/auth/request-link`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, orgId: ORG_SLUG }),
  });
  expect(linkRes.ok, `POST /portal/auth/request-link ${email} → ${linkRes.status}`).toBe(true);
  const { magicLink } = (await linkRes.json()) as { magicLink?: string };
  const token = magicLink?.split("token=").pop()?.split("&")[0];
  expect(token, `a magic link for ${email} (dev/test/e2e profiles return it)`).toBeTruthy();

  const exchangeRes = await fetch(`${BACKEND_URL}/portal/auth/exchange`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, orgId: ORG_SLUG }),
  });
  expect(exchangeRes.ok, `POST /portal/auth/exchange ${email} → ${exchangeRes.status}`).toBe(true);
  const session = (await exchangeRes.json()) as {
    token: string;
    clientId: string;
    clientName: string;
  };
  expect(session.token, "portal JWT").toBeTruthy();

  await page.goto(`${portalBaseUrl()}/login`);
  await page.evaluate(
    (s) => {
      localStorage.setItem("portal_jwt", s.token);
      localStorage.setItem(
        "portal_customer",
        JSON.stringify({ id: s.clientId, name: s.clientName, email: s.email, orgId: s.orgId })
      );
      localStorage.setItem("portal_last_org_id", s.orgId);
    },
    { ...session, email, orgId: ORG_SLUG }
  );
}

// ── Firm screens: shared readers ─────────────────────────────────────────────────────────────

/** The `dd` paired with the `dt` whose whole text is `label`, inside `scope`. */
export function ddFor(scope: Locator, label: string): Locator {
  return scope
    .locator("dt", { hasText: exactText(label) })
    .locator("xpath=following-sibling::dd[1]");
}

/** A Trust → Transactions row whose Reference cell (2nd column) is exactly `reference`. */
export function txRowByRef(page: Page, reference: string): Locator {
  return page.locator('[data-testid^="transaction-row-"]').filter({
    has: page.locator("td:nth-child(2)", { hasText: exactText(reference) }),
  });
}

/** The UUID suffix of a `<prefix><uuid>` test id. */
export async function idFromTestId(locator: Locator, prefix: string): Promise<string> {
  const testId = await locator.getAttribute("data-testid");
  const id = testId?.startsWith(prefix) ? testId.slice(prefix.length) : null;
  if (!id) throw new Error(`expected a data-testid "${prefix}<id>", got "${testId}"`);
  return id;
}

/** A reconciliation-wizard statement line by its exact description. */
export function bankLine(page: Page, description: string): Locator {
  return page.locator('[data-testid^="bank-line-"]').filter({
    has: page.locator("p", { hasText: exactText(description) }),
  });
}

/** A StatCard (`@b2mash/ui/stat-card`, role group named by its label) value. */
export function statCardValue(page: Page, label: string): Locator {
  return page
    .getByRole("group", { name: label, exact: true })
    .locator('[data-slot="stat-card-value"]');
}

// ── Report runner (/org/{slug}/reports/{reportSlug}) ─────────────────────────────────────────

export interface ReportParams {
  /** Date parameters by name (`#param-<name>`), ISO. */
  dates: Record<string, string>;
  /** Trust account picker (`#param-trust_account_id`) — the account's name; omitted = firm-wide. */
  trustAccount?: string;
}

/** Opens a report, fills its parameters and runs it; waits for the result table. */
export async function runReport(
  page: Page,
  base: string,
  slug: string,
  params: ReportParams
): Promise<void> {
  await page.goto(`${base}/reports/${slug}`);
  const run = page.getByRole("button", { name: "Run Report" });
  await expect(run).toBeVisible({ timeout: 30_000 });
  for (const [name, value] of Object.entries(params.dates)) {
    await page.locator(`#param-${name}`).fill(value);
  }
  if (params.trustAccount) {
    await page.locator("#param-trust_account_id").click();
    await page
      .getByRole("option", { name: new RegExp(escapeRegExp(params.trustAccount)) })
      .first()
      .click();
    await expect(page.locator("#param-trust_account_id")).toContainText(params.trustAccount);
  }
  await run.click();
  await expect(run).toBeEnabled({ timeout: 60_000 });
  await expect(page.locator("main table").first()).toBeVisible({ timeout: 60_000 });
}

/** Every row of the report's result table, all pages, keyed by column header. */
export async function readReportRows(page: Page): Promise<Record<string, string>[]> {
  const rows: Record<string, string>[] = [];
  const table = page.locator("main table").first();
  const pager = page.locator("main").getByText(/^Page \d+ of \d+$/);
  const next = page.locator("main").getByRole("button", { name: "Next", exact: true });
  for (let guard = 0; guard < 50; guard++) {
    rows.push(
      ...(await table.evaluate((t) => {
        const heads = [...t.querySelectorAll("thead th")].map((th) => th.textContent?.trim() ?? "");
        return [...t.querySelectorAll("tbody tr")].map((tr) =>
          Object.fromEntries(
            [...tr.querySelectorAll("td")].map((td, i) => [heads[i], td.textContent?.trim() ?? ""])
          )
        );
      }))
    );
    // The pager renders whenever the result has pages; wait for it before reading its state
    await expect(pager).toBeVisible();
    if (await next.isDisabled()) return rows;
    const before = await pager.textContent();
    await next.click();
    await expect(pager).not.toHaveText(before ?? "");
  }
  throw new Error("readReportRows: more than 50 pages");
}

/** The report's one row matching every given column value exactly; fails unless exactly one. */
export function reportRow(
  rows: Record<string, string>[],
  match: Record<string, string>
): Record<string, string> {
  const hits = rows.filter((r) => Object.entries(match).every(([k, v]) => r[k]?.trim() === v));
  expect(hits, `exactly one report row ${JSON.stringify(match)}`).toHaveLength(1);
  return hits[0];
}

/** A report summary card's value (label = the summary key split into words, e.g. "Bank Total"). */
export function reportSummary(page: Page, label: string): Locator {
  return page
    .locator("main p", { hasText: exactText(label) })
    .locator("xpath=following-sibling::p[1]");
}

// ── Step 21: the read-only database check ────────────────────────────────────────────────────

/** Column order of the `cashbook-vs-ledgers` section of `psql-check.sh`. */
export interface AccountEffects {
  account: string;
  cashbook: string;
  ledgers: string;
  diff: string;
  postedRows: string;
}

/** Column order of the `monthly-lists` section of `psql-check.sh`. */
export interface MonthlyListRow {
  account: string;
  periodEnd: string;
  bank: string;
  outstandingDeposits: string;
  outstandingPayments: string;
  adjustedBank: string;
  cashbook: string;
  clientLedgers: string;
  investmentCreditors: string;
  fund: string;
  unidentified: string;
  interAccount: string;
  creditorTotal: string;
  difference: string;
}

/**
 * Runs `qa_cycle/evidence/phase92-exit/psql-check.sh <month end>` (SELECT only, in a read-only
 * transaction; it writes `psql-output.txt`) and parses its machine-readable sections.
 */
export function runPsqlCheck(monthEnd: string): {
  effects: AccountEffects[];
  lists: MonthlyListRow[];
} {
  const out = execFileSync("bash", [path.join(EVIDENCE_DIR, "psql-check.sh"), monthEnd], {
    encoding: "utf8",
    timeout: 120_000,
  });
  const section = (name: string) => {
    const start = out.indexOf(`## ${name}\n`);
    if (start < 0) throw new Error(`psql-check.sh printed no "## ${name}" section:\n${out}`);
    const body = out.slice(start).split("\n").slice(1);
    const end = body.findIndex((l) => l.startsWith("## "));
    return (end < 0 ? body : body.slice(0, end)).filter((l) => l.trim() !== "");
  };
  const effects = section("cashbook-vs-ledgers").map((l) => {
    const [account, cashbook, ledgers, diff, postedRows] = l.split("|");
    return { account, cashbook, ledgers, diff, postedRows };
  });
  const lists = section("monthly-lists").map((l) => {
    const c = l.split("|");
    return {
      account: c[0],
      periodEnd: c[1],
      bank: c[2],
      outstandingDeposits: c[3],
      outstandingPayments: c[4],
      adjustedBank: c[5],
      cashbook: c[6],
      clientLedgers: c[7],
      investmentCreditors: c[8],
      fund: c[9],
      unidentified: c[10],
      interAccount: c[11],
      creditorTotal: c[12],
      difference: c[13],
    };
  });
  return { effects, lists };
}

/** A psql `numeric(15,2)` ("101630.00", "-172.50") as cents. */
export function dbCents(value: string): number {
  const match = value.trim().match(/^(-?)(\d+)\.(\d{2})$/);
  if (!match) throw new Error(`dbCents: "${value}" is not a numeric(15,2)`);
  return (match[1] ? -1 : 1) * (Number(match[2]) * 100 + Number(match[3]));
}
