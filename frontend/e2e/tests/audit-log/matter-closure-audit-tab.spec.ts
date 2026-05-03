/**
 * Epic 508B — Matter closure audit-tab demo (canonical Phase 69 demo)
 *
 * Per requirements §6.1 Day 15: when a matter is closed via override path,
 * the matter.closure.override_used audit event (CRITICAL/COMPLIANCE,
 * entityType="matter_closure") emitted by 508A surfaces in the per-row
 * audit timeline embedded inside <ClosureHistorySection> on the project
 * detail page.
 *
 * Setup: seeds a closed matter via direct API call to
 *   POST /api/matters/{id}/closure/close
 * with override=true + a fixed justification string. Asserts the override
 * row badges, expands the per-row audit toggle, then asserts the timeline
 * row carries data-severity="CRITICAL" and the expanded details contain
 * the verbatim justification.
 *
 * Prerequisites:
 *   - E2E stack running: bash compose/scripts/e2e-up.sh
 *   - At least one matter in a state where the trust-balance / disbursement
 *     gates fail (so closure requires override). Day 5 / Day 30 / Day 45
 *     flows of legal-depth-ii produce such a matter.
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e -- audit-log/matter-closure-audit-tab
 */
import { test, expect, request } from "@playwright/test";
import { loginAs, getApiToken } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;
const API_BASE = process.env.API_BASE_URL || "http://localhost:8081";
const JUSTIFICATION = "Client returned funds — trust account zero";

interface MatterSummary {
  id: string;
  name: string;
  status: string;
}

async function findCloseableMatter(token: string): Promise<MatterSummary | null> {
  const ctx = await request.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });

  // Pull a page of projects; pick the first non-CLOSED, non-ARCHIVED matter.
  const res = await ctx.get(`${API_BASE}/api/projects?size=50`);
  if (!res.ok()) {
    await ctx.dispose();
    return null;
  }

  const body = (await res.json()) as { content?: MatterSummary[] } | MatterSummary[];
  const projects = Array.isArray(body) ? body : (body.content ?? []);
  await ctx.dispose();

  const candidate = projects.find(
    (p) => p && p.id && p.status !== "CLOSED" && p.status !== "ARCHIVED"
  );
  return candidate ?? null;
}

async function closeMatterWithOverride(
  token: string,
  projectId: string
): Promise<{ ok: boolean; status: number; closureLogId?: string }> {
  const ctx = await request.newContext({
    extraHTTPHeaders: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
  });

  const res = await ctx.post(`${API_BASE}/api/matters/${projectId}/closure/close`, {
    data: {
      reason: "OTHER",
      generateClosureLetter: false,
      generateStatementOfAccount: false,
      override: true,
      overrideJustification: JUSTIFICATION,
    },
  });
  const status = res.status();
  let closureLogId: string | undefined;
  if (res.ok()) {
    const json = (await res.json()) as { closureLogId?: string };
    closureLogId = json.closureLogId;
  }
  await ctx.dispose();
  return { ok: res.ok(), status, closureLogId };
}

test.describe("Epic 508B — Matter closure override audit surface", () => {
  test("override row surfaces matter.closure.override_used CRITICAL event with justification", async ({
    page,
  }) => {
    // ── Seed: close a matter with override=true via API ────────────────
    const token = await getApiToken("alice");
    const target = await findCloseableMatter(token);
    if (!target) {
      // Not an env-not-up case — we got an authenticated session but no
      // closeable matter exists. That's a seed-data bug; fail loudly so CI
      // surfaces it instead of silently skipping the canonical Phase 69 demo.
      throw new Error(
        "No closeable matter available in e2e seed — this is the canonical " +
          "Phase 69 Day 15 demo and requires a non-CLOSED, non-ARCHIVED matter " +
          "in the seed. Fix the seed-data, do not skip."
      );
    }

    const close = await closeMatterWithOverride(token, target.id);
    if (!close.ok) {
      // Authenticated session succeeded but the close call failed — that's a
      // seed/product bug, not an env-not-up condition. Fail loudly.
      throw new Error(
        `Closure with override failed (status ${close.status}) for matter ${target.id}. ` +
          "This is the canonical Phase 69 Day 15 demo — surface the failure rather than skip."
      );
    }
    if (!close.closureLogId) {
      throw new Error(
        "Closure call succeeded but did not return closureLogId — backend response shape regression."
      );
    }

    // ── Login + navigate to project detail ─────────────────────────────
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects/${target.id}`);
    await page.waitForLoadState("networkidle");

    // ── Assert closure-history section is rendered (only on CLOSED) ────
    const section = page.locator('[data-testid="matter-closure-section"]');
    await expect(section).toBeVisible({ timeout: 10_000 });

    // ── Find the override row and expand its audit toggle ──────────────
    const overrideBadge = section
      .locator('[data-testid^="closure-row-override-"]')
      .first();
    await expect(overrideBadge).toBeVisible();

    // Use the exact closureLogId from the seed to avoid prefix-collision with
    // descendant testids (closure-row-reason-*, closure-row-override-*, etc.).
    const closureRow = section.locator(
      `[data-testid="closure-row-${close.closureLogId}"]`
    );
    const toggle = closureRow.locator(
      `[data-testid="closure-audit-toggle-${close.closureLogId}"]`
    );
    await toggle.click();

    // ── Assert the audit timeline contains a CRITICAL row ──────────────
    const criticalRow = page
      .locator('[data-testid="audit-timeline-row"]')
      .filter({
        has: page.locator('[data-testid="severity-pill"][data-severity="CRITICAL"]'),
      })
      .first();
    await expect(criticalRow).toBeVisible({ timeout: 10_000 });

    // ── Expand the row and assert justification is visible ─────────────
    await criticalRow.click();
    const expanded = page.locator('[data-testid="audit-timeline-row-expanded"]').first();
    await expect(expanded).toBeVisible();
    await expect(expanded).toContainText(JUSTIFICATION);
  });
});
