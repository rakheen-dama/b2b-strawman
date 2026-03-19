import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { JurisdictionSelectorSection } from "@/components/settings/jurisdiction-selector";
import { InformationOfficerSection } from "@/components/settings/information-officer-section";
import type { OrgSettings } from "@/lib/types";

export default async function DataProtectionSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Settings
        </Link>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Data Protection
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage data protection settings. Only
          admins and owners can access this page.
        </p>
      </div>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };

  const settingsResult = await Promise.allSettled([
    api.get<OrgSettings>("/api/settings"),
  ]);
  if (settingsResult[0].status === "fulfilled") {
    settings = settingsResult[0].value;
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
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Data Protection
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure data protection jurisdiction, information officer details,
          and manage compliance settings.
        </p>
      </div>

      {/* Section 1: Jurisdiction Selector */}
      <JurisdictionSelectorSection
        slug={slug}
        currentJurisdiction={settings.dataProtectionJurisdiction ?? null}
      />

      {/* Section 2: Information Officer */}
      <InformationOfficerSection
        slug={slug}
        initialName={settings.informationOfficerName ?? null}
        initialEmail={settings.informationOfficerEmail ?? null}
      />

      {/* Section 3: DSAR Requests (stub) */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Data Subject Access Requests
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Track and manage data subject access requests (DSARs) including
          access, deletion, correction, and portability requests.
        </p>
        <div className="mt-4">
          <Link
            href={`/org/${slug}/settings/data-protection/requests`}
            className="text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
          >
            View DSAR Requests →
          </Link>
        </div>
      </div>

      {/* Section 4: Retention Policies (stub — expanded in 379C) */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Retention Policies
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure how long different types of data are retained before
          automatic anonymisation or deletion.
        </p>
        <p className="mt-3 text-sm text-slate-500 dark:text-slate-500">
          Retention policy management coming soon.
        </p>
      </div>

      {/* Section 5: Processing Register (stub — expanded in 379C) */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Processing Register
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Maintain a register of all personal data processing activities
          as required by your jurisdiction.
        </p>
        <p className="mt-3 text-sm text-slate-500 dark:text-slate-500">
          Processing register management coming soon.
        </p>
      </div>

      {/* Section 6: PAIA Manual (stub — expanded in 379C) */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          PAIA Manual
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Generate and manage your PAIA (Promotion of Access to Information Act)
          manual for South African compliance.
        </p>
        <p className="mt-3 text-sm text-slate-500 dark:text-slate-500">
          PAIA manual management coming soon.
        </p>
      </div>
    </div>
  );
}
