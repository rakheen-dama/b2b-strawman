import { auth } from "@clerk/nextjs/server";
import { api, handleApiError, getFieldDefinitions } from "@/lib/api";
import type { Customer, CustomerStatus, FieldDefinitionResponse } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { CreateCustomerDialog } from "@/components/customers/create-customer-dialog";
import { CustomFieldBadges } from "@/components/field-definitions/CustomFieldBadges";
import { formatDate } from "@/lib/format";
import { UserRound } from "lucide-react";
import Link from "next/link";

const STATUS_BADGE: Record<CustomerStatus, { label: string; variant: "success" | "neutral" }> = {
  ACTIVE: { label: "Active", variant: "success" },
  ARCHIVED: { label: "Archived", variant: "neutral" },
};

export default async function CustomersPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let customers: Customer[] = [];
  try {
    customers = await api.get<Customer[]>("/api/customers");
  } catch (error) {
    handleApiError(error);
  }

  // Fetch field definitions for custom field badges on customer rows
  let customerFieldDefs: FieldDefinitionResponse[] = [];
  try {
    customerFieldDefs = await getFieldDefinitions("CUSTOMER");
  } catch {
    // Non-fatal: custom field badges won't render
  }

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">Customers</h1>
          <span className="rounded-full bg-olive-200 px-2.5 py-0.5 text-sm text-olive-700 dark:bg-olive-800 dark:text-olive-300">
            {customers.length}
          </span>
        </div>
        {isAdmin && <CreateCustomerDialog slug={slug} />}
      </div>

      {/* Customer Table or Empty State */}
      {customers.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <UserRound className="size-16 text-olive-300 dark:text-olive-700" />
          <h2 className="mt-6 font-display text-xl text-olive-900 dark:text-olive-100">
            No customers yet
          </h2>
          <p className="mt-2 text-sm text-olive-600 dark:text-olive-400">
            {isAdmin
              ? "Add your first customer to get started."
              : "No customers have been added yet."}
          </p>
          {isAdmin && (
            <div className="mt-6">
              <CreateCustomerDialog slug={slug} />
            </div>
          )}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-olive-200 dark:border-olive-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Name
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Email
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400 sm:table-cell">
                  Phone
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Status
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400 lg:table-cell">
                  Created
                </th>
              </tr>
            </thead>
            <tbody>
              {customers.map((customer) => {
                const statusBadge = STATUS_BADGE[customer.status];
                return (
                  <tr
                    key={customer.id}
                    className="group border-b border-olive-100 transition-colors last:border-0 hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900/50"
                  >
                    <td className="px-4 py-3">
                      <Link
                        href={`/org/${slug}/customers/${customer.id}`}
                        className="font-medium text-olive-950 hover:underline dark:text-olive-50"
                      >
                        {customer.name}
                      </Link>
                      {customer.customFields && Object.keys(customer.customFields).length > 0 && (
                        <CustomFieldBadges
                          customFields={customer.customFields}
                          fieldDefinitions={customerFieldDefs}
                          maxFields={2}
                        />
                      )}
                    </td>
                    <td className="px-4 py-3 text-sm text-olive-600 dark:text-olive-400">
                      {customer.email}
                    </td>
                    <td className="hidden px-4 py-3 text-sm text-olive-600 dark:text-olive-400 sm:table-cell">
                      {customer.phone || "\u2014"}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
                    </td>
                    <td className="hidden px-4 py-3 text-sm text-olive-400 dark:text-olive-600 lg:table-cell">
                      {formatDate(customer.createdAt)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
