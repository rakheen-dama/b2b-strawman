import { describe, it, expect, vi, afterEach } from "vitest";
import type { ReactNode } from "react";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/acme/dashboard"),
}));

import { usePathname } from "next/navigation";
import { Breadcrumbs } from "@/components/breadcrumbs";
import { BreadcrumbLabel, BreadcrumbLabelProvider } from "@/components/breadcrumb-label-provider";
import { RecentItemsProvider } from "@/components/recent-items-provider";
import { RecordRecentItem } from "@/components/record-recent-item";

const mockUsePathname = vi.mocked(usePathname);

afterEach(() => {
  cleanup();
});

describe("Breadcrumbs", () => {
  it("renders Settings as a link segment when on settings subpage", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/documents");
    render(<Breadcrumbs slug="acme" />);
    const settingsLink = screen.getByRole("link", { name: "Settings" });
    expect(settingsLink).toBeInTheDocument();
    expect(settingsLink).toHaveAttribute("href", "/org/acme/settings");
  });

  // Kept settings sub-routes (616E): labelled segments; pageless tab segments stay plain text.
  it("labels /settings/connections/xero/sync-log as Connections > Xero > Sync log (616E)", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/connections/xero/sync-log");
    render(<Breadcrumbs slug="acme" />);
    expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute(
      "href",
      "/org/acme/settings"
    );
    expect(screen.getByRole("link", { name: "Connections" })).toHaveAttribute(
      "href",
      "/org/acme/settings/connections"
    );
    const xero = screen.getByText("Xero");
    expect(xero.tagName).toBe("SPAN");
    expect(xero.closest("a")).toBeNull();
    const last = screen.getByText("Sync log");
    expect(last.tagName).toBe("SPAN");
    expect(last).toHaveClass("font-medium");
  });

  it("labels /settings/matters/checklists/<id>/edit with a linked Matters family (616E)", () => {
    mockUsePathname.mockReturnValue(
      "/org/acme/settings/matters/checklists/2a7b9c1d-3e4f-4a5b-8c6d-7e8f9a0b1c2d/edit"
    );
    render(<Breadcrumbs slug="acme" />);
    expect(screen.getByRole("link", { name: "Matters" })).toHaveAttribute(
      "href",
      "/org/acme/settings/matters"
    );
    expect(screen.getByText("Checklists").closest("a")).toBeNull();
  });

  it("labels /settings/connections/automations/executions as Automations > Executions (616E)", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/connections/automations/executions");
    render(<Breadcrumbs slug="acme" />);
    expect(screen.getByText("Automations").closest("a")).toBeNull();
    const last = screen.getByText("Executions");
    expect(last.tagName).toBe("SPAN");
    expect(last).toHaveClass("font-medium");
  });

  it("labels the Firm settings family Settings > Firm (616A)", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/firm");
    render(<Breadcrumbs slug="acme" />);
    expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute(
      "href",
      "/org/acme/settings"
    );
    const firmSpan = screen.getByText("Firm");
    expect(firmSpan.tagName).toBe("SPAN");
    expect(firmSpan).toHaveClass("font-medium");
  });

  it("labels the Fees & Trust settings family Settings > Fees & Trust (616C)", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/fees");
    render(<Breadcrumbs slug="acme" />);
    expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute(
      "href",
      "/org/acme/settings"
    );
    const span = screen.getByText("Fees & Trust");
    expect(span.tagName).toBe("SPAN");
    expect(span).toHaveClass("font-medium");
  });

  it("labels the Connections settings family Settings > Connections (616D)", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/connections");
    render(<Breadcrumbs slug="acme" />);
    expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute(
      "href",
      "/org/acme/settings"
    );
    const span = screen.getByText("Connections");
    expect(span.tagName).toBe("SPAN");
    expect(span).toHaveClass("font-medium");
  });

  it("renders Settings > Matters two-level path correctly", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/matters");
    render(<Breadcrumbs slug="acme" />);
    // "Settings" should be a link (intermediate)
    const settingsLink = screen.getByRole("link", { name: "Settings" });
    expect(settingsLink).toBeInTheDocument();
    // "Matters" is the last segment — renders as a <span> with font-medium
    const mattersSpan = screen.getByText("Matters");
    expect(mattersSpan.tagName).toBe("SPAN");
    expect(mattersSpan).toHaveClass("font-medium");
  });

  describe("intermediate segments without a page", () => {
    const TEMPLATE_ID = "3f1c2a4b-5d6e-4f70-8a9b-0c1d2e3f4a5b";
    const REQUEST_ID = "9a8b7c6d-5e4f-4a3b-9c2d-1e0f9a8b7c6d";

    function renderAt(pathname: string) {
      mockUsePathname.mockReturnValue(pathname);
      render(<Breadcrumbs slug="acme" />);
    }

    it("renders Legal as plain text, not a link, on /legal/disbursements", () => {
      renderAt("/org/acme/legal/disbursements");
      const crumb = screen.getByText("Legal");
      expect(crumb.tagName).toBe("SPAN");
      expect(crumb.closest("a")).toBeNull();
      expect(screen.queryByRole("link", { name: "Legal" })).not.toBeInTheDocument();
    });

    it("renders AI as plain text, not a link, on /ai/reviews", () => {
      renderAt("/org/acme/ai/reviews");
      expect(screen.getByText("AI").closest("a")).toBeNull();
      expect(screen.queryByRole("link", { name: "AI" })).not.toBeInTheDocument();
    });

    it("renders Information requests as plain text on /information-requests/<id>", () => {
      renderAt(`/org/acme/information-requests/${REQUEST_ID}`);
      expect(screen.getByText("Information requests").closest("a")).toBeNull();
    });

    // Phase 87 S6a (612A): nav-zone pages get labels.
    it("links Reports and labels Profitability on /reports/profitability", () => {
      renderAt("/org/acme/reports/profitability");
      expect(screen.getByRole("link", { name: "Reports" })).toHaveAttribute(
        "href",
        "/org/acme/reports"
      );
      expect(screen.getByText("Profitability").tagName).toBe("SPAN");
    });

    // Phase 87 S6b (613D): the Diary replaces both calendars.
    it("labels the Diary on /diary", () => {
      renderAt("/org/acme/diary");
      expect(screen.getByText("Diary").tagName).toBe("SPAN");
    });

    it("renders Legal as plain text and Tariffs last on /legal/tariffs", () => {
      renderAt("/org/acme/legal/tariffs");
      expect(screen.getByText("Legal").tagName).toBe("SPAN");
      expect(screen.queryByRole("link", { name: "Legal" })).not.toBeInTheDocument();
      const last = screen.getByText("Tariffs");
      expect(last.tagName).toBe("SPAN");
      expect(last).toHaveClass("font-medium");
    });

    // 628B (R-4): a [param] crumb takes its parent's fallback ("Template"), never the raw id.
    it("renders the template crumb as plain text on /settings/documents/templates/<id>/edit", () => {
      renderAt(`/org/acme/settings/documents/templates/${TEMPLATE_ID}/edit`);
      expect(screen.queryByText(TEMPLATE_ID)).not.toBeInTheDocument();
      expect(screen.getByText("Template").closest("a")).toBeNull();
      expect(screen.getByText("Edit")).toHaveClass("font-medium");
      // Routable ancestors stay linked; the pageless templates segment does not.
      expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute(
        "href",
        "/org/acme/settings"
      );
      expect(screen.getByRole("link", { name: "Documents" })).toHaveAttribute(
        "href",
        "/org/acme/settings/documents"
      );
      expect(screen.getByText("Templates").closest("a")).toBeNull();
    });

    it("renders AI as plain text on /settings/connections/ai/history (616D)", () => {
      renderAt("/org/acme/settings/connections/ai/history");
      expect(screen.getByRole("link", { name: "Connections" })).toHaveAttribute(
        "href",
        "/org/acme/settings/connections"
      );
      expect(screen.getByText("AI").closest("a")).toBeNull();
      expect(screen.queryByRole("link", { name: "AI" })).not.toBeInTheDocument();
    });

    it("still links intermediate segments that have a page", () => {
      renderAt("/org/acme/settings/fees");
      expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute(
        "href",
        "/org/acme/settings"
      );
    });
  });
  describe("matter and client routes (Phase 87 610E)", () => {
    const MATTER_ID = "5b1d7c2e-3f4a-4b5c-8d6e-7f8091a2b3c4";
    const CLIENT_ID = "6c2e8d3f-4a5b-4c6d-9e7f-8091a2b3c4d5";

    function renderCrumbsAt(pathname: string) {
      mockUsePathname.mockReturnValue(pathname);
      render(<Breadcrumbs slug="acme" />);
    }

    it("labels /matters/<id> as Matters > Matter and links the list", () => {
      renderCrumbsAt(`/org/acme/matters/${MATTER_ID}`);
      expect(screen.getByRole("link", { name: "Matters" })).toHaveAttribute(
        "href",
        "/org/acme/matters"
      );
      expect(screen.getByText("Matter").tagName).toBe("SPAN");
      expect(screen.queryByText(MATTER_ID)).not.toBeInTheDocument();
    });

    it("labels /clients/<id> as Clients > Client and links the list", () => {
      renderCrumbsAt(`/org/acme/clients/${CLIENT_ID}`);
      expect(screen.getByRole("link", { name: "Clients" })).toHaveAttribute(
        "href",
        "/org/acme/clients"
      );
      expect(screen.getByText("Client").tagName).toBe("SPAN");
    });

    it("labels the /matters list page Matters (not the raw segment)", () => {
      renderCrumbsAt("/org/acme/matters");
      expect(screen.getByText("Matters").tagName).toBe("SPAN");
      expect(screen.queryByText("matters")).not.toBeInTheDocument();
    });

    // 628B (R-4): sentence case.
    it("labels /trust-accounting/client-ledgers/<id> as Client ledger", () => {
      renderCrumbsAt(`/org/acme/trust-accounting/client-ledgers/${CLIENT_ID}`);
      expect(screen.getByRole("link", { name: "Trust accounting" })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "Client ledgers" })).toBeInTheDocument();
      expect(screen.getByText("Client ledger").tagName).toBe("SPAN");
    });
  });

  describe("legal labels (Phase 87 611B)", () => {
    const INVOICE_ID = "7d3f9e4a-5b6c-4d7e-8f90-a1b2c3d4e5f6";

    function renderCrumbsAt(pathname: string) {
      mockUsePathname.mockReturnValue(pathname);
      render(<Breadcrumbs slug="acme" />);
    }

    it("labels /settings/matters/templates/<id> as Settings > Matters > Templates (616E)", () => {
      renderCrumbsAt("/org/acme/settings/matters/templates/8e4fa05b-6c7d-4e8f-9a01-b2c3d4e5f607");
      expect(screen.getByRole("link", { name: "Matters" })).toHaveAttribute(
        "href",
        "/org/acme/settings/matters"
      );
      expect(screen.getByText("Templates").closest("a")).toBeNull();
      expect(screen.queryByText(/Project/)).not.toBeInTheDocument();
    });

    it("labels the /invoices list page Fee notes", () => {
      renderCrumbsAt("/org/acme/invoices");
      expect(screen.getByText("Fee notes").tagName).toBe("SPAN");
      expect(screen.queryByText("Invoices")).not.toBeInTheDocument();
    });

    it("labels /invoices/<id> as Fee notes > Fee note and links the list", () => {
      renderCrumbsAt(`/org/acme/invoices/${INVOICE_ID}`);
      expect(screen.getByRole("link", { name: "Fee notes" })).toHaveAttribute(
        "href",
        "/org/acme/invoices"
      );
      expect(screen.getByText("Fee note").tagName).toBe("SPAN");
    });
  });
  // Phase 88 628B (628.4, R-4): every org/[slug]/** segment has a label and every [param] crumb
  // falls back to its parent's singular — no raw slugs or ids.
  describe("labels for every segment (628B)", () => {
    const ID = "8f4a0b5c-6d7e-4f80-9a1b-2c3d4e5f6a7b";

    function lastCrumb(pathname: string) {
      mockUsePathname.mockReturnValue(pathname);
      render(<Breadcrumbs slug="acme" orgName="Mathebula & Partners" />);
      const crumbs = screen
        .getByRole("navigation", { name: "Breadcrumb" })
        .querySelectorAll(".font-medium");
      return crumbs[crumbs.length - 1]?.textContent;
    }

    it.each([
      ["/org/acme/dashboard", "Home"],
      ["/org/acme/my-work", "My work"],
      ["/org/acme/my-work/timesheet", "Timesheet"],
      ["/org/acme/matters/new", "New"],
      ["/org/acme/conflict-check", "Conflict check"],
      ["/org/acme/ai/reviews", "AI reviews"],
      ["/org/acme/compliance", "FICA & compliance"],
      ["/org/acme/compliance/requests", "Data requests"],
      [`/org/acme/compliance/requests/${ID}`, "Data request"],
      [`/org/acme/information-requests/${ID}`, "Information request"],
      ["/org/acme/invoices/billing-runs", "Fee runs"],
      ["/org/acme/invoices/billing-runs/new", "New"],
      [`/org/acme/invoices/billing-runs/${ID}`, "Fee run"],
      ["/org/acme/invoices/collections", "Debtors"],
      [`/org/acme/legal/disbursements/${ID}`, "Disbursement"],
      ["/org/acme/reports/invoice-aging", "Report"],
      ["/org/acme/trust-accounting", "Trust accounting"],
      ["/org/acme/trust-accounting/transactions", "Transactions"],
      ["/org/acme/trust-accounting/reconciliation", "Reconciliation"],
      ["/org/acme/trust-accounting/reconciliation/new", "New"],
      ["/org/acme/trust-accounting/interest", "Interest"],
      ["/org/acme/trust-accounting/investments", "Investments"],
      [`/org/acme/settings/matters/checklists/${ID}`, "Checklist template"],
      [`/org/acme/settings/matters/request-templates/${ID}`, "Request template"],
      [`/org/acme/settings/matters/templates/${ID}`, "Template"],
    ])("%s ends in %s", (pathname, label) => {
      expect(lastCrumb(pathname)).toBe(label);
    });

    it("never renders the raw report slug", () => {
      lastCrumb("/org/acme/reports/project-profitability");
      expect(screen.queryByText("project-profitability")).not.toBeInTheDocument();
      expect(screen.getByRole("link", { name: "Reports" })).toHaveAttribute(
        "href",
        "/org/acme/reports"
      );
    });

    it("labels Fee notes › Fee runs with the list linked", () => {
      lastCrumb("/org/acme/invoices/billing-runs");
      expect(screen.getByRole("link", { name: "Fee notes" })).toHaveAttribute(
        "href",
        "/org/acme/invoices"
      );
      expect(screen.queryByText("billing-runs")).not.toBeInTheDocument();
    });
  });

  // Phase 88 628B (628.4, R-2/R-3): the last crumb reads the entity name the page publishes.
  describe("entity name in the last crumb (628B)", () => {
    const MATTER_ID = "5b1d7c2e-3f4a-4b5c-8d6e-7f8091a2b3c4";
    const CLIENT_ID = "6c2e8d3f-4a5b-4c6d-9e7f-8091a2b3c4d5";
    const INVOICE_ID = "7d3f9e4a-5b6c-4d7e-8f90-a1b2c3d4e5f6";

    function renderWithLabels(pathname: string, publisher: ReactNode) {
      mockUsePathname.mockReturnValue(pathname);
      render(
        <RecentItemsProvider>
          <BreadcrumbLabelProvider>
            <Breadcrumbs slug="acme" />
            {publisher}
          </BreadcrumbLabelProvider>
        </RecentItemsProvider>
      );
    }

    it("reads the matter name published by RecordRecentItem", () => {
      renderWithLabels(
        `/org/acme/matters/${MATTER_ID}`,
        <RecordRecentItem href={`/org/acme/matters/${MATTER_ID}`} label="Dlamini v RAF" />
      );
      expect(screen.getByRole("link", { name: "Matters" })).toHaveAttribute(
        "href",
        "/org/acme/matters"
      );
      const last = screen.getByText("Dlamini v RAF");
      expect(last.tagName).toBe("SPAN");
      expect(last).toHaveClass("font-medium");
      expect(screen.queryByText("Matter")).not.toBeInTheDocument();
    });

    it("reads the client name published by RecordRecentItem", () => {
      renderWithLabels(
        `/org/acme/clients/${CLIENT_ID}`,
        <RecordRecentItem href={`/org/acme/clients/${CLIENT_ID}`} label="Sipho Dlamini" />
      );
      expect(screen.getByText("Sipho Dlamini")).toHaveClass("font-medium");
      expect(screen.queryByText("Client")).not.toBeInTheDocument();
    });

    it("reads the fee-note number published by BreadcrumbLabel", () => {
      renderWithLabels(
        `/org/acme/invoices/${INVOICE_ID}`,
        <BreadcrumbLabel href={`/org/acme/invoices/${INVOICE_ID}`} label="INV-0002" />
      );
      expect(screen.getByText("INV-0002")).toHaveClass("font-medium");
      expect(screen.queryByText("Fee note")).not.toBeInTheDocument();
    });

    it("ignores a label published for another href", () => {
      renderWithLabels(
        `/org/acme/matters/${MATTER_ID}`,
        <RecordRecentItem href={`/org/acme/clients/${CLIENT_ID}`} label="Sipho Dlamini" />
      );
      expect(screen.getByText("Matter")).toHaveClass("font-medium");
      expect(screen.queryByText("Sipho Dlamini")).not.toBeInTheDocument();
    });

    it("truncates a long last crumb and keeps the full name in its title", () => {
      const longName =
        "Dlamini v Road Accident Fund and Others — High Court Gauteng Division Pretoria (appeal)";
      renderWithLabels(
        `/org/acme/matters/${MATTER_ID}`,
        <BreadcrumbLabel href={`/org/acme/matters/${MATTER_ID}`} label={longName} />
      );
      const last = screen.getByText(longName);
      expect(last).toHaveClass("truncate");
      expect(last).toHaveAttribute("title", longName);
      expect(screen.getByRole("navigation", { name: "Breadcrumb" })).toHaveClass("min-w-0");
    });

    it("falls back to the generic label once the publisher unmounts", () => {
      mockUsePathname.mockReturnValue(`/org/acme/matters/${MATTER_ID}`);
      const tree = (withPublisher: boolean) => (
        <BreadcrumbLabelProvider>
          <Breadcrumbs slug="acme" />
          {withPublisher && (
            <BreadcrumbLabel href={`/org/acme/matters/${MATTER_ID}`} label="Dlamini v RAF" />
          )}
        </BreadcrumbLabelProvider>
      );
      const { rerender } = render(tree(true));
      expect(screen.getByText("Dlamini v RAF")).toBeInTheDocument();
      rerender(tree(false));
      expect(screen.queryByText("Dlamini v RAF")).not.toBeInTheDocument();
      expect(screen.getByText("Matter")).toHaveClass("font-medium");
    });
  });
});
