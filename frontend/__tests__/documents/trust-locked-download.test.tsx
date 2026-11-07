import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";

// G89-F52 / D19: a document that prints trust money stays listed, but its Download is disabled
// (tooltip "Trust details are not available") for a member without VIEW_TRUST. The server's 403
// is the real guard; this only spares the member a failing click.

vi.mock("server-only", () => ({}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/org/acme/matters/p1",
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock("@/app/(app)/org/[slug]/matters/[id]/actions", () => ({
  initiateUpload: vi.fn().mockResolvedValue({ success: true }),
  confirmUpload: vi.fn().mockResolvedValue({ success: true }),
  cancelUpload: vi.fn().mockResolvedValue({ success: true }),
  getDownloadUrl: vi.fn().mockResolvedValue({ success: true, presignedUrl: "https://x.test" }),
}));

vi.mock("@/app/(app)/org/[slug]/clients/[id]/actions", () => ({
  initiateCustomerUpload: vi.fn().mockResolvedValue({ success: true }),
  confirmCustomerUpload: vi.fn().mockResolvedValue({ success: true }),
  cancelCustomerUpload: vi.fn().mockResolvedValue({ success: true }),
  toggleDocumentVisibility: vi.fn().mockResolvedValue({ success: true }),
}));

vi.mock("@/lib/actions/comments", () => ({
  fetchComments: vi.fn().mockResolvedValue([]),
  createComment: vi.fn(),
  updateComment: vi.fn(),
  deleteComment: vi.fn(),
}));

const mockFetchGenerated = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/documents/_tabs/template-generation-actions", () => ({
  fetchGeneratedDocumentsAction: (...args: unknown[]) => mockFetchGenerated(...args),
  deleteGeneratedDocumentAction: vi.fn(),
  downloadGeneratedDocumentAction: vi.fn(),
  downloadDocxGeneratedDocumentAction: vi.fn(),
  shareGeneratedDocumentAction: vi.fn(),
}));

vi.mock("@/lib/actions/acceptance-actions", () => ({
  getAcceptanceRequests: vi.fn().mockResolvedValue({ success: true, data: [] }),
}));

const mockListStatements = vi.fn();
vi.mock("@/app/(app)/org/[slug]/matters/[id]/statement-actions", () => ({
  listStatementsAction: (...args: unknown[]) => mockListStatements(...args),
  generateStatementAction: vi.fn(),
}));

import { DocumentsPanel } from "@/components/documents/documents-panel";
import { CustomerDocumentsPanel } from "@/components/documents/customer-documents-panel";
import { GeneratedDocumentsList } from "@/components/templates/GeneratedDocumentsList";
import { ProjectStatementsTab } from "@/components/legal/project-statements-tab";
import { CapabilityProvider } from "@/lib/capabilities";
import { OrgProfileProvider } from "@/lib/org-profile";
import type { Document } from "@/lib/types";

const LOCKED = "Trust details are not available";

function makeDocument(overrides: Partial<Document> = {}): Document {
  return {
    id: "d1",
    matterId: "p1",
    fileName: "statement-of-account.pdf",
    contentType: "application/pdf",
    size: 2048,
    status: "UPLOADED",
    scope: "PROJECT",
    clientId: null,
    visibility: "INTERNAL",
    source: "MANUAL",
    uploadedBy: "m1",
    uploadedByName: "Alice Owner",
    uploadedAt: "2026-10-28T10:00:00Z",
    createdAt: "2026-10-28T10:00:00Z",
    ...overrides,
  } as Document;
}

function withCaps(ui: React.ReactElement, capabilities: string[]) {
  return (
    <SWRConfig value={{ provider: () => new Map() }}>
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["disbursements", "trust_accounting"]}
        terminologyNamespace={null}
      >
        <CapabilityProvider
          capabilities={capabilities}
          role="member"
          isAdmin={false}
          isOwner={false}
        >
          {ui}
        </CapabilityProvider>
      </OrgProfileProvider>
    </SWRConfig>
  );
}

async function expectLockedWithTooltip(button: HTMLElement) {
  expect(button).toBeDisabled();
  await userEvent.setup().hover(button.parentElement!);
  expect((await screen.findAllByText(LOCKED)).length).toBeGreaterThan(0);
}

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

describe("trust-locked downloads (G89-F52)", () => {
  describe("matter Documents panel", () => {
    it("disables Download with a tooltip for a trust document when the member lacks VIEW_TRUST", async () => {
      render(
        withCaps(
          <DocumentsPanel
            documents={[makeDocument({ containsTrustData: true })]}
            projectId="p1"
            slug="acme"
          />,
          ["VIEW_LEGAL"]
        )
      );
      await expectLockedWithTooltip(
        screen.getByRole("button", { name: /Download statement-of-account\.pdf/ })
      );
    });

    it("keeps Download enabled for a VIEW_TRUST member, and for a document without trust data", () => {
      render(
        withCaps(
          <DocumentsPanel
            documents={[
              makeDocument({ id: "d1", containsTrustData: true }),
              makeDocument({ id: "d2", fileName: "power-of-attorney.pdf" }),
            ]}
            projectId="p1"
            slug="acme"
          />,
          ["VIEW_LEGAL", "VIEW_TRUST"]
        )
      );
      expect(
        screen.getByRole("button", { name: /Download statement-of-account\.pdf/ })
      ).toBeEnabled();
      expect(screen.getByRole("button", { name: /Download power-of-attorney\.pdf/ })).toBeEnabled();
    });
  });

  describe("client Documents panel", () => {
    it("locks only the trust document for a member without VIEW_TRUST", async () => {
      render(
        withCaps(
          <CustomerDocumentsPanel
            documents={[
              makeDocument({
                id: "c1",
                scope: "CUSTOMER",
                matterId: null,
                clientId: "cl1",
                fileName: "client-trust-statement.pdf",
                containsTrustData: true,
              }),
              makeDocument({
                id: "c2",
                scope: "CUSTOMER",
                matterId: null,
                clientId: "cl1",
                fileName: "power-of-attorney.pdf",
              }),
            ]}
            slug="acme"
            customerId="cl1"
            canManage={false}
          />,
          ["VIEW_LEGAL"]
        )
      );
      expect(screen.getByRole("button", { name: /Download power-of-attorney\.pdf/ })).toBeEnabled();
      await expectLockedWithTooltip(
        screen.getByRole("button", { name: /Download client-trust-statement\.pdf/ })
      );
    });
  });

  describe("generated documents list", () => {
    const rows = [
      {
        id: "gen-soa",
        templateName: "Statement of Account",
        fileName: "statement-of-account.pdf",
        fileSize: 2048,
        generatedByName: "Alice Owner",
        generatedAt: "2026-10-28T10:00:00Z",
        documentId: "doc-soa",
        containsTrustData: true,
      },
    ];

    it("disables Download PDF with a tooltip for a trust row when the member lacks VIEW_TRUST", async () => {
      mockFetchGenerated.mockResolvedValue({ success: true, data: rows });
      render(
        withCaps(<GeneratedDocumentsList entityType="PROJECT" entityId="p1" slug="acme" />, [
          "VIEW_LEGAL",
        ])
      );
      const button = await screen.findByRole("button", { name: "Download PDF" });
      await expectLockedWithTooltip(button);
    });

    it("keeps Download PDF enabled for a VIEW_TRUST member", async () => {
      mockFetchGenerated.mockResolvedValue({ success: true, data: rows });
      render(
        withCaps(<GeneratedDocumentsList entityType="PROJECT" entityId="p1" slug="acme" />, [
          "VIEW_LEGAL",
          "VIEW_TRUST",
        ])
      );
      expect(await screen.findByRole("button", { name: "Download PDF" })).toBeEnabled();
    });
  });

  describe("matter Statements tab", () => {
    function statement(containsTrustData: boolean) {
      return {
        id: "s-1",
        templateId: "t-1",
        generatedAt: "2026-04-17T10:30:00Z",
        htmlPreview: null,
        pdfUrl: "/api/generated-documents/s-1/download",
        matter: { matterId: "p1", name: "Smith v Jones" },
        periodStart: null,
        periodEnd: null,
        containsTrustData,
        summary: {
          totalFees: 0,
          totalDisbursements: 0,
          previousBalanceOwing: 0,
          paymentsReceived: 0,
          closingBalanceOwing: 0,
          trustBalanceHeld: null,
          trustWithheld: true,
        },
      };
    }

    function listOf(containsTrustData: boolean) {
      return {
        success: true,
        data: {
          content: [statement(containsTrustData)],
          page: { totalElements: 1, totalPages: 1, size: 20, number: 0 },
        },
      };
    }

    it("disables Download with a tooltip for a trust statement when the member lacks VIEW_TRUST", async () => {
      mockListStatements.mockResolvedValue(listOf(true));
      render(withCaps(<ProjectStatementsTab projectId="p1" slug="acme" />, ["VIEW_LEGAL"]));
      const button = await screen.findByTestId("statement-download-s-1");
      await expectLockedWithTooltip(button);
    });

    it("keeps Download enabled for a statement without trust data", async () => {
      mockListStatements.mockResolvedValue(listOf(false));
      render(withCaps(<ProjectStatementsTab projectId="p1" slug="acme" />, ["VIEW_LEGAL"]));
      await waitFor(() => expect(screen.getByTestId("statement-download-s-1")).toBeEnabled());
    });
  });
});
