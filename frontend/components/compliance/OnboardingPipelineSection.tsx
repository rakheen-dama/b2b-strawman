import {
  OnboardingPipelineTable,
  type OnboardingPipelineRow,
} from "@/components/compliance/OnboardingPipelineTable";

interface OnboardingCustomer {
  id: string;
  name: string;
  lifecycleStatusChangedAt: string | null;
  checklistProgress: { completed: number; total: number };
}

interface OnboardingPipelineSectionProps {
  customers: OnboardingCustomer[];
  orgSlug: string;
}

function getDaysInOnboarding(changedAt: string | null): number {
  if (!changedAt) return 0;
  return Math.floor((Date.now() - new Date(changedAt).getTime()) / 86400000);
}

export function OnboardingPipelineSection({ customers, orgSlug }: OnboardingPipelineSectionProps) {
  // Day counts are computed here on the server and handed to the client table as data (DA-7).
  const rows: OnboardingPipelineRow[] = customers
    .map((customer) => ({
      id: customer.id,
      name: customer.name,
      days: getDaysInOnboarding(customer.lifecycleStatusChangedAt),
      completed: customer.checklistProgress.completed,
      total: customer.checklistProgress.total,
    }))
    .sort((a, b) => b.days - a.days);

  return (
    <div className="space-y-4">
      <h2 className="text-foreground text-lg font-semibold">Onboarding Pipeline</h2>
      {rows.length === 0 ? (
        <p className="text-muted-foreground text-sm">No clients currently in onboarding</p>
      ) : (
        <OnboardingPipelineTable rows={rows} orgSlug={orgSlug} />
      )}
    </div>
  );
}
