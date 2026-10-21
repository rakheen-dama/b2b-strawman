import type { SettingsTabLoaders } from "@/components/settings/settings-family-page";
import { renderGeneralTab } from "./general";
import { renderRolesTab } from "./roles";
import { renderAuditTrailTab } from "./audit-trail";
import { renderDataProtectionTab } from "./data-protection";
import { renderDataRequestsTab } from "./data-requests";
import { renderComplianceTab } from "./compliance";
import { renderFeaturesTab } from "./features";
import { renderSubscriptionTab } from "./subscription";

/**
 * Firm tab loaders, one per Firm tab id in lib/settings-nav.ts (typed — a missing or extra id is a
 * compile error). Each is the loader of a former settings page.
 */
export const FIRM_TAB_LOADERS: SettingsTabLoaders<"firm"> = {
  general: renderGeneralTab,
  roles: renderRolesTab,
  "audit-trail": renderAuditTrailTab,
  "data-protection": renderDataProtectionTab,
  "data-requests": renderDataRequestsTab,
  compliance: renderComplianceTab,
  features: renderFeaturesTab,
  subscription: renderSubscriptionTab,
};
