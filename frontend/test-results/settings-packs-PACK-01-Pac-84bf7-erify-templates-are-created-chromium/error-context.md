# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: settings/packs.spec.ts >> PACK-01: Pack Lifecycle >> Install pack and verify templates are created
- Location: e2e/tests/settings/packs.spec.ts:88:7

# Error details

```
Error: expect(locator).toBeEnabled() failed

Locator:  locator('[data-testid="pack-card-legal-za"]').getByRole('button', { name: 'Install' })
Expected: enabled
Received: disabled
Timeout:  5000ms

Call log:
  - Expect "toBeEnabled" with timeout 5000ms
  - waiting for locator('[data-testid="pack-card-legal-za"]').getByRole('button', { name: 'Install' })
    9 × locator resolved to <button disabled data-size="sm" data-slot="button" data-variant="outline" class="inline-flex items-center justify-center whitespace-nowrap text-sm font-medium transition-all active:scale-[0.97] disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg:not([class*='size-'])]:size-4 shrink-0 [&_svg]:shrink-0 outline-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-inval…>…</button>
      - unexpected value "disabled"

```

# Test source

```ts
  21  | 
  22  | /** Pack we will install -- not auto-installed because profile is accounting-za */
  23  | const TARGET_PACK_ID = "legal-za";
  24  | const TARGET_PACK_NAME = "SA Legal Templates";
  25  | /** A well-known template from the legal-za pack to verify installation */
  26  | const LEGAL_TEMPLATE_NAME = "Power of Attorney";
  27  | 
  28  | /**
  29  |  * Find a template by name via the backend API and return its detail (id + content).
  30  |  */
  31  | async function findTemplateByName(
  32  |   token: string,
  33  |   name: string
  34  | ): Promise<{
  35  |   id: string;
  36  |   name: string;
  37  |   content: Record<string, unknown>;
  38  |   css: string | null;
  39  | } | null> {
  40  |   const listRes = await fetch(`${BACKEND_URL}/api/templates`, {
  41  |     headers: { Authorization: `Bearer ${token}` },
  42  |   });
  43  |   if (!listRes.ok) return null;
  44  |   const templates: Array<{ id: string; name: string }> = await listRes.json();
  45  |   const match = templates.find((t) => t.name === name);
  46  |   if (!match) return null;
  47  | 
  48  |   const detailRes = await fetch(`${BACKEND_URL}/api/templates/${match.id}`, {
  49  |     headers: { Authorization: `Bearer ${token}` },
  50  |   });
  51  |   if (!detailRes.ok) return null;
  52  |   return detailRes.json();
  53  | }
  54  | 
  55  | /**
  56  |  * Uninstall the target pack via API (best-effort cleanup for test reruns).
  57  |  */
  58  | async function uninstallPackViaApi(token: string): Promise<void> {
  59  |   await fetch(`${BACKEND_URL}/api/packs/${TARGET_PACK_ID}/uninstall`, {
  60  |     method: "POST",
  61  |     headers: { Authorization: `Bearer ${token}` },
  62  |   });
  63  |   // Ignore errors — pack may not be installed
  64  | }
  65  | 
  66  | /**
  67  |  * Reset a template to its pack original via API (best-effort cleanup).
  68  |  */
  69  | async function resetTemplateViaApi(token: string, name: string): Promise<void> {
  70  |   const template = await findTemplateByName(token, name);
  71  |   if (!template) return;
  72  |   await fetch(`${BACKEND_URL}/api/templates/${template.id}/reset`, {
  73  |     method: "POST",
  74  |     headers: { Authorization: `Bearer ${token}` },
  75  |   });
  76  | }
  77  | 
  78  | test.describe.serial("PACK-01: Pack Lifecycle", () => {
  79  |   // Cleanup: ensure the pack is uninstalled before/after the suite so reruns start clean
  80  |   test.afterAll(async () => {
  81  |     const token = await getApiToken("alice");
  82  |     // Reset any edited templates first (so uninstall is not blocked)
  83  |     await resetTemplateViaApi(token, LEGAL_TEMPLATE_NAME);
  84  |     await uninstallPackViaApi(token);
  85  |   });
  86  | 
  87  |   // ---- Test 1: Install pack and verify content (479.2) ----
  88  |   test("Install pack and verify templates are created", async ({ page }) => {
  89  |     // Pre-test cleanup: ensure pack starts uninstalled
  90  |     const setupToken = await getApiToken("alice");
  91  |     await resetTemplateViaApi(setupToken, LEGAL_TEMPLATE_NAME);
  92  |     await uninstallPackViaApi(setupToken);
  93  | 
  94  |     await loginAs(page, "alice");
  95  |     await page.goto(`${BASE}/settings/packs`);
  96  | 
  97  |     // Verify packs page loads
  98  |     const heading = page.locator("h1").filter({ hasText: "Packs" });
  99  |     await expect(heading).toBeVisible({ timeout: 10000 });
  100 |     await expect(page.locator("body")).not.toContainText("Something went wrong");
  101 | 
  102 |     // Available tab should be active by default
  103 |     await expect(page.getByRole("tab", { name: "Available" })).toBeVisible({ timeout: 5000 });
  104 | 
  105 |     // All profile-matched packs are auto-installed; toggle "Show all packs" to see legal-za
  106 |     const showAllSwitch = page.locator("#show-all-packs");
  107 |     await expect(showAllSwitch).toBeVisible({ timeout: 5000 });
  108 |     await showAllSwitch.click();
  109 | 
  110 |     // Find the legal-za pack card
  111 |     const packCard = page.locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`);
  112 |     await expect(packCard).toBeVisible({ timeout: 10000 });
  113 | 
  114 |     // Verify pack metadata
  115 |     await expect(packCard.locator("h3")).toContainText(TARGET_PACK_NAME);
  116 |     await expect(packCard.getByText(/\d+ templates?/)).toBeVisible({ timeout: 5000 });
  117 | 
  118 |     // Click Install button on the legal-za card
  119 |     const installButton = packCard.getByRole("button", { name: "Install" });
  120 |     await expect(installButton).toBeVisible({ timeout: 5000 });
> 121 |     await expect(installButton).toBeEnabled();
      |                                 ^ Error: expect(locator).toBeEnabled() failed
  122 |     await installButton.click();
  123 | 
  124 |     // Wait for install to complete -- card should show "Installed" state
  125 |     await expect(packCard.getByRole("button", { name: "Installed" })).toBeVisible({
  126 |       timeout: 15000,
  127 |     });
  128 | 
  129 |     // Verify toast
  130 |     await expect(page.getByText("Pack installed successfully.")).toBeVisible({ timeout: 5000 });
  131 | 
  132 |     // Navigate to Templates page to verify templates were created
  133 |     await page.goto(`${BASE}/settings/templates`);
  134 |     await expect(page.locator("h1").filter({ hasText: "Templates" })).toBeVisible({
  135 |       timeout: 10000,
  136 |     });
  137 |     await expect(page.locator("body")).not.toContainText("Something went wrong");
  138 | 
  139 |     // Verify at least one legal-za template name is visible
  140 |     await expect(page.getByText(LEGAL_TEMPLATE_NAME)).toBeVisible({ timeout: 10000 });
  141 | 
  142 |     // Navigate back to Packs > Installed tab
  143 |     await page.goto(`${BASE}/settings/packs`);
  144 |     const installedTab = page.getByRole("tab", { name: /Installed/i });
  145 |     await expect(installedTab).toBeVisible({ timeout: 5000 });
  146 |     await installedTab.click();
  147 | 
  148 |     // Verify legal-za pack appears in the installed list
  149 |     const installedCard = page.locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`);
  150 |     await expect(installedCard).toBeVisible({ timeout: 10000 });
  151 |     await expect(installedCard.locator("h3")).toContainText(TARGET_PACK_NAME);
  152 |   });
  153 | 
  154 |   // ---- Test 2: Edit blocks uninstall, revert allows it (479.3) ----
  155 |   test("Uninstall blocked when template edited, succeeds after revert", async ({ page }) => {
  156 |     await loginAs(page, "alice");
  157 | 
  158 |     // Step 1: Navigate to Installed tab and verify legal-za is there
  159 |     await page.goto(`${BASE}/settings/packs`);
  160 |     const installedTab = page.getByRole("tab", { name: /Installed/i });
  161 |     await installedTab.click();
  162 | 
  163 |     const installedCard = page.locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`);
  164 |     await expect(installedCard).toBeVisible({ timeout: 10000 });
  165 | 
  166 |     // Step 2: Edit a pack template via the backend API to trigger hash mismatch
  167 |     const token = await getApiToken("alice");
  168 |     const template = await findTemplateByName(token, LEGAL_TEMPLATE_NAME);
  169 |     expect(template).toBeTruthy();
  170 |     const templateId = template!.id;
  171 | 
  172 |     // Modify the template content to change its hash
  173 |     const modifiedContent = {
  174 |       ...template!.content,
  175 |       _e2e_modified: true,
  176 |     };
  177 |     const updateRes = await fetch(`${BACKEND_URL}/api/templates/${templateId}`, {
  178 |       method: "PUT",
  179 |       headers: {
  180 |         "Content-Type": "application/json",
  181 |         Authorization: `Bearer ${token}`,
  182 |       },
  183 |       body: JSON.stringify({
  184 |         name: template!.name,
  185 |         content: modifiedContent,
  186 |         css: template!.css ?? undefined,
  187 |       }),
  188 |     });
  189 |     expect(updateRes.ok).toBeTruthy();
  190 | 
  191 |     // Step 3: Reload the packs page and go to Installed tab to see the blocked state
  192 |     await page.goto(`${BASE}/settings/packs`);
  193 |     await page.getByRole("tab", { name: /Installed/i }).click();
  194 | 
  195 |     // Wait for the uninstall check to load (button starts disabled, stays disabled if blocked)
  196 |     const uninstallBtn = installedCard.getByRole("button", { name: "Uninstall" });
  197 |     await expect(uninstallBtn).toBeVisible({ timeout: 10000 });
  198 | 
  199 |     // Verify the button is disabled (blocked because template was edited)
  200 |     await expect(uninstallBtn).toBeDisabled({ timeout: 10000 });
  201 | 
  202 |     // Hover to see tooltip with blocking reason
  203 |     // The button is wrapped in a <span> with TooltipTrigger
  204 |     const tooltipTrigger = installedCard.locator("span").filter({ has: uninstallBtn });
  205 |     await tooltipTrigger.hover();
  206 | 
  207 |     // Verify tooltip with blocking message appears
  208 |     const tooltipContent = page.locator("[data-radix-popper-content-wrapper]");
  209 |     await expect(tooltipContent).toBeVisible({ timeout: 3000 });
  210 |     await expect(tooltipContent).toContainText(/edited|modified/i);
  211 | 
  212 |     // Step 4: Revert the template via the reset API endpoint
  213 |     const resetRes = await fetch(`${BACKEND_URL}/api/templates/${templateId}/reset`, {
  214 |       method: "POST",
  215 |       headers: { Authorization: `Bearer ${token}` },
  216 |     });
  217 |     expect(resetRes.ok).toBeTruthy();
  218 | 
  219 |     // Step 5: Reload and verify uninstall is now allowed
  220 |     await page.goto(`${BASE}/settings/packs`);
  221 |     await page.getByRole("tab", { name: /Installed/i }).click();
```