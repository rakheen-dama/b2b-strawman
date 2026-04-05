import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";

/**
 * Shared layout for all reconciliation routes (list + new wizard).
 * Gates on trust_accounting module and VIEW_TRUST capability so that
 * direct navigation to /reconciliation/new cannot bypass authorisation.
 */
export default async function ReconciliationLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  // Module gating
  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("trust_accounting")) {
    notFound();
  }

  // Capability check
  const capData = await fetchMyCapabilities();
  const hasViewTrust =
    capData.isAdmin ||
    capData.isOwner ||
    capData.capabilities.includes("VIEW_TRUST");
  if (!hasViewTrust) {
    notFound();
  }

  return <>{children}</>;
}
