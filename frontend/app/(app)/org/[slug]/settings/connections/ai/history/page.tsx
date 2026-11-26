import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { settingsHref } from "@/lib/settings-nav";
import { getAiExecutions } from "@/lib/api/ai";
import type { AiExecutionListItem } from "@/lib/api/ai";
import { ExecutionHistoryClient } from "./history-client";

export default async function ExecutionHistoryPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ skillId?: string; status?: string; page?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin && !caps.capabilities.includes("AI_MANAGE")) {
    return (
      <div className="space-y-8">
        <Link
          href={settingsHref(slug, "connections", "ai")}
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
        >
          <ChevronLeft className="size-4" />
          AI Configuration
        </Link>
        <PageHeader title="Execution History" />
        <p className="text-muted-foreground text-sm">
          You do not have permission to view execution history. Contact your administrator.
        </p>
      </div>
    );
  }

  const page = search.page ? Math.max(0, parseInt(search.page, 10) || 0) : 0;
  let executions: AiExecutionListItem[] = [];
  let totalPages = 0;
  let totalElements = 0;

  try {
    const result = await getAiExecutions({
      skillId: search.skillId,
      status: search.status,
      page,
      size: 20,
    });
    executions = result.content;
    totalPages = result.page.totalPages;
    totalElements = result.page.totalElements;
  } catch {
    // Silently handle — show empty state
  }

  return (
    <div className="space-y-6">
      <Link
        href={settingsHref(slug, "connections", "ai")}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeft className="size-4" />
        AI Configuration
      </Link>

      <PageHeader
        title="Execution History"
        description="View all AI skill executions, costs, and outcomes."
      />

      <ExecutionHistoryClient
        slug={slug}
        executions={executions}
        currentPage={page}
        totalPages={totalPages}
        totalElements={totalElements}
        currentSkillId={search.skillId}
        currentStatus={search.status}
      />
    </div>
  );
}
