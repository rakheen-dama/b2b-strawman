import { auth } from "@clerk/nextjs/server";
import { api, handleApiError, getFieldDefinitions, getFieldGroups, getGroupMembers, getTags, getTemplates } from "@/lib/api";
import type {
  Customer,
  CustomerStatus,
  Document,
  Project,
  BillingRate,
  OrgMember,
  OrgSettings,
  CustomerProfitabilityResponse,
  OrgProfitabilityResponse,
  InvoiceResponse,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
  TagResponse,
  TemplateListResponse,
} from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EditCustomerDialog } from "@/components/customers/edit-customer-dialog";
import { ArchiveCustomerDialog } from "@/components/customers/archive-customer-dialog";
import { CustomerProjectsPanel } from "@/components/customers/customer-projects-panel";
import { CustomerDocumentsPanel } from "@/components/documents/customer-documents-panel";
import { CustomerTabs } from "@/components/customers/customer-tabs";
import { CustomerRatesTab } from "@/components/rates/customer-rates-tab";
import { CustomerFinancialsTab } from "@/components/profitability/customer-financials-tab";
import { CustomerInvoicesTab } from "@/components/customers/customer-invoices-tab";
import { CustomFieldSection } from "@/components/field-definitions/CustomFieldSection";
import { FieldGroupSelector } from "@/components/field-definitions/FieldGroupSelector";
import { TagInput } from "@/components/tags/TagInput";
import { GenerateDocumentDropdown } from "@/components/templates/GenerateDocumentDropdown";
import { GeneratedDocumentsList } from "@/components/templates/GeneratedDocumentsList";
import { formatDate } from "@/lib/format";
import { ArrowLeft, Pencil, Archive } from "lucide-react";
import Link from "next/link";

const STATUS_BADGE: Record<CustomerStatus, { label: string; variant: "success" | "neutral" }> = {
  ACTIVE: { label: "Active", variant: "success" },
  ARCHIVED: { label: "Archived", variant: "neutral" },
};

export default async function CustomerDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let customer: Customer;
  try {
    customer = await api.get<Customer>(`/api/customers/${id}`);
  } catch (error) {
    handleApiError(error);
  }

  let linkedProjects: Project[] = [];
  try {
    linkedProjects = await api.get<Project[]>(`/api/customers/${id}/projects`);
  } catch {
    // Non-fatal: show empty projects list if fetch fails
  }

  let customerDocuments: Document[] = [];
  try {
    customerDocuments = await api.get<Document[]>(
      `/api/documents?scope=CUSTOMER&customerId=${id}`
    );
  } catch {
    // Non-fatal: show empty documents list if fetch fails
  }

  // Billing rates + org members for the "Rates" tab (admin/owner only)
  let customerBillingRates: BillingRate[] = [];
  let orgMembers: OrgMember[] = [];
  let defaultCurrency = "USD";
  let customerProfitability: CustomerProfitabilityResponse | null = null;
  let projectBreakdown: OrgProfitabilityResponse | null = null;
  let customerInvoices: InvoiceResponse[] = [];
  if (isAdmin) {
    try {
      const [ratesRes, membersRes, settingsRes, profitabilityRes, breakdownRes, invoicesRes] =
        await Promise.all([
          api.get<{ content: BillingRate[] }>(`/api/billing-rates?customerId=${id}`),
          api.get<OrgMember[]>("/api/members"),
          api.get<OrgSettings>("/api/settings").catch(() => null),
          api
            .get<CustomerProfitabilityResponse>(
              `/api/customers/${id}/profitability`,
            )
            .catch(() => null),
          api
            .get<OrgProfitabilityResponse>(
              `/api/reports/profitability?customerId=${id}`,
            )
            .catch(() => null),
          api
            .get<InvoiceResponse[]>(
              `/api/invoices?customerId=${id}`,
            )
            .catch(() => [] as InvoiceResponse[]),
        ]);
      customerBillingRates = ratesRes?.content ?? [];
      orgMembers = membersRes;
      if (settingsRes?.defaultCurrency) {
        defaultCurrency = settingsRes.defaultCurrency;
      }
      customerProfitability = profitabilityRes;
      projectBreakdown = breakdownRes;
      customerInvoices = invoicesRes ?? [];
    } catch {
      // Non-fatal: show empty rates/financials/invoices tab if fetch fails
    }
  }

  // Custom field definitions and groups for the Custom Fields section
  let customerFieldDefs: FieldDefinitionResponse[] = [];
  let customerFieldGroups: FieldGroupResponse[] = [];
  const customerGroupMembers: Record<string, FieldGroupMemberResponse[]> = {};
  try {
    const [defs, groups] = await Promise.all([
      getFieldDefinitions("CUSTOMER"),
      getFieldGroups("CUSTOMER"),
    ]);
    customerFieldDefs = defs;
    customerFieldGroups = groups;

    // Fetch members for each applied group
    const appliedGroups = customer.appliedFieldGroups ?? [];
    if (appliedGroups.length > 0) {
      const memberResults = await Promise.allSettled(
        appliedGroups.map((gId) => getGroupMembers(gId)),
      );
      memberResults.forEach((result, i) => {
        if (result.status === "fulfilled") {
          customerGroupMembers[appliedGroups[i]] = result.value;
        }
      });
    }
  } catch {
    // Non-fatal: custom fields section won't render
  }

  // Tags for the Tags section
  let customerTags: TagResponse[] = [];
  let allTags: TagResponse[] = [];
  try {
    const [entityTags, orgTags] = await Promise.all([
      api.get<TagResponse[]>(`/api/customers/${id}/tags`),
      getTags(),
    ]);
    customerTags = entityTags;
    allTags = orgTags;
  } catch {
    // Non-fatal: tags section will show empty state
  }

  // Document templates for the "Generate Document" dropdown (admin only)
  let customerTemplates: TemplateListResponse[] = [];
  if (isAdmin) {
    try {
      customerTemplates = await getTemplates(undefined, "CUSTOMER");
    } catch {
      // Non-fatal: hide generate button if template fetch fails
    }
  }

  const statusBadge = STATUS_BADGE[customer.status];

  return (
    <div className="space-y-8">
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/customers`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Customers
        </Link>
      </div>

      {/* Customer Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
              {customer.name}
            </h1>
            <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
          </div>
          <p className="mt-1 text-slate-600 dark:text-slate-400">{customer.email}</p>
          <p className="mt-3 text-sm text-slate-400 dark:text-slate-600">
            {customer.phone && (
              <>
                {customer.phone} &middot;{" "}
              </>
            )}
            {customer.idNumber && (
              <>
                {customer.idNumber} &middot;{" "}
              </>
            )}
            Created {formatDate(customer.createdAt)} &middot; {linkedProjects.length}{" "}
            {linkedProjects.length === 1 ? "project" : "projects"}
          </p>
          {customer.notes && (
            <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">{customer.notes}</p>
          )}
        </div>

        {isAdmin && (
          <div className="flex shrink-0 gap-2">
            {customerTemplates.length > 0 && (
              <GenerateDocumentDropdown
                templates={customerTemplates}
                entityId={id}
                entityType="CUSTOMER"
              />
            )}
            {customer.status === "ACTIVE" && (
              <>
                <EditCustomerDialog customer={customer} slug={slug}>
                  <Button variant="outline" size="sm">
                    <Pencil className="mr-1.5 size-4" />
                    Edit
                  </Button>
                </EditCustomerDialog>
                <ArchiveCustomerDialog slug={slug} customerId={customer.id} customerName={customer.name}>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
                  >
                    <Archive className="mr-1.5 size-4" />
                    Archive
                  </Button>
                </ArchiveCustomerDialog>
              </>
            )}
          </div>
        )}
      </div>

      {/* Custom Fields */}
      <FieldGroupSelector
        entityType="CUSTOMER"
        entityId={id}
        appliedFieldGroups={customer.appliedFieldGroups ?? []}
        slug={slug}
        canManage={isAdmin}
        allGroups={customerFieldGroups}
      />
      <CustomFieldSection
        entityType="CUSTOMER"
        entityId={id}
        customFields={customer.customFields ?? {}}
        appliedFieldGroups={customer.appliedFieldGroups ?? []}
        editable={isAdmin}
        slug={slug}
        fieldDefinitions={customerFieldDefs}
        fieldGroups={customerFieldGroups}
        groupMembers={customerGroupMembers}
      />

      {/* Tags */}
      <div className="space-y-2">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
          Tags
        </p>
        <TagInput
          entityType="CUSTOMER"
          entityId={id}
          tags={customerTags}
          allTags={allTags}
          editable={isAdmin}
          canInlineCreate={isAdmin}
          slug={slug}
        />
      </div>

      {/* Tabbed Content */}
      <CustomerTabs
        projectsPanel={
          <CustomerProjectsPanel
            projects={linkedProjects}
            slug={slug}
            customerId={id}
            canManage={isAdmin && customer.status === "ACTIVE"}
          />
        }
        documentsPanel={
          <CustomerDocumentsPanel
            documents={customerDocuments}
            slug={slug}
            customerId={id}
            canManage={isAdmin && customer.status === "ACTIVE"}
          />
        }
        invoicesPanel={
          isAdmin ? (
            <CustomerInvoicesTab
              invoices={customerInvoices}
              customerId={id}
              customerName={customer.name}
              slug={slug}
              canManage={isAdmin && customer.status === "ACTIVE"}
              defaultCurrency={defaultCurrency}
            />
          ) : undefined
        }
        ratesPanel={
          isAdmin ? (
            <CustomerRatesTab
              billingRates={customerBillingRates}
              members={orgMembers}
              customerId={id}
              slug={slug}
              defaultCurrency={defaultCurrency}
            />
          ) : undefined
        }
        generatedPanel={
          <GeneratedDocumentsList
            entityType="CUSTOMER"
            entityId={id}
            slug={slug}
            isAdmin={isAdmin}
          />
        }
        financialsPanel={
          isAdmin ? (
            <CustomerFinancialsTab
              profitability={customerProfitability}
              projectBreakdown={projectBreakdown}
            />
          ) : undefined
        }
      />
    </div>
  );
}
