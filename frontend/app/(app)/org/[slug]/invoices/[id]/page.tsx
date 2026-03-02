import { getAuthContext } from "@/lib/auth";
import {
  api,
  handleApiError,
  getFieldDefinitions,
  getFieldGroups,
  getGroupMembers,
  getTemplates,
} from "@/lib/api";
import type {
  InvoiceResponse,
  TaxRateResponse,
  TemplateListResponse,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
  PaymentEvent,
} from "@/lib/types";
import { InvoiceDetailClient } from "@/components/invoices/invoice-detail-client";
import { GenerateDocumentDropdown } from "@/components/templates/GenerateDocumentDropdown";
import { GeneratedDocumentsList } from "@/components/templates/GeneratedDocumentsList";
import { CustomFieldSection } from "@/components/field-definitions/CustomFieldSection";
import { FieldGroupSelector } from "@/components/field-definitions/FieldGroupSelector";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";

export default async function InvoiceDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Invoice
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to view invoices. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  let invoice: InvoiceResponse;
  try {
    invoice = await api.get<InvoiceResponse>(`/api/invoices/${id}`);
  } catch (error) {
    handleApiError(error);
  }

  // Document templates for the "Generate Document" dropdown
  let invoiceTemplates: TemplateListResponse[] = [];
  try {
    invoiceTemplates = await getTemplates(undefined, "INVOICE");
  } catch {
    // Non-fatal: hide generate button if template fetch fails
  }

  // Payment events (only for SENT/PAID invoices)
  let paymentEvents: PaymentEvent[] = [];
  if (invoice!.status === "SENT" || invoice!.status === "PAID") {
    try {
      paymentEvents = await api.get<PaymentEvent[]>(
        `/api/invoices/${id}/payment-events`,
      );
    } catch {
      // Non-fatal: payment events section won't render data
    }
  }

  // Tax rates for the line item tax dropdown
  const taxRates = await api
    .get<TaxRateResponse[]>("/api/tax-rates")
    .catch(() => [] as TaxRateResponse[]);

  // Custom field definitions and groups for the Custom Fields section
  let invoiceFieldDefs: FieldDefinitionResponse[] = [];
  let invoiceFieldGroups: FieldGroupResponse[] = [];
  const invoiceGroupMembers: Record<string, FieldGroupMemberResponse[]> = {};
  try {
    const [defs, groups] = await Promise.all([
      getFieldDefinitions("INVOICE"),
      getFieldGroups("INVOICE"),
    ]);
    invoiceFieldDefs = defs;
    invoiceFieldGroups = groups;

    // Fetch members for each applied group
    const appliedGroups = invoice!.appliedFieldGroups ?? [];
    if (appliedGroups.length > 0) {
      const memberResults = await Promise.allSettled(
        appliedGroups.map((gId) => getGroupMembers(gId)),
      );
      memberResults.forEach((result, i) => {
        if (result.status === "fulfilled") {
          invoiceGroupMembers[appliedGroups[i]] = result.value;
        }
      });
    }
  } catch {
    // Non-fatal: custom fields section won't render
  }

  return (
    <div className="space-y-8">
      {/* Back link */}
      <div className="flex items-center justify-between">
        <Link
          href={`/org/${slug}/invoices`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Invoices
        </Link>
        {invoiceTemplates.length > 0 && (
          <GenerateDocumentDropdown
            templates={invoiceTemplates}
            entityId={id}
            entityType="INVOICE"
            slug={slug}
          />
        )}
      </div>

      <InvoiceDetailClient
        invoice={invoice!}
        slug={slug}
        isAdmin={isAdmin}
        paymentEvents={paymentEvents}
        taxRates={taxRates}
      />

      {/* Custom Fields */}
      <FieldGroupSelector
        entityType="INVOICE"
        entityId={id}
        appliedFieldGroups={invoice!.appliedFieldGroups ?? []}
        slug={slug}
        canManage={isAdmin}
        allGroups={invoiceFieldGroups}
      />
      <CustomFieldSection
        entityType="INVOICE"
        entityId={id}
        customFields={invoice!.customFields ?? {}}
        appliedFieldGroups={invoice!.appliedFieldGroups ?? []}
        editable={isAdmin}
        slug={slug}
        fieldDefinitions={invoiceFieldDefs}
        fieldGroups={invoiceFieldGroups}
        groupMembers={invoiceGroupMembers}
      />

      {/* Generated Documents section */}
      <div className="space-y-4">
        <h2 className="font-display text-lg text-slate-950 dark:text-slate-50">
          Generated Documents
        </h2>
        <GeneratedDocumentsList
          entityType="INVOICE"
          entityId={id}
          slug={slug}
          isAdmin={isAdmin}
        />
      </div>
    </div>
  );
}
