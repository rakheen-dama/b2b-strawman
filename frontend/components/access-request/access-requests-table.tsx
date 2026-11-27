"use client";

import { useMemo, useState } from "react";
import { Tabs as TabsPrimitive } from "radix-ui";
import { cn } from "@/lib/utils";
import { useRouter } from "next/navigation";
import { ClipboardList } from "lucide-react";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { EmptyState } from "@/components/empty-state";
import { ApproveDialog } from "@/components/access-request/approve-dialog";
import { RejectDialog } from "@/components/access-request/reject-dialog";
import { RelativeDate } from "@/components/ui/relative-date";
import type { AccessRequest } from "@/app/(app)/platform-admin/access-requests/actions";

const tabs = [
  { id: "ALL", label: "All" },
  { id: "PENDING", label: "Pending" },
  { id: "APPROVED", label: "Approved" },
  { id: "REJECTED", label: "Rejected" },
] as const;

type TabId = (typeof tabs)[number]["id"];

const statusBadgeVariant: Record<string, "warning" | "success" | "destructive" | "neutral"> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "destructive",
  PENDING_VERIFICATION: "neutral",
};

interface AccessRequestsTableProps {
  requests: AccessRequest[];
}

export function AccessRequestsTable({ requests }: AccessRequestsTableProps) {
  const [activeTab, setActiveTab] = useState<TabId>("PENDING");
  const [approveTarget, setApproveTarget] = useState<AccessRequest | null>(null);
  const [rejectTarget, setRejectTarget] = useState<AccessRequest | null>(null);
  const router = useRouter();

  // One sorted (oldest first) row array per tab, rebuilt only when the requests change: a new
  // `data` identity reads as navigation to the DataTable.
  const rowsByTab = useMemo(() => {
    const sortOldestFirst = (list: AccessRequest[]) =>
      [...list].sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
    return Object.fromEntries(
      tabs.map((tab) => [
        tab.id,
        sortOldestFirst(tab.id === "ALL" ? requests : requests.filter((r) => r.status === tab.id)),
      ])
    ) as Record<TabId, AccessRequest[]>;
  }, [requests]);

  // F-669B-B-8: the old nowrap table measured 1 188-1 195px in a 976px scroller at 1024 (Actions
  // out of view; 84-91px hidden at 1440). The prose columns wrap instead (the email anywhere,
  // above a 12rem floor), so no column has to hide: with the OBS rows the table fills its 974px
  // frame at 1024 on two-line rows, Approve and Reject in view, and fits the 1 230px frame at 1440
  // on one line. Submitted, Status and Actions never wrap.
  const columns = useMemo(
    () =>
      defineColumns<AccessRequest>([
        {
          id: "organizationName",
          enableHiding: false,
          meta: {
            label: "Org Name",
            cellClassName: "font-medium whitespace-normal",
            minWidth: "8rem",
          },
          cell: ({ row }) => row.original.organizationName,
        },
        {
          id: "email",
          meta: {
            label: "Email",
            cellClassName: "whitespace-normal wrap-anywhere",
            minWidth: "12rem",
          },
          cell: ({ row }) => row.original.email,
        },
        {
          id: "fullName",
          meta: { label: "Name", cellClassName: "whitespace-normal" },
          cell: ({ row }) => row.original.fullName,
        },
        {
          id: "country",
          meta: { label: "Country", cellClassName: "whitespace-normal" },
          cell: ({ row }) => row.original.country,
        },
        {
          id: "industry",
          meta: { label: "Industry", cellClassName: "whitespace-normal" },
          cell: ({ row }) => row.original.industry,
        },
        {
          id: "submitted",
          meta: { label: "Submitted" },
          cell: ({ row }) => <RelativeDate iso={row.original.createdAt} />,
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) => (
            <Badge variant={statusBadgeVariant[row.original.status] ?? "neutral"}>
              {row.original.status}
            </Badge>
          ),
        },
        {
          id: "actions",
          enableHiding: false,
          meta: { label: "Actions", align: "end" },
          cell: ({ row }) =>
            row.original.status === "PENDING" ? (
              <div className="flex justify-end gap-2">
                <Button
                  variant="accent"
                  size="sm"
                  data-testid="approve-btn"
                  onClick={() => setApproveTarget(row.original)}
                >
                  Approve
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => setRejectTarget(row.original)}
                >
                  Reject
                </Button>
              </div>
            ) : null,
        },
      ]),
    []
  );

  function handleSuccess() {
    router.refresh();
  }

  function renderContent(status: TabId) {
    return (
      <DataTable
        columns={columns}
        data={rowsByTab[status]}
        getRowId={(request) => request.id}
        rowTestId={(request) => `request-row-${request.organizationName}`}
        columnVisibility={false}
        stickyHeader={false}
        empty={
          <EmptyState
            variant="default"
            icon={ClipboardList}
            title="No access requests"
            description={
              status === "ALL"
                ? "No access requests have been submitted yet."
                : `No ${status.toLowerCase()} access requests.`
            }
          />
        }
      />
    );
  }

  return (
    <div data-testid="access-requests-page">
      <TabsPrimitive.Root value={activeTab} onValueChange={(v) => setActiveTab(v as TabId)}>
        <TabsPrimitive.List className="relative flex gap-6 border-b border-slate-200 dark:border-slate-800">
          {tabs.map((tab) => (
            <TabsPrimitive.Trigger
              key={tab.id}
              value={tab.id}
              data-testid={tab.id === "PENDING" ? "pending-tab" : undefined}
              className={cn(
                "relative pb-3 text-sm font-medium transition-colors outline-none",
                "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200",
                "data-[state=active]:text-slate-950 dark:data-[state=active]:text-slate-50",
                "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500",
                "after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:transition-colors",
                "after:bg-transparent data-[state=active]:after:bg-teal-500"
              )}
            >
              {tab.label}
            </TabsPrimitive.Trigger>
          ))}
        </TabsPrimitive.List>

        {tabs.map((tab) => (
          <TabsPrimitive.Content key={tab.id} value={tab.id} className="mt-6 outline-none">
            {renderContent(tab.id)}
          </TabsPrimitive.Content>
        ))}
      </TabsPrimitive.Root>

      {approveTarget && (
        <ApproveDialog
          requestId={approveTarget.id}
          orgName={approveTarget.organizationName}
          email={approveTarget.email}
          open={!!approveTarget}
          onOpenChange={(open) => {
            if (!open) setApproveTarget(null);
          }}
          onSuccess={handleSuccess}
        />
      )}

      {rejectTarget && (
        <RejectDialog
          requestId={rejectTarget.id}
          orgName={rejectTarget.organizationName}
          email={rejectTarget.email}
          open={!!rejectTarget}
          onOpenChange={(open) => {
            if (!open) setRejectTarget(null);
          }}
          onSuccess={handleSuccess}
        />
      )}
    </div>
  );
}
