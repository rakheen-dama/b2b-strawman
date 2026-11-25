// 661B post-fix pixel-identity re-check on d62c5dc19 (--button-accent-foreground: var(--color-white)).
// Re-runs exactly the OBS d/e captures that differed by Δ1 in ../../after/ (diffs.json) and compares
// them with the BEFORE run, using the same capture method as ../../after/scripts/capture-after.cjs
// (never edited): dark fee-note "Approve" (first DRAFT) and "Send fee note" (first APPROVED) crops at
// 1440x900, searched in the before tile; portal /login (and /home) 1024 light full tiles; marketing /
// 1024 light full tile. Plus computed label colours (dark / portal / marketing must be rgb(255, 255, 255))
// and the light firm accent pair (white on #00776a).
// Usage (worktree root): BEFORE_RAW=/tmp/661b-before-raw node qa_cycle/evidence/phase91-ui/661B/after-fix/scripts/capture-after-fix.cjs qa_cycle/evidence/phase91-ui/661B/after-fix [portalUrl]
const path = require("path");
const fs = require("fs");
const C = require("../../walk/scripts/common.cjs");
const { W, chromium, APP, API, ORG, rel, idpToken, api, list, loginState, newCtx, watch, ratio, hex3, sheet, differ } = C;

const OUT = path.resolve(process.argv[2] || "/tmp/661b-after-fix");
const PORTAL = process.argv[3] || "http://localhost:3012";
const BEFORE_RAW = process.env.BEFORE_RAW || "/tmp/661b-before-raw";
const RAW = process.env.RAW || "/tmp/661b-afterfix-raw";
const BEFORE_DIR = path.join(W, "qa_cycle/evidence/phase91-ui/661B/before");
fs.mkdirSync(RAW, { recursive: true });
const B = (n) => path.join(BEFORE_RAW, n + ".png");
const notes = [];
const note = (m) => {
  notes.push(m);
  console.log("NOTE", m);
};
const consoleMsgs = [];
const probes = {};
// In-page: computed label colour + fill of accent elements (all, or the one whose text is `name`).
const labelProbe = ({ name = null, max = 50 }) =>
  [...document.querySelectorAll('[data-variant="accent"]')]
    .filter((e) => (name ? e.textContent.trim() === name : e.getBoundingClientRect().width > 0))
    .slice(0, max)
    .map((e) => {
      const cs = getComputedStyle(e);
      return { text: e.textContent.trim().slice(0, 40), tag: e.tagName, slot: e.getAttribute("data-slot"), color: cs.color, colorHex: window.__hex(cs.color), bg: cs.backgroundColor, bgHex: window.__hex(cs.backgroundColor), fgRgb: window.__rgb(cs.color, cs.backgroundColor), bgRgb: window.__rgb(cs.backgroundColor), varFg: cs.getPropertyValue("--button-accent-foreground").trim(), html: document.documentElement.className };
    });

(async () => {
  const browser = await chromium.launch();
  const { cmp, diffs, visuals } = differ(browser);
  const state = await loginState(browser, "alice");
  const token = await idpToken("alice");
  const invoices = list(await api(token, "/api/invoices"));
  const draft = invoices.find((i) => i.status === "DRAFT");
  const approved = invoices.find((i) => i.status === "APPROVED");
  note(`out=${rel(OUT)} beforeRaw=${rel(BEFORE_RAW)} raw=${rel(RAW)} portal=${PORTAL}; draft=${draft && draft.id} approved=${approved && approved.id}`);

  // ---- Fee-note header accent buttons, dark (pixel identity) and light (firm accent pair).
  const tiles = [];
  for (const theme of ["dark", "light"]) {
    for (const [kind, inv, btn] of [
      ["draft-approve", draft, "Approve"],
      ["approved-send", approved, "Send fee note"],
    ]) {
      if (!inv) {
        note(`no ${kind} fee note (${theme})`);
        continue;
      }
      const ctx = await newCtx(browser, state, { width: 1440, height: 900, theme });
      const page = await ctx.newPage();
      const lab = { label: `${kind}-${theme}` };
      watch(page, consoleMsgs, lab);
      await page.goto(`${APP}/org/${ORG}/invoices/${inv.id}`, { waitUntil: "load" });
      const b = page.getByRole("button", { name: btn, exact: true }).first();
      await b.waitFor({ state: "visible", timeout: 30000 });
      await page.waitForTimeout(800);
      const box = await b.boundingBox();
      const mb = await page.locator("main#main-content").boundingBox();
      const clip = { x: mb.x, y: Math.max(0, box.y - 110), width: 1440 - mb.x, height: 200 };
      const f = path.join(RAW, `${kind}-${theme}.png`);
      await page.screenshot({ path: f, clip });
      tiles.push([`${kind}-${theme} ("${btn}")`, f]);
      probes[`accent-${kind}-${theme}`] = (await page.evaluate(labelProbe, { name: btn }))[0] || { found: false };
      const p = probes[`accent-${kind}-${theme}`];
      if (p.fgRgb) p.ratio = ratio(p.fgRgb, p.bgRgb);
      if (theme === "dark") {
        const bx = Math.round(box.x - clip.x);
        const by = Math.round(box.y - clip.y);
        await cmp(`dark accent button "${btn}" (crop)`, { a: f, b: B(`${kind}-dark`), ra: [bx, by, Math.round(box.width), Math.round(box.height)], search: { x0: 0, x1: 1440, y0: by - 12, y1: by + 12 }, keepVisual: true });
      }
      await ctx.close();
    }
  }

  // ---- Marketing / at 1024 light (no auth), as before: full tile vs before/marketing-1024-light.png.
  {
    const ctx = await browser.newContext({ viewport: { width: 1024, height: 768 }, deviceScaleFactor: 1 });
    await ctx.addInitScript(C.INIT, null); // helpers only, no stored theme (as before)
    const page = await ctx.newPage();
    const lab = { label: "marketing" };
    watch(page, consoleMsgs, lab);
    await page.goto(`${APP}/`, { waitUntil: "load" });
    await page.waitForTimeout(1500);
    const f = path.join(RAW, "marketing-1024-light.png");
    await page.screenshot({ path: f });
    probes["marketing-accent-labels"] = await page.evaluate(labelProbe, { max: 8 });
    const bb = await page.locator('[data-variant="accent"]').first().boundingBox();
    await ctx.close();
    await cmp("marketing / 1024 light (full tile)", { a: f, b: path.join(BEFORE_DIR, "marketing-1024-light.png") });
    if (bb && bb.y + bb.height <= 768) await cmp(`marketing / first accent button (crop)`, { a: f, b: path.join(BEFORE_DIR, "marketing-1024-light.png"), ra: [Math.floor(bb.x) - 4, Math.floor(bb.y) - 4, Math.ceil(bb.width) + 8, Math.ceil(bb.height) + 8], keepVisual: true });
    tiles.push(["marketing / 1024 light", f]);
  }

  // ---- Portal /login and /home 1024 light (worktree portal on next dev :3012; Origin shim as before).
  try {
    const email = process.env.PORTAL_EMAIL || "contact@acme.example.com";
    const lr = await fetch(`${API}/portal/auth/request-link`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, orgId: ORG }) });
    if (!lr.ok) throw new Error(`request-link ${lr.status}`);
    const { magicLink } = await lr.json();
    const tk = magicLink.split("token=").pop().split("&")[0];
    const er = await fetch(`${API}/portal/auth/exchange`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token: tk, orgId: ORG }) });
    if (!er.ok) throw new Error(`exchange ${er.status}`);
    const s = await er.json();
    const ctx = await browser.newContext({ viewport: { width: 1024, height: 768 }, deviceScaleFactor: 1 });
    await ctx.addInitScript(C.INIT, null); // helpers only, no stored theme (as before)
    await ctx.route(`${API}/**`, async (route) => {
      const req = route.request();
      const acao = { "access-control-allow-origin": new URL(PORTAL).origin, "access-control-allow-credentials": "true", "access-control-allow-headers": "*", "access-control-allow-methods": "*" };
      if (req.method() === "OPTIONS") return route.fulfill({ status: 204, headers: acao });
      const resp = await route.fetch({ headers: { ...req.headers(), origin: "http://localhost:3002" } });
      return route.fulfill({ response: resp, headers: { ...resp.headers(), ...acao } });
    });
    const page = await ctx.newPage();
    const lab = { label: "portal" };
    watch(page, consoleMsgs, lab);
    await page.goto(`${PORTAL}/login`, { waitUntil: "load" });
    await page.locator("input, button").first().waitFor({ state: "visible", timeout: 60000 });
    await page.waitForTimeout(1000);
    const fl = path.join(RAW, "portal-login.png");
    await page.screenshot({ path: fl });
    probes["portal-login-accent-labels"] = await page.evaluate(labelProbe, {});
    const pb = await page.locator('[data-variant="accent"]').first().boundingBox();
    await page.evaluate((x) => {
      localStorage.setItem("portal_jwt", x.token);
      localStorage.setItem("portal_customer", JSON.stringify({ id: x.clientId, name: x.clientName, email: x.email, orgId: x.orgId }));
      localStorage.setItem("portal_last_org_id", x.orgId);
    }, { token: s.token, clientId: s.clientId, clientName: s.clientName, email, orgId: ORG });
    lab.label = "portal-home";
    await page.goto(`${PORTAL}/home`, { waitUntil: "load" });
    await page.locator("h1").first().waitFor({ state: "visible", timeout: 60000 });
    await page.waitForTimeout(2500);
    const fh = path.join(RAW, "portal-home.png");
    await page.screenshot({ path: fh });
    await ctx.close();
    await cmp("portal /login 1024 light (full tile)", { a: fl, b: B("portal-login") });
    if (pb) await cmp(`portal /login "Send Magic Link" (crop)`, { a: fl, b: B("portal-login"), ra: [Math.floor(pb.x) - 4, Math.floor(pb.y) - 4, Math.ceil(pb.width) + 8, Math.ceil(pb.height) + 8], keepVisual: true });
    await cmp("portal /home 1024 light (full tile)", { a: fh, b: B("portal-home") });
    tiles.push(["portal /login 1024 light", fl], ["portal /home 1024 light", fh]);
  } catch (e) {
    note(`portal: ${String(e).slice(0, 300)}`);
  }

  // ---- Evidence: before | after | diff for the crops, and the as-rendered tiles.
  const cells = [];
  for (const [name, v, r] of visuals) cells.push([`${name} — BEFORE`, v.before], ["AFTER-FIX (d62c5dc19)", v.after], [`DIFF: ${r.exact} px differ (tol8 ${r.tol8}, max Δ ${r.maxDelta}) of ${r.pixels}`, v.diff]);
  await sheet(browser, path.join(OUT, "pixel-identity.png"), "661B after-fix (d62c5dc19) — accent labels: before | after-fix | diff (red = any RGB difference)", cells, 3);
  await sheet(browser, path.join(OUT, "tiles.png"), "661B after-fix (d62c5dc19) — as-rendered tiles (fee-note headers dark/light; marketing; portal)", tiles, 2, 700);

  const lightA = probes["accent-draft-approve-light"];
  probes.summary = {
    darkApproveColor: probes["accent-draft-approve-dark"] && probes["accent-draft-approve-dark"].color,
    darkSendColor: probes["accent-approved-send-dark"] && probes["accent-approved-send-dark"].color,
    portalLoginColor: (probes["portal-login-accent-labels"] || []).map((x) => x.color),
    marketingColors: [...new Set((probes["marketing-accent-labels"] || []).map((x) => x.color))],
    lightFirmAccent: lightA && { fg: hex3(lightA.fgRgb), bg: hex3(lightA.bgRgb), color: lightA.color, ratio: lightA.ratio },
  };
  fs.writeFileSync(path.join(OUT, "diffs.json"), JSON.stringify(diffs) + "\n");
  fs.writeFileSync(path.join(OUT, "probes.json"), JSON.stringify(probes) + "\n");
  fs.writeFileSync(path.join(OUT, "console.json"), JSON.stringify(consoleMsgs) + "\n");
  fs.writeFileSync(path.join(OUT, "notes.json"), JSON.stringify(notes) + "\n");
  console.log("SUMMARY", JSON.stringify(probes.summary));
  await browser.close();
  console.log("DONE", rel(OUT));
})().catch((e) => {
  console.error("FATAL", e);
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(path.join(OUT, "notes.json"), JSON.stringify([...notes, "FATAL " + String(e)]) + "\n");
  process.exit(1);
});
