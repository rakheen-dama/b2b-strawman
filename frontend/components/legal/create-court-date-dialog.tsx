"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
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
  createCourtDateSchema,
  type CreateCourtDateFormData,
} from "@/lib/schemas/legal";
import {
  createCourtDate,
  fetchProjects,
} from "@/app/(app)/org/[slug]/court-calendar/actions";

interface CreateCourtDateDialogProps {
  slug: string;
  onSuccess?: () => void;
}

const DATE_TYPES = [
  { value: "HEARING", label: "Hearing" },
  { value: "TRIAL", label: "Trial" },
  { value: "MOTION", label: "Motion" },
  { value: "CONFERENCE", label: "Conference" },
  { value: "MEDIATION", label: "Mediation" },
  { value: "ARBITRATION", label: "Arbitration" },
  { value: "MENTION", label: "Mention" },
  { value: "OTHER", label: "Other" },
] as const;

export function CreateCourtDateDialog({
  slug,
  onSuccess,
}: CreateCourtDateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [projects, setProjects] = useState<
    { id: string; name: string }[]
  >([]);
  const [projectsLoading, setProjectsLoading] = useState(false);
  const [projectsError, setProjectsError] = useState<string | null>(null);

  const form = useForm<CreateCourtDateFormData>({
    resolver: zodResolver(createCourtDateSchema),
    defaultValues: {
      projectId: "",
      dateType: "HEARING",
      scheduledDate: "",
      scheduledTime: "",
      courtName: "",
      courtReference: "",
      judgeMagistrate: "",
      description: "",
      reminderDays: 7,
    },
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

  async function onSubmit(values: CreateCourtDateFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await createCourtDate(slug, {
        projectId: values.projectId,
        dateType: values.dateType,
        scheduledDate: values.scheduledDate,
        scheduledTime: values.scheduledTime || undefined,
        courtName: values.courtName,
        courtReference: values.courtReference || undefined,
        judgeMagistrate: values.judgeMagistrate || undefined,
        description: values.description || undefined,
        reminderDays: values.reminderDays,
      });
      if (result.success) {
        form.reset();
        setOpen(false);
        onSuccess?.();
      } else {
        setError(result.error ?? "Failed to create court date");
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
        <Button size="sm" data-testid="create-court-date-trigger">
          <Plus className="mr-1.5 size-4" />
          New Court Date
        </Button>
      </DialogTrigger>
      <DialogContent data-testid="create-court-date-dialog">
        <DialogHeader>
          <DialogTitle>Schedule Court Date</DialogTitle>
          <DialogDescription>
            Add a new court date to the calendar.
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
              name="dateType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Type</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:placeholder:text-slate-400 dark:focus-visible:ring-slate-300"
                    >
                      {DATE_TYPES.map((dt) => (
                        <option key={dt.value} value={dt.value}>
                          {dt.label}
                        </option>
                      ))}
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="scheduledDate"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Date</FormLabel>
                    <FormControl>
                      <Input type="date" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="scheduledTime"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Time{" "}
                      <span className="font-normal text-slate-500">
                        (optional)
                      </span>
                    </FormLabel>
                    <FormControl>
                      <Input type="time" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="courtName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Court Name</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="e.g. Johannesburg High Court"
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
              name="courtReference"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Court Reference{" "}
                    <span className="font-normal text-slate-500">
                      (optional)
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Case number or reference"
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
              name="judgeMagistrate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Judge / Magistrate{" "}
                    <span className="font-normal text-slate-500">
                      (optional)
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Input maxLength={255} {...field} />
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
                  <FormLabel>
                    Description{" "}
                    <span className="font-normal text-slate-500">
                      (optional)
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Additional details..."
                      maxLength={2000}
                      rows={3}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="reminderDays"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Reminder (days before)</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      min={0}
                      max={365}
                      {...field}
                      onChange={(e) =>
                        field.onChange(
                          e.target.value === ""
                            ? 7
                            : Number(e.target.value)
                        )
                      }
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
                {isSubmitting ? "Scheduling..." : "Schedule Court Date"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
