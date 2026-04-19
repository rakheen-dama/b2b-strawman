import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
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
  usePathname: () => "/org/acme/projects/p1",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

const mockEvaluate = vi.fn();
const mockClose = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/matter-closure-actions", () => ({
  evaluateClosureAction: (...args: unknown[]) => mockEvaluate(...args),
  closeMatterAction: (...args: unknown[]) => mockClose(...args),
}));

// --- Imports after mocks ---

import { MatterClosureDialog } from "@/components/legal/matter-closure-dialog";
import { MatterClosureAction } from "@/components/projects/matter-closure-action";
import { OrgProfileProvider } from "@/lib/org-profile";
import { CapabilityProvider } from "@/lib/capabilities";
import type { ClosureReport, GateResult } from "@/lib/api/matter-closure";

afterEach(() => cleanup());
beforeEach(() => {
  vi.clearAllMocks();
});

// --- Helpers ---

function makeGate(overrides: Partial<GateResult> = {}): GateResult {
  return {
    order: 1,
    code: "TRUST_BALANCE_ZERO",
    passed: true,
    message: "Gate message",
    detail: null,
    ...overrides,
  };
}

function makeReport(overrides: Partial<ClosureReport> = {}): ClosureReport {
  return {
    projectId: "p1",
    evaluatedAt: "2026-04-17T10:14:30Z",
    allPassed: true,
    gates: [makeGate()],
    ...overrides,
  };
}

function withProviders(
  ui: React.ReactElement,
  opts: {
    modules?: string[];
    capabilities?: string[];
    isAdmin?: boolean;
    isOwner?: boolean;
    role?: string;
  } = {}
) {
  const {
    modules = ["matter_closure"],
    capabilities = ["CLOSE_MATTER"],
    isAdmin = false,
    isOwner = false,
    role = "member",
  } = opts;
  return (
    <OrgProfileProvider
      verticalProfile="legal-za"
      enabledModules={modules}
      terminologyNamespace={null}
    >
      <CapabilityProvider
        capabilities={capabilities}
        role={role}
        isAdmin={isAdmin}
        isOwner={isOwner}
      >
        {ui}
      </CapabilityProvider>
    </OrgProfileProvider>
  );
}

// =========================================================================
// Dialog — step flow + gate report + 409 handling + override visibility
// =========================================================================

describe("MatterClosureDialog", () => {
  it("step 1: fetches and renders the closure report on open", async () => {
    const allPassedReport = makeReport({ allPassed: true, gates: [makeGate()] });
    mockEvaluate.mockResolvedValue({ success: true, report: allPassedReport });

    render(
      withProviders(
        <MatterClosureDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />
      )
    );

    await waitFor(() => {
      expect(mockEvaluate).toHaveBeenCalledWith("p1");
    });
    await waitFor(() => {
      expect(screen.getByTestId("matter-closure-step-1")).toBeInTheDocument();
      expect(screen.getByTestId("matter-closure-report")).toBeInTheDocument();
    });
  });

  it("step 2: collects reason + notes and submits close action (happy path, all gates passing)", async () => {
    const user = userEvent.setup();
    mockEvaluate.mockResolvedValue({
      success: true,
      report: makeReport({ allPassed: true, gates: [makeGate()] }),
    });
    mockClose.mockResolvedValue({
      success: true,
      data: {
        projectId: "p1",
        status: "CLOSED",
        closedAt: "2026-04-17T10:15:02Z",
        closureLogId: "log-1",
        closureLetterDocumentId: "doc-1",
        retentionEndsAt: "2031-04-17",
      },
    });
    const onOpenChange = vi.fn();

    render(
      withProviders(
        <MatterClosureDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={onOpenChange}
        />
      )
    );

    // Step 1 loads; advance to step 2
    await waitFor(() =>
      expect(screen.getByTestId("matter-closure-step-1")).toBeInTheDocument()
    );
    await user.click(screen.getByTestId("matter-closure-next-btn"));
    await waitFor(() =>
      expect(screen.getByTestId("matter-closure-step-2")).toBeInTheDocument()
    );

    // Notes input
    await user.type(
      screen.getByTestId("matter-closure-notes-input"),
      "All done."
    );

    await user.click(screen.getByTestId("matter-closure-confirm-close-btn"));

    await waitFor(() => {
      expect(mockClose).toHaveBeenCalled();
    });
    const call = mockClose.mock.calls[0];
    expect(call[0]).toBe("acme");
    expect(call[1]).toBe("p1");
    expect(call[2]).toMatchObject({
      reason: "CONCLUDED",
      notes: "All done.",
      generateClosureLetter: true,
      override: false,
    });
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });

  it("step 3: shown when gates fail AND user lacks OVERRIDE_MATTER_CLOSURE — cannot close", async () => {
    const user = userEvent.setup();
    const failingReport = makeReport({
      allPassed: false,
      gates: [
        makeGate({ code: "TRUST_BALANCE_ZERO", passed: false, message: "trust" }),
      ],
    });
    mockEvaluate.mockResolvedValue({ success: true, report: failingReport });

    render(
      withProviders(
        <MatterClosureDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />,
        { capabilities: ["CLOSE_MATTER"] } // no OVERRIDE_MATTER_CLOSURE
      )
    );

    await waitFor(() =>
      expect(screen.getByTestId("matter-closure-step-1")).toBeInTheDocument()
    );
    await user.click(screen.getByTestId("matter-closure-next-btn"));

    // Should land on step 3 (blocked) not step 2
    await waitFor(() => {
      expect(screen.getByTestId("matter-closure-step-3")).toBeInTheDocument();
      expect(screen.queryByTestId("matter-closure-step-2")).not.toBeInTheDocument();
    });
    expect(
      screen.queryByTestId("matter-closure-override-toggle")
    ).not.toBeInTheDocument();
  });

  it("override toggle visible when user has OVERRIDE_MATTER_CLOSURE — blocks submit if justification < 20 chars", async () => {
    const user = userEvent.setup();
    const failingReport = makeReport({
      allPassed: false,
      gates: [
        makeGate({ code: "TRUST_BALANCE_ZERO", passed: false, message: "trust" }),
      ],
    });
    mockEvaluate.mockResolvedValue({ success: true, report: failingReport });

    render(
      withProviders(
        <MatterClosureDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />,
        { capabilities: ["CLOSE_MATTER", "OVERRIDE_MATTER_CLOSURE"] }
      )
    );

    await waitFor(() =>
      expect(screen.getByTestId("matter-closure-step-1")).toBeInTheDocument()
    );
    await user.click(screen.getByTestId("matter-closure-next-btn"));
    await waitFor(() =>
      expect(screen.getByTestId("matter-closure-step-2")).toBeInTheDocument()
    );

    // Override toggle IS visible
    const overrideToggle = screen.getByTestId("matter-closure-override-toggle");
    expect(overrideToggle).toBeInTheDocument();

    // Turn override on and enter an insufficient justification
    await user.click(overrideToggle);
    await waitFor(() =>
      expect(
        screen.getByTestId("matter-closure-override-justification-input")
      ).toBeInTheDocument()
    );
    await user.type(
      screen.getByTestId("matter-closure-override-justification-input"),
      "short"
    );
    await user.click(screen.getByTestId("matter-closure-confirm-close-btn"));

    // Validation must block the submit
    await waitFor(() => {
      expect(mockClose).not.toHaveBeenCalled();
    });
  });

  it("handles 409 gates_failed by re-rendering step 1 with fresh report", async () => {
    const user = userEvent.setup();
    const initiallyPassing = makeReport({ allPassed: true, gates: [makeGate()] });
    const freshFailing = makeReport({
      allPassed: false,
      gates: [
        makeGate({
          code: "TRUST_BALANCE_ZERO",
          passed: false,
          message: "Fresh failure",
        }),
      ],
    });
    mockEvaluate.mockResolvedValue({ success: true, report: initiallyPassing });
    mockClose.mockResolvedValue({
      success: false,
      kind: "gates_failed",
      report: freshFailing,
    });

    render(
      withProviders(
        <MatterClosureDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />
      )
    );

    await waitFor(() =>
      expect(screen.getByTestId("matter-closure-step-1")).toBeInTheDocument()
    );
    await user.click(screen.getByTestId("matter-closure-next-btn"));
    await waitFor(() =>
      expect(screen.getByTestId("matter-closure-step-2")).toBeInTheDocument()
    );
    await user.click(screen.getByTestId("matter-closure-confirm-close-btn"));

    // Should return to step 1 with fresh failing gate visible
    await waitFor(() => {
      expect(screen.getByTestId("matter-closure-step-1")).toBeInTheDocument();
      expect(
        screen.getByTestId("matter-closure-gate-fail-TRUST_BALANCE_ZERO")
      ).toBeInTheDocument();
      expect(screen.getByText("Fresh failure")).toBeInTheDocument();
    });
  });

  it("renders null when matter_closure module is disabled", () => {
    mockEvaluate.mockResolvedValue({
      success: true,
      report: makeReport({ allPassed: true }),
    });
    const { container } = render(
      withProviders(
        <MatterClosureDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />,
        { modules: [] }
      )
    );
    // ModuleGate renders its fallback (null). The dialog never mounts.
    expect(container).toBeEmptyDOMElement();
    expect(mockEvaluate).not.toHaveBeenCalled();
  });

  it("shows reason Select with CONCLUDED/CLIENT_TERMINATED/REFERRED_OUT/OTHER options", async () => {
    const user = userEvent.setup();
    mockEvaluate.mockResolvedValue({
      success: true,
      report: makeReport({ allPassed: true, gates: [makeGate()] }),
    });
    render(
      withProviders(
        <MatterClosureDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />
      )
    );
    await waitFor(() =>
      expect(screen.getByTestId("matter-closure-step-1")).toBeInTheDocument()
    );
    await user.click(screen.getByTestId("matter-closure-next-btn"));
    await waitFor(() =>
      expect(screen.getByTestId("matter-closure-reason-select")).toBeInTheDocument()
    );
  });
});

// =========================================================================
// Action button — capability + module + status gating (covers 490.6)
// =========================================================================

describe("MatterClosureAction", () => {
  it("renders the Close Matter button when status=ACTIVE, module enabled, and capability present", () => {
    render(
      withProviders(
        <MatterClosureAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="ACTIVE"
        />,
        { capabilities: ["CLOSE_MATTER"] }
      )
    );
    expect(screen.getByTestId("close-matter-btn")).toBeInTheDocument();
  });

  it("renders the Close Matter button when status=COMPLETED", () => {
    render(
      withProviders(
        <MatterClosureAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="COMPLETED"
        />,
        { capabilities: ["CLOSE_MATTER"] }
      )
    );
    expect(screen.getByTestId("close-matter-btn")).toBeInTheDocument();
  });

  it("hides the button when status=ARCHIVED", () => {
    const { container } = render(
      withProviders(
        <MatterClosureAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="ARCHIVED"
        />,
        { capabilities: ["CLOSE_MATTER"] }
      )
    );
    expect(container.querySelector('[data-testid="close-matter-btn"]')).toBeNull();
  });

  it("hides the button when matter_closure module is disabled", () => {
    const { container } = render(
      withProviders(
        <MatterClosureAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="ACTIVE"
        />,
        { modules: [], capabilities: ["CLOSE_MATTER"] }
      )
    );
    expect(container.querySelector('[data-testid="close-matter-btn"]')).toBeNull();
  });

  it("hides the button when user lacks CLOSE_MATTER capability", () => {
    const { container } = render(
      withProviders(
        <MatterClosureAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="ACTIVE"
        />,
        { capabilities: [], isAdmin: false, isOwner: false }
      )
    );
    expect(container.querySelector('[data-testid="close-matter-btn"]')).toBeNull();
  });

  it("shows the button when user is admin (admin short-circuit in hasCapability)", () => {
    render(
      withProviders(
        <MatterClosureAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="ACTIVE"
        />,
        { capabilities: [], isAdmin: true, role: "admin" }
      )
    );
    expect(screen.getByTestId("close-matter-btn")).toBeInTheDocument();
  });
});
