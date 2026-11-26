import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 91 665B: Firm › Data protection and Firm › Data requests render their h1 through
// PageHeader in every branch (admin/owner and the no-permission branch), so each page keeps
// exactly one level-1 heading.

vi.mock("server-only", () => ({}));
const { mockCaps, mockApiGet, mockFetchDsar } = vi.hoisted(() => ({
  mockCaps: vi.fn(),
  mockApiGet: vi.fn(),
  mockFetchDsar: vi.fn(),
}));
vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));
vi.mock("@/lib/api", () => ({ api: { get: (...args: unknown[]) => mockApiGet(...args) } }));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/data-protection-actions", () => ({
  fetchRetentionPolicies: vi.fn().mockResolvedValue([]),
  fetchProcessingActivities: vi.fn().mockResolvedValue([]),
}));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/data-requests-actions", () => ({
  fetchDsarRequests: (...args: unknown[]) => mockFetchDsar(...args),
}));
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// The sections have their own tests; here we only pin the page frame.
vi.mock("@/components/settings/jurisdiction-selector", () => ({
  JurisdictionSelectorSection: () => <section data-testid="jurisdiction-mount" />,
}));
vi.mock("@/components/settings/information-officer-section", () => ({
  InformationOfficerSection: () => <section data-testid="information-officer-mount" />,
}));
vi.mock("@/components/data-protection/retention-policies-table", () => ({
  RetentionPoliciesTable: () => <section data-testid="retention-policies-mount" />,
}));
vi.mock("@/components/data-protection/processing-register-table", () => ({
  ProcessingRegisterTable: () => <section data-testid="processing-register-mount" />,
}));
vi.mock("@/components/data-protection/paia-manual-section", () => ({
  PaiaManualSection: () => <section data-testid="paia-mount" />,
}));
vi.mock("@/components/data-protection/dsar-requests-table", () => ({
  DsarRequestsTable: ({ requests }: { requests: unknown[] }) => (
    <div data-testid="dsar-table-mount">{requests.length}</div>
  ),
}));
vi.mock("@/components/data-protection/log-dsar-dialog", () => ({
  LogDsarRequestDialog: () => <button type="button">Log New Request</button>,
}));

import { renderDataProtectionTab } from "@/app/(app)/org/[slug]/settings/firm/_tabs/data-protection";
import { renderDataRequestsTab } from "@/app/(app)/org/[slug]/settings/firm/_tabs/data-requests";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };

function expectOneH1(text: string) {
  const h1s = screen.getAllByRole("heading", { level: 1 });
  expect(h1s).toHaveLength(1);
  expect(h1s[0]).toHaveTextContent(new RegExp(`^${text}$`));
}

beforeEach(() => {
  mockApiGet.mockResolvedValue({ defaultCurrency: "ZAR" });
  mockFetchDsar.mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
  mockCaps.mockReset();
  mockApiGet.mockReset();
  mockFetchDsar.mockReset();
});

describe("renderDataProtectionTab", () => {
  it("owner: one h1 'Data Protection', the DSAR section and every section mounted", async () => {
    mockCaps.mockResolvedValue(OWNER);

    render(await renderDataProtectionTab({ slug: "acme", searchParams: {} }));

    expectOneH1("Data Protection");
    expect(
      screen.getByText(
        "Configure data protection jurisdiction, information officer details, and manage compliance settings."
      )
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "Data Subject Access Requests" })
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View DSAR Requests →" })).toHaveAttribute(
      "href",
      "/org/acme/settings/firm?tab=data-requests"
    );
    for (const id of [
      "jurisdiction-mount",
      "information-officer-mount",
      "retention-policies-mount",
      "processing-register-mount",
      "paia-mount",
    ]) {
      expect(screen.getByTestId(id)).toBeInTheDocument();
    }
  });

  it("member: one h1 'Data Protection' and the permission copy, no sections", async () => {
    mockCaps.mockResolvedValue(MEMBER);

    render(await renderDataProtectionTab({ slug: "acme", searchParams: {} }));

    expectOneH1("Data Protection");
    expect(
      screen.getByText(/You do not have permission to manage data protection settings/)
    ).toBeInTheDocument();
    expect(screen.queryByTestId("jurisdiction-mount")).toBeNull();
    expect(mockApiGet).not.toHaveBeenCalled();
  });
});

describe("renderDataRequestsTab", () => {
  it("owner: one h1 'DSAR Requests' with the Log dialog in the header and the table", async () => {
    mockCaps.mockResolvedValue(OWNER);

    render(await renderDataRequestsTab({ slug: "acme", searchParams: {} }));

    expectOneH1("DSAR Requests");
    expect(screen.getByText("Track and manage data subject access requests.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Log New Request" })).toBeInTheDocument();
    expect(screen.getByTestId("dsar-table-mount")).toBeInTheDocument();
  });

  it("owner: a fetch failure shows the error line above the (empty) table", async () => {
    mockCaps.mockResolvedValue(OWNER);
    mockFetchDsar.mockRejectedValue(new Error("Backend down"));
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    render(await renderDataRequestsTab({ slug: "acme", searchParams: {} }));

    expectOneH1("DSAR Requests");
    expect(screen.getByText("Backend down")).toBeInTheDocument();
    expect(screen.getByTestId("dsar-table-mount")).toHaveTextContent("0");
    consoleError.mockRestore();
  });

  it("member: one h1 'DSAR Requests' and the permission copy, no table", async () => {
    mockCaps.mockResolvedValue(MEMBER);

    render(await renderDataRequestsTab({ slug: "acme", searchParams: {} }));

    expectOneH1("DSAR Requests");
    expect(
      screen.getByText(/You do not have permission to manage DSAR requests/)
    ).toBeInTheDocument();
    expect(screen.queryByTestId("dsar-table-mount")).toBeNull();
    expect(mockFetchDsar).not.toHaveBeenCalled();
  });
});
