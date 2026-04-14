"use client";

import { useState } from "react";
import { Tabs as TabsPrimitive } from "radix-ui";
import { cn } from "@/lib/utils";
import { useRouter } from "next/navigation";
import { ClipboardList } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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

function RequestTable({
  requests,
  onApprove,
  onReject,
}: {
  requests: AccessRequest[];
  onApprove: (r: AccessRequest) => void;
  onReject: (r: AccessRequest) => void;
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Org Name</TableHead>
          <TableHead>Email</TableHead>
          <TableHead>Name</TableHead>
          <TableHead>Country</TableHead>
          <TableHead>Industry</TableHead>
          <TableHead>Submitted</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {requests.map((request) => (
          <TableRow key={request.id} data-testid={`request-row-${request.organizationName}`}>
            <TableCell className="font-medium">{request.organizationName}</TableCell>
            <TableCell>{request.email}</TableCell>
            <TableCell>{request.fullName}</TableCell>
            <TableCell>{request.country}</TableCell>
            <TableCell>{request.industry}</TableCell>
            <TableCell>
              <RelativeDate iso={request.createdAt} />
            </TableCell>
            <TableCell>
              <Badge variant={statusBadgeVariant[request.status] ?? "neutral"}>
                {request.status}
              </Badge>
            </TableCell>
            <TableCell className="text-right">
              {request.status === "PENDING" && (
                <div className="flex justify-end gap-2">
                  <Button
                    variant="accent"
                    size="sm"
                    data-testid="approve-btn"
                    onClick={() => onApprove(request)}
                  >
                    Approve
                  </Button>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={() => onReject(request)}
                  >
                    Reject
                  </Button>
                </div>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function AccessRequestsTable({ requests }: AccessRequestsTableProps) {
  const [activeTab, setActiveTab] = useState<TabId>("PENDING");
  const [approveTarget, setApproveTarget] = useState<AccessRequest | null>(null);
  const [rejectTarget, setRejectTarget] = useState<AccessRequest | null>(null);
  const router = useRouter();

  function filterAndSort(status: TabId) {
    const filtered = status === "ALL" ? requests : requests.filter((r) => r.status === status);
    return [...filtered].sort(
      (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
    );
  }

  function handleSuccess() {
    router.refresh();
  }

  function renderContent(status: TabId) {
    const sorted = filterAndSort(status);
    if (sorted.length === 0) {
      return (
        <EmptyState
          icon={ClipboardList}
          title="No access requests"
          description={
            status === "ALL"
              ? "No access requests have been submitted yet."
              : `No ${status.toLowerCase()} access requests.`
          }
        />
      );
    }
    return (
      <RequestTable
        requests={sorted}
        onApprove={setApproveTarget}
        onReject={setRejectTarget}
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
