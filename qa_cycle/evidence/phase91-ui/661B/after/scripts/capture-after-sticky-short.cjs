// 661B after — supplement to capture-after.cjs for check (b). On this stack the clients list (the only DataTable
// with stickyHeader on: clients-table.tsx:90; matters-table.tsx:80 sets it off) and the 1440 matter page are too
// short to scroll their sticky element up to the header at 1440x900 / 1024x768, so this re-measures them in SHORT
// viewports (same width, less height) where the page scrolls far enough. Same login, theme and cookie setup.
// Usage (from the worktree root):
//   node qa_cycle/evidence/phase91-ui/661B/after/scripts/capture-after-sticky-short.cjs <outDir>
const path = require("path");
const fs = require("fs");
const W = path.resolve(__dirname, "../../../../../..");
const { chromium } = require(path.join(W, "frontend/node_modules/@playwright/test"));

const OUT = path.resolve(process.argv[2] || "/tmp/661b-after-sticky");
const RAW = path.join(OUT, "raw");
const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const ORG = "e2e-test-org";
fs.mkdirSync(RAW, { recursive: true });
const rel = (p) => path.relative(W, p);

(async () => {
  const browser = await chromium.launch();
  const tr = await fetch(`${IDP}/token`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "owner" }) });
  const token = (await tr.json()).access_token;
  const mj = await (await fetch(`${API}/api/matters`, { headers: { Authorization: `Bearer ${token}` } })).json();
  const projects = Array.isArray(mj) ? mj : mj.content || [];
  const matter = projects.filter((p) => p.status === "ACTIVE" && p.clientName).sort((x, y) => (y.trustBalance || 0) - (x.trustBalance || 0))[0];

  const lctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const lp = await lctx.newPage();
  await lp.goto(`${APP}/mock-login`, { waitUntil: "load" });
  await lp.getByRole("button", { name: "Sign In", exact: true }).click();
  await lp.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  const state = await lctx.storageState();
  await lctx.close();

  const out = {};
  const consoleMsgs = [];
  const tiles = [];
  for (const theme of ["light", "dark"]) {
    for (const [k, url, sel, w, h] of [
      ["clientsListThead", `${APP}/org/${ORG}/clients`, '[data-testid="clients-table"] thead th', 1440, 560],
      ["clientsListThead", `${APP}/org/${ORG}/clients`, '[data-testid="clients-table"] thead th', 1024, 520],
      ["matterTabs", `${APP}/org/${ORG}/matters/${matter.id}`, '[data-slot="grouped-tab-bar-sticky"]', 1440, 600],
    ]) {
      const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1, storageState: state });
      await ctx.addCookies([{ name: "sidebar_state", value: "true", domain: "localhost", path: "/" }]);
      await ctx.addInitScript((t) => {
        localStorage.setItem("kazi-theme", t);
        window.__r = (s) => {
          const e = document.querySelector(s);
          if (!e) return null;
          const b = e.getBoundingClientRect();
          return { y: +b.y.toFixed(1), bottom: +b.bottom.toFixed(1), x: +b.x.toFixed(1), right: +b.right.toFixed(1) };
        };
      }, theme);
      const page = await ctx.newPage();
      page.on("console", (m) => (m.type() === "error" || m.type() === "warning") && consoleMsgs.push({ label: `${k}-${w}x${h}-${theme}`, type: m.type(), text: m.text().slice(0, 300) }));
      await page.goto(url, { waitUntil: "load" });
      await page.locator("#main-content h1").first().waitFor({ state: "visible", timeout: 30000 });
      await page.waitForTimeout(800);
      const at0 = await page.evaluate((s) => ({ header: window.__r("header"), inset: window.__r('[data-slot="sidebar-inset"]'), target: window.__r(s) }), sel);
      const need = Math.ceil(at0.target.y - at0.header.bottom + 40);
      await page.evaluate((y) => window.scrollTo(0, y), need);
      await page.waitForTimeout(500);
      const at1 = await page.evaluate((s) => {
        const t = document.querySelector(s);
        return { scrollY: window.scrollY, maxScroll: document.scrollingElement.scrollHeight - window.innerHeight, header: window.__r("header"), target: window.__r(s), position: getComputedStyle(t).position, top: getComputedStyle(t).top };
      }, sel);
      const key = `${k}-${w}x${h}-${theme}`;
      out[key] = { url: url.replace(APP, "").replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/, "<id>"), atTop: at0, requestedScroll: need, scrolled: at1, stuckUnderHeader: at1.target.y === at1.header.bottom, gapTargetToHeaderBottom: +(at1.target.y - at1.header.bottom).toFixed(1) };
      const f = path.join(RAW, `sticky-short-${key}.png`);
      await page.screenshot({ path: f });
      tiles.push([`${k} ${w}x${h} ${theme} — scrolled ${at1.scrollY}px: header ${at1.header.y}..${at1.header.bottom}, target top ${at1.target.y} (${at1.position}, top ${at1.top})`, f]);
      console.log(key, JSON.stringify(out[key]));
      await ctx.close();
    }
  }
  const cells = tiles.map(([c, f]) => `<figure><figcaption>${c}</figcaption><img src="data:image/png;base64,${fs.readFileSync(f).toString("base64")}"></figure>`).join("");
  const p = await browser.newPage({ viewport: { width: 800, height: 600 } });
  await p.setContent(`<html><body style="margin:0;background:#888;font:14px sans-serif"><h2 style="margin:8px">661B after — (b) sticky DataTable head / GroupedTabBar under the header, short viewports</h2><div style="display:grid;grid-template-columns:repeat(3,max-content);gap:8px;padding:8px">${cells}</div><style>figure{margin:0;background:#fff}figcaption{padding:4px 6px;font-weight:600;max-width:1440px}img{display:block}</style></body></html>`, { waitUntil: "load" });
  await p.screenshot({ path: path.join(OUT, "sticky-short-viewports.png"), fullPage: true });
  fs.writeFileSync(path.join(OUT, "sticky-short.json"), JSON.stringify({ checks: out, console: consoleMsgs }) + "\n");
  await browser.close();
  console.log("DONE", rel(OUT));
})().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
