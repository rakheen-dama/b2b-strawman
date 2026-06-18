import "server-only";

import { api } from "./client";
import type { McpStatus } from "@/lib/types";

// ---- MCP Connector enablement (Epic 565C / 566) ----

/**
 * Enables the MCP connector for the current tenant. The backend records a
 * GRANTED POPIA data-egress consent row first, then enables the integration
 * atomically. Responds 204 No Content.
 */
export async function enableMcp(consentVersion: string): Promise<void> {
  return api.post<void>("/api/integrations/mcp/enable", { consentVersion });
}

/**
 * Revokes the MCP connector for the current tenant — disables the integration
 * and appends a REVOKED consent row. Responds 204 No Content.
 */
export async function revokeMcp(): Promise<void> {
  return api.post<void>("/api/integrations/mcp/revoke");
}

/** Current effective MCP connector state + latest consent metadata. */
export async function getMcpStatus(): Promise<McpStatus> {
  return api.get<McpStatus>("/api/integrations/mcp/status");
}
