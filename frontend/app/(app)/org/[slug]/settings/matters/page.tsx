import type { Metadata } from "next";
import {
  generateSettingsFamilyMetadata,
  renderSettingsFamilyPage,
  type SettingsFamilyRouteProps,
} from "@/components/settings/settings-family-page";
import { MATTERS_TAB_LOADERS } from "./_tabs";

/**
 * Matters settings family (Phase 87 S9, ADR-341 §12.7): seven former pages as `?tab=` tabs; the
 * template, checklist and request-template editors stay routes under this family
 * (templates/[id], checklists/{new,[id],[id]/edit}, request-templates/{new,[id]}). Only the active
 * tab's loader runs.
 */
export function generateMetadata(props: SettingsFamilyRouteProps): Promise<Metadata> {
  return generateSettingsFamilyMetadata("matters", props);
}

export default function MattersSettingsPage(props: SettingsFamilyRouteProps) {
  return renderSettingsFamilyPage("matters", MATTERS_TAB_LOADERS, props);
}
