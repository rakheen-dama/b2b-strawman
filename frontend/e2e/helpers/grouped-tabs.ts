/**
 * Grouped tab bars (matter and client detail pages).
 *
 * Only the group triggers are `role="tab"` (`data-testid="tab-group-<group>"`); the sub-tabs are
 * menu items (`tab-item-<tab>`) and the tab panel has no accessible name, so
 * `getByRole("tab", { name: "Tasks" })` / `getByRole("tabpanel", { name: "Tasks" })` match nothing.
 * Group ids: `frontend/lib/constants/matter-tab-groups.ts`, `client-tab-groups.ts`.
 *
 * Lifted verbatim from `legal-depth-ii.ts` (Phase 88 628E), which re-exports it.
 */
import { expect, type Page } from "@playwright/test";

/**
 * Opens a sub-tab of a grouped tab bar (matter or client detail, e.g. Finance → Statements).
 * Clicking an inactive group activates its first sub-tab; the sub-tab menu is
 * opened by clicking the (now active) group trigger.
 */
export async function openGroupedTab(page: Page, groupId: string, tabId: string): Promise<void> {
  // `visible: true`: for a moment after load the page can hold a second, hidden (display:none)
  // copy of the tab bar — a test-id locator (unlike getByRole) would then match both (628F)
  const group = page.getByTestId(`tab-group-${groupId}`).filter({ visible: true });
  const item = page.getByTestId(`tab-item-${tabId}`).filter({ visible: true });
  await expect(group).toBeVisible({ timeout: 15_000 });
  // The group trigger toggles its sub-tab menu; whether the first click opens the
  // menu depends on whether the group was already active, so click until it is open.
  for (let attempt = 0; attempt < 3; attempt++) {
    await group.click();
    const menuOpen = await item.waitFor({ state: "visible", timeout: 2_000 }).then(
      () => true,
      () => false
    );
    if (menuOpen) {
      await item.click();
      await expect(group).toHaveAttribute("aria-selected", "true");
      return;
    }
  }
  throw new Error(`Sub-tab menu for tab-group-${groupId} never opened (looking for ${tabId})`);
}
