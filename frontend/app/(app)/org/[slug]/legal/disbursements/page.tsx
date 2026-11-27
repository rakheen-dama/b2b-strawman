import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { DisbursementsListClient } from "./disbursements-list-client";
import { fetchDisbursements, fetchProjects } from "./actions";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";
import { PageHeader } from "@b2mash/ui/page-header";

export default async function DisbursementsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;

  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("disbursements")) {
    notFound();
  }

  let initialDisbursements: DisbursementResponse[] = [];
  let initialTotal = 0;
  let initialProjectNames: Record<string, string> = {};

  try {
    const [list, projects] = await Promise.all([
      fetchDisbursements(),
      fetchProjects().catch(() => []),
    ]);
    initialDisbursements = list.content;
    initialTotal = list.page.totalElements;
    initialProjectNames = Object.fromEntries(projects.map((p) => [p.id, p.name]));
  } catch (error) {
    console.error("Failed to fetch disbursements:", error);
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Disbursements"
        description={
          <>
            Manage client disbursements &mdash; matter costs paid by the firm on the client&apos;s
            behalf.
          </>
        }
      />

      <DisbursementsListClient
        slug={slug}
        initialDisbursements={initialDisbursements}
        initialTotal={initialTotal}
        initialProjectNames={initialProjectNames}
      />
    </div>
  );
}
