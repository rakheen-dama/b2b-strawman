"use client";

import { useCallback, useEffect, useRef, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Table, TableBody, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@b2mash/ui/input";
import { Button } from "@b2mash/ui/button";
import { Label } from "@b2mash/ui/label";
import { AI_QUEUE_STRINGS } from "@/lib/constants/ai-queue-strings";
import { QueueRow } from "@/components/assistant/queue/queue-row";
import { BulkApproveBar } from "@/components/assistant/queue/bulk-approve-bar";
import { InvocationDrawer } from "@/components/assistant/queue/invocation-drawer";
import { getInvocationClient } from "@/lib/api/assistant-specialists";
import type { InvocationFilter, InvocationPage, InvocationDetail } from "@/lib/api/ai-invocations";

interface AiQueueClientProps {
  slug: string;
  initialData: InvocationPage;
  initialFilter: InvocationFilter;
}

const STATUS_OPTIONS = [
  { value: "__all__", label: "All statuses" },
  { value: "PENDING_APPROVAL", label: "Pending" },
  { value: "RUNNING", label: "Running" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
  { value: "AUTO_APPLIED", label: "Auto-applied" },
  { value: "FAILED", label: "Failed" },
  { value: "EXPIRED", label: "Expired" },
];

const SPECIALIST_OPTIONS = [
  { value: "__all__", label: "All specialists" },
  { value: "BILLING", label: "Billing" },
  { value: "INTAKE", label: "Intake" },
  { value: "INBOX", label: "Inbox" },
];

export function AiQueueClient({ slug, initialData, initialFilter }: AiQueueClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [isPending, startTransition] = useTransition();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerInvocation, setDrawerInvocation] = useState<InvocationDetail | null>(null);

  const buildPath = useCallback(
    (qs: string) => `/org/${slug}/settings/automations/ai-queue${qs ? `?${qs}` : ""}`,
    [slug]
  );

  const searchParamsRef = useRef(searchParams);
  useEffect(() => {
    searchParamsRef.current = searchParams;
  }, [searchParams]);

  const updateUrl = useCallback(
    (mutator: (params: URLSearchParams) => void) => {
      const latest = searchParamsRef.current;
      const params = new URLSearchParams(latest?.toString() ?? "");
      mutator(params);
      startTransition(() => {
        router.push(buildPath(params.toString()), { scroll: false });
      });
    },
    [router, buildPath]
  );

  const handleStatusChange = useCallback(
    (value: string) => {
      updateUrl((params) => {
        if (value === "__all__") {
          params.delete("status");
        } else {
          params.set("status", value);
        }
        params.delete("page");
      });
    },
    [updateUrl]
  );

  const handleSpecialistChange = useCallback(
    (value: string) => {
      updateUrl((params) => {
        if (value === "__all__") {
          params.delete("specialistId");
        } else {
          params.set("specialistId", value);
        }
        params.delete("page");
      });
    },
    [updateUrl]
  );

  const handleDateChange = useCallback(
    (field: "from" | "to", value: string) => {
      updateUrl((params) => {
        if (value) {
          params.set(field, new Date(value).toISOString());
        } else {
          params.delete(field);
        }
        params.delete("page");
      });
    },
    [updateUrl]
  );

  const handleActorChange = useCallback(
    (value: string) => {
      updateUrl((params) => {
        if (value) {
          params.set("actorId", value);
        } else {
          params.delete("actorId");
        }
        params.delete("page");
      });
    },
    [updateUrl]
  );

  const goToPage = useCallback(
    (page: number) => {
      updateUrl((params) => {
        params.set("page", String(page));
      });
    },
    [updateUrl]
  );

  const handleRowSelect = useCallback((id: string, checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }, []);

  const handleRowClick = useCallback(async (id: string) => {
    // Fetch detail for the drawer using authenticated client
    try {
      const detail = await getInvocationClient(id);
      setDrawerInvocation(detail as InvocationDetail);
      setDrawerOpen(true);
    } catch {
      // Silently fail — drawer stays closed
    }
  }, []);

  const handleActionComplete = useCallback(() => {
    // Refresh the page data
    router.refresh();
    setSelected(new Set());
  }, [router]);

  const selectedSpecialists: Record<string, string> = {};
  for (const id of selected) {
    const item = initialData.content.find((i) => i.id === id);
    if (item) selectedSpecialists[id] = item.specialistId;
  }

  const { content, page } = initialData;
  const hasNextPage = page.number < page.totalPages - 1;
  const hasPrevPage = page.number > 0;

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap items-end gap-3" data-testid="queue-filters">
        <div className="space-y-1">
          <Label className="text-xs">{AI_QUEUE_STRINGS.filter.status}</Label>
          <Select value={initialFilter.status ?? "__all__"} onValueChange={handleStatusChange}>
            <SelectTrigger className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {STATUS_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1">
          <Label className="text-xs">{AI_QUEUE_STRINGS.filter.specialist}</Label>
          <Select
            value={initialFilter.specialistId ?? "__all__"}
            onValueChange={handleSpecialistChange}
          >
            <SelectTrigger className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {SPECIALIST_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1">
          <Label className="text-xs">From</Label>
          <Input
            type="date"
            className="w-36"
            defaultValue={initialFilter.from ? initialFilter.from.slice(0, 10) : ""}
            onChange={(e) => handleDateChange("from", e.target.value)}
          />
        </div>

        <div className="space-y-1">
          <Label className="text-xs">To</Label>
          <Input
            type="date"
            className="w-36"
            defaultValue={initialFilter.to ? initialFilter.to.slice(0, 10) : ""}
            onChange={(e) => handleDateChange("to", e.target.value)}
          />
        </div>

        <div className="space-y-1">
          <Label className="text-xs">{AI_QUEUE_STRINGS.filter.actor}</Label>
          <Input
            type="text"
            className="w-48"
            placeholder="Actor ID"
            defaultValue={initialFilter.actorId ?? ""}
            onBlur={(e) => handleActorChange(e.target.value)}
          />
        </div>
      </div>

      {/* Table */}
      {content.length === 0 ? (
        <div className="rounded-lg border border-dashed p-8 text-center">
          <p className="text-sm text-slate-500">{AI_QUEUE_STRINGS.empty}</p>
        </div>
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10" />
                <TableHead>Status</TableHead>
                <TableHead>Specialist</TableHead>
                <TableHead>Context</TableHead>
                <TableHead>Entity</TableHead>
                <TableHead>Source</TableHead>
                <TableHead>Created</TableHead>
                <TableHead>Output</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {content.map((item) => (
                <QueueRow
                  key={item.id}
                  id={item.id}
                  specialistId={item.specialistId}
                  invokedBy={item.invokedBy}
                  status={item.status}
                  contextEntityType={item.contextEntityType}
                  contextEntityId={item.contextEntityId}
                  createdAt={item.createdAt}
                  proposedOutputSummary={item.proposedOutputSummary}
                  selected={selected.has(item.id)}
                  onSelect={handleRowSelect}
                  onClick={handleRowClick}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {/* Pagination */}
      {page.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-slate-500">
            Page {page.number + 1} of {page.totalPages} ({page.totalElements} total)
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={!hasPrevPage || isPending}
              onClick={() => goToPage(page.number - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={!hasNextPage || isPending}
              onClick={() => goToPage(page.number + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Bulk Approve Bar */}
      <BulkApproveBar
        selectedIds={Array.from(selected)}
        selectedSpecialists={selectedSpecialists}
        onComplete={handleActionComplete}
        onClear={() => setSelected(new Set())}
      />

      {/* Drawer */}
      <InvocationDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        invocation={drawerInvocation}
        onActionComplete={handleActionComplete}
      />
    </div>
  );
}
