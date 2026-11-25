// 661B "after" capture (task 661.14, observation part). A NEW script adapted from
// ../../before/scripts/capture-before.cjs (never edited). Same surfaces, same setup (theme via localStorage
// `kazi-theme`, sidebar via cookie `sidebar_state`, deviceScaleFactor 1, Alice via the mock-login UI), plus:
//  - carry-over 5: every path printed / written is repo-relative;
//  - carry-over 6: the accent probe selects `[data-variant="accent"]` on ANY element (an accent button inside
//    `PopoverTrigger asChild` carries no `data-slot="button"`, which is why 661A's `accentButtonPaint` was null);
//  - the builder's checks a–h (F-661A-8 rail, sticky header/tab bar/table head, 768/390, dark pixel identity,
//    portal/marketing identity, measured light contrast, console, Radix portal tokens).
// Usage (from the worktree root):
//   node qa_cycle/evidence/phase91-ui/661B/after/scripts/capture-after.cjs <outDir> [portalUrl]
// Env: BEFORE_RAW = directory with the before run's raw per-tile PNGs (default /tmp/661b-before-raw; kept out of
// the tree under EVIDENCE-BUDGET). Firm app: e2e mock-auth stack on :3001 (backend :8081, mock IDP :8090).
// Portal: worktree portal on `next dev --port 3012` with NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081.
const path = require("path");
const fs = require("fs");
const W = path.resolve(__dirname, "../../../../../..");
const { chromium } = require(path.join(W, "frontend/node_modules/@playwright/test"));

const OUT = path.resolve(process.argv[2] || "/tmp/661b-after");
const RAW = path.join(OUT, "raw");
const PORTAL = process.argv[3] || "http://localhost:3012";
const BEFORE_RAW = process.env.BEFORE_RAW || "/tmp/661b-before-raw";
const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const ORG = "e2e-test-org";
fs.mkdirSync(RAW, { recursive: true });
// Repo-relative when inside the worktree; otherwise a /tmp-style label (no home-directory paths).
const rel = (p) => {
  const r = path.relative(W, p);
  return r.startsWith("..") ? p.replace(/^\/(Users|home)\/[^/]+/, "~") : r;
};

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
    // Opaque sRGB [r,g,b] of colour c painted over `over` (itself painted over white): what the eye sees.
    window.__rgb = (c, over) => {
      const cv = document.createElement("canvas");
      cv.width = cv.height = 1;
      const x = cv.getContext("2d");
      x.fillStyle = "#fff";
      x.fillRect(0, 0, 1, 1);
      if (over) {
        x.fillStyle = over;
        x.fillRect(0, 0, 1, 1);
      }
      x.fillStyle = "rgba(0,0,0,0)";
      x.fillStyle = c;
      x.fillRect(0, 0, 1, 1);
      const d = x.getImageData(0, 0, 1, 1).data;
      return [d[0], d[1], d[2]];
    };
    window.__rect = (el) => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: +r.x.toFixed(1), y: +r.y.toFixed(1), w: +r.width.toFixed(1), h: +r.height.toFixed(1), bottom: +r.bottom.toFixed(1), right: +r.right.toFixed(1) };
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
const pagesSeen = [];
async function settle(page, url) {
  await page.goto(url, { waitUntil: "load" });
  await page.locator("#main-content h1").first().waitFor({ state: "visible", timeout: 30000 });
  await page.waitForTimeout(800);
  pagesSeen.push(url.replace(APP, ""));
}
async function shot(page, name, opts = {}) {
  const f = path.join(RAW, name + ".png");
  await page.screenshot({ path: f, ...opts });
  return f;
}

// WCAG 2.x contrast of two opaque sRGB triplets, unrounded.
function lum([r, g, b]) {
  const f = (v) => {
    v /= 255;
    return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}
const ratio = (a, b) => {
  const [l1, l2] = [lum(a), lum(b)].sort((x, y) => y - x);
  return +((l1 + 0.05) / (l2 + 0.05)).toFixed(3);
};
const hex3 = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");

// Computed-style probe: one record per selector (same shape as the before script).
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
// Shell geometry + inset card + tokens + the fixed accent probe (carry-over 6).
async function shellExtra(page) {
  return page.evaluate(() => {
    const q = (s) => document.querySelector(s);
    const root = getComputedStyle(document.documentElement);
    const tok = (n) => {
      const v = root.getPropertyValue(n).trim();
      return v ? { v, hex: window.__hex(v) } : null;
    };
    const wrap = q('[data-app-shell="firm"]');
    const inset = q('[data-slot="sidebar-inset"]');
    const cont = q('[data-slot="sidebar-container"]');
    const ics = inset && getComputedStyle(inset);
    const ccs = cont && getComputedStyle(cont);
    const acc = [...document.querySelectorAll('[data-variant="accent"]')].filter((e) => e.getBoundingClientRect().width > 0);
    const a0 = acc[0];
    return {
      dataAppShell: document.querySelectorAll('[data-app-shell="firm"]').length,
      wrapperSlot: wrap && wrap.getAttribute("data-slot"),
      wrapperBgHex: wrap && window.__hex(getComputedStyle(wrap).backgroundColor),
      inset: inset && { rect: window.__rect(inset), margin: ics.margin, radius: ics.borderRadius, shadow: ics.boxShadow, bgHex: window.__hex(ics.backgroundColor) },
      container: cont && { rect: window.__rect(cont), padding: ccs.padding, borderRight: ccs.borderRightWidth },
      header: window.__rect(q("header")),
      tokens: { "--background": tok("--background"), "--sidebar": tok("--sidebar"), "--accent": tok("--accent"), "--button-accent": tok("--button-accent"), "--primary": tok("--primary"), "--kazi-teal-700": tok("--kazi-teal-700") },
      accentButtonPaint: a0 ? { count: acc.length, tag: a0.tagName, slot: a0.getAttribute("data-slot"), text: a0.textContent.trim().slice(0, 40), bgHex: window.__hex(getComputedStyle(a0).backgroundColor), fgHex: window.__hex(getComputedStyle(a0).color) } : { count: 0 },
    };
  });
}
// Rail text pairs (light AA, check f): composite each foreground over the background it sits on.
async function railText(page) {
  return page.evaluate(() => {
    const q = (s) => document.querySelector(s);
    const inner = q('[data-slot="sidebar-inner"]');
    const railBg = getComputedStyle(inner).backgroundColor;
    const railRgb = window.__rgb(railBg);
    const pair = (name, el, onEl) => {
      if (!el) return { name, missing: true };
      const fg = getComputedStyle(el).color;
      let bgRgb = railRgb;
      if (onEl) bgRgb = window.__rgb(getComputedStyle(onEl).backgroundColor, `rgb(${railRgb.join(",")})`);
      return { name, text: el.textContent.trim().slice(0, 30), fg, fgRgb: window.__rgb(fg, `rgb(${bgRgb.join(",")})`), bgRgb };
    };
    const pill = q('[data-sidebar="sidebar"] [aria-label="Search, Command K"]');
    const active = q('[data-sidebar="menu-button"][data-active="true"]');
    const idle = q('nav[aria-label="Main navigation"] [data-sidebar="menu-button"]:not([data-active="true"])');
    const label = q('[data-sidebar="group-label"]');
    const footer = [...document.querySelectorAll('[data-sidebar="sidebar"] p')].find((p) => p.textContent.includes("@"));
    return [
      pair("zone label", label),
      pair("user-footer email", footer),
      pair("search pill text vs rail", pill),
      pair("search pill text vs pill fill", pill, pill),
      pair("idle nav item", idle),
      pair("active nav item vs active fill", active, active),
    ];
  });
}

async function sheet(browser, title, files, cols, outName) {
  const cells = files
    .map(([cap, f]) =>
      f && (f.startsWith("data:") || fs.existsSync(f))
        ? `<figure><figcaption>${cap}</figcaption><img src="${f.startsWith("data:") ? f : "data:image/png;base64," + fs.readFileSync(f).toString("base64")}"></figure>`
        : `<figure><figcaption>${cap}</figcaption><div class="miss">not captured</div></figure>`,
    )
    .join("");
  const html = `<html><body style="margin:0;background:#888;font:14px sans-serif"><h2 style="margin:8px">${title}</h2>
  <div style="display:grid;grid-template-columns:repeat(${cols},max-content);gap:8px;padding:8px">${cells}</div>
  <style>figure{margin:0;background:#fff}figcaption{padding:4px 6px;font-weight:600;max-width:1440px}img{display:block}.miss{padding:40px}</style></body></html>`;
  const p = await browser.newPage({ viewport: { width: 800, height: 600 } });
  await p.setContent(html, { waitUntil: "load" });
  await p.screenshot({ path: path.join(OUT, outName), fullPage: true });
  await p.close();
}

// ---- Pixel diff (in a blank Chromium page; no extra dependency).
// cmp({ a: afterPng, b: beforePng, ra: [x,y,w,h] crop in A, rb: [x,y] origin in B | null, search: {x0,x1,y0,y1} })
// Counts pixels whose RGB differs at all (exact) and by > 8 in any channel (tol8); with `search`, slides A's crop
// over B to the best origin first (data differs between stacks, so the same control may sit a few px apart).
let diffPage = null;
const diffs = {};
async function cmp(name, { a, b, ra, rb = null, search = null, keepVisual = false }) {
  if (!a || !b || !fs.existsSync(a) || !fs.existsSync(b)) {
    diffs[name] = { error: `missing ${!a || !fs.existsSync(a) ? "after" : "before"} tile` };
    return diffs[name];
  }
  const res = await diffPage.evaluate(
    async ({ A, B, ra, rb, search, keepVisual }) => {
      const load = (src) =>
        new Promise((ok, ko) => {
          const i = new Image();
          i.onload = () => ok(i);
          i.onerror = ko;
          i.src = src;
        });
      const [ia, ib] = await Promise.all([load(A), load(B)]);
      const data = (img) => {
        const c = document.createElement("canvas");
        c.width = img.width;
        c.height = img.height;
        const x = c.getContext("2d");
        x.drawImage(img, 0, 0);
        return x.getImageData(0, 0, img.width, img.height);
      };
      const da = data(ia);
      const db = data(ib);
      const [ax, ay, aw0, ah0] = ra || [0, 0, ia.width, ia.height];
      const aw = Math.min(aw0, ia.width - ax);
      const ah = Math.min(ah0, ia.height - ay);
      const count = (bx, by, limit) => {
        let n = 0;
        for (let y = 0; y < ah; y++) {
          const pa = ((ay + y) * da.width + ax) * 4;
          const pb = ((by + y) * db.width + bx) * 4;
          for (let x = 0; x < aw; x++) {
            const i = pa + x * 4;
            const j = pb + x * 4;
            if (da.data[i] !== db.data[j] || da.data[i + 1] !== db.data[j + 1] || da.data[i + 2] !== db.data[j + 2]) {
              if (++n > limit) return n;
            }
          }
        }
        return n;
      };
      let best = rb ? { x: rb[0], y: rb[1] } : { x: ax, y: ay };
      if (search) {
        let bn = Infinity;
        for (let y = Math.max(0, search.y0); y <= Math.min(search.y1, db.height - ah); y++)
          for (let x = Math.max(0, search.x0); x <= Math.min(search.x1, db.width - aw); x++) {
            const n = count(x, y, bn);
            if (n < bn) {
              bn = n;
              best = { x, y };
              if (n === 0) break;
            }
          }
      }
      if (best.x + aw > db.width || best.y + ah > db.height) return { error: `crop ${aw}x${ah} at ${best.x},${best.y} outside before ${db.width}x${db.height}`, sizeA: [ia.width, ia.height], sizeB: [db.width, db.height] };
      let exact = 0;
      let tol8 = 0;
      let maxDelta = 0;
      let box = null;
      const vis = keepVisual ? new ImageData(aw, ah) : null;
      for (let y = 0; y < ah; y++)
        for (let x = 0; x < aw; x++) {
          const i = ((ay + y) * da.width + ax + x) * 4;
          const j = ((best.y + y) * db.width + best.x + x) * 4;
          const d = Math.max(Math.abs(da.data[i] - db.data[j]), Math.abs(da.data[i + 1] - db.data[j + 1]), Math.abs(da.data[i + 2] - db.data[j + 2]));
          if (vis) {
            const k = (y * aw + x) * 4;
            const g = (da.data[i] + da.data[i + 1] + da.data[i + 2]) / 12 + 160;
            vis.data[k] = d ? 255 : g;
            vis.data[k + 1] = d ? 0 : g;
            vis.data[k + 2] = d ? 0 : g;
            vis.data[k + 3] = 255;
          }
          if (d) {
            exact++;
            if (d > 8) tol8++;
            if (d > maxDelta) maxDelta = d;
            box = box ? { x0: Math.min(box.x0, x), y0: Math.min(box.y0, y), x1: Math.max(box.x1, x), y1: Math.max(box.y1, y) } : { x0: x, y0: y, x1: x, y1: y };
          }
        }
      const crop = (img, sx, sy) => {
        const c = document.createElement("canvas");
        c.width = aw;
        c.height = ah;
        c.getContext("2d").drawImage(img, sx, sy, aw, ah, 0, 0, aw, ah);
        return c.toDataURL("image/png");
      };
      let visual = null;
      if (keepVisual) {
        const c = document.createElement("canvas");
        c.width = aw;
        c.height = ah;
        c.getContext("2d").putImageData(vis, 0, 0);
        visual = { before: crop(ib, best.x, best.y), after: crop(ia, ax, ay), diff: c.toDataURL("image/png") };
      }
      return { cropA: [ax, ay, aw, ah], originB: [best.x, best.y], pixels: aw * ah, exact, tol8, maxDelta, diffBox: box, sizeA: [ia.width, ia.height], sizeB: [db.width, db.height], visual };
    },
    { A: "data:image/png;base64," + fs.readFileSync(a).toString("base64"), B: "data:image/png;base64," + fs.readFileSync(b).toString("base64"), ra, rb, search, keepVisual },
  );
  const { visual, ...rest } = res;
  diffs[name] = { after: rel(a), before: rel(b), ...rest };
  if (visual) visuals.push([name, visual, rest]);
  console.log("DIFF", name, JSON.stringify(rest));
  return res;
}
const visuals = [];
const B = (n) => path.join(BEFORE_RAW, n + ".png");

(async () => {
  const browser = await chromium.launch();
  diffPage = await browser.newPage();
  await diffPage.setContent("<html><body></body></html>");
  const token = await idpToken();
  note(`out=${rel(OUT)} beforeRaw=${rel(BEFORE_RAW)} portal=${PORTAL}`);

  // ---- Login through the mock-login UI as Alice (owner).
  const lctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const lp = await lctx.newPage();
  await lp.goto(`${APP}/mock-login`, { waitUntil: "load" });
  await lp.getByRole("button", { name: "Sign In", exact: true }).click();
  await lp.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  const state = await lctx.storageState();
  await lctx.close();

  // ---- Data: same selection rule as before (ACTIVE client matter with the largest trust balance).
  const projects = list(await api(token, "/api/matters"));
  const matter = projects.filter((p) => p.status === "ACTIVE" && p.clientName).sort((x, y) => (y.trustBalance || 0) - (x.trustBalance || 0))[0] || projects[0];
  const invoices = list(await api(token, "/api/invoices"));
  const draft = invoices.find((i) => i.status === "DRAFT");
  let approved = invoices.find((i) => i.status === "APPROVED");
  const MATTER_URL = `${APP}/org/${ORG}/matters/${matter.id}`;
  note(`matter=${matter.id} (${matter.name}); invoices=${invoices.length} draft=${draft && draft.id} approved=${approved && approved.id}`);

  const probes = {};
  const live = {};
  const checks = {};

  // ---- Shell 1024/1440 x expanded/collapsed x light/dark (+ dark rail / full-tile diff vs before).
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
        const f = await shot(page, label);
        shellFiles[w].push([label, f]);
        const ex = await shellExtra(page);
        probes[`geom-${label}`] = ex;
        if (w === 1440 && !collapsed) {
          probes[`shell-${theme}`] = await probe(page, SHELL_PROBES);
          probes[`shellExtra-${theme}`] = ex;
          probes[`railText-${theme}`] = await railText(page);
        }
        if (theme === "dark") {
          const c = ex.container.rect;
          await cmp(`dark rail ${w} ${collapsed ? "collapsed" : "expanded"}`, { a: f, b: B(label), ra: [0, 0, Math.ceil(c.right), h], keepVisual: w === 1440 && !collapsed });
          await cmp(`dark full tile ${w} ${collapsed ? "collapsed" : "expanded"}`, { a: f, b: B(label) });
        }
        if (w === 1440 && !collapsed && theme === "light") {
          for (const [k, u] of [
            ["matter", MATTER_URL],
            ["dashboard", `${APP}/org/${ORG}/dashboard`],
            ["fee-notes", `${APP}/org/${ORG}/invoices`],
            ["matters-list", `${APP}/org/${ORG}/matters`],
          ]) {
            await settle(page, u);
            live[k] = await page.evaluate(() => ({
              mainCount: document.querySelectorAll("main").length,
              mainContentCount: document.querySelectorAll("main#main-content").length,
              h1InMain: document.querySelectorAll("#main-content h1").length,
              h1Text: [...document.querySelectorAll("#main-content h1")].map((e) => e.textContent.trim()),
              mainNav: document.querySelectorAll('nav[aria-label="Main navigation"]').length,
              searchCmdK: document.querySelectorAll('[aria-label="Search, Command K"]').length,
              exactSearchButtons: [...document.querySelectorAll("button")].filter((b) => (b.getAttribute("aria-label") || b.textContent.trim()) === "Search").length,
              brandAccent: document.querySelectorAll('[data-testid="org-brand-accent"]').length,
              dataAppShellFirm: document.querySelectorAll('[data-app-shell="firm"]').length,
            }));
          }
        }
        await ctx.close();
      }
    }
  }
  await sheet(browser, "661B after — shell 1024x768 (matter detail)", shellFiles[1024], 2, "shell-1024.png");
  await sheet(browser, "661B after — shell 1440x900 (matter detail)", shellFiles[1440], 2, "shell-1440.png");

  // ---- (a) F-661A-8: collapsed rail at 1024x768 on the fee-notes page — the active "Fee notes" item renders whole.
  const feeFiles = [];
  for (const theme of ["light", "dark"]) {
    const ctx = await newCtx(browser, state, { width: 1024, height: 768, theme, collapsed: true });
    const page = await ctx.newPage();
    watch(page, `fee-notes-collapsed-${theme}`);
    await settle(page, `${APP}/org/${ORG}/invoices`);
    const g = await page.evaluate(() => {
      const act = document.querySelector('[data-sidebar="menu-button"][data-active="true"]');
      const content = document.querySelector('[data-slot="sidebar-content"]');
      const ra = window.__rect(act);
      const rc = window.__rect(content);
      return {
        active: act && { text: act.textContent.trim(), label: act.getAttribute("aria-label") || act.querySelector("span")?.textContent, rect: ra },
        content: rc && { rect: rc, scrollTop: content.scrollTop, scrollHeight: content.scrollHeight, clientHeight: content.clientHeight },
        container: window.__rect(document.querySelector('[data-slot="sidebar-container"]')),
        inner: window.__rect(document.querySelector('[data-slot="sidebar-inner"]')),
        whole: !!(ra && rc && ra.y >= rc.y - 0.5 && ra.bottom <= rc.bottom + 0.5 && ra.bottom <= window.innerHeight),
      };
    });
    checks[`a-feeNotesCollapsed1024-${theme}`] = g;
    probes[`shellExtra-fee-notes-1024-collapsed-${theme}`] = await shellExtra(page);
    const r = g.active && g.active.rect;
    feeFiles.push([`fee-notes 1024x768 collapsed ${theme} — active "${g.active && g.active.text}" y=${r && r.y}..${r && r.bottom}, content ends ${g.content && g.content.rect.bottom}, whole=${g.whole}`, await shot(page, `fee-notes-1024-collapsed-${theme}`)]);
    await ctx.close();
  }
  await sheet(browser, "661B after — (a) F-661A-8: collapsed rail, fee-notes page, 1024x768", feeFiles, 2, "fee-notes-1024-collapsed.png");

  // ---- (b) Header inside the inset card; GroupedTabBar and DataTable sticky heads land under the header.
  const stickyFiles = [];
  for (const theme of ["light", "dark"]) {
    for (const [w, h] of [
      [1440, 900],
      [1024, 768],
    ]) {
      const ctx = await newCtx(browser, state, { width: w, height: h, theme });
      const page = await ctx.newPage();
      watch(page, `sticky-${w}-${theme}`);
      const rec = {};
      for (const [k, u, sel] of [
        ["matterTabs", MATTER_URL, '[data-slot="grouped-tab-bar-sticky"]'],
        // The matters table sets stickyHeader={false} (matters-table.tsx:80); the clients table is the sticky
        // DataTable with default columns (clients-table.tsx:90).
        ["clientsListThead", `${APP}/org/${ORG}/clients`, '[data-testid="clients-table"] thead th'],
      ]) {
        await settle(page, u);
        const at0 = await page.evaluate((s) => ({ scrollY: window.scrollY, header: window.__rect(document.querySelector("header")), inset: window.__rect(document.querySelector('[data-slot="sidebar-inset"]')), target: window.__rect(document.querySelector(s)) }), sel);
        await page.evaluate(() => window.scrollTo(0, 700));
        await page.waitForTimeout(500);
        const at1 = await page.evaluate((s) => {
          const t = document.querySelector(s);
          return { scrollY: window.scrollY, maxScroll: document.scrollingElement.scrollHeight - window.innerHeight, header: window.__rect(document.querySelector("header")), target: window.__rect(t), targetSticky: t && getComputedStyle(t).position, targetTop: t && getComputedStyle(t).top };
        }, sel);
        rec[k] = {
          url: u.replace(APP, ""),
          atTop: at0,
          scrolled: at1,
          headerTopVsCardTop: at0.header && at0.inset ? +(at0.header.y - at0.inset.y).toFixed(1) : null,
          headerInsideCard: !!(at0.header && at0.inset && at0.header.x >= at0.inset.x && at0.header.right <= at0.inset.right && at0.header.y >= at0.inset.y),
          gapTargetToHeaderBottom: at1.target && at1.header ? +(at1.target.y - at1.header.bottom).toFixed(1) : null,
        };
        if (w === 1440) stickyFiles.push([`${k} ${w} ${theme} scrolled ${at1.scrollY}px — header y=${at1.header && at1.header.y}..${at1.header && at1.header.bottom}, target y=${at1.target && at1.target.y}`, await shot(page, `sticky-${k}-${w}-${theme}`)]);
      }
      checks[`b-sticky-${w}-${theme}`] = rec;
      await ctx.close();
    }
  }
  await sheet(browser, "661B after — (b) header in the inset card; sticky tab bar / table head under it (scrolled 700px)", stickyFiles, 2, "sticky-scrolled.png");

  // ---- (c) 390 with the mobile Sheet open (light + dark), as before.
  const mob = [];
  for (const theme of ["light", "dark"]) {
    const ctx = await newCtx(browser, state, { width: 390, height: 844, theme });
    const page = await ctx.newPage();
    watch(page, `390-${theme}`);
    await settle(page, MATTER_URL);
    const fc = await shot(page, `390-closed-${theme}`);
    mob.push([`390-closed-${theme}`, fc]);
    const closedGeom = await shellExtra(page);
    await page.getByRole("button", { name: "Toggle menu" }).first().click();
    await page.locator('[data-mobile="true"][data-sidebar="sidebar"]').waitFor({ state: "visible" });
    await page.waitForTimeout(600);
    const fs1 = await shot(page, `390-sheet-${theme}`);
    mob.push([`390-sheet-open-${theme}`, fs1]);
    probes[`sheet390-${theme}`] = await probe(page, { sheet: '[data-mobile="true"][data-sidebar="sidebar"]' });
    const sheetRect = await page.evaluate(() => window.__rect(document.querySelector('[data-mobile="true"][data-sidebar="sidebar"]')));
    checks[`c-390-${theme}`] = { closed: { inset: closedGeom.inset, container: closedGeom.container, wrapperBgHex: closedGeom.wrapperBgHex }, sheetRect };
    if (theme === "dark") await cmp("dark 390 sheet region", { a: fs1, b: B("390-sheet-dark"), ra: [0, 0, Math.ceil(sheetRect.right), 844] });
    await ctx.close();
  }
  await sheet(browser, "661B after — 390x844, mobile Sheet", mob, 4, "shell-390-sheet.png");

  // ---- (c) Exactly 768 px wide (CSS @media width >= 48rem and useIsMobile both desktop) and 767 (both mobile).
  const w768 = [];
  for (const [w, theme] of [
    [768, "light"],
    [768, "dark"],
    [767, "light"],
  ]) {
    const ctx = await newCtx(browser, state, { width: w, height: 1024, theme });
    const page = await ctx.newPage();
    watch(page, `${w}-${theme}`);
    await settle(page, MATTER_URL);
    const ex = await shellExtra(page);
    const mq = await page.evaluate(() => ({ innerWidth: window.innerWidth, cssDesktop: matchMedia("(width >= 48rem)").matches, hookMobileQuery: matchMedia("(max-width: 767px)").matches, desktopRailRendered: !!document.querySelector('[data-slot="sidebar-container"]'), toggleMenuVisible: [...document.querySelectorAll("button")].some((b) => (b.getAttribute("aria-label") || b.textContent).includes("Toggle") && b.getBoundingClientRect().width > 0), scrollWidth: document.scrollingElement.scrollWidth }));
    checks[`c-${w}-${theme}`] = { ...mq, inset: ex.inset, container: ex.container, wrapperBgHex: ex.wrapperBgHex };
    w768.push([`${w}x1024 ${theme} — cssDesktop=${mq.cssDesktop} hookMobile=${mq.hookMobileQuery} rail=${mq.desktopRailRendered} insetMargin=${ex.inset && ex.inset.margin}`, await shot(page, `w${w}-${theme}`)]);
    await ctx.close();
  }
  await sheet(browser, "661B after — (c) 768 (desktop by CSS and hook) and 767 (mobile by both)", w768, 3, "shell-768.png");

  // ---- Marketing / at 1024 light (no auth) — (e) pixel-identical to before.
  {
    const ctx = await browser.newContext({ viewport: { width: 1024, height: 768 }, deviceScaleFactor: 1 });
    const page = await ctx.newPage();
    watch(page, "marketing");
    await page.goto(`${APP}/`, { waitUntil: "load" });
    await page.waitForTimeout(1500);
    pagesSeen.push("/ (marketing)");
    await page.screenshot({ path: path.join(OUT, "marketing-1024-light.png") });
    probes["marketing-light"] = await page.evaluate(() => {
      const r = getComputedStyle(document.documentElement);
      const acc = [...document.querySelectorAll('[data-variant="accent"]')][0];
      return { dataAppShell: document.querySelectorAll("[data-app-shell]").length, accent: r.getPropertyValue("--accent").trim(), buttonAccent: r.getPropertyValue("--button-accent").trim(), background: r.getPropertyValue("--background").trim(), accentButton: acc ? { text: acc.textContent.trim().slice(0, 30), bg: getComputedStyle(acc).backgroundColor } : null, bodyBg: getComputedStyle(document.body).backgroundColor };
    });
    await page.evaluate(() => document.querySelectorAll('[data-variant="accent"]').forEach((e) => (e.style.color = "rgb(255, 255, 255)")));
    await page.waitForTimeout(200);
    await page.screenshot({ path: path.join(RAW, "marketing-1024-light-COUNTERFACTUAL-fg-rgb-white.png") });
    await ctx.close();
    await cmp("COUNTERFACTUAL marketing / with accent fg rgb(255,255,255)", { a: path.join(RAW, "marketing-1024-light-COUNTERFACTUAL-fg-rgb-white.png"), b: path.join(W, "qa_cycle/evidence/phase91-ui/661B/before/marketing-1024-light.png") });
    await cmp("marketing / 1024 light (full tile)", { a: path.join(OUT, "marketing-1024-light.png"), b: path.join(W, "qa_cycle/evidence/phase91-ui/661B/before/marketing-1024-light.png") });
  }

  // ---- This stack ran no default suite, so it may hold no APPROVED fee note: approve the LAST draft (never the one
  // used for the "Approve" shot) through the UI, as a user would, to get a "Send fee note" header.
  if (!approved) {
    const drafts = invoices.filter((i) => i.status === "DRAFT");
    const cand = drafts.length > 1 ? drafts[drafts.length - 1] : null;
    if (cand) {
      const ctx = await newCtx(browser, state, { width: 1440, height: 900, theme: "light" });
      const page = await ctx.newPage();
      watch(page, "approve-setup");
      await page.goto(`${APP}/org/${ORG}/invoices/${cand.id}`, { waitUntil: "load" });
      try {
        const ab = page.getByRole("button", { name: "Approve", exact: true }).first();
        await ab.waitFor({ state: "visible", timeout: 30000 });
        await page.waitForTimeout(800);
        await ab.click();
        await page.getByRole("button", { name: "Send fee note", exact: true }).first().waitFor({ state: "visible", timeout: 30000 });
        approved = cand;
        note(`setup: approved draft ${cand.id} via the UI "Approve" button (no APPROVED fee note on this stack)`);
      } catch (e) {
        note(`setup: approving ${cand.id} failed: ${String(e).slice(0, 150)}`);
      }
      await ctx.close();
    }
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
      pagesSeen.push(`/org/${ORG}/invoices/<${kind}>`);
      const b = page.getByRole("button", { name: btn, exact: true }).first();
      try {
        await b.waitFor({ state: "visible", timeout: 30000 });
        await page.waitForTimeout(800);
        const box = await b.boundingBox();
        const mb = await page.locator("main#main-content").boundingBox();
        const clip = { x: mb.x, y: Math.max(0, box.y - 110), width: 1440 - mb.x, height: 200 };
        const f = await shot(page, `${kind}-${theme}`, { clip });
        accent.push([`${kind}-${theme} ("${btn}")`, f]);
        // carry-over 6: any element carrying data-variant="accent" (not only data-slot="button").
        probes[`accent-${kind}-${theme}`] = await page.evaluate(
          ([name]) => {
            const all = [...document.querySelectorAll('[data-variant="accent"]')];
            const el = all.find((e) => e.textContent.trim() === name);
            if (!el) return { sel: `[data-variant=accent] "${name}"`, found: false, accentCount: all.length };
            const cs = getComputedStyle(el);
            return { sel: `[data-variant=accent] "${name}"`, tag: el.tagName, slot: el.getAttribute("data-slot"), accentCount: all.length, bg: cs.backgroundColor, bgHex: window.__hex(cs.backgroundColor), fg: cs.color, fgHex: window.__hex(cs.color), bgRgb: window.__rgb(cs.backgroundColor), fgRgb: window.__rgb(cs.color, cs.backgroundColor), html: document.documentElement.className };
          },
          [btn],
        );
        // Button-only crop, located in the before tile by search (the header row differs by data).
        const bx = Math.round(box.x - clip.x);
        const by = Math.round(box.y - clip.y);
        if (theme === "dark") {
          await cmp(`dark accent button "${btn}" (crop)`, { a: f, b: B(`${kind}-${theme}`), ra: [bx, by, Math.round(box.width), Math.round(box.height)], search: { x0: 0, x1: 1440, y0: by - 12, y1: by + 12 }, keepVisual: true });
          // Counterfactual (named apart, never the as-rendered file): label colour forced to before's computed
          // rgb(255, 255, 255) (text-white) instead of the slice's oklch(100% 0 0) -> does the crop diff vanish?
          await b.evaluate((e) => (e.style.color = "rgb(255, 255, 255)"));
          await page.waitForTimeout(200);
          const cf = await shot(page, `${kind}-${theme}-COUNTERFACTUAL-fg-rgb-white`, { clip });
          await cmp(`COUNTERFACTUAL dark accent button "${btn}" with fg rgb(255,255,255)`, { a: cf, b: B(`${kind}-${theme}`), ra: [bx, by, Math.round(box.width), Math.round(box.height)], search: { x0: 0, x1: 1440, y0: by - 12, y1: by + 12 } });
        }
      } catch (e) {
        note(`${kind}-${theme}: button "${btn}" not visible: ${String(e).slice(0, 120)}`);
        accent.push([`${kind}-${theme}`, await shot(page, `${kind}-${theme}-page`)]);
      }
      await ctx.close();
    }
  }
  await sheet(browser, "661B after — fee-note header accent buttons (light | dark)", accent, 2, "dark-accent-buttons.png");

  // ---- Open row menu with a keyboard-focused item (light + dark), as before; (d) dark diff; (h) light portal tokens.
  const menus = [];
  for (const theme of ["light", "dark"]) {
    const ctx = await newCtx(browser, state, { width: 1440, height: 900, theme });
    const page = await ctx.newPage();
    watch(page, `rowmenu-${theme}`);
    let ok = false;
    for (const u of [`${APP}/org/${ORG}/settings/matters?tab=custom-fields`, `${APP}/org/${ORG}/diary`]) {
      await page.goto(u, { waitUntil: "load" });
      pagesSeen.push(u.replace(APP, ""));
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
          const menuEl = el.closest('[role="menu"]');
          const mcs = getComputedStyle(menuEl);
          return { sel: "[role=menuitem]:focus", slot: el.getAttribute("data-slot"), variant: el.getAttribute("data-variant"), text: el.textContent.trim(), bg: cs.backgroundColor, bgHex: window.__hex(cs.backgroundColor), fg: cs.color, fgHex: window.__hex(cs.color), bgRgb: window.__rgb(cs.backgroundColor, mcs.backgroundColor), fgRgb: window.__rgb(cs.color, cs.backgroundColor), rect: window.__rect(el), portalAccentVar: mcs.getPropertyValue("--accent").trim(), portalInBody: menuEl.closest("[data-radix-popper-content-wrapper]")?.parentElement === document.body, html: document.documentElement.className };
        });
      const keyTiles = [];
      for (const step of [1, 2]) {
        await page.keyboard.press("ArrowDown");
        await page.waitForTimeout(400);
        const mb = await menu.boundingBox();
        const clip = { x: Math.max(0, mb.x - 420), y: Math.max(0, mb.y - 60), width: Math.min(820, 1440 - Math.max(0, mb.x - 420)), height: Math.min(900 - Math.max(0, mb.y - 60), mb.height + 120) };
        const pr = await focusedProbe();
        probes[`menuFocused${step}-${theme}`] = pr;
        const f = await shot(page, `rowmenu-${theme}-${step}`, { clip });
        menus.push([`row-menu-${theme}, ArrowDown x${step} → "${pr && pr.text}" (${u.split(ORG)[1]})`, f]);
        keyTiles.push([f, clip, pr]);
      }
      if (theme === "dark") {
        for (const [i, [f, clip, pr]] of keyTiles.entries()) {
          await cmp(`dark row menu ArrowDown x${i + 1} (full tile)`, { a: f, b: B(`rowmenu-dark-${i + 1}`) });
          if (pr && pr.rect) {
            const ix = Math.round(pr.rect.x - clip.x);
            const iy = Math.round(pr.rect.y - clip.y);
            await cmp(`dark focused menu item "${pr.text}" (crop)`, { a: f, b: B(`rowmenu-dark-${i + 1}`), ra: [ix, iy, Math.round(pr.rect.w), Math.round(pr.rect.h)], search: { x0: ix - 40, x1: ix + 40, y0: iy - 40, y1: iy + 40 }, keepVisual: i === 0 });
          }
        }
      }
      ok = true;
      break;
    }
    if (!ok) menus.push([`row-menu-${theme}`, null]);
    await ctx.close();
  }
  await sheet(browser, "661B after — open row menu, item focused via ArrowDown (light | dark)", menus, 2, "dark-row-menu-focused.png");

  // ---- Controls: theme toggle (user menu), dialog close button (+ h: dialog tokens), Select open — light, then dark.
  for (const theme of ["light", "dark"]) {
    const ctrl = [];
    const ctx = await newCtx(browser, state, { width: 1440, height: 900, theme });
    const page = await ctx.newPage();
    watch(page, `controls-${theme}`);
    await settle(page, `${APP}/org/${ORG}/dashboard`);
    try {
      await page.locator("header").getByRole("button", { name: /^[A-Z]{1,2}$/ }).first().click();
      const tg = page.getByRole("group", { name: "Theme" });
      await tg.waitFor({ state: "visible", timeout: 10000 });
      await page.waitForTimeout(400);
      const m = await tg.locator('xpath=ancestor::div[contains(@class,"absolute")][1]').boundingBox();
      ctrl.push([`theme toggle (User menu) ${theme}`, await shot(page, `theme-toggle-${theme}`, { clip: { x: Math.max(0, m.x - 40), y: Math.max(0, m.y - 20), width: Math.min(m.width + 80, 1440), height: Math.min(m.height + 40, 900) } })]);
      probes[`themeToggle-${theme}`] = await page.evaluate(() => {
        const items = [...document.querySelectorAll('[role="group"][aria-label="Theme"] [role="radio"], [role="group"][aria-label="Theme"] button')];
        return items.map((e) => {
          const cs = getComputedStyle(e);
          return { text: e.textContent.trim(), state: e.getAttribute("data-state"), bgHex: window.__hex(cs.backgroundColor), fgHex: window.__hex(cs.color), bgRgb: window.__rgb(cs.backgroundColor), fgRgb: window.__rgb(cs.color, cs.backgroundColor) };
        });
      });
      await page.mouse.click(700, 600);
    } catch (e) {
      note(`theme toggle ${theme}: ${String(e).slice(0, 150)}`);
      ctrl.push([`theme toggle ${theme}`, null]);
    }
    try {
      await settle(page, `${APP}/org/${ORG}/matters`);
      await page.getByRole("button", { name: "New Matter" }).first().click();
      const dlg = page.locator('[data-slot="dialog-content"]').first();
      await dlg.waitFor({ state: "visible", timeout: 10000 });
      await page.waitForTimeout(500);
      const close = dlg.locator('[data-slot="dialog-close"]').last();
      await close.waitFor({ state: "visible" });
      const db = await dlg.boundingBox();
      ctrl.push([`dialog close button (New Matter header) ${theme}`, await shot(page, `dialog-close-${theme}`, { clip: { x: db.x - 10, y: db.y - 10, width: db.width + 20, height: 110 } })]);
      probes[`dialogClose-${theme}`] = await close.evaluate((e) => ({ opacity: getComputedStyle(e).opacity, fgHex: window.__hex(getComputedStyle(e).color), bgHex: window.__hex(getComputedStyle(e).backgroundColor) }));
      // (h) A Radix portal under <body> receives the firm tokens (they sit on :root).
      probes[`dialogTokens-${theme}`] = await dlg.evaluate((d) => {
        const cs = getComputedStyle(d);
        const btns = [...d.querySelectorAll('button[data-variant="default"], [data-slot="button"][data-variant="default"]')].filter((b) => b.getBoundingClientRect().width > 0);
        const b = btns[btns.length - 1];
        return { portalOutsideFirmShell: !d.closest('[data-app-shell="firm"]'), accentVar: cs.getPropertyValue("--accent").trim(), accentHex: window.__hex(cs.getPropertyValue("--accent").trim()), primaryHex: window.__hex(cs.getPropertyValue("--primary").trim()), dialogBgHex: window.__hex(cs.backgroundColor), primaryButton: b ? { text: b.textContent.trim(), bgHex: window.__hex(getComputedStyle(b).backgroundColor), fgHex: window.__hex(getComputedStyle(b).color) } : null };
      });
      ctrl.push([`dialog (New Matter) full ${theme}`, await shot(page, `dialog-full-${theme}`)]);
      await page.keyboard.press("Escape");
    } catch (e) {
      note(`dialog close ${theme}: ${String(e).slice(0, 150)}`);
      ctrl.push([`dialog close button ${theme}`, null]);
    }
    let sel = false;
    for (const u of [`${APP}/org/${ORG}/settings/firm?tab=general`, `${APP}/org/${ORG}/settings/firm?tab=audit-trail`, `${APP}/org/${ORG}/invoices/unbilled`]) {
      try {
        await page.goto(u, { waitUntil: "load" });
        pagesSeen.push(u.replace(APP, ""));
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
        ctrl.push([`Select open ${theme} (${u.split(ORG)[1]})`, await shot(page, `select-open-${theme}`, { clip: { x, y, width: x2 - x, height: y2 - y } })]);
        probes[`selectHighlighted-${theme}`] = await page.evaluate(() => {
          const el = document.querySelector('[data-slot="select-item"][data-highlighted]') || document.querySelector('[data-slot="select-item"]:focus');
          if (!el) return null;
          const cs = getComputedStyle(el);
          const pcs = getComputedStyle(el.closest('[data-slot="select-content"]'));
          return { text: el.textContent.trim(), bg: cs.backgroundColor, bgHex: window.__hex(cs.backgroundColor), fg: cs.color, fgHex: window.__hex(cs.color), bgRgb: window.__rgb(cs.backgroundColor, pcs.backgroundColor), fgRgb: window.__rgb(cs.color, cs.backgroundColor) };
        });
        await page.keyboard.press("Escape");
        sel = true;
        break;
      } catch (e) {
        note(`select ${theme} on ${u}: ${String(e).slice(0, 120)}`);
      }
    }
    if (!sel) ctrl.push([`Select open ${theme}`, null]);
    await ctx.close();
    await sheet(browser, `661B after — ${theme} controls: theme toggle, dialog close, Select open`, ctrl, 2, `${theme}-controls.png`);
  }

  // ---- Performance proxy (§18.6.4): identical method to before — CDP Performance.getMetrics delta over a matter
  // page load from the settled dashboard, 3 runs per theme, 1440x900, plus one light trace. Checks the :has() cost.
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

  // ---- Portal /login and /home at 1024 light, as before — (e) pixel-identical.
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
    const portalProbe = () =>
      page.evaluate(() => {
        const r = getComputedStyle(document.documentElement);
        const btn = document.querySelector("button");
        return { accent: r.getPropertyValue("--accent").trim(), buttonAccent: r.getPropertyValue("--button-accent").trim(), background: r.getPropertyValue("--background").trim(), primary: r.getPropertyValue("--primary").trim(), bodyBg: getComputedStyle(document.body).backgroundColor, firstButton: btn ? { text: btn.textContent.trim().slice(0, 30), bg: getComputedStyle(btn).backgroundColor, fg: getComputedStyle(btn).color } : null };
      });
    await page.goto(`${PORTAL}/login`, { waitUntil: "load" });
    await page.locator("input, button").first().waitFor({ state: "visible", timeout: 60000 });
    await page.waitForTimeout(1000);
    const fl = await shot(page, "portal-login");
    portalFiles.push(["portal /login 1024 light", fl]);
    probes["portal-login"] = await portalProbe();
    await page.evaluate(() => document.querySelectorAll('[data-variant="accent"]').forEach((e) => (e.style.color = "rgb(255, 255, 255)")));
    await page.waitForTimeout(200);
    const flCf = await shot(page, "portal-login-COUNTERFACTUAL-fg-rgb-white");
    await cmp("COUNTERFACTUAL portal /login with accent fg rgb(255,255,255)", { a: flCf, b: B("portal-login") });
    await page.evaluate((x) => {
      localStorage.setItem("portal_jwt", x.token);
      localStorage.setItem("portal_customer", JSON.stringify({ id: x.clientId, name: x.clientName, email: x.email, orgId: x.orgId }));
      localStorage.setItem("portal_last_org_id", x.orgId);
    }, { token: s.token, clientId: s.clientId, clientName: s.clientName, email, orgId: ORG });
    await page.goto(`${PORTAL}/home`, { waitUntil: "load" });
    await page.locator("h1").first().waitFor({ state: "visible", timeout: 60000 });
    await page.waitForTimeout(2500);
    note(`portal /home url=${page.url().replace(PORTAL, "<portal>")} h1=${await page.locator("h1").first().textContent()}`);
    const fh = await shot(page, "portal-home");
    portalFiles.push(["portal /home 1024 light", fh]);
    probes["portal-home"] = await portalProbe();
    await ctx.close();
    await cmp("portal /login 1024 light (full tile)", { a: fl, b: B("portal-login"), keepVisual: false });
    await cmp("portal /home 1024 light (full tile)", { a: fh, b: B("portal-home"), keepVisual: false });
  } catch (e) {
    note(`portal: ${String(e).slice(0, 300)}`);
  }
  await sheet(browser, "661B after — portal 1024 light", portalFiles.length ? portalFiles : [["portal", null]], 2, "portal-1024-light.png");

  // ---- (d) Pixel-diff contact sheet: before | after | diff (red = any RGB difference) for the crops kept.
  if (visuals.length) {
    const cells = [];
    for (const [name, v, r] of visuals) {
      cells.push([`${name} — BEFORE`, v.before], [`AFTER`, v.after], [`DIFF: ${r.exact} px differ (tol8 ${r.tol8}, max Δ ${r.maxDelta}) of ${r.pixels}`, v.diff]);
    }
    await sheet(browser, "661B after — (d) dark pixel identity, before | after | diff", cells, 3, "dark-pixel-diff.png");
  }

  // ---- (f) Measured contrast from the probed paint (WCAG 2.x, unrounded inputs).
  const aa = [];
  const add = (pair, fgRgb, bgRgb, floor) => {
    if (!fgRgb || !bgRgb) return aa.push({ pair, missing: true });
    const r = ratio(fgRgb, bgRgb);
    aa.push({ pair, fg: hex3(fgRgb), bg: hex3(bgRgb), ratio: r, floor, pass: r >= floor });
  };
  for (const theme of ["light", "dark"]) {
    const a1 = probes[`accent-draft-approve-${theme}`];
    if (a1 && a1.bgRgb) add(`${theme}: accent button "Approve" white on fill`, a1.fgRgb, a1.bgRgb, 4.5);
    const a2 = probes[`accent-approved-send-${theme}`];
    if (a2 && a2.bgRgb) add(`${theme}: accent button "Send fee note" white on fill`, a2.fgRgb, a2.bgRgb, 4.5);
    for (const s of [1, 2]) {
      const m = probes[`menuFocused${s}-${theme}`];
      if (m) add(`${theme}: focused menu item "${m.text}"`, m.fgRgb, m.bgRgb, 4.5);
    }
    const sh = probes[`selectHighlighted-${theme}`];
    if (sh) add(`${theme}: Select highlighted item "${sh.text}"`, sh.fgRgb, sh.bgRgb, 4.5);
    const tt = (probes[`themeToggle-${theme}`] || []).find((x) => x.state === "on" || x.state === "checked");
    if (tt) add(`${theme}: theme toggle "${tt.text}" (on)`, tt.fgRgb, tt.bgRgb, 4.5);
    for (const p of probes[`railText-${theme}`] || []) if (!p.missing) add(`${theme}: rail ${p.name} "${p.text}"`, p.fgRgb, p.bgRgb, 4.5);
  }
  checks.f_aa = aa;
  checks.g_console = {
    pagesSeen: [...new Set(pagesSeen.map((u) => u.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/g, "<id>")))],
    hydration: consoleMsgs.filter((m) => /hydrat/i.test(m.text)).length,
    errors: consoleMsgs.filter((m) => m.type !== "warning").length,
    warnings: consoleMsgs.filter((m) => m.type === "warning").length,
  };

  fs.writeFileSync(path.join(OUT, "probes.json"), JSON.stringify(probes) + "\n");
  fs.writeFileSync(path.join(OUT, "perf.json"), JSON.stringify(perf) + "\n");
  fs.writeFileSync(path.join(OUT, "live-selectors.json"), JSON.stringify(live) + "\n");
  fs.writeFileSync(path.join(OUT, "console.json"), JSON.stringify(consoleMsgs) + "\n");
  fs.writeFileSync(path.join(OUT, "diffs.json"), JSON.stringify(diffs) + "\n");
  fs.writeFileSync(path.join(OUT, "checks.json"), JSON.stringify(checks) + "\n");
  fs.writeFileSync(path.join(OUT, "notes.json"), JSON.stringify(notes) + "\n");
  await browser.close();
  console.log("DONE", rel(OUT));
})().catch((e) => {
  console.error("FATAL", e);
  fs.writeFileSync(path.join(OUT, "notes.json"), JSON.stringify([...notes, "FATAL " + String(e)]) + "\n");
  process.exit(1);
});
