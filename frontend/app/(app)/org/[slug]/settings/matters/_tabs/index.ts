import type { SettingsTabLoaders } from "@/components/settings/settings-family-page";
import { renderTemplatesTab } from "./templates";
import { renderNamingTab } from "./naming";
import { renderCustomFieldsTab } from "./custom-fields";
import { renderTagsTab } from "./tags";
import { renderChecklistsTab } from "./checklists";
import { renderRequestTemplatesTab } from "./request-templates";
import { renderRequestSettingsTab } from "./request-settings";

/**
 * Matters tab loaders, one per Matters tab id in lib/settings-nav.ts (typed — a missing or extra id
 * is a compile error). Each is the loader of a former settings page.
 *
 * Phase 84 take-on (architecture §12.14 #3, ADR-341): the firm take-on settings land here as a
 * `take-on` tab (`/settings/matters?tab=take-on`) — declare it in SETTINGS_FAMILIES and add its
 * loader to this map together; the route count stays 17. Not built in Phase 87.
 */
export const MATTERS_TAB_LOADERS: SettingsTabLoaders<"matters"> = {
  templates: renderTemplatesTab,
  naming: renderNamingTab,
  "custom-fields": renderCustomFieldsTab,
  tags: renderTagsTab,
  checklists: renderChecklistsTab,
  "request-templates": renderRequestTemplatesTab,
  "request-settings": renderRequestSettingsTab,
};
