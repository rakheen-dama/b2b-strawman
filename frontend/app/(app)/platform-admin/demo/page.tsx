import { PageHeader } from "@b2mash/ui/page-header";
import { DemoProvisionForm } from "@/app/(app)/platform-admin/demo/demo-provision-form";
import { DemoTenantsTable } from "@/components/billing/demo-tenants-table";
import { listDemoTenants } from "@/app/(app)/platform-admin/demo/actions";

export default async function DemoProvisionPage() {
  const result = await listDemoTenants();
  const tenants = result.success && result.data ? result.data : [];

  return (
    <div className="mx-auto max-w-7xl px-6 py-10">
      <PageHeader
        title="Demo Provisioning"
        description="Create a demo tenant with optional seed data for demonstrations and testing."
      />
      <div className="mt-8 max-w-2xl">
        <DemoProvisionForm />
      </div>

      <div className="mt-12">
        <h2 className="font-display text-xl font-semibold tracking-tight">Demo Tenants</h2>
        <p className="text-muted-foreground mt-1 text-sm">
          Manage existing demo tenants. Reseed data or delete tenants when no longer needed.
        </p>
        <div className="mt-6">
          <DemoTenantsTable tenants={tenants} />
        </div>
      </div>
    </div>
  );
}
