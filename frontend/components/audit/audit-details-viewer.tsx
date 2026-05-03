"use client";

import { useState } from "react";
import { ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";

type DeltaShape = "field-from-to" | "before-after" | "freeform";

function detectShape(details: Record<string, unknown> | null): DeltaShape {
  if (!details || Object.keys(details).length === 0) return "freeform";
  const before = (details as Record<string, unknown>).before;
  const after = (details as Record<string, unknown>).after;
  const changedFields = (details as Record<string, unknown>).changedFields;
  if (
    typeof before === "object" &&
    before !== null &&
    typeof after === "object" &&
    after !== null &&
    Array.isArray(changedFields)
  ) {
    return "before-after";
  }
  const values = Object.values(details);
  const allFromTo =
    values.length > 0 &&
    values.every(
      (v) => v !== null && typeof v === "object" && "from" in (v as object) && "to" in (v as object)
    );
  if (allFromTo) return "field-from-to";
  return "freeform";
}

// Defensive caps to prevent adversarial / bloated `details` payloads from
// freezing the browser. Keep these conservative — the audit details viewer
// is for at-a-glance inspection, not deep forensics.
const MAX_DEPTH = 5;
const MAX_ENTRIES = 100;
const MAX_STRING_LEN = 2048;

function renderScalar(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "string") {
    if (value === "") return '""';
    return value.length > MAX_STRING_LEN ? value.slice(0, MAX_STRING_LEN) + "…" : value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  const json = JSON.stringify(value);
  if (json && json.length > MAX_STRING_LEN) {
    return json.slice(0, MAX_STRING_LEN) + "…";
  }
  return json;
}

interface JsonNodeProps {
  value: unknown;
  depth?: number;
}

function JsonNode({ value, depth = 0 }: JsonNodeProps) {
  // Auto-collapse nodes deeper than MAX_DEPTH; user can still expand them.
  const [expanded, setExpanded] = useState(depth < 1 && depth < MAX_DEPTH);

  if (value === null) {
    return <span className="text-slate-500">null</span>;
  }
  if (typeof value !== "object") {
    return <span className="text-slate-700 dark:text-slate-300">{renderScalar(value)}</span>;
  }
  const isArray = Array.isArray(value);
  const allEntries = isArray
    ? (value as unknown[]).map((v, i) => [String(i), v] as const)
    : Object.entries(value as Record<string, unknown>);

  if (allEntries.length === 0) {
    return <span className="text-slate-500">{isArray ? "[]" : "{}"}</span>;
  }

  const truncated = allEntries.length > MAX_ENTRIES;
  const entries = truncated ? allEntries.slice(0, MAX_ENTRIES) : allEntries;
  const overLimit = depth >= MAX_DEPTH;

  return (
    <div className="font-mono text-[11px]">
      <button
        type="button"
        onClick={() => setExpanded((e) => !e)}
        aria-expanded={expanded}
        aria-label={expanded ? "Collapse node" : "Expand node"}
        className="inline-flex items-center gap-1 text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
      >
        <ChevronRight className={cn("size-3 transition-transform", expanded && "rotate-90")} />
        <span>
          {isArray ? `Array(${allEntries.length})` : `Object(${allEntries.length})`}
          {overLimit && !expanded && " · deep"}
        </span>
      </button>
      {expanded && (
        <ul className="mt-1 ml-4 space-y-0.5 border-l border-slate-200 pl-2 dark:border-slate-700">
          {entries.map(([key, v]) => (
            <li key={key} className="flex gap-2">
              <span className="text-teal-700 dark:text-teal-400">{key}:</span>
              <JsonNode value={v} depth={depth + 1} />
            </li>
          ))}
          {truncated && (
            <li className="text-slate-500 italic">…{allEntries.length - MAX_ENTRIES} more</li>
          )}
        </ul>
      )}
    </div>
  );
}

interface DiffRowProps {
  field: string;
  from: unknown;
  to: unknown;
}

function DiffRow({ field, from, to }: DiffRowProps) {
  return (
    <div
      data-testid="diff-row"
      data-field={field}
      className="grid grid-cols-[140px_1fr_1fr] gap-3 border-b border-slate-100 py-1.5 text-xs last:border-b-0 dark:border-slate-800"
    >
      <span className="font-medium text-slate-700 dark:text-slate-300">{field}</span>
      <span className="rounded bg-red-50 px-2 py-1 font-mono text-[11px] text-red-800 line-through dark:bg-red-950/40 dark:text-red-300">
        {renderScalar(from)}
      </span>
      <span className="rounded bg-green-50 px-2 py-1 font-mono text-[11px] text-green-800 dark:bg-green-950/40 dark:text-green-300">
        {renderScalar(to)}
      </span>
    </div>
  );
}

export interface AuditDetailsViewerProps {
  details: Record<string, unknown> | null;
}

export function AuditDetailsViewer({ details }: AuditDetailsViewerProps) {
  const shape = detectShape(details);

  if (shape === "freeform" && (!details || Object.keys(details).length === 0)) {
    return (
      <p className="text-xs text-slate-500" data-testid="audit-details-empty">
        No structured details for this event.
      </p>
    );
  }

  if (shape === "field-from-to" && details) {
    return (
      <div data-testid="audit-details-diff" className="space-y-0">
        <div className="grid grid-cols-[140px_1fr_1fr] gap-3 pb-1 text-[10px] font-semibold tracking-wider text-slate-500 uppercase">
          <span>Field</span>
          <span>From</span>
          <span>To</span>
        </div>
        {Object.entries(details).map(([field, value]) => {
          const v = value as { from: unknown; to: unknown };
          return <DiffRow key={field} field={field} from={v.from} to={v.to} />;
        })}
      </div>
    );
  }

  if (shape === "before-after" && details) {
    const before = details.before as Record<string, unknown>;
    const after = details.after as Record<string, unknown>;
    const changedFields = details.changedFields as string[];
    return (
      <div data-testid="audit-details-diff" className="space-y-0">
        <div className="grid grid-cols-[140px_1fr_1fr] gap-3 pb-1 text-[10px] font-semibold tracking-wider text-slate-500 uppercase">
          <span>Field</span>
          <span>Before</span>
          <span>After</span>
        </div>
        {changedFields.map((field) => (
          <DiffRow key={field} field={field} from={before?.[field]} to={after?.[field]} />
        ))}
      </div>
    );
  }

  return (
    <div data-testid="audit-details-tree" className="rounded bg-slate-50 p-2 dark:bg-slate-900">
      <JsonNode value={details} />
    </div>
  );
}
