import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, handleApiError } from "@/lib/api";
import { getClient } from "@/lib/api/clients";
import type { Client, OrgSettings } from "@/lib/types";
import { CustomerProjectsPanel } from "@/components/customers/customer-projects-panel";
import { CustomerDocumentsPanel } from "@/components/documents/customer-documents-panel";
import { CustomerGroupedTabs } from "@/components/customers/customer-grouped-tabs";
import { ClientHeaderCardWithLifecycle } from "@/components/customers/client-header-card-with-lifecycle";
import { ClientDetailsTab } from "@/components/customers/client-details-tab";
import { CollectionsExemptionToggle } from "@/components/customers/collections-exemption-toggle";
import { CollectionsHistoryTable } from "@/components/collections/collections-history-table";
import { CollectionsHistoryPagination } from "@/components/collections/collections-history-pagination";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { ClientFieldsTab } from "@/components/customers/client-fields-tab";
import { ClientTagsTab } from "@/components/customers/client-tags-tab";
import { ClientOverviewTab } from "@/components/customers/client-overview-tab";
import { CustomerAuditTab } from "./audit-tab";
import { CustomerRatesTab } from "@/components/rates/customer-rates-tab";
import { CustomerFinancialsTab } from "@/components/profitability/customer-financials-tab";
import { CustomerInvoicesTab } from "@/components/customers/customer-invoices-tab";
import { GeneratedDocumentsList } from "@/components/templates/GeneratedDocumentsList";
import { ChecklistInstancePanel } from "@/components/compliance/ChecklistInstancePanel";
import { ActionCard } from "@/components/setup";
import { RequestList } from "@/components/information-requests/request-list";
import { CreateRequestDialog } from "@/components/information-requests/create-request-dialog";
import { TrustBalanceCard } from "@/components/trust/TrustBalanceCard";
import { formatCurrencySafe } from "@/lib/format";
import { FicaVerificationPanel } from "@/components/ai/fica-verification-panel";
import { ArrowLeft, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { RecordRecentItem } from "@/components/record-recent-item";
import { buildClientHeroTiles, chaseHistory, loadClientHeroData } from "./hero-tiles";
import { loadClientPageData } from "./client-page-data";

export default async function CustomerDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string; id: string }>;
  searchParams: Promise<{ chasePage?: string }>;
}) {
  const { slug, id } = await params;
  const { chasePage } = await searchParams;
  const chasePageNum = Math.max(0, Number.parseInt(chasePage ?? "0", 10) || 0);
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;
  const isOwner = caps.isOwner;

  // Org settings drive currency + module gates for the tabs and the hero. One read, started beside
  // getClient and never rejecting, so a failing admin-only fetch can't suppress module-gated UI.
  const settingsPromise = api.get<OrgSettings>("/api/settings").catch(() => null);
  let customer: Client;
  try {
    customer = await getClient(id);
  } catch (error) {
    handleApiError(error);
  }
  const orgSettings = await settingsPromise;

  // Loaders (642B, §15.3.5): the tab-panel reads (./client-page-data) and the hero reads
  // (./hero-tiles — open matters, debtor detail, trust pair, FICA; the debtor detail is shared
  // with the Chase-history card). Each read once; the two loaders run in parallel.
  const [data, hero] = await Promise.all([
    loadClientPageData({ id, customer, caps, orgSettings }),
    loadClientHeroData(id, caps, {
      trustEnabled: (orgSettings?.enabledModules ?? []).includes("trust_accounting"),
      chasePage: chasePageNum,
    }),
  ]);
  const debtorDetail = chaseHistory(hero);
  const {
    linkedProjects,
    customerDocuments,
    defaultCurrency,
    enabledModules,
    customerBillingRates,
    orgMembers,
    customerProfitability,
    projectBreakdown,
    customerInvoices,
    customerFieldDefs,
    customerFieldGroups,
    customerGroupMembers,
    headerFields,
    customerTags,
    allTags,
    customerTemplates,
    checklistInstances,
    checklistTemplates,
    kycStatus,
    xeroConnected,
    kycSummary,
    customerRequests,
    checklistTemplateNames,
    showOnboardingTab,
    customerReadiness,
    customerUnbilledSummary,
    customerTemplateReadiness,
    activationBlockers,
    hasDocuments,
    hasPendingChecklistItems,
    isAiConfigured,
    customerSetupSteps,
    customerReadinessPercentage,
    customerReadinessComplete,
    contextGroups,
    lifecycleActionPrompt,
    promotedFieldValues,
  } = data;

  return (
    <div className="space-y-6">
      <RecordRecentItem href={`/org/${slug}/clients/${id}`} label={customer.name} />
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/clients`}
          className="text-muted-foreground hover:text-foreground inline-flex items-center text-sm transition-colors"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Clients
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
          headerFields={headerFields}
          tiles={buildClientHeroTiles(hero, orgSettings?.defaultCurrency ?? null)}
        />
      </div>

      {/* Anonymized Info Banner */}
      {customer.lifecycleStatus === "ANONYMIZED" && (
        <div className="border-border bg-muted flex items-start gap-3 rounded-lg border p-4">
          <ShieldCheck className="text-muted-foreground mt-0.5 size-5 shrink-0" />
          <div className="text-foreground text-sm">
            <p className="font-medium">Client data anonymized</p>
            <p className="text-muted-foreground mt-0.5">
              This client&apos;s personal data has been anonymized. All identifying information has
              been removed.
            </p>
          </div>
        </div>
      )}

      {/* Grouped Tabs — all panels as ReactNode props */}
      <CustomerGroupedTabs
        detailsPanel={
          <div className="space-y-6">
            <ClientDetailsTab customer={customer} />
            <CollectionsExemptionToggle
              slug={slug}
              customerId={id}
              isAdmin={isAdmin}
              exempt={customer.collectionsExempt ?? false}
            />
            {debtorDetail && (
              <Card data-testid="chase-history-card">
                <CardHeader>
                  <CardTitle>Chase history</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <CollectionsHistoryTable
                    activities={debtorDetail.activities.content}
                    slug={slug}
                    emptyMessage="No chase history for this client yet."
                  />
                  <CollectionsHistoryPagination
                    slug={slug}
                    customerId={id}
                    number={debtorDetail.activities.page.number}
                    totalPages={debtorDetail.activities.page.totalPages}
                  />
                </CardContent>
              </Card>
            )}
          </div>
        }
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
                    title: "Client Readiness",
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
            templateReadiness={
              customerTemplateReadiness.length > 0
                ? {
                    templates: customerTemplateReadiness,
                    baseHref: `/org/${slug}/clients/${id}`,
                  }
                : null
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
        mattersPanel={
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
              clientType={customer.clientType ?? null}
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
