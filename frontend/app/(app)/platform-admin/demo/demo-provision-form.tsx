"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { demoProvisionSchema, type DemoProvisionFormData } from "@/lib/schemas/demo-provision";
import { provisionDemo, type DemoProvisionResponse } from "@/app/(app)/platform-admin/demo/actions";

const VERTICAL_OPTIONS = [
  {
    value: "consulting-generic" as const,
    label: "Generic",
    description: "Marketing agency / consultancy",
  },
  {
    value: "consulting-za" as const,
    label: "Consulting ZA",
    description: "South African digital agency / consulting firm",
  },
  {
    value: "accounting-za" as const,
    label: "Accounting",
    description: "South African accounting firm",
  },
  {
    value: "legal-za" as const,
    label: "Legal",
    description: "South African law firm",
  },
];

export function DemoProvisionForm() {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<DemoProvisionResponse | null>(null);

  const form = useForm<DemoProvisionFormData>({
    resolver: zodResolver(demoProvisionSchema),
    defaultValues: {
      organizationName: "",
      verticalProfile: "consulting-generic",
      adminEmail: "",
      seedDemoData: true,
    },
  });

  async function handleSubmit(values: DemoProvisionFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const response = await provisionDemo({
        organizationName: values.organizationName.trim(),
        verticalProfile: values.verticalProfile,
        adminEmail: values.adminEmail.trim(),
        seedDemoData: values.seedDemoData,
      });
      if (response.success && response.data) {
        setResult(response.data);
      } else {
        setError(response.error ?? "An error occurred");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleCreateAnother() {
    setResult(null);
    setError(null);
    form.reset();
  }

  if (result) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center gap-3">
          <h2 className="text-lg font-semibold">{result.organizationName}</h2>
          <Badge variant="neutral">{result.verticalProfile}</Badge>
        </div>
        <dl className="mt-4 space-y-3 text-sm">
          <div>
            <dt className="font-medium text-slate-500">Organization ID</dt>
            <dd className="mt-0.5 text-slate-900">{result.organizationId}</dd>
          </div>
          <div>
            <dt className="font-medium text-slate-500">Slug</dt>
            <dd className="mt-0.5 text-slate-900">{result.organizationSlug}</dd>
          </div>
          <div>
            <dt className="font-medium text-slate-500">Login URL</dt>
            <dd className="mt-0.5">
              <code className="rounded bg-slate-100 px-2 py-1 text-sm text-slate-900">
                {result.loginUrl}
              </code>
            </dd>
          </div>
          <div>
            <dt className="font-medium text-slate-500">Seed Data</dt>
            <dd className="mt-0.5 text-slate-900">{result.demoDataSeeded ? "Yes" : "No"}</dd>
          </div>
          {result.adminNote && (
            <div>
              <dt className="font-medium text-slate-500">Admin Note</dt>
              <dd className="mt-0.5 text-slate-900">{result.adminNote}</dd>
            </div>
          )}
        </dl>
        <div className="mt-6">
          <Button variant="outline" onClick={handleCreateAnother}>
            Create Another
          </Button>
        </div>
      </div>
    );
  }

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(handleSubmit)}
        className="space-y-6 rounded-lg border border-slate-200 bg-white p-6 shadow-sm"
      >
        <FormField
          control={form.control}
          name="organizationName"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Organization Name</FormLabel>
              <FormControl>
                <Input placeholder="Demo — Accounting Firm" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="verticalProfile"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Vertical Profile</FormLabel>
              <FormControl>
                <div className="space-y-2">
                  {VERTICAL_OPTIONS.map((option) => (
                    <label
                      key={option.value}
                      className="flex cursor-pointer items-center gap-3 rounded-md border border-slate-200 p-3 hover:bg-slate-50"
                    >
                      <input
                        type="radio"
                        name="verticalProfile"
                        value={option.value}
                        checked={field.value === option.value}
                        onChange={() => field.onChange(option.value)}
                        className="h-4 w-4 text-teal-600"
                      />
                      <div>
                        <div className="text-sm font-medium">{option.label}</div>
                        <div className="text-xs text-slate-500">{option.description}</div>
                      </div>
                    </label>
                  ))}
                </div>
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="adminEmail"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Admin Email</FormLabel>
              <FormControl>
                <Input type="email" placeholder="admin@example.com" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="seedDemoData"
          render={({ field }) => (
            <FormItem className="flex items-center gap-3">
              <FormControl>
                <Switch
                  id="seed-demo-data"
                  checked={field.value}
                  onCheckedChange={field.onChange}
                />
              </FormControl>
              <FormLabel htmlFor="seed-demo-data" className="cursor-pointer font-normal">
                Seed Demo Data
              </FormLabel>
            </FormItem>
          )}
        />

        {error && <p className="text-destructive text-sm">{error}</p>}

        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Creating..." : "Create Demo Tenant"}
        </Button>
      </form>
    </Form>
  );
}
