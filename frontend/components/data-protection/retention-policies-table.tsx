"use client";

import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { Archive, Save, Loader2, Play } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Switch } from "@/components/ui/switch";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { SettingsSection } from "@/components/settings/settings-section";
import { formatDateTime } from "@/lib/format";
import {
  updateRetentionPolicy,
  evaluateRetentionPolicies,
  executeRetentionPurge,
} from "@/app/(app)/org/[slug]/settings/firm/_tabs/data-protection-actions";
import type { RetentionPolicyExtended } from "@/lib/types/data-protection";
import { entityNounLabel } from "@/components/audit/entity-type-labels";

const ACTIONS = ["ANONYMIZE", "FLAG"] as const;
const FINANCIAL_RECORD_TYPES = ["DOCUMENT", "AUDIT_EVENT"];

interface EditableRow {
  id: string;
  recordType: string;
  triggerEvent: string;
  retentionDays: number;
  action: string;
  active: boolean;
  description: string | null;
  lastEvaluatedAt: string | null;
  saving: boolean;
  error: string | null;
}

function toEditableRow(policy: RetentionPolicyExtended): EditableRow {
  return {
    id: policy.id,
    recordType: policy.recordType,
    triggerEvent: policy.triggerEvent,
    retentionDays: policy.retentionDays,
    action: policy.action,
    active: policy.active,
    description: policy.description,
    lastEvaluatedAt: policy.lastEvaluatedAt,
    saving: false,
    error: null,
  };
}

// Display label for the backend `recordType` enum (wire values unchanged): the legal noun for
// CUSTOMER / PROJECT / INVOICE, other types title-cased from the enum ("AUDIT_EVENT" → "Audit Event").
function formatRecordType(type: string): string {
  const noun = entityNounLabel(type);
  if (noun) return noun;
  return type
    .split("_")
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");
}

// SA-pinned date and time: SSR (UTC) and hydration print the same string.
function formatDate(iso: string | null): string {
  return iso ? formatDateTime(iso) : "Never";
}

const selectClasses =
  "border-input text-foreground dark:bg-input/30 rounded-md border bg-transparent px-2 py-1.5 text-sm transition-[color,box-shadow] outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50";

const getRowId = (row: EditableRow) => row.id;

interface RowActions {
  updateRow: (index: number, updates: Partial<EditableRow>) => void;
  saveRow: (index: number) => Promise<void>;
}

interface RetentionPoliciesTableProps {
  policies: RetentionPolicyExtended[];
  slug: string;
  financialRetentionMonths?: number;
}

export function RetentionPoliciesTable({
  policies,
  slug,
  financialRetentionMonths,
}: RetentionPoliciesTableProps) {
  const [rows, setRows] = useState<EditableRow[]>(policies.map(toEditableRow));
  const [isEvaluating, setIsEvaluating] = useState(false);

  // Sync local rows when the policies prop changes (e.g. after evaluate/purge triggers a server refresh)
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- Re-sync server-fetched policies prop into local editable state; deps only change when server returns new data, not a render loop.
    setRows(policies.map(toEditableRow));
  }, [policies]);
  const [evaluateMessage, setEvaluateMessage] = useState<string | null>(null);
  const [evaluateError, setEvaluateError] = useState<string | null>(null);

  const financialMinDays = financialRetentionMonths ? financialRetentionMonths * 30 : 0;

  function updateRow(index: number, updates: Partial<EditableRow>) {
    setRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...updates } : row)));
  }

  function validateRetentionDays(recordType: string, retentionDays: number): string | null {
    if (
      financialMinDays > 0 &&
      FINANCIAL_RECORD_TYPES.includes(recordType) &&
      retentionDays < financialMinDays
    ) {
      return `Financial records require at least ${financialMinDays} days (${financialRetentionMonths} months).`;
    }
    return null;
  }

  async function saveRow(index: number) {
    const row = rows[index];

    const validationError = validateRetentionDays(row.recordType, row.retentionDays);
    if (validationError) {
      updateRow(index, { error: validationError });
      return;
    }

    updateRow(index, { saving: true, error: null });

    const result = await updateRetentionPolicy(slug, row.id, {
      retentionDays: row.retentionDays,
      action: row.action,
      enabled: row.active,
    });

    if (result.success) {
      updateRow(index, { saving: false, error: null });
    } else {
      updateRow(index, { saving: false, error: result.error ?? "Save failed" });
    }
  }

  async function handleEvaluate() {
    setIsEvaluating(true);
    setEvaluateError(null);
    setEvaluateMessage(null);

    const response = await evaluateRetentionPolicies(slug);
    if (response.success && response.data) {
      const { totalPoliciesEvaluated, entitiesEligibleForPurge } = response.data;
      setEvaluateMessage(
        `Evaluated ${totalPoliciesEvaluated} policies. ${entitiesEligibleForPurge} entities eligible for purge.`
      );
    } else {
      setEvaluateError(response.error ?? "Evaluation failed.");
    }
    setIsEvaluating(false);
  }

  async function handleExecutePurge() {
    if (
      !window.confirm(
        "Are you sure you want to execute the retention purge? This action cannot be undone."
      )
    ) {
      return;
    }

    setIsEvaluating(true);
    setEvaluateError(null);
    setEvaluateMessage(null);

    const response = await executeRetentionPurge(slug);
    if (response.success && response.data) {
      const { totalPurged, totalFailed } = response.data;
      setEvaluateMessage(
        `Purge complete. ${totalPurged} records purged${totalFailed > 0 ? `, ${totalFailed} failed` : ""}.`
      );
    } else {
      setEvaluateError(response.error ?? "Purge failed.");
    }
    setIsEvaluating(false);
  }

  // TanStack renders each column's `cell` as a component, so the column array must keep its
  // identity across edits (a new `cell` function would remount the row's inputs on every
  // keystroke). Cells reach today's index-based handlers through a ref refreshed after each render.
  const actions = useRef<RowActions>({ updateRow, saveRow });
  useLayoutEffect(() => {
    actions.current = { updateRow, saveRow };
  });

  const columns = useMemo(
    () =>
      defineColumns<EditableRow>([
        {
          id: "recordType",
          meta: { label: "Entity Type", cellClassName: "font-medium" },
          cell: ({ row }) => formatRecordType(row.original.recordType),
        },
        {
          id: "retentionDays",
          meta: { label: "Retention Period" },
          cell: ({ row }) => (
            <div className="flex items-center gap-2">
              <Input
                type="number"
                min={1}
                value={row.original.retentionDays}
                onChange={(e) =>
                  actions.current.updateRow(row.index, {
                    retentionDays: Math.max(1, parseInt(e.target.value) || 1),
                    error: null,
                  })
                }
                className="w-24"
                aria-label={`Retention days for ${formatRecordType(row.original.recordType)}`}
              />
              <span className="text-muted-foreground text-sm">days</span>
            </div>
          ),
        },
        {
          id: "action",
          meta: { label: "Action" },
          cell: ({ row }) => (
            <select
              value={row.original.action}
              onChange={(e) => actions.current.updateRow(row.index, { action: e.target.value })}
              className={selectClasses}
              aria-label={`Action for ${formatRecordType(row.original.recordType)}`}
            >
              {ACTIONS.map((a) => (
                <option key={a} value={a}>
                  {a === "ANONYMIZE" ? "Anonymize" : "Flag"}
                </option>
              ))}
            </select>
          ),
        },
        {
          id: "active",
          meta: { label: "Enabled" },
          cell: ({ row }) => (
            <Switch
              checked={row.original.active}
              onCheckedChange={(checked) =>
                actions.current.updateRow(row.index, { active: checked })
              }
              aria-label={`Toggle ${formatRecordType(row.original.recordType)} policy`}
            />
          ),
        },
        {
          id: "lastEvaluatedAt",
          meta: { label: "Last Evaluated", cellClassName: "text-muted-foreground text-sm" },
          cell: ({ row }) => formatDate(row.original.lastEvaluatedAt),
        },
        {
          id: "save",
          enableHiding: false,
          meta: { label: "Save", align: "end" },
          cell: ({ row }) => (
            <div className="flex flex-col items-end">
              <Button
                variant="ghost"
                size="sm"
                disabled={row.original.saving}
                onClick={() => actions.current.saveRow(row.index)}
                aria-label={`Save ${formatRecordType(row.original.recordType)} policy`}
              >
                {row.original.saving ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Save className="size-4" />
                )}
                <span className="sr-only">Save</span>
              </Button>
              {row.original.error && (
                <p className="text-destructive mt-1 max-w-48 text-xs" role="alert">
                  {row.original.error}
                </p>
              )}
            </div>
          ),
        },
      ]),
    []
  );

  return (
    <SettingsSection
      title="Retention Policies"
      description="Configure how long different types of data are retained before automatic anonymisation or deletion."
    >
      <div className="space-y-4">
        <DataTable
          columns={columns}
          data={rows}
          getRowId={getRowId}
          columnVisibility={false}
          pageSize={null}
          stickyHeader={false}
          frame={false}
          empty={
            <EmptyState
              variant="inline"
              icon={Archive}
              title="No retention policies configured."
              description={null}
            />
          }
        />

        <div className="flex items-center gap-3">
          <Button variant="outline" size="sm" disabled={isEvaluating} onClick={handleEvaluate}>
            {isEvaluating ? (
              <Loader2 className="mr-1.5 size-4 animate-spin" />
            ) : (
              <Play className="mr-1.5 size-4" />
            )}
            Run Retention Check
          </Button>
          <Button variant="outline" size="sm" disabled={isEvaluating} onClick={handleExecutePurge}>
            Execute Purge
          </Button>
        </div>

        {evaluateMessage && (
          <p className="text-sm text-teal-600 dark:text-teal-400">{evaluateMessage}</p>
        )}
        {evaluateError && <p className="text-destructive text-sm">{evaluateError}</p>}
      </div>
    </SettingsSection>
  );
}
