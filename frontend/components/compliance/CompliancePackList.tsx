"use client";

import { useMemo, useState } from "react";
import { ChevronDown, ChevronRight, Loader2 } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { StatusBadgeCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { formatDate } from "@/lib/format";
import { getCompliancePackDetail } from "@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions";
import type { CompliancePackEntry, CompliancePackDetail } from "@/lib/types";

interface CompliancePackListProps {
  packs: CompliancePackEntry[];
}

export function CompliancePackList({ packs }: CompliancePackListProps) {
  const [expandedPackId, setExpandedPackId] = useState<string | null>(null);
  const [packDetails, setPackDetails] = useState<Record<string, CompliancePackDetail>>({});
  const [loadingPackId, setLoadingPackId] = useState<string | null>(null);

  async function handleToggle(packId: string) {
    if (expandedPackId === packId) {
      setExpandedPackId(null);
      return;
    }

    setExpandedPackId(packId);

    if (!packDetails[packId]) {
      setLoadingPackId(packId);
      const detail = await getCompliancePackDetail(packId);
      if (detail) {
        setPackDetails((prev) => ({ ...prev, [packId]: detail }));
      }
      setLoadingPackId(null);
    }
  }

  if (packs.length === 0) {
    return <p className="text-muted-foreground text-sm">No compliance packs applied.</p>;
  }

  return (
    <div className="space-y-2">
      {packs.map((pack) => {
        const isExpanded = expandedPackId === pack.packId;
        const detail = packDetails[pack.packId];
        const isLoading = loadingPackId === pack.packId;

        return (
          <div key={pack.packId} className="rounded-md border">
            <button
              type="button"
              onClick={() => handleToggle(pack.packId)}
              className="hover:bg-muted flex w-full items-center justify-between rounded-md px-4 py-2 text-left transition-colors"
            >
              <div className="flex items-center gap-2">
                {isExpanded ? (
                  <ChevronDown className="text-muted-foreground size-4 shrink-0" />
                ) : (
                  <ChevronRight className="text-muted-foreground size-4 shrink-0" />
                )}
                <div>
                  <p className="text-foreground font-medium">{detail?.name ?? pack.packId}</p>
                  {detail?.name && <p className="text-muted-foreground text-xs">{pack.packId}</p>}
                </div>
              </div>
              <div className="flex items-center gap-3">
                <Badge variant="neutral">v{pack.version}</Badge>
                <p className="text-muted-foreground text-xs">
                  Applied {formatDate(pack.appliedAt)}
                </p>
              </div>
            </button>

            {isExpanded && (
              <div className="border-t px-4 py-4">
                {isLoading ? (
                  <div className="text-muted-foreground flex items-center gap-2 text-sm">
                    <Loader2 className="size-4 animate-spin" />
                    Loading pack details...
                  </div>
                ) : detail ? (
                  <PackDetailView detail={detail} />
                ) : (
                  <p className="text-muted-foreground text-sm">Could not load pack details.</p>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

type ChecklistItem = NonNullable<CompliancePackDetail["checklistTemplate"]>["items"][number];
type RetentionOverride = NonNullable<CompliancePackDetail["retentionOverrides"]>[number];
/** Pack rows have no id; the position in the pack is their key (as the raw tables used). */
type Keyed<T> = T & { rowKey: string };

const getRowKey = (row: { rowKey: string }) => row.rowKey;

const itemColumns = defineColumns<Keyed<ChecklistItem>>([
  {
    id: "name",
    meta: { label: "Name", cellClassName: "text-foreground" },
    cell: ({ row }) => row.original.name,
  },
  {
    id: "description",
    meta: { label: "Description", cellClassName: "text-muted-foreground whitespace-normal" },
    cell: ({ row }) => row.original.description,
  },
  {
    id: "required",
    meta: { label: "Required" },
    cell: ({ row }) => (
      <StatusBadgeCell
        label={row.original.required ? "Yes" : "No"}
        variant={row.original.required ? "success" : "neutral"}
      />
    ),
  },
  {
    id: "documentRequired",
    meta: { label: "Document Required" },
    cell: ({ row }) =>
      row.original.requiresDocument ? (
        <StatusBadgeCell label={row.original.requiredDocumentLabel ?? "Yes"} variant="warning" />
      ) : (
        <StatusBadgeCell label="No" variant="neutral" />
      ),
  },
]);

const overrideColumns = defineColumns<Keyed<RetentionOverride>>([
  {
    id: "recordType",
    meta: { label: "Record Type", cellClassName: "text-foreground" },
    cell: ({ row }) => row.original.recordType,
  },
  {
    id: "triggerEvent",
    meta: { label: "Trigger", cellClassName: "text-muted-foreground" },
    cell: ({ row }) => row.original.triggerEvent,
  },
  {
    id: "retentionDays",
    meta: { label: "Days", cellClassName: "text-foreground" },
    cell: ({ row }) => row.original.retentionDays,
  },
  {
    id: "action",
    meta: { label: "Action" },
    cell: ({ row }) => <StatusBadgeCell label={row.original.action} variant="outline" />,
  },
]);

function PackDetailView({ detail }: { detail: CompliancePackDetail }) {
  // Sorted on a copy (the old in-place `.sort` mutated the cached detail) — same order (DA-20).
  const items = useMemo<Keyed<ChecklistItem>[]>(
    () =>
      [...(detail.checklistTemplate?.items ?? [])]
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((item, idx) => ({ ...item, rowKey: String(idx) })),
    [detail.checklistTemplate]
  );
  const overrides = useMemo<Keyed<RetentionOverride>[]>(
    () => (detail.retentionOverrides ?? []).map((o, idx) => ({ ...o, rowKey: String(idx) })),
    [detail.retentionOverrides]
  );

  return (
    <div className="space-y-5">
      {/* Description and metadata */}
      <div>
        <p className="text-foreground text-sm">{detail.description}</p>
        <div className="mt-2 flex items-center gap-2">
          {detail.jurisdiction && <Badge variant="outline">{detail.jurisdiction}</Badge>}
          <Badge variant="outline">{detail.customerType}</Badge>
        </div>
      </div>

      {/* Checklist Items */}
      {items.length > 0 && (
        <div>
          <h4 className="text-foreground text-sm font-semibold">Checklist Items</h4>
          <div className="mt-2">
            <DataTable
              columns={itemColumns}
              data={items}
              getRowId={getRowKey}
              columnVisibility={false}
              pageSize={null}
              stickyHeader={false}
              frame={false}
              empty={null}
            />
          </div>
        </div>
      )}

      {/* Field Definitions */}
      {detail.fieldDefinitions && detail.fieldDefinitions.length > 0 && (
        <div>
          <h4 className="text-foreground text-sm font-semibold">Field Definitions</h4>
          <ul className="mt-2 space-y-1">
            {detail.fieldDefinitions.map((field, idx) => (
              <li key={idx} className="text-foreground flex items-center gap-2 text-sm">
                <span className="font-medium">{field.label}</span>
                <Badge variant="outline">{field.fieldType}</Badge>
                {field.required && <Badge variant="success">Required</Badge>}
                {field.groupName && (
                  <span className="text-muted-foreground text-xs">({field.groupName})</span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Retention Overrides */}
      {overrides.length > 0 && (
        <div>
          <h4 className="text-foreground text-sm font-semibold">Retention Overrides</h4>
          <div className="mt-2">
            <DataTable
              columns={overrideColumns}
              data={overrides}
              getRowId={getRowKey}
              columnVisibility={false}
              pageSize={null}
              stickyHeader={false}
              frame={false}
              empty={null}
            />
          </div>
        </div>
      )}
    </div>
  );
}
