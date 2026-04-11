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
import { createAdversePartySchema, type CreateAdversePartyFormData } from "@/lib/schemas/legal";
import {
  createAdverseParty,
  updateAdverseParty,
} from "@/app/(app)/org/[slug]/legal/adverse-parties/actions";
import type { AdverseParty } from "@/lib/types";

const PARTY_TYPES = [
  { value: "NATURAL_PERSON", label: "Natural Person" },
  { value: "COMPANY", label: "Company" },
  { value: "TRUST", label: "Trust" },
  { value: "CLOSE_CORPORATION", label: "Close Corporation" },
  { value: "PARTNERSHIP", label: "Partnership" },
  { value: "OTHER", label: "Other" },
] as const;

interface AdversePartyDialogProps {
  slug: string;
  party?: AdverseParty;
  onSuccess?: () => void;
  trigger?: React.ReactNode;
}

export function AdversePartyDialog({ slug, party, onSuccess, trigger }: AdversePartyDialogProps) {
  const isEdit = !!party;
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<CreateAdversePartyFormData>({
    resolver: zodResolver(createAdversePartySchema),
    defaultValues: {
      name: party?.name ?? "",
      idNumber: party?.idNumber ?? "",
      registrationNumber: party?.registrationNumber ?? "",
      partyType: party?.partyType ?? "NATURAL_PERSON",
      aliases: party?.aliases ?? "",
      notes: party?.notes ?? "",
    },
  });

  // Reset form when party changes or dialog opens
  useEffect(() => {
    if (open) {
      form.reset({
        name: party?.name ?? "",
        idNumber: party?.idNumber ?? "",
        registrationNumber: party?.registrationNumber ?? "",
        partyType: party?.partyType ?? "NATURAL_PERSON",
        aliases: party?.aliases ?? "",
        notes: party?.notes ?? "",
      });
    }
  }, [open, party, form]);

  async function onSubmit(values: CreateAdversePartyFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const data = {
        name: values.name,
        idNumber: values.idNumber || undefined,
        registrationNumber: values.registrationNumber || undefined,
        partyType: values.partyType,
        aliases: values.aliases || undefined,
        notes: values.notes || undefined,
      };

      const result = isEdit
        ? await updateAdverseParty(slug, party!.id, data)
        : await createAdverseParty(slug, data);

      if (result.success) {
        form.reset();
        setOpen(false);
        onSuccess?.();
      } else {
        setError(result.error ?? `Failed to ${isEdit ? "update" : "create"} adverse party`);
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
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        {trigger ?? (
          <Button size="sm" data-testid="create-adverse-party-trigger">
            <Plus className="mr-1.5 size-4" /> {isEdit ? "Edit Party" : "Add Party"}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent data-testid="adverse-party-dialog">
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit Adverse Party" : "Add Adverse Party"}</DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update adverse party details."
              : "Register a new adverse party in the system."}
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name *</FormLabel>
                  <FormControl>
                    <Input placeholder="Full name or company name" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="partyType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Party Type *</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none dark:border-slate-800 dark:focus-visible:ring-slate-300"
                    >
                      {PARTY_TYPES.map((t) => (
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
                name="idNumber"
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
                name="registrationNumber"
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
              name="aliases"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Aliases</FormLabel>
                  <FormControl>
                    <Input placeholder="Alternative names, comma separated" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="notes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Notes</FormLabel>
                  <FormControl>
                    <Textarea placeholder="Additional notes..." rows={3} {...field} />
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
                {isSubmitting
                  ? isEdit
                    ? "Updating..."
                    : "Creating..."
                  : isEdit
                    ? "Update Party"
                    : "Create Party"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
