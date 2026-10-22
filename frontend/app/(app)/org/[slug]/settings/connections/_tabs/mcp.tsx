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
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Claude / MCP Connector
          </h1>
          <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
            You do not have permission to manage integrations. Contact your administrator.
          </p>
        </div>
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
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Claude / MCP Connector
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Connect your firm&apos;s own Claude (Desktop or Code) to Kazi over the Model Context
          Protocol, behind a POPIA data-egress acknowledgement.
        </p>
      </div>

      <div className="space-y-6">
        <McpConnectorCard status={status} slug={slug} serverUrl={serverUrl} />
      </div>
    </div>
  );
}
