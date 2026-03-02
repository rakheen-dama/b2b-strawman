import { Suspense } from "react";
import { getAuthContext } from "@/lib/auth";
import { api, handleApiError, getFieldDefinitions, getViews, getTags } from "@/lib/api";
import type { Customer, CustomerStatus, CompletenessScore, FieldDefinitionResponse, LifecycleStatus, SavedViewResponse, TagResponse } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { CreateCustomerDialog } from "@/components/customers/create-customer-dialog";
import { CompletenessBadge } from "@/components/customers/completeness-badge";
import { CustomFieldBadges } from "@/components/field-definitions/CustomFieldBadges";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import { createSavedViewAction } from "./view-actions";
import { fetchCompletenessSummary } from "./actions";
import { formatDate } from "@/lib/format";
import { LifecycleStatusBadge } from "@/components/compliance/LifecycleStatusBadge";
import { UserRound } from "lucide-react";
import Link from "next/link";

const VALID_LIFECYCLE_STATUSES: ReadonlySet<LifecycleStatus> = new Set([
  "PROSPECT",
  "ONBOARDING",
  "ACTIVE",
  "DORMANT",
  "OFFBOARDING",
  "OFFBOARDED",
]);

const LIFECYCLE_FILTER_OPTIONS: Array<{ value: LifecycleStatus | ""; label: string }> = [
  { value: "", label: "All" },
  { value: "PROSPECT", label: "Prospect" },
  { value: "ONBOARDING", label: "Onboarding" },
  { value: "ACTIVE", label: "Active" },
  { value: "DORMANT", label: "Dormant" },
  { value: "OFFBOARDING", label: "Offboarding" },
  { value: "OFFBOARDED", label: "Offboarded" },
];

const STATUS_BADGE: Record<CustomerStatus, { label: string; variant: "success" | "neutral" }> = {
  ACTIVE: { label: "Active", variant: "success" },
  ARCHIVED: { label: "Archived", variant: "neutral" },
};

function buildFilteredUrl(
  slug: string,
  currentParams: Record<string, string | string[] | undefined>,
  overrides: Record<string, string | undefined>
): string {
  const merged: Record<string, string> = {};
  // Carry forward existing string params
  for (const [key, val] of Object.entries(currentParams)) {
    if (typeof val === "string") {
      merged[key] = val;
    }
  }
  // Apply overrides (undefined = remove key)
  for (const [key, val] of Object.entries(overrides)) {
    if (val === undefined) {
      delete merged[key];
    } else {
      merged[key] = val;
    }
  }
  const qs = new URLSearchParams(merged).toString();
  return `/org/${slug}/customers${qs ? `?${qs}` : ""}`;
}

export default async function CustomersPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  const currentViewId = typeof resolvedSearchParams.view === "string" ? resolvedSearchParams.view : null;
  const rawLifecycleFilter = typeof resolvedSearchParams.lifecycleStatus === "string" ? resolvedSearchParams.lifecycleStatus : null;
  const lifecycleFilter: LifecycleStatus | null =
    rawLifecycleFilter !== null && VALID_LIFECYCLE_STATUSES.has(rawLifecycleFilter as LifecycleStatus)
      ? (rawLifecycleFilter as LifecycleStatus)
      : null;

  const showIncomplete = resolvedSearchParams.showIncomplete === "true";
  const sortBy = typeof resolvedSearchParams.sortBy === "string" ? resolvedSearchParams.sortBy : null;
  const sortDir = typeof resolvedSearchParams.sortDir === "string" ? resolvedSearchParams.sortDir : "asc";

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
  } else if (lifecycleFilter) {
    customersEndpoint = `/api/customers?lifecycleStatus=${lifecycleFilter}`;
  }

  let customers: Customer[] = [];
  try {
    customers = await api.get<Customer[]>(customersEndpoint);
  } catch (error) {
    handleApiError(error);
  }

  // Fetch completeness summary for loaded customers
  let completenessScores: Record<string, CompletenessScore> = {};
  if (customers.length > 0) {
    try {
      completenessScores = await fetchCompletenessSummary(customers.map((c) => c.id));
    } catch {
      // Non-fatal: completeness column will show N/A
    }
  }

  // Apply completeness filter (client-side from already-fetched data)
  let displayCustomers = customers;
  if (showIncomplete) {
    displayCustomers = customers.filter((c) => {
      const score = completenessScores[c.id];
      return !score || score.percentage < 100;
    });
  }

  // Apply completeness sort
  if (sortBy === "completeness") {
    displayCustomers = [...displayCustomers].sort((a, b) => {
      const scoreA = completenessScores[a.id]?.percentage ?? -1;
      const scoreB = completenessScores[b.id]?.percentage ?? -1;
      return sortDir === "asc" ? scoreA - scoreB : scoreB - scoreA;
    });
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

  // Build toggle URLs for completeness filter
  const incompleteToggleUrl = showIncomplete
    ? buildFilteredUrl(slug, resolvedSearchParams, { showIncomplete: undefined })
    : buildFilteredUrl(slug, resolvedSearchParams, { showIncomplete: "true" });

  // Build sort toggle URL for completeness column
  const completenessSortUrl =
    sortBy === "completeness" && sortDir === "asc"
      ? buildFilteredUrl(slug, resolvedSearchParams, { sortBy: "completeness", sortDir: "desc" })
      : sortBy === "completeness" && sortDir === "desc"
        ? buildFilteredUrl(slug, resolvedSearchParams, { sortBy: undefined, sortDir: undefined })
        : buildFilteredUrl(slug, resolvedSearchParams, { sortBy: "completeness", sortDir: "asc" });

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

      {/* Lifecycle Status Filter */}
      <div className="flex flex-wrap items-center gap-2">
        {LIFECYCLE_FILTER_OPTIONS.map((option) => {
          const isActive = lifecycleFilter === option.value || (!lifecycleFilter && option.value === "");
          const href = option.value
            ? `/org/${slug}/customers?lifecycleStatus=${option.value}`
            : `/org/${slug}/customers`;
          return (
            <Link
              key={option.value}
              href={href}
              className={`rounded-full px-3 py-1 text-sm font-medium transition-colors ${
                isActive
                  ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
                  : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700"
              }`}
            >
              {option.label}
            </Link>
          );
        })}

        {/* Completeness filter toggle */}
        <span className="mx-1 h-4 w-px bg-slate-300 dark:bg-slate-700" />
        <Link
          href={incompleteToggleUrl}
          className={`rounded-full px-3 py-1 text-sm font-medium transition-colors ${
            showIncomplete
              ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
              : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700"
          }`}
        >
          Show incomplete
        </Link>
      </div>

      {/* Customer Table or Empty State */}
      {displayCustomers.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <UserRound className="size-16 text-slate-300 dark:text-slate-700" />
          <h2 className="mt-6 font-display text-xl text-slate-900 dark:text-slate-100">
            {showIncomplete ? "All customers are complete" : "No customers yet"}
          </h2>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
            {showIncomplete
              ? "All customers have 100% completeness."
              : isAdmin
                ? "Add your first customer to get started."
                : "No customers have been added yet."}
          </p>
          {!showIncomplete && isAdmin && (
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
                  Lifecycle
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Status
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  <Link
                    href={completenessSortUrl}
                    className="inline-flex items-center gap-1 hover:text-slate-900 dark:hover:text-slate-200"
                  >
                    Completeness
                    {sortBy === "completeness" && (
                      <span className="text-xs">{sortDir === "asc" ? "\u2191" : "\u2193"}</span>
                    )}
                  </Link>
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400 lg:table-cell">
                  Created
                </th>
              </tr>
            </thead>
            <tbody>
              {displayCustomers.map((customer) => {
                const statusBadge = STATUS_BADGE[customer.status];
                const score = completenessScores[customer.id];
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
                      {customer.lifecycleStatus && (
                        <LifecycleStatusBadge status={customer.lifecycleStatus} />
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      {score ? (
                        <CompletenessBadge score={score} />
                      ) : (
                        <Badge variant="neutral">N/A</Badge>
                      )}
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
