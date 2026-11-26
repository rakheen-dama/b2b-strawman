"use client";

import { useLayoutEffect, useMemo, useRef, useState } from "react";
import { Archive, Plus, Save, Trash2, Loader2, Play } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Switch } from "@/components/ui/switch";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { SettingsSection } from "@/components/settings/settings-section";
import { RetentionCheckResults } from "@/components/compliance/RetentionCheckResults";
import {
  createRetentionPolicy,
  updateRetentionPolicy,
  deleteRetentionPolicy,
  runRetentionCheck,
  executePurge,
} from "@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions";
import type { RetentionPolicy, RetentionCheckResult } from "@/lib/types";
import { entityNounLabel } from "@/components/audit/entity-type-labels";

const RECORD_TYPES = ["CUSTOMER", "AUDIT_EVENT", "DOCUMENT", "COMMENT"] as const;
const TRIGGER_EVENTS = ["CUSTOMER_OFFBOARDED", "RECORD_CREATED"] as const;
const ACTIONS = ["FLAG", "ANONYMIZE"] as const;

/**
 * Display label for a record-type / trigger-event enum (the option `value` stays the wire enum):
 * entity nouns use the legal word (CUSTOMER → Client), other words are title-cased —
 * "CUSTOMER_OFFBOARDED" → "Client Offboarded", "AUDIT_EVENT" → "Audit Event".
 */
function formatEnumLabel(value: string): string {
  return value
    .split("_")
    .filter(Boolean)
    .map((word) => entityNounLabel(word) ?? word.charAt(0) + word.slice(1).toLowerCase())
    .join(" ");
}

interface EditableRow {
  /** Stable client row id for DataTable's `getRowId` (665A DA-19); never sent to the server. */
  key: string;
  id: string | null; // null = new row
  recordType: string;
  triggerEvent: string;
  retentionDays: number;
  action: string;
  active: boolean;
  saving: boolean;
  error: string | null;
}

function toEditableRow(policy: RetentionPolicy): EditableRow {
  return {
    key: policy.id,
    id: policy.id,
    recordType: policy.recordType,
    triggerEvent: policy.triggerEvent,
    retentionDays: policy.retentionDays,
    action: policy.action,
    active: policy.active,
    saving: false,
    error: null,
  };
}

/** Client-only ids for unsaved rows (a module counter: `crypto.randomUUID` needs a secure context). */
let newRowSeq = 0;

function newEmptyRow(): EditableRow {
  newRowSeq += 1;
  return {
    key: `new-${newRowSeq}`,
    id: null,
    recordType: "CUSTOMER",
    triggerEvent: "CUSTOMER_OFFBOARDED",
    retentionDays: 365,
    action: "FLAG",
    active: true,
    saving: false,
    error: null,
  };
}

const getRowKey = (row: EditableRow) => row.key;

const selectClasses =
  "rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-500 dark:border-slate-700 dark:bg-input/30 dark:text-slate-100";

interface RowActions {
  updateRow: (index: number, updates: Partial<EditableRow>) => void;
  saveRow: (index: number) => void;
  removeRow: (index: number) => void;
}

interface RetentionPolicyTableProps {
  policies: RetentionPolicy[];
  slug: string;
}

export function RetentionPolicyTable({ policies, slug }: RetentionPolicyTableProps) {
  const [rows, setRows] = useState<EditableRow[]>(policies.map(toEditableRow));
  const [checkResult, setCheckResult] = useState<RetentionCheckResult | null>(null);
  const [isChecking, setIsChecking] = useState(false);
  const [checkError, setCheckError] = useState<string | null>(null);

  function updateRow(index: number, updates: Partial<EditableRow>) {
    setRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...updates } : row)));
  }

  function addRow() {
    setRows((prev) => [...prev, newEmptyRow()]);
  }

  async function saveRow(index: number) {
    const row = rows[index];
    updateRow(index, { saving: true, error: null });

    let result;
    if (row.id) {
      result = await updateRetentionPolicy(slug, row.id, {
        retentionDays: row.retentionDays,
        action: row.action,
        active: row.active,
      });
    } else {
      result = await createRetentionPolicy(slug, {
        recordType: row.recordType,
        retentionDays: row.retentionDays,
        triggerEvent: row.triggerEvent,
        action: row.action,
      });
    }

    if (result.success) {
      updateRow(index, { saving: false, error: null });
    } else {
      updateRow(index, { saving: false, error: result.error ?? "Save failed" });
    }
  }

  async function removeRow(index: number) {
    const row = rows[index];
    if (row.id) {
      if (!window.confirm("Delete this retention policy?")) return;
      updateRow(index, { saving: true, error: null });
      const result = await deleteRetentionPolicy(slug, row.id);
      if (!result.success) {
        updateRow(index, { saving: false, error: result.error ?? "Delete failed" });
        return;
      }
    }
    setRows((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleRunCheck() {
    setIsChecking(true);
    setCheckError(null);
    setCheckResult(null);

    const response = await runRetentionCheck();
    if (response.success && response.result) {
      setCheckResult(response.result);
    } else {
      setCheckError(response.error ?? "Retention check failed.");
    }
    setIsChecking(false);
  }

  async function handlePurge(recordType: string, recordIds: string[]) {
    const response = await executePurge(slug, recordType, recordIds);
    if (!response.success) {
      setCheckError(response.error ?? "Purge failed.");
    } else {
      // Re-run check after purge to refresh results
      await handleRunCheck();
    }
  }

  // TanStack renders each column's `cell` as a component, so the column array must keep its
  // identity across edits (a new `cell` function would remount the row's inputs on every
  // keystroke). Cells reach today's index-based handlers through a ref refreshed after each render.
  const actions = useRef<RowActions>({ updateRow, saveRow, removeRow });
  useLayoutEffect(() => {
    actions.current = { updateRow, saveRow, removeRow };
  });

  const columns = useMemo(
    () =>
      defineColumns<EditableRow>([
        {
          id: "recordType",
          meta: { label: "Record Type" },
          cell: ({ row }) => (
            <select
              value={row.original.recordType}
              onChange={(e) => actions.current.updateRow(row.index, { recordType: e.target.value })}
              disabled={!!row.original.id}
              className={selectClasses}
            >
              {RECORD_TYPES.map((rt) => (
                <option key={rt} value={rt}>
                  {formatEnumLabel(rt)}
                </option>
              ))}
            </select>
          ),
        },
        {
          id: "triggerEvent",
          meta: { label: "Trigger Event" },
          cell: ({ row }) => (
            <select
              value={row.original.triggerEvent}
              onChange={(e) =>
                actions.current.updateRow(row.index, { triggerEvent: e.target.value })
              }
              disabled={!!row.original.id}
              className={selectClasses}
            >
              {TRIGGER_EVENTS.map((te) => (
                <option key={te} value={te}>
                  {formatEnumLabel(te)}
                </option>
              ))}
            </select>
          ),
        },
        {
          id: "retentionDays",
          meta: { label: "Retention Days" },
          cell: ({ row }) => (
            <Input
              type="number"
              min={1}
              value={row.original.retentionDays}
              onChange={(e) =>
                actions.current.updateRow(row.index, {
                  retentionDays: Math.max(1, parseInt(e.target.value) || 1),
                })
              }
              className="w-24"
            />
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
            >
              {ACTIONS.map((a) => (
                <option key={a} value={a}>
                  {a}
                </option>
              ))}
            </select>
          ),
        },
        {
          id: "active",
          meta: { label: "Active" },
          cell: ({ row }) => (
            <Switch
              checked={row.original.active}
              onCheckedChange={(checked) =>
                actions.current.updateRow(row.index, { active: checked })
              }
              aria-label="Toggle active"
            />
          ),
        },
        {
          id: "actions",
          enableHiding: false,
          meta: { label: "Actions", align: "end" },
          cell: ({ row }) => (
            <>
              <div className="flex items-center justify-end gap-1">
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={row.original.saving}
                  onClick={() => actions.current.saveRow(row.index)}
                >
                  {row.original.saving ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <Save className="size-4" />
                  )}
                  <span className="sr-only">Save</span>
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={row.original.saving}
                  onClick={() => actions.current.removeRow(row.index)}
                  className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                >
                  <Trash2 className="size-4" />
                  <span className="sr-only">Delete</span>
                </Button>
              </div>
              {row.original.error && (
                <p className="text-destructive mt-1 text-xs">{row.original.error}</p>
              )}
            </>
          ),
        },
      ]),
    []
  );

  return (
    <SettingsSection
      title="Retention Policies"
      actions={
        <Button size="sm" onClick={addRow}>
          <Plus className="mr-1.5 size-4" />
          Add Row
        </Button>
      }
    >
      <div className="space-y-4">
        <DataTable
          columns={columns}
          data={rows}
          getRowId={getRowKey}
          columnVisibility={false}
          pageSize={null}
          stickyHeader={false}
          frame={false}
          empty={
            <EmptyState
              variant="inline"
              icon={Archive}
              title="No retention policies configured."
              description="Click “Add Row” to create one."
            />
          }
        />

        <div className="flex items-center gap-3">
          <Button variant="soft" size="sm" disabled={isChecking} onClick={handleRunCheck}>
            {isChecking ? (
              <Loader2 className="mr-1.5 size-4 animate-spin" />
            ) : (
              <Play className="mr-1.5 size-4" />
            )}
            Run Retention Check
          </Button>
          {checkError && <p className="text-destructive text-sm">{checkError}</p>}
        </div>

        {checkResult && <RetentionCheckResults result={checkResult} onPurge={handlePurge} />}
      </div>
    </SettingsSection>
  );
}
