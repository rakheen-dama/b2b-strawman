import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";

// --- Mocks (must come before component imports) ---

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

const mockGenerate = vi.fn();
const mockList = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/statement-actions", () => ({
  generateStatementAction: (...args: unknown[]) => mockGenerate(...args),
  listStatementsAction: (...args: unknown[]) => mockList(...args),
  getStatementAction: vi.fn(),
}));

const mockDownload = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/templates/template-generation-actions",
  () => ({
    downloadGeneratedDocumentAction: (...args: unknown[]) =>
      mockDownload(...args),
    downloadDocxGeneratedDocumentAction: vi.fn(),
    fetchGeneratedDocumentsAction: vi.fn(),
    deleteGeneratedDocumentAction: vi.fn(),
  })
);

// --- Imports after mocks ---

import { StatementOfAccountDialog } from "@/components/legal/statement-of-account-dialog";
import { OrgProfileProvider } from "@/lib/org-profile";
import { CapabilityProvider } from "@/lib/capabilities";
import { toast } from "sonner";
import type { StatementResponse } from "@/lib/api/statement-of-account";

afterEach(() => cleanup());
beforeEach(() => {
  vi.clearAllMocks();
  // Default: no prior statements
  mockList.mockResolvedValue({
    success: true,
    data: { content: [], page: { totalElements: 0, totalPages: 0, size: 20, number: 0 } },
  });
});

function makeStatement(overrides: Partial<StatementResponse> = {}): StatementResponse {
  return {
    id: "s1",
    templateId: "t1",
    generatedAt: "2026-04-17T10:30:00Z",
    htmlPreview: "<html><body><h1>Statement</h1></body></html>",
    pdfUrl: "/api/documents/s1/pdf",
    matter: { projectId: "p1", name: "Smith v Jones" },
    summary: {
      totalFees: 42000,
      totalDisbursements: 3200,
      previousBalanceOwing: 0,
      paymentsReceived: 15000,
      closingBalanceOwing: 30200,
      trustBalanceHeld: 10000,
    },
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
    modules = ["disbursements"],
    capabilities = ["GENERATE_STATEMENT_OF_ACCOUNT"],
    isAdmin = false,
    isOwner = false,
    role = "member",
  } = opts;
  return (
    <SWRConfig value={{ provider: () => new Map() }}>
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
    </SWRConfig>
  );
}

describe("StatementOfAccountDialog", () => {
  it("uses first-of-current-month as periodStart default when no prior statements exist", async () => {
    render(
      withProviders(
        <StatementOfAccountDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />
      )
    );

    await waitFor(() => {
      expect(screen.getByTestId("statement-of-account-dialog")).toBeInTheDocument();
    });

    const startInput = screen.getByTestId(
      "statement-period-start-input"
    ) as HTMLInputElement;
    const endInput = screen.getByTestId(
      "statement-period-end-input"
    ) as HTMLInputElement;
    // Default periodStart = first of current month; periodEnd = today
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, "0");
    const dd = String(now.getDate()).padStart(2, "0");
    expect(startInput.value).toBe(`${yyyy}-${mm}-01`);
    expect(endInput.value).toBe(`${yyyy}-${mm}-${dd}`);
  });

  it("renders htmlPreview in an iframe after successful generation and fires toast.success", async () => {
    const user = userEvent.setup();
    const statement = makeStatement();
    mockGenerate.mockResolvedValue({ success: true, data: statement });

    render(
      withProviders(
        <StatementOfAccountDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />
      )
    );

    await waitFor(() =>
      expect(screen.getByTestId("statement-of-account-dialog")).toBeInTheDocument()
    );

    await user.click(screen.getByTestId("statement-generate-btn"));

    await waitFor(() => {
      expect(mockGenerate).toHaveBeenCalled();
    });
    expect(mockGenerate.mock.calls[0][0]).toBe("acme");
    expect(mockGenerate.mock.calls[0][1]).toBe("p1");

    // Preview iframe present (A4PreviewWrapper renders iframe with title="Document Preview")
    await waitFor(() => {
      expect(screen.getByTitle("Document Preview")).toBeInTheDocument();
    });
    // Download button appears after successful generation
    expect(screen.getByTestId("statement-download-btn")).toBeInTheDocument();
    expect(vi.mocked(toast.success)).toHaveBeenCalledWith("Statement generated");
  });

  it("blocks submit when periodEnd is before periodStart (schema validation)", async () => {
    const user = userEvent.setup();

    render(
      withProviders(
        <StatementOfAccountDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />
      )
    );

    await waitFor(() =>
      expect(screen.getByTestId("statement-of-account-dialog")).toBeInTheDocument()
    );

    const startInput = screen.getByTestId("statement-period-start-input");
    const endInput = screen.getByTestId("statement-period-end-input");

    // periodStart > periodEnd
    await user.clear(startInput);
    await user.type(startInput, "2026-05-10");
    await user.clear(endInput);
    await user.type(endInput, "2026-05-01");

    await user.click(screen.getByTestId("statement-generate-btn"));

    // schema validation should block the submit
    await waitFor(() => {
      expect(mockGenerate).not.toHaveBeenCalled();
    });
    expect(
      screen.getByText("Period end must be on or after period start")
    ).toBeInTheDocument();
  });

  it("surfaces backend error via inline alert and toast.error", async () => {
    const user = userEvent.setup();
    mockGenerate.mockResolvedValue({
      success: false,
      kind: "error",
      error: "Template not found",
    });

    render(
      withProviders(
        <StatementOfAccountDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />
      )
    );

    await waitFor(() =>
      expect(screen.getByTestId("statement-of-account-dialog")).toBeInTheDocument()
    );

    await user.click(screen.getByTestId("statement-generate-btn"));

    await waitFor(() => {
      expect(mockGenerate).toHaveBeenCalled();
    });
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent("Template not found")
    );
    expect(vi.mocked(toast.error)).toHaveBeenCalledWith("Template not found");
  });

  it("renders null when disbursements module is disabled", () => {
    const { container } = render(
      withProviders(
        <StatementOfAccountDialog
          slug="acme"
          projectId="p1"
          projectName="Smith v Jones"
          open={true}
          onOpenChange={vi.fn()}
        />,
        { modules: [] }
      )
    );
    expect(container).toBeEmptyDOMElement();
    expect(mockGenerate).not.toHaveBeenCalled();
    expect(mockList).not.toHaveBeenCalled();
  });
});
