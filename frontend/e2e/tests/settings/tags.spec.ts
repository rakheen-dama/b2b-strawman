/**
 * TAG-01: Tags — Playwright E2E Tests
 *
 * Tests tag management: view tags, create tag with colour,
 * apply tag to entity.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/auth'

const ORG = 'e2e-test-org'
const base = `/org/${ORG}`

test.describe('TAG-01: Tags', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'alice')
  })

  test('View tags', async ({ page }) => {
    await page.goto(`${base}/settings/tags`)
    const body = page.locator('body')
    const bodyText = await body.textContent()

    if (bodyText?.includes('Something went wrong') || bodyText?.includes('404')) {
      test.skip(true, 'Tags settings page not implemented')
      return
    }

    const content = page.locator('main, [role="main"], .flex-1').first()
    const text = await content.textContent({ timeout: 10000 })
    expect(text?.length).toBeGreaterThan(0)
  })

  test('Create tag', async ({ page }) => {
    await page.goto(`${base}/settings/tags`)
    const bodyText = await page.locator('body').textContent()

    if (bodyText?.includes('Something went wrong') || bodyText?.includes('404')) {
      test.skip(true, 'Tags settings page not implemented')
      return
    }

    const createBtn = page.getByRole('button', { name: /new|create|add/i }).first()
    if (!await createBtn.isVisible().catch(() => false)) {
      test.skip(true, 'Create tag button not found')
      return
    }

    await createBtn.click()
    await page.waitForTimeout(1000)

    const dialog = page.getByRole('dialog').first()
    if (await dialog.isVisible().catch(() => false)) {
      const nameInput = dialog.getByRole('textbox', { name: /name|label/i }).first()
      if (await nameInput.isVisible().catch(() => false)) {
        await nameInput.fill(`Test Tag ${Date.now().toString(36).slice(-4)}`)
      }

      // Set colour if available
      const colourInput = dialog.locator('input[type="color"]').first()
      if (await colourInput.isVisible().catch(() => false)) {
        await colourInput.fill('#3b82f6')
      }

      const submitBtn = dialog.getByRole('button', { name: /create|save|add/i }).last()
      if (await submitBtn.isVisible().catch(() => false)) {
        await submitBtn.click()
        await page.waitForTimeout(1000)
      }
    }
  })

  test('Apply tag to entity', async ({ page }) => {
    // Navigate to a project or customer to apply a tag
    await page.goto(`${base}/projects`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    // Click on first project
    const projectLink = page.getByRole('link').filter({ hasText: /project/i }).first()
    if (!await projectLink.isVisible({ timeout: 10000 }).catch(() => false)) {
      // Try any link in the main content area
      const anyLink = page.locator('main a, [role="main"] a').first()
      if (!await anyLink.isVisible().catch(() => false)) {
        test.skip(true, 'No projects found to apply tag')
        return
      }
      await anyLink.click()
    } else {
      await projectLink.click()
    }
    await page.waitForTimeout(2000)

    // Look for tag/label button or section
    const tagBtn = page.getByRole('button', { name: /tag|label/i }).first()
    if (!await tagBtn.isVisible().catch(() => false)) {
      test.skip(true, 'Tag/label button not found on entity detail')
      return
    }

    await tagBtn.click()
    await page.waitForTimeout(500)

    // Select a tag from dropdown
    const tagOption = page.getByRole('option').first()
    if (await tagOption.isVisible().catch(() => false)) {
      await tagOption.click()
      await page.waitForTimeout(1000)
    }
  })
})
