"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
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
  linkAdverseParty,
  fetchAdverseParties,
  fetchCustomers,
} from "@/app/(app)/org/[slug]/legal/adverse-parties/actions";
import type { AdverseParty } from "@/lib/types";

const RELATIONSHIPS = [
  { value: "OPPOSING_PARTY", label: "Opposing Party" },
  { value: "WITNESS", label: "Witness" },
  { value: "CO_ACCUSED", label: "Co-Accused" },
  { value: "RELATED_ENTITY", label: "Related Entity" },
  { value: "GUARANTOR", label: "Guarantor" },
] as const;

const linkToProjectSchema = z.object({
  adversePartyId: z.string().uuid("Please select an adverse party"),
  customerId: z.string().uuid("Please select a customer"),
  relationship: z.enum(["OPPOSING_PARTY", "WITNESS", "CO_ACCUSED", "RELATED_ENTITY", "GUARANTOR"], {
    message: "Relationship is required",
  }),
  description: z.string().max(2000).optional().or(z.literal("")),
});

type LinkToProjectFormData = z.infer<typeof linkToProjectSchema>;

interface LinkAdversePartyToProjectDialogProps {
  slug: string;
  projectId: string;
  onSuccess?: () => void;
}

export function LinkAdversePartyToProjectDialog({
  slug,
  projectId,
  onSuccess,
}: LinkAdversePartyToProjectDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [adverseParties, setAdverseParties] = useState<AdverseParty[]>([]);
  const [customers, setCustomers] = useState<{ id: string; name: string }[]>([]);

  const form = useForm<LinkToProjectFormData>({
    resolver: zodResolver(linkToProjectSchema),
    defaultValues: {
      adversePartyId: "",
      customerId: "",
      relationship: "OPPOSING_PARTY",
      description: "",
    },
  });

  useEffect(() => {
    if (open) {
      Promise.all([fetchAdverseParties(), fetchCustomers()])
        .then(([ap, c]) => {
          setAdverseParties(ap?.content ?? []);
          setCustomers(c ?? []);
        })
        .catch(() => {
          setAdverseParties([]);
          setCustomers([]);
        });
    }
  }, [open]);

  async function onSubmit(values: LinkToProjectFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await linkAdverseParty(slug, values.adversePartyId, {
        projectId,
        customerId: values.customerId,
        relationship: values.relationship,
        description: values.description || undefined,
      });
      if (result.success) {
        form.reset();
        setOpen(false);
        onSuccess?.();
      } else {
        setError(result.error ?? "Failed to link adverse party");
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
        <Button size="sm" data-testid="link-adverse-party-trigger">
          <Plus className="mr-1.5 size-4" />
          Link Adverse Party
        </Button>
      </DialogTrigger>
      <DialogContent data-testid="link-adverse-party-to-project-dialog">
        <DialogHeader>
          <DialogTitle>Link Adverse Party</DialogTitle>
          <DialogDescription>Link an adverse party to this matter.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="adversePartyId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Adverse Party *</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none dark:border-slate-800 dark:focus-visible:ring-slate-300"
                    >
                      <option value="">-- Select adverse party --</option>
                      {adverseParties.map((ap) => (
                        <option key={ap.id} value={ap.id}>
                          {ap.name}
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
              name="customerId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Customer *</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none dark:border-slate-800 dark:focus-visible:ring-slate-300"
                    >
                      <option value="">-- Select customer --</option>
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
              name="relationship"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Relationship *</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none dark:border-slate-800 dark:focus-visible:ring-slate-300"
                    >
                      {RELATIONSHIPS.map((r) => (
                        <option key={r.value} value={r.value}>
                          {r.label}
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
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Additional context about this link..."
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
                variant="outline"
                onClick={() => setOpen(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Linking..." : "Link Party"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
