import { test, expect, type Page } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";

/**
 * Epic 499B — Portal visual baselines at sm / md / lg.
 *
 * Iterates over 10 representative portal routes and captures `toHaveScreenshot`
 * snapshots at three viewport sizes — producing 30 baselines under
 *   portal/e2e/screenshots/portal-v2/{projectName}/{sm,md,lg}/<name>.png
 * (the snapshotPathTemplate in playwright.portal.config.ts maps the first
 * `toHaveScreenshot` arg to that path).
 *
 * These tests require a running, seeded portal stack (portal on :3002 with a
 * valid portal-contact JWT). The default is "run" — in an environment where
 * the portal stack is not provisioned (e.g. sandboxed builders), explicitly
 * export `SKIP_PORTAL_BASELINES=true` to skip. Without that opt-out the
 * `webServer` block in `playwright.portal.config.ts` will boot the portal.
 *
 * Future work: mask dynamic timestamps/counts via selectors once the pages
 * expose `data-testid` markers for them. For now we use a conservative
 * `domcontentloaded` + short settle delay (networkidle is racy for SPAs that
 * poll in the background).
 */

type Breakpoint = {
  name: "sm" | "md" | "lg";
  width: number;
  height: number;
};

const BREAKPOINTS: Breakpoint[] = [
  { name: "sm", width: 375, height: 667 },
  { name: "md", width: 768, height: 1024 },
  { name: "lg", width: 1280, height: 800 },
];

/**
 * 10 representative portal pages. Detail routes that need a seeded id
 * (trust matter, retainer, invoice, proposal) are resolved at runtime via
 * `beforeAll` — see the stub below; supply real ids via env when available.
 */
const PAGES: Array<{ path: string; name: string }> = [
  { path: "/home", name: "home-populated" },
  { path: "/trust", name: "trust-matter-list" },
  { path: "/retainer", name: "retainer-list" },
  { path: "/deadlines", name: "deadlines-list" },
  { path: "/invoices", name: "invoices-list" },
  { path: "/proposals", name: "proposals-list" },
  { path: "/projects", name: "projects-list" },
  { path: "/settings/notifications", name: "settings-notifications" },
  { path: "/profile", name: "profile" },
  { path: "/acceptance", name: "acceptance-list" },
];

test.describe.configure({ mode: "serial" });

// Default: run. CI without a seeded portal stack must opt out by exporting
// SKIP_PORTAL_BASELINES=true (the unset/empty case should fail loudly so
// the baselines aren't silently green).
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";
const PORTAL_EMAIL =
  process.env.PORTAL_CONTACT_EMAIL || "alice.portal@example.com";

test.beforeAll(async () => {
  if (SKIP) {
    test.info().annotations.push({
      type: "skipped-reason",
      description: "SKIP_PORTAL_BASELINES=true",
    });
  }
  test.skip(
    SKIP,
    "Portal baseline capture disabled via SKIP_PORTAL_BASELINES=true. Unset (or set to anything other than 'true') with a seeded portal stack to generate baselines.",
  );
});

async function authenticatePortalSession(page: Page): Promise<void> {
  const jwt = await getPortalJwt(PORTAL_EMAIL);
  if (!jwt) {
    throw new Error(
      `Could not obtain portal JWT for ${PORTAL_EMAIL}. Confirm the seeded stack has a portal contact with that email.`,
    );
  }
  await loginAsPortalContact(page, jwt);
}

for (const bp of BREAKPOINTS) {
  test.describe(`portal @ ${bp.name} (${bp.width}x${bp.height})`, () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize({ width: bp.width, height: bp.height });
      await authenticatePortalSession(page);
    });

    for (const p of PAGES) {
      test(`${p.name}`, async ({ page }) => {
        await page.goto(p.path);
        // `networkidle` is racy for SPAs that poll in the background (e.g.
        // the portal's auth refresh timer). Prefer `domcontentloaded` + a
        // short settle delay for visual baselines.
        await page.waitForLoadState("domcontentloaded");
        await page.waitForTimeout(500);
        await expect(page).toHaveScreenshot(`${bp.name}/${p.name}.png`, {
          fullPage: true,
          maxDiffPixelRatio: 0.01,
          animations: "disabled",
          // Future work: add `mask: [page.locator('[data-dynamic="timestamp"]'), ...]`
          // once portal pages tag volatile regions with data-dynamic selectors.
        });
      });
    }
  });
}
