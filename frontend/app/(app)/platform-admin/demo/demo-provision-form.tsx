"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@b2mash/ui/badge";
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

export function DemoProvisionForm() {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<DemoProvisionResponse | null>(null);

  const form = useForm<DemoProvisionFormData>({
    resolver: zodResolver(demoProvisionSchema),
    defaultValues: {
      organizationName: "",
      verticalProfile: "legal-za",
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
                <Input placeholder="Demo — Law Firm" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        {/* Phase 86 (ADR-332): single vertical profile — shown read-only, still sent in the payload. */}
        <div className="space-y-1">
          <p className="text-sm font-medium">Vertical Profile</p>
          <p className="text-sm text-slate-600" data-testid="demo-vertical-profile">
            Legal (South Africa)
          </p>
        </div>

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
