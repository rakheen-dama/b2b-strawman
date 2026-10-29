/**
 * CUST-02: Customer Lifecycle — Playwright E2E Tests
 *
 * Lifecycle state machine: PROSPECT default, the firm-UI transitions that exist (header "Start
 * Onboarding", Compliance › dormancy check "Mark as Dormant"), checklist auto-activation and the
 * lifecycle guards. DORMANT → OFFBOARDING and OFFBOARDING → OFFBOARDED have no working firm UI
 * (628F-BLOCKER fixmes below).
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
 *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
 *
 * Run:
 *   cd frontend/e2e
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test customers/customer-lifecycle --reporter=list
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import {
  api,
  clientStatus,
  createActiveClient,
  createClient,
  settleChecklists,
} from "../../helpers/api";
import { startClientOnboarding } from "../../helpers/legal-depth-ii";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

const RUN_ID = Date.now().toString(36).slice(-4);

function transitionViaApi(clientId: string, targetStatus: string): Promise<Response> {
  // Phase 87 S2 (ADR-337): API path
  return api(`/api/clients/${clientId}/transition`, {
    method: "POST",
    body: { targetStatus, notes: "E2E test transition" },
  });
}

// ═══════════════════════════════════════════════════════════════════
//  CUST-02: Customer Lifecycle State Machine
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("CUST-02: Customer Lifecycle", () => {
  const LIFECYCLE_NAME = `LC Cust ${RUN_ID}`;
  const LIFECYCLE_EMAIL = `lc-${RUN_ID}@example.com`;
  let customerId: string;

  test("New customer defaults to PROSPECT", async ({ page }) => {
    const customer = await createClient(LIFECYCLE_NAME, LIFECYCLE_EMAIL);
    customerId = customer.id;
    expect(customer.lifecycleStatus).toBe("PROSPECT");

    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${customerId}`);
    await expect(
      page.getByRole("heading", { level: 1, name: LIFECYCLE_NAME, exact: true })
    ).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Prospect");
  });

  test("PROSPECT -> ONBOARDING", async ({ page }) => {
    await loginAs(page, "bob");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${customerId}`);
    await expect(
      page.getByRole("heading", { level: 1, name: LIFECYCLE_NAME, exact: true })
    ).toBeVisible({
      timeout: 10_000,
    });

    // The header smart action of a PROSPECT is "Start Onboarding" (confirm dialog → ONBOARDING)
    await expect(page.getByTestId("smart-primary-action")).toHaveText("Start Onboarding");
    await startClientOnboarding(page);

    await page.reload();
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Onboarding", { timeout: 10_000 });
    expect(await clientStatus(customerId)).toBe("ONBOARDING");
  });

  test("ONBOARDING -> ACTIVE (via checklist completion)", async () => {
    expect(await clientStatus(customerId)).toBe("ONBOARDING");
    await settleChecklists(customerId);
    // Settling the last item auto-transitions the client (its billing address is on file)
    await expect.poll(() => clientStatus(customerId)).toBe("ACTIVE");
  });

  test("PROSPECT blocked from creating project", async () => {
    const prospect = await createClient(
      `Prospect Guard ${RUN_ID}`,
      `prospect-guard-${RUN_ID}@example.com`
    );

    // Phase 87 S1 (ADR-337): API path
    const projectRes = await api("/api/matters", {
      method: "POST",
      body: {
        name: `Should Fail ${RUN_ID}`,
        description: "This should be blocked by lifecycle guard",
        // Phase 87 S1 (ADR-337): API field (CreateMatterRequest.clientId)
        clientId: prospect.id,
      },
    });
    // CustomerLifecycleGuard: CREATE_PROJECT is blocked for PROSPECT (InvalidStateException → 400)
    expect(projectRes.status).toBe(400);
    expect(await projectRes.text()).toContain("PROSPECT");
  });

  test("Fee note blocked for a client without a billing address", async () => {
    // A PROSPECT may be billed (GAP-L-60); what blocks the fee note is the missing billing address
    // (INVOICE_GENERATION structural prerequisites → 422 with an address_line1 violation)
    const prospect = await createClient(
      `ProspectInv ${RUN_ID}`,
      `prospect-inv-${RUN_ID}@example.com`,
      { withAddress: false }
    );

    const invoiceRes = await api("/api/invoices", {
      method: "POST",
      body: {
        // Phase 87 S3 (ADR-337): request body
        clientId: prospect.id,
        currency: "ZAR",
        timeEntryIds: [],
      },
    });
    expect(invoiceRes.status).toBe(422);
    expect(await invoiceRes.text()).toContain("address_line1");
  });

  test("ACTIVE -> DORMANT", async ({ page }) => {
    expect(await clientStatus(customerId)).toBe("ACTIVE");

    // The firm-UI ACTIVE → DORMANT path: Compliance › dormancy check › "Mark as Dormant". The client
    // has no matters, documents, fee notes or time, so it is always a dormancy candidate.
    await loginAs(page, "alice");
    await page.goto(`${BASE}/compliance`);
    await page.getByRole("button", { name: "Check for Dormant Clients" }).click();
    const row = page
      .getByRole("row")
      .filter({ has: page.getByRole("link", { name: LIFECYCLE_NAME, exact: true }) });
    await expect(row).toBeVisible({ timeout: 15_000 });
    const markDormant = row.getByRole("button", { name: "Mark as Dormant" });
    await markDormant.click();
    // The candidate list is client state and is not re-fetched after marking: assert on the client
    await expect.poll(() => clientStatus(customerId), { timeout: 15_000 }).toBe("DORMANT");

    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${customerId}`);
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Dormant", { timeout: 10_000 });
  });

  test("DORMANT -> OFFBOARDING", async ({ page }) => {
    test.fixme(
      true,
      "628F-BLOCKER: no firm-UI DORMANT→OFFBOARDING transition (header action 'Edit' has no handler, overflow menu has no lifecycle items; LifecycleTransitionDropdown orphaned since #1395) — components/customers/client-header-card-with-lifecycle.tsx:70, app/(app)/org/[slug]/clients/[id]/page.tsx:434-470, components/customers/client-overflow-menu.tsx:101-177"
    );
    // Intended real steps (run once the product exposes the transition):
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${customerId}`);
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Dormant", { timeout: 10_000 });
    await page.getByTestId("client-overflow-trigger").click();
    // Phase 88 628A (ADR-357): label → "Offboard Client"
    await page.getByRole("menuitem", { name: "Offboard Client" }).click();
    const confirm = page.getByRole("alertdialog");
    await confirm.getByRole("button", { name: "Begin Offboarding" }).click();
    await expect(confirm).toBeHidden({ timeout: 15_000 });
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Offboarding", {
      timeout: 15_000,
    });
  });

  test("OFFBOARDING -> OFFBOARDED", async ({ page }) => {
    test.fixme(
      true,
      "628F-BLOCKER: header 'Complete Offboarding' button has no onClick (no target status computed for OFFBOARDING) — components/customers/client-header-card-with-lifecycle.tsx:70, app/(app)/org/[slug]/clients/[id]/page.tsx:434-470"
    );
    // Intended real steps (run once the product wires the header action):
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${customerId}`);
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Offboarding", {
      timeout: 10_000,
    });
    await page.getByTestId("smart-primary-action").click();
    const confirm = page.getByRole("alertdialog", { name: "Complete Offboarding" });
    await confirm.getByRole("button", { name: "Complete Offboarding" }).click();
    await expect(confirm).toBeHidden({ timeout: 15_000 });
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Offboarded", {
      timeout: 15_000,
    });
  });

  test("OFFBOARDED blocked from project creation", async () => {
    // Self-contained (the UI path to OFFBOARDED is blocked above): ACTIVE → OFFBOARDING → OFFBOARDED
    const offboardedId = await createActiveClient(
      `Offboarded Guard ${RUN_ID}`,
      `offboarded-guard-${RUN_ID}@example.com`
    );
    expect((await transitionViaApi(offboardedId, "OFFBOARDING")).ok).toBe(true);
    expect((await transitionViaApi(offboardedId, "OFFBOARDED")).ok).toBe(true);
    expect(await clientStatus(offboardedId)).toBe("OFFBOARDED");

    // Phase 87 S1 (ADR-337): API path
    const projectRes = await api("/api/matters", {
      method: "POST",
      body: {
        name: `Should Fail Offboarded ${RUN_ID}`,
        description: "Blocked by lifecycle guard",
        // Phase 87 S1 (ADR-337): API field (CreateMatterRequest.clientId)
        clientId: offboardedId,
      },
    });
    expect(projectRes.status).toBe(400);
    expect(await projectRes.text()).toContain("OFFBOARDED");
  });

  test("Invalid: PROSPECT -> ACTIVE (skip) rejected via API", async () => {
    const prospect = await createClient(`SkipTest ${RUN_ID}`, `skip-${RUN_ID}@example.com`);

    // PROSPECT may only move to ONBOARDING (LifecycleStatus.ALLOWED_TRANSITIONS)
    const res = await transitionViaApi(prospect.id, "ACTIVE");
    expect(res.status).toBe(400);
    expect(await clientStatus(prospect.id)).toBe("PROSPECT");
  });
});
