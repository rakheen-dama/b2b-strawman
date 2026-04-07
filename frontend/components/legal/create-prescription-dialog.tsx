"use client";

import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Plus } from "lucide-react";
import {
  createPrescriptionTrackerSchema,
  type CreatePrescriptionTrackerFormData,
} from "@/lib/schemas/legal";
import {
  createPrescriptionTracker,
  fetchProjects,
} from "@/app/(app)/org/[slug]/court-calendar/actions";

interface CreatePrescriptionDialogProps {
  slug: string;
  onSuccess?: () => void;
}

const PRESCRIPTION_TYPES = [
  { value: "GENERAL_3Y", label: "General (3 years)" },
  { value: "DEBT_6Y", label: "Debt (6 years)" },
  { value: "MORTGAGE_30Y", label: "Mortgage (30 years)" },
  { value: "DELICT_3Y", label: "Delict (3 years)" },
  { value: "CONTRACT_3Y", label: "Contract (3 years)" },
  { value: "CUSTOM", label: "Custom" },
] as const;

export function CreatePrescriptionDialog({
  slug,
  onSuccess,
}: CreatePrescriptionDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [projects, setProjects] = useState<
    { id: string; name: string }[]
  >([]);
  const [projectsLoading, setProjectsLoading] = useState(false);
  const [projectsError, setProjectsError] = useState<string | null>(null);

  const form = useForm<CreatePrescriptionTrackerFormData>({
    resolver: zodResolver(createPrescriptionTrackerSchema),
    defaultValues: {
      projectId: "",
      causeOfActionDate: "",
      prescriptionType: "GENERAL_3Y",
      customYears: undefined,
      notes: "",
    },
  });

  const prescriptionType = useWatch({
    control: form.control,
    name: "prescriptionType",
  });

  useEffect(() => {
    if (open) {
      setProjectsLoading(true);
      setProjectsError(null);
      fetchProjects()
        .then((all) => setProjects(all ?? []))
        .catch((err) => {
          console.error("Failed to load matters:", err);
          setProjects([]);
          setProjectsError("Failed to load matters. Please try again.");
        })
        .finally(() => setProjectsLoading(false));
    }
  }, [open]);

  async function onSubmit(values: CreatePrescriptionTrackerFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await createPrescriptionTracker(slug, {
        projectId: values.projectId,
        causeOfActionDate: values.causeOfActionDate,
        prescriptionType: values.prescriptionType,
        customYears:
          values.prescriptionType === "CUSTOM"
            ? values.customYears
            : undefined,
        notes: values.notes || undefined,
      });
      if (result.success) {
        form.reset();
        setOpen(false);
        onSuccess?.();
      } else {
        setError(result.error ?? "Failed to create prescription tracker");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      setError(null);
      form.reset();
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm" data-testid="create-prescription-trigger">
          <Plus className="mr-1.5 size-4" />
          Add Tracker
        </Button>
      </DialogTrigger>
      <DialogContent data-testid="create-prescription-dialog">
        <DialogHeader>
          <DialogTitle>Create Prescription Tracker</DialogTitle>
          <DialogDescription>
            Track prescription periods for a matter.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="projectId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Matter</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      disabled={projectsLoading}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:placeholder:text-slate-400 dark:focus-visible:ring-slate-300"
                    >
                      <option value="">
                        {projectsLoading
                          ? "Loading matters..."
                          : "-- Select matter --"}
                      </option>
                      {projects.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    </select>
                  </FormControl>
                  {projectsError && (
                    <p className="text-sm text-red-600">{projectsError}</p>
                  )}
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="causeOfActionDate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Cause of Action Date</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="prescriptionType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Prescription Type</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:placeholder:text-slate-400 dark:focus-visible:ring-slate-300"
                    >
                      {PRESCRIPTION_TYPES.map((pt) => (
                        <option key={pt.value} value={pt.value}>
                          {pt.label}
                        </option>
                      ))}
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {prescriptionType === "CUSTOM" && (
              <FormField
                control={form.control}
                name="customYears"
                render={({ field }) => (
                  <FormItem data-testid="custom-years-field">
                    <FormLabel>Custom Years</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        min={1}
                        max={100}
                        placeholder="Number of years"
                        {...field}
                        value={field.value ?? ""}
                        onChange={(e) =>
                          field.onChange(
                            e.target.value === ""
                              ? undefined
                              : Number(e.target.value)
                          )
                        }
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}

            <FormField
              control={form.control}
              name="notes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Notes{" "}
                    <span className="font-normal text-slate-500">
                      (optional)
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Additional notes..."
                      maxLength={2000}
                      rows={3}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {error && <p className="text-sm text-red-600">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => setOpen(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Creating..." : "Create Tracker"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
