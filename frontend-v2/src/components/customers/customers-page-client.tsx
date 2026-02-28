"use client";

import * as React from "react";
import { Plus } from "lucide-react";

import type { Customer } from "@/lib/types";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { CustomerList } from "@/components/customers/customer-list";
import { CreateCustomerDialog } from "@/components/customers/create-customer-dialog";

interface CustomersPageClientProps {
  customers: Customer[];
  lifecycleSummary: Record<string, number>;
}

export function CustomersPageClient({
  customers,
  lifecycleSummary,
}: CustomersPageClientProps) {
  const [createOpen, setCreateOpen] = React.useState(false);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Customers"
        count={customers.length}
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="size-4" />
            New Customer
          </Button>
        }
      />

      <CustomerList
        customers={customers}
        lifecycleSummary={lifecycleSummary}
      />

      <CreateCustomerDialog open={createOpen} onOpenChange={setCreateOpen} />
    </div>
  );
}
