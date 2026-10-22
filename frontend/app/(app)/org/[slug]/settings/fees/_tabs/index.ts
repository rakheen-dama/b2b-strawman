import type { SettingsTabLoaders } from "@/components/settings/settings-family-page";
import { renderTariffsTab } from "./tariffs";
import { renderTaxTab } from "./tax";
import { renderTimeRecordingTab } from "./time-recording";
import { renderFeeRunsTab } from "./fee-runs";
import { renderCollectionsTab } from "./collections";
import { renderTrustAccountingTab } from "./trust-accounting";

/**
 * Fees & Trust tab loaders, one per Fees tab id in lib/settings-nav.ts (typed — a missing or extra
 * id is a compile error). Each is the loader of a former settings page.
 */
export const FEES_TAB_LOADERS: SettingsTabLoaders<"fees"> = {
  tariffs: renderTariffsTab,
  tax: renderTaxTab,
  "time-recording": renderTimeRecordingTab,
  "fee-runs": renderFeeRunsTab,
  collections: renderCollectionsTab,
  "trust-accounting": renderTrustAccountingTab,
};
