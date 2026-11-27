// 670A M2 target lists per group (brief M-4, M2 scope). Ids from ../ids.json (M1 keys + "m2", written
// by m2-discover.cjs [read-only GETs] and m2-data.cjs [UI-created SENT-overdue fee note + fee run]).
// Groups: fees (fee-note lists + fee runs), feenote (detail per status), trust, sites (663A / 663B),
// sec8 (668A), sec9 (669A / 669B light pass). Every target: Alice + Carol × 1024/1440 × light/dark
// unless noted. Interactions (act) only click tabs or scroll an element into view; nothing is saved
// (the timesheet is opened, never saved — F-668A-R1).
const L = require("./lib.cjs");

const B = L.BASE;
const AC = { alice: L.V4, carol: L.V4 };
const A = { alice: L.V4 };
// Carol (member, no INVOICING) gets the in-app "Page not found" (HTTP 404) on the fee-note list,
// collections, unbilled and every fee-note detail: the designed denied branch (666A/666B before +
// after READMEs: "Carol: HTTP 404, h1 Page not found … same as before").
const C404 = { expect404: ["carol"] };
const ids = L.readIds();
const m = ids.m2 || {};
const inv = m.invoices || {};

const clickTab = (name) => async (page) => {
  const t = page.locator("#main-content").getByRole("tab", { name, exact: true });
  if (!(await t.count())) return `tab "${name}" absent`;
  await t.first().click();
  await page.waitForTimeout(1500);
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  const sel = await page.locator('#main-content [role="tab"][aria-selected="true"]').allTextContents();
  return `tab clicked "${name}"; selected ${JSON.stringify(sel.map((s) => s.trim()))}`;
};
const scrollTo = (sel, label) => async (page) => {
  const el = page.locator(sel).first();
  if (!(await el.count())) return `${label}: not present`;
  await el.scrollIntoViewIfNeeded();
  await page.waitForTimeout(600);
  const box = await el.boundingBox();
  return `${label}: scrolled into view (${box ? `${Math.round(box.width)}×${Math.round(box.height)} at y ${Math.round(box.y)}` : "no box"})`;
};
const taskSheet = (tab) => async (page) => {
  const dlg = page.getByRole("dialog");
  await dlg.first().waitFor({ timeout: 15000 }).catch(() => {});
  if (!(await dlg.count())) return "task sheet did not open";
  if (tab) {
    const t = dlg.getByRole("tab", { name: tab, exact: true });
    if (!(await t.count())) return `sheet tab "${tab}" absent`;
    await t.first().click();
    await page.waitForTimeout(1500);
  }
  const sel = await dlg.locator('[role="tab"][aria-selected="true"]').allTextContents();
  return `task sheet open; selected ${JSON.stringify(sel.map((s) => s.trim()))}`;
};

const t = (key, surface, url, personas = AC, extra = {}) => ({ key, surface, url: url.startsWith("http") ? url : `${B}${url}`, personas, ...extra });

const GROUPS = {
  fees: {
    targets: [
      t("invoices", "Fee notes › list (status pills)", "/invoices", AC, C404),
      t("billing-runs", "Fee notes › fee runs list", "/invoices/billing-runs"),
      t("billing-runs-detail", "Fee notes › fee run detail (PREVIEW, UI-created)", `/invoices/billing-runs/${m.billingRunId}`),
      t("billing-runs-new", "Fee notes › new fee run", "/invoices/billing-runs/new"),
      t("collections", "Fee notes › collections (Debtors)", "/invoices/collections", AC, C404),
      t("unbilled", "Fee notes › unbilled", "/invoices/unbilled", AC, C404),
    ],
  },
  feenote: {
    targets: [
      ["DRAFT", "draft"], ["APPROVED", "approved"], ["SENT", "sent"], ["SENT_OVERDUE", "sent-overdue"],
      ["PARTIALLY_PAID", "partially-paid"], ["PAID", "paid"], ["VOID", "void"],
    ].map(([s, k]) => t(`feenote-${k}`, `Fee note detail › ${s.replace("_", " ")}${inv[s] && inv[s].number ? ` (${inv[s].number})` : ""}`, `/invoices/${inv[s] && inv[s].id}`, AC, C404)),
  },
  trust: {
    targets: [
      t("trust-home", "Trust › home", "/trust-accounting"),
      t("trust-transactions", "Trust › transactions", "/trust-accounting/transactions"),
      t("trust-client-ledgers", "Trust › client ledgers", "/trust-accounting/client-ledgers"),
      t("trust-client-ledger-detail", `Trust › client ledger detail (${m.trustLedgerClientName || "?"})`, `/trust-accounting/client-ledgers/${m.trustLedgerClientId}`),
      t("trust-investments", "Trust › investments", "/trust-accounting/investments"),
      t("trust-other-creditors", "Trust › other creditors", "/trust-accounting/other-creditors"),
      t("trust-reconciliation", "Trust › reconciliation", "/trust-accounting/reconciliation"),
      t("trust-reconciliation-new", "Trust › reconciliation › new", "/trust-accounting/reconciliation/new"),
      t("trust-reports", "Trust › reports", "/trust-accounting/reports"),
    ],
  },
  sites: {
    targets: [
      t("matter-fields", "663A › matter Fields tab", `/matters/${m.matterId}?tab=fields`),
      t("client-fields", "663A › client Fields tab", `/clients/${m.clientId}?tab=fields`),
      t("feenote-custom-fields", "663A › fee-note custom fields (DRAFT detail, section scrolled into view)", `/invoices/${inv.DRAFT && inv.DRAFT.id}`, A, { act: scrollTo('[data-testid="custom-field-section"], #main-content [data-slot="empty-state"]:has-text("No custom fields configured")', "custom-fields section / inline empty") }),
      t("task-sheet-time", "663A › task Sheet › Time entries", `/matters/${m.matterId}?tab=tasks&taskId=${m.taskId}`, AC, { sheet: "task-sheet", act: taskSheet(null) }),
      t("task-sheet-comments", "663A › task Sheet › Comments", `/matters/${m.matterId}?tab=tasks&taskId=${m.taskId}`, AC, { sheet: "task-sheet", act: taskSheet("Comments") }),
      // My Tasks is a fee-earner-audience widget (lib/dashboard-home.ts: tasks: audience === "feeEarner"),
      // so Alice's (owner) home has none — her row shows the owner home as observed.
      t("dashboard-my-tasks", "663A › dashboard My Tasks widget (scrolled into view)", "/dashboard", AC, { act: scrollTo('[data-testid="my-tasks-widget"]', "my-tasks-widget") }),
      t("matter-activity", "663B › matter Activity", `/matters/${m.matterId}?tab=activity`),
      t("matter-budget", "663B › matter Budget", `/matters/${m.matterId}?tab=budget`),
      t("matter-clients", "663B › matter Clients panel", `/matters/${m.matterId}?tab=clients`),
      t("client-rates", "663B › client Rates", `/clients/${m.clientId}?tab=rates`),
      t("matter-documents", "663B › matter Documents", `/matters/${m.matterId}?tab=documents`),
      t("matter-time", "663B › matter Time summary", `/matters/${m.matterId}?tab=time`),
    ],
  },
  sec8: {
    targets: [
      t("diary", "Section 8 › Diary", "/diary"),
      t("my-work", "Section 8 / 663A › My Work", "/my-work"),
      t("my-work-timesheet", "Section 8 › My Work › Timesheet (opened, never saved)", "/my-work/timesheet"),
      t("notifications", "Section 8 › Notifications", "/notifications"),
    ],
  },
  sec9: {
    targets: [
      // Carol on /compliance: HTTP 404 "Page not found" = designed (669A after README: "Carol /compliance | HTTP 404 … same | PASS").
      t("compliance-overview", "Section 9 › Compliance › Overview", "/compliance", AC, { sheet: "compliance", ...C404 }),
      t("compliance-ai-audit", "Section 9 › Compliance › AI Audit", "/compliance", AC, { sheet: "compliance", act: clickTab("AI Audit"), ...C404 }),
      t("compliance-requests", "Section 9 › Compliance › requests", "/compliance/requests"),
      t("compliance-request-detail", "Section 9 › Compliance › request detail", `/compliance/requests/${m.dataRequestId}`),
      t("conflict-check", "Section 9 › Conflict check", "/conflict-check"),
      t("adverse-parties", "Section 9 › Adverse parties", "/legal/adverse-parties"),
      t("disbursements", "Section 9 › Disbursements", "/legal/disbursements"),
      t("disbursement-detail", "Section 9 › Disbursement detail", `/legal/disbursements/${m.disbursementId}`),
      t("tariffs", "Section 9 › Tariffs", "/legal/tariffs"),
      t("information-request-detail", `Section 9 › Information request detail (${m.informationRequestNumber || "?"})`, `/information-requests/${m.informationRequestId}`),
      t("team-members", "Section 9 › Team › Members", "/team", AC, { sheet: "team" }),
      t("team-invitations", "Section 9 › Team › Pending Invitations", "/team", AC, { sheet: "team", act: clickTab("Pending Invitations") }),
      t("ai-reviews-pending", "Section 9 / 663A › AI reviews › Pending", "/ai/reviews", AC, { sheet: "ai-reviews" }),
      t("ai-reviews-history", "Section 9 / 663A › AI reviews › History", "/ai/reviews", AC, { sheet: "ai-reviews", act: clickTab("History") }),
    ],
  },
};

module.exports = { GROUPS };
