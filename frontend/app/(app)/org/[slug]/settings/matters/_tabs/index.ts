import type { SettingsTabLoaders } from "@/components/settings/settings-family-page";
import { renderTemplatesTab } from "./templates";
import { renderNamingTab } from "./naming";
import { renderCustomFieldsTab } from "./custom-fields";
import { renderTagsTab } from "./tags";
import { renderChecklistsTab } from "./checklists";
import { renderRequestTemplatesTab } from "./request-templates";
import { renderRequestSettingsTab } from "./request-settings";
import { renderTakeOnTab } from "./take-on-tab";

/**
 * Matters tab loaders, one per Matters tab id in lib/settings-nav.ts (typed — a missing or extra id
 * is a compile error). Each is the loader of a former settings page.
 *
 * Take-on (Phase 93 653A, architecture §17.8.2): the wizard renders inside this tab
 * (`?tab=take-on&batch={id}`) so the settings route count stays 17 (ADR-341).
 */
export const MATTERS_TAB_LOADERS: SettingsTabLoaders<"matters"> = {
  templates: renderTemplatesTab,
  naming: renderNamingTab,
  "custom-fields": renderCustomFieldsTab,
  tags: renderTagsTab,
  checklists: renderChecklistsTab,
  "request-templates": renderRequestTemplatesTab,
  "request-settings": renderRequestSettingsTab,
  "take-on": renderTakeOnTab,
};
