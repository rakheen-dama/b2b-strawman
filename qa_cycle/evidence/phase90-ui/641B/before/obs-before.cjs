// 641B observe-first "before" capture (task 641.7). Read-only: drives the e2e mock-auth stack
// (frontend :3001) as Alice (owner) and Carol (member), screenshots the matter detail page at
// 1024x768 / 1440x900 in light + dark, and dumps header DOM facts, Overview KPI cards and
// console errors/warnings to obs-before.json.
// Run: node obs-before.cjs   (nvm node v20; requires the worktree frontend's @playwright/test)
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-641B/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const OUT = __dirname;

const USERS = [
  { key: "alice", userId: "user_e2e_alice" },
  { key: "carol", userId: "user_e2e_carol" },
];
// shared = a matter both Alice and Carol are members of (Carol is only on 3 test matters).
const MATTERS = {
  shared: { id: "e56794da-53ad-41aa-83e1-8d65ee33dc50", note: "Budget Test Project 6crh (budget + time, no client)" },
  rich: { id: "1960b1cd-0efd-4e2e-a55b-3fdcebf53365", note: "Website Redesign (client, fee notes)" },
  closed: { id: "71588d0d-c986-4ff3-8eb4-55f8ca2e7a4c", note: "Ndlovu v RAF Depth II (CLOSED, trust txns)" },
};
const VIEWPORTS = [
  { w: 1024, h: 768 },
  { w: 1440, h: 900 },
];
const THEMES = ["light", "dark"];

// [user, matterKey, full matrix?]
const PLAN = [
  ["alice", "shared", true],
  ["carol", "shared", true],
  ["alice", "rich", true],
  ["alice", "closed", false],
  ["carol", "rich", false], // access check: Carol is not a project member
];

async function login(page, userId) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.selectOption("#user-select", userId);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/org\/e2e-test-org\/dashboard/, { timeout: 60000 });
}

async function domFacts(page) {
  return page.evaluate(() => {
    const q = (s) => document.querySelector(s);
    const txt = (el) => (el ? el.textContent.replace(/\s+/g, " ").trim() : null);
    const card = q('[data-testid="matter-header-card"]');
    const fieldsDl = q('[data-testid="matter-header-fields"]');
    const fields = fieldsDl
      ? [...fieldsDl.querySelectorAll('[data-testid^="matter-header-field-"]')].map((f) => ({
          testid: f.getAttribute("data-testid"),
          dt: txt(f.querySelector("dt")),
          dd: txt(f.querySelector("dd")),
        }))
      : null;
    const actions = q('[data-testid="header-lifecycle-actions"]');
    const grid = q('[data-testid="metric-card-grid"]');
    const cards = [...document.querySelectorAll('[data-testid^="metric-card-"]')]
      .filter((c) => c.getAttribute("data-testid") !== "metric-card-grid")
      .map((c) => ({ testid: c.getAttribute("data-testid"), text: txt(c) }));
    const h1s = [...document.querySelectorAll("h1")].map((h) => ({
      testid: h.getAttribute("data-testid"),
      text: txt(h),
    }));
    return {
      url: location.pathname,
      htmlClass: document.documentElement.className,
      headerCardPresent: !!card,
      headerCardTag: card ? card.tagName : null,
      headerCardText: txt(card),
      h1Count: h1s.length,
      h1s,
      matterName: txt(q('[data-testid="matter-name"]')),
      statusBadge: txt(q('[data-testid="project-status-badge"]')),
      closedAt: txt(q('[data-testid="project-closed-at"]')),
      headerFieldsPresent: !!fieldsDl,
      headerFields: fields,
      lifecycleActionsPresent: !!actions,
      lifecycleButtons: actions
        ? [...actions.querySelectorAll("button, a")].map((b) => ({
            text: txt(b),
            testid: b.getAttribute("data-testid"),
            ariaLabel: b.getAttribute("aria-label"),
          }))
        : null,
      healthHeader: txt(q('[data-testid="project-health-header"]')),
      metricGridPresent: !!grid,
      metricCards: cards,
      upcomingDeadlines: txt(q('[data-testid="matter-upcoming-deadlines-tile"]')),
      tabs: [...document.querySelectorAll('[role="tab"]')].map((t) => ({
        text: txt(t),
        selected: t.getAttribute("aria-selected"),
      })),
    };
  });
}

(async () => {
  const browser = await chromium.launch();
  const results = [];
  for (const userKey of ["alice", "carol"]) {
    const user = USERS.find((u) => u.key === userKey);
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    let consoleBuf = [];
    page.on("console", (m) => {
      if (m.type() === "error" || m.type() === "warning")
        consoleBuf.push({ type: m.type(), text: m.text().slice(0, 600) });
    });
    page.on("pageerror", (e) => consoleBuf.push({ type: "pageerror", text: String(e).slice(0, 600) }));
    await login(page, user.userId);

    for (const [u, matterKey, full] of PLAN.filter((p) => p[0] === userKey)) {
      const matter = MATTERS[matterKey];
      const combos = full
        ? VIEWPORTS.flatMap((vp) => THEMES.map((t) => [vp, t]))
        : [[VIEWPORTS[1], "light"]];
      for (const [vp, theme] of combos) {
        await page.setViewportSize({ width: vp.w, height: vp.h });
        await page.evaluate((t) => localStorage.setItem("kazi-theme", t), theme);
        consoleBuf = [];
        const url = `${BASE}/org/${SLUG}/matters/${matter.id}`;
        const resp = await page.goto(url, { waitUntil: "load", timeout: 90000 });
        let h1ok = true;
        try {
          await page.locator("h1").first().waitFor({ state: "visible", timeout: 60000 });
          await page
            .locator('[data-testid="metric-card-grid"]')
            .waitFor({ state: "visible", timeout: 20000 })
            .catch(() => {});
        } catch {
          h1ok = false;
        }
        await page.waitForTimeout(2500); // let client islands hydrate / late console warnings land
        const file = `${u}-${matterKey}-${vp.w}-${theme}.png`;
        await page.screenshot({ path: path.join(OUT, file), fullPage: true });
        const facts = await domFacts(page);
        results.push({
          user: u,
          matter: matterKey,
          matterId: matter.id,
          matterNote: matter.note,
          viewport: `${vp.w}x${vp.h}`,
          theme,
          httpStatus: resp ? resp.status() : null,
          h1ok,
          file,
          facts,
          console: consoleBuf,
        });
        console.log(`captured ${file} status=${resp && resp.status()} h1=${h1ok} html=${facts.htmlClass} console=${consoleBuf.length}`);
      }
    }
    await context.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, "obs-before.json"), JSON.stringify(results, null, 2));
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
