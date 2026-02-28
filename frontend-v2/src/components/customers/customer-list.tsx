"use client";

import * as React from "react";
import { useRouter, useParams } from "next/navigation";
import { Users } from "lucide-react";

import type { Customer, LifecycleStatus } from "@/lib/types";
import { DataTable } from "@/components/ui/data-table";
import { DataTableToolbar } from "@/components/ui/data-table-toolbar";
import { DataTableEmpty } from "@/components/ui/data-table-empty";
import { Button } from "@/components/ui/button";
import { getCustomerColumns } from "@/components/customers/customer-columns";
import { CreateCustomerDialog } from "@/components/customers/create-customer-dialog";
import { EditCustomerDialog } from "@/components/customers/edit-customer-dialog";
import { cn } from "@/lib/utils";

const LIFECYCLE_FILTERS: { label: string; value: LifecycleStatus | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "Prospects", value: "PROSPECT" },
  { label: "Onboarding", value: "ONBOARDING" },
  { label: "Active", value: "ACTIVE" },
  { label: "Dormant", value: "DORMANT" },
  { label: "Offboarding", value: "OFFBOARDING" },
  { label: "Offboarded", value: "OFFBOARDED" },
];

interface CustomerListProps {
  customers: Customer[];
  lifecycleSummary: Record<string, number>;
}

export function CustomerList({
  customers,
  lifecycleSummary,
}: CustomerListProps) {
  const router = useRouter();
  const params = useParams<{ slug: string }>();
  const [search, setSearch] = React.useState("");
  const [activeFilter, setActiveFilter] = React.useState<
    LifecycleStatus | "ALL"
  >("ALL");
  const [createOpen, setCreateOpen] = React.useState(false);
  const [editCustomer, setEditCustomer] = React.useState<Customer | null>(null);

  const filteredCustomers = React.useMemo(() => {
    let result = customers;

    if (activeFilter !== "ALL") {
      result = result.filter((c) => c.lifecycleStatus === activeFilter);
    }

    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter(
        (c) =>
          c.name.toLowerCase().includes(q) ||
          c.email.toLowerCase().includes(q)
      );
    }

    return result;
  }, [customers, activeFilter, search]);

  const columns = React.useMemo(
    () =>
      getCustomerColumns({
        onEdit: (c) => setEditCustomer(c),
        onArchive: () => router.refresh(),
        onUnarchive: () => router.refresh(),
      }),
    [router]
  );

  function handleRowClick(customer: Customer) {
    router.push(`/org/${params.slug}/customers/${customer.id}`);
  }

  return (
    <>
      {/* Lifecycle filter tabs */}
      <div className="flex items-center gap-1 overflow-x-auto border-b border-slate-200 pb-px">
        {LIFECYCLE_FILTERS.map((filter) => {
          const count =
            filter.value === "ALL"
              ? customers.length
              : lifecycleSummary[filter.value] ?? 0;
          const isActive = activeFilter === filter.value;

          return (
            <button
              key={filter.value}
              onClick={() => setActiveFilter(filter.value)}
              className={cn(
                "relative whitespace-nowrap px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "text-slate-900"
                  : "text-slate-500 hover:text-slate-700"
              )}
            >
              {filter.label}
              <span
                className={cn(
                  "ml-1.5 rounded-full px-1.5 py-0.5 text-[10px] tabular-nums",
                  isActive
                    ? "bg-slate-900 text-white"
                    : "bg-slate-100 text-slate-500"
                )}
              >
                {count}
              </span>
              {isActive && (
                <span className="absolute inset-x-0 -bottom-px h-0.5 bg-teal-600" />
              )}
            </button>
          );
        })}
      </div>

      <DataTableToolbar
        searchPlaceholder="Search customers..."
        searchValue={search}
        onSearchChange={setSearch}
      />

      <DataTable
        columns={columns}
        data={filteredCustomers}
        onRowClick={handleRowClick}
        emptyState={
          <DataTableEmpty
            icon={<Users />}
            title="No customers yet"
            description="Create your first customer to start managing your client relationships."
            action={
              <Button onClick={() => setCreateOpen(true)}>
                New Customer
              </Button>
            }
          />
        }
      />

      <CreateCustomerDialog open={createOpen} onOpenChange={setCreateOpen} />
      <EditCustomerDialog
        customer={editCustomer}
        open={!!editCustomer}
        onOpenChange={(open) => {
          if (!open) setEditCustomer(null);
        }}
      />
    </>
  );
}
