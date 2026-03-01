import { CustomerDetailClient } from "@/components/customers/customer-detail-client";
import {
  getCustomer,
  getCustomerProjects,
  getLifecycleHistory,
  getCustomerChecklist,
  getCustomerInvoices,
  getCustomerDocuments,
  getCustomerProposals,
} from "./actions";

export default async function CustomerDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { id } = await params;

  const [
    customer,
    projects,
    history,
    checklist,
    invoices,
    documents,
    proposalsData,
  ] = await Promise.all([
    getCustomer(id),
    getCustomerProjects(id),
    getLifecycleHistory(id),
    getCustomerChecklist(id),
    getCustomerInvoices(id),
    getCustomerDocuments(id),
    getCustomerProposals(id),
  ]);

  return (
    <CustomerDetailClient
      customer={customer}
      projects={projects}
      checklist={checklist}
      history={history}
      invoices={invoices}
      documents={documents}
      proposals={proposalsData.content}
    />
  );
}
