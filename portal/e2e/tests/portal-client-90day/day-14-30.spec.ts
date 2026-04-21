import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";

/**
 * Epic 500A — Portal client-POV 90-day lifecycle: Day 14 → Day 30.
 *
 * Exercises checkpoints 4–6 from `qa/testplan/demos/portal-client-90day-keycloak.md`:
 *   - Day 14: Client reviews + accepts proposal                                  [all profiles]
 *   - Day 21: Trust-deposit nudge, views balance on /trust                       [legal-za only]
 *   - Day 30: Client views hour-bank remaining + consumption on /retainer        [consulting-za / legal-za]
 *
 * Prerequisites: same stack contract as day-00-07.spec.ts. Day 21 requires a
 * legal-za tenant with at least one trust-deposit transaction; Day 30 requires
 * a consulting-za or legal-za tenant with a retainer engagement.
 *
 * Run (full — slice 500B):
 *   PLAYWRIGHT_BASE_URL=http://localhost:3002 \
 *     pnpm exec playwright test --config=playwright.portal.config.ts \
 *     --project=portal-client-90day --update-snapshots
 *
 * Scaffold guard (slice 500A — this PR):
 *   SKIP_PORTAL_BASELINES=true is honoured so `playwright --list` parses cleanly.
 */

const PORTAL_EMAIL =
  process.env.PORTAL_CONTACT_EMAIL || "sipho.portal@example.com";
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";

test.describe.serial("Day 14–30 — Portal Client Lifecycle (Checkpoints 4–6)", () => {
  test.beforeAll(async () => {
    test.skip(
      SKIP,
      "SKIP_PORTAL_BASELINES=true — scaffold-only run (Epic 500A). Unset with a seeded portal stack to execute (Epic 500B).",
    );
  });

  test.beforeEach(async ({ page }) => {
    const jwt = await getPortalJwt(PORTAL_EMAIL);
    if (!jwt) {
      test.skip(
        true,
        `Could not obtain portal JWT for ${PORTAL_EMAIL}. Stack may not be running or contact not seeded — deferred to slice 500B execution.`,
      );
      return;
    }
    await loginAsPortalContact(page, jwt);
  });

  // ── Checkpoint 4 — Day 14: proposal review + accept ────────────────────
  test("Day 14: Client opens proposal list and detail view", async ({ page }) => {
    await page.goto("/proposals");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "/proposals not rendered — tenant may not have proposals seeded");
      return;
    }

    // Click into the first pending proposal row/link
    const proposalLink = page
      .getByRole("link", { name: /proposal|pending|review/i })
      .first();
    if (await proposalLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await proposalLink.click();
      await page.waitForLoadState("domcontentloaded");
      await page.waitForTimeout(500);
    }

    await expect(page).toHaveScreenshot("day-14-proposal-review.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  test("Day 14: Client accepts proposal", async ({ page }) => {
    await page.goto("/proposals");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const proposalLink = page
      .getByRole("link", { name: /proposal|pending|review/i })
      .first();
    if (!(await proposalLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "No pending proposal rendered — firm-side prerequisite not met");
      return;
    }

    await proposalLink.click();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const acceptBtn = page
      .getByRole("button", { name: /accept|approve|agree/i })
      .first();
    if (!(await acceptBtn.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Accept action not rendered — proposal may already be accepted");
      return;
    }

    await acceptBtn.click();
    await page.waitForTimeout(800);

    // Some variants route through a confirm dialog; others are inline.
    const confirmBtn = page
      .getByRole("button", { name: /confirm|yes|accept/i })
      .first();
    if (await confirmBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await confirmBtn.click();
      await page.waitForTimeout(1000);
    }

    await page.waitForLoadState("domcontentloaded");

    // Soft assertion: accepted state indicator
    expect
      .soft(
        await page
          .getByText(/accepted|agreed|confirmed/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false),
      )
      .toBeTruthy();

    await expect(page).toHaveScreenshot("day-14-proposal-accepted.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 5 — Day 21: trust balance (legal-za only) ───────────────
  test("Day 21 [legal-za]: Client views trust balance after first deposit", async ({
    page,
  }) => {
    await page.goto("/trust");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(
        true,
        "/trust not rendered — non-legal-za tenant (profile-gated skip is expected)",
      );
      return;
    }

    // Empty state vs populated — both acceptable; only fail on 500.
    const bodyText = (await page.locator("body").textContent().catch(() => "")) || "";
    if (/something went wrong|internal server error/i.test(bodyText)) {
      throw new Error("/trust rendered an error page — investigate backend");
    }

    expect
      .soft(
        await page
          .getByText(/trust|balance|deposit|ledger/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false),
      )
      .toBeTruthy();

    await expect(page).toHaveScreenshot("day-21-trust-balance.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 6 — Day 30: hour-bank on /retainer ──────────────────────
  test("Day 30 [consulting-za / legal-za]: Client views hour-bank remaining", async ({
    page,
  }) => {
    await page.goto("/retainer");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(
        true,
        "/retainer not rendered — profile may not expose retainer (accounting-za gated skip)",
      );
      return;
    }

    const bodyText = (await page.locator("body").textContent().catch(() => "")) || "";
    if (/something went wrong|internal server error/i.test(bodyText)) {
      throw new Error("/retainer rendered an error page — investigate backend");
    }

    expect
      .soft(
        await page
          .getByText(/retainer|hour|remaining|consumption/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false),
      )
      .toBeTruthy();

    await expect(page).toHaveScreenshot("day-30-retainer-hour-bank.png", {
      fullPage: true,
      animations: "disabled",
    });
  });
});
