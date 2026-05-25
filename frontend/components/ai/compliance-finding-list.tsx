"use client";

import { useCallback, useEffect, useState, useTransition } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ChevronLeft, ChevronRight, Filter } from "lucide-react";
import { ComplianceFindingDetail } from "@/components/ai/compliance-finding-detail";
import { fetchAuditFindingsAction } from "@/app/(app)/org/[slug]/compliance/actions";
import type {
  ComplianceAuditFindingResponse,
  FindingFilters,
  PaginatedResponse,
} from "@/lib/api/compliance-audit";

interface ComplianceFindingListProps {
  reportId: string;
  slug: string;
}

const SEVERITY_OPTIONS = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"];
const CATEGORY_OPTIONS = [
  "FICA_CDD",
  "POPIA",
  "TRUST_ACCOUNTING",
  "PRESCRIPTION",
  "RECORD_RETENTION",
];
const STATUS_OPTIONS = ["OPEN", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED", "FALSE_POSITIVE"];

function getSeverityBadgeVariant(
  severity: string
): "destructive" | "warning" | "success" | "neutral" {
  switch (severity.toUpperCase()) {
    case "CRITICAL":
      return "destructive";
    case "HIGH":
      return "destructive";
    case "MEDIUM":
      return "warning";
    case "LOW":
      return "success";
    case "INFO":
      return "neutral";
    default:
      return "neutral";
  }
}

function getStatusBadgeVariant(status: string): "destructive" | "warning" | "success" | "neutral" {
  switch (status.toUpperCase()) {
    case "OPEN":
      return "destructive";
    case "ACKNOWLEDGED":
      return "warning";
    case "IN_PROGRESS":
      return "warning";
    case "RESOLVED":
      return "success";
    case "FALSE_POSITIVE":
      return "neutral";
    default:
      return "neutral";
  }
}

function formatCategoryName(category: string): string {
  return category
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

function formatStatusName(status: string): string {
  return status
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export function ComplianceFindingList({ reportId, slug }: ComplianceFindingListProps) {
  const [findings, setFindings] =
    useState<PaginatedResponse<ComplianceAuditFindingResponse> | null>(null);
  const [filters, setFilters] = useState<FindingFilters>({});
  const [page, setPage] = useState(0);
  const [selectedFinding, setSelectedFinding] = useState<ComplianceAuditFindingResponse | null>(
    null
  );
  const [isPending, startTransition] = useTransition();

  const loadFindings = useCallback(() => {
    startTransition(async () => {
      const result = await fetchAuditFindingsAction(slug, reportId, filters, page, 20);
      if (result.success && result.data) {
        setFindings(result.data);
      }
    });
  }, [slug, reportId, filters, page]);

  useEffect(() => {
    loadFindings();
  }, [loadFindings]);

  function handleFilterChange(filterKey: keyof FindingFilters, value: string) {
    setFilters((prev) => ({
      ...prev,
      [filterKey]: value === "ALL" ? undefined : [value],
    }));
    setPage(0);
  }

  function handleStatusChange() {
    setSelectedFinding(null);
    loadFindings();
  }

  if (!findings) {
    return (
      <div className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
        {isPending ? "Loading findings..." : "No findings data available."}
      </div>
    );
  }

  if (findings.content.length === 0 && page === 0) {
    return (
      <div className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
        No findings for this audit report.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold text-slate-950 dark:text-slate-50">
          Findings ({findings.page.totalElements})
        </h4>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-2">
        <Filter className="size-3.5 text-slate-400" />
        <Select onValueChange={(v) => handleFilterChange("severity", v)} defaultValue="ALL">
          <SelectTrigger className="h-8 w-[140px]">
            <SelectValue placeholder="Severity" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Severities</SelectItem>
            {SEVERITY_OPTIONS.map((s) => (
              <SelectItem key={s} value={s}>
                {s}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select onValueChange={(v) => handleFilterChange("category", v)} defaultValue="ALL">
          <SelectTrigger className="h-8 w-[180px]">
            <SelectValue placeholder="Category" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Categories</SelectItem>
            {CATEGORY_OPTIONS.map((c) => (
              <SelectItem key={c} value={c}>
                {formatCategoryName(c)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select onValueChange={(v) => handleFilterChange("status", v)} defaultValue="ALL">
          <SelectTrigger className="h-8 w-[160px]">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Statuses</SelectItem>
            {STATUS_OPTIONS.map((s) => (
              <SelectItem key={s} value={s}>
                {formatStatusName(s)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Table */}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Severity</TableHead>
            <TableHead>Category</TableHead>
            <TableHead>Finding ID</TableHead>
            <TableHead>Title</TableHead>
            <TableHead>Entity</TableHead>
            <TableHead>Status</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {findings.content.map((finding) => (
            <TableRow
              key={finding.id}
              className="cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900"
              onClick={() => setSelectedFinding(finding)}
            >
              <TableCell>
                <Badge variant={getSeverityBadgeVariant(finding.severity)}>
                  {finding.severity}
                </Badge>
              </TableCell>
              <TableCell>
                <Badge variant="neutral">{formatCategoryName(finding.category)}</Badge>
              </TableCell>
              <TableCell className="font-mono text-xs">{finding.findingId}</TableCell>
              <TableCell className="max-w-[300px] truncate">{finding.title}</TableCell>
              <TableCell>
                {finding.entityType && finding.entityId ? (
                  <a
                    href={
                      finding.entityType === "CUSTOMER"
                        ? `/org/${slug}/customers/${finding.entityId}`
                        : `/org/${slug}/projects/${finding.entityId}`
                    }
                    className="text-teal-600 underline hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                    onClick={(e) => e.stopPropagation()}
                  >
                    {finding.entityType}
                  </a>
                ) : (
                  <span className="text-slate-400">-</span>
                )}
              </TableCell>
              <TableCell>
                <Badge variant={getStatusBadgeVariant(finding.status)}>
                  {formatStatusName(finding.status)}
                </Badge>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {/* Pagination */}
      {findings.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Page {findings.page.number + 1} of {findings.page.totalPages} (
            {findings.page.totalElements} total)
          </p>
          <div className="flex items-center gap-1">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0 || isPending}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft className="size-3.5" />
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= findings.page.totalPages - 1 || isPending}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
              <ChevronRight className="size-3.5" />
            </Button>
          </div>
        </div>
      )}

      {/* Finding Detail Dialog */}
      {selectedFinding && (
        <ComplianceFindingDetail
          finding={selectedFinding}
          reportId={reportId}
          slug={slug}
          open={!!selectedFinding}
          onOpenChange={(open) => {
            if (!open) setSelectedFinding(null);
          }}
          onStatusChange={handleStatusChange}
        />
      )}
    </div>
  );
}
