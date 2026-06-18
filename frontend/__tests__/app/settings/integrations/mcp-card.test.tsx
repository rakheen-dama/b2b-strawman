import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { McpConnectorCard } from "@/app/(app)/org/[slug]/settings/integrations/mcp/mcp-connector-card";
import type { McpStatus } from "@/lib/types";

const mockEnableMcpAction = vi.fn();
const mockRevokeMcpAction = vi.fn();
const mockRefresh = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/integrations/mcp/actions", () => ({
  enableMcpAction: (...args: unknown[]) => mockEnableMcpAction(...args),
  revokeMcpAction: (...args: unknown[]) => mockRevokeMcpAction(...args),
  getMcpStatusAction: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRefresh, push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
}));

const SERVER_URL = "http://localhost:8443/mcp";

const disabledStatus: McpStatus = {
  effectivelyEnabled: false,
  integrationEnabled: false,
  serverUrl: SERVER_URL,
  consent: { granted: false, action: null, version: null, consentedBy: null, consentedAt: null },
};

const enabledStatus: McpStatus = {
  effectivelyEnabled: true,
  integrationEnabled: true,
  serverUrl: SERVER_URL,
  consent: {
    granted: true,
    action: "GRANTED",
    version: "popia-egress-v1",
    consentedBy: "3f9a0000-0000-0000-0000-000000000001",
    consentedAt: "2026-06-17T09:30:00Z",
  },
};

describe("McpConnectorCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockEnableMcpAction.mockResolvedValue({ success: true, data: enabledStatus });
    mockRevokeMcpAction.mockResolvedValue({ success: true, data: disabledStatus });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders the Disabled state with an enable toggle", () => {
    render(<McpConnectorCard status={disabledStatus} slug="acme" serverUrl={SERVER_URL} />);

    expect(screen.getByText("Disabled")).toBeInTheDocument();
    const toggle = screen.getByRole("switch", { name: "Enabled" });
    expect(toggle).toBeInTheDocument();
    expect(toggle).toHaveAttribute("aria-checked", "false");
    // Server URL + instructions are hidden until enabled
    expect(screen.queryByText("MCP server URL")).not.toBeInTheDocument();
  });

  it("opens the POPIA consent modal when the toggle is switched on", async () => {
    const user = userEvent.setup();
    render(<McpConnectorCard status={disabledStatus} slug="acme" serverUrl={SERVER_URL} />);

    await user.click(screen.getByRole("switch", { name: "Enabled" }));

    expect(screen.getByText("Acknowledge POPIA responsibility")).toBeInTheDocument();
    expect(screen.getByText(/responsible party/i)).toBeInTheDocument();
    expect(mockEnableMcpAction).not.toHaveBeenCalled();
  });

  it("calls enableMcpAction with the consent version when consent is acknowledged", async () => {
    const user = userEvent.setup();
    render(<McpConnectorCard status={disabledStatus} slug="acme" serverUrl={SERVER_URL} />);

    await user.click(screen.getByRole("switch", { name: "Enabled" }));
    await user.click(screen.getByRole("button", { name: /I acknowledge & enable/i }));

    expect(mockEnableMcpAction).toHaveBeenCalledWith("popia-egress-v1", "acme");
  });

  it("renders the server URL and connection instructions when enabled", () => {
    render(<McpConnectorCard status={enabledStatus} slug="acme" serverUrl={SERVER_URL} />);

    expect(screen.getByText("Enabled")).toBeInTheDocument();
    expect(screen.getByText("MCP server URL")).toBeInTheDocument();
    expect(screen.getAllByText(SERVER_URL).length).toBeGreaterThan(0);
    expect(screen.getByText(/Connect from Claude Desktop or Claude Code/i)).toBeInTheDocument();
  });

  it("renders the consent metadata (version + who) when enabled", () => {
    render(<McpConnectorCard status={enabledStatus} slug="acme" serverUrl={SERVER_URL} />);

    expect(screen.getByText("POPIA consent")).toBeInTheDocument();
    expect(screen.getByText("popia-egress-v1")).toBeInTheDocument();
    expect(screen.getByText(/3f9a0000-0000-0000-0000-000000000001/)).toBeInTheDocument();
  });

  it("opens the revoke confirmation dialog when the toggle is switched off", async () => {
    const user = userEvent.setup();
    render(<McpConnectorCard status={enabledStatus} slug="acme" serverUrl={SERVER_URL} />);

    await user.click(screen.getByRole("switch", { name: "Enabled" }));

    expect(screen.getByText("Revoke MCP connector access?")).toBeInTheDocument();
    expect(mockRevokeMcpAction).not.toHaveBeenCalled();
  });

  it("calls revokeMcpAction when the revoke control is confirmed", async () => {
    const user = userEvent.setup();
    render(<McpConnectorCard status={enabledStatus} slug="acme" serverUrl={SERVER_URL} />);

    await user.click(screen.getByRole("button", { name: "Revoke access" }));
    // Confirm in the AlertDialog
    const confirmButtons = screen.getAllByRole("button", { name: /Revoke access/i });
    // The dialog action is the last "Revoke access" button rendered
    await user.click(confirmButtons[confirmButtons.length - 1]);

    expect(mockRevokeMcpAction).toHaveBeenCalledWith("acme");
  });
});
