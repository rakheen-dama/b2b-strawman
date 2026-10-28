import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 88 U17 (ADR-354): Firm › Compliance mounts the "Matter retention period (years)" section
// first, fed from the one GET /api/settings read, editable only by the firm owner.

vi.mock("server-only", () => ({}));
const { mockCaps, mockApiGet } = vi.hoisted(() => ({ mockCaps: vi.fn(), mockApiGet: vi.fn() }));
vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));
vi.mock("@/lib/api", () => ({ api: { get: (...args: unknown[]) => mockApiGet(...args) } }));

// The sections have their own tests; here we only pin where and with what they are mounted.
vi.mock("@/components/compliance/MatterRetentionSettingsForm", () => ({
  MatterRetentionSettingsForm: ({
    slug,
    legalMatterRetentionYears,
    canEdit,
  }: {
    slug: string;
    legalMatterRetentionYears: number;
    canEdit: boolean;
  }) => (
    <section data-testid="matter-retention-form-mount">{`${slug}|${legalMatterRetentionYears}|${canEdit}`}</section>
  ),
}));
vi.mock("@/components/compliance/ComplianceSettingsForm", () => ({
  ComplianceSettingsForm: () => <section data-testid="compliance-settings-form-mount" />,
}));
vi.mock("@/components/compliance/RetentionPolicyTable", () => ({
  RetentionPolicyTable: () => <section data-testid="retention-policy-table-mount" />,
}));
vi.mock("@/components/compliance/CompliancePackList", () => ({
  CompliancePackList: () => <div data-testid="compliance-pack-list-mount" />,
}));

import { renderComplianceTab } from "@/app/(app)/org/[slug]/settings/firm/_tabs/compliance";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const ADMIN = { capabilities: [], role: "admin", isAdmin: true, isOwner: false };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };

function routeGets(settings: Record<string, unknown> | Error) {
  mockApiGet.mockImplementation((url: string) => {
    if (url === "/api/settings") {
      return settings instanceof Error ? Promise.reject(settings) : Promise.resolve(settings);
    }
    if (url === "/api/retention-policies") return Promise.resolve([]);
    return Promise.reject(new Error(`unexpected GET ${url}`));
  });
}

const SETTINGS = {
  defaultCurrency: "ZAR",
  dormancyThresholdDays: 365,
  dataRequestDeadlineDays: 30,
  legalMatterRetentionYears: 5,
  compliancePackStatus: [],
};

afterEach(() => {
  cleanup();
  mockCaps.mockReset();
  mockApiGet.mockReset();
});

describe("renderComplianceTab — matter retention period (Phase 88 U17)", () => {
  beforeEach(() => {
    routeGets(SETTINGS);
  });

  it("owner: mounts the editable retention form with the firm's years, before General Settings", async () => {
    mockCaps.mockResolvedValue(OWNER);

    render(await renderComplianceTab({ slug: "acme", searchParams: {} }));

    const mount = screen.getByTestId("matter-retention-form-mount");
    expect(mount).toHaveTextContent("acme|5|true");
    const general = screen.getByTestId("compliance-settings-form-mount");
    expect(mount.compareDocumentPosition(general) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING
    );
    // One settings read feeds every section.
    expect(mockApiGet.mock.calls.filter(([url]) => url === "/api/settings")).toHaveLength(1);
    expect(screen.getByText(/Manage the matter retention period/)).toBeInTheDocument();
  });

  it("owner: shows a non-default setting (7)", async () => {
    mockCaps.mockResolvedValue(OWNER);
    routeGets({ ...SETTINGS, legalMatterRetentionYears: 7 });

    render(await renderComplianceTab({ slug: "acme", searchParams: {} }));

    expect(screen.getByTestId("matter-retention-form-mount")).toHaveTextContent("acme|7|true");
  });

  it("admin: mounts the retention form read-only", async () => {
    mockCaps.mockResolvedValue(ADMIN);

    render(await renderComplianceTab({ slug: "acme", searchParams: {} }));

    expect(screen.getByTestId("matter-retention-form-mount")).toHaveTextContent("acme|5|false");
  });

  it("member: denial copy, no form, no settings read", async () => {
    mockCaps.mockResolvedValue(MEMBER);

    render(await renderComplianceTab({ slug: "acme", searchParams: {} }));

    expect(
      screen.getByText(/You do not have permission to manage compliance settings/)
    ).toBeInTheDocument();
    expect(screen.queryByTestId("matter-retention-form-mount")).not.toBeInTheDocument();
    expect(mockApiGet).not.toHaveBeenCalledWith("/api/settings");
  });

  it("settings read omits the period: falls back to the 5-year default", async () => {
    mockCaps.mockResolvedValue(OWNER);
    const withoutYears: Record<string, unknown> = { ...SETTINGS };
    delete withoutYears.legalMatterRetentionYears;
    routeGets(withoutYears);

    render(await renderComplianceTab({ slug: "acme", searchParams: {} }));

    expect(screen.getByTestId("matter-retention-form-mount")).toHaveTextContent("acme|5|true");
  });

  it("settings read failed: load error instead of the form, nothing to save", async () => {
    mockCaps.mockResolvedValue(OWNER);
    routeGets(new Error("settings down"));

    render(await renderComplianceTab({ slug: "acme", searchParams: {} }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Couldn't load the current matter retention period. Reload the page to change it."
    );
    expect(screen.getByRole("heading", { name: "Matter retention" })).toBeInTheDocument();
    expect(screen.queryByTestId("matter-retention-form-mount")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save retention period" })).not.toBeInTheDocument();
    expect(screen.queryByRole("spinbutton")).not.toBeInTheDocument();
    // The sibling sections keep their existing fallback behaviour.
    expect(screen.getByTestId("compliance-settings-form-mount")).toBeInTheDocument();
  });
});
