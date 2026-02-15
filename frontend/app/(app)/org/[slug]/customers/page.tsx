import { Suspense } from "react";
import { auth } from "@clerk/nextjs/server";
import { api, handleApiError, getFieldDefinitions, getViews, getTags } from "@/lib/api";
import type { Customer, CustomerStatus, FieldDefinitionResponse, SavedViewResponse, TagResponse } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { CreateCustomerDialog } from "@/components/customers/create-customer-dialog";
import { CustomFieldBadges } from "@/components/field-definitions/CustomFieldBadges";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import { createSavedViewAction } from "./view-actions";
import { formatDate } from "@/lib/format";
import { UserRound } from "lucide-react";
import Link from "next/link";

const STATUS_BADGE: Record<CustomerStatus, { label: string; variant: "success" | "neutral" }> = {
  ACTIVE: { label: "Active", variant: "success" },
  ARCHIVED: { label: "Archived", variant: "neutral" },
};

export default async function CustomersPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  const currentViewId = typeof resolvedSearchParams.view === "string" ? resolvedSearchParams.view : null;

  // Fetch saved views for customer entity type
  let views: SavedViewResponse[] = [];
  try {
    views = await getViews("CUSTOMER");
  } catch {
    // Non-fatal: view selector won't show saved views
  }

  // Build query string with view filters
  let customersEndpoint = "/api/customers";
  if (currentViewId) {
    customersEndpoint = `/api/customers?view=${currentViewId}`;
  }

  let customers: Customer[] = [];
  try {
    customers = await api.get<Customer[]>(customersEndpoint);
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

  // Fetch tags for filter UI
  let allTags: TagResponse[] = [];
  try {
    allTags = await getTags();
  } catch {
    // Non-fatal
  }

  async function handleCreateView(req: import("@/lib/types").CreateSavedViewRequest) {
    "use server";
    return createSavedViewAction(slug, req);
  }

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Customers</h1>
          <span className="rounded-full bg-slate-200 px-2.5 py-0.5 text-sm text-slate-700 dark:bg-slate-800 dark:text-slate-300">
            {customers.length}
          </span>
        </div>
        {isAdmin && <CreateCustomerDialog slug={slug} />}
      </div>

      {/* Saved View Selector */}
      <Suspense fallback={null}>
        <ViewSelectorClient
          entityType="CUSTOMER"
          views={views}
          canCreate={true}
          canCreateShared={isAdmin}
          slug={slug}
          allTags={allTags}
          fieldDefinitions={customerFieldDefs}
          onSave={handleCreateView}
        />
      </Suspense>

      {/* Customer Table or Empty State */}
      {customers.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <UserRound className="size-16 text-slate-300 dark:text-slate-700" />
          <h2 className="mt-6 font-display text-xl text-slate-900 dark:text-slate-100">
            No customers yet
          </h2>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
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
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Name
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Email
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400 sm:table-cell">
                  Phone
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Status
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400 lg:table-cell">
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
                    className="group border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                  >
                    <td className="px-4 py-3">
                      <Link
                        href={`/org/${slug}/customers/${customer.id}`}
                        className="font-medium text-slate-950 hover:underline dark:text-slate-50"
                      >
                        {customer.name}
                      </Link>
                      {customer.tags && customer.tags.length > 0 && (
                        <div className="mt-1 flex flex-wrap gap-1">
                          {customer.tags.slice(0, 3).map((tag) => (
                            <Badge
                              key={tag.id}
                              variant="outline"
                              className="text-xs"
                              style={
                                tag.color
                                  ? { borderColor: tag.color, color: tag.color }
                                  : undefined
                              }
                            >
                              {tag.name}
                            </Badge>
                          ))}
                          {customer.tags.length > 3 && (
                            <Badge variant="neutral" className="text-xs">
                              +{customer.tags.length - 3}
                            </Badge>
                          )}
                        </div>
                      )}
                      {customer.customFields && Object.keys(customer.customFields).length > 0 && (
                        <CustomFieldBadges
                          customFields={customer.customFields}
                          fieldDefinitions={customerFieldDefs}
                          maxFields={2}
                        />
                      )}
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                      {customer.email}
                    </td>
                    <td className="hidden px-4 py-3 text-sm text-slate-600 dark:text-slate-400 sm:table-cell">
                      {customer.phone || "\u2014"}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
                    </td>
                    <td className="hidden px-4 py-3 text-sm text-slate-400 dark:text-slate-600 lg:table-cell">
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
