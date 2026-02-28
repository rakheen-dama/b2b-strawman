import { getAuthContext } from "@/lib/auth";
import { getTemplates, getOrgSettings } from "@/lib/api";
import { SettingsSidebar } from "@/components/shell/settings-sidebar";
import { TemplateList } from "@/components/templates/template-list";
import type { TemplateListResponse, OrgSettings } from "@/lib/types";

export default async function TemplatesSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  const [templatesResult, settingsResult] = await Promise.allSettled([
    getTemplates(),
    getOrgSettings(),
  ]);

  const templates: TemplateListResponse[] =
    templatesResult.status === "fulfilled" ? templatesResult.value : [];
  const settings: OrgSettings | null =
    settingsResult.status === "fulfilled" ? settingsResult.value : null;

  return (
    <SettingsSidebar slug={slug}>
      <div className="space-y-6">
        <div>
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            Templates
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Manage document templates for engagement letters, statements of
            work, and more.
          </p>
        </div>

        <TemplateList
          slug={slug}
          templates={templates}
          settings={settings}
          canManage={isAdmin}
        />
      </div>
    </SettingsSidebar>
  );
}
