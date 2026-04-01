import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// --- Mocks (before component imports) ---

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/conflict-check",
}));

vi.mock("@/app/(app)/org/[slug]/conflict-check/actions", () => ({
  performConflictCheck: vi.fn().mockResolvedValue({
    success: true,
    data: {
      id: "cc-1",
      checkedName: "Test Party",
      checkType: "NEW_CLIENT",
      result: "NO_CONFLICT",
      conflictsFound: null,
      resolution: null,
      checkedBy: "member-1",
      checkedAt: "2026-04-01T10:00:00Z",
    },
  }),
  fetchConflictChecks: vi
    .fn()
    .mockResolvedValue({ content: [], page: { totalElements: 0 } }),
  fetchConflictCheck: vi.fn(),
  resolveConflict: vi.fn().mockResolvedValue({ success: true }),
  fetchProjects: vi.fn().mockResolvedValue([]),
  fetchCustomers: vi.fn().mockResolvedValue([]),
}));

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn().mockResolvedValue({ content: [] }),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

// --- Imports after mocks ---

import { ConflictCheckForm } from "@/components/legal/conflict-check-form";
import { ConflictCheckResultDisplay } from "@/components/legal/conflict-check-result";
import { ConflictCheckHistory } from "@/components/legal/conflict-check-history";
import { OrgProfileProvider } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";
import { NAV_GROUPS } from "@/lib/nav-items";
import type { ConflictCheck } from "@/lib/types";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

// --- Helpers ---

function makeConflictCheck(
  overrides: Partial<ConflictCheck> = {}
): ConflictCheck {
  return {
    id: "cc-1",
    checkedName: "Ndlovu Trading",
    checkedIdNumber: null,
    checkedRegistrationNumber: "2020/123456/07",
    checkType: "NEW_CLIENT",
    result: "NO_CONFLICT",
    conflictsFound: null,
    resolution: null,
    resolutionNotes: null,
    waiverDocumentId: null,
    checkedBy: "member-1",
    resolvedBy: null,
    checkedAt: "2026-04-01T10:00:00Z",
    resolvedAt: null,
    customerId: null,
    projectId: null,
    ...overrides,
  };
}

// --- Tests ---

describe("ConflictCheckForm", () => {
  it("renders required form fields", () => {
    render(<ConflictCheckForm slug="acme" />);

    const form = screen.getByTestId("conflict-check-form");
    expect(form).toBeInTheDocument();
    expect(screen.getByLabelText("Name to Check *")).toBeInTheDocument();
    expect(screen.getByLabelText("Check Type *")).toBeInTheDocument();
    expect(screen.getByLabelText("ID Number")).toBeInTheDocument();
    expect(screen.getByLabelText("Registration Number")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Run Conflict Check/i })
    ).toBeInTheDocument();
  });
});

describe("ConflictCheckResultDisplay", () => {
  it("displays CONFLICT_FOUND result with red styling", () => {
    const check = makeConflictCheck({
      result: "CONFLICT_FOUND",
      conflictsFound: [
        {
          adversePartyId: "ap-1",
          adversePartyName: "Ndlovu Trading",
          projectId: "proj-1",
          projectName: "Mabaso v Ndlovu Trading",
          customerId: "cust-1",
          customerName: "Mabaso Holdings",
          relationship: "OPPOSING_PARTY",
          matchType: "REGISTRATION_NUMBER_EXACT",
          similarityScore: 1.0,
          explanation: "Exact registration number match",
        },
      ],
    });

    render(<ConflictCheckResultDisplay result={check} slug="acme" />);

    const resultEl = screen.getByTestId("conflict-check-result");
    expect(resultEl).toBeInTheDocument();
    // "Conflict Found" appears in both heading and badge
    expect(screen.getAllByText("Conflict Found").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Ndlovu Trading")).toBeInTheDocument();
    expect(
      screen.getByText("Mabaso v Ndlovu Trading")
    ).toBeInTheDocument();
    expect(screen.getByText("100%")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Resolve Conflict/i })
    ).toBeInTheDocument();
  });

  it("displays NO_CONFLICT result with green styling", () => {
    const check = makeConflictCheck({ result: "NO_CONFLICT" });

    render(<ConflictCheckResultDisplay result={check} slug="acme" />);

    const resultEl = screen.getByTestId("conflict-check-result");
    expect(resultEl).toBeInTheDocument();
    // "No Conflict" appears in both heading and badge
    expect(screen.getAllByText("No Conflict").length).toBeGreaterThanOrEqual(1);
    // No resolve button for clean checks
    expect(
      screen.queryByRole("button", { name: /Resolve Conflict/i })
    ).not.toBeInTheDocument();
  });
});

describe("ConflictCheckHistory", () => {
  it("renders check records in table", () => {
    const checks: ConflictCheck[] = [
      makeConflictCheck({ id: "cc-1", checkedName: "Alpha Corp", result: "NO_CONFLICT" }),
      makeConflictCheck({
        id: "cc-2",
        checkedName: "Beta Holdings",
        result: "CONFLICT_FOUND",
        checkType: "NEW_MATTER",
      }),
    ];

    render(
      <ConflictCheckHistory
        initialChecks={checks}
        initialTotal={2}
        slug="acme"
      />
    );

    const history = screen.getByTestId("conflict-check-history");
    expect(history).toBeInTheDocument();
    expect(screen.getByText("Alpha Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta Holdings")).toBeInTheDocument();
    // "No Conflict" appears in both filter dropdown and result badge
    expect(screen.getAllByText("No Conflict").length).toBeGreaterThanOrEqual(1);
    // "Conflict" badge for CONFLICT_FOUND result
    expect(screen.getAllByText("Conflict").length).toBeGreaterThanOrEqual(1);
  });
});

describe("ResolveConflictDialog", () => {
  it("opens and submits resolution", async () => {
    const user = userEvent.setup();
    const check = makeConflictCheck({
      result: "CONFLICT_FOUND",
      conflictsFound: [
        {
          adversePartyId: "ap-1",
          adversePartyName: "Test",
          projectId: "proj-1",
          projectName: "Matter 1",
          customerId: "cust-1",
          customerName: "Client 1",
          relationship: "OPPOSING_PARTY",
          matchType: "NAME_SIMILARITY",
          similarityScore: 0.85,
          explanation: "Name similarity match",
        },
      ],
    });

    render(<ConflictCheckResultDisplay result={check} slug="acme" />);

    // Click resolve button
    await user.click(
      screen.getByRole("button", { name: /Resolve Conflict/i })
    );

    // Dialog should appear
    const dialog = screen.getByTestId("resolve-conflict-dialog");
    expect(dialog).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Resolve Conflict" })
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Resolution *")).toBeInTheDocument();
    expect(screen.getByLabelText("Notes")).toBeInTheDocument();
  });
});

describe("Nav items - conflict check", () => {
  it("conflict-check nav item has requiredModule: conflict_check", () => {
    const clientsGroup = NAV_GROUPS.find((g) => g.id === "clients");
    const conflictItem = clientsGroup?.items.find(
      (i) => i.label === "Conflict Check"
    );
    expect(conflictItem).toBeDefined();
    expect(conflictItem?.requiredModule).toBe("conflict_check");
    expect(conflictItem?.requiredCapability).toBeUndefined();
  });

  it("conflict-check module gate hides content when disabled", () => {
    render(
      <OrgProfileProvider
        verticalProfile={null}
        enabledModules={[]}
        terminologyNamespace={null}
      >
        <ModuleGate module="conflict_check">
          <span>Conflict Check Content</span>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(
      screen.queryByText("Conflict Check Content")
    ).not.toBeInTheDocument();
  });

  it("conflict-check module gate shows content when enabled", () => {
    render(
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["conflict_check"]}
        terminologyNamespace={null}
      >
        <ModuleGate module="conflict_check">
          <span>Conflict Check Content</span>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(screen.getByText("Conflict Check Content")).toBeInTheDocument();
  });
});
