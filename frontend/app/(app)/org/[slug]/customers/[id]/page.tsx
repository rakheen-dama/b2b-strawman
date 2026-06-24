import { fetchMyCapabilities } from "@/lib/api/capabilities";
import {
  api,
  handleApiError,
  getFieldDefinitions,
  getFieldGroups,
  getGroupMembers,
  getTags,
  getTemplates,
} from "@/lib/api";
import type {
  Customer,
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
import type { SetupStep, ContextGroup } from "@/components/setup/types";
import { listDeals, getStages } from "@/lib/api/crm";
import type { DealResponse, StageDto } from "@/lib/api/crm";
import { TerminologyText } from "@/components/terminology-text";
import { PROMOTED_CUSTOMER_SLUGS } from "@/lib/constants/promoted-field-slugs";
import { CustomerProjectsPanel } from "@/components/customers/customer-projects-panel";
import { CustomerDealsTab } from "@/components/pipeline/CustomerDealsTab";
import { CustomerDocumentsPanel } from "@/components/documents/customer-documents-panel";
import { CustomerGroupedTabs } from "@/components/customers/customer-grouped-tabs";
import { ClientHeaderCardWithLifecycle } from "@/components/customers/client-header-card-with-lifecycle";
import { ClientDetailsTab } from "@/components/customers/client-details-tab";
import { ClientFieldsTab } from "@/components/customers/client-fields-tab";
import { ClientTagsTab } from "@/components/customers/client-tags-tab";
import { ClientOverviewTab } from "@/components/customers/client-overview-tab";
import { CustomerAuditTab } from "./audit-tab";
import { CustomerRatesTab } from "@/components/rates/customer-rates-tab";
import { CustomerFinancialsTab } from "@/components/profitability/customer-financials-tab";
import { CustomerInvoicesTab } from "@/components/customers/customer-invoices-tab";
import { GeneratedDocumentsList } from "@/components/templates/GeneratedDocumentsList";
import { ChecklistInstancePanel } from "@/components/compliance/ChecklistInstancePanel";
import type { KycSummary } from "@/components/customers/kyc-status-badge";
import { ActionCard } from "@/components/setup";
import {
  fetchCustomerReadiness,
  fetchCustomerUnbilledSummary,
  fetchTemplateReadiness,
} from "@/lib/api/setup-status";
import { getCustomerChecklists, getChecklistTemplates } from "@/lib/checklist-api";
import type { KycIntegrationStatus } from "@/lib/types";
import {
  getCustomerRequests,
  type InformationRequestResponse,
} from "@/lib/api/information-requests";
import { RequestList } from "@/components/information-requests/request-list";
import { CreateRequestDialog } from "@/components/information-requests/create-request-dialog";
import { isXeroConnected } from "@/lib/api/integrations";
import { fetchRetainers, fetchPeriods } from "@/lib/api/retainers";
import type { RetainerResponse, PeriodSummary } from "@/lib/api/retainers";
import { CustomerRetainerTab } from "@/components/customers/customer-retainer-tab";
import { PendingSuggestionsWidget } from "@/components/assistant/queue/pending-suggestions-widget";
import { TrustBalanceCard } from "@/components/trust/TrustBalanceCard";
import { checkPrerequisites } from "@/lib/prerequisites";
import type { PrerequisiteCheck } from "@/components/prerequisite/types";
import { formatCurrencySafe } from "@/lib/format";
import { FicaVerificationPanel } from "@/components/ai/fica-verification-panel";
import { getAiProfile } from "@/lib/api/ai";
import { ArrowLeft, ArrowRight, UserCheck, ShieldCheck } from "lucide-react";
import Link from "next/link";

export default async function CustomerDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;
  const isOwner = caps.isOwner;

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
    customerDocuments = await api.get<Document[]>(`/api/documents?scope=CUSTOMER&customerId=${id}`);
  } catch {
    // Non-fatal: show empty documents list if fetch fails
  }

  // Pipeline deals for the customer "Deals" tab (Epic 580A.3). Both fetches are
  // non-fatal — the tab degrades to an empty state / disabled intake on failure.
  let customerDeals: DealResponse[] = [];
  try {
    const dealsPage = await listDeals({ customerId: id });
    customerDeals = dealsPage.content;
  } catch {
    // Non-fatal: show empty deals tab if fetch fails
  }
  let pipelineStages: StageDto[] = [];
  try {
    pipelineStages = await getStages();
  } catch {
    // Non-fatal: intake stage selector falls back to first open stage
  }

  // Org settings drive currency + module gates for both admin and non-admin
  // surfaces (e.g. the conflict-check button gate). Fetched independently so a
  // failing admin-only fetch can't suppress module-gated UI.
  const orgSettings = await api.get<OrgSettings>("/api/settings").catch(() => null);
  const defaultCurrency = orgSettings?.defaultCurrency ?? "USD";
  const enabledModules: string[] = orgSettings?.enabledModules ?? [];

  // Billing rates + org members for the "Rates" tab (admin/owner only)
  let customerBillingRates: BillingRate[] = [];
  let orgMembers: OrgMember[] = [];
  let customerProfitability: CustomerProfitabilityResponse | null = null;
  let projectBreakdown: OrgProfitabilityResponse | null = null;
  let customerInvoices: InvoiceResponse[] = [];
  if (isAdmin) {
    try {
      const [ratesRes, membersRes, profitabilityRes, breakdownRes, invoicesRes] = await Promise.all(
        [
          api.get<{ content: BillingRate[] }>(`/api/billing-rates?customerId=${id}`),
          api.get<OrgMember[]>("/api/members"),
          api
            .get<CustomerProfitabilityResponse>(`/api/customers/${id}/profitability`)
            .catch(() => null),
          api
            .get<OrgProfitabilityResponse>(`/api/reports/profitability?customerId=${id}`)
            .catch(() => null),
          api
            .get<InvoiceResponse[]>(`/api/invoices?customerId=${id}`)
            .catch(() => [] as InvoiceResponse[]),
        ]
      );
      customerBillingRates = ratesRes?.content ?? [];
      orgMembers = membersRes;
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
    // Filter out promoted field slugs — they are rendered as first-class
    // form fields (Epic 463) and should not appear in the custom field section.
    customerFieldDefs = defs.filter((d) => !PROMOTED_CUSTOMER_SLUGS.has(d.slug));
    customerFieldGroups = groups;

    // Fetch members for each applied group
    const appliedGroups = customer.appliedFieldGroups ?? [];
    if (appliedGroups.length > 0) {
      const memberResults = await Promise.allSettled(
        appliedGroups.map((gId) => getGroupMembers(gId))
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

  // Fetch KYC integration status
  let kycStatus: KycIntegrationStatus = { configured: false, provider: null };
  try {
    kycStatus = await api.get<KycIntegrationStatus>("/api/integrations/kyc/status");
  } catch {
    // Non-fatal: KYC verification buttons won't show
  }

  const xeroConnected = await isXeroConnected();

  // Derive a customer-level KYC summary from existing checklist data so the
  // header can surface a status badge without an extra round-trip. Walks
  // checklist items looking for a verification provider; collapses to one of
  // verified / pending / unverified.
  const kycSummary: KycSummary | null = (() => {
    if (!kycStatus.configured) return null;
    let latestVerified: { verifiedAt: string | null; provider: string } | null = null;
    let hasPending = false;
    for (const inst of checklistInstances) {
      for (const item of inst.items ?? []) {
        // A VERIFIED item without a verifiedAt timestamp is still verified —
        // don't demote it to "pending". The badge tolerates a null timestamp.
        if (item.verificationStatus === "VERIFIED" && item.verificationProvider) {
          const itemVerifiedAt = item.verifiedAt ?? null;
          const isNewer =
            !latestVerified ||
            (itemVerifiedAt &&
              (latestVerified.verifiedAt === null || itemVerifiedAt > latestVerified.verifiedAt));
          if (isNewer) {
            latestVerified = {
              verifiedAt: itemVerifiedAt,
              provider: item.verificationProvider,
            };
          }
        } else if (item.verificationProvider) {
          hasPending = true;
        }
      }
    }
    if (latestVerified) {
      return {
        state: "verified",
        provider: latestVerified.provider,
        verifiedAt: latestVerified.verifiedAt,
      };
    }
    if (hasPending) {
      return { state: "pending" };
    }
    return { state: "unverified" };
  })();

  // Fetch retainer data for the Retainer tab
  let customerRetainers: RetainerResponse[] = [];
  try {
    customerRetainers = await fetchRetainers({ customerId: id });
  } catch {
    // Non-fatal: retainer tab will show empty state
  }

  // Find active/most-recent retainer
  const activeRetainer =
    customerRetainers.find((r) => r.status === "ACTIVE") ??
    customerRetainers.find((r) => r.status === "PAUSED") ??
    customerRetainers[0] ??
    null;

  // Fetch periods for the active/latest retainer
  let retainerPeriods: PeriodSummary[] = [];
  if (activeRetainer) {
    try {
      const periodsRes = await fetchPeriods(activeRetainer.id, 0);
      retainerPeriods = periodsRes.content;
    } catch {
      // Non-fatal: period history table will show empty
    }
  }

  // Fetch information requests for the Requests tab
  let customerRequests: InformationRequestResponse[] = [];
  try {
    customerRequests = await getCustomerRequests(id);
  } catch {
    // Non-fatal: requests tab will show empty state
  }

  // Retainers can be established at any non-terminal lifecycle stage (including
  // PROSPECT / ONBOARDING), so the tab is visible unless the customer is in a
  // terminal state (OFFBOARDING / OFFBOARDED / ANONYMIZED).
  const showRetainerTab =
    (customer.lifecycleStatus !== "OFFBOARDED" &&
      customer.lifecycleStatus !== "OFFBOARDING" &&
      customer.lifecycleStatus !== "ANONYMIZED") ||
    customerRetainers.length > 0;

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

  // Activation prerequisite check for ONBOARDING customers (Task 248.4)
  let activationPrerequisites: PrerequisiteCheck | null = null;
  if (customer.lifecycleStatus === "ONBOARDING") {
    try {
      activationPrerequisites = await checkPrerequisites("LIFECYCLE_ACTIVATION", "CUSTOMER", id);
    } catch {
      // Non-fatal: activation blockers won't show if check fails
    }
  }

  const activationBlockers: string[] =
    activationPrerequisites && !activationPrerequisites.passed
      ? activationPrerequisites.violations.map((v) => v.message)
      : [];

  // AI FICA panel prerequisites
  const hasDocuments = customerDocuments.length > 0;
  const hasPendingChecklistItems = checklistInstances.some((inst) =>
    (inst.items ?? []).some((item) => item.status === "PENDING")
  );
  let isAiConfigured = false;
  if (caps.capabilities.includes("AI_MANAGE")) {
    // OWNER/ADMIN can check profile directly
    try {
      const aiProfile = await getAiProfile();
      isAiConfigured = aiProfile.coldStartCompleted;
    } catch {
      // Non-fatal: panel will show disabled state
    }
  } else if (caps.capabilities.includes("AI_EXECUTE")) {
    // MEMBER with AI_EXECUTE: they wouldn't have this capability without setup being done
    isAiConfigured = true;
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
            (customerReadiness.requiredFields?.total ?? 0) === 0
              ? "No required fields defined"
              : `Required fields filled (${customerReadiness.requiredFields?.filled ?? 0}/${customerReadiness.requiredFields?.total ?? 0})`,
          complete:
            (customerReadiness.requiredFields?.total ?? 0) === 0 ||
            (customerReadiness.requiredFields?.filled ?? 0) ===
              (customerReadiness.requiredFields?.total ?? 0),
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

  // Build context groups from required fields for expandable display (Epic 251B)
  const contextGroups: ContextGroup[] =
    customerReadiness && (customerReadiness.requiredFields?.fields?.length ?? 0) > 0
      ? [
          {
            contextLabel: "Required Fields",
            filled: customerReadiness.requiredFields?.filled ?? 0,
            total: customerReadiness.requiredFields?.total ?? 0,
            fields: (customerReadiness.requiredFields?.fields ?? []).map((f) => ({
              name: f.name,
              slug: f.slug,
              filled: f.filled,
            })),
          },
        ]
      : [];

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
            description: "Move this customer to Onboarding to begin compliance checklists.",
            actionLabel: "Start Onboarding",
            targetStatus: "ONBOARDING",
          }
        : customer.lifecycleStatus === "ONBOARDING" &&
            (customerReadiness?.checklistProgress == null ||
              customerReadiness.checklistProgress.total === 0 ||
              (customerReadiness.checklistProgress.completed ===
                customerReadiness.checklistProgress.total &&
                customerReadiness.checklistProgress.total > 0))
          ? {
              icon: UserCheck,
              title:
                customerReadiness?.checklistProgress != null &&
                customerReadiness.checklistProgress.total > 0
                  ? "All items verified — Activate Customer"
                  : "Ready to activate",
              description:
                customerReadiness?.checklistProgress != null &&
                customerReadiness.checklistProgress.total > 0
                  ? "Onboarding checklist is complete. This customer is ready to be activated."
                  : "No onboarding checklist assigned. This customer is ready to be activated.",
              actionLabel: "Activate Customer",
              targetStatus: "ACTIVE",
            }
          : null
      : null;

  // Pre-compute promoted field values for ClientFieldsTab
  const promotedFieldValues: Record<string, unknown> = {};
  if (customer.entityType) {
    promotedFieldValues["acct_entity_type"] = customer.entityType;
    promotedFieldValues["client_type"] = customer.entityType;
  }
  if (customer.taxNumber) {
    promotedFieldValues["tax_number"] = customer.taxNumber;
    promotedFieldValues["vat_number"] = customer.taxNumber;
  }
  if (customer.registrationNumber) {
    promotedFieldValues["acct_company_registration_number"] = customer.registrationNumber;
    promotedFieldValues["registration_number"] = customer.registrationNumber;
  }

  return (
    <div className="space-y-6">
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/customers`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          <TerminologyText template="Back to {Customers}" />
        </Link>
      </div>

      {/* Client header card */}
      <div id="lifecycle-transition">
        <ClientHeaderCardWithLifecycle
          customerId={id}
          customerName={customer.name}
          customerStatus={customer.status}
          lifecycleStatus={customer.lifecycleStatus ?? null}
          email={customer.email}
          phone={customer.phone}
          lifecycleStatusChangedAt={customer.lifecycleStatusChangedAt ?? null}
          linkedProjectCount={linkedProjects.length}
          kycSummary={kycSummary}
          xeroConnected={xeroConnected}
          slug={slug}
          isAdmin={isAdmin}
          isOwner={isOwner}
          templates={customerTemplates}
          aiProviderConfigured={isAiConfigured}
          conflictCheckEnabled={enabledModules.includes("conflict_check")}
          kycConfigured={kycStatus.configured}
          kycVerified={kycSummary?.state === "verified"}
          customer={customer}
          targetLifecycleStatus={lifecycleActionPrompt?.targetStatus ?? null}
        />
      </div>

      {/* Anonymized Info Banner */}
      {customer.lifecycleStatus === "ANONYMIZED" && (
        <div className="flex items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900">
          <ShieldCheck className="mt-0.5 size-5 shrink-0 text-slate-500 dark:text-slate-400" />
          <div className="text-sm text-slate-700 dark:text-slate-300">
            <p className="font-medium">Customer data anonymized</p>
            <p className="mt-0.5 text-slate-500 dark:text-slate-400">
              This customer&apos;s personal data has been anonymized. All identifying information
              has been removed.
            </p>
          </div>
        </div>
      )}

      {/* Grouped Tabs — all panels as ReactNode props */}
      <CustomerGroupedTabs
        detailsPanel={<ClientDetailsTab customer={customer} />}
        fieldsPanel={
          <ClientFieldsTab
            entityId={id}
            appliedFieldGroups={customer.appliedFieldGroups ?? []}
            slug={slug}
            canManage={isAdmin}
            allGroups={customerFieldGroups}
            customFields={customer.customFields ?? {}}
            editable={isAdmin}
            fieldDefinitions={customerFieldDefs}
            fieldGroups={customerFieldGroups}
            groupMembers={customerGroupMembers}
            promotedFieldValues={promotedFieldValues}
          />
        }
        tagsPanel={
          <ClientTagsTab
            entityId={id}
            tags={customerTags}
            allTags={allTags}
            editable={isAdmin}
            canInlineCreate={isAdmin}
            slug={slug}
          />
        }
        overviewPanel={
          <ClientOverviewTab
            setupProgressData={
              customerReadiness
                ? {
                    title: <TerminologyText template="{Client} Readiness" />,
                    completionPercentage: customerReadinessPercentage,
                    overallComplete: customerReadinessComplete,
                    steps: customerSetupSteps,
                    canManage: isAdmin,
                    activationBlockers:
                      activationBlockers.length > 0 ? activationBlockers : undefined,
                    contextGroups: contextGroups.length > 0 ? contextGroups : undefined,
                  }
                : null
            }
            lifecyclePrompt={
              lifecycleActionPrompt ? (
                <ActionCard
                  icon={lifecycleActionPrompt.icon}
                  title={lifecycleActionPrompt.title}
                  description={lifecycleActionPrompt.description}
                  primaryAction={{
                    label: lifecycleActionPrompt.actionLabel,
                    href: "#lifecycle-transition",
                  }}
                  variant="default"
                />
              ) : null
            }
            unbilledTimeData={
              isAdmin && customerUnbilledSummary && customerUnbilledSummary.entryCount > 0
                ? {
                    amount: formatCurrencySafe(
                      customerUnbilledSummary.totalAmount,
                      customerUnbilledSummary.currency
                    ),
                    hours: customerUnbilledSummary.totalHours.toFixed(1),
                    createInvoiceHref: "?tab=invoices",
                    viewTimeHref: "?tab=time",
                  }
                : null
            }
            activeRetainer={
              activeRetainer
                ? {
                    name: activeRetainer.name,
                    status: activeRetainer.status,
                    allocatedHours:
                      activeRetainer.currentPeriod?.allocatedHours ??
                      activeRetainer.allocatedHours ??
                      null,
                    consumedHours: activeRetainer.currentPeriod?.consumedHours ?? null,
                    remainingHours: activeRetainer.currentPeriod?.remainingHours ?? null,
                  }
                : null
            }
            templateReadiness={
              customerTemplateReadiness.length > 0
                ? {
                    templates: customerTemplateReadiness,
                    baseHref: `/org/${slug}/customers/${id}`,
                  }
                : null
            }
            pendingSuggestions={
              <PendingSuggestionsWidget contextEntityType="customer" contextEntityId={id} />
            }
            ficaPanel={
              caps.capabilities.includes("AI_EXECUTE") &&
              customer.lifecycleStatus !== "ANONYMIZED" ? (
                <FicaVerificationPanel
                  customerId={id}
                  slug={slug}
                  hasDocuments={hasDocuments}
                  hasPendingChecklistItems={hasPendingChecklistItems}
                  isAiConfigured={isAiConfigured}
                  canReviewGates={caps.capabilities.includes("AI_REVIEW")}
                />
              ) : null
            }
            customerName={customer.name}
            lifecycleStatus={customer.lifecycleStatus ?? null}
            linkedProjectCount={linkedProjects.length}
          />
        }
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
        retainerPanel={
          showRetainerTab ? (
            <CustomerRetainerTab
              retainer={activeRetainer}
              allRetainers={customerRetainers}
              periods={retainerPeriods}
              slug={slug}
              customerId={id}
              canManage={isAdmin && customer.status === "ACTIVE"}
            />
          ) : undefined
        }
        requestsPanel={
          <div className="space-y-6">
            <div className="flex items-center justify-end">
              <CreateRequestDialog slug={slug} customerId={id} customerName={customer.name} />
            </div>
            <RequestList requests={customerRequests} slug={slug} showCustomer={false} />
          </div>
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
            customerId={id}
          />
        }
        dealsPanel={
          <CustomerDealsTab
            slug={slug}
            customerId={id}
            customerName={customer.name}
            deals={customerDeals}
            stages={pipelineStages}
            canManage={isAdmin && customer.status === "ACTIVE"}
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
        trustPanel={<TrustBalanceCard customerId={id} slug={slug} showQuickActions={isAdmin} />}
        auditPanel={<CustomerAuditTab customerId={id} />}
        onboardingPanel={
          showOnboardingTab ? (
            <ChecklistInstancePanel
              customerId={id}
              instances={checklistInstances}
              isAdmin={isAdmin}
              slug={slug}
              templateNames={checklistTemplateNames}
              templates={isAdmin ? checklistTemplates : undefined}
              customerDocuments={customerDocuments}
              kycConfigured={kycStatus.configured}
              customerName={customer.name}
            />
          ) : undefined
        }
      />
    </div>
  );
}
