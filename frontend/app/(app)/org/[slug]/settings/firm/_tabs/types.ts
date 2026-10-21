export type SettingsSearchParams = Record<string, string | string[] | undefined>;

/**
 * Props every Firm tab loader receives; `searchParams` is the page's full query (the audit trail
 * reads its filters from it).
 */
export interface FirmTabProps {
  slug: string;
  searchParams: SettingsSearchParams;
}
