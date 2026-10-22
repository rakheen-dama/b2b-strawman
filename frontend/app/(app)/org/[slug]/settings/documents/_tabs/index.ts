import type { SettingsTabLoaders } from "@/components/settings/settings-family-page";
import { renderTemplatesTab } from "./templates";
import { renderClausesTab } from "./clauses";
import { renderAcceptanceTab } from "./acceptance";
import { renderEmailTab } from "./email";

/**
 * Documents tab loaders, one per Documents tab id in lib/settings-nav.ts (typed — a missing or extra
 * id is a compile error). Each is the loader of a former settings page.
 */
export const DOCUMENTS_TAB_LOADERS: SettingsTabLoaders<"documents"> = {
  templates: renderTemplatesTab,
  clauses: renderClausesTab,
  acceptance: renderAcceptanceTab,
  email: renderEmailTab,
};
