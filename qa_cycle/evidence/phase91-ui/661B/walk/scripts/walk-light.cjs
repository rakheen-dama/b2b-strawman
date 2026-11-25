// 661B task 661.13: whole-app light walk on the 661B head. Every org/[slug] route (enumerated at run
// time from frontend/app/(app)/org/[slug]/**/page.tsx), all 32 settings tabs (?tab= ids parsed from
// frontend/lib/settings-nav.ts) and the 11 settings sub-pages, as Alice (owner), 1440x900, light,
// rail expanded. Read-only: pages are opened, nothing is clicked, submitted or saved (dynamic ids are
// read from the API or harvested from the parent page's links).
// Per page: full-page screenshot (clipped at 3200 px) into RAW (out of the tree), the light audit
// (./audit.cjs: as rendered vs the pre-scheme counterfactual), console errors/warnings/hydration.
// Then contact sheets (6 pages each) into OUT.
// Usage (worktree root): node qa_cycle/evidence/phase91-ui/661B/walk/scripts/walk-light.cjs qa_cycle/evidence/phase91-ui/661B/walk
const path = require("path");
const fs = require("fs");
const { W, chromium, APP, ORG, rel, idpToken, api, list, loginState, newCtx, sheet } = require("./common.cjs");
const { lightAudit } = require("./audit.cjs");

const OUT = path.resolve(process.argv[2] || "/tmp/661b-walk");
const RAW = process.env.RAW || "/tmp/661b-walk-raw";
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${APP}/org/${ORG}`;
const ROOT = path.join(W, "frontend/app/(app)/org/[slug]");

// ---- Route inventory from the file system.
function pages(dir, pre = "") {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.isDirectory()) out.push(...pages(path.join(dir, e.name), `${pre}/${e.name}`));
    else if (e.name === "page.tsx") out.push(pre || "/");
  }
  return out;
}
const ROUTES = pages(ROOT)
  .map((r) => r.replace(/\/\([^)]+\)/g, "")) // route groups do not appear in the URL
  .sort();
// ---- Settings tabs from the one nav source.
const navSrc = fs.readFileSync(path.join(W, "frontend/lib/settings-nav.ts"), "utf8");
const TABS = [];
{
  let fam = null;
  for (const line of navSrc.split("\n")) {
    const f = line.match(/^ {4}id: "([\w-]+)",/);
    if (f) fam = f[1];
    const t = line.match(/^ {8}id: "([\w-]+)",/);
    if (t && fam) TABS.push([fam, t[1]]);
  }
}
const FAMILY_HOSTS = new Set(["/settings/firm", "/settings/matters", "/settings/documents", "/settings/fees", "/settings/connections"]);
const isSub = (r) => r.startsWith("/settings/") && !FAMILY_HOSTS.has(r);

const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
const consoleMsgs = [];
const cur = { label: "login" };

(async () => {
  const browser = await chromium.launch();
  const state = await loginState(browser, "alice");
  const token = await idpToken("alice");
  const ctx = await newCtx(browser, state, { width: 1440, height: 900, theme: "light", extra: { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" } });
  const page = await ctx.newPage();
  page.on("console", (m) => {
    if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(m.text())) consoleMsgs.push({ label: cur.label, type: m.type(), text: m.text().slice(0, 300) });
  });
  page.on("pageerror", (e) => consoleMsgs.push({ label: cur.label, type: "pageerror", text: String(e).slice(0, 300) }));

  // ---- Resolve dynamic segments with real ids (API reads, or links harvested from the parent page).
  const harvest = async (parent, re) => {
    await page.goto(BASE + parent, { waitUntil: "load" });
    await page.locator("#main-content h1").first().waitFor({ timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(1500);
    const hrefs = await page.evaluate(() => [...document.querySelectorAll("a[href]")].map((a) => a.getAttribute("href")));
    const m = hrefs.map((h) => h && h.split(/[?#]/)[0].match(re)).find(Boolean);
    return m ? m[1] : null;
  };
  const safe = async (f) => {
    try {
      return await f();
    } catch (e) {
      return null;
    }
  };
  const matters = list(await api(token, "/api/matters"));
  const matter = matters.filter((p) => p.status === "ACTIVE" && p.clientName).sort((x, y) => (y.trustBalance || 0) - (x.trustBalance || 0))[0] || matters[0];
  const invoices = list(await api(token, "/api/invoices"));
  const ID = {
    matter: matter && matter.id,
    invoice: (invoices.find((i) => i.status === "DRAFT") || invoices[0] || {}).id,
    infoReq: await safe(async () => list(await api(token, "/api/information-requests"))[0].id),
    disb: await safe(async () => list(await api(token, "/api/legal/disbursements"))[0].id),
    docTpl: await safe(async () => list(await api(token, "/api/templates"))[0].id),
    checklist: await safe(async () => list(await api(token, "/api/checklist-templates"))[0].id),
    reqTpl: await safe(async () => list(await api(token, "/api/request-templates"))[0].id),
    billingRun: await safe(async () => list(await api(token, "/api/billing-runs"))[0].id),
    report: "timesheet",
  };
  ID.client = await harvest("/clients", new RegExp(`^/org/${ORG}/clients/(${UUID})$`));
  ID.dsr = await harvest("/compliance/requests", new RegExp(`^/org/${ORG}/compliance/requests/(${UUID})$`));
  ID.ledgerClient = await harvest("/trust-accounting/client-ledgers", new RegExp(`^/org/${ORG}/trust-accounting/client-ledgers/(${UUID})$`));
  ID.reconList = await harvest("/trust-accounting/reconciliation", new RegExp(`^/org/${ORG}/trust-accounting/reconciliation/lists/(${UUID})$`));
  ID.matterTpl = await harvest("/settings/matters?tab=templates", new RegExp(`^/org/${ORG}/settings/matters/templates/(${UUID})$`));
  ID.billingRun = ID.billingRun || (await harvest("/invoices/billing-runs", new RegExp(`^/org/${ORG}/invoices/billing-runs/(${UUID})$`)));
  console.log("IDS", JSON.stringify(ID));
  const DYN = {
    "/clients/[id]": ID.client,
    "/compliance/requests/[id]": ID.dsr,
    "/information-requests/[id]": ID.infoReq,
    "/invoices/[id]": ID.invoice,
    "/invoices/billing-runs/[id]": ID.billingRun,
    "/legal/disbursements/[id]": ID.disb,
    "/matters/[id]": ID.matter,
    "/reports/[reportSlug]": ID.report,
    "/settings/documents/templates/[id]/edit": ID.docTpl,
    "/settings/matters/checklists/[id]": ID.checklist,
    "/settings/matters/checklists/[id]/edit": ID.checklist,
    "/settings/matters/request-templates/[id]": ID.reqTpl,
    "/settings/matters/templates/[id]": ID.matterTpl,
    "/trust-accounting/client-ledgers/[clientId]": ID.ledgerClient,
    "/trust-accounting/reconciliation/lists/[listId]": ID.reconList,
  };

  // ---- Visit list: [group, routePattern, url path] — app routes, the settings redirect, 32 tabs, 11 sub-pages.
  const visits = [];
  const skipped = [];
  for (const r of ROUTES) {
    if (FAMILY_HOSTS.has(r)) continue; // walked per ?tab= below
    const group = r === "/settings" ? "settings" : isSub(r) ? "sub" : "app";
    if (r.includes("[")) {
      const id = DYN[r];
      if (!id) {
        skipped.push({ route: r, reason: "no id resolvable from the populated data (read-only walk)" });
        continue;
      }
      visits.push([group, r, r.replace(/\[[^\]]+\]/, id)]);
    } else visits.push([group, r, r === "/" ? "" : r]);
  }
  for (const [fam, tab] of TABS) visits.push(["tab", `/settings/${fam}?tab=${tab}`, `/settings/${fam}?tab=${tab}`]);
  const order = { app: 0, settings: 1, tab: 2, sub: 3 };
  visits.sort((a, b) => order[a[0]] - order[b[0]]);

  // Console-sink probe (an empty console list is then a real zero).
  cur.label = "probe";
  await page.evaluate(() => console.warn("661B walk console-sink probe"));
  await page.waitForTimeout(300);
  const probeCaptured = consoleMsgs.some((c) => c.text.includes("661B walk console-sink probe"));

  const walk = [];
  const t0 = Date.now();
  let n = 0;
  for (const [group, route, p] of visits) {
    n++;
    const name = `${String(n).padStart(2, "0")}-` + (p.replace(/^\//, "").replace(/\?tab=/, "--").replace(new RegExp(UUID, "g"), "id").replace(/[/?=&]+/g, "-") || "root");
    cur.label = name;
    const before = consoleMsgs.length;
    const rec = { n, group, route, name, file: rel(path.join(RAW, `${name}.png`)) };
    try {
      const resp = await page.goto(BASE + p, { waitUntil: "load", timeout: 120000 });
      rec.status = resp ? resp.status() : null;
      await page.locator("#main-content h1").first().waitFor({ timeout: 30000 }).catch(() => (rec.noH1Wait = true));
      await page.mouse.move(1, 1);
      await page.waitForTimeout(2000);
      const h = await page.evaluate(() => document.documentElement.scrollHeight);
      await page.screenshot({ path: path.join(RAW, `${name}.png`), fullPage: true, clip: { x: 0, y: 0, width: 1440, height: Math.min(h, 3200) } });
      Object.assign(
        rec,
        await page.evaluate(() => ({
          url: location.pathname.replace(/^\/org\/[^/]+/, "") + location.search,
          h1: [...document.querySelectorAll("#main-content h1")].map((e) => e.textContent.trim().slice(0, 60)),
          mains: document.querySelectorAll("main#main-content").length,
          selectedTab: [...document.querySelectorAll('[role="tab"][aria-selected="true"], [role="tab"][data-state="active"]')].map((t) => t.textContent.trim()).join(" | ") || null,
          dark: document.documentElement.classList.contains("dark"),
          docHeight: document.documentElement.scrollHeight,
          errorText: /Something went wrong|could not be found|Application error|Unhandled Runtime Error|page could not be found/i.test((document.querySelector("#main-content") || document.body).innerText.slice(0, 3000)) ? (document.querySelector("#main-content") || document.body).innerText.slice(0, 200).replace(/\s+/g, " ") : null,
        })),
      );
      rec.audit = await page.evaluate(lightAudit);
    } catch (e) {
      rec.error = String(e).slice(0, 300);
    }
    const mine = consoleMsgs.slice(before);
    rec.console = { errors: mine.filter((m) => m.type !== "warning").length, warnings: mine.filter((m) => m.type === "warning").length, hydration: mine.filter((m) => /hydrat/i.test(m.text)).length };
    walk.push(rec);
    const a = rec.audit || {};
    console.log(`${name} ${rec.status} ${rec.url} h1=${JSON.stringify(rec.h1)} low=${a.lowTextCount}/${a.lowTextPreCount} schemeText=${a.schemeTextCount} illeg=${a.schemeIllegibleCount} icons=${a.schemeIconCount} faint=${a.schemeFaintCount} con=${rec.console.errors}/${rec.console.warnings} ${rec.error ? "ERR " + rec.error.slice(0, 80) : ""} ${Math.round((Date.now() - t0) / 1000)}s`);
  }
  await ctx.close();

  // ---- Contact sheets: 6 pages per sheet (3 x 2), tiles at half scale, captioned with the route.
  const sheets = [];
  for (let i = 0; i < walk.length; i += 6) {
    const chunk = walk.slice(i, i + 6);
    const file = `walk-${String(sheets.length + 1).padStart(2, "0")}.png`;
    await sheet(
      browser,
      path.join(OUT, file),
      `661B light walk (1440x900, owner, light) — ${file}`,
      chunk.map((r) => [`#${r.n} ${r.route}${r.url && r.url.split("?")[0] !== r.route.split("?")[0] ? ` → ${r.url}` : ""} · ${r.status} · ${r.docHeight > 3200 ? "clipped 3200 of " + r.docHeight : r.docHeight + "px"}`, path.join(RAW, `${r.name}.png`)]),
      3,
      720,
    );
    chunk.forEach((r) => (r.sheet = file));
    sheets.push(file);
  }
  fs.writeFileSync(
    path.join(OUT, "walk.json"),
    JSON.stringify({ capturedAt: new Date().toISOString(), persona: "alice-owner", theme: "light", viewport: "1440x900", probeCaptured, routesOnDisk: ROUTES.length, tabs: TABS.length, subPages: ROUTES.filter(isSub).length, ids: ID, skipped, sheets, walk }) + "\n",
  );
  fs.writeFileSync(path.join(OUT, "console.json"), JSON.stringify(consoleMsgs) + "\n");
  await browser.close();
  console.log("DONE", walk.length, "pages,", skipped.length, "skipped,", sheets.length, "sheets");
})().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
