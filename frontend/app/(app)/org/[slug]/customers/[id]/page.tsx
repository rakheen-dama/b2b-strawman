import { auth } from "@clerk/nextjs/server";
import { api, handleApiError, getFieldDefinitions, getFieldGroups, getGroupMembers, getTags, getTemplates } from "@/lib/api";
import type {
  Customer,
  CustomerStatus,
  CustomerReadiness,
  UnbilledTimeSummary,
  TemplateReadiness,
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
  ChecklistInstanceResponse,
  ChecklistTemplateResponse,
} from "@/lib/types";
import type { SetupStep } from "@/components/setup/types";
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
import { LifecycleStatusBadge } from "@/components/compliance/LifecycleStatusBadge";
import { LifecycleTransitionDropdown } from "@/components/compliance/LifecycleTransitionDropdown";
import { ChecklistInstancePanel } from "@/components/compliance/ChecklistInstancePanel";
import {
  SetupProgressCard,
  ActionCard,
  TemplateReadinessCard,
} from "@/components/setup";
import {
  fetchCustomerReadiness,
  fetchCustomerUnbilledSummary,
  fetchTemplateReadiness,
} from "@/lib/api/setup-status";
import { getCustomerChecklists, getChecklistTemplates } from "@/lib/checklist-api";
import { formatDate, formatCurrency } from "@/lib/format";
import { ArrowLeft, Pencil, Archive, Clock, UserCheck, ArrowRight } from "lucide-react";
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

  // Checklist instances for the Onboarding tab
  let checklistInstances: ChecklistInstanceResponse[] = [];
  try {
    checklistInstances = await getCustomerChecklists(id);
  } catch {
    // Non-fatal: onboarding tab won't show if fetch fails
  }

  // Checklist templates for the "Manually Add" selector (admin only)
  let checklistTemplates: ChecklistTemplateResponse[] = [];
  if (isAdmin) {
    try {
      checklistTemplates = await getChecklistTemplates();
    } catch {
      // Non-fatal: hide add button if template fetch fails
    }
  }

  // Build template name lookup for the onboarding panel
  const checklistTemplateNames: Record<string, string> = {};
  for (const t of checklistTemplates) {
    checklistTemplateNames[t.id] = t.name;
  }

  const showOnboardingTab =
    customer.lifecycleStatus === "ONBOARDING" || checklistInstances.length > 0;

  // Setup guidance data (Epic 113A)
  let customerReadiness: CustomerReadiness | null = null;
  let customerUnbilledSummary: UnbilledTimeSummary | null = null;
  let customerTemplateReadiness: TemplateReadiness[] = [];
  try {
    const [readinessRes, unbilledRes, templateReadinessRes] = await Promise.all([
      fetchCustomerReadiness(id),
      fetchCustomerUnbilledSummary(id),
      fetchTemplateReadiness("CUSTOMER", id),
    ]);
    customerReadiness = readinessRes;
    customerUnbilledSummary = unbilledRes;
    customerTemplateReadiness = templateReadinessRes;
  } catch {
    // Non-fatal: setup guidance cards will not render if fetch fails
  }

  // Map customer readiness to setup steps
  const customerSetupSteps: SetupStep[] = customerReadiness
    ? [
        {
          label: "Projects linked",
          complete: customerReadiness.hasLinkedProjects,
          actionHref: `?tab=projects`,
        },
        {
          label:
            customerReadiness.checklistProgress === null
              ? "No onboarding checklist"
              : `Onboarding checklist (${customerReadiness.checklistProgress.completed}/${customerReadiness.checklistProgress.total})`,
          complete:
            customerReadiness.checklistProgress === null ||
            customerReadiness.checklistProgress.completed ===
              customerReadiness.checklistProgress.total,
          actionHref: showOnboardingTab ? `?tab=onboarding` : undefined,
        },
        {
          label:
            customerReadiness.requiredFields.total === 0
              ? "No required fields defined"
              : `Required fields filled (${customerReadiness.requiredFields.filled}/${customerReadiness.requiredFields.total})`,
          complete:
            customerReadiness.requiredFields.total === 0 ||
            customerReadiness.requiredFields.filled ===
              customerReadiness.requiredFields.total,
          actionHref: "#custom-fields",
        },
      ]
    : [];

  // Compute readiness percentage from steps
  const completedStepCount = customerSetupSteps.filter((s) => s.complete).length;
  const customerReadinessPercentage =
    customerSetupSteps.length === 0
      ? 100
      : Math.round((completedStepCount / customerSetupSteps.length) * 100);
  const customerReadinessComplete =
    customerSetupSteps.length === 0 || customerSetupSteps.every((s) => s.complete);

  // Lifecycle action prompt (Epic 113A)
  const lifecycleActionPrompt: {
    icon: typeof ArrowRight;
    title: string;
    description: string;
    actionLabel: string;
    targetStatus: "ONBOARDING" | "ACTIVE";
  } | null =
    isAdmin && customer.lifecycleStatus && customer.status === "ACTIVE"
      ? customer.lifecycleStatus === "PROSPECT"
        ? {
            icon: ArrowRight,
            title: "Ready to start onboarding?",
            description:
              "Move this customer to Onboarding to begin compliance checklists.",
            actionLabel: "Start Onboarding",
            targetStatus: "ONBOARDING",
          }
        : customer.lifecycleStatus === "ONBOARDING" &&
            customerReadiness?.checklistProgress !== null &&
            customerReadiness?.checklistProgress?.completed ===
              customerReadiness?.checklistProgress?.total &&
            (customerReadiness?.checklistProgress?.total ?? 0) > 0
          ? {
              icon: UserCheck,
              title: "All items verified — Activate Customer",
              description:
                "Onboarding checklist is complete. This customer is ready to be activated.",
              actionLabel: "Activate Customer",
              targetStatus: "ACTIVE",
            }
          : null
      : null;

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
            {customer.lifecycleStatus && (
              <LifecycleStatusBadge status={customer.lifecycleStatus} />
            )}
          </div>
          <p className="mt-1 text-slate-600 dark:text-slate-400">{customer.email}</p>
          {customer.lifecycleStatusChangedAt && (
            <p className="mt-1 text-sm text-slate-500">
              Since {formatDate(customer.lifecycleStatusChangedAt)}
            </p>
          )}
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
          <div id="lifecycle-transition" className="flex shrink-0 gap-2">
            {customer.status === "ACTIVE" && customer.lifecycleStatus && (
              <LifecycleTransitionDropdown
                currentStatus={customer.lifecycleStatus}
                customerId={id}
                slug={slug}
              />
            )}
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

      {/* Setup Guidance Cards — Epic 113A */}
      {customerReadiness && (
        <SetupProgressCard
          title="Customer Readiness"
          completionPercentage={customerReadinessPercentage}
          overallComplete={customerReadinessComplete}
          steps={customerSetupSteps}
          canManage={isAdmin}
        />
      )}

      {customerUnbilledSummary && customerUnbilledSummary.entryCount > 0 && (
        <ActionCard
          icon={Clock}
          title="Unbilled Time"
          description={`${formatCurrency(customerUnbilledSummary.totalAmount, customerUnbilledSummary.currency)} across ${customerUnbilledSummary.totalHours.toFixed(1)} hours`}
          primaryAction={
            isAdmin
              ? {
                  label: "Create Invoice",
                  href: `/org/${slug}/invoices/new?customerId=${id}`,
                }
              : undefined
          }
          secondaryAction={{
            label: "View Time",
            href: `?tab=invoices`,
          }}
          variant="accent"
        />
      )}

      {customerTemplateReadiness.length > 0 && (
        <TemplateReadinessCard
          templates={customerTemplateReadiness}
          generateHref={(templateId) =>
            `/org/${slug}/customers/${id}?generateTemplate=${templateId}`
          }
        />
      )}

      {lifecycleActionPrompt && (
        <ActionCard
          icon={lifecycleActionPrompt.icon}
          title={lifecycleActionPrompt.title}
          description={lifecycleActionPrompt.description}
          primaryAction={{
            label: lifecycleActionPrompt.actionLabel,
            href: `#lifecycle-transition`,
          }}
          variant="default"
        />
      )}

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
        onboardingPanel={
          showOnboardingTab ? (
            <ChecklistInstancePanel
              customerId={id}
              instances={checklistInstances}
              isAdmin={isAdmin}
              slug={slug}
              templateNames={checklistTemplateNames}
              templates={isAdmin ? checklistTemplates : undefined}
            />
          ) : undefined
        }
      />
    </div>
  );
}
