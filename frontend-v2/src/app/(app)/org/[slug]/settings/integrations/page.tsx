import { listIntegrations, listProviders } from "@/lib/api/integrations";
import { SettingsSidebar } from "@/components/shell/settings-sidebar";
import { IntegrationGrid } from "@/components/integrations/integration-grid";
import type { IntegrationDomain, OrgIntegration } from "@/lib/types";

export default async function IntegrationsSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let integrations: OrgIntegration[] = [];
  let providers: Partial<Record<IntegrationDomain, string[]>> = {};

  try {
    [integrations, providers] = await Promise.all([
      listIntegrations(),
      listProviders(),
    ]);
  } catch {
    // Non-fatal
  }

  return (
    <SettingsSidebar slug={slug}>
      <div className="space-y-6">
        <div>
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            Integrations
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Connect third-party tools and services to your organization.
          </p>
        </div>

        <IntegrationGrid
          slug={slug}
          integrations={integrations}
          providers={providers}
        />
      </div>
    </SettingsSidebar>
  );
}
