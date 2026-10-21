import type { Metadata } from "next";
import {
  generateSettingsFamilyMetadata,
  renderSettingsFamilyPage,
  type SettingsFamilyRouteProps,
} from "@/components/settings/settings-family-page";
import { FIRM_TAB_LOADERS } from "./_tabs";

/**
 * Firm settings family (Phase 87 S9, ADR-341 §12.7): eight former pages as `?tab=` tabs. Only the
 * active tab's loader runs.
 */
export function generateMetadata(props: SettingsFamilyRouteProps): Promise<Metadata> {
  return generateSettingsFamilyMetadata("firm", props);
}

export default function FirmSettingsPage(props: SettingsFamilyRouteProps) {
  return renderSettingsFamilyPage("firm", FIRM_TAB_LOADERS, props);
}
