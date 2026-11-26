// 665E OBS step 4: one save per save model, through the UI as Alice (1440×900 light), re-submitting
// the DISPLAYED values (nothing typed):
//   Templates › Branding card "Save Branding"   (per-card; brandColor + documentFooterText; the logo
//                                                 upload is a separate instant action, not exercised)
//   Acceptance › "Save Settings"                 (form card; acceptanceExpiryDays)
// For each: button text, disabled before / during, the message (text, tag, role, aria-live, class,
// placement), any sonner toast, click → message time, the server-action POST status (Next-Action
// header), and the API state before / after (GET /api/settings). Writes $RAW/saves.json + tiles.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), saves: [] };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const log = (o) => { out.saves.push(o); console.log(JSON.stringify(o).slice(0, 1200)); };
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(500); };
const toasts = (p) => p.locator("[data-sonner-toast]").evaluateAll((els) => els.map((e) => `${e.getAttribute("data-type") || ""}:${(e.innerText || "").replace(/\s+/g, " ").trim()}`));
const MSG = (btnText) => {
  const btn = [...document.querySelectorAll("#main-content button")].find((b) => b.textContent.trim().replace(/^Saving\.\.\.$/, btnText) === btnText || b.textContent.trim() === "Saving...");
  const card = btn && btn.closest("div.rounded-lg.border");
  const ps = card ? [...card.querySelectorAll("p")].filter((p) => /text-(teal|green|red)-600|text-destructive/.test(p.className)) : [];
  const m = ps[0];
  const br = btn ? btn.getBoundingClientRect() : null;
  const mr = m ? m.getBoundingClientRect() : null;
  return {
    button: btn ? { text: btn.textContent.trim(), disabled: btn.disabled, card: card ? (card.querySelector("h2") || {}).textContent || "(card)" : null } : null,
    message: m ? { text: m.textContent.trim(), tag: m.tagName.toLowerCase(), role: m.getAttribute("role"), ariaLive: m.getAttribute("aria-live"), cls: m.className, placement: mr.top < br.top - 4 ? "above the button row (inside the card)" : Math.abs(mr.top - br.top) < 20 ? "same row as the button" : "below the button" } : null,
  };
};
const settings = async (t) => { const s = (await L.apiGet(t, "/api/settings")).json || {}; return { brandColor: s.brandColor ?? null, documentFooterText: s.documentFooterText ?? null, logoUrlPresent: !!s.logoUrl, acceptanceExpiryDays: s.acceptanceExpiryDays ?? null }; };

async function save(p, key, label, btnText, displayed) {
  const posts = [];
  const onResp = async (r) => { const req = r.request(); if (req.method() === "POST" && req.headers()["next-action"]) posts.push({ url: r.url().replace(L.BASE, ""), status: r.status() }); };
  p.on("response", onResp);
  const before = await p.evaluate(MSG, btnText);
  const t0 = Date.now();
  await p.getByRole("button", { name: btnText, exact: true }).click();
  const during = await p.evaluate(MSG, btnText);
  let msgAt = null;
  await p.waitForFunction((bt) => { const b = [...document.querySelectorAll("#main-content button")].find((x) => x.textContent.trim() === bt); const c = b && b.closest("div.rounded-lg.border"); return !!c && [...c.querySelectorAll("p")].some((q) => /text-(teal|green|red)-600|text-destructive/.test(q.className)) && !b.disabled; }, btnText, { timeout: 15000, polling: 50 }).then(() => { msgAt = Date.now() - t0; }).catch(() => {});
  await p.waitForTimeout(250);
  const after = await p.evaluate(MSG, btnText);
  const tt = await toasts(p);
  await p.getByRole("button", { name: btnText, exact: true }).scrollIntoViewIfNeeded();
  await shot(p, `save__${key}`, `Alice 1440 light — ${label}: displayed values ${JSON.stringify(displayed)} → "${btnText}" → "${after.message && after.message.text}" (${msgAt} ms; POST ${posts.map((x) => x.status).join(",")})`);
  await p.waitForTimeout(3300);
  const later = await p.evaluate(MSG, btnText);
  p.off("response", onResp);
  return { before, duringClick: during, after, msgMs: msgAt, toasts: tt, serverActionPosts: posts, messageAfter3_5s: later.message ? later.message.text : null };
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  const a = await L.idpToken("alice");
  try {
    lab.label = "save|branding";
    await p.goto(`${L.BASE}/settings/documents?tab=templates`, { waitUntil: "load" });
    await settle(p);
    const disp = { brandColor: await p.locator("#brand-color").inputValue(), footerText: await p.locator("#footer-text").inputValue() };
    const s0 = await settings(a);
    const r = await save(p, "branding", "Templates › Branding", "Save Branding", disp);
    const s1 = await settings(a);
    log({ model: "Templates › Branding 'Save Branding' (per-card)", displayed: disp, ...r, apiBefore: s0, apiAfter: s1, changed: Object.keys(s0).filter((k) => JSON.stringify(s0[k]) !== JSON.stringify(s1[k])).map((k) => `${k}: ${JSON.stringify(s0[k])} → ${JSON.stringify(s1[k])}`) });

    lab.label = "save|acceptance";
    await p.goto(`${L.BASE}/settings/documents?tab=acceptance`, { waitUntil: "load" });
    await settle(p);
    const disp2 = { acceptanceExpiryDays: await p.locator("#acceptance-expiry-days").inputValue() };
    const s2 = await settings(a);
    const r2 = await save(p, "acceptance", "Acceptance", "Save Settings", disp2);
    const s3 = await settings(a);
    log({ model: "Acceptance 'Save Settings' (form card)", displayed: disp2, ...r2, apiBefore: s2, apiAfter: s3, changed: Object.keys(s2).filter((k) => JSON.stringify(s2[k]) !== JSON.stringify(s3[k])).map((k) => `${k}: ${JSON.stringify(s2[k])} → ${JSON.stringify(s3[k])}`) });
  } finally {
    await browser.close();
    out.console = consoleSink;
    out.tiles = tiles;
    fs.writeFileSync(path.join(L.RAW, "saves.json"), JSON.stringify(out));
    console.log("console", consoleSink.length, "tiles", tiles.length);
  }
})();
