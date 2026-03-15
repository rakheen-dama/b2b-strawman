import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { BatchBillingSettings } from "@/components/settings/batch-billing-settings";
import type { OrgSettings } from "@/lib/types";

export default async function BatchBillingSettingsPage({
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
          Batch Billing
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage batch billing settings. Only
          admins and owners can access this page.
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
          Batch Billing
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure async thresholds, email rate limits, and default billing
          currency for batch billing runs.
        </p>
      </div>

      <BatchBillingSettings
        slug={slug}
        billingBatchAsyncThreshold={
          settings.billingBatchAsyncThreshold ?? 50
        }
        billingEmailRateLimit={settings.billingEmailRateLimit ?? 5}
        defaultBillingRunCurrency={
          settings.defaultBillingRunCurrency ?? null
        }
      />
    </div>
  );
}
