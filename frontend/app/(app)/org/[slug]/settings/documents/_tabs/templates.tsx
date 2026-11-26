import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getTemplates, getOrgSettings } from "@/lib/api";
import { HelpTip } from "@/components/help-tip";
import { TemplatesContent } from "./templates-content";
import type { TemplateListResponse, OrgSettings } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Documents › Templates tab (Phase 87 S9, ADR-341) — the former templates settings page's loader. */
export async function renderTemplatesTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;

  const [templatesResult, settingsResult] = await Promise.allSettled([
    getTemplates(),
    getOrgSettings(),
  ]);
  const templates: TemplateListResponse[] =
    templatesResult.status === "fulfilled" ? templatesResult.value : [];
  const settings: OrgSettings | null =
    settingsResult.status === "fulfilled" ? settingsResult.value : null;

  return (
    <div className="space-y-8">
      <PageHeader
        title={
          <span className="inline-flex items-center gap-2">
            Templates
            <HelpTip code="templates.tiptapVsWord" />
          </span>
        }
        description="Manage document templates for engagement letters, statements of work, and more."
      />

      <TemplatesContent slug={slug} templates={templates} settings={settings} canManage={isAdmin} />
    </div>
  );
}
