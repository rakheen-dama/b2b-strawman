import type { Metadata } from "next";
import {
  generateSettingsFamilyMetadata,
  renderSettingsFamilyPage,
  type SettingsFamilyRouteProps,
} from "@/components/settings/settings-family-page";
import { DOCUMENTS_TAB_LOADERS } from "./_tabs";

/**
 * Documents settings family (Phase 87 S9, ADR-341 §12.7): four former pages as `?tab=` tabs; the
 * document-template editors stay routes under this family (templates/new, templates/[id]/edit).
 * Only the active tab's loader runs.
 */
export function generateMetadata(props: SettingsFamilyRouteProps): Promise<Metadata> {
  return generateSettingsFamilyMetadata("documents", props);
}

export default function DocumentsSettingsPage(props: SettingsFamilyRouteProps) {
  return renderSettingsFamilyPage("documents", DOCUMENTS_TAB_LOADERS, props);
}
