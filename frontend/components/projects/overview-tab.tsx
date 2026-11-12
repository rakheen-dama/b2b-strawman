import { KPIDashboard } from "@/components/projects/kpi-dashboard";
import type { SetupStep } from "@/components/setup/types";
import type { WidgetResult } from "@/lib/dashboard-home";
import type {
  BudgetStatusResponse,
  MatterSetupStatus,
  FicaStatus,
  MatterTrustBalance,
  MatterUpcomingDeadline,
  OutstandingSummary,
} from "@/lib/types";

interface OverviewTabProps {
  projectId: string;
  projectName: string;
  projectStatus: string;
  customerName: string | null;
  customerId: string | null;
  canManage: boolean;
  slug: string;
  setupStatus: MatterSetupStatus | null;
  setupSteps: SetupStep[];
  ficaStatus: FicaStatus | null;
  retentionClockStartedAt: string | null;
  retentionEndsOn: string | null;
  trustEnabled: boolean;
  outstandingSummary: WidgetResult<OutstandingSummary>;
  trustBalance: WidgetResult<MatterTrustBalance>;
  projectDueDate?: string | null;
  /** Read once by the page and passed through to KPIDashboard (641B). */
  upcomingDeadlines: MatterUpcomingDeadline[];
  /** Read once by the page (shared with the Budget tab) and passed through (641B). */
  budget: BudgetStatusResponse | null;
}

export async function OverviewTab({
  projectId,
  projectName,
  projectStatus,
  customerName,
  customerId,
  canManage,
  slug,
  setupStatus,
  setupSteps,
  ficaStatus,
  retentionClockStartedAt,
  retentionEndsOn,
  trustEnabled,
  outstandingSummary,
  trustBalance,
  projectDueDate,
  upcomingDeadlines,
  budget,
}: OverviewTabProps) {
  return (
    <KPIDashboard
      projectId={projectId}
      projectName={projectName}
      projectStatus={projectStatus}
      slug={slug}
      canManage={canManage}
      customerName={customerName}
      customerId={customerId}
      setupStatus={setupStatus}
      setupSteps={setupSteps}
      ficaStatus={ficaStatus}
      retentionClockStartedAt={retentionClockStartedAt}
      retentionEndsOn={retentionEndsOn}
      trustEnabled={trustEnabled}
      outstandingSummary={outstandingSummary}
      trustBalance={trustBalance}
      projectDueDate={projectDueDate}
      upcomingDeadlines={upcomingDeadlines}
      budget={budget}
    />
  );
}
