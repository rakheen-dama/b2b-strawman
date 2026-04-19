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

const mockList = vi.fn();
const mockGenerate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/statement-actions", () => ({
  listStatementsAction: (...args: unknown[]) => mockList(...args),
  generateStatementAction: (...args: unknown[]) => mockGenerate(...args),
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

import { ProjectStatementsTab } from "@/components/legal/project-statements-tab";
import { OrgProfileProvider } from "@/lib/org-profile";
import { CapabilityProvider } from "@/lib/capabilities";
import type { StatementResponse } from "@/lib/api/statement-of-account";

afterEach(() => cleanup());
beforeEach(() => {
  vi.clearAllMocks();
});

function makeStatement(overrides: Partial<StatementResponse> = {}): StatementResponse {
  return {
    id: "s-1",
    templateId: "t-1",
    generatedAt: "2026-04-17T10:30:00Z",
    htmlPreview: null,
    pdfUrl: "/api/documents/s-1/pdf",
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

describe("ProjectStatementsTab", () => {
  it("renders a table row for each returned statement", async () => {
    const statements = [
      makeStatement({ id: "s-1" }),
      makeStatement({ id: "s-2", generatedAt: "2026-03-15T09:00:00Z" }),
    ];
    mockList.mockResolvedValue({
      success: true,
      data: {
        content: statements,
        page: { totalElements: 2, totalPages: 1, size: 20, number: 0 },
      },
    });

    render(
      withProviders(
        <ProjectStatementsTab projectId="p1" slug="acme" />
      )
    );

    await waitFor(() => {
      expect(screen.getByTestId("project-statements-tab")).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.getByTestId("statement-row-s-1")).toBeInTheDocument();
      expect(screen.getByTestId("statement-row-s-2")).toBeInTheDocument();
    });
  });

  it("shows the empty state when there are no statements", async () => {
    mockList.mockResolvedValue({
      success: true,
      data: {
        content: [],
        page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
      },
    });

    render(
      withProviders(
        <ProjectStatementsTab projectId="p1" slug="acme" />
      )
    );

    await waitFor(() => {
      expect(screen.getByTestId("statements-empty")).toBeInTheDocument();
    });
  });

  it("triggers downloadGeneratedDocumentAction when a row is clicked", async () => {
    const user = userEvent.setup();
    mockList.mockResolvedValue({
      success: true,
      data: {
        content: [makeStatement({ id: "s-1" })],
        page: { totalElements: 1, totalPages: 1, size: 20, number: 0 },
      },
    });
    // Empty base64 is fine; we just care the action is invoked.
    mockDownload.mockResolvedValue({
      success: true,
      pdfBase64: "",
      fileName: "statement-s-1.pdf",
    });

    // jsdom/happy-dom needs these for the blob download flow not to throw.
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    URL.createObjectURL = vi.fn(() => "blob:fake");
    URL.revokeObjectURL = vi.fn();

    try {
      render(
        withProviders(
          <ProjectStatementsTab projectId="p1" slug="acme" />
        )
      );

      const row = await screen.findByTestId("statement-row-s-1");
      await user.click(row);

      await waitFor(() => {
        expect(mockDownload).toHaveBeenCalledWith("s-1");
      });
    } finally {
      URL.createObjectURL = originalCreateObjectURL;
      URL.revokeObjectURL = originalRevokeObjectURL;
    }
  });
});
