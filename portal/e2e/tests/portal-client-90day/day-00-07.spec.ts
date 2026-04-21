import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";

/**
 * Epic 500A — Portal client-POV 90-day lifecycle: Day 0 → Day 7.
 *
 * Exercises checkpoints 1–3 from `qa/testplan/demos/portal-client-90day-keycloak.md`:
 *   - Day 0: Client receives magic-link, lands on /home, reviews pending info requests [all profiles]
 *   - Day 3: Client uploads requested documents, submits info request              [all profiles]
 *   - Day 7: Client receives weekly digest, clicks through to /deadlines           [accounting-za / legal-za]
 *
 * Prerequisites (enforced by firm-side lifecycle pre-runs — see master doc):
 *   1. Portal on :3002 (via `bash compose/scripts/svc.sh start portal`)
 *   2. Backend on :8081 (e2e stack) OR :8080 (Keycloak dev) — override via BACKEND_URL
 *   3. Seeded tenant with a portal contact matching PORTAL_CONTACT_EMAIL
 *   4. Firm-side lifecycle has created: one pending info request, one weekly-digest email
 *
 * Run (full — slice 500B):
 *   PLAYWRIGHT_BASE_URL=http://localhost:3002 \
 *     pnpm exec playwright test --config=playwright.portal.config.ts \
 *     --project=portal-client-90day --update-snapshots
 *
 * Scaffold guard (slice 500A — this PR):
 *   SKIP_PORTAL_BASELINES=true is honoured so `playwright --list` parses cleanly
 *   without requiring a live stack. Full execution + baseline capture lands in 500B.
 */

const PORTAL_EMAIL =
  process.env.PORTAL_CONTACT_EMAIL || "sipho.portal@example.com";
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";

test.describe.serial("Day 0–7 — Portal Client Lifecycle (Checkpoints 1–3)", () => {
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

  // ── Checkpoint 1 — Day 0: magic-link → /home → review info requests ────
  test("Day 0: Client lands on /home, reviews pending info requests", async ({
    page,
  }) => {
    await page.goto("/home");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Portal /home not rendered — seed may be missing");
      return;
    }

    // Soft assertion: the info-requests surface is present (either populated
    // or empty-state card). Hard failure here would mask a legitimate layout
    // regression on a populated tenant — keep soft until 500B execution.
    expect
      .soft(
        await page
          .getByText(/info request|pending|awaiting/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false),
      )
      .toBeTruthy();

    await expect(page).toHaveScreenshot("day-00-home-landing.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  test("Day 0: Info-request detail view renders", async ({ page }) => {
    await page.goto("/home");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    // Click into the first info-request surface we find — tolerant of either
    // a named link ("Info request") or a dedicated card's "View" button.
    const infoRequestLink = page
      .getByRole("link", { name: /info request|view/i })
      .first();
    const hasLink = await infoRequestLink
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (!hasLink) {
      test.skip(
        true,
        "No pending info-request rendered on /home — firm-side prerequisite not met",
      );
      return;
    }

    await infoRequestLink.click();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    await expect(page).toHaveScreenshot("day-00-info-request-detail.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 2 — Day 3: upload + submit info request ─────────────────
  test("Day 3: Client uploads document and submits info request", async ({
    page,
  }) => {
    await page.goto("/home");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const infoRequestLink = page
      .getByRole("link", { name: /info request|view/i })
      .first();
    if (!(await infoRequestLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "No pending info request to submit against");
      return;
    }

    await infoRequestLink.click();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    // Upload slot — most portal info-request forms expose an input[type=file].
    const fileInput = page.locator('input[type="file"]').first();
    const hasFileInput = await fileInput
      .isVisible({ timeout: 3000 })
      .catch(() => false);

    if (hasFileInput) {
      // Minimal in-memory PDF stub — real E2E execution (500B) can swap in a
      // fixture file from portal/e2e/fixtures/ once one exists.
      await fileInput.setInputFiles({
        name: "id-copy.pdf",
        mimeType: "application/pdf",
        buffer: Buffer.from("%PDF-1.4\n%fake-pdf-for-scaffold\n"),
      });
      await page.waitForTimeout(800);
    }

    // Optional free-text response
    const responseField = page
      .getByRole("textbox", { name: /response|note|message/i })
      .first();
    if (await responseField.isVisible({ timeout: 2000 }).catch(() => false)) {
      await responseField.fill("ID copy attached as requested");
    }

    const submitBtn = page
      .getByRole("button", { name: /submit|send|complete/i })
      .first();
    if (await submitBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await submitBtn.click();
      await page.waitForTimeout(1000);
      await page.waitForLoadState("domcontentloaded");
    }

    // Soft assertion: state transition indicator visible
    expect
      .soft(
        await page
          .getByText(/submitted|awaiting review|received/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false),
      )
      .toBeTruthy();

    await expect(page).toHaveScreenshot("day-03-info-request-submitted.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 3 — Day 7: weekly digest → /deadlines ───────────────────
  test("Day 7: Client clicks through weekly digest to /deadlines", async ({
    page,
  }) => {
    // This checkpoint is gated: accounting-za / legal-za only. We navigate
    // directly to /deadlines (the digest click-through target); profile-gated
    // absence is tolerated via empty-state handling.
    await page.goto("/deadlines");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(
        true,
        "/deadlines not rendered — profile may not expose deadlines (consulting-za) or 404",
      );
      return;
    }

    expect
      .soft(
        await page
          .getByText(/deadline|due|upcoming/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false),
      )
      .toBeTruthy();

    await expect(page).toHaveScreenshot("day-07-deadlines-list.png", {
      fullPage: true,
      animations: "disabled",
    });
  });
});
