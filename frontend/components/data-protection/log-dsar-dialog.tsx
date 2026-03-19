"use client";

import { useState } from "react";
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
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Loader2, Plus } from "lucide-react";
import { createDsarRequest } from "@/app/(app)/org/[slug]/settings/data-protection/requests/actions";
import {
  logDsarRequestSchema,
  type LogDsarRequestFormData,
} from "@/lib/schemas/data-protection";

const REQUEST_TYPES = [
  { value: "ACCESS", label: "Access" },
  { value: "CORRECTION", label: "Correction" },
  { value: "DELETION", label: "Deletion" },
  { value: "OBJECTION", label: "Objection" },
] as const;

interface LogDsarRequestDialogProps {
  slug: string;
}

export function LogDsarRequestDialog({ slug }: LogDsarRequestDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const today = new Date().toLocaleDateString("en-CA"); // "YYYY-MM-DD"

  const form = useForm<LogDsarRequestFormData>({
    resolver: zodResolver(logDsarRequestSchema),
    defaultValues: {
      subjectName: "",
      subjectEmail: "",
      requestType: "ACCESS",
      customerId: "",
      receivedDate: today,
    },
  });

  function handleOpenChange(newOpen: boolean) {
    setOpen(newOpen);
    if (!newOpen) {
      form.reset({
        subjectName: "",
        subjectEmail: "",
        requestType: "ACCESS",
        customerId: "",
        receivedDate: today,
      });
      setError(null);
    }
  }

  async function handleSubmit(values: LogDsarRequestFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await createDsarRequest(slug, {
        customerId: values.customerId,
        requestType: values.requestType,
        // description combines subjectName + email as the required description field
        description: [
          `Subject: ${values.subjectName}`,
          values.subjectEmail ? `Email: ${values.subjectEmail}` : null,
          `Received: ${values.receivedDate}`,
        ]
          .filter(Boolean)
          .join(", "),
      });
      if (result.success) {
        setOpen(false);
        form.reset();
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
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus className="mr-1.5 size-4" />
          Log New Request
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Log DSAR Request</DialogTitle>
          <DialogDescription>
            Record a new data subject access request received from a customer.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(handleSubmit)}
            className="space-y-4 py-2"
          >
            <FormField
              control={form.control}
              name="subjectName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Subject Name</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Full name of the data subject"
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
              name="subjectEmail"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Email{" "}
                    <span className="font-normal text-muted-foreground">
                      (optional)
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="email"
                      placeholder="subject@example.com"
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
              name="requestType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Request Type</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800"
                    >
                      {REQUEST_TYPES.map((rt) => (
                        <option key={rt.value} value={rt.value}>
                          {rt.label}
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
                  <FormLabel>Customer ID</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="Customer UUID (required by backend)"
                      maxLength={36}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Enter the UUID of the linked customer record.
                  </p>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="receivedDate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Date Received</FormLabel>
                  <FormControl>
                    <Input type="date" max={today} {...field} />
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
                onClick={() => setOpen(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Logging...
                  </>
                ) : (
                  "Log Request"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
