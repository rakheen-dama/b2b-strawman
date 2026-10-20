/**
 * OPT-IN high-sample probe for the Next router null-prefetch race
 * (`KAZI-PATCH(next-null-prefetch)`, see tasks/next-null-prefetch-blank-segment.md).
 *
 * Without the patch, clicking into a segment that has its own `loading.tsx` while that
 * segment's prefetch is still in flight sometimes commits an empty segment and the page
 * stays blank (~6% of fresh-session clicks into a matter). The day-14 click-through test
 * (10 attempts) misses a regression at that rate about half the time, so a Next upgrade
 * or a patch change must be re-verified with this probe.
 *
 * Each attempt: fresh browser context -> login -> open the list -> click the target link
 * as soon as it is visible -> a marker only the destination renders (e.g. the matter's
 * `matter-name`) must be visible within 15 s, so the source page's heading can't count as
 * success. Any blank (or failed attempt) fails that route's test.
 *
 * Skipped unless NULL_PREFETCH_PROBE is set, so it never runs in the normal suites.
 *
 * Run (patched e2e stack on :3001; ~1 s per attempt, 3 routes):
 *   NULL_PREFETCH_PROBE=1 ATTEMPTS=120 PLAYWRIGHT_BASE_URL=http://localhost:3001 \
 *     pnpm exec playwright test --config e2e/playwright.legal-depth-ii.config.ts null-prefetch-probe
 */
import { test, expect, type Locator, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "/org/e2e-test-org";
const ATTEMPTS = Number(process.env.ATTEMPTS || "120");
const HEADING_TIMEOUT_MS = 15_000;

interface ProbeRoute {
  name: string;
  start: string;
  link: (page: Page) => Locator;
  url: RegExp;
  /** Rendered only by the destination, so the source page's h1 can't count as success. */
  marker: (page: Page) => Locator;
}

// The routes measured for the root-cause note: each destination has its own loading.tsx.
const ROUTES: ProbeRoute[] = [
  {
    name: "Matters list -> matter (projects/[id])",
    // Phase 87 S4 (ADR-344): route rename
    start: `${ORG}/matters`,
    // Phase 87 610E (ADR-344): URL-only — list hrefs now point at /matters directly
    link: (p) => p.locator(`#main-content a[href^="${ORG}/matters/"]`).first(),
    // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
    url: /\/matters\/[0-9a-f-]{36}$/,
    marker: (p) => p.locator('#main-content [data-testid="matter-name"]'),
  },
  {
    name: "Reports -> report detail (reports/[reportSlug])",
    start: `${ORG}/reports`,
    link: (p) => p.locator(`#main-content a[href^="${ORG}/reports/"]`).first(),
    url: /\/reports\/[a-z-]+$/,
    // The list page's h1 is "Reports"; the detail page's h1 is the report's name.
    marker: (p) => p.locator("#main-content h1", { hasNotText: /^\s*Reports\s*$/ }),
  },
  {
    name: "Sidebar /projects -> /dashboard",
    // Phase 87 S4 (ADR-344): route rename
    start: `${ORG}/matters`,
    link: (p) =>
      p.locator(`aside a[href="${ORG}/dashboard"], nav a[href="${ORG}/dashboard"]`).first(),
    url: /\/dashboard$/,
    marker: (p) => p.locator("#main-content h1", { hasText: /^\s*Dashboard\s*$/ }),
  },
];

test.describe("Next null-prefetch blank-segment probe (opt-in)", () => {
  test.skip(!process.env.NULL_PREFETCH_PROBE, "opt-in: NULL_PREFETCH_PROBE=1");

  for (const route of ROUTES) {
    test(`${route.name}: ${ATTEMPTS} fresh-session clicks render the destination`, async ({
      browser,
    }) => {
      // ~1 s per healthy attempt; a blank costs the full heading timeout.
      test.setTimeout(ATTEMPTS * (HEADING_TIMEOUT_MS + 15_000) + 60_000);

      const failures: string[] = [];
      let blank = 0;
      let errors = 0;

      for (let i = 1; i <= ATTEMPTS; i++) {
        const context = await browser.newContext();
        const page = await context.newPage();
        try {
          await loginAs(page, "alice");
          await page.goto(route.start);
          const link = route.link(page);
          await expect(link).toBeVisible({ timeout: 15_000 });
          await link.click();
          await expect(page).toHaveURL(route.url, { timeout: 15_000 });
          const rendered = await route
            .marker(page)
            .first()
            .waitFor({ state: "visible", timeout: HEADING_TIMEOUT_MS })
            .then(
              () => true,
              () => false
            );
          if (!rendered) {
            blank++;
            const main = await page
              .locator("#main-content")
              .innerHTML()
              .catch(() => "?");
            failures.push(
              `#${i} BLANK url=${page.url()} main=${main.replace(/\s+/g, " ").slice(0, 300)}`
            );
          }
        } catch (e) {
          errors++;
          failures.push(`#${i} ERROR ${(e as Error).message.split("\n")[0]}`);
        } finally {
          await context.close();
        }
      }

      const summary = `PROBE-SUMMARY route="${route.name}" blank=${blank}/${ATTEMPTS} errors=${errors}`;
      console.log([...failures, summary].join("\n"));
      test.info().annotations.push({ type: "probe", description: summary });

      expect(failures, summary).toEqual([]);
    });
  }
});
