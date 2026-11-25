/**
 * Phase 93 exit gate — the client & trust take-on, observed end to end (slice 660A, task 660.3).
 *
 * A fictional firm's exports (qa/fixtures/take-on/, README there for every designed row and the
 * expected figures) are taken on through the UI: trust accounts, clients from a two-sheet XLSX,
 * matters, the trust opening position (a debit block, a control-total block, then clean), the
 * owner's sign-off on her Home page, the commit and its report, the FICA review queue, the debtor
 * opening balances, and finally the first post-cutover month, reconciled and balanced with no
 * manual adjustment. Every step asserts the product: a mismatch is a defect to report
 * (qa_cycle/evidence/phase93-exit/gap-report.md), never a spec edit, never a skip.
 *
 * Dates (660.2, D-224): C = the last day of the month two months back (SA date), computed ONCE in
 * beforeAll; the first month M is the month after C, wholly in the past (FU-6a).
 *
 * Rulings applied (tasks/phase93-decisions.md):
 *  - P-14  own Playwright project `take-on`, run alone on a fresh stack (B-6 / D-263):
 *            bash compose/scripts/e2e-down.sh && bash compose/scripts/e2e-up.sh
 *            PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:take-on
 *          (TAKE_ON_EVIDENCE_DIR for the evidence dir; MAILPIT_URL defaults to the e2e :8026).
 *  - B-1 / D-259  the trust position is taken on into a NEW non-primary GENERAL account G (the
 *          seeded primary opens on the stack date, after any cutover two months back). The first
 *          month's "deposit" and "payment" are an unidentified deposit and the Fund sweep recorded
 *          from statement lines (option B): no UI posts a client deposit or payment to a
 *          non-primary account (F-657C-8 / FR-1, gap report).
 *  - B-2 / D-260  the dormancy JOB has no product trigger: the read-only "Check for Dormant
 *          Clients" is observed, the job run is a DEFERRED annotation (never a skip, never PASS).
 *  - B-3 / D-261  the CashDigest has no product trigger: the debtor book (Collections) is
 *          observed in its place, the digest is a DEFERRED annotation.
 *  - B-5 / D-262  "matters with the orphan refused, then linked": the orphan row is refused with
 *          its exact text and not committed; the other matters are linked to their clients.
 *
 * Users: Bob (admin) prepares, attests and commits every take-on; Alice (owner) signs the trust
 * take-on off from her Home page (FD-4, D-200/D-210) and reconciles the first month. One browser
 * context per user, so pages stay open across steps.
 *
 * Single-shot per stack: every step commits. Evidence (screenshots, the fixtures as rendered, the
 * statement as generated, the report downloads + SHA-256, Mailpit counts) goes to one per-run
 * directory: TAKE_ON_EVIDENCE_DIR, default <os tmpdir>/take-on-<timestamp>/.
 */
import { test, expect, type BrowserContext, type Page } from "@playwright/test";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { daysBetween, todayInSA } from "@b2mash/shared/date";
import { apiOk } from "../../helpers/api";
import { BASE, openClient, openGroupedTab, selectNativeOption } from "../../helpers/legal-depth-ii";
import {
  bankLine,
  ddFor,
  evidenceShot,
  exactText,
  firmSession,
  idFromTestId,
  randExact,
  randIn,
  runReport,
} from "../../helpers/trust-balanced-month";
import {
  addTrustSource,
  evidenceDir,
  expectCards,
  expectEquation,
  fillPosition,
  filterRows,
  firstMonthStatementCsv,
  getBatch,
  logEvidence,
  longDate,
  mailpitTotal,
  mapAndSave,
  type MailpitCount,
  openTakeOn,
  pickOption,
  renderFixture,
  rowMessages,
  runDryRun,
  savePosition,
  takeOnDates,
  type TakeOnDates,
  uploadFirst,
} from "../../helpers/take-on";

// ── The world (qa/fixtures/take-on/README.md) ────────────────────────────────────────────────
let D: TakeOnDates;

const FIRM = "E2E Test Organization";
const BANK = "Standard Bank";
const G = { name: "Take-on Trust Banking Account", number: "6570001" };
const I3 = {
  name: "Take-on Investment 6573001",
  number: "6573001",
  basis: "Section 86(3) — the firm invests surplus trust money",
};
const I4 = {
  name: "Take-on Investment 6574001",
  number: "6574001",
  basis: "Section 86(4) — invested on a client's instruction",
};
const SEEDED_PRIMARY = "Mathebula & Partners Trust Account";

const SECTION = {
  "client-ledger": "Client and matter balances",
  "unidentified-deposits": "Unidentified deposits",
  investments: "Investments",
  "outstanding-items": "Items outstanding at cutover",
} as const;
type Kebab = keyof typeof SECTION;

/** The trust sources' suggested mappings (headers = the catalogue labels). */
const TRUST_MAPPING: Record<Kebab, Record<string, string>> = {
  "client-ledger": {
    clientRef: "Client reference",
    matterRef: "Matter reference",
    balance: "Balance at cutover",
    sourceRef: "Previous system ID",
  },
  "unidentified-deposits": {
    receivedDate: "Date received",
    amount: "Amount",
    bankReference: "Bank reference",
    description: "Description",
  },
  investments: {
    investmentAccount: "Investment account (number or name)",
    principal: "Principal at cutover",
    depositDate: "Original deposit date",
    clientRef: "Client reference",
    matterRef: "Matter reference",
    balancePerConfirmation: "Balance per confirmation",
  },
  "outstanding-items": {
    originalDate: "Original date",
    amount: "Amount",
    reference: "Reference",
    clientRef: "Client reference",
    matterRef: "Matter reference",
  },
};

const POSITION = {
  bank: "418500.00",
  od: "5000.00",
  op: "20000.00",
  cashbook: "403500.00",
  fund: "1250.00",
  invested: "300000.00",
};

/**
 * The FICA review queue as designed: Unrated first, then next review date ascending (D-138).
 * `reasons` are the attestation's own reasons in the product's order (`FicaReviewReason`
 * declaration order); "Review date passed" is added (last) once `nextReview` is on or before the
 * SA run date, so the expectation follows the run date (qa/fixtures/take-on/README.md "Dates").
 * Mooi River Family Trust (C-018, MEDIUM, next 2027-05-05) has no reason of its own: it joins the
 * queue on that date. Every expectation here holds until FICA_QUEUE_HORIZON, when Karoo Freight
 * (C-006) joins on the same date as Kgomotso Moloi (an unobserved tie order): the step refuses to
 * run from then (renew the fixture's fixed CDD dates).
 */
const FICA_QUEUE: { name: string; nextReview: string | null; reasons: string[] }[] = [
  { name: "Naledi Khoza", nextReview: null, reasons: ["Not risk-rated"] },
  { name: "Pieter Steyn", nextReview: "2023-03-01", reasons: [] },
  { name: "Refilwe Dube", nextReview: "2027-02-10", reasons: ["Not screened"] },
  { name: "Mooi River Family Trust", nextReview: "2027-05-05", reasons: [] },
  { name: "Kgomotso Moloi", nextReview: "2027-08-15", reasons: ["Source of funds outstanding"] },
  {
    name: "Highveld Logistics (Pty) Ltd",
    nextReview: "2028-04-01",
    reasons: ["Beneficial ownership outstanding"],
  },
];
const FICA_QUEUE_HORIZON = "2027-08-15";

/** The queue on SA date `today`: each client's full reason list, clients with none left out. */
function ficaQueueOn(today: string): { name: string; reasons: string }[] {
  if (today >= FICA_QUEUE_HORIZON) {
    throw new Error(
      `FICA queue fixture expired on ${FICA_QUEUE_HORIZON} (run date ${today}): renew the fixed CDD dates`
    );
  }
  return FICA_QUEUE.map((q) => ({
    name: q.name,
    reasons: [
      ...q.reasons,
      ...(q.nextReview && q.nextReview <= today ? ["Review date passed"] : []),
    ],
  }))
    .filter((q) => q.reasons.length > 0)
    .map((q) => ({ name: q.name, reasons: q.reasons.join(", ") }));
}

/** The 18 clients the take-on creates (file rows 2–19). */
const CREATED_CLIENTS = [
  "Amahle Zulu",
  "Bongani Nkosi",
  "Chantal Pillay",
  "Thandiwe Mokoena",
  "Johan Pretorius",
  "Karoo Freight (Pty) Ltd",
  "Ayesha Patel",
  "Lerato Mahlangu",
  "Bheki Zondo",
  "Naledi Khoza",
  "Pieter Steyn",
  "Refilwe Dube",
  "Kgomotso Moloi",
  "Highveld Logistics (Pty) Ltd",
  "Karabo Sithole",
  "Mandla Shabalala",
  "Zinhle Mabuza",
  "Mooi River Family Trust",
];

/** The six brought-forward fee notes (debtors.csv rows 2–7): client, amount, due as C + n. */
const DEBTORS = [
  { number: "PS-1001", client: "Thandiwe Mokoena", amount: "4 600,00", due: -47 },
  { number: "PS-1002", client: "Johan Pretorius", amount: "12 075,50", due: 1 },
  { number: "PS-1003", client: "Karoo Freight (Pty) Ltd", amount: "2 300,00", due: 20 },
  { number: "PS-1004", client: "Ayesha Patel", amount: "8 050,00", due: 120 },
  { number: "INV-0007", client: "Lerato Mahlangu", amount: "1 150,00", due: 30 },
  { number: "PS-1006", client: "Bheki Zondo", amount: "3 450,00", due: 0 },
];

// ── Run state ────────────────────────────────────────────────────────────────────────────────
let aliceCtx: BrowserContext;
let bobCtx: BrowserContext;
let alice: Page;
let bob: Page;
const state: {
  trustBatch?: string;
  reportId?: string;
  downloads: Record<string, string>;
  mailpit: Record<string, { before: number; after: number }>;
  /** Mailpit at step 0: the last step asserts the whole run left it unchanged (R-3). */
  mailpitStart?: MailpitCount;
} = { downloads: {}, mailpit: {} };

// ── Small helpers ────────────────────────────────────────────────────────────────────────────

/**
 * R-3: an action sends no email. Waits 8 s after the action settled (the 657D precedent: any
 * async email would have landed), then the total and the latest message are unchanged.
 */
async function expectMailpitUnchanged(before: MailpitCount, label: string): Promise<void> {
  // A pure wait, on purpose: the claim is that nothing arrives within the window
  await new Promise((resolve) => setTimeout(resolve, 8_000));
  const after = await mailpitTotal();
  state.mailpit[label] = { before: before.total, after: after.total };
  logEvidence(`[mailpit ${label}]`, { before, after });
  expect(after.total, `Mailpit total across ${label}`).toBe(before.total);
  expect(after.latest?.id, `latest Mailpit message across ${label}`).toBe(before.latest?.id);
}

/** A Results-step stat card value. */
function resultCard(page: Page, id: "created" | "skipped" | "failed") {
  return page.getByTestId(`take-on-result-${id}`).locator('[data-slot="stat-card-value"]');
}

/** Opens the commit dialog, asserts its title and body, confirms; waits for the Results cards. */
async function commit(
  page: Page,
  o: { title: string; body: string; trustSentence?: string }
): Promise<void> {
  await page.getByTestId("take-on-commit").click();
  const dialog = page.getByTestId("take-on-commit-dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText(o.title);
  await expect(page.getByTestId("take-on-commit-body")).toHaveText(o.body);
  if (o.trustSentence) {
    await expect(page.getByTestId("take-on-commit-trust")).toHaveText(o.trustSentence);
  } else {
    await expect(page.getByTestId("take-on-commit-trust")).toHaveCount(0);
  }
  await page.getByTestId("take-on-commit-confirm").click();
  await expect(page.getByTestId("take-on-result-cards")).toBeVisible({ timeout: 240_000 });
}

/**
 * Maps every trust source in `kebabs`: the wizard shows one unmapped source at a time (its tab
 * pressed) and moves to the next after each save; the order is the wizard's, not asserted.
 */
async function mapTrustSources(page: Page, kebabs: Kebab[]): Promise<void> {
  const remaining = new Set<string>(kebabs);
  const pressed = page.locator('[data-testid^="take-on-source-tab-"][aria-pressed="true"]');
  let previous = "";
  while (remaining.size > 0) {
    let kebab = "";
    await expect
      .poll(
        async () => {
          if ((await pressed.count()) !== 1) return "";
          const current = ((await pressed.getAttribute("data-testid")) ?? "").replace(
            "take-on-source-tab-",
            ""
          );
          kebab = current !== previous && remaining.has(current) ? current : "";
          return kebab;
        },
        {
          message: `the next unmapped trust source of ${[...remaining].join(", ")}`,
          timeout: 60_000,
        }
      )
      .not.toBe("");
    await mapAndSave(page, TRUST_MAPPING[kebab as Kebab]);
    remaining.delete(kebab);
    previous = kebab;
  }
}

/** Downloads one file through `button`; keeps it in the evidence dir; returns its SHA-256. */
async function download(page: Page, button: ReturnType<Page["getByTestId"]>, label: string) {
  const [file] = await Promise.all([
    page.waitForEvent("download", { timeout: 60_000 }),
    button.click(),
  ]);
  const target = path.join(evidenceDir(), "downloads", `${label}-${file.suggestedFilename()}`);
  await file.saveAs(target);
  const bytes = fs.readFileSync(target);
  const sha256 = crypto.createHash("sha256").update(bytes).digest("hex");
  state.downloads[`${label}-${file.suggestedFilename()}`] = sha256;
  logEvidence(`[download ${label}]`, {
    file: file.suggestedFilename(),
    size: bytes.length,
    sha256,
  });
  return { bytes, sha256, name: file.suggestedFilename() };
}

/** The four-way debtor-book bucket of a due date on `today` (the SA date; AgingBuckets). */
function bucketOf(dueIso: string, today: string): 0 | 1 | 2 | 3 {
  const overdue = daysBetween(dueIso, today);
  if (overdue <= 0) return 0;
  if (overdue <= 30) return 1;
  if (overdue <= 60) return 2;
  return 3;
}

// ── The take-on ──────────────────────────────────────────────────────────────────────────────

test.describe.serial("Phase 93 exit — the take-on, observed end to end (660.3)", () => {
  test.beforeAll(async ({ browser }, testInfo) => {
    // 660.2: C and M once per run (refuses within 30 minutes of SA midnight at a month end)
    D = takeOnDates();
    console.log(
      `take-on: C = ${D.c} (${D.cLong}); first month ${D.month.monthName}; evidence ${evidenceDir()}`
    );
    logEvidence("[dates]", { today: D.today, c: D.c, month: D.month.monthName });
    aliceCtx = await firmSession(browser, testInfo, "alice");
    bobCtx = await firmSession(browser, testInfo, "bob");
    alice = await aliceCtx.newPage();
    bob = await bobCtx.newPage();
  });

  test.afterEach(async ({}, testInfo) => {
    // The shared pages are not the `page` fixture: capture them on failure for the report
    if (testInfo.status === testInfo.expectedStatus) return;
    for (const [name, p] of [
      ["alice", alice],
      ["bob", bob],
    ] as const) {
      if (!p || p.isClosed()) continue;
      await testInfo.attach(`${name}-on-failure`, {
        body: await p.screenshot({ fullPage: true }),
        contentType: "image/png",
      });
    }
  });

  test.afterAll(async () => {
    fs.writeFileSync(
      path.join(evidenceDir(), "state.json"),
      JSON.stringify({ dates: { today: D?.today, c: D?.c }, ...state }, null, 2) + "\n"
    );
    await aliceCtx?.close();
    await bobCtx?.close();
  });

  // ── 0. Fresh stack ─────────────────────────────────────────────────────────────────────────
  test("0 · fresh stack: only the seeded primary trust account, no take-on batch, no fixture client", async () => {
    const freshStack =
      "take-on runs once per fresh stack: bash compose/scripts/e2e-down.sh && bash compose/scripts/e2e-up.sh";
    const accounts =
      await apiOk<{ accountName: string; isPrimary: boolean }[]>("/api/trust-accounts");
    expect(
      accounts.map((a) => a.accountName),
      `only the seeded primary trust account — ${freshStack}`
    ).toEqual([SEEDED_PRIMARY]);
    const batches = await apiOk<{ page: { totalElements: number } }>("/api/take-on/batches");
    expect(batches.page.totalElements, `take-on batches exist — ${freshStack}`).toBe(0);
    const clients = await apiOk<{ email: string | null }[]>("/api/clients");
    expect(
      clients.filter((c) => (c.email ?? "").endsWith("@example.test")),
      `fixture clients exist — ${freshStack}`
    ).toHaveLength(0);
    state.mailpitStart = await mailpitTotal();
    logEvidence("[mailpit at start]", state.mailpitStart);
  });

  // ── 1. Accounts ────────────────────────────────────────────────────────────────────────────
  test("1 · Bob adds G (General, not primary) and the s86(3) / s86(4) investment accounts", async () => {
    test.setTimeout(180_000);
    // Opened well before C and before the oldest unidentified deposit (C − 472 days)
    const opened = `${Number(D.c.slice(0, 4)) - 2}-01-01`;
    for (const a of [
      { ...G, type: "General", basis: undefined as string | undefined },
      { ...I3, type: "Investment" },
      { ...I4, type: "Investment" },
    ]) {
      await bob.goto(`${BASE}/settings/fees?tab=trust-accounting`);
      const section = bob.getByTestId("trust-accounts-section");
      await expect(section).toBeVisible({ timeout: 60_000 });
      await bob.getByTestId("add-trust-account-button").click();
      const dialog = bob.getByTestId("create-trust-account-dialog");
      await expect(dialog).toBeVisible();
      await dialog.getByLabel("Account Name", { exact: true }).fill(a.name);
      await dialog.getByLabel("Bank Name", { exact: true }).fill(BANK);
      await dialog.getByLabel("Branch Code", { exact: true }).fill("051001");
      await dialog.getByLabel("Account Number", { exact: true }).fill(a.number);
      await pickOption(bob, dialog.getByTestId("trust-account-type-select"), a.type);
      if (a.basis) await pickOption(bob, dialog.getByTestId("trust-account-basis-select"), a.basis);
      await dialog.getByLabel("Opened Date", { exact: true }).fill(opened);
      const primary = dialog.getByRole("checkbox", { name: "Set as primary trust account" });
      if (await primary.isChecked()) await primary.click();
      await expect(primary).not.toBeChecked();
      await dialog.getByRole("button", { name: "Create Account" }).click();
      await expect(dialog).toBeHidden({ timeout: 20_000 });
      await bob.reload();
      await expect(bob.getByTestId("trust-accounts-section").getByText(a.name).first()).toBeVisible(
        { timeout: 30_000 }
      );
    }
    const accounts =
      await apiOk<
        { accountName: string; accountType: string; isPrimary: boolean; openedDate: string }[]
      >("/api/trust-accounts");
    const g = accounts.find((x) => x.accountName === G.name);
    expect(g).toMatchObject({ accountType: "GENERAL", isPrimary: false, openedDate: opened });
    expect(accounts.find((x) => x.accountName === I3.name)?.accountType).toBe("INVESTMENT");
    expect(accounts.find((x) => x.accountName === I4.name)?.accountType).toBe("INVESTMENT");
    expect(accounts.find((x) => x.accountName === SEEDED_PRIMARY)?.isPrimary).toBe(true);
    await evidenceShot(bob, "01-trust-accounts");
  });

  // ── 2. Clients (XLSX, two sheets) ──────────────────────────────────────────────────────────
  test("2 · clients from the two-sheet XLSX: sheet step, cards, every designed row, attest, commit (no email)", async () => {
    test.setTimeout(300_000);
    await uploadFirst(bob, {
      kebab: "clients",
      batchName: "Take-on clients (previous system export)",
      file: renderFixture("clients.xlsx", D),
    });
    // D-39: an unmapped XLSX with more than one sheet opens on the Sheet step
    const picker = bob.getByTestId("take-on-sheet-picker");
    await expect(picker).toBeVisible({ timeout: 60_000 });
    await expect(bob.locator('label[for="take-on-sheet-0"]')).toHaveText(exactText("Old notes"));
    await expect(bob.getByTestId("take-on-sheet-hidden-0")).toBeVisible();
    await expect(bob.locator('label[for="take-on-sheet-1"]')).toHaveText(exactText("Clients"));
    await expect(bob.getByTestId("take-on-sheet-hidden-1")).toHaveCount(0);
    // The default sheet is the first visible one
    await expect(bob.getByTestId("take-on-sheet-1")).toHaveAttribute("aria-checked", "true");
    await expect(bob.getByTestId("take-on-header-row")).toHaveValue("1");
    await evidenceShot(bob, "02-clients-sheet-step");
    await bob.getByTestId("take-on-apply-sheet").click();

    await mapAndSave(bob, {
      sourceRef: "Previous system ID",
      name: "Client name",
      email: "Email",
      idNumber: "ID number",
      cddCompletedDate: "CDD completed date",
      lastReviewDate: "Last review date",
      riskRating: "Risk rating",
      screeningStatus: "Screening",
      screeningDate: "Screening date",
      sourceOfFundsStatus: "Source of funds",
      cddRecordsLocation: "CDD records location",
    });
    await runDryRun(bob);
    await expectCards(bob, { total: 23, valid: 19, error: 4, duplicate: 1, blocks: 0 });

    // The designed rows (file row numbers; the header is row 1)
    await expect(bob.getByTestId("take-on-row-status-20")).toHaveText("Error");
    await expect(rowMessages(bob, 20)).toContainText(["Duplicate of row 5 in this file"]);
    await expect(bob.getByTestId("take-on-row-status-21")).toHaveText(
      "Duplicate — will be skipped"
    );
    await expect(bob.getByTestId("take-on-row-duplicate-21")).toContainText(
      "Matches a client already in Kazi"
    );
    await expect(bob.getByTestId("take-on-row-status-22")).toHaveText("Error");
    await expect(rowMessages(bob, 22)).toContainText([
      "VERY_HIGH is not one of: Low, Medium, High, Unrated",
    ]);
    await expect(bob.getByTestId("take-on-row-22-riskRating")).toHaveText(exactText("—"));
    await expect(rowMessages(bob, 23)).toContainText(["Client name is required."]);
    await expect(rowMessages(bob, 24)).toContainText([
      "An email address is required to create a client in Kazi",
    ]);
    for (const n of [2, 10, 11, 12, 13, 14, 15, 19]) {
      await expect(bob.getByTestId(`take-on-row-status-${n}`)).toHaveText("Valid");
    }
    await evidenceShot(bob, "02-clients-validation");
    await filterRows(bob, "Error", [20, 22, 23, 24]);
    await filterRows(bob, "Duplicate — will be skipped", [21]);
    await pickOption(bob, bob.getByTestId("take-on-filter-status"), "All statuses");

    // FICA attestation (FICA-ATTEST-v1) by Bob
    await bob.getByTestId("take-on-to-attestation").click();
    await expect(bob.getByTestId("take-on-attestation")).toBeVisible({ timeout: 60_000 });
    await expect(bob.getByTestId("take-on-attestation-statement")).toContainText(
      `I confirm that customer due diligence for the clients in this batch was performed by ${FIRM} in its previous system`
    );
    await expect(bob.getByTestId("take-on-attestation-fica-summary")).toContainText(
      "of the 18 clients to be created"
    );
    await expect(bob.getByTestId("take-on-attest")).toBeDisabled();
    await bob.getByTestId("take-on-attestation-accept").click();
    await bob.getByTestId("take-on-attest").click();
    await expect(bob.getByTestId("take-on-attestation-live")).toContainText(
      /Attested by Bob Admin \(Admin\) on .+\./,
      { timeout: 60_000 }
    );
    await evidenceShot(bob, "02-clients-attested");

    const before = await mailpitTotal();
    await commit(bob, {
      title: "Commit clients take-on?",
      body: "18 records will be created, and 1 duplicate skipped. This cannot be undone.",
    });
    await expect(resultCard(bob, "created")).toHaveText("18");
    await expect(resultCard(bob, "skipped")).toHaveText("1");
    await expect(resultCard(bob, "failed")).toHaveText("0");
    await expect(bob.getByTestId("take-on-result-skipped")).toContainText("Duplicate — skipped");
    await evidenceShot(bob, "02-clients-results");
    await expectMailpitUnchanged(before, "clients commit");

    // FD-10: portal access creates contacts, sends nothing
    const beforePortal = await mailpitTotal();
    await bob.getByTestId("take-on-portal-access").click();
    await expect(bob.getByTestId("take-on-portal-result")).toHaveText(
      "18 portal contacts created",
      {
        timeout: 60_000,
      }
    );
    await expectMailpitUnchanged(beforePortal, "portal access");
  });

  // ── 3. Matters ─────────────────────────────────────────────────────────────────────────────
  test("3 · matters: the orphan refused with its exact text and not committed; the others linked to their clients", async () => {
    test.setTimeout(300_000);
    await uploadFirst(bob, {
      kebab: "matters",
      batchName: "Take-on matters (previous system export)",
      file: renderFixture("matters.csv", D),
    });
    await expect(bob.getByTestId("take-on-mapping")).toBeVisible({ timeout: 60_000 });
    // The extra column is parked as a note (its trigger's test id carries the header's index)
    await pickOption(
      bob,
      bob.getByTestId("take-on-leftovers").getByRole("combobox", { name: "File room" }),
      "Keep as a note on the record"
    );
    await mapAndSave(bob, {
      name: "Matter name",
      clientEmail: "Client email",
      clientRef: "Client previous-system ID",
      clientIdNumber: "Client ID number",
      referenceNumber: "Matter reference",
      dueDate: "Due date",
      workType: "Work type",
      priority: "Priority",
      leadMemberEmail: "Responsible attorney email",
      sourceRef: "Previous system ID",
    });
    await runDryRun(bob);
    await expectCards(bob, { total: 16, valid: 15, error: 1, duplicate: 0, blocks: 0 });
    await expect(bob.getByTestId("take-on-row-status-16")).toHaveText("Error");
    await expect(rowMessages(bob, 16)).toContainText([
      "No client matches C-099. Take the client on first, or correct the reference.",
    ]);
    await expect(bob.getByTestId("take-on-row-11-lead-warning")).toHaveText("Not a member");
    await expect(rowMessages(bob, 11)).toContainText([
      "No member of the firm has this email address. The member who commits the take-on will be the matter's lead.",
    ]);
    await evidenceShot(bob, "03-matters-validation");
    await filterRows(bob, "Error", [16]);

    // Matters commit from Validation (no attestation, D-169)
    const before = await mailpitTotal();
    await commit(bob, {
      title: "Commit matters take-on?",
      body: "15 records will be created. This cannot be undone.",
    });
    await expect(resultCard(bob, "created")).toHaveText("15");
    await expect(resultCard(bob, "failed")).toHaveText("0");
    await evidenceShot(bob, "03-matters-results");
    await expectMailpitUnchanged(before, "matters commit");

    // B-5: after the commit the same table shows each row's outcome — the orphan is still an
    // error (never committed), the others were created …
    await bob.getByTestId("take-on-step-validation").click();
    await expect(bob.getByTestId("take-on-row-status-16")).toHaveText("Error", { timeout: 30_000 });
    await expect(bob.getByTestId("take-on-row-status-2")).toHaveText("Created");
    // … and linked to their clients, by previous-system ID, email and ID number (client → Matters):
    // all 15 created matters (matters.csv rows 2–15 and 17), on the 13 clients that own them
    for (const [client, matters] of [
      ["Amahle Zulu", ["Transfer of Erf 101 Brooklyn", "Bond registration Erf 101 Brooklyn"]],
      ["Bongani Nkosi", ["Estate late S Nkosi", "Estate duty objection"]],
      ["Chantal Pillay", ["Supply contract claim"]],
      ["Thandiwe Mokoena", ["Transfer of Erf 77 Waterkloof"]],
      ["Johan Pretorius", ["Labour dispute CCMA"]], // by email
      ["Karoo Freight (Pty) Ltd", ["Freight contract review"]],
      ["Ayesha Patel", ["Sale of Erf 88 Umhlanga"]], // by ID number
      ["Naledi Khoza", ["Will drafting"]],
      ["Pieter Steyn", ["Road accident claim"]],
      ["Refilwe Dube", ["Liquor licence renewal"]],
      ["Kgomotso Moloi", ["Farm lease agreement"]],
      ["Karabo Sithole", ["Antenuptial contract"]],
      ["Mooi River Family Trust", ["Trust deed amendment"]],
    ] as const) {
      await openClient(bob, client);
      await openGroupedTab(bob, "work", "matters");
      const panel = bob.getByRole("tabpanel");
      for (const matter of matters) {
        await expect(panel.getByRole("link", { name: matter, exact: true })).toBeVisible({
          timeout: 15_000,
        });
      }
    }
    await evidenceShot(bob, "03-client-matters-tab");
  });

  // ── 4–6. Trust opening position ────────────────────────────────────────────────────────────
  test("4 · trust position with C-003 in debit: 2 statutory blocks, exact texts, nothing to override", async () => {
    test.setTimeout(300_000);
    const batchId = await uploadFirst(bob, {
      kebab: "trust-position",
      batchName: `Take-on trust position at ${D.c}`,
      file: renderFixture("trust-client-ledger-debit.csv", D),
      trust: { account: G.name, section: SECTION["client-ledger"] },
      cutover: D.c,
    });
    state.trustBatch = batchId;
    logEvidence("[trust batch]", batchId);
    await bob.getByTestId("take-on-step-upload").click();
    await expect(bob.getByTestId("take-on-files")).toBeVisible({ timeout: 60_000 });
    await addTrustSource(
      bob,
      "unidentified-deposits",
      SECTION["unidentified-deposits"],
      renderFixture("trust-unidentified-deposits.csv", D)
    );
    await addTrustSource(
      bob,
      "investments",
      SECTION.investments,
      renderFixture("trust-investments.csv", D)
    );
    await addTrustSource(
      bob,
      "outstanding-items",
      SECTION["outstanding-items"],
      renderFixture("trust-outstanding-items.csv", D)
    );
    for (const kebab of Object.keys(SECTION)) {
      await expect(bob.getByTestId(`take-on-trust-section-${kebab}`)).toHaveAttribute(
        "data-uploaded",
        "true"
      );
    }
    await evidenceShot(bob, "04-trust-files");
    await bob.getByTestId("take-on-files-next").click();
    // The wizard walks the unmapped sources one by one (the next unmapped after each save)
    await mapTrustSources(bob, Object.keys(SECTION) as Kebab[]);
    await expect(bob.getByTestId("take-on-validation")).toBeVisible({ timeout: 60_000 });
    await fillPosition(bob, POSITION);
    await savePosition(bob, batchId);

    await expectCards(bob, { total: 9, valid: 8, error: 1, duplicate: 0, blocks: 2 });
    const debitRow = bob
      .locator('[data-testid^="take-on-row-"][data-section="CLIENT_LEDGER"]')
      .filter({ hasText: "TL-C1" });
    await expect(debitRow).toHaveCount(1);
    await expect(debitRow).toContainText("Error");
    await expect(debitRow).toContainText("Block");
    await expect(debitRow.locator("td").last().locator("li")).toContainText([
      "This client's matter is in debit (R-500,00) at the cutover date. No trust creditor may be in debit (Rule 54.14.9). The firm must immediately report a debit balance to the Council in writing, with the reason and proof that it was rectified (Rule 54.14.11). Correct it in the previous system, then upload the corrected file.",
    ]);
    const banner = bob.getByTestId("take-on-trust-block-banner");
    await expect(banner).toContainText("Statutory block — cannot be overridden");
    await expect(bob.getByTestId("take-on-trust-blocks").locator("li")).toHaveText([
      "The trust creditors (R353 500,00) do not equal the cashbook balance at cutover (R403 500,00): difference R50 000,00. Every creditor group must be included and must agree to the cent.",
    ]);
    await expect(bob.getByTestId("take-on-trust-row-blocks")).toHaveText(
      "1 row holds a statutory block (marked Block below). Correct it in the previous system, then upload the corrected file."
    );
    // FD-16 / D-124: never overridable
    await expect(banner.locator("button, input, a, [role=checkbox]")).toHaveCount(0);
    await expect(bob.getByTestId("take-on-commit")).toHaveCount(0);
    const batch = await getBatch(batchId);
    expect(batch.allowedActions).not.toContain("SIGN_OFF");
    expect(batch.allowedActions).not.toContain("COMMIT");
    await evidenceShot(bob, "04-trust-debit-block");
    // F-660A-1 (MEDIUM, gap report; D-264): "Continue to sign-off" is offered on this blocked
    // batch (the wizard gates it on status VALIDATED only, take-on-wizard.tsx:340), and the
    // Sign-off step then reads as if the take-on awaited a partner. Observed as the product
    // behaves; the gate itself holds — nothing there signs or commits. The F-660A-1 fix PR must
    // flip the next line to `toHaveCount(0)` (and drop the Sign-off step checks) in the same PR.
    await expect(bob.getByTestId("take-on-to-sign-off")).toBeVisible();
    await bob.getByTestId("take-on-to-sign-off").click();
    await expect(bob.getByTestId("take-on-signoff-status")).toHaveText(
      "A partner or director signs off this take-on from their Home page."
    );
    await expect(bob.getByTestId("takeon-signoff-submit")).toHaveCount(0);
    await expect(bob.getByTestId("take-on-commit")).toHaveCount(0);
    await evidenceShot(bob, "04-trust-debit-block-sign-off-step");
    test.info().annotations.push({
      type: "FINDING",
      description:
        'F-660A-1 (MEDIUM): "Continue to sign-off" is offered on a trust batch with 2 statutory blocks; its Sign-off step says "A partner or director signs off this take-on from their Home page." although it can never be signed (allowedActions has no SIGN_OFF/COMMIT).',
    });
  });

  test("5 · corrected client file, cashbook off by R10,00: EQ-1 and EQ-2 blocks, exact texts", async () => {
    test.setTimeout(240_000);
    const batchId = state.trustBatch!;
    await bob.getByTestId("take-on-step-upload").click();
    await expect(bob.getByTestId("take-on-files")).toBeVisible({ timeout: 60_000 });
    await bob.getByTestId("take-on-remove-source-client-ledger").click();
    await expect(bob.getByTestId("take-on-trust-section-client-ledger")).toHaveAttribute(
      "data-uploaded",
      "false",
      { timeout: 30_000 }
    );
    await addTrustSource(
      bob,
      "client-ledger",
      SECTION["client-ledger"],
      renderFixture("trust-client-ledger.csv", D)
    );
    await bob.getByTestId("take-on-files-next").click();
    await mapTrustSources(bob, ["client-ledger"]);
    await expect(bob.getByTestId("take-on-validation")).toBeVisible({ timeout: 60_000 });
    await fillPosition(bob, { ...POSITION, cashbook: "403510.00" });
    await savePosition(bob, batchId);

    await expectCards(bob, { total: 9, valid: 9, error: 0, duplicate: 0, blocks: 2 });
    await expect(bob.getByTestId("take-on-trust-blocks").locator("li")).toHaveText([
      "Bank balance R418 500,00 + outstanding deposits R5 000,00 − outstanding payments R20 000,00 is R403 500,00, not the cashbook balance R403 510,00.",
      "The trust creditors (R403 500,00) do not equal the cashbook balance at cutover (R403 510,00): difference R10,00. Every creditor group must be included and must agree to the cent.",
    ]);
    await expect(bob.getByTestId("take-on-trust-row-blocks")).toHaveCount(0);
    await expectEquation(bob, "eq1", false, "10,00");
    await expectEquation(bob, "eq2", false, "10,00");
    await expect(bob.getByTestId("take-on-commit")).toHaveCount(0);
    await evidenceShot(bob, "05-trust-r10-block");
  });

  test("6 · clean position: Validated, 0 blocks, every equation R 0,00, one flag; Bob is sent to the Home page sign-off", async () => {
    test.setTimeout(180_000);
    const batchId = state.trustBatch!;
    await fillPosition(bob, POSITION);
    await savePosition(bob, batchId);
    await expectCards(bob, { total: 9, valid: 9, error: 0, duplicate: 0, blocks: 0 });
    await expect(bob.getByTestId("take-on-trust-block-banner")).toHaveCount(0);
    for (const id of ["eq1", "items", "eq2", "investments", "deficit"] as const) {
      await expectEquation(bob, id, true, "0,00");
    }
    await expect(bob.getByTestId("take-on-trust-flags")).toContainText(
      "Unidentified deposit older than one year"
    );
    await expect(bob.getByTestId("take-on-trust-flags").locator("li")).toHaveCount(1);
    await expect(bob.getByTestId("take-on-batch-status")).toContainText("Validated");
    await evidenceShot(bob, "06-trust-validated");
    // D-211: an admin is never offered the sign-off; the owner signs from her Home page
    await bob.getByTestId("take-on-to-sign-off").click();
    await expect(bob.getByTestId("take-on-signoff-status")).toHaveText(
      "A partner or director signs off this take-on from their Home page."
    );
    await expect(bob.getByTestId("takeon-signoff-submit")).toHaveCount(0);
    await expect(bob.getByTestId("take-on-commit")).toHaveCount(0);
  });

  // ── 7. Sign-off (Alice, Home widget) ───────────────────────────────────────────────────────
  test("7 · Alice signs the take-on off from her Home page (TAKEON-SIGNOFF-v1)", async () => {
    test.setTimeout(180_000);
    const batchId = state.trustBatch!;
    await alice.goto(`${BASE}/dashboard`);
    // OBS-657D-3: a transient second copy during load
    const widget = alice.getByTestId("take-on-sign-off-widget").first();
    await expect(widget).toBeVisible({ timeout: 60_000 });
    await expect(widget).toContainText("Trust take-on awaiting your sign-off");
    const item = alice.getByTestId(`take-on-sign-off-item-${batchId}`).first();
    await expect(item).toContainText(`${G.name} at ${D.cLong}`, { timeout: 30_000 });
    await expect(item).toContainText("Prepared by Bob Admin");
    await evidenceShot(alice, "07-alice-home-widget");
    await item.click();
    const sheet = alice.getByTestId("takeon-signoff-sheet");
    await expect(sheet.getByTestId("takeon-signoff-panel")).toBeVisible({ timeout: 60_000 });
    await expect(sheet.getByTestId("takeon-signoff-declaration-version")).toHaveText(
      "TAKEON-SIGNOFF-v1"
    );
    await expect(sheet.getByTestId("takeon-signoff-declaration")).toContainText(
      `I am a partner or director of ${FIRM} responsible for its trust accounting records (Rule 54.19). I have reviewed this take-on of ${G.name} at ${D.cLong}. The trust creditors listed agree, to the cent, with the reconciled trust position in the previous system at that date, and no trust creditor is in debit.`
    );
    await sheet.getByTestId("takeon-signoff-accept").click();
    // R-3: the sign-off sends no email (its own reading, so the commit check in step 8 still
    // names the action that sent one)
    const before = await mailpitTotal();
    await sheet.getByTestId("takeon-signoff-submit").click();
    // D-212: the sheet stays open after signing
    await expect(sheet.getByTestId("takeon-signoff-live")).toContainText(
      /Signed off by you \(Owner\) on .+\./,
      { timeout: 60_000 }
    );
    await expectMailpitUnchanged(before, "trust sign-off");
    await expect(sheet.getByTestId("takeon-signoff-withdraw")).toBeVisible();
    await evidenceShot(alice, "07-alice-signed");
    await alice.keyboard.press("Escape");
    await expect(sheet).toBeHidden({ timeout: 15_000 });
    await expect(alice.getByTestId(`take-on-sign-off-item-${batchId}`)).toHaveCount(0, {
      timeout: 30_000,
    });
  });

  // ── 8–9. Commit, report, downloads ─────────────────────────────────────────────────────────
  test("8 · Bob commits: the trust sentence, the report summary (every difference R 0,00), no email", async () => {
    test.setTimeout(300_000);
    const batchId = state.trustBatch!;
    await openTakeOn(bob, batchId);
    await expect(bob.getByTestId("take-on-signoff-status")).toContainText(
      /Signed off by Alice Owner \(Owner\) on .+\./,
      { timeout: 60_000 }
    );
    const before = await mailpitTotal();
    await commit(bob, {
      title: "Commit trust opening position take-on?",
      body: "9 records will be created. This cannot be undone.",
      trustSentence: `The opening position of ${G.name} at ${D.cLong} will be posted and the account will not accept entries dated on or before that date.`,
    });
    await expect(resultCard(bob, "failed")).toHaveText("0");
    const summary = bob.getByTestId("take-on-report-summary");
    await expect(summary).toBeVisible({ timeout: 60_000 });
    const positionRows = bob.locator('[data-testid^="take-on-report-position-"]');
    expect(await positionRows.count()).toBeGreaterThan(0);
    for (const row of await positionRows.all()) {
      await expect(row.locator("td").last()).toHaveText(randExact("0,00"));
    }
    await expect(bob.getByTestId("take-on-report-creditor-total")).toHaveText(
      randExact("403 500,00")
    );
    const signOff = bob.getByTestId("take-on-report-signoff");
    await expect(signOff).toContainText("Signed off by Alice Owner (Owner)");
    await expect(signOff).toContainText("Prepared by Bob Admin.");
    // FD-31: only a signer who is also the preparer gets that sentence
    await expect(signOff).not.toContainText("Prepared and signed off by the same person");
    await expect(bob.getByTestId("take-on-report-flags")).toContainText(
      "Unidentified deposit older than one year"
    );
    await evidenceShot(bob, "08-trust-results-report");
    await expectMailpitUnchanged(before, "trust commit");
    const batch = await getBatch(batchId);
    expect(batch.status).toBe("COMPLETED");
    expect(batch.reportId, "the take-on report id").toBeTruthy();
    state.reportId = batch.reportId!;
    logEvidence("[trust report]", batch.reportId);
  });

  test("9 · the report files: PDF and CSV from the Results step and from Trust → Reports, byte-identical", async () => {
    test.setTimeout(180_000);
    const downloads = bob.getByTestId("take-on-report-downloads");
    const pdfButton = downloads.getByTestId("take-on-report-download-pdf");
    const csvButton = downloads.getByTestId("take-on-report-download-csv");
    await expect(pdfButton).toBeVisible({ timeout: 90_000 });
    await expect(csvButton).toBeVisible({ timeout: 90_000 });
    const pdf = await download(bob, pdfButton, "results-step");
    const csv = await download(bob, csvButton, "results-step");
    expect(pdf.bytes.subarray(0, 4).toString("latin1")).toBe("%PDF");
    const csvLines = csv.bytes.toString("utf8").split(/\r?\n/);
    expect(csvLines[0]).toBe(`# Trust take-on report — ${G.name} at ${D.cLong}`);
    expect(csvLines).toContain("Section,Group,Client,Matter,Reference,Date,Amount");
    await expect(bob.getByTestId("take-on-report-download-error")).toHaveCount(0);

    await alice.goto(`${BASE}/trust-accounting/reports`);
    const card = alice.getByTestId("trust-take-on-reports");
    await expect(card).toBeVisible({ timeout: 60_000 });
    const item = alice.getByTestId(`trust-take-on-report-${state.reportId}`);
    await expect(item).toContainText(`${G.name} — taken on at ${D.cLong}`);
    await expect(item).toContainText("by Bob Admin");
    await evidenceShot(alice, "09-trust-reports-take-on-card");
    const pdf2 = await download(
      alice,
      item.getByTestId("take-on-report-download-pdf"),
      "reports-card"
    );
    const csv2 = await download(
      alice,
      item.getByTestId("take-on-report-download-csv"),
      "reports-card"
    );
    expect(pdf2.sha256, "PDF from the Reports card = Results step").toBe(pdf.sha256);
    expect(csv2.sha256, "CSV from the Reports card = Results step").toBe(csv.sha256);
  });

  // ── 10. FICA review queue ──────────────────────────────────────────────────────────────────
  test("10 · FICA review queue: the Unrated client first, then by next review date; each with its reason", async () => {
    await alice.goto(`${BASE}/clients?view=system:fica-review-due`);
    await expect(alice.getByText("Clients — FICA review due").first()).toBeVisible({
      timeout: 60_000,
    });
    const queue = ficaQueueOn(todayInSA());
    logEvidence("[fica queue expected]", queue);
    const rows = alice.locator("main table tbody tr");
    await expect(rows).toHaveCount(queue.length, { timeout: 30_000 });
    for (const [i, q] of queue.entries()) {
      const row = rows.nth(i);
      await expect(row.locator('a[href*="/clients/"]').first()).toHaveText(exactText(q.name));
      await expect(row.getByTestId("client-fica-status")).toHaveText("Review due (attested)");
      // the complete reason list, exactly (an extra reason fails)
      await expect(row.getByTestId("client-fica-review-reasons")).toHaveText(exactText(q.reasons));
    }
    await evidenceShot(alice, "10-fica-review-queue");
  });

  // ── 11. Debtors ────────────────────────────────────────────────────────────────────────────
  test("11 · debtor opening balances: exact errors and warnings, commit from Validation, quiet SENT fee notes", async () => {
    test.setTimeout(300_000);
    await uploadFirst(bob, {
      kebab: "debtor-balances",
      batchName: `Take-on debtors at ${D.c}`,
      file: renderFixture("debtors.csv", D),
      cutover: D.c,
    });
    await mapAndSave(bob, {
      clientRef: "Client reference",
      matterRef: "Matter reference",
      feeNoteNumber: "Fee note number",
      issueDate: "Issue date",
      dueDate: "Due date",
      outstandingAmount: "Outstanding amount",
      currency: "Currency",
      description: "Description",
      sourceRef: "Previous system ID",
    });
    await runDryRun(bob);
    await expectCards(bob, { total: 11, valid: 6, error: 5, duplicate: 0, blocks: 0 });
    for (const n of [2, 3, 4, 5, 6, 7]) {
      await expect(bob.getByTestId(`take-on-row-status-${n}`)).toHaveText("Valid");
    }
    await expect(bob.getByTestId("take-on-row-4-due-date-default")).toHaveText("Default");
    await expect(rowMessages(bob, 4)).toContainText([
      `No due date was given. It is set to ${longDate(D.cPlus(20))}, the issue date plus the firm's payment terms of 30 days.`,
    ]);
    await expect(rowMessages(bob, 6)).toContainText([
      "Kazi's own fee-note numbers will continue after INV-0007.",
    ]);
    await expect(bob.getByTestId("take-on-row-7-details-warning")).toHaveText(
      "Address or email missing"
    );
    await expect(rowMessages(bob, 7)).toContainText([
      "This client has no billing address or email in Kazi. The brought-forward fee note is taken on as it is; add them before generating a new fee note for this client.",
    ]);
    const errors: [number, string][] = [
      [8, "No client matches C-098. Take the client on first, or correct the reference."],
      [9, "The amount must be more than R0,00."],
      [
        10,
        `The date (${longDate(D.cPlus(10))}) is after the cutover date (${D.cLong}). Take on only what the previous system held at the cutover date.`,
      ],
      [11, "Matter TO-D-2001 is not a matter of client C-006."],
      [12, "Fee-note number INV-0001 already exists in Kazi"],
    ];
    for (const [n, message] of errors) {
      await expect(bob.getByTestId(`take-on-row-status-${n}`)).toHaveText("Error");
      await expect(rowMessages(bob, n)).toContainText([message]);
    }
    await evidenceShot(bob, "11-debtors-validation");
    await filterRows(bob, "Error", [8, 9, 10, 11, 12]);

    const before = await mailpitTotal();
    await commit(bob, {
      title: "Commit debtor opening balances take-on?",
      body: "6 records will be created. This cannot be undone.",
    });
    await expect(resultCard(bob, "created")).toHaveText("6");
    await expect(resultCard(bob, "failed")).toHaveText("0");
    await evidenceShot(bob, "11-debtors-results");
    await expectMailpitUnchanged(before, "debtors commit");

    // The fee-note list: each brought-forward note SENT with its source number and the badge
    await bob.goto(`${BASE}/invoices`);
    for (const d of DEBTORS) {
      const row = bob.locator("main table tbody tr").filter({ hasText: d.number });
      await expect(row, `fee note ${d.number}`).toHaveCount(1, { timeout: 30_000 });
      await expect(row.getByTestId("fee-note-takeon-badge")).toHaveText(
        "Brought forward — take-on"
      );
      await expect(row).toContainText("Sent");
      // The amount cell (a row-wide match would run into the due date's "…2026" before it)
      await expect(row.locator("td").filter({ hasText: randExact(d.amount) })).toHaveCount(1);
    }
    // The seeded ordinary note is listed and carries no badge
    const seeded = bob.locator("main table tbody tr").filter({ hasText: "INV-0001" });
    await expect(seeded, "seeded fee note INV-0001").toHaveCount(1, { timeout: 30_000 });
    await expect(seeded.getByTestId("fee-note-takeon-badge")).toHaveCount(0);
    await evidenceShot(bob, "11-fee-note-list");
  });

  // ── 12–13. Collections silent; the debtor book (CashDigest DEFERRED) ───────────────────────
  test("12–13 · collections reminders off; the debtor book lists the taken-on debtors in their buckets (CashDigest DEFERRED)", async () => {
    await bob.goto(`${BASE}/settings/fees?tab=collections`);
    const toggle = bob.locator("#collections-toggle");
    await expect(toggle).toBeVisible({ timeout: 60_000 });
    await expect(toggle).toHaveAttribute("data-state", "unchecked");
    await evidenceShot(bob, "12-collections-settings-off");

    await bob.goto(`${BASE}/invoices/collections`);
    await expect(bob.getByRole("heading", { name: "Debtor book" })).toBeVisible({
      timeout: 60_000,
    });
    // Today as the page was rendered (not D.today: a run may cross an SA midnight before here)
    const today = todayInSA();
    logEvidence("[debtor book today]", today);
    for (const d of DEBTORS) {
      const row = bob.locator("main table tbody tr").filter({
        has: bob.getByRole("link", { name: d.client, exact: true }),
      });
      await expect(row, `debtor book row for ${d.client}`).toHaveCount(1, { timeout: 30_000 });
      const cells = row.locator("td");
      await expect(cells.nth(1)).toHaveText(randExact(d.amount));
      // Current · 30d · 60d · 90d+ are cells 3–6; the note's bucket by today's SA date
      const bucket = bucketOf(D.cPlus(d.due), today);
      for (let b = 0; b < 4; b++) {
        await expect(cells.nth(3 + b), `${d.client} bucket ${b}`).toHaveText(
          randExact(b === bucket ? d.amount : "0,00")
        );
      }
    }
    await evidenceShot(bob, "12-debtor-book");
    test.info().annotations.push({
      type: "DEFERRED",
      description:
        "CashDigest (B-3 / D-261): no product trigger — CashDigestScheduler runs only at 07:00 on Mondays and no endpoint enqueues it. The debtor book above is the substitute observation; the digest itself is a gap-report finding (owner: product, needs its own PR).",
    });
  });

  // ── 14. Dormancy (job DEFERRED) ────────────────────────────────────────────────────────────
  test("14 · dormancy check: no dormant clients; every imported client still Active (job run DEFERRED)", async () => {
    await alice.goto(`${BASE}/compliance`);
    const check = alice.getByRole("button", { name: "Check for Dormant Clients" });
    await expect(check).toBeVisible({ timeout: 60_000 });
    await check.click();
    await expect(alice.getByText("No dormant clients detected.")).toBeVisible({ timeout: 30_000 });
    await evidenceShot(alice, "14-dormancy-check");
    const clients = await apiOk<{ name: string; lifecycleStatus: string }[]>("/api/clients");
    for (const name of CREATED_CLIENTS) {
      expect(clients.find((c) => c.name === name)?.lifecycleStatus, name).toBe("ACTIVE");
    }
    await alice.goto(`${BASE}/clients`);
    for (const name of ["Amahle Zulu", "Naledi Khoza", "Mooi River Family Trust", "Bheki Zondo"]) {
      const row = alice.locator("main table tbody tr").filter({
        has: alice.getByRole("link", { name, exact: true }),
      });
      await expect(row).toContainText("Active", { timeout: 30_000 });
    }
    test.info().annotations.push({
      type: "DEFERRED",
      description:
        "Dormancy job (B-2 / D-260): no product trigger for executeDormancyTransitions() — DormancyScheduledJob runs only at 02:00 and the platform-admin jobs API only lists/retries/deletes. The read-only check above is the substitute observation; the job run is a gap-report finding (owner: product, needs its own PR).",
    });
  });

  // ── 15. The first post-cutover month ──────────────────────────────────────────────────────
  test("15 · first month on G: statement imported, cutover items auto-matched, deposit / payment / interest / fee recorded, balanced, list locked", async () => {
    test.setTimeout(300_000);
    const m = D.month;
    const before = await mailpitTotal();
    await alice.goto(`${BASE}/trust-accounting/reconciliation/new`);
    await expect(alice.getByTestId("step-select")).toBeVisible({ timeout: 30_000 });
    await selectNativeOption(
      alice.getByTestId("account-select"),
      `${G.name} (${BANK} - ${G.number})`
    );
    await alice.getByTestId("period-end-input").fill(m.d(31));
    await alice.getByTestId("step-next-btn").click();
    const upload = alice.getByTestId("bank-statement-upload");
    await expect(upload).toBeVisible({ timeout: 15_000 });
    await upload.getByTestId("file-input").setInputFiles(firstMonthStatementCsv(D, G.number));
    const result = alice.getByTestId("upload-result");
    await expect(result).toContainText("Statement imported successfully", { timeout: 30_000 });
    await expect(result).toContainText("Lines imported: 6");
    await expect(result).toContainText(`Period: ${m.d(1)} to ${m.d(31)}`);
    // No line on or before C (FD-32)
    await expect(alice.getByTestId("statement-import-warnings")).toHaveCount(0);
    await expect(alice.getByTestId("upload-error")).toHaveCount(0);
    await evidenceShot(alice, "15-recon-upload");
    await alice.getByTestId("step-next-btn").click();

    const step3 = alice.getByTestId("step-auto-match");
    await expect(step3).toContainText("6 bank statement lines ready to match");
    await step3.getByTestId("auto-match-btn").click();
    const matched = alice.getByTestId("auto-match-result");
    await expect(matched).toContainText("Auto-matched 2 lines", { timeout: 30_000 });
    await expect(matched).toContainText("4 lines remaining for manual review");
    await step3.getByTestId("step-next-btn").click();
    await expect(alice.getByTestId("reconciliation-split-pane")).toBeVisible({ timeout: 30_000 });
    await expect(alice.getByTestId("balance-summary")).toBeVisible({ timeout: 30_000 });
    await expect(bankLine(alice, "Opening Balance")).toHaveCount(0);
    // FD-3: the take-on's deposit in transit and unpresented cheque join the matching pool
    for (const d of ["EFT B-0001", "PAY-778"]) {
      await expect(bankLine(alice, d)).not.toContainText("Unmatched");
    }
    await evidenceShot(alice, "15-recon-after-auto-match");

    // Option B (D-259): the deposit as an unidentified deposit, the payment as the Fund sweep
    const record: [string, string, string, boolean][] = [
      ["CASH DEPOSIT BRANCH 0045", "UNIDENTIFIED_DEPOSIT", "Unidentified deposit", false],
      [
        "LPFF INTEREST TRANSFER",
        "FUND_SWEEP",
        "Interest paid to the Legal Practitioners' Fidelity Fund",
        false,
      ],
      [
        "INTEREST",
        "FUND_INTEREST",
        "Interest received for the Legal Practitioners' Fidelity Fund",
        false,
      ],
      ["SERVICE FEE", "BANK_CHARGE", "Bank charge", true],
    ];
    for (const [description, classification, label, charge] of record) {
      const line = bankLine(alice, description);
      await expect(line).toHaveCount(1);
      await expect(line).toContainText("Unmatched");
      const lineId = await idFromTestId(line, "bank-line-");
      await line.click();
      await alice.getByTestId(`record-btn-${lineId}`).click();
      const dialog = alice.getByTestId("record-from-statement-dialog");
      await expect(dialog).toBeVisible();
      await dialog.locator(`label[for="classification-${classification}"]`).click();
      await expect(dialog.locator(`#classification-${classification}`)).toBeChecked();
      if (charge) {
        await dialog.locator('label[for="charge-recoverable-yes"]').click();
        await dialog.getByTestId("charge-vat-input").fill("0");
      }
      await dialog.getByTestId("record-submit").click();
      await expect(dialog).toBeHidden({ timeout: 30_000 });
      await expect(alice.getByTestId(`recorded-badge-${lineId}`)).toHaveText(`Recorded: ${label}`, {
        timeout: 30_000,
      });
      await expect(alice.getByTestId(`unmatch-btn-${lineId}`)).toBeEnabled({ timeout: 30_000 });
    }

    // EQ-1: bank = adjusted bank = cashbook; EQ-2: cashbook = trust creditors (no adjustment)
    const bank = alice.getByTestId("comparison-bank-cashbook");
    await expect(ddFor(bank, "Bank balance")).toHaveText(randExact("403 477,40"), {
      timeout: 30_000,
    });
    await expect(ddFor(bank, "+ Outstanding deposits")).toHaveText(randExact("0,00"));
    await expect(ddFor(bank, "− Outstanding payments")).toHaveText(randExact("0,00"));
    await expect(ddFor(bank, "= Adjusted bank balance")).toHaveText(randExact("403 477,40"));
    await expect(ddFor(bank, "Cashbook")).toHaveText(randExact("403 477,40"));
    await expect(alice.getByTestId("bank-to-cashbook-difference")).toHaveText(randExact("0,00"));
    const books = alice.getByTestId("comparison-cashbook-creditors");
    await expect(ddFor(books, "Trust creditors")).toHaveText(randExact("403 477,40"));
    await expect(alice.getByTestId("cashbook-to-creditors-difference")).toHaveText(
      randExact("0,00")
    );
    const creditors = alice.getByTestId("creditor-breakdown");
    await expect(ddFor(creditors, "Client ledgers")).toHaveText(randExact("499 500,00"));
    await expect(
      ddFor(creditors, "Legal Practitioners' Fidelity Fund — interest and charges")
    ).toHaveText(randExact("227,40"));
    await expect(ddFor(creditors, "Unidentified deposits")).toHaveText(randExact("3 750,00"));
    await expect(ddFor(creditors, "Placed in section 86(3) investment accounts")).toHaveText(
      randExact("-100 000,00")
    );
    await expect(alice.getByTestId("unresolved-line-count")).toHaveText(
      "0 statement lines are neither matched, recorded nor excluded"
    );
    // D-221: the statement opens at the take-on bank balance — no OPENING_DIFFERS flag; the one
    // warning is the year-old unidentified deposit taken on at C
    const flagCodes = await alice
      .locator('[data-testid^="reconciliation-flag-"]')
      .evaluateAll((els) =>
        els.map((e) => (e.getAttribute("data-testid") ?? "").replace("reconciliation-flag-", ""))
      );
    logEvidence("[recon flags]", flagCodes);
    expect(flagCodes).toEqual(["UNIDENTIFIED_OVER_ONE_YEAR"]);
    await expect(alice.getByTestId("reconciliation-flag-UNIDENTIFIED_OVER_ONE_YEAR")).toContainText(
      "Unidentified deposit held over a year"
    );
    await expect(alice.getByTestId("balance-summary")).toContainText("Reconciliation is balanced");
    await evidenceShot(alice, "15-recon-balanced");

    const complete = alice.getByTestId("complete-reconciliation-btn");
    await expect(complete).toBeEnabled();
    await complete.click();
    const done = alice.getByTestId("step-complete");
    await expect(done).toBeVisible({ timeout: 30_000 });
    const locked = done.getByTestId("monthly-list-locked");
    await expect(locked).toContainText(`${m.monthName} list locked —`);
    await expect(locked).toContainText(randIn("403 477,40"));
    await expect(locked).toContainText("agrees with the cashbook and the adjusted bank balance.");
    await evidenceShot(alice, "15-recon-complete");
    await expectMailpitUnchanged(before, "first-month reconciliation");
  });

  // ── 16. Receipts & Payments over C ─────────────────────────────────────────────────────────
  test("16 · Receipts & Payments on G over C: one brought-forward line R 403 500,00 and its footnote", async () => {
    await runReport(alice, BASE, "trust-receipts-payments", {
      dates: { dateFrom: `${D.c.slice(0, 8)}01`, dateTo: D.month.d(31) },
      trustAccount: G.name,
    });
    const bf = alice.getByTestId("report-row-brought-forward");
    await expect(bf).toHaveCount(1);
    await expect(bf).toContainText("Balance brought forward — take-on");
    // Each figure in its own column, found by its header (TrustReportPackSeeder
    // trustReceiptsPaymentsDefinition: … Credit · Debit · Balance): the amount is the Credit, the
    // Debit is nil, and the running balance opens at the same figure
    const heads = (
      await alice.locator("main table").first().locator("thead th").allTextContents()
    ).map((h) => h.trim());
    const cell = (header: string) => {
      const i = heads.indexOf(header);
      expect(
        i,
        `Receipts & Payments column "${header}" (headers ${heads.join(" | ")})`
      ).toBeGreaterThanOrEqual(0);
      return bf.locator("td").nth(i);
    };
    await expect(cell("Credit")).toHaveText(randExact("403 500,00"));
    await expect(cell("Debit")).toHaveText(randExact("0,00"));
    await expect(cell("Balance")).toHaveText(randExact("403 500,00"));
    await expect(alice.getByTestId("report-footnote-brought-forward")).toContainText(
      `The opening position taken on at ${D.cLong} is shown as one brought-forward line, not as receipts.`
    );
    await evidenceShot(alice, "16-receipts-payments");
  });

  // ── 17. The whole run sent no email ────────────────────────────────────────────────────────
  test("17 · Mailpit over the whole run: total and latest message unchanged since step 0 (R-3)", async () => {
    expect(state.mailpitStart, "step 0 read Mailpit").toBeDefined();
    await expectMailpitUnchanged(state.mailpitStart!, "whole run");
  });
});
