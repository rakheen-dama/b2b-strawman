// 661B observe-first "before" capture (task 661.8). Playwright library script (the Playwright MCP
// Chrome profile is held by the user's interactive session — memory env_playwright_mcp_profile_lock_obs).
// Usage (from the worktree root):
//   node qa_cycle/evidence/phase91-ui/661B/before/scripts/capture-before.cjs <outDir> [portalUrl]
// Firm app: e2e mock-auth stack on :3001 (backend :8081, mock IDP :8090). Portal: worktree portal on
// `next dev --port 3012` with NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 (Origin shim below).
const path = require("path");
const fs = require("fs");
const W = path.resolve(__dirname, "../../../../../..");
const { chromium } = require(path.join(W, "frontend/node_modules/@playwright/test"));

const OUT = path.resolve(process.argv[2] || "/tmp/661b-before");
const RAW = path.join(OUT, "raw");
const PORTAL = process.argv[3] || "http://localhost:3012";
const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const ORG = "e2e-test-org";
fs.mkdirSync(RAW, { recursive: true });

const notes = [];
const consoleMsgs = [];
const note = (m) => {
  notes.push(m);
  console.log("NOTE", m);
};

async function idpToken() {
  const r = await fetch(`${IDP}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "owner" }),
  });
  return (await r.json()).access_token;
}
async function api(token, p) {
  const r = await fetch(`${API}${p}`, { headers: { Authorization: `Bearer ${token}` } });
  if (!r.ok) throw new Error(`${p} -> ${r.status}`);
  return r.json();
}
const list = (j) => (Array.isArray(j) ? j : j.content || j.items || j.data || []);

async function newCtx(browser, state, { width, height, theme = "light", collapsed = false }) {
  const ctx = await browser.newContext({ viewport: { width, height }, deviceScaleFactor: 1, storageState: state });
  await ctx.addCookies([{ name: "sidebar_state", value: String(!collapsed), domain: "localhost", path: "/" }]);
  await ctx.addInitScript((t) => {
    try {
      localStorage.setItem("kazi-theme", t);
    } catch (e) {}
    // sRGB hex of any CSS colour (computed styles come back as lab()/oklab()); alpha appended when < 1.
    window.__hex = (c) => {
      const cv = document.createElement("canvas");
      cv.width = cv.height = 1;
      const x = cv.getContext("2d");
      x.fillStyle = "#000";
      x.fillStyle = c;
      x.fillRect(0, 0, 1, 1);
      const d = x.getImageData(0, 0, 1, 1).data;
      return "#" + [d[0], d[1], d[2]].map((v) => v.toString(16).padStart(2, "0")).join("") + (d[3] < 255 ? ` a=${(d[3] / 255).toFixed(2)}` : "");
    };
  }, theme);
  return ctx;
}
function watch(page, label) {
  page.on("console", (m) => {
    if (m.type() === "error" || m.type() === "warning") consoleMsgs.push({ label, type: m.type(), text: m.text().slice(0, 300) });
  });
  page.on("pageerror", (e) => consoleMsgs.push({ label, type: "pageerror", text: String(e).slice(0, 300) }));
}
async function settle(page, url) {
  await page.goto(url, { waitUntil: "load" });
  await page.locator("#main-content h1").first().waitFor({ state: "visible", timeout: 30000 });
  await page.waitForTimeout(800);
}
async function shot(page, name, opts = {}) {
  const f = path.join(RAW, name + ".png");
  await page.screenshot({ path: f, ...opts });
  return f;
}

// Computed-style probe: one record per selector.
async function probe(page, selectors) {
  return page.evaluate((sels) => {
    const out = {};
    for (const [k, sel] of Object.entries(sels)) {
      const el = document.querySelector(sel);
      if (!el) {
        out[k] = null;
        continue;
      }
      const cs = getComputedStyle(el);
      out[k] = { sel, bg: cs.backgroundColor, bgHex: window.__hex(cs.backgroundColor), fg: cs.color, fgHex: window.__hex(cs.color) };
    }
    out.htmlClass = document.documentElement.className;
    return out;
  }, selectors);
}
const SHELL_PROBES = {
  rail: '[data-slot="sidebar-inner"]',
  activeNav: '[data-sidebar="menu-button"][data-active="true"]',
  main: "main#main-content",
  header: "header",
};

async function sheet(browser, title, files, cols, outName) {
  const cells = files
    .map(([cap, f]) =>
      f && fs.existsSync(f)
        ? `<figure><figcaption>${cap}</figcaption><img src="data:image/png;base64,${fs.readFileSync(f).toString("base64")}"></figure>`
        : `<figure><figcaption>${cap}</figcaption><div class="miss">not captured</div></figure>`,
    )
    .join("");
  const html = `<html><body style="margin:0;background:#888;font:14px sans-serif"><h2 style="margin:8px">${title}</h2>
  <div style="display:grid;grid-template-columns:repeat(${cols},max-content);gap:8px;padding:8px">${cells}</div>
  <style>figure{margin:0;background:#fff}figcaption{padding:4px 6px;font-weight:600}img{display:block}.miss{padding:40px}</style></body></html>`;
  const p = await browser.newPage({ viewport: { width: 800, height: 600 } });
  await p.setContent(html, { waitUntil: "load" });
  await p.screenshot({ path: path.join(OUT, outName), fullPage: true });
  await p.close();
}

(async () => {
  const browser = await chromium.launch();
  const token = await idpToken();

  // ---- Login through the mock-login UI as Alice (owner).
  const lctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const lp = await lctx.newPage();
  await lp.goto(`${APP}/mock-login`, { waitUntil: "load" });
  await lp.getByRole("button", { name: "Sign In", exact: true }).click();
  await lp.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  const state = await lctx.storageState();
  await lctx.close();

  // ---- Data: a matter, a DRAFT and an APPROVED fee note.
  const projects = list(await api(token, "/api/matters"));
  // The ACTIVE client matter with the largest trust balance (a representative legal matter, not a spec fixture).
  const matter = projects.filter((p) => p.status === "ACTIVE" && p.clientName).sort((x, y) => (y.trustBalance || 0) - (x.trustBalance || 0))[0] || projects[0];
  const invoices = list(await api(token, "/api/invoices"));
  const draft = invoices.find((i) => i.status === "DRAFT");
  const approved = invoices.find((i) => i.status === "APPROVED");
  const MATTER_URL = `${APP}/org/${ORG}/matters/${matter.id}`;
  note(`matter=${matter.id} (${matter.name}); invoices=${invoices.length} draft=${draft && draft.id} approved=${approved && approved.id}`);

  const probes = {};
  const live = {};

  // ---- Shell 1024/1440 x expanded/collapsed x light/dark.
  const shellFiles = { 1024: [], 1440: [] };
  for (const [w, h] of [
    [1024, 768],
    [1440, 900],
  ]) {
    for (const theme of ["light", "dark"]) {
      for (const collapsed of [false, true]) {
        const ctx = await newCtx(browser, state, { width: w, height: h, theme, collapsed });
        const page = await ctx.newPage();
        const label = `shell-${w}-${collapsed ? "collapsed" : "expanded"}-${theme}`;
        watch(page, label);
        await settle(page, MATTER_URL);
        shellFiles[w].push([label, await shot(page, label)]);
        if (w === 1440 && !collapsed) probes[`shell-${theme}`] = await probe(page, SHELL_PROBES);
        if (w === 1440 && !collapsed && theme === "light") {
          for (const [k, u] of [
            ["matter", MATTER_URL],
            ["dashboard", `${APP}/org/${ORG}/dashboard`],
          ]) {
            await settle(page, u);
            live[k] = await page.evaluate(() => ({
              mainCount: document.querySelectorAll("main").length,
              mainContentCount: document.querySelectorAll("main#main-content").length,
              h1InMain: document.querySelectorAll("#main-content h1").length,
              h1Text: [...document.querySelectorAll("#main-content h1")].map((e) => e.textContent.trim()),
              mainNav: document.querySelectorAll('nav[aria-label="Main navigation"]').length,
              searchCmdK: document.querySelectorAll('[aria-label="Search, Command K"]').length,
              brandAccent: document.querySelectorAll('[data-testid="org-brand-accent"]').length,
            }));
          }
        }
        await ctx.close();
      }
    }
  }
  await sheet(browser, "661B before — shell 1024x768 (matter detail)", shellFiles[1024], 2, "shell-1024.png");
  await sheet(browser, "661B before — shell 1440x900 (matter detail)", shellFiles[1440], 2, "shell-1440.png");

  // ---- 390 with the mobile Sheet open (light + dark).
  const mob = [];
  for (const theme of ["light", "dark"]) {
    const ctx = await newCtx(browser, state, { width: 390, height: 844, theme });
    const page = await ctx.newPage();
    watch(page, `390-${theme}`);
    await settle(page, MATTER_URL);
    mob.push([`390-closed-${theme}`, await shot(page, `390-closed-${theme}`)]);
    await page.getByRole("button", { name: "Toggle menu" }).first().click();
    await page.locator('[data-mobile="true"][data-sidebar="sidebar"]').waitFor({ state: "visible" });
    await page.waitForTimeout(600);
    mob.push([`390-sheet-open-${theme}`, await shot(page, `390-sheet-${theme}`)]);
    probes[`sheet390-${theme}`] = await probe(page, { sheet: '[data-mobile="true"][data-sidebar="sidebar"]' });
    await ctx.close();
  }
  await sheet(browser, "661B before — 390x844, mobile Sheet", mob, 4, "shell-390-sheet.png");

  // ---- Marketing / at 1024 light (no auth).
  {
    const ctx = await browser.newContext({ viewport: { width: 1024, height: 768 }, deviceScaleFactor: 1 });
    const page = await ctx.newPage();
    watch(page, "marketing");
    await page.goto(`${APP}/`, { waitUntil: "load" });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(OUT, "marketing-1024-light.png") });
    await ctx.close();
  }

  // ---- Fee-note header accent buttons (Approve on DRAFT, Send on APPROVED), light + dark.
  const accent = [];
  for (const theme of ["light", "dark"]) {
    for (const [kind, inv, btn] of [
      ["draft-approve", draft, "Approve"],
      ["approved-send", approved, "Send fee note"],
    ]) {
      if (!inv) {
        note(`no ${kind} fee note — not captured (${theme})`);
        accent.push([`${kind}-${theme}`, null]);
        continue;
      }
      const ctx = await newCtx(browser, state, { width: 1440, height: 900, theme });
      const page = await ctx.newPage();
      watch(page, `${kind}-${theme}`);
      await page.goto(`${APP}/org/${ORG}/invoices/${inv.id}`, { waitUntil: "load" });
      const b = page.getByRole("button", { name: btn, exact: true }).first();
      try {
        await b.waitFor({ state: "visible", timeout: 30000 });
        await page.waitForTimeout(800);
        const box = await b.boundingBox();
        const mb = await page.locator("main#main-content").boundingBox();
        const clip = { x: mb.x, y: Math.max(0, box.y - 110), width: 1440 - mb.x, height: 200 };
        accent.push([`${kind}-${theme} ("${btn}")`, await shot(page, `${kind}-${theme}`, { clip })]);
        probes[`accent-${kind}-${theme}`] = await page.evaluate(
          ([name]) => {
            const el = [...document.querySelectorAll('button[data-variant="accent"]')].find((e) => e.textContent.trim() === name);
            if (!el) return null;
            const cs = getComputedStyle(el);
            return { sel: `button[data-variant=accent] "${name}"`, bg: cs.backgroundColor, bgHex: window.__hex(cs.backgroundColor), fg: cs.color, fgHex: window.__hex(cs.color), html: document.documentElement.className };
          },
          [btn],
        );
      } catch (e) {
        note(`${kind}-${theme}: button "${btn}" not visible: ${String(e).slice(0, 120)}`);
        accent.push([`${kind}-${theme}`, await shot(page, `${kind}-${theme}-page`)]);
      }
      await ctx.close();
    }
  }
  await sheet(browser, "661B before — fee-note header accent buttons (light | dark)", accent, 2, "dark-accent-buttons.png");

  // ---- Open row menu with a keyboard-focused item (light + dark).
  const menus = [];
  for (const theme of ["light", "dark"]) {
    const ctx = await newCtx(browser, state, { width: 1440, height: 900, theme });
    const page = await ctx.newPage();
    watch(page, `rowmenu-${theme}`);
    let ok = false;
    for (const u of [`${APP}/org/${ORG}/settings/matters?tab=custom-fields`, `${APP}/org/${ORG}/diary`]) {
      await page.goto(u, { waitUntil: "load" });
      const trig = page.locator('button[aria-label^="Actions for"]').first();
      try {
        await trig.waitFor({ state: "visible", timeout: 20000 });
      } catch {
        note(`row menu: no "Actions for" trigger on ${u} (${theme})`);
        continue;
      }
      await page.waitForTimeout(600);
      await trig.click();
      const menu = page.locator('[role="menu"]').first();
      await menu.waitFor({ state: "visible" });
      await page.waitForTimeout(300);
      const focusedProbe = () =>
        page.evaluate(() => {
          const el = document.activeElement && document.activeElement.getAttribute("role") === "menuitem" ? document.activeElement : document.querySelector('[role="menuitem"][data-highlighted]');
          if (!el) return null;
          const cs = getComputedStyle(el);
          return { sel: "[role=menuitem]:focus", slot: el.getAttribute("data-slot"), variant: el.getAttribute("data-variant"), text: el.textContent.trim(), bg: cs.backgroundColor, bgHex: window.__hex(cs.backgroundColor), fg: cs.color, fgHex: window.__hex(cs.color), html: document.documentElement.className };
        });
      for (const step of [1, 2]) {
        await page.keyboard.press("ArrowDown");
        await page.waitForTimeout(400);
        const mb = await menu.boundingBox();
        const clip = { x: Math.max(0, mb.x - 420), y: Math.max(0, mb.y - 60), width: Math.min(820, 1440 - Math.max(0, mb.x - 420)), height: Math.min(900 - Math.max(0, mb.y - 60), mb.height + 120) };
        const pr = await focusedProbe();
        probes[`menuFocused${step}-${theme}`] = pr;
        menus.push([`row-menu-${theme}, ArrowDown x${step} → "${pr && pr.text}" (${u.split(ORG)[1]})`, await shot(page, `rowmenu-${theme}-${step}`, { clip })]);
      }
      ok = true;
      break;
    }
    if (!ok) menus.push([`row-menu-${theme}`, null]);
    await ctx.close();
  }
  await sheet(browser, "661B before — open row menu, item focused via ArrowDown (light | dark)", menus, 2, "dark-row-menu-focused.png");

  // ---- Light controls: theme toggle (user menu), dialog close button, Select open.
  const ctrl = [];
  {
    const ctx = await newCtx(browser, state, { width: 1440, height: 900, theme: "light" });
    const page = await ctx.newPage();
    watch(page, "controls");
    // Theme toggle lives in the user menu.
    await settle(page, `${APP}/org/${ORG}/dashboard`);
    try {
      // Mock auth renders MockUserButton (auth-header-controls.tsx): an initials button whose popover holds ThemeToggle.
      await page.locator("header").getByRole("button", { name: /^[A-Z]{1,2}$/ }).first().click();
      const tg = page.getByRole("group", { name: "Theme" });
      await tg.waitFor({ state: "visible", timeout: 10000 });
      await page.waitForTimeout(400);
      const m = await tg.locator('xpath=ancestor::div[contains(@class,"absolute")][1]').boundingBox();
      ctrl.push(["theme toggle (User menu)", await shot(page, "theme-toggle", { clip: { x: Math.max(0, m.x - 40), y: Math.max(0, m.y - 20), width: Math.min(m.width + 80, 1440), height: Math.min(m.height + 40, 900) } })]);
      probes["themeToggle-light"] = await page.evaluate(() => {
        const items = [...document.querySelectorAll('[role="group"][aria-label="Theme"] [role="radio"], [role="group"][aria-label="Theme"] button')];
        return items.map((e) => ({ text: e.textContent.trim(), state: e.getAttribute("data-state"), bgHex: window.__hex(getComputedStyle(e).backgroundColor), fgHex: window.__hex(getComputedStyle(e).color) }));
      });
      await page.mouse.click(700, 600);
    } catch (e) {
      note(`theme toggle: ${String(e).slice(0, 150)}`);
      ctrl.push(["theme toggle", null]);
    }
    // Dialog close button: New Matter dialog on the matters list.
    try {
      await settle(page, `${APP}/org/${ORG}/matters`);
      await page.getByRole("button", { name: "New Matter" }).first().click();
      const dlg = page.locator('[data-slot="dialog-content"]').first();
      await dlg.waitFor({ state: "visible", timeout: 10000 });
      await page.waitForTimeout(500);
      const close = dlg.locator('[data-slot="dialog-close"]').last();
      await close.waitFor({ state: "visible" });
      const db = await dlg.boundingBox();
      ctrl.push(["dialog close button (New Matter header)", await shot(page, "dialog-close", { clip: { x: db.x - 10, y: db.y - 10, width: db.width + 20, height: 110 } })]);
      probes["dialogClose-light"] = await close.evaluate((e) => ({ opacity: getComputedStyle(e).opacity, fgHex: window.__hex(getComputedStyle(e).color), bgHex: window.__hex(getComputedStyle(e).backgroundColor) }));
      ctrl.push(["dialog (New Matter) full", await shot(page, "dialog-full")]);
      await page.keyboard.press("Escape");
    } catch (e) {
      note(`dialog close: ${String(e).slice(0, 150)}`);
      ctrl.push(["dialog close button", null]);
    }
    // Select open: first Radix Select trigger on firm settings > General, then fallbacks.
    let sel = false;
    for (const u of [`${APP}/org/${ORG}/settings/firm?tab=general`, `${APP}/org/${ORG}/settings/firm?tab=audit-trail`, `${APP}/org/${ORG}/invoices/unbilled`]) {
      try {
        await page.goto(u, { waitUntil: "load" });
        const t = page.locator('[data-slot="select-trigger"]').first();
        await t.waitFor({ state: "visible", timeout: 15000 });
        await t.scrollIntoViewIfNeeded();
        await page.waitForTimeout(500);
        await t.click();
        const c = page.locator('[data-slot="select-content"]').first();
        await c.waitFor({ state: "visible", timeout: 5000 });
        await page.waitForTimeout(400);
        const tb = await t.boundingBox();
        const cb = await c.boundingBox();
        const x = Math.max(0, Math.min(tb.x, cb.x) - 200);
        const y = Math.max(0, Math.min(tb.y, cb.y) - 40);
        const x2 = Math.min(1440, Math.max(tb.x + tb.width, cb.x + cb.width) + 200);
        const y2 = Math.min(900, Math.max(tb.y + tb.height, cb.y + cb.height) + 40);
        ctrl.push([`Select open (${u.split(ORG)[1]})`, await shot(page, "select-open", { clip: { x, y, width: x2 - x, height: y2 - y } })]);
        probes["selectHighlighted-light"] = await page.evaluate(() => {
          const el = document.querySelector('[data-slot="select-item"][data-highlighted]') || document.querySelector('[data-slot="select-item"]:focus');
          return el ? { text: el.textContent.trim(), bg: getComputedStyle(el).backgroundColor, bgHex: window.__hex(getComputedStyle(el).backgroundColor), fg: getComputedStyle(el).color, fgHex: window.__hex(getComputedStyle(el).color) } : null;
        });
        await page.keyboard.press("Escape");
        sel = true;
        break;
      } catch (e) {
        note(`select on ${u}: ${String(e).slice(0, 120)}`);
      }
    }
    if (!sel) ctrl.push(["Select open", null]);
    await ctx.close();
  }
  await sheet(browser, "661B before — light controls: theme toggle, dialog close, Select open", ctrl, 2, "light-controls.png");

  // ---- Performance proxy (§18.6.4): CDP Performance.getMetrics delta over a matter page load, 3 runs per theme.
  const perf = {};
  for (const theme of ["light", "dark"]) {
    const runs = [];
    for (let i = 0; i < 3; i++) {
      const ctx = await newCtx(browser, state, { width: 1440, height: 900, theme });
      const page = await ctx.newPage();
      const cdp = await ctx.newCDPSession(page);
      await cdp.send("Performance.enable", { timeDomain: "timeTicks" });
      await page.goto(`${APP}/org/${ORG}/dashboard`, { waitUntil: "load" });
      await page.waitForTimeout(1000);
      const m0 = Object.fromEntries((await cdp.send("Performance.getMetrics")).metrics.map((m) => [m.name, m.value]));
      await settle(page, MATTER_URL);
      await page.waitForTimeout(1500);
      const m1 = Object.fromEntries((await cdp.send("Performance.getMetrics")).metrics.map((m) => [m.name, m.value]));
      const d = (k) => +(m1[k] - m0[k]).toFixed(4);
      runs.push({
        RecalcStyleDuration_s: d("RecalcStyleDuration"),
        RecalcStyleCount: d("RecalcStyleCount"),
        LayoutDuration_s: d("LayoutDuration"),
        LayoutCount: d("LayoutCount"),
        ScriptDuration_s: d("ScriptDuration"),
        TaskDuration_s: d("TaskDuration"),
        Nodes: m1.Nodes,
      });
      await ctx.close();
    }
    const med = (k) => runs.map((r) => r[k]).sort((a, b) => a - b)[1];
    perf[theme] = { runs, median: { RecalcStyleDuration_s: med("RecalcStyleDuration_s"), LayoutDuration_s: med("LayoutDuration_s") } };
  }
  // Plus one Chromium trace of the matter page load (light) for the Performance panel.
  {
    const ctx = await newCtx(browser, state, { width: 1440, height: 900, theme: "light" });
    const page = await ctx.newPage();
    await page.goto(`${APP}/org/${ORG}/dashboard`, { waitUntil: "load" });
    await browser.startTracing(page, { path: path.join(RAW, "matter-load-trace.json"), categories: ["devtools.timeline", "disabled-by-default-devtools.timeline"] });
    await settle(page, MATTER_URL);
    await page.waitForTimeout(1500);
    await browser.stopTracing();
    const tr = JSON.parse(fs.readFileSync(path.join(RAW, "matter-load-trace.json"), "utf8"));
    const ev = tr.traceEvents || tr;
    const sum = (n) => +(ev.filter((e) => e.name === n && e.ph === "X").reduce((s, e) => s + (e.dur || 0), 0) / 1000).toFixed(2);
    perf.trace_light_ms = { UpdateLayoutTree: sum("UpdateLayoutTree"), Layout: sum("Layout"), Paint: sum("Paint"), events: ev.length };
    await ctx.close();
  }

  // ---- Portal /home and /login at 1024 light.
  const portalFiles = [];
  try {
    const email = process.env.PORTAL_EMAIL || "contact@acme.example.com";
    const lr = await fetch(`${API}/portal/auth/request-link`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, orgId: ORG }) });
    if (!lr.ok) throw new Error(`request-link ${lr.status} ${await lr.text()}`);
    const { magicLink } = await lr.json();
    const tk = magicLink.split("token=").pop().split("&")[0];
    const er = await fetch(`${API}/portal/auth/exchange`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token: tk, orgId: ORG }) });
    if (!er.ok) throw new Error(`exchange ${er.status}`);
    const s = await er.json();
    const ctx = await browser.newContext({ viewport: { width: 1024, height: 768 }, deviceScaleFactor: 1 });
    await ctx.route(`${API}/**`, async (route) => {
      const req = route.request();
      const acao = { "access-control-allow-origin": new URL(PORTAL).origin, "access-control-allow-credentials": "true", "access-control-allow-headers": "*", "access-control-allow-methods": "*" };
      if (req.method() === "OPTIONS") return route.fulfill({ status: 204, headers: acao });
      const resp = await route.fetch({ headers: { ...req.headers(), origin: "http://localhost:3002" } });
      return route.fulfill({ response: resp, headers: { ...resp.headers(), ...acao } });
    });
    const page = await ctx.newPage();
    watch(page, "portal");
    await page.goto(`${PORTAL}/login`, { waitUntil: "load" });
    await page.locator("input, button").first().waitFor({ state: "visible", timeout: 60000 });
    await page.waitForTimeout(1000);
    portalFiles.push(["portal /login 1024 light", await shot(page, "portal-login")]);
    await page.evaluate((x) => {
      localStorage.setItem("portal_jwt", x.token);
      localStorage.setItem("portal_customer", JSON.stringify({ id: x.clientId, name: x.clientName, email: x.email, orgId: x.orgId }));
      localStorage.setItem("portal_last_org_id", x.orgId);
    }, { token: s.token, clientId: s.clientId, clientName: s.clientName, email, orgId: ORG });
    await page.goto(`${PORTAL}/home`, { waitUntil: "load" });
    await page.locator("h1").first().waitFor({ state: "visible", timeout: 60000 });
    await page.waitForTimeout(2500);
    note(`portal /home url=${page.url()} h1=${await page.locator("h1").first().textContent()}`);
    portalFiles.push(["portal /home 1024 light", await shot(page, "portal-home")]);
    await ctx.close();
  } catch (e) {
    note(`portal: ${String(e).slice(0, 300)}`);
  }
  await sheet(browser, "661B before — portal 1024 light", portalFiles.length ? portalFiles : [["portal", null]], 2, "portal-1024-light.png");

  fs.writeFileSync(path.join(OUT, "probes.json"), JSON.stringify(probes) + "\n");
  fs.writeFileSync(path.join(OUT, "perf.json"), JSON.stringify(perf) + "\n");
  fs.writeFileSync(path.join(OUT, "live-selectors.json"), JSON.stringify(live) + "\n");
  fs.writeFileSync(path.join(OUT, "console.json"), JSON.stringify(consoleMsgs) + "\n");
  fs.writeFileSync(path.join(OUT, "notes.json"), JSON.stringify(notes) + "\n");
  await browser.close();
  console.log("DONE");
})().catch((e) => {
  console.error("FATAL", e);
  fs.writeFileSync(path.join(OUT, "notes.json"), JSON.stringify([...notes, "FATAL " + String(e)]) + "\n");
  process.exit(1);
});
