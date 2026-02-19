"use client";

import { useEffect, useState, useTransition } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
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
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Plus, Check } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  createDataRequest,
  fetchCustomersForSelector,
} from "@/app/(app)/org/[slug]/compliance/requests/actions";
import type { Customer, DataRequestType } from "@/lib/types";

interface CreateDataRequestDialogProps {
  slug: string;
}

const REQUEST_TYPES: { value: DataRequestType; label: string }[] = [
  { value: "ACCESS", label: "Access Request" },
  { value: "DELETION", label: "Deletion Request" },
  { value: "CORRECTION", label: "Correction Request" },
  { value: "OBJECTION", label: "Objection Request" },
];

export function CreateDataRequestDialog({ slug }: CreateDataRequestDialogProps) {
  const [open, setOpen] = useState(false);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [isLoadingCustomers, setIsLoadingCustomers] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null);
  const [requestType, setRequestType] = useState<DataRequestType | "">("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const [customerSearchOpen, setCustomerSearchOpen] = useState(false);

  useEffect(() => {
    if (!open) return;

    let cancelled = false;

    fetchCustomersForSelector()
      .then((data) => {
        if (!cancelled) setCustomers(data.filter((c) => c.status === "ACTIVE"));
      })
      .catch(() => {
        if (!cancelled) setFetchError("Failed to load customers.");
      })
      .finally(() => {
        if (!cancelled) setIsLoadingCustomers(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open]);

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      // Reset state when opening
      setSelectedCustomer(null);
      setRequestType("");
      setDescription("");
      setError(null);
      setFetchError(null);
      setCustomers([]);
      setCustomerSearchOpen(false);
      setIsLoadingCustomers(true);
    }
    setOpen(newOpen);
  }

  const canSubmit =
    selectedCustomer !== null && requestType !== "" && description.trim().length > 0;

  function handleSubmit() {
    if (!canSubmit || !selectedCustomer || !requestType) return;
    setError(null);

    startTransition(async () => {
      try {
        const result = await createDataRequest(
          slug,
          selectedCustomer.id,
          requestType,
          description.trim(),
        );
        if (result.success) {
          setOpen(false);
        } else {
          setError(result.error ?? "Failed to create data request.");
        }
      } catch {
        setError("An unexpected error occurred.");
      }
    });
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus className="mr-1.5 size-4" />
          New Request
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>New Data Request</DialogTitle>
          <DialogDescription>
            Create a new data subject request (DSR) on behalf of a customer.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Customer selector */}
          <div className="space-y-1.5">
            <Label>Customer</Label>
            {fetchError ? (
              <p className="text-sm text-destructive">{fetchError}</p>
            ) : (
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setCustomerSearchOpen((prev) => !prev)}
                  className={cn(
                    "flex w-full items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background",
                    "focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
                    "disabled:cursor-not-allowed disabled:opacity-50",
                  )}
                  disabled={isLoadingCustomers || isPending}
                >
                  <span
                    className={cn(
                      selectedCustomer ? "text-foreground" : "text-muted-foreground",
                    )}
                  >
                    {isLoadingCustomers
                      ? "Loading customers..."
                      : selectedCustomer
                        ? selectedCustomer.name
                        : "Select a customer..."}
                  </span>
                </button>
                {customerSearchOpen && (
                  <div className="absolute z-50 mt-1 w-full rounded-md border bg-popover shadow-md">
                    <Command>
                      <CommandInput placeholder="Search customers..." />
                      <CommandList>
                        <CommandEmpty>No customers found.</CommandEmpty>
                        <CommandGroup>
                          {customers.map((customer) => (
                            <CommandItem
                              key={customer.id}
                              value={`${customer.name} ${customer.email}`}
                              onSelect={() => {
                                setSelectedCustomer(customer);
                                setCustomerSearchOpen(false);
                              }}
                              className="gap-2"
                            >
                              <Check
                                className={cn(
                                  "size-4 shrink-0",
                                  selectedCustomer?.id === customer.id
                                    ? "opacity-100"
                                    : "opacity-0",
                                )}
                              />
                              <div className="min-w-0 flex-1">
                                <p className="truncate text-sm font-medium">{customer.name}</p>
                                {customer.email && (
                                  <p className="truncate text-xs text-slate-500 dark:text-slate-400">
                                    {customer.email}
                                  </p>
                                )}
                              </div>
                            </CommandItem>
                          ))}
                        </CommandGroup>
                      </CommandList>
                    </Command>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Request type */}
          <div className="space-y-1.5">
            <Label htmlFor="request-type">Request Type</Label>
            <select
              id="request-type"
              value={requestType}
              onChange={(e) => setRequestType(e.target.value as DataRequestType | "")}
              disabled={isPending}
              className={cn(
                "flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background",
                "focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
                "disabled:cursor-not-allowed disabled:opacity-50",
                requestType === "" ? "text-muted-foreground" : "text-foreground",
              )}
            >
              <option value="" disabled>
                Select request type...
              </option>
              {REQUEST_TYPES.map((type) => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </select>
          </div>

          {/* Description */}
          <div className="space-y-1.5">
            <Label htmlFor="description">Description</Label>
            <Textarea
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Describe the data request..."
              className="resize-none"
              rows={3}
              disabled={isPending}
            />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="plain"
            onClick={() => setOpen(false)}
            disabled={isPending}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={isPending || !canSubmit}
          >
            {isPending ? "Creating..." : "Create Request"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
