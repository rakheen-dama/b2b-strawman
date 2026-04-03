import { DemoProvisionForm } from "@/app/(app)/platform-admin/demo/demo-provision-form";
import { DemoTenantsTable } from "@/components/billing/demo-tenants-table";
import { listDemoTenants } from "@/app/(app)/platform-admin/demo/actions";

export default async function DemoProvisionPage() {
  const result = await listDemoTenants();
  const tenants = result.success && result.data ? result.data : [];

  return (
    <div className="mx-auto max-w-4xl px-6 py-10">
      <h1 className="font-display text-2xl font-bold tracking-tight">
        Demo Provisioning
      </h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Create a demo tenant with optional seed data for demonstrations and
        testing.
      </p>
      <div className="mt-8 max-w-2xl">
        <DemoProvisionForm />
      </div>

      <div className="mt-12">
        <h2 className="font-display text-xl font-semibold tracking-tight">
          Demo Tenants
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Manage existing demo tenants. Reseed data or delete tenants when no
          longer needed.
        </p>
        <div className="mt-6">
          <DemoTenantsTable tenants={tenants} />
        </div>
      </div>
    </div>
  );
}
