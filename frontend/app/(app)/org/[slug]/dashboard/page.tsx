import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { Card, CardContent } from "@b2mash/ui/card";
import { fetchMyCapabilities, type MyCapabilitiesResponse } from "@/lib/api/capabilities";
import { getOrgSettings } from "@/lib/api/settings";
import { ApiError, handleApiError } from "@/lib/api";
import {
  listAuditEvents,
  listFacetEventTypes,
  type AuditEventResponse,
  type EventTypeFacet,
} from "@/lib/api/audit-events";
import { fetchHomeWidgets, type HomeWidgetData } from "@/lib/actions/dashboard";
import {
  buildHomePlan,
  rowSpanClass,
  withoutAbsent,
  type HomeWidget,
  type WidgetResult,
} from "@/lib/dashboard-home";
import { cn } from "@/lib/utils";
import { ProvisioningPendingRefresh } from "./provisioning-pending-refresh";
import { DashboardHeader } from "./dashboard-header";
import { ReturnToHandler } from "@/components/auth/return-to-handler";
import { GettingStartedCard } from "@/components/dashboard/getting-started-card";
import { TodaysDiary } from "@/components/dashboard/todays-agenda";
import { MyTasksWidget } from "@/components/dashboard/my-tasks-widget";
import { UnbilledWipWidget } from "@/components/dashboard/unbilled-wip-widget";
import { DebtorsWidget } from "@/components/dashboard/debtors-widget";
import { TrustPositionWidget } from "@/components/dashboard/trust-position-widget";
import { FicaOutstandingWidget } from "@/components/dashboard/fica-outstanding-widget";
import { MatterHealthWidget } from "@/components/dashboard/matter-health-widget";
import { RecentActivityWidget } from "@/components/dashboard/recent-activity-widget";
import { SensitiveEventsWidget } from "@/components/dashboard/sensitive-events-widget";

const SENSITIVE_LOOKBACK_MS = 7 * 24 * 60 * 60 * 1000;
const SENSITIVE_VIEW_ALL_LOOKBACK_DAYS = 30;

/** `ok` → data, `error` → null (the card shows its quiet error line). Absent never renders. */
function dataOf<T>(result: WidgetResult<T>): T | null {
  return result.state === "ok" ? result.data : null;
}

/** Widgets whose gated read came back 403/404 — they drop out of their row. */
function absentWidgets(data: HomeWidgetData): Set<HomeWidget> {
  const results: Array<[HomeWidget, WidgetResult<unknown>]> = [
    ["tasks", data.tasks],
    ["unbilledWip", data.unbilledWip],
    ["debtors", data.debtors],
    ["trustPosition", data.trustPosition],
    ["ficaOutstanding", data.ficaOutstanding],
  ];
  return new Set(results.filter(([, r]) => r.state === "absent").map(([w]) => w));
}

/**
 * Home (Phase 87 614B, arch §12.6.3): a capability-keyed widget matrix. The caller's capabilities
 * pick an audience (fee earner, bookkeeper/admin, owner), the audience picks the rows, and each
 * widget is fetched only when its own gate passes. A widget is present or absent, never greyed.
 */
export default async function OrgDashboardPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;

  // Capabilities are the call that fails while a new org is still provisioning (403, or 500
  // "Member context not available" until the member syncs) — the widget reads below fail soft.
  let caps: MyCapabilitiesResponse;
  let enabledModules: string[];
  try {
    const [capabilities, settings] = await Promise.all([
      fetchMyCapabilities(),
      getOrgSettings().catch((error) => {
        console.error("Failed to load org settings for Home:", error);
        return null;
      }),
    ]);
    caps = capabilities;
    enabledModules = settings?.enabledModules ?? [];
  } catch (error) {
    const isProvisioning =
      error instanceof ApiError &&
      (error.status === 403 ||
        (error.status === 500 && error.detail?.title === "Member context not available"));
    if (isProvisioning) {
      return (
        <div className="flex min-h-[40vh] flex-col items-center justify-center gap-4">
          <Loader2 className="text-muted-foreground size-8 animate-spin" />
          <div className="text-center">
            <h2 className="text-lg font-semibold">Setting up your workspace</h2>
            <p className="text-muted-foreground mt-1 text-sm">
              This usually takes just a few seconds.
            </p>
          </div>
          <ProvisioningPendingRefresh />
        </div>
      );
    }
    handleApiError(error);
  }

  const plan = buildHomePlan(
    { capabilities: caps.capabilities ?? [], isAdmin: caps.isAdmin, isOwner: caps.isOwner },
    enabledModules
  );
  const today = new Date().toLocaleDateString("en-CA");

  // Sensitive events (Epic 509A) — last 7 days, gated server-side so Admin-only events never
  // reach a Member's RSC payload. Timestamps computed once here: a Date.now() inside the client
  // widget's <Link href> would differ between SSR and CSR (hydration mismatch).
  const sensitiveTo = new Date();
  const sensitiveFromIso = new Date(sensitiveTo.getTime() - SENSITIVE_LOOKBACK_MS).toISOString();
  const sensitiveToIso = sensitiveTo.toISOString();
  const sensitiveViewAllFromIso = new Date(
    sensitiveTo.getTime() - SENSITIVE_VIEW_ALL_LOOKBACK_DAYS * 24 * 60 * 60 * 1000
  ).toISOString();

  const [data, sensitiveFacets, sensitiveRecent] = await Promise.all([
    fetchHomeWidgets(plan, today),
    plan.sensitive
      ? listFacetEventTypes({ from: sensitiveFromIso, to: sensitiveToIso }).catch(
          () => [] as EventTypeFacet[]
        )
      : Promise.resolve([] as EventTypeFacet[]),
    plan.sensitive
      ? listAuditEvents({
          severities: ["WARNING", "CRITICAL"],
          from: sensitiveFromIso,
          to: sensitiveToIso,
          size: 5,
        })
          .then((page) => page.content)
          .catch((): AuditEventResponse[] | null => null)
      : Promise.resolve(null),
  ]);

  const rows = withoutAbsent(plan.rows, absentWidgets(data));

  function renderWidget(widget: HomeWidget): ReactNode {
    switch (widget) {
      case "diary":
        return (
          <Card data-testid="home-diary-widget" className="gap-0">
            <CardContent>
              <TodaysDiary
                slug={slug}
                today={today}
                diaryEvents={data.diary}
                headingStyle="title"
              />
            </CardContent>
          </Card>
        );
      case "tasks":
        return <MyTasksWidget slug={slug} today={today} data={dataOf(data.tasks)} />;
      case "unbilledWip":
        return (
          <UnbilledWipWidget
            slug={slug}
            data={dataOf(data.unbilledWip)}
            personal={plan.audience === "feeEarner"}
            showLink={plan.unbilledWipLink}
          />
        );
      case "debtors":
        return <DebtorsWidget slug={slug} data={dataOf(data.debtors)} />;
      case "trustPosition":
        return <TrustPositionWidget slug={slug} data={dataOf(data.trustPosition)} />;
      case "ficaOutstanding":
        return <FicaOutstandingWidget slug={slug} data={dataOf(data.ficaOutstanding)} />;
      case "matterHealth":
        return <MatterHealthWidget matters={data.matterHealth} orgSlug={slug} />;
      case "activity":
        return <RecentActivityWidget items={data.activity} orgSlug={slug} />;
      case "sensitive":
        return (
          <SensitiveEventsWidget
            orgSlug={slug}
            facets={sensitiveFacets}
            recent={sensitiveRecent}
            viewAllFromIso={sensitiveViewAllFromIso}
          />
        );
    }
  }

  return (
    <div className="space-y-6" data-testid="home-page" data-audience={plan.audience}>
      {/* Post-login return-to read-back. This is the real landing surface after
          the `/dashboard` → `/org/{slug}/dashboard` redirect chain, so the
          persisted `kazi.returnTo` is consumed + forwarded here (569A.3). */}
      <ReturnToHandler />

      <GettingStartedCard />

      <DashboardHeader />

      {rows.map((row) => (
        <div key={row.join("+")} className="grid grid-cols-1 gap-4 lg:grid-cols-12">
          {row.map((widget) => (
            <div
              key={widget}
              data-testid={`home-cell-${widget}`}
              className={cn(rowSpanClass(row.length), "flex min-w-0 flex-col [&>*]:flex-1")}
            >
              {renderWidget(widget)}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
