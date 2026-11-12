// 642B after (task 642.10): diagnose the horizontal page overflow obs-after.cjs recorded at 390 / 768
// (Alice, Sipho Ndlovu mwdzyk4n12b3, light). Read-only. For each viewport: scrollWidth vs innerWidth,
// the outermost elements whose right edge passes the viewport, and scrollWidth again with (a) the
// client hero hidden, (b) the grouped tab bar hidden, (c) the active tab panel hidden. Also loads a
// matter page (641B DetailHero, already on main) and the Clients list at the same widths for
// comparison, and re-measures with the hero actions wrapper allowed to shrink. Prints JSON lines to stdout (pasted into NOTES.md).
const path = require("path");
const PW_PATH =
  process.env.PW_PATH ||
  require.resolve("@playwright/test", {
    paths: [path.resolve(__dirname, "../../../../../frontend")],
  });
const { chromium } = require(PW_PATH);

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const PAGES = {
  client: `/org/${SLUG}/clients/14fcc109-3ff2-4abc-a66e-854bbd5761c1`,
  matter: `/org/${SLUG}/matters/96d72956-2b20-4545-bcb7-6ddc344a8eac`,
  clientsList: `/org/${SLUG}/clients`,
};

async function diagnose(page) {
  return page.evaluate(() => {
    const W = window.innerWidth;
    const sw = () => document.documentElement.scrollWidth;
    const desc = (el) =>
      `${el.tagName.toLowerCase()}${el.id ? "#" + el.id : ""}${el.getAttribute("data-testid") ? `[data-testid=${el.getAttribute("data-testid")}]` : ""}${el.getAttribute("data-slot") ? `[data-slot=${el.getAttribute("data-slot")}]` : ""}`;
    const over = [...document.querySelectorAll("body *")].filter((el) => {
      const r = el.getBoundingClientRect();
      return r.width > 0 && r.right > W + 1 && getComputedStyle(el).position !== "fixed";
    });
    // outermost overflowing elements (no overflowing ancestor)
    const outer = over.filter((el) => !over.some((o) => o !== el && o.contains(el)));
    // deepest overflowing elements (no overflowing descendant)
    const leaves = over.filter((el) => !over.some((o) => o !== el && el.contains(o)));
    const fmt = (el) => {
      const r = el.getBoundingClientRect();
      return { el: desc(el), left: Math.round(r.left), right: Math.round(r.right), w: Math.round(r.width), text: (el.textContent || "").replace(/\s+/g, " ").trim().slice(0, 60) };
    };
    const withHidden = (sel) => {
      const els = [...document.querySelectorAll(sel)];
      if (!els.length) return null;
      els.forEach((e) => (e.style.display = "none"));
      const v = sw();
      els.forEach((e) => (e.style.display = ""));
      return v;
    };
    const card = document.querySelector('[data-testid="client-header-card"], [data-testid="matter-header-card"]');
    const cr = card ? card.getBoundingClientRect() : null;
    return {
      url: location.pathname,
      innerWidth: W,
      scrollWidth: sw(),
      heroRect: cr ? { left: Math.round(cr.left), right: Math.round(cr.right), w: Math.round(cr.width) } : null,
      heroScrollWidth: card ? card.scrollWidth : null,
      outer: outer.slice(0, 8).map(fmt),
      leaves: leaves.slice(0, 12).map(fmt),
      scrollWidthHeroHidden: withHidden('[data-testid="client-header-card"], [data-testid="matter-header-card"]'),
      scrollWidthTabBarHidden: withHidden('[role="tablist"]'),
      scrollWidthPanelHidden: withHidden('[role="tabpanel"][data-state="active"]'),
      // Root-cause check: the hero's actions wrapper is `shrink-0`; let it shrink (class removed
      // in the live DOM only, then restored) and re-measure the hero and the page.
      actionsShrinkable: (() => {
        const a = card && card.querySelector('[data-slot="detail-hero-actions"]');
        if (!a) return null;
        const had = a.classList.contains("shrink-0"); // post-fix builds no longer carry it
        a.classList.remove("shrink-0");
        const ar = a.getBoundingClientRect();
        const out = { heroScrollWidth: card.scrollWidth, actionsRight: Math.round(ar.right), scrollWidthTabBarHidden: withHidden('[role="tablist"]') };
        if (had) a.classList.add("shrink-0");
        return out;
      })(),
      // Post-fix: where the actions sit relative to the h1, as served (`asServed`) and with the
      // pre-fix `shrink-0` re-added in the live DOM only (`withShrink0`, i.e. the before layout).
      actionsLayout: (() => {
        const a = card && card.querySelector('[data-slot="detail-hero-actions"]');
        const h = card && card.querySelector("h1");
        if (!a || !h) return null;
        const m = () => {
          const ar = a.getBoundingClientRect(), hr = h.getBoundingClientRect(), c = card.getBoundingClientRect();
          return { h1Top: Math.round(hr.top), actionsTop: Math.round(ar.top), actionsLeft: Math.round(ar.left), actionsRight: Math.round(ar.right), actionsH: Math.round(ar.height), heroH: Math.round(c.height), heroScrollWidth: card.scrollWidth, scrollWidth: sw() };
        };
        const had = a.classList.contains("shrink-0");
        const asServed = m();
        a.classList.add("shrink-0");
        const withShrink0 = m();
        if (!had) a.classList.remove("shrink-0");
        return { servedHasShrink0: had, asServed, withShrink0 };
      })(),
    };
  });
}

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.selectOption("#user-select", "user_e2e_alice");
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/org\/e2e-test-org\/dashboard/, { timeout: 60000 });
  await page.evaluate(() => localStorage.setItem("kazi-theme", "light"));
  // VIEWPORTS="390x844,768x1024,1024x768,1440x900" to widen; PAGE_KEYS="client,matter" to narrow;
  // COMPACT=1 drops the outer/leaves lists; SHOTS=<prefix> screenshots the client hero per viewport.
  const VPS = (process.env.VIEWPORTS || "390x844,768x1024").split(",").map((v) => v.split("x").map(Number));
  const KEYS = process.env.PAGE_KEYS ? process.env.PAGE_KEYS.split(",") : Object.keys(PAGES);
  for (const [w, h] of VPS) {
    await page.setViewportSize({ width: w, height: h });
    for (const key of KEYS) {
      await page.goto(`${BASE}${PAGES[key]}`, { waitUntil: "load", timeout: 90000 });
      await page.locator("h1").first().waitFor({ state: "visible", timeout: 60000 }).catch(() => {});
      await page.waitForTimeout(2500);
      const d = await diagnose(page);
      if (process.env.COMPACT) {
        d.outer = d.outer.map((o) => `${o.el} ${o.left}->${o.right}`);
        delete d.leaves;
      }
      console.log(JSON.stringify({ viewport: `${w}x${h}`, page: key, ...d }));
      if (process.env.SHOTS && key === "client") {
        await page.locator('[data-testid="client-header-card"]').screenshot({ path: path.join(__dirname, `${process.env.SHOTS}-${w}-light.png`) });
      }
    }
  }
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
