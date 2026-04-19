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

const { toastSuccess, toastError, mockReopen } = vi.hoisted(() => ({
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
  mockReopen: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: { success: toastSuccess, error: toastError },
}));

vi.mock(
  "@/app/(app)/org/[slug]/projects/[id]/matter-reopen-actions",
  () => ({
    reopenMatterAction: (...args: unknown[]) => mockReopen(...args),
  })
);

// --- Imports after mocks ---

import { MatterReopenDialog } from "@/components/legal/matter-reopen-dialog";
import { MatterReopenAction } from "@/components/projects/matter-reopen-action";
import { OrgProfileProvider } from "@/lib/org-profile";
import { CapabilityProvider } from "@/lib/capabilities";

afterEach(() => cleanup());
beforeEach(() => {
  vi.clearAllMocks();
});

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
// Action button — capability + module + status gating
// =========================================================================

describe("MatterReopenAction", () => {
  it("renders the Reopen Matter button when status=CLOSED, module enabled, and capability present", () => {
    render(
      withProviders(
        <MatterReopenAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="CLOSED"
        />,
        { capabilities: ["CLOSE_MATTER"] }
      )
    );
    expect(screen.getByTestId("reopen-matter-btn")).toBeInTheDocument();
  });

  it("hides the button when status=ACTIVE", () => {
    const { container } = render(
      withProviders(
        <MatterReopenAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="ACTIVE"
        />,
        { capabilities: ["CLOSE_MATTER"] }
      )
    );
    expect(
      container.querySelector('[data-testid="reopen-matter-btn"]')
    ).toBeNull();
  });

  it("hides the button when status=ARCHIVED", () => {
    const { container } = render(
      withProviders(
        <MatterReopenAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="ARCHIVED"
        />,
        { capabilities: ["CLOSE_MATTER"] }
      )
    );
    expect(
      container.querySelector('[data-testid="reopen-matter-btn"]')
    ).toBeNull();
  });

  it("hides the button when matter_closure module is disabled", () => {
    const { container } = render(
      withProviders(
        <MatterReopenAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="CLOSED"
        />,
        { modules: [], capabilities: ["CLOSE_MATTER"] }
      )
    );
    expect(
      container.querySelector('[data-testid="reopen-matter-btn"]')
    ).toBeNull();
  });

  it("hides the button when user lacks CLOSE_MATTER capability", () => {
    const { container } = render(
      withProviders(
        <MatterReopenAction
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          projectStatus="CLOSED"
        />,
        { capabilities: [], isAdmin: false, isOwner: false }
      )
    );
    expect(
      container.querySelector('[data-testid="reopen-matter-btn"]')
    ).toBeNull();
  });
});

// =========================================================================
// Dialog — validation + error surfaces
// =========================================================================

describe("MatterReopenDialog", () => {
  it("blocks submission when notes are shorter than 10 characters", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();

    render(
      withProviders(
        <MatterReopenDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open
          onOpenChange={onOpenChange}
        />
      )
    );

    const notes = await screen.findByTestId("matter-reopen-notes-input");
    await user.type(notes, "too short");

    const submit = screen.getByTestId("matter-reopen-confirm-btn");
    await user.click(submit);

    await waitFor(() => {
      expect(
        screen.getByText(/at least 10 characters/i)
      ).toBeInTheDocument();
    });

    // Server action should never have been called
    expect(mockReopen).not.toHaveBeenCalled();
    // Dialog should remain open
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });

  it("calls reopenMatterAction with trimmed notes and closes on success", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    mockReopen.mockResolvedValue({
      success: true,
      data: {
        projectId: "p1",
        status: "ACTIVE",
        reopenedAt: "2026-04-19T10:00:00Z",
        closureLogId: "log-1",
      },
    });

    render(
      withProviders(
        <MatterReopenDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open
          onOpenChange={onOpenChange}
        />
      )
    );

    const notes = await screen.findByTestId("matter-reopen-notes-input");
    await user.type(notes, "  Reopening to add late discovery materials  ");

    await user.click(screen.getByTestId("matter-reopen-confirm-btn"));

    await waitFor(() => {
      expect(mockReopen).toHaveBeenCalledWith(
        "acme",
        "p1",
        "Reopening to add late discovery materials"
      );
    });

    await waitFor(() => {
      expect(toastSuccess).toHaveBeenCalledWith("Matter reopened");
    });
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("surfaces a friendly retention-elapsed message when the server returns kind=retention_elapsed", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    mockReopen.mockResolvedValue({
      success: false,
      kind: "retention_elapsed",
      retentionEndedOn: "2031-04-17",
      error:
        "Matter retention window expired on 2031-04-17. This matter can no longer be reopened.",
    });

    render(
      withProviders(
        <MatterReopenDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open
          onOpenChange={onOpenChange}
        />
      )
    );

    const notes = await screen.findByTestId("matter-reopen-notes-input");
    await user.type(notes, "Reopening with sufficient context here");

    await user.click(screen.getByTestId("matter-reopen-confirm-btn"));

    await waitFor(() => {
      expect(mockReopen).toHaveBeenCalled();
    });

    // Inline error visible
    await waitFor(() => {
      const inline = screen.getByTestId("matter-reopen-error");
      expect(inline.textContent).toMatch(/retention window expired/i);
      expect(inline.textContent).toContain("2031-04-17");
    });

    // Toast error fired with the friendly message
    expect(toastError).toHaveBeenCalledWith(
      expect.stringMatching(/retention window expired/i)
    );

    // Dialog stays open so user can read the message
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });

  it("surfaces a forbidden error when the server returns kind=forbidden", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    mockReopen.mockResolvedValue({
      success: false,
      kind: "forbidden",
      error: "You do not have permission to reopen matters.",
    });

    render(
      withProviders(
        <MatterReopenDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open
          onOpenChange={onOpenChange}
        />
      )
    );

    const notes = await screen.findByTestId("matter-reopen-notes-input");
    await user.type(notes, "Sufficient reopen notes provided");

    await user.click(screen.getByTestId("matter-reopen-confirm-btn"));

    await waitFor(() => {
      expect(mockReopen).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(screen.getByTestId("matter-reopen-error").textContent).toMatch(
        /do not have permission/i
      );
    });
    expect(toastError).toHaveBeenCalled();
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });
});
