/**
 * PROJ-01: Project CRUD — Playwright E2E Tests
 *
 * Tests project creation (with/without customer), editing, tab navigation,
 * archival, and verification that archived projects block task/time creation.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
 *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
 *
 * Run:
 *   cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test projects/project-crud --reporter=list
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import { SEED } from "../../helpers/seed";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";

// --- API helpers ---

async function getToken(user: "alice" | "bob" | "carol"): Promise<string> {
  const users = {
    alice: { userId: "user_e2e_alice", orgRole: "org:owner" },
    bob: { userId: "user_e2e_bob", orgRole: "org:admin" },
    carol: { userId: "user_e2e_carol", orgRole: "org:member" },
  };
  const u = users[user];
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...u, orgId: "org_e2e_test", orgSlug: "e2e-test-org" }),
  });
  const { access_token } = await res.json();
  return access_token;
}

async function apiGet(path: string, token: string) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return res.json();
}

async function apiPost(path: string, body: object, token: string) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
  return { status: res.status, data: await res.json().catch(() => null) };
}

async function apiPatch(path: string, token: string, body?: object) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, data: await res.json().catch(() => null) };
}

// ═══════════════════════════════════════════════════════════════════
//  PROJ-01: Project CRUD
// ═══════════════════════════════════════════════════════════════════

test.describe.serial("PROJ-01: Project CRUD", () => {
  const PROJECT_WITH_CUSTOMER = `Proj Customer ${RUN_ID}`;
  const PROJECT_WITHOUT_CUSTOMER = `Proj Internal ${RUN_ID}`;
  const EDITED_NAME = `Proj Renamed ${RUN_ID}`;
  let projectId: string;
  let taskId: string;

  test("1. Create project with customer", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 10_000 });

    // Click "New Matter" (not "New from Template", which also starts with "New")
    // Phase 87 S5 (ADR-344): label
    await page.getByRole("button", { name: "New Matter" }).click();
    const dialog = page.getByRole("dialog", { name: "Create Matter" });
    await expect(dialog).toBeVisible();

    await dialog.getByLabel("Name", { exact: true }).fill(PROJECT_WITH_CUSTOMER);
    await dialog.getByLabel(/^Description/).fill("E2E test project with customer");
    // The client options load asynchronously (active clients); the seed client is ACTIVE
    const clientSelect = dialog.getByLabel(/^Client/);
    await expect(clientSelect.locator("option", { hasText: SEED.client })).toHaveCount(1, {
      timeout: 10_000,
    });
    await clientSelect.selectOption({ label: SEED.client });

    // Phase 87 S5 (ADR-344): label
    await dialog.getByRole("button", { name: "Create Matter" }).click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });

    // Listed, linked to the client
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    const row = page
      .getByRole("row")
      .filter({ has: page.getByRole("link", { name: PROJECT_WITH_CUSTOMER, exact: true }) });
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row).toContainText(SEED.client);
  });

  test("2. Create project without customer (internal)", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    await expect(page.locator("h1").first()).toBeVisible({ timeout: 10000 });

    // Create via API — more reliable than UI creation for internal projects
    const token = await getToken("alice");
    const { status } = await apiPost(
      // Phase 87 S1 (ADR-337): API path
      "/api/matters",
      {
        name: PROJECT_WITHOUT_CUSTOMER,
        description: "E2E internal project — no customer",
      },
      token
    );
    expect(status).toBe(201);

    // Verify project appears in UI
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    await expect(page.getByText(PROJECT_WITHOUT_CUSTOMER).first()).toBeVisible({ timeout: 10000 });
  });

  test("3. Edit project name", async ({ page }) => {
    const token = await getToken("alice");
    // Phase 87 S1 (ADR-337): API path
    const projects = (await apiGet("/api/matters", token)) as Array<{ id: string; name: string }>;
    const project = projects.find((p) => p.name === PROJECT_WITH_CUSTOMER);
    expect(project, `matter "${PROJECT_WITH_CUSTOMER}" created by test 1`).toBeDefined();

    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters/${project!.id}`);
    await expect(page.getByRole("heading", { level: 1, name: PROJECT_WITH_CUSTOMER })).toBeVisible({
      timeout: 10_000,
    });

    // Editing lives in the matter overflow menu
    await page.getByTestId("overflow-actions-trigger").click();
    await page.getByRole("menuitem", { name: "Edit Matter" }).click();
    const dialog = page.getByRole("dialog", { name: "Edit Matter" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Name", { exact: true }).fill(EDITED_NAME);
    await dialog.getByRole("button", { name: "Save Changes" }).click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });

    await page.reload();
    await expect(page.getByRole("heading", { level: 1, name: EDITED_NAME })).toBeVisible({
      timeout: 10_000,
    });
  });

  test("4. Project detail tabs load", async ({ page }) => {
    const token = await getToken("alice");
    // Phase 87 S1 (ADR-337): API path
    const projects = (await apiGet("/api/matters", token)) as Array<{ id: string; name: string }>;
    const project = projects.find((p) => p.name === EDITED_NAME);
    expect(project, `matter "${EDITED_NAME}" renamed by test 3`).toBeDefined();

    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters/${project!.id}`);
    await expect(page.getByRole("heading", { level: 1, name: EDITED_NAME })).toBeVisible({
      timeout: 10_000,
    });

    // Grouped tab bar: each call opens the sub-tab and asserts its group is selected.
    // Phase 87 S8 (ADR-344): labels — Time → Time recording, Budget → Fee estimate (§12.6.5)
    const panel = page.getByRole("tabpanel");
    await openGroupedTab(page, "work", "tasks");
    await expect(panel.getByRole("button", { name: "New Task" })).toBeVisible();
    await openGroupedTab(page, "finance", "time");
    // The matter has no time yet: the time summary shows its empty state
    await expect(panel.getByRole("heading", { name: "No time tracked yet" })).toBeVisible();
    await openGroupedTab(page, "work", "documents");
    await expect(
      panel.getByRole("heading", { name: "Documents", level: 2, exact: true })
    ).toBeVisible();
    await expect(panel.getByRole("button", { name: /^Drag and drop files here/ })).toBeVisible();
    await openGroupedTab(page, "finance", "budget");
    await expect(panel.getByText("No fee estimate set")).toBeVisible();
    await openGroupedTab(page, "activity", "activity");
    await expect(page).toHaveURL(/[?&]tab=activity/);
  });

  // Phase 87 S8 (ADR-340, ADR-344; OQ #18): the standalone matter expenses page is deleted — its URL (and the
  // pre-610D /projects/:id/expenses URL, via the 610D row) 308s to the matter's Disbursements tab
  // (lib/legacy-redirects.ts). Replaces the retired projects/project-expenses.spec.ts.
  test("4b. Legacy expenses URL redirects to the Disbursements tab", async ({ page }) => {
    const token = await getToken("alice");
    // Phase 87 S1 (ADR-337): API path
    const projects = (await apiGet("/api/matters", token)) as Array<{ id: string; name: string }>;
    const found = projects.find((p) => p.name === EDITED_NAME);
    expect(found, `matter "${EDITED_NAME}" renamed by test 3`).toBeDefined();
    const project = found!;

    await loginAs(page, "alice");
    for (const legacy of [
      `${BASE}/matters/${project.id}/expenses`,
      `${BASE}/projects/${project.id}/expenses`,
    ]) {
      await page.goto(legacy);
      await expect(page).toHaveURL(new RegExp(`/matters/${project.id}\\?tab=disbursements$`));
      // legal-za enables the disbursements module, so the Fees group (id `finance`) is the active group.
      await expect(page.getByTestId("tab-group-finance")).toHaveAttribute("aria-selected", "true", {
        timeout: 10000,
      });
    }
  });

  test("5. Archive project (Complete then Archive)", async ({ page }) => {
    const token = await getToken("alice");
    // Phase 87 S1 (ADR-337): API path
    const projects = (await apiGet("/api/matters?status=ALL", token)) as Array<{
      id: string;
      name: string;
    }>;
    const project = projects.find((p) => p.name === EDITED_NAME);
    expect(project, `matter "${EDITED_NAME}" renamed by test 3`).toBeDefined();
    projectId = project!.id;

    // A task (done before closing) for test 7's time-logging check
    const task = await apiPost(
      // Phase 87 S1 (ADR-337): API path
      `/api/matters/${projectId}/tasks`,
      { title: `Pre-archive Task ${RUN_ID}` },
      token
    );
    expect(task.status).toBe(201);
    taskId = task.data.id;
    // OPEN → IN_PROGRESS → DONE (completing straight from OPEN is refused)
    const started = await fetch(`${BACKEND_URL}/api/tasks/${taskId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: JSON.stringify({ ...task.data, status: "IN_PROGRESS" }),
    });
    expect(started.status).toBe(200);
    expect((await apiPatch(`/api/tasks/${taskId}/complete`, token)).status).toBe(200);

    // Complete the project first (required before archive), then archive
    // Phase 87 S1 (ADR-337): API path
    const completed = await apiPatch(`/api/matters/${projectId}/complete`, token, {
      acknowledgeUnbilledTime: true,
    });
    expect(completed.status).toBe(200);
    // Phase 87 S1 (ADR-337): API path
    const archived = await apiPatch(`/api/matters/${projectId}/archive`, token);
    expect(archived.status).toBe(200);

    // Verify in UI
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters/${projectId}`);
    await expect(page.getByRole("heading", { level: 1, name: EDITED_NAME })).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByTestId("project-status-badge")).toContainText("Archived");
  });

  test("6. Archived project blocks task creation", async () => {
    // Attempt to create a task on the archived project via API
    const token = await getToken("alice");
    const { status } = await apiPost(
      // Phase 87 S1 (ADR-337): API path
      `/api/matters/${projectId}/tasks`,
      {
        title: `Blocked Task ${RUN_ID}`,
      },
      token
    );

    // Should be rejected (400 or 409)
    expect([400, 409]).toContain(status);
  });

  test("7. Archived project blocks time logging via API", async () => {
    // The backend refuses time on a task of an archived matter (the UI may still show buttons)
    const token = await getToken("alice");
    const res = await fetch(`${BACKEND_URL}/api/tasks/${taskId}/time-entries`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        date: new Date().toISOString().split("T")[0],
        durationMinutes: 30,
        billable: true,
        description: `Time Block Test ${RUN_ID}`,
      }),
    });
    expect([400, 409]).toContain(res.status);
    expect(await res.text()).toMatch(/archived/i);
  });
});
