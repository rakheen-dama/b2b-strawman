import { Receipt } from "lucide-react";

import { fetchInvoices } from "@/lib/api/invoices";
import { handleApiError } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";
import { InvoiceList } from "@/components/invoices/invoice-list";
import { CreateInvoiceDialog } from "@/components/invoices/create-invoice-dialog";
import { createInvoiceDraftAction } from "./actions";
import type { InvoiceStatus } from "@/lib/types";

interface InvoicesPageProps {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ status?: string }>;
}

async function fetchCustomersList(): Promise<
  { id: string; name: string }[]
> {
  // Fetch customers for the create dialog
  const { api } = await import("@/lib/api");
  try {
    return await api.get<{ id: string; name: string }[]>("/api/customers");
  } catch {
    return [];
  }
}

export default async function InvoicesPage({
  params,
  searchParams,
}: InvoicesPageProps) {
  const { slug } = await params;
  const { status } = await searchParams;

  let invoicesData;
  let customers;
  try {
    [invoicesData, customers] = await Promise.all([
      fetchInvoices({ size: 200 }),
      fetchCustomersList(),
    ]);
  } catch (error) {
    handleApiError(error);
  }

  const activeStatus = (status as InvoiceStatus) || "ALL";

  return (
    <div className="space-y-6">
      <PageHeader
        title="Invoices"
        count={invoicesData.content.length}
        actions={
          <CreateInvoiceDialog
            orgSlug={slug}
            customers={customers}
            onCreateAction={createInvoiceDraftAction}
          />
        }
      />

      <InvoiceList
        invoices={invoicesData.content}
        orgSlug={slug}
        activeStatus={activeStatus}
      />
    </div>
  );
}
