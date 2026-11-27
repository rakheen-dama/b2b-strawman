import Link from "next/link";
import { Bot, ExternalLink } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { SettingsSection } from "@/components/settings/settings-section";
import { IntegrationSectionTitle } from "@/components/integrations/integration-section-title";
import { settingsHref } from "@/lib/settings-nav";
import type { McpStatus } from "@/lib/types";

interface McpIntegrationCardProps {
  slug: string;
  status: McpStatus | null;
}

export function McpIntegrationCard({ slug, status }: McpIntegrationCardProps) {
  const enabled = status?.effectivelyEnabled ?? false;

  return (
    <SettingsSection
      title={<IntegrationSectionTitle icon={Bot}>Claude / MCP Connector</IntegrationSectionTitle>}
      description={
        <>Let your firm&apos;s own Claude read your Kazi data over the Model Context Protocol</>
      }
      actions={
        enabled ? (
          <Badge variant="success">Enabled</Badge>
        ) : (
          <Badge variant="neutral">Disabled</Badge>
        )
      }
    >
      <Button variant="outline" size="sm" asChild>
        <Link href={settingsHref(slug, "connections", "mcp")}>
          <ExternalLink className="mr-2 size-4" />
          Configure Connector
        </Link>
      </Button>
    </SettingsSection>
  );
}
