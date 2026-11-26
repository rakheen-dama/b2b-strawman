import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { McpConnectorCard } from "./mcp-connector-card";
import { getMcpStatusAction } from "./mcp-actions";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Connections › MCP tab (Phase 87 S9, ADR-341) — the former MCP connector settings page's loader. */
export async function renderMcpTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin && !caps.capabilities.includes("INTEGRATION_MANAGE")) {
    return (
      <div className="space-y-8">
        <PageHeader title="MCP" />
        <p className="text-muted-foreground text-sm">
          You do not have permission to manage integrations. Contact your administrator.
        </p>
      </div>
    );
  }

  const status = await getMcpStatusAction();

  // The MCP server URL is authoritative on the backend (kazi.mcp.resource-url),
  // surfaced via the status payload. Fall back to the gateway-fronted /mcp path.
  const serverUrl =
    status?.serverUrl ?? `${process.env.NEXT_PUBLIC_GATEWAY_URL ?? "http://localhost:8443"}/mcp`;

  return (
    <div className="space-y-8">
      {/* K-124: the h1 is the tab label ("MCP"); the card keeps "Claude / MCP Connector". */}
      <PageHeader
        title="MCP"
        description={
          <>
            Connect your firm&apos;s own Claude (Desktop or Code) to Kazi over the Model Context
            Protocol, behind a POPIA data-egress acknowledgement.
          </>
        }
      />

      <div className="space-y-6">
        <McpConnectorCard status={status} slug={slug} serverUrl={serverUrl} />
      </div>
    </div>
  );
}
