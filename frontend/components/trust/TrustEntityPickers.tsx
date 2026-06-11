"use client";

/**
 * Shared customer + matter combobox pickers used by the trust transaction
 * dialogs (Record Deposit / Payment / Refund). Replaces the raw
 * `<Input placeholder="UUID"/>` fields that made these dialogs unusable
 * for real users — see qa_cycle/fix-specs/OBS-1001.md.
 *
 * The customer list is supplied by the caller (server-fetched, passed as a
 * prop). The matter list is fetched on demand via `fetchCustomerProjects`
 * (SWR) once a customer is picked. When `defaultCustomerId` /
 * `defaultProjectId` are supplied, the corresponding picker renders
 * pre-selected and disabled — used by the matter-detail Trust tab where
 * the customer + matter are already known from page context.
 */

import { useEffect, useState } from "react";
import { ChevronsUpDown, Check } from "lucide-react";
import useSWR from "swr";
import type { ControllerRenderProps, FieldValues, Path } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { fetchCustomerProjects } from "@/app/(app)/org/[slug]/customers/[id]/actions";
import { cn } from "@/lib/utils";

export type TrustPickerCustomer = { id: string; name: string; email: string };

interface TrustCustomerPickerProps<TFieldValues extends FieldValues> {
  field: ControllerRenderProps<TFieldValues, Path<TFieldValues>>;
  customers: TrustPickerCustomer[];
  /** When set, the trigger renders disabled and pre-populated. */
  defaultCustomerId?: string;
  /** Test id on the trigger button. */
  triggerTestId?: string;
}

export function TrustCustomerPicker<TFieldValues extends FieldValues>({
  field,
  customers,
  defaultCustomerId,
  triggerTestId,
}: TrustCustomerPickerProps<TFieldValues>) {
  const [open, setOpen] = useState(false);
  const locked = Boolean(defaultCustomerId);
  const selected = customers.find((c) => c.id === field.value);

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        if (locked) {
          setOpen(false);
          return;
        }
        setOpen(next);
      }}
      modal={false}
    >
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={locked}
          data-testid={triggerTestId}
          className="w-full justify-between font-normal"
        >
          {selected ? selected.name : "Select a client..."}
          <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[--radix-popover-trigger-width] p-0">
        <Command>
          <CommandInput placeholder="Search clients..." />
          <CommandList>
            <CommandEmpty>No clients found.</CommandEmpty>
            <CommandGroup>
              {customers.map((customer) => (
                <CommandItem
                  key={customer.id}
                  value={`${customer.name} ${customer.email}`}
                  onSelect={() => {
                    field.onChange(customer.id);
                    setOpen(false);
                  }}
                  className="gap-3 py-2"
                >
                  <Check
                    className={cn(
                      "size-4 shrink-0",
                      field.value === customer.id ? "opacity-100" : "opacity-0"
                    )}
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{customer.name}</p>
                    {customer.email && (
                      <p className="truncate text-xs text-slate-500">{customer.email}</p>
                    )}
                  </div>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

interface TrustMatterPickerProps<TFieldValues extends FieldValues> {
  field: ControllerRenderProps<TFieldValues, Path<TFieldValues>>;
  /** The currently selected customer id (drives the dependent fetch). */
  customerId: string;
  /** When set, the trigger renders disabled and pre-populated. */
  defaultProjectId?: string;
  /** Test id on the trigger button. */
  triggerTestId?: string;
}

export function TrustMatterPicker<TFieldValues extends FieldValues>({
  field,
  customerId,
  defaultProjectId,
  triggerTestId,
}: TrustMatterPickerProps<TFieldValues>) {
  const [open, setOpen] = useState(false);
  const locked = Boolean(defaultProjectId);

  // Fetch the customer's matters once a customer is picked. The server
  // action returns `Project[]`; we narrow to the fields the picker needs.
  const swrKey = customerId ? `trust-matters-for-customer-${customerId}` : null;
  const { data: matters, isLoading } = useSWR(swrKey, () => fetchCustomerProjects(customerId));

  // When the customer changes (and the picker is not locked), clear any
  // previously-selected matter so the form value never references a
  // matter belonging to the wrong customer.
  useEffect(() => {
    if (locked) return;
    if (field.value) {
      field.onChange("");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId]);

  const triggerDisabled = locked || !customerId;
  const selected = matters?.find((m) => m.id === field.value);

  let triggerLabel: string;
  if (selected) {
    triggerLabel = selected.referenceNumber
      ? `${selected.referenceNumber} — ${selected.name}`
      : selected.name;
  } else if (!customerId) {
    triggerLabel = "Select a client first";
  } else if (isLoading) {
    triggerLabel = "Loading matters...";
  } else {
    triggerLabel = "Select a matter (optional)...";
  }

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        if (triggerDisabled) {
          setOpen(false);
          return;
        }
        setOpen(next);
      }}
      modal={false}
    >
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={triggerDisabled}
          data-testid={triggerTestId}
          className="w-full justify-between font-normal"
        >
          {triggerLabel}
          <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[--radix-popover-trigger-width] p-0">
        <Command>
          <CommandInput placeholder="Search matters..." />
          <CommandList>
            <CommandEmpty>
              {isLoading ? "Loading..." : "No matters found for this client."}
            </CommandEmpty>
            <CommandGroup>
              {(matters ?? []).map((matter) => (
                <CommandItem
                  key={matter.id}
                  value={`${matter.name} ${matter.referenceNumber ?? ""}`}
                  onSelect={() => {
                    field.onChange(matter.id);
                    setOpen(false);
                  }}
                  className="gap-3 py-2"
                >
                  <Check
                    className={cn(
                      "size-4 shrink-0",
                      field.value === matter.id ? "opacity-100" : "opacity-0"
                    )}
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">
                      {matter.referenceNumber
                        ? `${matter.referenceNumber} — ${matter.name}`
                        : matter.name}
                    </p>
                  </div>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
