"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useParams } from "next/navigation";

import { DataTable } from "@/components/ui/data-table";
import { DataTableToolbar } from "@/components/ui/data-table-toolbar";
import { EmptyState } from "@/components/empty-state";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import { getProjectColumns, type ProjectRow } from "@/components/projects/project-columns";
import { FolderOpen } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { Customer } from "@/lib/types";

interface ProjectListClientProps {
  projects: ProjectRow[];
  customers: Customer[];
  slug: string;
  onCreateProject: (slug: string, formData: FormData) => Promise<{ success: boolean; error?: string }>;
}

export function ProjectListClient({
  projects,
  customers,
  slug,
  onCreateProject,
}: ProjectListClientProps) {
  const router = useRouter();
  const [search, setSearch] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState("all");

  const columns = React.useMemo(() => getProjectColumns(), []);

  const filtered = React.useMemo(() => {
    let result = projects;

    if (search) {
      const q = search.toLowerCase();
      result = result.filter(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          p.customerName?.toLowerCase().includes(q) ||
          p.description?.toLowerCase().includes(q)
      );
    }

    if (statusFilter !== "all") {
      result = result.filter((p) => p.status === statusFilter);
    }

    return result;
  }, [projects, search, statusFilter]);

  function handleRowClick(row: ProjectRow) {
    router.push(`/org/${slug}/projects/${row.id}`);
  }

  if (projects.length === 0) {
    return (
      <EmptyState
        icon={FolderOpen}
        title="No projects yet"
        description="Create your first project to get started."
        action={
          <CreateProjectDialog
            slug={slug}
            customers={customers}
            onCreateProject={onCreateProject}
          />
        }
      />
    );
  }

  return (
    <div className="space-y-4">
      <DataTableToolbar
        searchPlaceholder="Search projects..."
        searchValue={search}
        onSearchChange={setSearch}
        filters={
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="h-8 w-[130px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Statuses</SelectItem>
              <SelectItem value="ACTIVE">Active</SelectItem>
              <SelectItem value="COMPLETED">Completed</SelectItem>
              <SelectItem value="ARCHIVED">Archived</SelectItem>
            </SelectContent>
          </Select>
        }
        actions={
          <CreateProjectDialog
            slug={slug}
            customers={customers}
            onCreateProject={onCreateProject}
          />
        }
      />
      <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
        <DataTable
          columns={columns}
          data={filtered}
          onRowClick={handleRowClick}
        />
      </div>
    </div>
  );
}
