"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
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
import { Plus } from "lucide-react";

interface CustomerOption {
  id: string;
  name: string;
}

interface CreateInvoiceButtonProps {
  customers: CustomerOption[];
  slug: string;
}

export function CreateInvoiceButton({ customers, slug }: CreateInvoiceButtonProps) {
  const [open, setOpen] = useState(false);
  const router = useRouter();

  function handleSelect(customerId: string) {
    setOpen(false);
    router.push(`/org/${slug}/customers/${customerId}?tab=invoices`);
  }

  if (customers.length === 0) return null;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="accent" size="sm">
          <Plus className="mr-1.5 size-4" />
          New Invoice
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-64 p-0" align="end">
        <Command>
          <CommandInput placeholder="Search customers..." />
          <CommandList>
            <CommandEmpty>No customers found.</CommandEmpty>
            <CommandGroup>
              {customers.map((customer) => (
                <CommandItem
                  key={customer.id}
                  value={customer.name}
                  onSelect={() => handleSelect(customer.id)}
                >
                  {customer.name}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
