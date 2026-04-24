"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Search } from "lucide-react";
import { performConflictCheckSchema, type PerformConflictCheckFormData } from "@/lib/schemas/legal";
import {
  performConflictCheck,
  fetchProjects,
  fetchCustomers,
} from "@/app/(app)/org/[slug]/conflict-check/actions";
import { ConflictCheckResultDisplay } from "@/components/legal/conflict-check-result";
import type { ConflictCheck } from "@/lib/types";

const CHECK_TYPES = [
  { value: "NEW_CLIENT", label: "New Client" },
  { value: "NEW_MATTER", label: "New Matter" },
  { value: "PERIODIC_REVIEW", label: "Periodic Review" },
] as const;

interface ConflictCheckFormProps {
  slug: string;
  initialCustomers?: { id: string; name: string }[];
  initialProjects?: { id: string; name: string }[];
  onCheckComplete?: () => void;
}

export function ConflictCheckForm({
  slug,
  initialCustomers,
  initialProjects,
  onCheckComplete,
}: ConflictCheckFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ConflictCheck | null>(null);
  const [projects, setProjects] = useState<{ id: string; name: string }[]>(initialProjects ?? []);
  const [customers, setCustomers] = useState<{ id: string; name: string }[]>(
    initialCustomers ?? []
  );

  const form = useForm<PerformConflictCheckFormData>({
    resolver: zodResolver(performConflictCheckSchema),
    defaultValues: {
      checkedName: "",
      checkedIdNumber: "",
      checkedRegistrationNumber: "",
      checkType: "NEW_CLIENT",
      customerId: "",
      projectId: "",
    },
  });

  useEffect(() => {
    // Only fetch client-side if the server didn't supply initial data (e.g.
    // legacy callers). Keeps the dropdowns hydrated on first render (GAP-L-29).
    if ((initialCustomers?.length ?? 0) > 0 || (initialProjects?.length ?? 0) > 0) {
      return;
    }
    Promise.all([fetchProjects(), fetchCustomers()])
      .then(([p, c]) => {
        setProjects(p ?? []);
        setCustomers(c ?? []);
      })
      .catch(() => {
        setProjects([]);
        setCustomers([]);
      });
  }, [initialCustomers, initialProjects]);

  async function onSubmit(values: PerformConflictCheckFormData) {
    setError(null);
    setResult(null);
    setIsSubmitting(true);
    try {
      const res = await performConflictCheck({
        checkedName: values.checkedName,
        checkedIdNumber: values.checkedIdNumber || undefined,
        checkedRegistrationNumber: values.checkedRegistrationNumber || undefined,
        checkType: values.checkType,
        customerId: values.customerId || undefined,
        projectId: values.projectId || undefined,
      });
      if (res.success && res.data) {
        setResult(res.data);
        onCheckComplete?.();
      } else {
        setError(res.error ?? "Failed to perform conflict check");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div data-testid="conflict-check-form" className="space-y-6">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <FormField
            control={form.control}
            name="checkedName"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Name to Check *</FormLabel>
                <FormControl>
                  <Input placeholder="Enter name to check for conflicts" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormField
              control={form.control}
              name="checkedIdNumber"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>ID Number</FormLabel>
                  <FormControl>
                    <Input placeholder="e.g. 8501015800087" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="checkedRegistrationNumber"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Registration Number</FormLabel>
                  <FormControl>
                    <Input placeholder="e.g. 2020/123456/07" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>

          <FormField
            control={form.control}
            name="checkType"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Check Type *</FormLabel>
                <FormControl>
                  <select
                    value={field.value}
                    onChange={field.onChange}
                    className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none dark:border-slate-800 dark:focus-visible:ring-slate-300"
                  >
                    {CHECK_TYPES.map((t) => (
                      <option key={t.value} value={t.value}>
                        {t.label}
                      </option>
                    ))}
                  </select>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormField
              control={form.control}
              name="customerId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Customer (optional)</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none dark:border-slate-800 dark:focus-visible:ring-slate-300"
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
              name="projectId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Matter (optional)</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none dark:border-slate-800 dark:focus-visible:ring-slate-300"
                    >
                      <option value="">-- None --</option>
                      {projects.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <Button type="submit" disabled={isSubmitting}>
            <Search className="mr-1.5 size-4" />
            {isSubmitting ? "Checking..." : "Run Conflict Check"}
          </Button>
        </form>
      </Form>

      {result && <ConflictCheckResultDisplay result={result} slug={slug} />}
    </div>
  );
}
