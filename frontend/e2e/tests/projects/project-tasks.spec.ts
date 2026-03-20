/**
 * PROJ-02: Project Tasks — Playwright E2E Tests
 *
 * Tests task CRUD, status transitions (OPEN -> IN_PROGRESS -> DONE),
 * reopen, cancel, and member assignment. Uses a mix of API setup
 * and UI verification.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/lifecycle-test.sh
 *
 * Run:
 *   cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test projects/project-tasks --reporter=list
 */
import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/auth'

const ORG = 'e2e-test-org'
const BASE = `/org/${ORG}`
const RUN_ID = Date.now().toString(36).slice(-4)

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8081'
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || 'http://localhost:8090'

// --- API helpers ---

async function getToken(user: 'alice' | 'bob' | 'carol'): Promise<string> {
  const users = {
    alice: { userId: 'user_e2e_alice', orgRole: 'org:owner' },
    bob: { userId: 'user_e2e_bob', orgRole: 'org:admin' },
    carol: { userId: 'user_e2e_carol', orgRole: 'org:member' },
  }
  const u = users[user]
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...u, orgId: 'org_e2e_test', orgSlug: 'e2e-test-org' }),
  })
  const { access_token } = await res.json()
  return access_token
}

async function apiGet(path: string, token: string) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.json()
}

async function apiPost(path: string, body: object, token: string) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  return { status: res.status, data: await res.json().catch(() => null) }
}

async function apiPut(path: string, body: object, token: string) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  return { status: res.status, data: await res.json().catch(() => null) }
}

async function apiPatch(path: string, token: string, body?: object) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { status: res.status, data: await res.json().catch(() => null) }
}

// ═══════════════════════════════════════════════════════════════════
//  PROJ-02: Project Tasks
// ═══════════════════════════════════════════════════════════════════

test.describe.serial('PROJ-02: Project Tasks', () => {
  const PROJECT_NAME = `Task Test Project ${RUN_ID}`
  const TASK_TITLE = `Test Task ${RUN_ID}`
  const EDITED_TITLE = `Edited Task ${RUN_ID}`
  let projectId: string
  let taskId: string

  // Setup: create a project via API for task testing
  test.beforeAll(async () => {
    const token = await getToken('alice')
    const { data } = await apiPost('/api/projects', {
      name: PROJECT_NAME,
      description: 'Project for task E2E tests',
    }, token)
    projectId = data.id

    // Add Carol as a project member so she can be assigned tasks
    const members = await apiGet('/api/members', token) as Array<{ id: string; name: string }>
    const carol = members.find((m) => m.name?.includes('Carol'))
    if (carol) {
      await apiPost(`/api/projects/${projectId}/members`, {
        memberId: carol.id,
        projectRole: 'contributor',
      }, token)
    }
  })

  test('1. Create task on project via UI', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects/${projectId}`)
    await expect(page.getByRole('heading', { name: PROJECT_NAME, level: 1 })).toBeVisible({ timeout: 10000 })

    // Click Tasks tab
    await page.getByRole('tab', { name: 'Tasks' }).click()
    await expect(page.getByRole('tabpanel', { name: 'Tasks' })).toBeVisible({ timeout: 5000 })

    // Click "New Task"
    await page.getByRole('button', { name: 'New Task' }).click()
    await expect(page.getByRole('dialog', { name: 'Create Task' })).toBeVisible({ timeout: 5000 })

    // Fill title
    await page.getByRole('textbox', { name: 'Title' }).fill(TASK_TITLE)

    // Submit
    await page.getByRole('button', { name: 'Create Task' }).click()
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 10000 })

    // Reload and verify task appears
    await page.waitForTimeout(1000)
    await page.reload()
    await page.getByRole('tab', { name: 'Tasks' }).click()
    await expect(page.getByText(TASK_TITLE).first()).toBeVisible({ timeout: 10000 })

    // Get the task ID via API for subsequent tests
    const token = await getToken('alice')
    const tasks = await apiGet(`/api/projects/${projectId}/tasks`, token) as Array<{ id: string; title: string }>
    const task = tasks.find((t) => t.title === TASK_TITLE)
    expect(task).toBeTruthy()
    taskId = task!.id
  })

  test('2. Edit task title via API and verify in UI', async ({ page }) => {
    const token = await getToken('alice')

    // Get current task to preserve fields for the PUT request
    const task = await apiGet(`/api/tasks/${taskId}`, token) as {
      title: string
      description: string
      priority: string
      status: string
      type: string | null
    }

    // Update title
    await apiPut(`/api/tasks/${taskId}`, {
      title: EDITED_TITLE,
      description: task.description || '',
      priority: task.priority || 'MEDIUM',
      status: task.status || 'OPEN',
      type: task.type,
    }, token)

    // Verify in UI
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects/${projectId}`)
    await page.getByRole('tab', { name: 'Tasks' }).click()
    await expect(page.getByText(EDITED_TITLE).first()).toBeVisible({ timeout: 10000 })
  })

  test('3. Change task status: OPEN -> IN_PROGRESS', async ({ page }) => {
    const token = await getToken('alice')
    const task = await apiGet(`/api/tasks/${taskId}`, token) as {
      title: string
      description: string
      priority: string
      status: string
      type: string | null
    }

    const { status, data } = await apiPut(`/api/tasks/${taskId}`, {
      ...task,
      status: 'IN_PROGRESS',
    }, token)

    expect(status).toBe(200)
    expect(data.status).toBe('IN_PROGRESS')

    // Verify in UI
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects/${projectId}`)
    await page.getByRole('tab', { name: 'Tasks' }).click()
    await expect(page.getByText(/In Progress/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('4. Change task status: IN_PROGRESS -> DONE', async ({ page }) => {
    const token = await getToken('alice')

    // Use the PATCH complete endpoint
    const { status, data } = await apiPatch(`/api/tasks/${taskId}/complete`, token)

    expect(status).toBe(200)
    expect(data.status).toBe('DONE')

    // Verify in UI — completed tasks may be filtered out by default, use status filter
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects/${projectId}`)
    await page.getByRole('tab', { name: 'Tasks' }).click()

    // The task list may filter DONE tasks — check if there's a way to show all
    // or verify via API that the status is DONE
    const updatedTask = await apiGet(`/api/tasks/${taskId}`, token) as { status: string }
    expect(updatedTask.status).toBe('DONE')
  })

  test('5. Reopen completed task', async ({ page }) => {
    const token = await getToken('alice')

    const { status, data } = await apiPatch(`/api/tasks/${taskId}/reopen`, token)

    expect(status).toBe(200)
    expect(data.status).toBe('OPEN')

    // Verify in UI
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects/${projectId}`)
    await page.getByRole('tab', { name: 'Tasks' }).click()
    await expect(page.getByText(EDITED_TITLE).first()).toBeVisible({ timeout: 10000 })
  })

  test('6. Cancel task', async ({ page }) => {
    const token = await getToken('alice')

    const { status, data } = await apiPatch(`/api/tasks/${taskId}/cancel`, token)

    expect(status).toBe(200)
    expect(data.status).toBe('CANCELLED')

    // Verify via API
    const updatedTask = await apiGet(`/api/tasks/${taskId}`, token) as { status: string }
    expect(updatedTask.status).toBe('CANCELLED')
  })

  test('7. Assign member to task', async ({ page }) => {
    const token = await getToken('alice')

    // Create a fresh task to assign (cancelled task cannot be edited)
    const { data: newTask } = await apiPost(`/api/projects/${projectId}/tasks`, {
      title: `Assignable Task ${RUN_ID}`,
    }, token)
    const newTaskId = newTask.id

    // Get Carol's member ID
    const members = await apiGet('/api/members', token) as Array<{ id: string; name: string }>
    const carol = members.find((m) => m.name?.includes('Carol'))
    expect(carol).toBeTruthy()

    // Assign Carol to the task
    const task = await apiGet(`/api/tasks/${newTaskId}`, token) as {
      title: string
      description: string
      priority: string
      status: string
      type: string | null
    }

    const { status, data: updatedTask } = await apiPut(`/api/tasks/${newTaskId}`, {
      ...task,
      assigneeId: carol!.id,
    }, token)

    expect(status).toBe(200)
    expect(updatedTask.assigneeId).toBe(carol!.id)

    // Verify in UI — Carol's name should appear on the task
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects/${projectId}`)
    await page.getByRole('tab', { name: 'Tasks' }).click()
    await expect(page.getByText(/Carol/).first()).toBeVisible({ timeout: 10000 })
  })
})
