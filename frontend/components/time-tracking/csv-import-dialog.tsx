"use client";

import { useCallback, useRef, useState } from "react";
import { Download, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import type { GridTaskRow } from "./weekly-time-grid";

// --- CSV Parser Types ---

export interface ParsedCsvRow {
  date: string;
  taskName: string;
  projectName: string;
  hours: number;
  description: string;
  billable: boolean;
  valid: boolean;
  errors: string[];
  /** Matched task ID from available tasks, or null if unmatched */
  matchedTaskId: string | null;
  matchedTask: GridTaskRow | null;
}

// --- CSV Parser ---

const CSV_TEMPLATE = `date,task_name,project_name,hours,description,billable
2026-03-09,Design wireframes,Project Alpha,2,Worked on mockups,true
2026-03-10,Write API docs,Project Beta,3,,true`;

function parseCsvLine(line: string): string[] {
  const fields: string[] = [];
  let current = "";
  let inQuotes = false;

  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (inQuotes) {
      if (ch === '"') {
        if (i + 1 < line.length && line[i + 1] === '"') {
          current += '"';
          i++; // skip escaped quote
        } else {
          inQuotes = false;
        }
      } else {
        current += ch;
      }
    } else {
      if (ch === '"') {
        inQuotes = true;
      } else if (ch === ",") {
        fields.push(current.trim());
        current = "";
      } else {
        current += ch;
      }
    }
  }
  fields.push(current.trim());
  return fields;
}

export function parseCsv(
  content: string,
  availableTasks: GridTaskRow[],
): ParsedCsvRow[] {
  const lines = content
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0);

  if (lines.length === 0) return [];

  // Validate header row
  const headerLine = lines[0].toLowerCase();
  const headers = parseCsvLine(headerLine);

  // Check that we have expected headers (at least the required ones)
  const hasDateHeader =
    headers.includes("date") || headers[0]?.includes("date");
  if (!hasDateHeader && lines.length > 0) {
    // Try treating first line as data if it looks like a date
    // Otherwise skip it as header
  }

  // Determine start index (skip header if present)
  const datePattern = /^\d{4}-\d{2}-\d{2}$/;
  const startIndex =
    headers.length >= 4 && !datePattern.test(headers[0]) ? 1 : 0;

  const rows: ParsedCsvRow[] = [];

  for (let i = startIndex; i < lines.length; i++) {
    const fields = parseCsvLine(lines[i]);
    const errors: string[] = [];

    // Extract fields by position
    const date = fields[0] ?? "";
    const taskName = fields[1] ?? "";
    const projectName = fields[2] ?? "";
    const hoursStr = fields[3] ?? "";
    const description = fields[4] ?? "";
    const billableStr = (fields[5] ?? "true").toLowerCase();

    // Validate date
    if (!date) {
      errors.push("Date is required");
    } else if (!datePattern.test(date)) {
      errors.push("Date must be YYYY-MM-DD format");
    } else {
      // Validate it's a real date
      const [y, m, d] = date.split("-").map(Number);
      const testDate = new Date(y, m - 1, d);
      if (
        testDate.getFullYear() !== y ||
        testDate.getMonth() !== m - 1 ||
        testDate.getDate() !== d
      ) {
        errors.push("Invalid date");
      }
    }

    // Validate task_name
    if (!taskName) {
      errors.push("Task name is required");
    }

    // Validate project_name
    if (!projectName) {
      errors.push("Project name is required");
    }

    // Validate hours
    const hours = parseFloat(hoursStr);
    if (!hoursStr) {
      errors.push("Hours is required");
    } else if (isNaN(hours) || hours <= 0) {
      errors.push("Hours must be a positive number");
    } else if (hours > 24) {
      errors.push("Hours cannot exceed 24");
    }

    // Parse billable
    const billable = billableStr !== "false" && billableStr !== "0";

    // Match task by name + project (case-insensitive)
    let matchedTask: GridTaskRow | null = null;
    let matchedTaskId: string | null = null;
    if (taskName && projectName) {
      const found = availableTasks.find(
        (t) =>
          t.title.toLowerCase() === taskName.toLowerCase() &&
          t.projectName.toLowerCase() === projectName.toLowerCase(),
      );
      if (found) {
        matchedTask = found;
        matchedTaskId = found.id;
      } else {
        errors.push("No matching task found");
      }
    }

    rows.push({
      date,
      taskName,
      projectName,
      hours: isNaN(hours) ? 0 : hours,
      description,
      billable,
      valid: errors.length === 0,
      errors,
      matchedTaskId,
      matchedTask,
    });
  }

  return rows;
}

// --- CSV Import Dialog Component ---

interface CsvImportDialogProps {
  availableTasks: GridTaskRow[];
  onImport: (
    rows: Array<{
      taskId: string;
      task: GridTaskRow;
      date: string;
      hours: number;
      description: string;
      billable: boolean;
    }>,
  ) => void;
  children: React.ReactNode;
}

export function CsvImportDialog({
  availableTasks,
  onImport,
  children,
}: CsvImportDialogProps) {
  const [open, setOpen] = useState(false);
  const [parsedRows, setParsedRows] = useState<ParsedCsvRow[]>([]);
  const [fileName, setFileName] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFileSelect = useCallback(
    (file: File) => {
      setFileName(file.name);
      const reader = new FileReader();
      reader.onload = (e) => {
        const content = e.target?.result;
        if (typeof content === "string") {
          const rows = parseCsv(content, availableTasks);
          setParsedRows(rows);
        }
      };
      reader.readAsText(file);
    },
    [availableTasks],
  );

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) {
        handleFileSelect(file);
      }
      // Reset so the same file can be re-selected
      e.target.value = "";
    },
    [handleFileSelect],
  );

  function handleDownloadTemplate() {
    const blob = new Blob([CSV_TEMPLATE], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "time-entry-template.csv";
    a.click();
    URL.revokeObjectURL(url);
  }

  function handleImport() {
    const validRows = parsedRows.filter((r) => r.valid && r.matchedTask);
    const importData = validRows.map((r) => ({
      taskId: r.matchedTaskId!,
      task: r.matchedTask!,
      date: r.date,
      hours: r.hours,
      description: r.description,
      billable: r.billable,
    }));
    onImport(importData);
    setOpen(false);
    setParsedRows([]);
    setFileName(null);
  }

  function handleOpenChange(newOpen: boolean) {
    setOpen(newOpen);
    if (!newOpen) {
      setParsedRows([]);
      setFileName(null);
    }
  }

  const validCount = parsedRows.filter((r) => r.valid).length;
  const invalidCount = parsedRows.filter((r) => !r.valid).length;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Import Time Entries from CSV</DialogTitle>
          <DialogDescription>
            Upload a CSV file with your time entries. Download the template for
            the expected format.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Template download + file upload */}
          <div className="flex items-center gap-3">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleDownloadTemplate}
            >
              <Download className="mr-1.5 size-3.5" />
              Download Template
            </Button>
            <div className="flex-1">
              <input
                ref={inputRef}
                type="file"
                accept=".csv"
                onChange={handleInputChange}
                className="hidden"
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => inputRef.current?.click()}
              >
                <Upload className="mr-1.5 size-3.5" />
                {fileName ?? "Choose CSV file"}
              </Button>
            </div>
          </div>

          {/* Preview table */}
          {parsedRows.length > 0 && (
            <div className="space-y-2">
              <div className="flex items-center gap-2 text-sm">
                <span className="text-slate-600 dark:text-slate-400">
                  {parsedRows.length} row{parsedRows.length !== 1 ? "s" : ""}{" "}
                  parsed:
                </span>
                {validCount > 0 && (
                  <span className="text-emerald-600 dark:text-emerald-400">
                    {validCount} valid
                  </span>
                )}
                {invalidCount > 0 && (
                  <span className="text-red-600 dark:text-red-400">
                    {invalidCount} invalid
                  </span>
                )}
              </div>

              <div className="max-h-64 overflow-auto rounded-lg border border-slate-200 dark:border-slate-800">
                <Table>
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      <TableHead className="text-xs">Status</TableHead>
                      <TableHead className="text-xs">Date</TableHead>
                      <TableHead className="text-xs">Task</TableHead>
                      <TableHead className="text-xs">Project</TableHead>
                      <TableHead className="text-xs">Hours</TableHead>
                      <TableHead className="text-xs">Errors</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {parsedRows.map((row, idx) => (
                      <TableRow
                        key={idx}
                        className={cn(
                          row.valid
                            ? "bg-emerald-50/50 dark:bg-emerald-950/20"
                            : "bg-red-50/50 dark:bg-red-950/20",
                        )}
                      >
                        <TableCell className="py-1.5">
                          <span
                            className={cn(
                              "inline-block size-2 rounded-full",
                              row.valid ? "bg-emerald-500" : "bg-red-500",
                            )}
                          />
                        </TableCell>
                        <TableCell className="py-1.5 text-xs">
                          {row.date}
                        </TableCell>
                        <TableCell className="py-1.5 text-xs">
                          {row.taskName}
                        </TableCell>
                        <TableCell className="py-1.5 text-xs">
                          {row.projectName}
                        </TableCell>
                        <TableCell className="py-1.5 text-xs">
                          {row.hours > 0 ? row.hours : "-"}
                        </TableCell>
                        <TableCell className="py-1.5 text-xs text-red-600 dark:text-red-400">
                          {row.errors.join("; ")}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => setOpen(false)}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleImport}
            disabled={validCount === 0}
          >
            Import {validCount > 0 ? `${validCount} entries` : ""}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
