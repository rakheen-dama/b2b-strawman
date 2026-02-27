import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { AcceptanceSettingsForm } from "@/components/acceptance/AcceptanceSettingsForm";
import type { OrgSettings } from "@/lib/types";

export default async function AcceptanceSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
          Document Acceptance
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage acceptance settings. Only admins
          and owners can access this page.
        </p>
      </div>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };

  const settingsResult = await api
    .get<OrgSettings>("/api/settings")
    .catch(() => null);
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
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Document Acceptance
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure default expiry period for document acceptance requests.
        </p>
      </div>

      <AcceptanceSettingsForm
        slug={slug}
        acceptanceExpiryDays={settings.acceptanceExpiryDays ?? 30}
      />
    </div>
  );
}
