import { listBillingTenants } from "./actions";
import { BillingTenantsTable } from "@/components/billing/billing-tenants-table";

export default async function BillingPage() {
  const result = await listBillingTenants();
  const tenants = result.data ?? [];

  return (
    <div className="mx-auto max-w-7xl px-6 py-10">
      <h1 className="text-2xl font-bold tracking-tight">Billing Management</h1>
      <p className="mt-2 text-sm text-muted-foreground">
        View and manage tenant billing status, methods, and trial periods.
      </p>
      {!result.success && (
        <div className="mt-4 rounded-md bg-red-50 p-4 text-sm text-red-700">
          Failed to load billing data: {result.error}
        </div>
      )}
      <div className="mt-8">
        <BillingTenantsTable tenants={tenants} />
      </div>
    </div>
  );
}
