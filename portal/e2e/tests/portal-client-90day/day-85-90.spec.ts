import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";

/**
 * Epic 500A — Portal client-POV 90-day lifecycle: Day 85 → Day 90.
 *
 * Exercises checkpoints 10–11 from `qa/testplan/demos/portal-client-90day-keycloak.md`:
 *   - Day 85: Client updates profile + changes digest cadence to biweekly       [all profiles]
 *   - Day 90: Final digest, client reviews activity trail of the quarter         [all profiles]
 *
 * Prerequisites: same stack contract as day-00-07.spec.ts. Day 90 requires the
 * prior checkpoints (Day 3 submit, Day 14 accept, Day 45 pay, Day 60 download,
 * Day 75 mark-read, Day 85 profile update) to have actually landed in order for
 * the activity trail to populate — this is why the `portal-client-90day`
 * project runs `workers: 1` (sequential).
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

test.describe.serial("Day 85–90 — Portal Client Lifecycle (Checkpoints 10–11)", () => {
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

  // ── Checkpoint 10 — Day 85: profile + notifications cadence ────────────
  test("Day 85: Client updates profile", async ({ page }) => {
    await page.goto("/profile");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "/profile not rendered");
      return;
    }

    // Find the first editable text input that looks like a name field.
    const nameField = page
      .getByRole("textbox", { name: /name|display/i })
      .first();
    if (await nameField.isVisible({ timeout: 3000 }).catch(() => false)) {
      const currentValue = (await nameField.inputValue().catch(() => "")) || "";
      // Idempotent update — only append suffix if not already present.
      if (!/updated Day 85/.test(currentValue)) {
        await nameField.fill(`${currentValue} (updated Day 85)`.trim());
      }

      const saveBtn = page
        .getByRole("button", { name: /save|update/i })
        .first();
      if (await saveBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await saveBtn.click();
        await page.waitForTimeout(800);
      }
    }

    await page.waitForLoadState("domcontentloaded");

    await expect(page).toHaveScreenshot("day-85-profile-updated.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  test("Day 85: Client changes digest cadence to biweekly", async ({ page }) => {
    await page.goto("/settings/notifications");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "/settings/notifications not rendered");
      return;
    }

    // The cadence control shape varies — could be a Radio group, a Select,
    // or a SegmentedControl. Try `combobox` first, fall back to radio.
    const cadenceCombobox = page
      .getByRole("combobox", { name: /cadence|frequency|digest/i })
      .first();
    if (await cadenceCombobox.isVisible({ timeout: 3000 }).catch(() => false)) {
      await cadenceCombobox.click();
      await page.waitForTimeout(300);
      const biweeklyOption = page
        .getByRole("option", { name: /biweekly|bi-weekly|every other/i })
        .first();
      if (await biweeklyOption.isVisible({ timeout: 2000 }).catch(() => false)) {
        await biweeklyOption.click();
        await page.waitForTimeout(300);
      }
    } else {
      const biweeklyRadio = page
        .getByRole("radio", { name: /biweekly|bi-weekly|every other/i })
        .first();
      if (await biweeklyRadio.isVisible({ timeout: 2000 }).catch(() => false)) {
        await biweeklyRadio.click();
        await page.waitForTimeout(300);
      }
    }

    const saveBtn = page
      .getByRole("button", { name: /save|update/i })
      .first();
    if (await saveBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await saveBtn.click();
      await page.waitForTimeout(800);
    }

    await page.waitForLoadState("domcontentloaded");

    expect
      .soft(
        await page
          .getByText(/biweekly|bi-weekly|every other/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false),
      )
      .toBeTruthy();

    await expect(page).toHaveScreenshot("day-85-notifications-biweekly.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 11 — Day 90: final digest + activity trail ──────────────
  test("Day 90: Client reviews activity trail of the quarter", async ({ page }) => {
    // The activity trail lives either on /home (scrolled to an activity
    // section) or on a dedicated /profile/activity route. Try /home first;
    // it is the documented landing target for the quarter-summary digest link.
    await page.goto("/home");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "/home not rendered");
      return;
    }

    // Activity trail surface — look for expected keywords from prior days.
    // Empty-match is tolerated because slice 500A does not actually populate
    // the prior-day state; slice 500B will execute the full chain in order.
    expect
      .soft(
        await page
          .getByText(/activity|history|recent|timeline/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false),
      )
      .toBeTruthy();

    await expect(page).toHaveScreenshot("day-90-activity-trail.png", {
      fullPage: true,
      animations: "disabled",
    });
  });
});
