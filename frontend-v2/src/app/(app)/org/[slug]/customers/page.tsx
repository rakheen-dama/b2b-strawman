import { CustomersPageClient } from "@/components/customers/customers-page-client";
import { getCustomers, getLifecycleSummary } from "./actions";

export default async function CustomersPage() {
  const [customers, lifecycleSummary] = await Promise.all([
    getCustomers(),
    getLifecycleSummary(),
  ]);

  return (
    <CustomersPageClient
      customers={customers}
      lifecycleSummary={lifecycleSummary}
    />
  );
}
