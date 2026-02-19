"use client";

import { useState } from "react";
import { Plus, Save, Trash2, Loader2, Play } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { RetentionCheckResults } from "@/components/compliance/RetentionCheckResults";
import {
  createRetentionPolicy,
  updateRetentionPolicy,
  deleteRetentionPolicy,
  runRetentionCheck,
  executePurge,
} from "@/app/(app)/org/[slug]/settings/compliance/actions";
import type { RetentionPolicy, RetentionCheckResult } from "@/lib/types";

const RECORD_TYPES = ["CUSTOMER", "AUDIT_EVENT", "DOCUMENT", "COMMENT"] as const;
const TRIGGER_EVENTS = ["CUSTOMER_OFFBOARDED", "RECORD_CREATED"] as const;
const ACTIONS = ["FLAG", "ANONYMIZE"] as const;

interface EditableRow {
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

function newEmptyRow(): EditableRow {
  return {
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

  const selectClasses =
    "rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100";

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Retention Policies
        </h2>
        <Button size="sm" onClick={addRow}>
          <Plus className="mr-1.5 size-4" />
          Add Row
        </Button>
      </div>

      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Record Type</TableHead>
              <TableHead>Trigger Event</TableHead>
              <TableHead>Retention Days</TableHead>
              <TableHead>Action</TableHead>
              <TableHead>Active</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-slate-500 dark:text-slate-400">
                  No retention policies configured. Click &ldquo;Add Row&rdquo; to create one.
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row, index) => (
                <TableRow key={row.id ?? `new-${index}`}>
                  <TableCell>
                    <select
                      value={row.recordType}
                      onChange={(e) => updateRow(index, { recordType: e.target.value })}
                      disabled={!!row.id}
                      className={selectClasses}
                    >
                      {RECORD_TYPES.map((rt) => (
                        <option key={rt} value={rt}>
                          {rt.replace("_", " ")}
                        </option>
                      ))}
                    </select>
                  </TableCell>
                  <TableCell>
                    <select
                      value={row.triggerEvent}
                      onChange={(e) => updateRow(index, { triggerEvent: e.target.value })}
                      disabled={!!row.id}
                      className={selectClasses}
                    >
                      {TRIGGER_EVENTS.map((te) => (
                        <option key={te} value={te}>
                          {te.replace(/_/g, " ")}
                        </option>
                      ))}
                    </select>
                  </TableCell>
                  <TableCell>
                    <Input
                      type="number"
                      min={1}
                      value={row.retentionDays}
                      onChange={(e) =>
                        updateRow(index, { retentionDays: parseInt(e.target.value) || 0 })
                      }
                      className="w-24"
                    />
                  </TableCell>
                  <TableCell>
                    <select
                      value={row.action}
                      onChange={(e) => updateRow(index, { action: e.target.value })}
                      className={selectClasses}
                    >
                      {ACTIONS.map((a) => (
                        <option key={a} value={a}>
                          {a}
                        </option>
                      ))}
                    </select>
                  </TableCell>
                  <TableCell>
                    <Switch
                      checked={row.active}
                      onCheckedChange={(checked) => updateRow(index, { active: checked })}
                      aria-label="Toggle active"
                    />
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={row.saving}
                        onClick={() => saveRow(index)}
                      >
                        {row.saving ? (
                          <Loader2 className="size-4 animate-spin" />
                        ) : (
                          <Save className="size-4" />
                        )}
                        <span className="sr-only">Save</span>
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={row.saving}
                        onClick={() => removeRow(index)}
                        className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
                      >
                        <Trash2 className="size-4" />
                        <span className="sr-only">Delete</span>
                      </Button>
                    </div>
                    {row.error && (
                      <p className="mt-1 text-xs text-red-600 dark:text-red-400">{row.error}</p>
                    )}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center gap-3">
        <Button variant="soft" size="sm" disabled={isChecking} onClick={handleRunCheck}>
          {isChecking ? (
            <Loader2 className="mr-1.5 size-4 animate-spin" />
          ) : (
            <Play className="mr-1.5 size-4" />
          )}
          Run Retention Check
        </Button>
        {checkError && (
          <p className="text-sm text-red-600 dark:text-red-400">{checkError}</p>
        )}
      </div>

      {checkResult && <RetentionCheckResults result={checkResult} onPurge={handlePurge} />}
    </div>
  );
}
