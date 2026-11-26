import Link from "next/link";
import { Calculator, ExternalLink } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { SettingsSection } from "@/components/settings/settings-section";
import { IntegrationSectionTitle } from "@/components/integrations/integration-section-title";
import { settingsHref } from "@/lib/settings-nav";
import type { XeroConnectionStatus } from "@/lib/types";

interface AccountingIntegrationCardProps {
  slug: string;
  xeroStatus: XeroConnectionStatus | null;
  xeroOrgName: string | null;
}

export function AccountingIntegrationCard({
  slug,
  xeroStatus,
  xeroOrgName,
}: AccountingIntegrationCardProps) {
  function getStatusBadge() {
    switch (xeroStatus) {
      case "CONNECTED":
        return <Badge variant="success">Connected</Badge>;
      case "TOKEN_EXPIRED":
        return <Badge variant="warning">Reconnect Required</Badge>;
      case "REVOKED":
        return <Badge variant="destructive">Revoked</Badge>;
      default:
        return <Badge variant="neutral">Not Connected</Badge>;
    }
  }

  return (
    <SettingsSection
      title={<IntegrationSectionTitle icon={Calculator}>Accounting</IntegrationSectionTitle>}
      description={
        xeroStatus === "CONNECTED" && xeroOrgName
          ? `Connected to ${xeroOrgName} via Xero`
          : "Connect your accounting software for fee note sync"
      }
      actions={getStatusBadge()}
    >
      <Button variant="outline" size="sm" asChild>
        <Link href={`${settingsHref(slug, "connections", "xero")}#xero`}>
          <ExternalLink className="mr-2 size-4" />
          Configure Xero
        </Link>
      </Button>
    </SettingsSection>
  );
}
