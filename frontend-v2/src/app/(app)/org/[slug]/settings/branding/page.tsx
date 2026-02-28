import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { SettingsSidebar } from "@/components/shell/settings-sidebar";
import { BrandingSettingsForm } from "@/components/settings/branding-settings-form";
import type { OrgSettings } from "@/lib/types";

export default async function BrandingSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let settings: OrgSettings = { defaultCurrency: "USD" };
  try {
    settings = await api.get<OrgSettings>("/api/settings");
  } catch {
    // Non-fatal
  }

  return (
    <SettingsSidebar slug={slug}>
      <div className="space-y-6">
        <div>
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            Branding
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Customize your organization logo, brand color, and document footer.
          </p>
        </div>

        <BrandingSettingsForm
          slug={slug}
          logoUrl={settings.logoUrl ?? null}
          brandColor={settings.brandColor ?? "#0d9488"}
          footerText={settings.documentFooterText ?? ""}
          canEdit={isAdmin}
        />
      </div>
    </SettingsSidebar>
  );
}
