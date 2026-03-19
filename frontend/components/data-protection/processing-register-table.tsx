"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Plus, Pencil, Trash2, Download, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  createProcessingActivity,
  updateProcessingActivity,
  deleteProcessingActivity,
} from "@/app/(app)/org/[slug]/settings/data-protection/actions";
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
          <form
            onSubmit={form.handleSubmit(handleSubmit)}
            className="space-y-4 py-2"
          >
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
                    Recipients{" "}
                    <span className="font-normal text-muted-foreground">
                      (optional)
                    </span>
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

            {error && <p className="text-sm text-destructive">{error}</p>}

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

interface ProcessingRegisterTableProps {
  activities: ProcessingActivity[];
  slug: string;
}

export function ProcessingRegisterTable({
  activities,
  slug,
}: ProcessingRegisterTableProps) {
  const router = useRouter();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingActivity, setEditingActivity] =
    useState<ProcessingActivity | null>(null);
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

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Processing Register
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Maintain a register of all personal data processing activities as
            required by your jurisdiction.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={handleExport}>
            <Download className="mr-1.5 size-4" />
            Export Register
          </Button>
          <Button size="sm" onClick={handleAdd}>
            <Plus className="mr-1.5 size-4" />
            Add Activity
          </Button>
        </div>
      </div>

      <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Category</TableHead>
              <TableHead>Description</TableHead>
              <TableHead>Legal Basis</TableHead>
              <TableHead>Data Subjects</TableHead>
              <TableHead>Retention Period</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {activities.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={6}
                  className="py-8 text-center text-slate-500 dark:text-slate-400"
                >
                  No processing activities recorded. Click &ldquo;Add
                  Activity&rdquo; to create one.
                </TableCell>
              </TableRow>
            ) : (
              activities.map((activity) => (
                <TableRow key={activity.id}>
                  <TableCell className="font-medium">
                    {activity.category}
                  </TableCell>
                  <TableCell className="max-w-xs truncate">
                    {activity.description}
                  </TableCell>
                  <TableCell>{activity.legalBasis}</TableCell>
                  <TableCell>{activity.dataSubjects}</TableCell>
                  <TableCell>{activity.retentionPeriod}</TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleEdit(activity)}
                        aria-label={`Edit ${activity.category}`}
                      >
                        <Pencil className="size-4" />
                        <span className="sr-only">Edit</span>
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={deletingId === activity.id}
                        onClick={() => handleDelete(activity.id)}
                        className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
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
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <ProcessingActivityDialog
        key={editingActivity?.id ?? "new"}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        slug={slug}
        activity={editingActivity}
      />
    </div>
  );
}
