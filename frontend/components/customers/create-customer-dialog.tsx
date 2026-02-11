"use client";

import { useRef, useState } from "react";
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
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { createCustomer } from "@/app/(app)/org/[slug]/customers/actions";
import { Plus } from "lucide-react";

interface CreateCustomerDialogProps {
  slug: string;
}

export function CreateCustomerDialog({ slug }: CreateCustomerDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);

  async function handleSubmit(formData: FormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await createCustomer(slug, formData);
      if (result.success) {
        formRef.current?.reset();
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to create customer.");
      }
    } catch {
      setError("An unexpected error occurred.");
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
        <Button size="sm">
          <Plus className="mr-1.5 size-4" />
          New Customer
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create Customer</DialogTitle>
          <DialogDescription>Add a new customer to your organization.</DialogDescription>
        </DialogHeader>
        <form ref={formRef} action={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="customer-name">Name</Label>
            <Input
              id="customer-name"
              name="name"
              placeholder="Customer name"
              required
              maxLength={255}
              autoFocus
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="customer-email">Email</Label>
            <Input
              id="customer-email"
              name="email"
              type="email"
              placeholder="customer@example.com"
              required
              maxLength={255}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="customer-phone">
              Phone <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Input
              id="customer-phone"
              name="phone"
              type="tel"
              placeholder="+1 (555) 000-0000"
              maxLength={50}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="customer-id-number">
              ID Number <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Input
              id="customer-id-number"
              name="idNumber"
              placeholder="e.g. CUS-001"
              maxLength={100}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="customer-notes">
              Notes <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Textarea
              id="customer-notes"
              name="notes"
              placeholder="Any additional notes about this customer..."
              maxLength={2000}
              rows={3}
            />
          </div>
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
              {isSubmitting ? "Creating..." : "Create Customer"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
