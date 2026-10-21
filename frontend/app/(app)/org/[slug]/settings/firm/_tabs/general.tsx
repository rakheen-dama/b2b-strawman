import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { GeneralSettingsForm } from "@/components/settings/general-settings-form";
import { HelpTip } from "@/components/help-tip";
import { OrgDocumentsSection } from "@/components/settings/org-documents-section";
import { PortalSettingsSection } from "@/components/settings/portal-settings-section";
import type { Document, OrgSettings } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Firm › General tab (Phase 87 S9, ADR-341) — the former settings/general page's loader. */
export async function renderGeneralTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  let documents: Document[] = [];
  try {
    documents = await api.get<Document[]>("/api/documents?scope=ORG");
  } catch {
    // silently degrade — documents section renders with empty list
  }

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          General
          <HelpTip code="org.general" />
        </h1>

        <OrgDocumentsSection slug={slug} documents={documents} isAdmin={false} />
      </div>
    );
  }

  const settings = await api.get<OrgSettings>("/api/settings");

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          General
          <HelpTip code="org.general" />
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage your organization&apos;s currency, tax configuration, and branding.
        </p>
      </div>

      <GeneralSettingsForm
        slug={slug}
        defaultCurrency={settings.defaultCurrency}
        logoUrl={settings.logoUrl ?? null}
        brandColor={settings.brandColor ?? "#000000"}
        documentFooterText={settings.documentFooterText ?? ""}
        taxRegistrationNumber={settings.taxRegistrationNumber ?? ""}
        taxLabel={settings.taxLabel ?? ""}
        taxInclusive={settings.taxInclusive ?? false}
      />

      <PortalSettingsSection
        slug={slug}
        currentCadence={settings.portalDigestCadence ?? "WEEKLY"}
      />

      <OrgDocumentsSection slug={slug} documents={documents} isAdmin={true} />
    </div>
  );
}
