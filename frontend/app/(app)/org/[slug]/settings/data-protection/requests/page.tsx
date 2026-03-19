import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { fetchDsarRequests } from "./actions";
import { DsarRequestsTable } from "@/components/data-protection/dsar-requests-table";
import { LogDsarRequestDialog } from "@/components/data-protection/log-dsar-dialog";

export default async function DsarRequestsPage({
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
          href={`/org/${slug}/settings/data-protection`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Data Protection
        </Link>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          DSAR Requests
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage DSAR requests. Only admins and
          owners can access this page.
        </p>
      </div>
    );
  }

  let requests: Awaited<ReturnType<typeof fetchDsarRequests>> = [];
  let fetchError: string | null = null;
  try {
    requests = await fetchDsarRequests(slug);
  } catch (error) {
    console.error("Failed to fetch DSAR requests:", error);
    fetchError =
      error instanceof Error ? error.message : "Failed to load DSAR requests.";
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/data-protection`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Data Protection
      </Link>

      <div className="flex items-start justify-between">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            DSAR Requests
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Track and manage data subject access requests.
          </p>
        </div>
        <LogDsarRequestDialog slug={slug} />
      </div>

      {fetchError && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950">
          <p className="text-sm text-red-700 dark:text-red-300">
            {fetchError}
          </p>
        </div>
      )}

      <DsarRequestsTable requests={requests} slug={slug} />
    </div>
  );
}
