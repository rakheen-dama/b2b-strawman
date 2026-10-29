import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";
import { RUN_STARTED_AT, SEED, portalApiGet } from "../../helpers/firm-api";

/**
 * Portal client-POV 90-day lifecycle: Day 85 → Day 90
 * (checkpoints 10–11 of `qa/testplan/demos/portal-client-90day-keycloak.md`).
 *   - Day 85: the client's profile (read-only) and the firm's digest cadence (read-only)
 *   - Day 90: the client reviews the activity trail of the quarter
 *
 * Phase 88 628G:
 *   - The portal profile is display-only (app/(authenticated)/profile/page.tsx; backend
 *     PortalProfileController exposes GET /portal/me only), so Day 85 checks the contact details
 *     the client sees; profile editing (scenario 85.1–85.2) is a recorded finding, not a test.
 *   - Digest cadence is firm-level by design (ADR-258; gap register §5 "Day 85 digest cadence"):
 *     the seed sets BIWEEKLY (compose/seed/seed.sh) and the portal page shows it read-only.
 *   - Day 90 reads the events this run's Day 3 (upload + submit) and Day 60 (statement download)
 *     produced, so it depends on day-00-07 and day-45-75 having run first in the same worker
 *     (`workers: 1`, files in name order) — if either failed, Day 90 fails too, by design.
 *
 * Strict: nothing is soft-asserted or skipped at runtime; SKIP_PORTAL_BASELINES=true is the only
 * opt-out.
 */

const PORTAL_EMAIL = process.env.PORTAL_CONTACT_EMAIL || SEED.contactEmail;
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";

type ActivityEvent = {
  eventType: string;
  actorType: string;
  occurredAt: string;
};

test.describe
  .serial("Day 85–90 — Portal Client Lifecycle (Checkpoints 10–11)", () => {
  let jwt: string;

  test.beforeAll(() => {
    test.skip(
      SKIP,
      "SKIP_PORTAL_BASELINES=true — portal e2e disabled (no seeded stack).",
    );
  });

  test.beforeEach(async ({ page }) => {
    const token = await getPortalJwt(PORTAL_EMAIL);
    expect(
      token,
      `portal JWT for ${PORTAL_EMAIL}: stack up, contact seeded, and neither magic-link cap hit ` +
        `(3 tokens per contact per 5 min, DB; 3 request-link calls per email per 15 min, in-memory)`,
    ).toBeTruthy();
    jwt = token!;
    await loginAsPortalContact(page, jwt);
  });

  // ── Checkpoint 10 — Day 85: profile + notifications ────────────────────
  test("Day 85: Profile shows the client's contact details (read-only)", async ({
    page,
  }) => {
    await page.goto("/profile");
    await expect(
      page.getByRole("heading", { level: 1, name: "Profile" }),
    ).toBeVisible();
    await expect(page.getByText("Contact Information")).toBeVisible();
    // Each <dt>/<dd> pair sits in its own <div> (Name, Email, Role, Client).
    const field = (label: string) =>
      page
        .locator("main dl > div")
        .filter({ has: page.locator("dt", { hasText: label }) })
        .locator("dd");
    await expect(field("Email")).toHaveText(SEED.contactEmail);
    await expect(field("Client")).toHaveText(SEED.client);
    await expect(field("Role")).not.toBeEmpty();
    // Display-only today: no editable field, no save action.
    await expect(page.locator("main").getByRole("textbox")).toHaveCount(0);
    await expect(
      page.locator("main").getByRole("button", { name: /save|update/i }),
    ).toHaveCount(0);

    await expect(page).toHaveScreenshot("day-85-profile.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  test("Day 85: Notifications page shows the firm's bi-weekly digest cadence (read-only)", async ({
    page,
  }) => {
    await page.goto("/settings/notifications");
    await expect(
      page.getByRole("heading", { level: 1, name: "Notification Preferences" }),
    ).toBeVisible();
    await expect(
      page.getByText("Your firm sends bi-weekly digests.", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("switch", { name: /Weekly digest/ }),
    ).toBeVisible();
    // The cadence itself is the firm's setting: the client has no control for it.
    await expect(page.locator("main").getByRole("combobox")).toHaveCount(0);
    await expect(page.locator("main").getByRole("radio")).toHaveCount(0);

    await expect(page).toHaveScreenshot("day-85-notifications-biweekly.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 11 — Day 90: activity trail ─────────────────────────────
  test("Day 90: Activity trail shows this run's submit and download", async ({
    page,
  }) => {
    // This run's Day 3 and Day 60 events (not an older run's): occurred after the helpers loaded,
    // less a minute of host/container clock tolerance.
    const mine = await portalApiGet<{ content: ActivityEvent[] }>(
      "/portal/activity?tab=MINE",
      jwt,
    );
    const thisRun = (eventType: string) =>
      mine.content.some(
        (e) =>
          e.eventType === eventType &&
          e.actorType === "PORTAL_CONTACT" &&
          Date.parse(e.occurredAt) >= RUN_STARTED_AT - 60_000,
      );
    expect(thisRun("portal.request_item.submitted")).toBe(true);
    expect(thisRun("portal.document.downloaded")).toBe(true);

    await page.goto("/activity");
    await expect(
      page.getByRole("heading", { level: 1, name: "Activity" }),
    ).toBeVisible();
    await expect(
      page.getByRole("tab", { name: "Your actions" }),
    ).toHaveAttribute("aria-selected", "true");
    const list = page.getByTestId("activity-list");
    await expect(
      list.getByText("You submitted an information request item").first(),
    ).toBeVisible();
    await expect(
      list.getByText("You downloaded a document").first(),
    ).toBeVisible();

    await expect(page).toHaveScreenshot("day-90-activity-trail.png", {
      fullPage: true,
      animations: "disabled",
    });
  });
});
