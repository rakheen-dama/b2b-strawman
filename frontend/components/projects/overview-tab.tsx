import { KPIDashboard } from "@/components/projects/kpi-dashboard";
import type { SetupStep } from "@/components/setup/types";
import type { ProjectSetupStatus, FicaStatus } from "@/lib/types";

interface OverviewTabProps {
  projectId: string;
  projectName: string;
  projectStatus: string;
  customerName: string | null;
  customerId: string | null;
  canManage: boolean;
  slug: string;
  setupStatus: ProjectSetupStatus | null;
  setupSteps: SetupStep[];
  ficaStatus: FicaStatus | null;
  retentionClockStartedAt: string | null;
  retentionEndsOn: string | null;
  trustEnabled: boolean;
  disbursementsEnabled: boolean;
  projectDueDate?: string | null;
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
  disbursementsEnabled,
  projectDueDate,
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
      disbursementsEnabled={disbursementsEnabled}
      projectDueDate={projectDueDate}
    />
  );
}
