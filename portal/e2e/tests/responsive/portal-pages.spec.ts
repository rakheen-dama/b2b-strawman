import { test, expect, type Locator, type Page } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";
import { SEED, portalApiGet } from "../../helpers/firm-api";

/**
 * Epic 499B — Portal visual baselines at sm / md / lg.
 *
 * Iterates over 7 representative portal routes and captures `toHaveScreenshot`
 * snapshots at three viewport sizes — producing 21 baselines under
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
 * Phase 88 628G: each route waits for its h1 and one loaded-content anchor
 * from the e2e boot seed (web-first; no settle delay) before the snapshot, so
 * a capture of /login or a loading skeleton fails instead of passing.
 *
 * Run-dependent regions (the trust balance, the trust transactions and the
 * home "Last trust movement" card change with every 90-day Day 21 deposit) are
 * masked in the capture; the strict assertions in `ready` run before it.
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

/** The portal JWT the browser session uses (for the one data-dependent route). */
async function sessionJwt(): Promise<string> {
  const jwt = await getPortalJwt(PORTAL_EMAIL);
  if (!jwt) {
    throw new Error(
      `Could not obtain portal JWT for ${PORTAL_EMAIL}. Confirm the seeded stack has a portal contact with that email.`,
    );
  }
  return jwt;
}

/** A home-page card by its title (the @b2mash/ui Card root is `data-slot="card"`). */
const homeCard = (page: Page, title: string) =>
  page.locator('[data-slot="card"]').filter({
    has: page.locator('[data-slot="card-title"]', { hasText: title }),
  });

const h1 = (page: Page, name: string) =>
  expect(page.getByRole("heading", { level: 1, name })).toBeVisible();

/**
 * 7 representative portal pages, each with the content that proves it loaded
 * (the e2e boot seed: Acme Corp's contact, INV-0001 SENT, Website Redesign,
 * BIWEEKLY digest cadence).
 */
// Phase 87 S10 (ADR-344): /acceptance row dropped — the page never existed; the engagement-letters list is on /matters (617A, OQ #5)
const PAGES: Array<{
  path: string;
  name: string;
  ready: (page: Page) => Promise<void>;
  /** Run-dependent regions to mask in the capture (asserted in `ready` first). */
  mask?: (page: Page) => Locator[];
}> = [
  {
    path: "/home",
    name: "home-populated",
    ready: async (page) => {
      await h1(page, "Home");
      // Positive count: the card falls back to 0 when /portal/requests fails
      // (app/(authenticated)/home/page.tsx), and seeded REQ-0001 (SENT, read-only)
      // keeps at least one request pending.
      await expect(
        page.getByRole("link", { name: /Pending info requests/ }).locator("p"),
      ).toHaveText(/^[1-9]\d*$/);
      // Fee-note card past "Loading...": the seed's SENT INV-0001 means it
      // lists at least one fee note.
      await expect(
        homeCard(page, "Recent fee notes")
          .locator('a[href^="/invoices/"]')
          .first(),
      ).toBeVisible();
      // Trust card past "Loading...", in the state the portal API reports
      // (the card also falls back to "No recent activity" on a failed fetch).
      const movements = await portalApiGet<unknown[]>(
        "/portal/trust/movements?limit=1",
        await sessionJwt(),
      );
      const trust = homeCard(page, "Last trust movement");
      if (movements.length > 0) {
        await expect(trust).toContainText(/\d/);
        await expect(trust).not.toContainText("No recent activity");
      } else {
        await expect(trust).toContainText("No recent activity");
      }
    },
    mask: (page) => [
      homeCard(page, "Last trust movement").locator(
        '[data-slot="card-content"]',
      ),
    ],
  },
  {
    path: "/trust",
    name: "trust-matter-list",
    ready: async (page) => {
      // /trust redirects to the matter when the client has exactly one trust
      // matter (app/(authenticated)/trust/page.tsx) — after the 90-day Day 21
      // deposit Acme has one; on a fresh stack it has none. Expect the state
      // the portal API reports.
      const { matters } = await portalApiGet<{
        matters: { matterId: string }[];
      }>("/portal/trust/summary", await sessionJwt());
      if (matters.length === 1) {
        await expect(page).toHaveURL(
          new RegExp(`/trust/${matters[0].matterId}$`),
        );
        await expect(page.getByLabel("Current balance")).toBeVisible();
      } else {
        await h1(page, "Trust statement");
        if (matters.length === 0) {
          await expect(
            page.getByText("No trust activity on your matters"),
          ).toBeVisible();
        }
      }
    },
    // Balance + transactions grow with every Day 21 deposit. By label, not by
    // table role: below md the list renders as a <ul aria-label="Trust
    // transactions"> (components/trust/transaction-list.tsx).
    mask: (page) => [
      page.getByLabel("Current balance"),
      page.getByLabel("Trust transactions"),
    ],
  },
  {
    path: "/deadlines",
    name: "deadlines-list",
    ready: async (page) => {
      await h1(page, "Diary dates");
      // No portal-visible deadline exists on the e2e tenant (90-day Day 7).
      await expect(page.getByTestId("deadline-list-empty")).toBeVisible();
    },
  },
  {
    path: "/invoices",
    name: "invoices-list",
    ready: async (page) => {
      await h1(page, "Fee notes");
      await expect(
        page
          .getByRole("link", { name: new RegExp(SEED.feeNoteSentNumber) })
          .first(),
      ).toBeVisible();
    },
  },
  // Phase 87 S10 (ADR-344): route rename
  {
    path: "/matters",
    name: "matters-list",
    ready: async (page) => {
      await h1(page, "Your matters");
      await expect(
        page
          .locator('main a[href^="/matters/"]')
          .filter({ has: page.getByText(SEED.matter, { exact: true }) }),
      ).toBeVisible();
    },
  },
  {
    path: "/settings/notifications",
    name: "settings-notifications",
    ready: async (page) => {
      await h1(page, "Notification Preferences");
      await expect(
        page.getByText("Your firm sends bi-weekly digests.", { exact: true }),
      ).toBeVisible();
    },
  },
  {
    path: "/profile",
    name: "profile",
    ready: async (page) => {
      await h1(page, "Profile");
      await expect(page.getByText(PORTAL_EMAIL, { exact: true })).toBeVisible();
    },
  },
];

test.describe.configure({ mode: "serial" });

// Default: run. CI without a seeded portal stack must opt out by exporting
// SKIP_PORTAL_BASELINES=true (the unset/empty case should fail loudly so
// the baselines aren't silently green).
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";
const PORTAL_EMAIL = process.env.PORTAL_CONTACT_EMAIL || SEED.contactEmail;

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
  await loginAsPortalContact(page, await sessionJwt());
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
        // Web-first: the route's h1 + loaded content, never a settle delay
        // (`networkidle` is racy for SPAs that poll in the background).
        await p.ready(page);
        await expect(page).toHaveScreenshot(`${bp.name}/${p.name}.png`, {
          fullPage: true,
          maxDiffPixelRatio: 0.01,
          animations: "disabled",
          mask: p.mask?.(page) ?? [],
        });
      });
    }
  });
}
