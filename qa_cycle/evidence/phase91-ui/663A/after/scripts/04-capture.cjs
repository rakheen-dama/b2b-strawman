// 663A OBS-after step 4 (copied from before/scripts/03-capture.cjs): capture every empty-state surface of task 663.2 at 1024x768 and 1440x900,
// light and dark (localStorage `kazi-theme`, as 661B/662A), as Alice (owner) and Carol (member);
// probe each EmptyState box in its real container; record console errors / hydration warnings.
// Read-only apart from clicking tabs (Comments, History). Raw PNG/JSON go to .tmp/663A-after-raw
// (git-ignored); 05-assemble.cjs builds the committed sheets/JSON from them.
// Prereqs: 02-create-task.cjs and 03-setup-fields.cjs have run (task.json, setup.json, state-*.json).
// Usage (worktree root): NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/663A/after/scripts/04-capture.cjs
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");
const RAW = path.join(C.W, ".tmp/663A-after-raw");
const BASE = `${C.APP}/org/${C.ORG}`;
const FORCE = !!process.env.FORCE;
const VP = { 1024: { width: 1024, height: 768 }, 1440: { width: 1440, height: 900 } };
const task = JSON.parse(fs.readFileSync(path.join(RAW, "task.json"), "utf8"));
const setup = JSON.parse(fs.readFileSync(path.join(RAW, "setup.json"), "utf8"));
const SHEET_EMPTY = '[role="dialog"] [role="tabpanel"][data-state="active"]';

// Fee-note id: harvested from the /invoices list as Alice (first DRAFT-free detail link).
async function feeNoteId(browser) {
  const f = path.join(RAW, "invoice.json");
  if (fs.existsSync(f)) return JSON.parse(fs.readFileSync(f, "utf8")).id;
  const ctx = await C.newCtx(browser, state("alice"), VP[1440]);
  const p = await ctx.newPage();
  await p.goto(`${BASE}/invoices`, { waitUntil: "load" });
  await p.locator("#main-content h1").first().waitFor({ timeout: 30000 });
  await p.waitForTimeout(2000);
  const hrefs = await p.evaluate(() => [...document.querySelectorAll("a[href]")].map((a) => a.getAttribute("href")));
  const id = hrefs.map((h) => h && h.split(/[?#]/)[0].match(/\/invoices\/([0-9a-f-]{36})$/)).find(Boolean)[1];
  fs.writeFileSync(f, JSON.stringify({ id }));
  await ctx.close();
  return id;
}
const states = {};
const state = (who) => (states[who] ||= JSON.parse(fs.readFileSync(path.join(RAW, `state-${who}.json`), "utf8")));

function surfaces(inv) {
  const taskUrl = `${BASE}/matters/${task.matterId}?tab=tasks&taskId=${task.taskId}`;
  const sheetReady = async (p) => {
    await p.getByRole("dialog").getByText(task.title).first().waitFor({ timeout: 30000 });
    await p.getByText("Loading time entries...").waitFor({ state: "hidden", timeout: 20000 }).catch(() => {});
    await p.waitForTimeout(1200);
  };
  return [
    { key: "task-sheet-time-entries", url: taskUrl, ready: sheetReady, target: `${SHEET_EMPTY} [data-slot="empty-state"]`, scope: '[role="dialog"]' },
    {
      key: "task-sheet-comments", url: taskUrl, scope: '[role="dialog"]', target: `${SHEET_EMPTY} [data-slot="empty-state"]`,
      ready: async (p) => {
        await sheetReady(p);
        await p.getByRole("dialog").getByRole("tab", { name: "Comments" }).click();
        await p.waitForTimeout(2000);
      },
    },
    { key: "matter-fields-tab", url: `${BASE}/matters/${setup.matterId}?tab=fields`, target: '[data-testid="matter-fields-tab"]' },
    { key: "client-fields-tab", url: `${BASE}/clients/${setup.clientId}?tab=fields`, target: '[data-testid="client-fields-tab"]' },
    { key: "fee-note-custom-fields", url: `${BASE}/invoices/${inv}`, target: '#main-content [data-slot="empty-state"]' },
    { key: "my-work", url: `${BASE}/my-work`, full: true },
    { key: "ai-reviews-pending", url: `${BASE}/ai/reviews` },
    {
      key: "ai-reviews-history", url: `${BASE}/ai/reviews`,
      ready: async (p) => {
        const t = p.getByRole("tab", { name: "History" });
        if (await t.count()) { await t.click(); await p.waitForTimeout(1200); }
      },
    },
    { key: "dashboard-my-tasks-widget", url: `${BASE}/dashboard`, target: '[data-testid="my-tasks-widget"]' }, // after: full 1024/1440 x light/dark grid (compact box)
  ];
}

// In-page probe: every visible EmptyState (shared component), the AI-reviews local look-alike, the
// hand-rolled matter-fields org-empty block; title tag/role, box size, container, ancestor testids.
function probe({ scope }) {
  const vis = (e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== "hidden"; };
  const root = (scope && document.querySelector(scope)) || document.querySelector("#main-content") || document.body;
  const anc = (e) => { const out = []; for (let n = e.parentElement; n && out.length < 6; n = n.parentElement) { const t = n.getAttribute("data-testid"); if (t) out.push(t); } return out; };
  const container = (e) => (e.closest('[role="dialog"]') ? "sheet/dialog" : e.closest('[data-slot="card"],[data-testid$="-widget"]') ? "card" : e.closest('[role="tabpanel"]') ? "tabpanel" : "page");
  const box = (e, kind, titleEl, descEl) => {
    const r = e.getBoundingClientRect();
    return {
      kind, variant: e.getAttribute("data-variant") || null, h: +r.height.toFixed(1), w: +r.width.toFixed(1),
      title: titleEl ? titleEl.textContent.trim() : null, titleTag: titleEl ? titleEl.tagName.toLowerCase() : null,
      titleRole: titleEl ? titleEl.getAttribute("role") || (/^h[1-6]$/i.test(titleEl.tagName) ? "heading(implicit)" : null) : null,
      description: descEl ? descEl.textContent.trim().slice(0, 160) : null,
      actions: [...e.querySelectorAll("a,button")].map((a) => `${a.tagName.toLowerCase()}:${a.textContent.trim()}${a.getAttribute("href") ? " -> " + a.getAttribute("href") : ""}`),
      testid: e.getAttribute("data-testid"), ancestorTestids: anc(e), container: container(e), classes: e.className,
      // after-only: visible actions, flex rows (distinct child tops, 4px tolerance), each <p>'s own line
      // count (height / computed line-height), and any underlined secondary link (text-primary underline).
      actionsVisible: [...e.querySelectorAll("a,button")].filter(vis).map((a) => a.textContent.trim()),
      // rows of the inline flex row: children are items-center, so one visual line shares one vertical centre
      flexRows: [...[...e.children].filter(vis).map((c) => { const r = c.getBoundingClientRect(); return r.top + r.height / 2; }).sort((a, b) => a - b).reduce((acc, y) => (acc.length && y - acc[acc.length - 1] < 6 ? acc : [...acc, y]), [])].length,
      pLines: [...e.querySelectorAll(":scope > p")].map((p) => Math.round(p.getBoundingClientRect().height / parseFloat(getComputedStyle(p).lineHeight))),
      secondaryLinks: [...e.querySelectorAll(":scope > a")].filter((a) => /\btext-primary\b/.test(a.className) && !/\binline-flex\b/.test(a.className)).map((a) => ({ text: a.textContent.trim(), cls: a.className, deco: getComputedStyle(a).textDecorationLine, color: getComputedStyle(a).color })),
    };
  };
  const out = [];
  for (const e of root.querySelectorAll('[data-slot="empty-state"]')) {
    if (!vis(e)) continue;
    const inline = e.getAttribute("data-variant") === "inline";
    const ps = [...e.children].filter((c) => c.tagName === "P");
    out.push(box(e, "shared EmptyState", inline ? ps[0] : e.querySelector("h1,h2,h3,h4"), inline ? ps[1] : e.querySelector(":scope > p")));
  }
  for (const e of root.querySelectorAll("div.border-dashed")) {
    const h = e.querySelector(":scope > h3");
    if (h && vis(e) && e.querySelector(":scope > svg")) out.push(box(e, "local look-alike (reviews-client.tsx)", h, e.querySelector(":scope > p")));
  }
  const mf = root.querySelector('[data-testid="matter-fields-tab"].py-12');
  if (mf && vis(mf)) out.push(box(mf, "hand-rolled matter-fields block", mf.querySelector("p"), mf.querySelectorAll("p")[1]));
  const want = ["matter-fields-tab", "client-fields-tab", "custom-field-section", "field-group-selector", "my-tasks-widget", "extended-widgets"];
  const tidPresent = Object.fromEntries(want.map((t) => [t, document.querySelectorAll(`[data-testid="${t}"]`).length]));
  const h2s = [...root.querySelectorAll("h2,h3")].filter(vis).map((h) => `${h.tagName.toLowerCase()}:${h.textContent.trim().slice(0, 60)}`);
  const sheet = document.querySelector('[role="dialog"]');
  return {
    url: location.pathname + location.search, dark: document.documentElement.classList.contains("dark"),
    h1: (document.querySelector("#main-content h1") || {}).textContent || null,
    deniedText: /do not have permission|not authori[sz]ed|access denied|don't have access|no access/i.test(root.textContent) ? (root.textContent.match(/[^.]*(permission|authori[sz]ed|access denied|don't have access|no access)[^.]*\./i) || [""])[0].trim().slice(0, 200) : null,
    sheetSections: sheet ? [...sheet.querySelectorAll("h3")].map((h) => h.textContent.trim()) : null,
    empties: out, tidPresent, headings: h2s.slice(0, 40),
    invariants: { mainContent: document.querySelectorAll('main#main-content').length, h1InMain: document.querySelectorAll("#main-content h1").length, h1Text: [...document.querySelectorAll("#main-content h1")].map((h) => h.textContent.trim().slice(0, 80)) },
  };
}

async function one(browser, s, who, w, theme, consoleSink) {
  const base = path.join(RAW, `${s.key}__${who}-${w}-${theme}`);
  if (!FORCE && fs.existsSync(base + ".json")) return JSON.parse(fs.readFileSync(base + ".json", "utf8"));
  const ctx = await C.newCtx(browser, state(who), { ...VP[w], theme, extra: { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" } });
  const page = await ctx.newPage();
  const label = `${s.key}|${who}|${w}|${theme}`;
  const msgs = [];
  page.on("console", (m) => { if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(m.text())) msgs.push({ type: m.type(), text: m.text().slice(0, 400) }); });
  page.on("pageerror", (e) => msgs.push({ type: "pageerror", text: String(e).slice(0, 400) }));
  let res;
  try {
    await page.goto(s.url, { waitUntil: "load", timeout: 60000 });
    await page.locator("#main-content h1").first().waitFor({ timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(1500);
    if (s.ready) await s.ready(page);
    res = await page.evaluate(probe, { scope: s.scope || null });
    if (s.target) {
      const t = page.locator(s.target).filter({ visible: true }).first();
      if (await t.count()) await t.evaluate((e) => e.scrollIntoView({ block: "center" }));
      await page.waitForTimeout(400);
    }
    if (s.full) {
      const H = await page.evaluate(() => document.documentElement.scrollHeight);
      await page.screenshot({ path: base + ".png", fullPage: true, clip: { x: 0, y: 0, width: VP[w].width, height: Math.min(H, 2400) } });
    } else await page.screenshot({ path: base + ".png" });
    // Crops of each box at 1440 light (Alice and Carol) for the measured-box crop sheet.
    if (w === 1440 && theme === "light") {
      const sel = '[data-slot="empty-state"], div.border-dashed:has(> h3):has(> svg), [data-testid="matter-fields-tab"].py-12';
      const scopeSel = s.scope || "#main-content";
      const els = page.locator(`${scopeSel} :is(${sel})`).filter({ visible: true });
      const n = await els.count();
      res.crops = [];
      for (let i = 0; i < n; i++) {
        const f = `${base}.crop${i}.png`;
        await els.nth(i).scrollIntoViewIfNeeded();
        await els.nth(i).screenshot({ path: f }).then(() => res.crops.push(f)).catch(() => {});
      }
    }
  } catch (e) {
    res = { ...(res || {}), error: String(e).slice(0, 300) };
  }
  res.label = label;
  res.console = msgs;
  fs.writeFileSync(base + ".json", JSON.stringify(res));
  console.log(label, res.error ? "ERROR " + res.error : `empties=${(res.empties || []).map((e) => `${e.title}[${e.variant || e.kind}] ${e.h}px ${e.titleTag}`).join(" ; ")} console=${msgs.length} dark=${res.dark}`);
  await ctx.close();
  return res;
}

(async () => {
  const browser = await C.chromium.launch();
  const inv = await feeNoteId(browser);
  console.log("fee note", inv);
  const jobs = [];
  for (const s of surfaces(inv))
    for (const who of ["alice", "carol"])
      for (const w of [1024, 1440])
        for (const theme of ["light", "dark"]) {
          if (s.only1440light && (w !== 1440 || theme !== "light")) continue;
          jobs.push([s, who, w, theme]);
        }
  const q = jobs.slice();
  const run = async () => { while (q.length) { const [s, who, w, theme] = q.shift(); await one(browser, s, who, w, theme); } };
  await Promise.all([run(), run(), run()]);
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
