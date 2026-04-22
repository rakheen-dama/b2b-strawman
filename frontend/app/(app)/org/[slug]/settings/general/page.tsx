import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { GeneralSettingsForm } from "@/components/settings/general-settings-form";
import { HelpTip } from "@/components/help-tip";
import { VerticalProfileSection } from "@/components/settings/vertical-profile-section";
import { OrgDocumentsSection } from "@/components/settings/org-documents-section";
import { PortalSettingsSection } from "@/components/settings/portal-settings-section";
import type { Document, OrgSettings } from "@/lib/types";

export default async function GeneralSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
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
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Settings
        </Link>
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          General
          <HelpTip code="org.general" docsPath="/admin/org-settings" />
        </h1>

        <OrgDocumentsSection slug={slug} documents={documents} isAdmin={false} />
      </div>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };

  const settingsResult = await api.get<OrgSettings>("/api/settings").catch(() => null);
  if (settingsResult) {
    settings = settingsResult;
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      <div>
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          General
          <HelpTip code="org.general" docsPath="/admin/org-settings" />
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage your organization&apos;s currency, tax configuration, and branding.
        </p>
      </div>

      <VerticalProfileSection
        slug={slug}
        currentProfile={settings.verticalProfile ?? null}
        isOwner={caps.isOwner}
      />

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
        currentMemberDisplay={settings.portalRetainerMemberDisplay ?? "FIRST_NAME_ROLE"}
      />

      <OrgDocumentsSection slug={slug} documents={documents} isAdmin={true} />
    </div>
  );
}
