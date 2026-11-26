// 665F OBS-after step 6b (NEW): why keyboard focus leaves the rule switch (06 found activeElement =
// body right after Space), and the restore of "Investment Maturity Reminder" to enabled=true.
// 06's second Space landed on <body> (focus already lost), so 06 left the rule OFF; this script's one
// keyboard toggle turns it back ON (net no change over 06 + 06b), then checks the API.
// Instrumentation (read-only, in the page): the switch node is tagged before Space (detects a remount),
// a MutationObserver on it records each `disabled` / `aria-checked` / `data-state` change with
// document.activeElement at that moment, and focusout / blur events are logged with relatedTarget.
// Falls back to a mouse click only if the API still says enabled=false after the keyboard toggle.
// Writes $RAW/k114-focus-diagnose.json + one tile.
// Usage: PHASE=after node 06b-k114-focus-diagnose.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

(async () => {
  const a = await L.idpToken("alice");
  const ruleOf = async () => L.list((await L.apiGet(a, "/api/automation-rules")).json).find((r) => r.name === L.TOGGLE_RULE);
  const out = { at: new Date().toISOString(), apiBefore: null, steps: [], tiles: [], console: [] };
  const r0 = await ruleOf();
  out.apiBefore = { enabled: r0.enabled, updatedAt: r0.updatedAt };
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  const lab = { label: "k114-diagnose" };
  L.watch(p, out.console, lab);
  await p.goto(`${L.BASE}/settings/connections?tab=automations`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(800);
  const name = `Toggle ${L.TOGGLE_RULE}`;
  const sw = p.getByRole("switch", { name });
  await p.locator("#main-content h1").first().click();
  let tabs = null;
  for (let i = 1; i <= 80; i++) { await p.keyboard.press("Tab"); if (await p.evaluate((n) => document.activeElement && document.activeElement.getAttribute("aria-label") === n, name)) { tabs = i; break; } }
  await sw.evaluate((el) => {
    el.__obsMark = "before-space";
    window.__log = [];
    const ae = () => { const x = document.activeElement; return x ? `${x.tagName.toLowerCase()}${x.getAttribute("aria-label") ? `[${x.getAttribute("aria-label")}]` : ""}` : null; };
    new MutationObserver((ms) => { for (const m of ms) window.__log.push({ t: Math.round(performance.now()), kind: "attr", attr: m.attributeName, value: el.getAttribute(m.attributeName), disabledProp: el.disabled, activeElement: ae(), connected: el.isConnected }); }).observe(el, { attributes: true, attributeFilter: ["disabled", "aria-checked", "data-state", "data-disabled"] });
    el.addEventListener("focusout", (e) => window.__log.push({ t: Math.round(performance.now()), kind: "focusout", relatedTarget: e.relatedTarget ? e.relatedTarget.tagName.toLowerCase() : null, disabledProp: el.disabled, connected: el.isConnected }));
    el.addEventListener("blur", () => window.__log.push({ t: Math.round(performance.now()), kind: "blur", disabledProp: el.disabled }));
  });
  const before = await sw.getAttribute("aria-checked");
  await p.keyboard.press("Space");
  await p.getByText(/Rule toggled successfully|Failed to toggle rule/).first().waitFor({ timeout: 15000 }).catch(() => {});
  await p.waitForTimeout(1500);
  const res = await p.evaluate((n) => {
    const now = [...document.querySelectorAll('[role="switch"]')].find((s) => s.getAttribute("aria-label") === n);
    const x = document.activeElement;
    return { sameNode: !!(now && now.__obsMark === "before-space"), activeElement: x ? `${x.tagName.toLowerCase()}${x.getAttribute("aria-label") ? `[${x.getAttribute("aria-label")}]` : ""}` : null, log: window.__log };
  }, name);
  const r1 = await ruleOf();
  out.steps.push({ step: "keyboard-toggle", tabsFromH1: tabs, ariaBefore: before, ariaAfter: await sw.getAttribute("aria-checked"), ...res, apiEnabled: r1.enabled, apiUpdatedAt: r1.updatedAt });
  const f = path.join(L.RAW, "acc__k114-focus-diagnose.png");
  await p.screenshot({ path: f });
  out.tiles.push(["acc__k114-focus-diagnose", f, `Alice 1440 light — Automations: Tab ×${tabs} to "${name}", Space (turns it back ON after 06). Same node after the write: ${res.sameNode}; activeElement after settle: ${res.activeElement}; disabled→focusout sequence ${JSON.stringify(res.log.map((l) => l.kind === "attr" ? `${l.attr}=${l.value}(ae ${l.activeElement})` : `${l.kind}`))}`]);
  if (!r1.enabled) {
    await p.waitForTimeout(4500);
    await sw.click();
    await p.getByText(/Rule toggled successfully|Failed to toggle rule/).first().waitFor({ timeout: 15000 }).catch(() => {});
    await p.waitForTimeout(1500);
    const r2 = await ruleOf();
    out.steps.push({ step: "mouse-restore-fallback", apiEnabled: r2.enabled, apiUpdatedAt: r2.updatedAt });
  }
  await browser.close();
  const r3 = await ruleOf();
  out.apiAfter = { enabled: r3.enabled, updatedAt: r3.updatedAt };
  fs.writeFileSync(path.join(L.RAW, "k114-focus-diagnose.json"), JSON.stringify(out));
  console.log(JSON.stringify(out).slice(0, 3000));
})();
