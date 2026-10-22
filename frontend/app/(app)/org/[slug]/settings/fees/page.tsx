import type { Metadata } from "next";
import {
  generateSettingsFamilyMetadata,
  renderSettingsFamilyPage,
  type SettingsFamilyRouteProps,
} from "@/components/settings/settings-family-page";
import { FEES_TAB_LOADERS } from "./_tabs";

/**
 * Fees & Trust settings family (Phase 87 S9, ADR-341 §12.7): six former pages as `?tab=` tabs (no
 * kept sub-routes). Fee runs and Trust accounting are module-gated (bulk_billing,
 * trust_accounting), so the shared seam resolves the org's enabled modules. Only the active tab's
 * loader runs.
 */
export function generateMetadata(props: SettingsFamilyRouteProps): Promise<Metadata> {
  return generateSettingsFamilyMetadata("fees", props);
}

export default function FeesSettingsPage(props: SettingsFamilyRouteProps) {
  return renderSettingsFamilyPage("fees", FEES_TAB_LOADERS, props);
}
