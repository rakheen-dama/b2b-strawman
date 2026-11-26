"use client";

import { useLayoutEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { LayoutTemplate, Pencil, Copy, Trash2 } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { StatusBadgeCell } from "@/components/data-table/cells";
import { EmptyState } from "@/components/empty-state";
import { SettingsSection } from "@/components/settings/settings-section";
import {
  deleteTemplateAction,
  duplicateTemplateAction,
} from "@/app/(app)/org/[slug]/settings/matters/_tabs/templates-actions";
import type { ProjectTemplateResponse } from "@/lib/api/templates";

// The backend stores `source` as a free string: ProjectTemplatePackSeeder writes "SEEDER",
// save-as-template writes "FROM_PROJECT", everything else is "MANUAL".
const SOURCE_BADGE: Record<
  ProjectTemplateResponse["source"],
  { label: string; variant: "lead" | "neutral" }
> = {
  SEEDER: { label: "Pack", variant: "neutral" },
  FROM_PROJECT: { label: "From matter", variant: "lead" },
  MANUAL: { label: "Manual", variant: "neutral" },
};

/** A template row plus its per-row UI state (so the memoised cells read it from `row.original`). */
interface TemplateRow extends ProjectTemplateResponse {
  errorMessage: string | undefined;
  isDeleting: boolean;
}

interface RowActions {
  handleDuplicate: (id: string) => void;
  handleDelete: (id: string) => void;
}

const getTemplateId = (row: TemplateRow) => row.id;

interface TemplateListProps {
  slug: string;
  templates: ProjectTemplateResponse[];
  canManage: boolean;
}

/** Matters › Templates list (Phase 91 665C): DataTable client mode in an untitled section. */
export function TemplateList({ slug, templates, canManage }: TemplateListProps) {
  const [errorMessages, setErrorMessages] = useState<Record<string, string>>({});
  const [deleting, setDeleting] = useState<string | null>(null);

  async function handleDuplicate(id: string) {
    const result = await duplicateTemplateAction(slug, id);
    if (!result.success && result.error) {
      setErrorMessages((prev) => ({ ...prev, [id]: result.error! }));
    }
  }

  async function handleDelete(id: string) {
    if (deleting) return;
    setDeleting(id);
    try {
      const result = await deleteTemplateAction(slug, id);
      if (!result.success && result.error) {
        setErrorMessages((prev) => ({ ...prev, [id]: result.error! }));
      }
    } finally {
      setDeleting(null);
    }
  }

  // TanStack renders each `cell` as a component, so the column array keeps its identity (a new
  // `cell` would remount the row and close an open delete dialog). Cells reach the handlers through
  // a ref refreshed after each render, and read the per-row state from the row itself.
  const actions = useRef<RowActions>({ handleDuplicate, handleDelete });
  useLayoutEffect(() => {
    actions.current = { handleDuplicate, handleDelete };
  });

  const rows = useMemo<TemplateRow[]>(
    () =>
      templates.map((template) => ({
        ...template,
        errorMessage: errorMessages[template.id],
        isDeleting: deleting === template.id,
      })),
    [templates, errorMessages, deleting]
  );

  const columns = useMemo(
    () =>
      defineColumns<TemplateRow>([
        {
          id: "name",
          enableHiding: false,
          meta: { label: "Name", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <>
              <p className="text-foreground font-medium">{row.original.name}</p>
              {row.original.description && (
                <p className="text-muted-foreground mt-0.5 text-xs">{row.original.description}</p>
              )}
              {row.original.errorMessage && (
                <p className="text-destructive mt-1 text-xs">{row.original.errorMessage}</p>
              )}
            </>
          ),
        },
        {
          id: "source",
          meta: { label: "Source" },
          cell: ({ row }) => <SourceBadge source={row.original.source} />,
        },
        {
          id: "tasks",
          meta: { label: "Tasks", cellClassName: "text-muted-foreground text-sm" },
          cell: ({ row }) => row.original.taskCount,
        },
        {
          id: "tags",
          meta: { label: "Tags", cellClassName: "text-muted-foreground text-sm" },
          cell: ({ row }) => row.original.tagCount,
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) =>
            row.original.active ? (
              <StatusBadgeCell label="Active" variant="success" />
            ) : (
              <StatusBadgeCell label="Inactive" variant="neutral" />
            ),
        },
        ...(canManage
          ? defineColumns<TemplateRow>([
              {
                id: "actions",
                enableHiding: false,
                meta: { label: "Actions", align: "end" },
                cell: ({ row }) => {
                  const template = row.original;
                  return (
                    <div className="flex items-center justify-end gap-1">
                      <Link href={`/org/${slug}/settings/matters/templates/${template.id}`}>
                        <Button variant="ghost" size="sm" title="Edit template">
                          <Pencil className="size-4" />
                          <span className="sr-only">Edit {template.name}</span>
                        </Button>
                      </Link>
                      <Button
                        variant="ghost"
                        size="sm"
                        title="Duplicate template"
                        onClick={() => actions.current.handleDuplicate(template.id)}
                      >
                        <Copy className="size-4" />
                        <span className="sr-only">Duplicate {template.name}</span>
                      </Button>
                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:bg-destructive/10 hover:text-destructive dark:text-destructive dark:hover:bg-destructive/10 dark:hover:text-destructive"
                            title="Delete template"
                          >
                            <Trash2 className="size-4" />
                            <span className="sr-only">Delete {template.name}</span>
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>Delete Template</AlertDialogTitle>
                            <AlertDialogDescription>
                              Are you sure you want to delete &quot;{template.name}&quot;? This
                              action cannot be undone.
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel>Cancel</AlertDialogCancel>
                            <AlertDialogAction
                              className="bg-red-600 hover:bg-red-700"
                              onClick={() => actions.current.handleDelete(template.id)}
                              disabled={template.isDeleting}
                            >
                              {template.isDeleting ? "Deleting..." : "Delete"}
                            </AlertDialogAction>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                    </div>
                  );
                },
              },
            ])
          : []),
      ]),
    [slug, canManage]
  );

  const empty = (
    <EmptyState
      variant="default"
      icon={LayoutTemplate}
      title="No matter templates yet."
      description="Create your first template to standardize how matters are structured."
    />
  );
  // DA-7: an empty list is the whole tab panel's content, so no card around it.
  if (templates.length === 0) return empty;

  return (
    <SettingsSection>
      <DataTable
        columns={columns}
        data={rows}
        getRowId={getTemplateId}
        columnVisibility={false}
        pageSize={null}
        stickyHeader={false}
        frame={false}
        empty={empty}
      />
    </SettingsSection>
  );
}

function SourceBadge({ source }: { source: ProjectTemplateResponse["source"] }) {
  const badge = SOURCE_BADGE[source] ?? SOURCE_BADGE.MANUAL;
  return <Badge variant={badge.variant}>{badge.label}</Badge>;
}
