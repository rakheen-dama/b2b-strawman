import Link from "next/link";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { getDeal, listDealProposals } from "@/lib/api/crm";
import { PermissionDenied } from "@/components/permission-denied";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { DealOverview } from "@/components/pipeline/DealOverview";
import { DealProposalsPanel } from "@/components/pipeline/DealProposalsPanel";
import { DealActivityTab } from "@/components/pipeline/DealActivityTab";
import { ArrowLeft } from "lucide-react";
import type { Customer, OrgMember } from "@/lib/types";
import type { LinkedProposalDto } from "@/lib/api/crm";

export default async function DealDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const capData = await fetchMyCapabilities();

  if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("VIEW_DEALS")) {
    return <PermissionDenied featureName="Pipeline" dashboardHref={`/org/${slug}/dashboard`} />;
  }

  const isAdmin = capData.isAdmin || capData.isOwner;
  const canManage = isAdmin || capData.capabilities.includes("MANAGE_DEALS");

  // getDeal maps a backend 404 to Next.js notFound() automatically.
  const deal = await getDeal(id);

  let proposals: LinkedProposalDto[] = [];
  try {
    proposals = await listDealProposals(id);
  } catch {
    /* non-fatal: show empty proposals panel */
  }

  // Resolve customer + owner display names (mirror the board page). There is no
  // typed `getCustomer` / `listMembers` client in lib/api/ — the established
  // convention is raw `api.get` for these read-only display lookups (see the
  // sibling customers/[id]/page.tsx:94/147 and pipeline/page.tsx:166).
  let customerName = "Unknown customer";
  try {
    const customer = await api.get<Customer>(`/api/customers/${deal.customerId}`);
    customerName = customer.name;
  } catch {
    /* non-fatal: fall back to placeholder name */
  }

  let ownerName: string | null = null;
  if (deal.ownerId) {
    try {
      const members = await api.get<OrgMember[]>("/api/members");
      ownerName = members.find((m) => m.id === deal.ownerId)?.name ?? null;
    } catch {
      /* non-fatal */
    }
  }

  const currency = deal.valueCurrency || "ZAR";

  return (
    <div className="space-y-6">
      <div>
        <Link
          href={`/org/${slug}/pipeline`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Pipeline
        </Link>
      </div>

      <div>
        <p className="font-mono text-xs tracking-wide text-slate-500 uppercase dark:text-slate-400">
          {deal.dealNumber}
        </p>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">{deal.title}</h1>
      </div>

      <Tabs defaultValue="overview" className="w-full">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="proposals">Proposals</TabsTrigger>
          <TabsTrigger value="activity">Activity</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="pt-6 outline-none">
          <DealOverview deal={deal} slug={slug} customerName={customerName} ownerName={ownerName} />
        </TabsContent>

        <TabsContent value="proposals" className="pt-6 outline-none">
          <DealProposalsPanel
            slug={slug}
            dealId={id}
            proposals={proposals}
            currency={currency}
            canManage={canManage}
          />
        </TabsContent>

        <TabsContent value="activity" className="pt-6 outline-none">
          <DealActivityTab dealId={id} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
