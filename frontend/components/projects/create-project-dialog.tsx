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
import { createProject, fetchActiveCustomers } from "@/app/(app)/org/[slug]/projects/actions";
import type { Customer } from "@/lib/types";
import { Plus, ShieldAlert } from "lucide-react";
import { createMessages } from "@/lib/messages";
import { scrollToFirstError } from "@/lib/error-handler";
import { useTerminology } from "@/lib/terminology";
import { useSubscription } from "@/lib/subscription-context";
import { ModuleGate } from "@/components/module-gate";
import { Badge } from "@/components/ui/badge";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  createProjectSchema,
  type CreateProjectFormData,
} from "@/lib/schemas/project";
import { nativeSelectClassName } from "@/lib/styles/native-select";

interface CreateProjectDialogProps {
  slug: string;
}

export function CreateProjectDialog({ slug }: CreateProjectDialogProps) {
  const { t } = useTerminology();
  const { isWriteEnabled } = useSubscription();
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);

  const form = useForm<CreateProjectFormData>({
    resolver: zodResolver(createProjectSchema),
    defaultValues: {
      name: "",
      description: "",
      dueDate: "",
      customerId: "",
      referenceNumber: "",
      priority: "",
      workType: "",
    },
  });

  useEffect(() => {
    if (open) {
      fetchActiveCustomers()
        .then((all) => setCustomers(all))
        .catch(() => setCustomers([]));
    }
  }, [open]);

  async function onSubmit(values: CreateProjectFormData) {
    const { t } = createMessages("errors");
    setError(null);
    setIsSubmitting(true);

    try {
      // Build FormData to preserve existing API contract
      const formData = new FormData();
      formData.set("name", values.name);
      formData.set("description", values.description ?? "");
      formData.set("dueDate", values.dueDate ?? "");
      formData.set("customerId", values.customerId ?? "");
      formData.set("referenceNumber", values.referenceNumber ?? "");
      formData.set("priority", values.priority ?? "");
      formData.set("workType", values.workType ?? "");

      const result = await createProject(slug, formData);
      if (result.success) {
        form.reset();
        setOpen(false);
      } else {
        setError(result.error ?? t("api.serverError"));
        scrollToFirstError();
      }
    } catch {
      setError(t("api.networkError"));
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
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <span tabIndex={0} className="inline-flex">
              <Button size="sm" disabled={!isWriteEnabled} onClick={() => setOpen(true)}>
                <Plus className="mr-1.5 size-4" />
                New {t("Project")}
              </Button>
            </span>
          </TooltipTrigger>
          {!isWriteEnabled && (
            <TooltipContent>Subscribe to enable this action</TooltipContent>
          )}
        </Tooltip>
      </TooltipProvider>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create Project</DialogTitle>
          <DialogDescription>Add a new project to your organization.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="My Project"
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
                  <FormLabel>
                    Description <span className="text-muted-foreground font-normal">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="A brief description of the project..."
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
              name="dueDate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Due Date <span className="text-muted-foreground font-normal">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="customerId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Customer <span className="text-muted-foreground font-normal">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:placeholder:text-slate-400 dark:focus-visible:ring-slate-300"
                    >
                      <option value="">-- None --</option>
                      {customers.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="referenceNumber"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Reference Number{" "}
                    <span className="font-normal text-muted-foreground">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      placeholder="e.g. PRJ-2026-001"
                      maxLength={100}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="priority"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Priority{" "}
                    <span className="font-normal text-muted-foreground">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <select
                      value={field.value ?? ""}
                      onChange={field.onChange}
                      className={nativeSelectClassName}
                    >
                      <option value="">Select priority…</option>
                      <option value="LOW">Low</option>
                      <option value="MEDIUM">Medium</option>
                      <option value="HIGH">High</option>
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="workType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Work Type{" "}
                    <span className="font-normal text-muted-foreground">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      placeholder="e.g. Consulting, Litigation"
                      maxLength={50}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {error && <p className="text-destructive text-sm">{error}</p>}
            <ModuleGate module="conflict_check">
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900">
                <div className="flex items-center gap-2">
                  <ShieldAlert className="size-4 text-slate-500" />
                  <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
                    Conflict Check
                  </p>
                  <Badge variant="neutral">Coming Soon</Badge>
                </div>
                <p className="mt-2 text-sm text-muted-foreground">
                  Run a conflict of interest check before creating this {t("project")}.
                </p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled
                  className="mt-3"
                >
                  Run Conflict Check
                </Button>
              </div>
            </ModuleGate>
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
                {isSubmitting ? "Creating..." : "Create Project"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
