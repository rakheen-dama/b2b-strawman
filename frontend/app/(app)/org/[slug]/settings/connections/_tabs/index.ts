import type { SettingsTabLoaders } from "@/components/settings/settings-family-page";
import { renderXeroTab } from "./xero";
import { renderMcpTab } from "./mcp";
import { renderAiTab } from "./ai";
import { renderNotificationsTab } from "./notifications";
import { renderPacksTab } from "./packs";
import { renderAutomationsTab } from "./automations";

/**
 * Connections tab loaders, one per Connections tab id in lib/settings-nav.ts (typed — a missing or
 * extra id is a compile error). Each is the loader of a former settings page; `xero` merges the
 * former integrations hub and the former Xero page (§12.7.1).
 */
export const CONNECTIONS_TAB_LOADERS: SettingsTabLoaders<"connections"> = {
  xero: renderXeroTab,
  mcp: renderMcpTab,
  ai: renderAiTab,
  notifications: renderNotificationsTab,
  packs: renderPacksTab,
  automations: renderAutomationsTab,
};
