import { DemoProvisionForm } from "@/app/(app)/platform-admin/demo/demo-provision-form";

export default function DemoProvisionPage() {
  return (
    <div className="mx-auto max-w-2xl px-6 py-10">
      <h1 className="text-2xl font-bold tracking-tight">
        Demo Provisioning
      </h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Create a demo tenant with optional seed data for demonstrations and
        testing.
      </p>
      <div className="mt-8">
        <DemoProvisionForm />
      </div>
    </div>
  );
}
