"use client";

import { useEffect, useMemo, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  fetchCustomers,
  linkCustomerToProject,
} from "@/app/(app)/org/[slug]/projects/[id]/actions";
import type { Customer } from "@/lib/types";

interface LinkCustomerDialogProps {
  slug: string;
  projectId: string;
  existingCustomers: Customer[];
  children: React.ReactNode;
}

export function LinkCustomerDialog({
  slug,
  projectId,
  existingCustomers,
  children,
}: LinkCustomerDialogProps) {
  const [open, setOpen] = useState(false);
  const [allCustomers, setAllCustomers] = useState<Customer[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isLinking, setIsLinking] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [linkError, setLinkError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;

    setIsLoading(true);
    setFetchError(null);

    fetchCustomers()
      .then(setAllCustomers)
      .catch(() => setFetchError("Failed to load customers."))
      .finally(() => setIsLoading(false));
  }, [open]);

  const availableCustomers = useMemo(() => {
    const linkedIds = new Set(existingCustomers.map((c) => c.id));
    return allCustomers.filter((c) => !linkedIds.has(c.id));
  }, [allCustomers, existingCustomers]);

  async function handleLinkCustomer(customerId: string) {
    setLinkError(null);
    setIsLinking(true);

    try {
      const result = await linkCustomerToProject(slug, projectId, customerId);
      if (result.success) {
        setOpen(false);
      } else {
        setLinkError(result.error ?? "Failed to link customer.");
      }
    } catch {
      setLinkError("An unexpected error occurred.");
    } finally {
      setIsLinking(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (isLinking) return;
    if (newOpen) {
      setFetchError(null);
      setLinkError(null);
      setAllCustomers([]);
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-md p-0">
        <DialogHeader className="px-4 pt-4">
          <DialogTitle>Link Customer</DialogTitle>
          <DialogDescription>
            Search and select a customer to link to this project.
          </DialogDescription>
        </DialogHeader>

        <Command className="border-t">
          <CommandInput placeholder="Search customers..." disabled={isLoading || isLinking} />
          <CommandList>
            {isLoading ? (
              <div className="py-6 text-center text-sm text-muted-foreground">
                Loading customers...
              </div>
            ) : fetchError ? (
              <div className="py-6 text-center text-sm text-destructive">{fetchError}</div>
            ) : availableCustomers.length === 0 ? (
              <CommandEmpty>
                {allCustomers.length === 0
                  ? "No customers found."
                  : "All customers are already linked to this project."}
              </CommandEmpty>
            ) : (
              <CommandGroup>
                {availableCustomers.map((customer) => (
                  <CommandItem
                    key={customer.id}
                    value={`${customer.name} ${customer.email}`}
                    onSelect={() => handleLinkCustomer(customer.id)}
                    disabled={isLinking}
                    className="gap-3 py-3 data-[selected=true]:bg-olive-100 dark:data-[selected=true]:bg-olive-800"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">{customer.name}</p>
                      {customer.email && (
                        <p className="truncate text-xs text-olive-600 dark:text-olive-400">
                          {customer.email}
                        </p>
                      )}
                    </div>
                  </CommandItem>
                ))}
              </CommandGroup>
            )}
          </CommandList>
        </Command>

        {linkError && (
          <p className="px-4 pb-4 text-sm text-destructive">{linkError}</p>
        )}
      </DialogContent>
    </Dialog>
  );
}
