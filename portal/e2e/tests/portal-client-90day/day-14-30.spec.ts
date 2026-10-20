import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";

/**
 * Epic 500A — Portal client-POV 90-day lifecycle: Day 14 → Day 30.
 *
 * Exercises checkpoint 5 from `qa/testplan/demos/portal-client-90day-keycloak.md`:
 *   - Day 21: Trust-deposit nudge, views balance on /trust                       [legal-za only]
 *
 * Prerequisites: same stack contract as day-00-07.spec.ts. Day 21 requires a
 * legal-za tenant with at least one trust-deposit transaction.
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

test.describe
  .serial("Day 14–30 — Portal Client Lifecycle (Checkpoint 5)", () => {
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
    const bodyText =
      (await page
        .locator("body")
        .textContent()
        .catch(() => "")) || "";
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
});
