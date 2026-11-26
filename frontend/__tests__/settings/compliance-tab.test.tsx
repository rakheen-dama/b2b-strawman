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

    const alerts = screen.getAllByRole("alert");
    expect(alerts[0]).toHaveTextContent(
      "Couldn't load the current matter retention period. Reload the page to change it."
    );
    expect(screen.getByRole("heading", { level: 2, name: "Matter retention" })).toBeInTheDocument();
    expect(screen.queryByTestId("matter-retention-form-mount")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save retention period" })).not.toBeInTheDocument();
    expect(screen.queryByRole("spinbutton")).not.toBeInTheDocument();
    // 655B: General Settings (dormancy, deadline, FICA review intervals) saves every field, so it
    // is not offered on fallback values either.
    expect(screen.queryByTestId("compliance-settings-form-mount")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "General Settings" })).toBeInTheDocument();
    // 665A: both load-error blocks are settings sections with no footer (nothing to save).
    for (const name of ["Matter retention", "General Settings"]) {
      const section = screen
        .getByRole("heading", { level: 2, name })
        .closest('[data-slot="settings-section"]') as HTMLElement;
      expect(section).not.toBeNull();
      expect(section.querySelector('[data-slot="card-footer"]')).toBeNull();
    }
    expect(alerts[1]).toHaveTextContent(
      "Couldn't load the current compliance settings. Reload the page to change them."
    );
    // The retention policies section keeps its existing behaviour.
    expect(screen.getByTestId("retention-policy-table-mount")).toBeInTheDocument();
  });

  it("settings loaded: General Settings mounts (no load error)", async () => {
    mockCaps.mockResolvedValue(ADMIN);

    render(await renderComplianceTab({ slug: "acme", searchParams: {} }));

    expect(screen.getByTestId("compliance-settings-form-mount")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("665A: one h1 'Compliance' via PageHeader and a Compliance Packs section (h2)", async () => {
    mockCaps.mockResolvedValue(OWNER);

    render(await renderComplianceTab({ slug: "acme", searchParams: {} }));

    const h1s = screen.getAllByRole("heading", { level: 1 });
    expect(h1s).toHaveLength(1);
    expect(h1s[0]).toHaveTextContent("Compliance");
    expect(h1s[0].closest('[data-slot="page-header"]')).not.toBeNull();
    const packs = screen
      .getByRole("heading", { level: 2, name: "Compliance Packs" })
      .closest('[data-slot="settings-section"]') as HTMLElement;
    expect(packs).not.toBeNull();
    expect(packs).toContainElement(screen.getByTestId("compliance-pack-list-mount"));
    expect(
      screen.getByText(
        "Applied compliance packs and their versions. Click a pack to view its full contents."
      )
    ).toBeInTheDocument();
  });

  it("665A: member branch keeps exactly one h1 'Compliance'", async () => {
    mockCaps.mockResolvedValue(MEMBER);

    render(await renderComplianceTab({ slug: "acme", searchParams: {} }));

    const h1s = screen.getAllByRole("heading", { level: 1 });
    expect(h1s).toHaveLength(1);
    expect(h1s[0]).toHaveTextContent("Compliance");
  });
});
