import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Document } from "@/lib/types";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: vi.fn() }),
  usePathname: () => "/org/test-org/settings/general",
}));

// Mock motion/react to avoid animation issues in tests
vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.ComponentProps<"div">) => <div {...props}>{children}</div>,
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock server-only
vi.mock("server-only", () => ({}));

// Mock OrgDocumentUpload to avoid deep dependency chain
vi.mock("@/components/documents/org-document-upload", () => ({
  OrgDocumentUpload: (_props: { slug: string }) => (
    <button data-testid="org-upload-btn">Upload Document</button>
  ),
}));

// Mock SidebarUserFooter (depends on auth provider)
vi.mock("@/components/sidebar-user-footer", () => ({
  SidebarUserFooter: () => <div data-testid="sidebar-user-footer" />,
}));

// Mock command-palette-provider
vi.mock("@/components/command-palette-provider", () => ({
  useCommandPalette: vi.fn(() => ({ open: false, setOpen: vi.fn() })),
  CommandPaletteProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

import { OrgDocumentsSection } from "@/components/settings/org-documents-section";
import { CapabilityProvider } from "@/lib/capabilities";
import { TerminologyProvider } from "@/lib/terminology";
import { OrgProfileProvider } from "@/lib/org-profile";
import { DesktopSidebar } from "@/components/desktop-sidebar";

afterEach(() => {
  cleanup();
});

const MOCK_DOCUMENTS: Document[] = [
  {
    id: "doc-1",
    projectId: null,
    fileName: "contract.pdf",
    contentType: "application/pdf",
    size: 1024000,
    status: "UPLOADED",
    scope: "ORG",
    customerId: null,
    visibility: "INTERNAL",
    uploadedBy: "user-1",
    uploadedByName: "Alice",
    uploadedAt: "2026-03-01T10:00:00Z",
    createdAt: "2026-03-01T10:00:00Z",
  },
  {
    id: "doc-2",
    projectId: null,
    fileName: "logo.png",
    contentType: "image/png",
    size: 512000,
    status: "PENDING",
    scope: "ORG",
    customerId: null,
    visibility: "INTERNAL",
    uploadedBy: "user-2",
    uploadedByName: "Bob",
    uploadedAt: null,
    createdAt: "2026-03-02T10:00:00Z",
  },
];

describe("OrgDocumentsSection", () => {
  it("renders document list with correct columns", () => {
    render(<OrgDocumentsSection slug="test-org" documents={MOCK_DOCUMENTS} isAdmin={true} />);

    expect(screen.getByText("File")).toBeInTheDocument();
    expect(screen.getByText("Size")).toBeInTheDocument();
    expect(screen.getByText("Scope")).toBeInTheDocument();
    expect(screen.getByText("Status")).toBeInTheDocument();
    // "Uploaded" appears as both a column header and a badge label
    expect(screen.getAllByText("Uploaded").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("contract.pdf")).toBeInTheDocument();
    expect(screen.getByText("logo.png")).toBeInTheDocument();
  });

  it("renders empty state when no documents", () => {
    render(<OrgDocumentsSection slug="test-org" documents={[]} isAdmin={false} />);

    expect(screen.getByText("No organization documents yet")).toBeInTheDocument();
    expect(
      screen.getByText("No organization documents have been uploaded yet.")
    ).toBeInTheDocument();
  });

  it("renders upload button for admin users", () => {
    render(<OrgDocumentsSection slug="test-org" documents={MOCK_DOCUMENTS} isAdmin={true} />);

    expect(screen.getByTestId("org-upload-btn")).toBeInTheDocument();
  });

  it("hides upload button for non-admin users", () => {
    render(<OrgDocumentsSection slug="test-org" documents={MOCK_DOCUMENTS} isAdmin={false} />);

    expect(screen.queryByTestId("org-upload-btn")).not.toBeInTheDocument();
  });

  it("renders document count badge", () => {
    render(<OrgDocumentsSection slug="test-org" documents={MOCK_DOCUMENTS} isAdmin={true} />);

    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("has data-testid on root element", () => {
    render(<OrgDocumentsSection slug="test-org" documents={[]} isAdmin={false} />);

    expect(screen.getByTestId("org-documents-section")).toBeInTheDocument();
  });
});

describe("Sidebar nav label changes", () => {
  function renderSidebar() {
    return render(
      <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
        <TerminologyProvider verticalProfile={null}>
          <CapabilityProvider capabilities={[]} role="Admin" isAdmin={true} isOwner={false}>
            <DesktopSidebar slug="test-org" />
          </CapabilityProvider>
        </TerminologyProvider>
      </OrgProfileProvider>
    );
  }

  it('renders "Projects" group label instead of "Delivery"', () => {
    renderSidebar();

    // "Projects" appears as both a group label and a nav item, so at least 2
    const projectsElements = screen.getAllByText("Projects");
    expect(projectsElements.length).toBeGreaterThanOrEqual(2);
    // "Delivery" group should not exist
    expect(screen.queryByText("Delivery")).not.toBeInTheDocument();
  });

  it('renders "Team" group label instead of "Team & Resources"', () => {
    renderSidebar();

    // "Team" appears as both a group label and a nav item
    const teamElements = screen.getAllByText("Team");
    expect(teamElements.length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText("Team & Resources")).not.toBeInTheDocument();
  });

  it("does not render Documents item in sidebar", () => {
    renderSidebar();

    expect(screen.queryByRole("link", { name: "Documents" })).not.toBeInTheDocument();
  });

  it("renders Proposals in Clients group", async () => {
    const user = userEvent.setup();
    renderSidebar();

    // Expand Clients group (defaultExpanded: false)
    const clientsHeader = screen.getByText("Clients");
    await user.click(clientsHeader.closest("button")!);

    expect(screen.getByText("Proposals")).toBeInTheDocument();
  });
});
