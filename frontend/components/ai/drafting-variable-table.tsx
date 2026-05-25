"use client";

import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { AlertTriangle } from "lucide-react";
import type { VariableFill } from "@/lib/api/ai";

interface DraftingVariableTableProps {
  variableFills: VariableFill[];
  onChange: (fills: VariableFill[]) => void;
}

const CONFIDENCE_ORDER: Record<string, number> = {
  UNDETERMINED: 0,
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
};

function getConfidenceBadgeVariant(confidence: string) {
  switch (confidence) {
    case "HIGH":
      return "success" as const;
    case "MEDIUM":
      return "warning" as const;
    case "LOW":
    case "UNDETERMINED":
      return "destructive" as const;
    default:
      return "default" as const;
  }
}

function sortByConfidence(fills: VariableFill[]): VariableFill[] {
  return [...fills].sort(
    (a, b) => (CONFIDENCE_ORDER[a.confidence] ?? 4) - (CONFIDENCE_ORDER[b.confidence] ?? 4)
  );
}

export function DraftingVariableTable({ variableFills, onChange }: DraftingVariableTableProps) {
  const sorted = sortByConfidence(variableFills);

  function handleValueChange(variableName: string, newValue: string) {
    const updated = variableFills.map((fill) =>
      fill.variableName === variableName ? { ...fill, value: newValue || null } : fill
    );
    onChange(updated);
  }

  if (sorted.length === 0) {
    return (
      <p className="text-sm text-slate-500 dark:text-slate-400">
        No variable fills returned by the AI.
      </p>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 dark:border-slate-800">
      <Table>
        <TableHeader>
          <TableRow className="border-slate-200 hover:bg-transparent dark:border-slate-800">
            <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Variable
            </TableHead>
            <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Value
            </TableHead>
            <TableHead className="hidden text-xs tracking-wide text-slate-600 uppercase sm:table-cell dark:text-slate-400">
              Source
            </TableHead>
            <TableHead className="text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Confidence
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((fill, i) => (
            <TableRow
              key={fill.variableName + "-" + i}
              className="border-slate-100 dark:border-slate-800/50"
            >
              <TableCell>
                <div className="flex items-center gap-1.5">
                  <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
                    {fill.variableName}
                  </span>
                  {fill.flag && (
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <AlertTriangle className="size-3.5 text-amber-500" />
                        </TooltipTrigger>
                        <TooltipContent>
                          <p>{fill.flag}</p>
                        </TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  )}
                </div>
              </TableCell>
              <TableCell>
                <Input
                  value={fill.value ?? ""}
                  onChange={(e) => handleValueChange(fill.variableName, e.target.value)}
                  placeholder={
                    fill.confidence === "UNDETERMINED" ? "Enter value..." : "Override value..."
                  }
                  className="h-8 text-sm"
                />
              </TableCell>
              <TableCell className="hidden sm:table-cell">
                <span className="text-sm text-slate-600 dark:text-slate-400">{fill.source}</span>
              </TableCell>
              <TableCell>
                <Badge variant={getConfidenceBadgeVariant(fill.confidence)}>
                  {fill.confidence}
                </Badge>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
