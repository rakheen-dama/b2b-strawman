"use client";

import { useState, useMemo } from "react";
import type { Document, DocumentScope, DocumentVisibility } from "@/lib/types";
import { DataTable } from "@/components/ui/data-table";
import { DataTableToolbar } from "@/components/ui/data-table-toolbar";
import { EmptyState } from "@/components/empty-state";
import { getDocumentColumns, getFileIcon } from "@/components/documents/document-columns";
import { DocumentUploadDialog } from "@/components/documents/document-upload-dialog";
import { StatusBadge } from "@/components/ui/status-badge";
import { formatDate, formatFileSize } from "@/lib/format";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  FileText,
  LayoutGrid,
  LayoutList,
  Lock,
  Globe,
} from "lucide-react";

interface DocumentListProps {
  documents: Document[];
  slug: string;
}

export function DocumentList({ documents, slug }: DocumentListProps) {
  const [search, setSearch] = useState("");
  const [scopeFilter, setScopeFilter] = useState<string>("all");
  const [visibilityFilter, setVisibilityFilter] = useState<string>("all");
  const [viewMode, setViewMode] = useState<"table" | "grid">("table");

  const columns = useMemo(() => getDocumentColumns(), []);

  const filtered = useMemo(() => {
    return documents.filter((doc) => {
      if (
        search &&
        !doc.fileName.toLowerCase().includes(search.toLowerCase())
      ) {
        return false;
      }
      if (scopeFilter !== "all" && doc.scope !== scopeFilter) {
        return false;
      }
      if (visibilityFilter !== "all" && doc.visibility !== visibilityFilter) {
        return false;
      }
      return true;
    });
  }, [documents, search, scopeFilter, visibilityFilter]);

  const emptyState = (
    <EmptyState
      icon={FileText}
      title="No documents yet"
      description="Upload your first document to get started."
      action={<DocumentUploadDialog slug={slug} />}
    />
  );

  // If there are no documents at all, show the empty state
  if (documents.length === 0) {
    return emptyState;
  }

  return (
    <div className="space-y-4">
      <DataTableToolbar
        searchPlaceholder="Search documents..."
        searchValue={search}
        onSearchChange={setSearch}
        filters={
          <>
            <Select value={scopeFilter} onValueChange={setScopeFilter}>
              <SelectTrigger className="w-[130px] h-8 text-xs">
                <SelectValue placeholder="Scope" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Scopes</SelectItem>
                <SelectItem value="ORG">Org</SelectItem>
                <SelectItem value="PROJECT">Project</SelectItem>
                <SelectItem value="CUSTOMER">Customer</SelectItem>
              </SelectContent>
            </Select>

            <Select
              value={visibilityFilter}
              onValueChange={setVisibilityFilter}
            >
              <SelectTrigger className="w-[140px] h-8 text-xs">
                <SelectValue placeholder="Visibility" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Visibility</SelectItem>
                <SelectItem value="INTERNAL">Internal</SelectItem>
                <SelectItem value="SHARED">Shared</SelectItem>
              </SelectContent>
            </Select>
          </>
        }
        actions={
          <div className="flex items-center gap-2">
            <div className="flex items-center rounded-md border border-slate-200">
              <Button
                variant={viewMode === "table" ? "secondary" : "ghost"}
                size="icon-xs"
                onClick={() => setViewMode("table")}
                aria-label="Table view"
                className="rounded-r-none"
              >
                <LayoutList className="size-3.5" />
              </Button>
              <Button
                variant={viewMode === "grid" ? "secondary" : "ghost"}
                size="icon-xs"
                onClick={() => setViewMode("grid")}
                aria-label="Grid view"
                className="rounded-l-none"
              >
                <LayoutGrid className="size-3.5" />
              </Button>
            </div>
            <DocumentUploadDialog slug={slug} />
          </div>
        }
      />

      {filtered.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-center">
          <FileText className="size-12 text-slate-300" />
          <h3 className="mt-4 font-display text-lg text-slate-900">
            No matching documents
          </h3>
          <p className="mt-1 text-sm text-slate-500">
            Try adjusting your filters or search query.
          </p>
        </div>
      ) : viewMode === "table" ? (
        <DataTable columns={columns} data={filtered} />
      ) : (
        <DocumentCardGrid documents={filtered} />
      )}
    </div>
  );
}

// --- Card Grid View ---

function DocumentCardGrid({ documents }: { documents: Document[] }) {
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {documents.map((doc) => {
        const Icon = getFileIcon(doc.contentType);
        const isShared = doc.visibility === "SHARED";
        return (
          <div
            key={doc.id}
            className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition-colors hover:bg-slate-50"
          >
            <div className="flex items-start gap-3">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-slate-100">
                <Icon className="size-5 text-slate-500" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-900">
                  {doc.fileName}
                </p>
                <p className="text-xs text-slate-500">
                  {formatFileSize(doc.size)}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <StatusBadge status={doc.scope} />
              <StatusBadge status={doc.status} />
            </div>

            <div className="flex items-center justify-between text-xs text-slate-500">
              <span>
                {doc.uploadedAt
                  ? formatDate(doc.uploadedAt)
                  : formatDate(doc.createdAt)}
              </span>
              <span
                className={`inline-flex items-center gap-1 ${
                  isShared ? "text-teal-600" : "text-slate-400"
                }`}
              >
                {isShared ? (
                  <Globe className="size-3" />
                ) : (
                  <Lock className="size-3" />
                )}
                {isShared ? "Shared" : "Internal"}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
