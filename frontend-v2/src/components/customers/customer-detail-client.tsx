"use client";

import * as React from "react";
import { useParams } from "next/navigation";
import { Pencil } from "lucide-react";

import type {
  Customer,
  ChecklistInstanceResponse,
  LifecycleHistoryEntry,
  InvoiceResponse,
  Document,
} from "@/lib/types";
import { PageHeader } from "@/components/layout/page-header";
import { DetailPage } from "@/components/layout/detail-page";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { CustomerOverview } from "@/components/customers/customer-overview";
import { CustomerProjectsTab } from "@/components/customers/customer-projects-tab";
import { LifecycleTab } from "@/components/customers/lifecycle-tab";
import { CustomerInvoicesTab } from "@/components/customers/customer-invoices-tab";
import { CustomerDocsTab } from "@/components/customers/customer-docs-tab";
import { EditCustomerDialog } from "@/components/customers/edit-customer-dialog";

interface LinkedProject {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
}

interface CustomerDetailClientProps {
  customer: Customer;
  projects: LinkedProject[];
  checklist: ChecklistInstanceResponse | null;
  history: LifecycleHistoryEntry[];
  invoices: InvoiceResponse[];
  documents: Document[];
}

export function CustomerDetailClient({
  customer,
  projects,
  checklist,
  history,
  invoices,
  documents,
}: CustomerDetailClientProps) {
  const params = useParams<{ slug: string }>();
  const [editOpen, setEditOpen] = React.useState(false);

  const header = (
    <div className="space-y-2">
      <PageHeader
        title={customer.name}
        backHref={`/org/${params.slug}/customers`}
        actions={
          <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
            <Pencil className="size-3.5" />
            Edit
          </Button>
        }
      />
      <div className="ml-11 flex items-center gap-3">
        <StatusBadge status={customer.lifecycleStatus ?? customer.status} />
        {customer.customerType && (
          <span className="text-sm text-slate-500">
            {customer.customerType.charAt(0) +
              customer.customerType.slice(1).toLowerCase()}
          </span>
        )}
        <span className="text-sm text-slate-400">{customer.email}</span>
      </div>
    </div>
  );

  const tabs = [
    {
      id: "overview",
      label: "Overview",
      content: <CustomerOverview customer={customer} />,
    },
    {
      id: "projects",
      label: "Projects",
      count: projects.length,
      content: <CustomerProjectsTab projects={projects} />,
    },
    {
      id: "lifecycle",
      label: "Lifecycle",
      content: (
        <LifecycleTab
          customer={customer}
          checklist={checklist}
          history={history}
        />
      ),
    },
    {
      id: "invoices",
      label: "Invoices",
      count: invoices.length,
      content: <CustomerInvoicesTab invoices={invoices} />,
    },
    {
      id: "docs",
      label: "Docs",
      count: documents.length,
      content: <CustomerDocsTab documents={documents} />,
    },
  ];

  return (
    <>
      <DetailPage header={header} tabs={tabs} />
      <EditCustomerDialog
        customer={customer}
        open={editOpen}
        onOpenChange={setEditOpen}
      />
    </>
  );
}
