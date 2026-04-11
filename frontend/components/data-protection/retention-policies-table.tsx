"use client";

import { useEffect, useState } from "react";
import { Save, Loader2, Play } from "lucide-react";
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
import {
  updateRetentionPolicy,
  evaluateRetentionPolicies,
  executeRetentionPurge,
} from "@/app/(app)/org/[slug]/settings/data-protection/actions";
import type { RetentionPolicyExtended } from "@/lib/types/data-protection";

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

function formatRecordType(type: string): string {
  return type.replace(/_/g, " ");
}

function formatDate(iso: string | null): string {
  if (!iso) return "Never";
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
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

  const selectClasses =
    "rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100";

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <div className="mb-4">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Retention Policies
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure how long different types of data are retained before automatic anonymisation or
          deletion.
        </p>
      </div>

      <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Entity Type</TableHead>
              <TableHead>Retention Period</TableHead>
              <TableHead>Action</TableHead>
              <TableHead>Enabled</TableHead>
              <TableHead>Last Evaluated</TableHead>
              <TableHead className="text-right">Save</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={6}
                  className="py-8 text-center text-slate-500 dark:text-slate-400"
                >
                  No retention policies configured.
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row, index) => (
                <TableRow key={row.id}>
                  <TableCell className="font-medium">{formatRecordType(row.recordType)}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Input
                        type="number"
                        min={1}
                        value={row.retentionDays}
                        onChange={(e) =>
                          updateRow(index, {
                            retentionDays: Math.max(1, parseInt(e.target.value) || 1),
                            error: null,
                          })
                        }
                        className="w-24"
                        aria-label={`Retention days for ${row.recordType}`}
                      />
                      <span className="text-sm text-slate-500 dark:text-slate-400">days</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <select
                      value={row.action}
                      onChange={(e) => updateRow(index, { action: e.target.value })}
                      className={selectClasses}
                      aria-label={`Action for ${row.recordType}`}
                    >
                      {ACTIONS.map((a) => (
                        <option key={a} value={a}>
                          {a === "ANONYMIZE" ? "Anonymize" : "Flag"}
                        </option>
                      ))}
                    </select>
                  </TableCell>
                  <TableCell>
                    <Switch
                      checked={row.active}
                      onCheckedChange={(checked) => updateRow(index, { active: checked })}
                      aria-label={`Toggle ${row.recordType} policy`}
                    />
                  </TableCell>
                  <TableCell className="text-sm text-slate-500 dark:text-slate-400">
                    {formatDate(row.lastEvaluatedAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex flex-col items-end">
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={row.saving}
                        onClick={() => saveRow(index)}
                        aria-label={`Save ${row.recordType} policy`}
                      >
                        {row.saving ? (
                          <Loader2 className="size-4 animate-spin" />
                        ) : (
                          <Save className="size-4" />
                        )}
                        <span className="sr-only">Save</span>
                      </Button>
                      {row.error && (
                        <p
                          className="mt-1 max-w-48 text-xs text-red-600 dark:text-red-400"
                          role="alert"
                        >
                          {row.error}
                        </p>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <div className="mt-4 flex items-center gap-3">
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
        <p className="mt-3 text-sm text-teal-600 dark:text-teal-400">{evaluateMessage}</p>
      )}
      {evaluateError && (
        <p className="mt-3 text-sm text-red-600 dark:text-red-400">{evaluateError}</p>
      )}
    </div>
  );
}
