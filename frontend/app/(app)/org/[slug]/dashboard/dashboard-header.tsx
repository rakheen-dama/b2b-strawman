import { PageHeader } from "@b2mash/ui/page-header";

/**
 * Home page header (614B; Phase 90 640A on PageHeader): nothing on Home is date-ranged, so no
 * range selector.
 */
export function DashboardHeader() {
  return <PageHeader title="Home" description="What needs you today" />;
}
