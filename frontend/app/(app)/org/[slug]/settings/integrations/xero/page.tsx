import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import {
  getXeroConnection,
  getXeroTaxMappings,
  getXeroSettings,
} from "@/lib/api/integrations";
import { XeroConnectionCard } from "@/components/integrations/XeroConnectionCard";
import { XeroTaxMappingEditor } from "@/components/integrations/XeroTaxMappingEditor";
import { XeroCustomerImport } from "@/components/integrations/XeroCustomerImport";
import { XeroSettingsForm } from "@/components/integrations/XeroSettingsForm";
import type { XeroConnectionResponse, XeroTaxMapping, XeroSettingsResponse } from "@/lib/types";

export default async function XeroSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin && !caps.capabilities.includes("INTEGRATION_MANAGE")) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings/integrations`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Integrations
        </Link>
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Xero Integration
          </h1>
          <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
            You do not have permission to manage integrations. Contact your administrator.
          </p>
        </div>
      </div>
    );
  }

  let connection: XeroConnectionResponse | null = null;
  let taxMappings: XeroTaxMapping[] = [];
  let settings: XeroSettingsResponse | null = null;

  // Fetch connection status — 404 means not connected
  try {
    connection = await getXeroConnection();
  } catch {
    // Not connected or error — leave as null
  }

  const isConnected = connection?.status === "CONNECTED";

  // If connected, fetch tax mappings and settings in parallel
  if (isConnected) {
    const [mappingsResult, settingsResult] = await Promise.allSettled([
      getXeroTaxMappings(),
      getXeroSettings(),
    ]);
    if (mappingsResult.status === "fulfilled") {
      taxMappings = mappingsResult.value;
    }
    if (settingsResult.status === "fulfilled") {
      settings = settingsResult.value;
    }
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/integrations`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Integrations
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Xero Integration
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage your Xero connection, tax mappings, and sync settings.
        </p>
      </div>

      <div className="space-y-6">
        <XeroConnectionCard connection={connection} slug={slug} />

        {isConnected && (
          <>
            <XeroTaxMappingEditor mappings={taxMappings} slug={slug} />

            <XeroCustomerImport slug={slug} />

            {/* Sync summary widget placeholder — lands in 525B */}

            {settings && <XeroSettingsForm settings={settings} slug={slug} />}
          </>
        )}
      </div>
    </div>
  );
}
