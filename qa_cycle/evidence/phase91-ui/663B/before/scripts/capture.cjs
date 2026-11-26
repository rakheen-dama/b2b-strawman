// 663B OBS capture (task 663.8): the empty detail tabs whose EmptyState call sites 663B sweeps to an
// explicit variant="default" — matter Activity / Fee estimate (budget) / Documents / Time, client Rates,
// plus the matter Clients tab (project-customers-panel) as the substitute for Activity (see README).
// 1440x900, light + dark (localStorage `kazi-theme`), Alice (owner) + Carol (member), reduced motion,
// Africa/Johannesburg. Read-only: GET calls with each persona's own token + page navigation only.
//
// PHASE=before|after selects the output: raw tiles → <worktree>/.tmp/663B-raw/<PHASE>/ (git-ignored),
// committed JSON → qa_cycle/evidence/phase91-ui/663B/<PHASE>/{measurements,console}.json.
// The same unedited script serves both runs. Overrides: MATTER_ID, CLIENT_ID (default: resolved by name).
// Usage (worktree root): PHASE=after NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/663B/before/scripts/capture.cjs
const path = require("path");
const fs = require("fs");
const crypto = require("crypto");
const { execFileSync } = require("child_process");

const PHASE = process.env.PHASE;
if (!/^(before|after)$/.test(PHASE || "")) throw new Error("set PHASE=before or PHASE=after");
const ROOT = path.resolve(__dirname, "../../../../../.."); // scripts → before → 663B → phase91-ui → evidence → qa_cycle → root
const RAW = path.join(ROOT, ".tmp/663B-raw", PHASE);
const OUT = path.resolve(__dirname, "../..", PHASE);
const COMMON = "qa_cycle/evidence/phase91-ui/661B/walk/scripts/common.cjs";

// common.cjs (unedited) resolves @playwright/test from <its repo>/frontend/node_modules. A fresh worktree
// may have no node_modules yet; then fall back to the main checkout's copy (git common dir's parent).
function loadCommon() {
  try {
    return require(path.join(ROOT, COMMON));
  } catch (e) {
    if (e.code !== "MODULE_NOT_FOUND") throw e;
    const gitCommon = execFileSync("git", ["rev-parse", "--path-format=absolute", "--git-common-dir"], { cwd: ROOT, encoding: "utf8" }).trim();
    return require(path.join(path.dirname(gitCommon), COMMON));
  }
}
const C = loadCommon();
const BASE = `${C.APP}/org/${C.ORG}`;
const VP = { width: 1440, height: 900 };
const MATTER_NAME = "663A OBS empty-fields matter";
const CLIENT_NAME = "663A OBS empty-fields client";
const relOut = (p) => path.relative(ROOT, p);

const SURFACES = [
  { key: "activity", entity: "matter", tab: "activity", title: "No activity yet", site: "components/activity/activity-feed.tsx" },
  { key: "budget", entity: "matter", tab: "budget", title: "No fee estimate set", site: "components/budget/budget-panel.tsx" },
  { key: "documents", entity: "matter", tab: "documents", title: "No documents yet", site: "components/documents/documents-panel.tsx" },
  { key: "time-summary", entity: "matter", tab: "time", title: "No time tracked yet", site: "components/projects/time-summary-panel.tsx" },
  { key: "customer-rates", entity: "client", tab: "rates", title: "No client rate overrides", site: "components/rates/customer-rates-tab.tsx" },
  { key: "matter-clients", entity: "matter", tab: "clients", title: "No linked clients", site: "components/projects/project-customers-panel.tsx", substituteFor: "activity" },
];

// React/Radix generated ids differ per render; strip them so the hash compares markup, not ids.
const normalise = (h) =>
  h
    .replace(/radix-[^"\s]*/g, "radix-RID")
    .replace(/«r[0-9a-z]+»|:r[0-9a-z]+:|_R_[0-9A-Za-z_]+_/g, "RID")
    .replace(/\s+/g, " ")
    .trim();
const sha = (s) => crypto.createHash("sha256").update(s).digest("hex");

async function resolveRecords() {
  const t = await C.idpToken("alice");
  const byName = async (p, name) => {
    const hit = C.list(await C.api(t, `${p}?size=500`)).filter((x) => x.name === name);
    if (hit.length !== 1) throw new Error(`${p}: expected exactly one "${name}", found ${hit.length} — set MATTER_ID / CLIENT_ID`);
    return hit[0].id;
  };
  return {
    matterId: process.env.MATTER_ID || (await byName("/api/matters", MATTER_NAME)),
    clientId: process.env.CLIENT_ID || (await byName("/api/clients", CLIENT_NAME)),
  };
}

// Read-only GETs proving each tab's empty-state condition, per persona (own token).
async function preconditions({ matterId, clientId }) {
  const out = {};
  for (const who of ["alice", "carol"]) {
    const t = await C.idpToken(who);
    const g = async (p) => {
      const r = await fetch(`${C.API}${p}`, { headers: { Authorization: `Bearer ${t}` } });
      let body = null;
      try { body = await r.json(); } catch (e) {}
      return { status: r.status, body };
    };
    const act = await g(`/api/matters/${matterId}/activity?page=0&size=20`);
    const docs = await g(`/api/matters/${matterId}/documents`);
    const bud = await g(`/api/matters/${matterId}/budget`);
    const time = await g(`/api/matters/${matterId}/time-summary`);
    const m = await g(`/api/matters/${matterId}`);
    const rates = await g(`/api/billing-rates?clientId=${clientId}`);
    out[who] = {
      matter: { status: m.status, name: m.body && m.body.name, clientId: m.body ? m.body.clientId : undefined },
      activityItems: act.status === 200 ? C.list(act.body).length : `HTTP ${act.status}`,
      activityEventTypes: act.status === 200 ? C.list(act.body).map((a) => a.eventType) : undefined,
      documents: docs.status === 200 ? C.list(docs.body).length : `HTTP ${docs.status}`,
      budget: bud.status === 404 ? "none (404)" : `HTTP ${bud.status}`,
      timeSummary: time.status === 200 ? { totalMinutes: time.body.totalMinutes, entryCount: time.body.entryCount } : `HTTP ${time.status}`,
      clientRateOverrides: rates.status === 200 ? C.list(rates.body).filter((r) => r.scope === "CUSTOMER_OVERRIDE" && r.clientId === clientId).length : `HTTP ${rates.status}`,
    };
  }
  return out;
}

// In-page probe: the active tab panel, every visible shared EmptyState in it, and the target box in detail.
function probe(title) {
  const vis = (e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== "hidden"; };
  const panels = [...document.querySelectorAll('[role="tabpanel"][data-state="active"]')].filter(vis);
  const panel = panels[0] || null;
  const activeTab = panel && panel.id ? panel.id.replace(/^.*-content-/, "") : null;
  const empties = panel ? [...panel.querySelectorAll('[data-slot="empty-state"]')].filter(vis) : [];
  const titleOf = (e) => (e.getAttribute("data-variant") === "inline" ? e.querySelector(":scope > p") : e.querySelector(":scope > h1,:scope > h2,:scope > h3,:scope > h4"));
  const box = empties.find((e) => { const t = titleOf(e); return t && t.textContent.trim() === title; }) || null;
  const cs = (e, props) => { if (!e) return null; const s = getComputedStyle(e); return Object.fromEntries(props.map((p) => [p, s[p]])); };
  let target = null;
  if (box) {
    const r = box.getBoundingClientRect();
    const t = titleOf(box);
    const icon = box.querySelector(':scope > [data-slot="empty-state-icon"]');
    const ir = icon && icon.getBoundingClientRect();
    target = {
      variant: box.getAttribute("data-variant"),
      w: +r.width.toFixed(1), h: +r.height.toFixed(1),
      titleTag: t.tagName.toLowerCase(), title: t.textContent.trim(),
      description: (box.querySelector(":scope > p:not(:first-of-type), :scope > h2 + p") || {}).textContent || null,
      actions: [...box.querySelectorAll("a,button")].map((a) => `${a.tagName.toLowerCase()}:${a.textContent.trim()}`),
      boxStyle: cs(box, ["display", "flexDirection", "alignItems", "paddingTop", "paddingRight", "paddingBottom", "paddingLeft", "rowGap", "columnGap"]),
      titleStyle: cs(t, ["fontSize", "fontWeight", "lineHeight", "fontFamily", "color"]),
      iconTile: ir ? { w: +ir.width.toFixed(1), h: +ir.height.toFixed(1) } : null,
      className: box.className,
      outerHTML: box.outerHTML,
    };
  }
  return {
    url: location.pathname + location.search,
    dark: document.documentElement.classList.contains("dark"),
    activeTab,
    panelText: panel ? panel.innerText.replace(/\s+/g, " ").trim().slice(0, 240) : null,
    otherEmpties: empties.filter((e) => e !== box).map((e) => ({ variant: e.getAttribute("data-variant"), title: (titleOf(e) || {}).textContent || null })),
    target,
  };
}

async function one(browser, states, recs, s, who, theme) {
  const base = path.join(RAW, `${s.key}__${who}-1440-${theme}`);
  const ctx = await C.newCtx(browser, states[who], { ...VP, theme, extra: { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" } });
  const page = await ctx.newPage();
  const msgs = [];
  page.on("console", (m) => { if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(m.text())) msgs.push({ type: m.type(), text: m.text().slice(0, 400) }); });
  page.on("pageerror", (e) => msgs.push({ type: "pageerror", text: String(e).slice(0, 400) }));
  const id = s.entity === "matter" ? recs.matterId : recs.clientId;
  const url = `${BASE}/${s.entity === "matter" ? "matters" : "clients"}/${id}?tab=${s.tab}`;
  let res = { key: s.key, who, theme, url };
  try {
    await page.goto(url, { waitUntil: "load", timeout: 60000 });
    res.networkIdle = await page.waitForLoadState("networkidle", { timeout: 20000 }).then(() => true, () => false);
    await page.locator('[role="tabpanel"][data-state="active"]').first().waitFor({ timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(1000);
    Object.assign(res, await page.evaluate(probe, s.title));
    const box = page.locator('[role="tabpanel"][data-state="active"] [data-slot="empty-state"]').filter({ hasText: s.title }).first();
    if (res.target) await box.evaluate((e) => e.scrollIntoView({ block: "center" }));
    else await page.locator('[role="tabpanel"][data-state="active"]').first().evaluate((e) => e.scrollIntoView({ block: "start" })).catch(() => {});
    await page.waitForTimeout(400);
    if (res.target) {
      const r = await box.boundingBox();
      res.target.viewportY = r ? Math.round(r.y) : null;
      res.target.outerHTMLNormalised = normalise(res.target.outerHTML);
      res.target.sha256 = sha(res.target.outerHTMLNormalised);
      res.target.idsStripped = res.target.outerHTMLNormalised !== res.target.outerHTML.replace(/\s+/g, " ").trim();
      delete res.target.outerHTML;
    }
    await page.screenshot({ path: base + ".png" });
    res.png = relOut(base + ".png");
  } catch (e) {
    res.error = String(e).slice(0, 300);
  }
  res.gated = !res.target && res.activeTab !== s.tab;
  res.console = msgs;
  fs.writeFileSync(base + ".json", JSON.stringify(res));
  const t = res.target;
  console.log(`${s.key}|${who}|${theme}`, res.error ? "ERROR " + res.error : t ? `${t.variant} ${t.w}x${t.h} <${t.titleTag}> "${t.title}" ${t.sha256.slice(0, 12)}` : `no target (activeTab=${res.activeTab}${res.gated ? ", gated" : ""}) ${res.panelText ? res.panelText.slice(0, 80) : ""}`, `dark=${res.dark} idle=${res.networkIdle} console=${msgs.length}`);
  await ctx.close();
  return res;
}

(async () => {
  fs.mkdirSync(RAW, { recursive: true });
  fs.mkdirSync(OUT, { recursive: true });
  const recs = await resolveRecords();
  const pre = await preconditions(recs);
  console.log("records", JSON.stringify(recs));
  console.log("preconditions", JSON.stringify(pre));
  const browser = await C.chromium.launch();
  const states = { alice: await C.loginState(browser, "alice"), carol: await C.loginState(browser, "carol") };
  const jobs = [];
  for (const s of SURFACES) for (const who of ["alice", "carol"]) for (const theme of ["light", "dark"]) jobs.push([s, who, theme]);
  const results = [];
  const q = jobs.slice();
  const run = async () => { while (q.length) { const [s, who, theme] = q.shift(); results.push(await one(browser, states, recs, s, who, theme)); } };
  await Promise.all([run(), run(), run()]);
  const order = (r) => jobs.findIndex(([s, w, t]) => s.key === r.key && w === r.who && t === r.theme);
  results.sort((a, b) => order(a) - order(b));
  let frontendImage = null;
  try { frontendImage = execFileSync("docker", ["inspect", "-f", "{{.Image}} {{.Created}}", "e2e-frontend"], { encoding: "utf8" }).trim(); } catch (e) {}
  const meas = {
    phase: PHASE, capturedAt: new Date().toISOString(), viewport: "1440x900", reducedMotion: true, timezone: "Africa/Johannesburg",
    scriptsHead: execFileSync("git", ["rev-parse", "--short", "HEAD"], { cwd: ROOT, encoding: "utf8" }).trim(),
    chromium: browser.version(), playwrightTest: JSON.parse(fs.readFileSync(path.join(C.W, "frontend/node_modules/@playwright/test/package.json"), "utf8")).version,
    playwrightFrom: C.W === ROOT ? "worktree" : "main checkout (worktree has no node_modules)",
    frontendContainer: frontendImage,
    method: "getBoundingClientRect() of the shared EmptyState root ([data-slot=empty-state]) whose title text matches, inside the active [role=tabpanel]; outerHTML normalised (radix/React ids → RID, whitespace collapsed) then sha256",
    records: { matter: { name: MATTER_NAME, id: recs.matterId }, client: { name: CLIENT_NAME, id: recs.clientId } },
    preconditions: pre,
    surfaces: SURFACES.map((s) => ({ key: s.key, site: s.site, tab: s.tab, entity: s.entity, title: s.title, substituteFor: s.substituteFor })),
    captures: results.map(({ console: _c, ...r }) => r),
  };
  fs.writeFileSync(path.join(OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  const pages = results.map((r) => ({
    capture: `${r.key}|${r.who}|1440|${r.theme}`, url: r.url,
    errors: r.console.filter((m) => m.type === "error" || m.type === "pageerror"),
    warnings: r.console.filter((m) => m.type === "warning"),
    hydration: r.console.filter((m) => /hydrat/i.test(m.text)),
  }));
  const totals = { captures: pages.length, errors: pages.reduce((a, p) => a + p.errors.length, 0), warnings: pages.reduce((a, p) => a + p.warnings.length, 0), hydration: pages.reduce((a, p) => a + p.hydration.length, 0) };
  fs.writeFileSync(path.join(OUT, "console.json"), JSON.stringify({ phase: PHASE, capturedAt: meas.capturedAt, totals, pages }) + "\n");
  await browser.close();
  console.log("wrote", relOut(path.join(OUT, "measurements.json")), relOut(path.join(OUT, "console.json")), JSON.stringify(totals));
})().catch((e) => { console.error(e); process.exit(1); });
