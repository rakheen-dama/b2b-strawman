"use client";

import { useLayoutEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { ClipboardList, Plus, Pencil, Trash2, Download, Loader2 } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { SettingsSection } from "@/components/settings/settings-section";
import {
  createProcessingActivity,
  updateProcessingActivity,
  deleteProcessingActivity,
} from "@/app/(app)/org/[slug]/settings/firm/_tabs/data-protection-actions";
import {
  processingActivitySchema,
  type ProcessingActivityFormData,
} from "@/lib/schemas/data-protection";
import type { ProcessingActivity } from "@/lib/types/data-protection";

// --- Processing Activity Dialog ---

interface ProcessingActivityDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  slug: string;
  activity?: ProcessingActivity | null;
}

function ProcessingActivityDialog({
  open,
  onOpenChange,
  slug,
  activity,
}: ProcessingActivityDialogProps) {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isEditing = !!activity;

  const form = useForm<ProcessingActivityFormData>({
    resolver: zodResolver(processingActivitySchema),
    defaultValues: {
      category: activity?.category ?? "",
      description: activity?.description ?? "",
      legalBasis: activity?.legalBasis ?? "",
      dataSubjects: activity?.dataSubjects ?? "",
      retentionPeriod: activity?.retentionPeriod ?? "",
      recipients: activity?.recipients ?? "",
    },
  });

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      form.reset({
        category: "",
        description: "",
        legalBasis: "",
        dataSubjects: "",
        retentionPeriod: "",
        recipients: "",
      });
      setError(null);
    }
  }

  async function handleSubmit(values: ProcessingActivityFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const payload = {
        category: values.category,
        description: values.description,
        legalBasis: values.legalBasis,
        dataSubjects: values.dataSubjects,
        retentionPeriod: values.retentionPeriod,
        recipients: values.recipients || undefined,
      };

      const result = isEditing
        ? await updateProcessingActivity(slug, activity.id, payload)
        : await createProcessingActivity(slug, payload);

      if (result.success) {
        onOpenChange(false);
        form.reset();
        router.refresh();
      } else {
        setError(result.error ?? "An unexpected error occurred.");
      }
    } catch {
      setError("Network error. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {isEditing ? "Edit Processing Activity" : "Add Processing Activity"}
          </DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update the details of this processing activity."
              : "Record a new personal data processing activity."}
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4 py-2">
            <FormField
              control={form.control}
              name="category"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Category</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="e.g. Client Management, Billing"
                      maxLength={255}
                      autoFocus
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Describe the processing activity..."
                      rows={3}
                      maxLength={2000}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="legalBasis"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Legal Basis</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="e.g. Legitimate interest, Consent, Contract"
                      maxLength={255}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="dataSubjects"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Data Subjects</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="e.g. Clients, Employees, Contractors"
                      maxLength={255}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="retentionPeriod"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Retention Period</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="e.g. 5 years after contract end"
                      maxLength={255}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="recipients"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Recipients <span className="text-muted-foreground font-normal">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      placeholder="e.g. Tax authorities, Cloud providers"
                      maxLength={500}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {error && <p className="text-destructive text-sm">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => onOpenChange(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Saving...
                  </>
                ) : isEditing ? (
                  "Update"
                ) : (
                  "Add Activity"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

// --- Main Table Component ---

const getActivityId = (activity: ProcessingActivity) => activity.id;

interface ActivityHandlers {
  handleEdit: (activity: ProcessingActivity) => void;
  handleDelete: (id: string) => Promise<void>;
}

interface ProcessingRegisterTableProps {
  activities: ProcessingActivity[];
  slug: string;
}

export function ProcessingRegisterTable({ activities, slug }: ProcessingRegisterTableProps) {
  const router = useRouter();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingActivity, setEditingActivity] = useState<ProcessingActivity | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  function handleAdd() {
    setEditingActivity(null);
    setDialogOpen(true);
  }

  function handleEdit(activity: ProcessingActivity) {
    setEditingActivity(activity);
    setDialogOpen(true);
  }

  async function handleDelete(id: string) {
    if (!window.confirm("Delete this processing activity?")) return;
    setDeletingId(id);

    const result = await deleteProcessingActivity(slug, id);
    if (result.success) {
      router.refresh();
    } else {
      toast.error(result.error ?? "Failed to delete activity.");
    }
    setDeletingId(null);
  }

  function handleExport() {
    toast.info("Export register is coming soon.");
  }

  // Cells reach today's handlers through a ref refreshed after each render; the columns rebuild
  // only when `deletingId` changes (the Delete cell reads it during render).
  const handlers = useRef<ActivityHandlers>({ handleEdit, handleDelete });
  useLayoutEffect(() => {
    handlers.current = { handleEdit, handleDelete };
  });

  const columns = useMemo(
    () =>
      defineColumns<ProcessingActivity>([
        {
          id: "category",
          meta: { label: "Category", cellClassName: "font-medium" },
          cell: ({ row }) => row.original.category,
        },
        {
          id: "description",
          meta: { label: "Description", cellClassName: "max-w-xs truncate" },
          cell: ({ row }) => row.original.description,
        },
        {
          id: "legalBasis",
          meta: { label: "Legal Basis" },
          cell: ({ row }) => row.original.legalBasis,
        },
        {
          id: "dataSubjects",
          meta: { label: "Data Subjects" },
          cell: ({ row }) => row.original.dataSubjects,
        },
        {
          id: "retentionPeriod",
          meta: { label: "Retention Period" },
          cell: ({ row }) => row.original.retentionPeriod,
        },
        {
          id: "actions",
          enableHiding: false,
          meta: { label: "Actions", align: "end" },
          cell: ({ row }) => {
            const activity = row.original;
            return (
              <div className="flex items-center justify-end gap-1">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => handlers.current.handleEdit(activity)}
                  aria-label={`Edit ${activity.category}`}
                >
                  <Pencil className="size-4" />
                  <span className="sr-only">Edit</span>
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={deletingId === activity.id}
                  onClick={() => handlers.current.handleDelete(activity.id)}
                  className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                  aria-label={`Delete ${activity.category}`}
                >
                  {deletingId === activity.id ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <Trash2 className="size-4" />
                  )}
                  <span className="sr-only">Delete</span>
                </Button>
              </div>
            );
          },
        },
      ]),
    [deletingId]
  );

  return (
    <>
      <SettingsSection
        title="Processing Register"
        description="Maintain a register of all personal data processing activities as required by your jurisdiction."
        actions={
          // K-109: the two buttons wrap inside the header's action column on a narrow screen.
          <div className="flex flex-wrap items-center justify-end gap-2">
            <Button variant="outline" size="sm" onClick={handleExport}>
              <Download className="mr-1.5 size-4" />
              Export Register
            </Button>
            <Button size="sm" onClick={handleAdd}>
              <Plus className="mr-1.5 size-4" />
              Add Activity
            </Button>
          </div>
        }
      >
        <DataTable
          columns={columns}
          data={activities}
          getRowId={getActivityId}
          columnVisibility={false}
          pageSize={null}
          stickyHeader={false}
          frame={false}
          empty={
            <EmptyState
              variant="inline"
              icon={ClipboardList}
              title="No processing activities recorded."
              description="Click “Add Activity” to create one."
            />
          }
        />
      </SettingsSection>

      <ProcessingActivityDialog
        key={editingActivity?.id ?? "new"}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        slug={slug}
        activity={editingActivity}
      />
    </>
  );
}
