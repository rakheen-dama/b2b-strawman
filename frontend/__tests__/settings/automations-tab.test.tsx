import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 87 S9 (616D, ADR-341 D21): the former automations page called notFound() for members
// without AUTOMATIONS; as a tab loader that would 404 the whole Connections family page, so the
// loader renders an in-panel denial instead.
const { mockCaps, mockListRules, mockNotFound } = vi.hoisted(() => ({
  mockCaps: vi.fn(),
  mockListRules: vi.fn(),
  mockNotFound: vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));
vi.mock("@/lib/api/automations", () => ({ listRules: () => mockListRules() }));
vi.mock("next/navigation", () => ({ notFound: mockNotFound, redirect: vi.fn() }));
vi.mock("@/components/automations/rule-list", () => ({
  RuleList: ({ canManage }: { canManage: boolean }) => (
    <div data-testid="rule-list" data-can-manage={String(canManage)} />
  ),
}));

import { renderAutomationsTab } from "@/app/(app)/org/[slug]/settings/connections/_tabs/automations";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };
const AUTOMATIONS_HOLDER = {
  capabilities: ["AUTOMATIONS"],
  role: "member",
  isAdmin: false,
  isOwner: false,
};

async function renderTab() {
  render(await renderAutomationsTab({ slug: "acme", searchParams: {} }));
}

describe("Connections › Automations tab (renderAutomationsTab)", () => {
  beforeEach(() => {
    mockListRules.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders an in-panel denial for a member without AUTOMATIONS — never notFound (D21)", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderTab();
    expect(mockNotFound).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { level: 1, name: "Automations" })).toBeInTheDocument();
    expect(
      screen.getByText("You do not have permission to view automation rules.")
    ).toBeInTheDocument();
    expect(screen.queryByTestId("rule-list")).not.toBeInTheDocument();
    expect(mockListRules).not.toHaveBeenCalled();
  });

  it("gives an owner the managed rule list and the execution-log link", async () => {
    mockCaps.mockResolvedValue(OWNER);
    await renderTab();
    expect(screen.getByTestId("rule-list")).toHaveAttribute("data-can-manage", "true");
    expect(screen.getByRole("link", { name: /View Execution Log/ })).toHaveAttribute(
      "href",
      "/org/acme/settings/connections/automations/executions"
    );
  });

  it("gives an AUTOMATIONS holder the rule list read-only, without the execution-log link", async () => {
    mockCaps.mockResolvedValue(AUTOMATIONS_HOLDER);
    await renderTab();
    expect(screen.getByTestId("rule-list")).toHaveAttribute("data-can-manage", "false");
    expect(screen.queryByRole("link", { name: /View Execution Log/ })).not.toBeInTheDocument();
  });
});
