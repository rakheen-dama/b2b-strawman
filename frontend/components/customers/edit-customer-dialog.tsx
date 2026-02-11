"use client";

import { useState } from "react";
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
import { updateCustomer } from "@/app/(app)/org/[slug]/customers/actions";
import type { Customer } from "@/lib/types";

interface EditCustomerDialogProps {
  customer: Customer;
  slug: string;
  children: React.ReactNode;
}

export function EditCustomerDialog({ customer, slug, children }: EditCustomerDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(formData: FormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await updateCustomer(slug, customer.id, formData);
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to update customer.");
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
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit Customer</DialogTitle>
          <DialogDescription>Update customer information.</DialogDescription>
        </DialogHeader>
        <form action={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="edit-customer-name">Name</Label>
            <Input
              id="edit-customer-name"
              name="name"
              defaultValue={customer.name}
              required
              maxLength={255}
              autoFocus
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="edit-customer-email">Email</Label>
            <Input
              id="edit-customer-email"
              name="email"
              type="email"
              defaultValue={customer.email}
              required
              maxLength={255}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="edit-customer-phone">
              Phone <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Input
              id="edit-customer-phone"
              name="phone"
              type="tel"
              defaultValue={customer.phone ?? ""}
              maxLength={50}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="edit-customer-id-number">
              ID Number <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Input
              id="edit-customer-id-number"
              name="idNumber"
              defaultValue={customer.idNumber ?? ""}
              maxLength={100}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="edit-customer-notes">
              Notes <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Textarea
              id="edit-customer-notes"
              name="notes"
              defaultValue={customer.notes ?? ""}
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
              {isSubmitting ? "Saving..." : "Save Changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
