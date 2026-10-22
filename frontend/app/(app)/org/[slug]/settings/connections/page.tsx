import type { Metadata } from "next";
import {
  generateSettingsFamilyMetadata,
  renderSettingsFamilyPage,
  type SettingsFamilyRouteProps,
} from "@/components/settings/settings-family-page";
import { CONNECTIONS_TAB_LOADERS } from "./_tabs";

/**
 * Connections settings family (Phase 87 S9, ADR-341 §12.7): seven former pages as six `?tab=` tabs
 * (integrations + integrations/xero → xero); the Xero sync log, the AI execution history and the
 * automation execution log stay routes under this family (xero/sync-log, ai/history,
 * automations/executions). No tab is module-gated, so the seam does not fetch org settings. Only the
 * active tab's loader runs.
 */
export function generateMetadata(props: SettingsFamilyRouteProps): Promise<Metadata> {
  return generateSettingsFamilyMetadata("connections", props);
}

export default function ConnectionsSettingsPage(props: SettingsFamilyRouteProps) {
  return renderSettingsFamilyPage("connections", CONNECTIONS_TAB_LOADERS, props);
}
