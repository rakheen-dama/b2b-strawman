import { PricingTable } from "@clerk/nextjs";

export default function BillingPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Billing</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Manage your organization&apos;s subscription and billing.
        </p>
      </div>
      <PricingTable for="organization" />
    </div>
  );
}
