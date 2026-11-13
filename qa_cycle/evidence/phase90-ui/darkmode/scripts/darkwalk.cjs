// 644B task 644.6: dark-mode long-tail walk. Every long-tail page under org/[slug] (brief route
// inventory: 49 page files − the /settings redirect; the 5 settings family hosts are walked per
// ?tab= panel from frontend/lib/settings-nav.ts) as the owner (Thandi), dark, 1440×900.
// Read-only: pages are opened, nothing is clicked, submitted or saved.
// Per page: screenshot (full page, clipped at 3200px), the in-page audit from common-kc.cjs
// (low-contrast text, light surfaces on a dark page, faint form-field borders), console messages.
const path = require("path");
const fs = require("fs");
const { chromium, ORG, session, setTheme, setWidth, pageAudit, writeJson } = require("../../matrix/scripts/common-kc.cjs");
const OUT = path.resolve(__dirname, "..");

// Real ids on the KC tenant (read-only SELECTs, 2026-11-13; see darkmode/README.md).
const ID = {
  dsr: "e1a3adfe-027b-4a74-9540-5bc1000f7180", infoReq: "de4cf6b8-18da-4f62-b57c-4030508a8b52", invoice: "76b170cc-164b-4f63-a88e-aa1bef24b2d6",
  disb: "dcd7d88f-b7d5-40eb-ade9-9909a5a84f39", report: "timesheet", docTpl: "b15cc653-3e09-452b-ade4-be34363b0877",
  checklist: "56bafb4e-939f-43d6-ae33-1f6efa1c46a2", reqTpl: "866c7b1d-10f3-469c-9e94-0bc237812fe1", matterTpl: "af0d2286-dea1-4112-a5ba-974ad99a690b",
  client: "713a1f9a-9cd4-406e-920e-507ac449c98a",
};
const PAGES = [
  "/ai/reviews", "/compliance", "/compliance/requests", `/compliance/requests/${ID.dsr}`, "/conflict-check", "/diary",
  `/information-requests/${ID.infoReq}`, "/invoices", `/invoices/${ID.invoice}`, "/invoices/billing-runs", "/invoices/billing-runs/new",
  "/invoices/collections", "/invoices/unbilled", "/legal/adverse-parties", "/legal/disbursements", `/legal/disbursements/${ID.disb}`,
  "/legal/tariffs", "/matters/new", "/my-work", "/my-work/timesheet", "/notifications", `/reports/${ID.report}`,
  "/settings/connections/ai/history", "/settings/connections/automations/executions", "/settings/connections/xero/sync-log",
  `/settings/documents/templates/${ID.docTpl}/edit`, "/settings/documents/templates/new",
  `/settings/matters/checklists/${ID.checklist}`, `/settings/matters/checklists/${ID.checklist}/edit`, "/settings/matters/checklists/new",
  `/settings/matters/request-templates/${ID.reqTpl}`, "/settings/matters/request-templates/new", `/settings/matters/templates/${ID.matterTpl}`,
  "/team", "/trust-accounting/client-ledgers", `/trust-accounting/client-ledgers/${ID.client}`, "/trust-accounting/interest",
  "/trust-accounting/investments", "/trust-accounting/reconciliation", "/trust-accounting/reconciliation/new", "/trust-accounting/reports",
  "/trust-accounting/transactions",
];
const TABS = {
  firm: ["general", "roles", "audit-trail", "data-protection", "data-requests", "compliance", "features", "subscription"],
  matters: ["templates", "naming", "custom-fields", "tags", "checklists", "request-templates", "request-settings"],
  documents: ["templates", "clauses", "acceptance", "email"],
  fees: ["tariffs", "tax", "time-recording", "fee-runs", "collections", "trust-accounting"],
  connections: ["xero", "mcp", "ai", "notifications", "packs", "automations"],
};
for (const [fam, tabs] of Object.entries(TABS)) for (const t of tabs) PAGES.push(`/settings/${fam}?tab=${t}`);
const slug = (p) => p.replace(/^\//, "").replace(/\?tab=/, "--").replace(/[0-9a-f]{8}-[0-9a-f-]{27}/g, "id").replace(/[/?=&]+/g, "-");

(async () => {
  const browser = await chromium.launch(); const consoleLog = []; const walk = []; const t0 = Date.now(); let probeCaptured = null;
  try {
    const s = await session(browser, "thandi", { consoleSink: consoleLog });
    await setTheme(s, "dark"); await setWidth(s, 1440);
    // Console-sink probe: proves the listener records warnings (so an empty list is a real zero).
    s.state.label = "probe"; await s.page.evaluate(() => console.warn("644B console-sink probe")); await s.page.waitForTimeout(300);
    probeCaptured = consoleLog.some((c) => c.text.includes("644B console-sink probe"));
    for (const p of PAGES) {
      const name = slug(p); s.state.label = name;
      const rec = { route: p, file: `${name}-1440-dark.png` };
      try {
        const resp = await s.page.goto(ORG + p, { waitUntil: "load", timeout: 180000 });
        rec.status = resp ? resp.status() : null;
        await s.page.locator("main h1, main h2, [role=tabpanel]").first().waitFor({ timeout: 45000 }).catch(() => { rec.noHeading = true; });
        await s.page.mouse.move(1, 1);
        await s.page.waitForTimeout(3500);
        const h = await s.page.evaluate(() => document.documentElement.scrollHeight);
        await s.page.screenshot({ path: path.join(OUT, rec.file), fullPage: true, clip: { x: 0, y: 0, width: 1440, height: Math.min(h, 3200) } });
        Object.assign(rec, await s.page.evaluate(() => ({
          url: location.pathname.replace(/^\/org\/[^/]+/, "") + location.search,
          h1: document.querySelector("main h1")?.textContent?.trim() ?? null,
          selectedTab: [...document.querySelectorAll('[role="tab"][aria-selected="true"], [role="tab"][data-state="active"]')].map((t) => t.textContent.trim()).join(" | ") || null,
          dark: document.documentElement.classList.contains("dark"), docHeight: document.documentElement.scrollHeight,
          errorText: /Something went wrong|could not be found|Application error|Unhandled Runtime Error/i.test(document.body.innerText.slice(0, 4000)) ? document.body.innerText.slice(0, 200).replace(/\s+/g, " ") : null,
        })));
        rec.audit = await s.page.evaluate(pageAudit);
      } catch (e) { rec.error = String(e).slice(0, 300); }
      walk.push(rec);
      const a = rec.audit || {};
      console.log(`${name} ${rec.status} dark=${rec.dark} low=${a.lowTextCount} light=${a.lightSurfaceCount} faint=${a.faintFieldCount} ${rec.error ? "ERR " + rec.error.slice(0, 80) : ""} ${Math.round((Date.now() - t0) / 1000)}s`);
    }
    await s.ctx.close();
  } finally {
    writeJson(OUT, "walk.json", { capturedAt: new Date().toISOString(), persona: "owner-thandi", theme: "dark", viewport: "1440x900", probeCaptured, ids: ID, walk });
    writeJson(OUT, "console.json", consoleLog);
    await browser.close();
  }
  console.log("DONE", walk.length, "pages");
})().catch((e) => { console.error("OBS FAILED", e); process.exit(1); });
