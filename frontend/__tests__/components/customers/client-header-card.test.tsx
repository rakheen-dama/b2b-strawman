import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { ClientHeaderCard } from "@/components/customers/client-header-card";
import type { ClientHeaderCardProps } from "@/components/customers/client-header-card";
import type { HeroTile } from "@/components/shared/detail-hero";
import type { TemplateListResponse } from "@/lib/types";

// ---- Mocks ----

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/components/compliance/LifecycleStatusBadge", () => ({
  LifecycleStatusBadge: ({ status }: { status: string }) => <span>{status}</span>,
}));

vi.mock("@/components/customers/kyc-status-badge", () => ({
  KycStatusBadge: ({ summary }: { summary: { state: string } }) => (
    <span data-testid="kyc-badge">{summary.state}</span>
  ),
}));

vi.mock("@/components/customers/XeroContactBadge", () => ({
  XeroContactBadge: () => <span data-testid="xero-badge">Xero</span>,
}));

vi.mock("@/components/customers/client-overflow-menu", () => ({
  ClientOverflowMenu: () => <div data-testid="client-overflow-trigger" />,
}));

// Standalone Generate Document control, as on the matter page (G88-F46)
vi.mock("@/components/templates/GenerateDocumentDropdown", () => ({
  GenerateDocumentDropdown: ({
    entityId,
    entityType,
    customerId,
  }: {
    entityId: string;
    entityType: string;
    customerId?: string;
  }) => (
    <button
      data-testid="generate-document-dropdown"
      data-entity-id={entityId}
      data-entity-type={entityType}
      data-customer-id={customerId}
    >
      Generate Document
    </button>
  ),
}));

vi.mock("@/components/customers/edit-customer-dialog", () => ({
  EditCustomerDialog: () => <div data-testid="edit-customer-dialog" />,
}));

vi.mock("@/components/customers/archive-customer-dialog", () => ({
  ArchiveCustomerDialog: () => <div data-testid="archive-customer-dialog" />,
}));

vi.mock("@/components/customers/anonymize-customer-dialog", () => ({
  AnonymizeCustomerDialog: () => <div data-testid="anonymize-customer-dialog" />,
}));

vi.mock("@/components/customers/data-export-dialog", () => ({
  DataExportDialog: () => <div data-testid="data-export-dialog" />,
}));

// ---- Test Data ----

const mockCustomer = {
  id: "cust-1",
  name: "Acme Corporation",
  email: "info@acme.com",
  phone: "+27 11 123 4567",
  idNumber: null,
  status: "ACTIVE" as const,
  notes: null,
  createdBy: "user-1",
  createdByName: "Alice",
  createdAt: "2026-01-10T10:00:00Z",
  updatedAt: "2026-01-15T10:00:00Z",
  lifecycleStatus: "ACTIVE" as const,
};

const defaultProps: ClientHeaderCardProps = {
  customerId: "cust-1",
  customerName: "Acme Corporation",
  customerStatus: "ACTIVE",
  lifecycleStatus: "ACTIVE",
  email: "info@acme.com",
  phone: "+27 11 123 4567",
  lifecycleStatusChangedAt: "2026-01-15T10:00:00Z",
  linkedProjectCount: 3,
  kycSummary: null,
  xeroConnected: false,
  slug: "test-org",
  isAdmin: true,
  isOwner: true,
  templates: [],
  aiProviderConfigured: false,
  conflictCheckEnabled: false,
  kycConfigured: false,
  kycVerified: false,
  customer: mockCustomer,
};

const mockTemplates: TemplateListResponse[] = [
  { id: "tpl-1", name: "Power of Attorney", format: "TIPTAP" } as TemplateListResponse,
];

// ---- Tests ----

describe("ClientHeaderCard", () => {
  afterEach(() => {
    cleanup();
  });

  // 642B: the DetailHero h1 wraps instead of clamping, so the old `title` tooltip is gone.
  it("renders the customer name as the only h1", () => {
    render(<ClientHeaderCard {...defaultProps} />);
    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toBe(screen.getByTestId("client-name"));
    expect(headings[0]).toHaveTextContent(/^Acme Corporation$/);
  });

  it('renders "Start Onboarding" button for PROSPECT lifecycle', () => {
    render(<ClientHeaderCard {...defaultProps} lifecycleStatus="PROSPECT" />);
    const actionBtn = screen.getByTestId("smart-primary-action");
    expect(actionBtn).toHaveTextContent("Start Onboarding");
  });

  it('renders "Edit" button for ACTIVE lifecycle', () => {
    render(<ClientHeaderCard {...defaultProps} lifecycleStatus="ACTIVE" />);
    const actionBtn = screen.getByTestId("smart-primary-action");
    expect(actionBtn).toHaveTextContent("Edit");
  });

  it("renders no primary action for OFFBOARDED lifecycle", () => {
    render(<ClientHeaderCard {...defaultProps} lifecycleStatus="OFFBOARDED" />);
    expect(screen.queryByTestId("smart-primary-action")).not.toBeInTheDocument();
  });

  it("renders KYC and Xero badges when present", () => {
    render(
      <ClientHeaderCard
        {...defaultProps}
        kycSummary={{ state: "verified", provider: "Onfido", verifiedAt: "2026-01-20T10:00:00Z" }}
        xeroConnected={true}
      />
    );
    expect(screen.getByTestId("kyc-badge")).toBeInTheDocument();
    expect(screen.getByTestId("xero-badge")).toBeInTheDocument();
  });

  it("hides primary action and shows minimal badges for ANONYMIZED state", () => {
    render(
      <ClientHeaderCard
        {...defaultProps}
        lifecycleStatus="ANONYMIZED"
        kycSummary={null}
        xeroConnected={false}
      />
    );
    expect(screen.queryByTestId("smart-primary-action")).not.toBeInTheDocument();
    expect(screen.getByTestId("lifecycle-badge")).toHaveTextContent("ANONYMIZED");
    expect(screen.queryByTestId("kyc-badge")).not.toBeInTheDocument();
    expect(screen.queryByTestId("xero-badge")).not.toBeInTheDocument();
  });

  // Phase 88 628B (G-B1, R-11): the record-status badge only shows when the client is not ACTIVE
  // (or has no lifecycle badge to show) — no more "Active Active" / "Active Prospect".
  it("shows only the lifecycle badge for an ACTIVE client", () => {
    render(<ClientHeaderCard {...defaultProps} lifecycleStatus="PROSPECT" />);
    expect(screen.queryByTestId("customer-status-badge")).not.toBeInTheDocument();
    expect(screen.getByTestId("lifecycle-badge")).toHaveTextContent("PROSPECT");
  });

  it("shows the Archived record badge (and Restore) for an ARCHIVED client", () => {
    render(
      <ClientHeaderCard {...defaultProps} customerStatus="ARCHIVED" lifecycleStatus="ACTIVE" />
    );
    expect(screen.getByTestId("customer-status-badge")).toHaveTextContent("Archived");
    expect(screen.getByTestId("lifecycle-badge")).toBeInTheDocument();
    expect(screen.getByTestId("smart-primary-action")).toHaveTextContent("Restore");
  });

  it("falls back to the Active record badge when there is no lifecycle status", () => {
    render(<ClientHeaderCard {...defaultProps} lifecycleStatus={null} />);
    expect(screen.getByTestId("customer-status-badge")).toHaveTextContent("Active");
    expect(screen.queryByTestId("lifecycle-badge")).not.toBeInTheDocument();
  });

  // Phase 88 G-B9: id_passport_number facts strip under the client name
  it("renders the ID number facts strip as a dl with a visible ID prefix", () => {
    render(
      <ClientHeaderCard
        {...defaultProps}
        headerFields={[
          { slug: "id_passport_number", label: "ID / Passport Number", value: "8501015800087" },
        ]}
      />
    );
    const strip = screen.getByTestId("client-header-fields");
    expect(strip.tagName).toBe("DL");
    const term = within(strip).getByText("ID");
    expect(term.closest("dt")).not.toBeNull();
    expect(term).not.toHaveClass("sr-only");
    expect(within(strip).getByText("8501015800087").tagName).toBe("DD");
  });

  it("renders no facts strip without headerFields", () => {
    render(<ClientHeaderCard {...defaultProps} />);
    expect(screen.queryByTestId("client-header-fields")).not.toBeInTheDocument();
  });

  // G88-F46: the client page mounts the Generate Document control next to the overflow menu
  describe("Generate Document", () => {
    it("renders the Generate Document control for an admin with client templates", () => {
      render(<ClientHeaderCard {...defaultProps} templates={mockTemplates} />);
      const actions = screen.getByTestId("client-header-actions");
      const control = within(actions).getByTestId("generate-document-dropdown");
      expect(control).toHaveAttribute("data-entity-type", "CUSTOMER");
      expect(control).toHaveAttribute("data-entity-id", "cust-1");
      expect(control).toHaveAttribute("data-customer-id", "cust-1");
    });

    it("hides the control for a non-admin", () => {
      render(<ClientHeaderCard {...defaultProps} templates={mockTemplates} isAdmin={false} />);
      expect(screen.queryByTestId("generate-document-dropdown")).not.toBeInTheDocument();
    });

    it("hides the control when there are no client templates", () => {
      render(<ClientHeaderCard {...defaultProps} templates={[]} />);
      expect(screen.queryByTestId("generate-document-dropdown")).not.toBeInTheDocument();
    });

    it("hides the control for an anonymised client", () => {
      render(
        <ClientHeaderCard
          {...defaultProps}
          templates={mockTemplates}
          lifecycleStatus="ANONYMIZED"
        />
      );
      expect(screen.queryByTestId("generate-document-dropdown")).not.toBeInTheDocument();
    });
  });
});

// --- 642B: composed on DetailHero (ADR-373, §15.6.6 selectors) ---

const TILES: HeroTile[] = [
  { id: "openMatters", label: "Open matters", value: "2", state: "ready" },
  { id: "trust", label: "Trust balance", value: "R 50 000,00", hint: "Main Trust", state: "ready" },
  {
    id: "outstanding",
    label: "Outstanding",
    value: "R 3 450,00",
    hint: "1 overdue",
    state: "ready",
  },
  { id: "fica", label: "FICA", value: "Done", hint: "Completed 2 May 2026", state: "ready" },
];

function heroRoot(): HTMLElement {
  return screen.getByTestId("client-header-card");
}

function tilesContainer(): HTMLElement | null {
  return heroRoot().querySelector('[data-slot="detail-hero-tiles"]');
}

describe("ClientHeaderCard on DetailHero (642B)", () => {
  afterEach(() => {
    cleanup();
  });

  it("is the DetailHero root with the client name as the only h1", () => {
    render(<ClientHeaderCard {...defaultProps} />);

    const root = heroRoot();
    expect(root).toHaveAttribute("data-slot", "detail-hero");
    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toBe(screen.getByTestId("client-name"));
    expect(root).toContainElement(headings[0]);
  });

  it("keeps every header test id inside the hero root", () => {
    render(
      <ClientHeaderCard
        {...defaultProps}
        headerFields={[
          { slug: "id_passport_number", label: "ID / Passport Number", value: "8501015800087" },
        ]}
      />
    );

    const root = heroRoot();
    for (const id of [
      "client-name",
      "lifecycle-badge",
      "client-contact",
      "client-context",
      "client-header-fields",
      "client-header-actions",
      "smart-primary-action",
      "client-overflow-trigger",
    ]) {
      expect(within(root).getByTestId(id)).toBeInTheDocument();
    }
    const actions = within(root).getByTestId("client-header-actions");
    expect(within(actions).getByTestId("smart-primary-action")).toHaveTextContent("Edit");
    expect(within(actions).getByTestId("client-overflow-trigger")).toBeInTheDocument();
  });

  it("puts the contact line in the subtitle and the context line after it", () => {
    render(<ClientHeaderCard {...defaultProps} />);

    const subtitle = heroRoot().querySelector('[data-slot="detail-hero-subtitle"]') as HTMLElement;
    expect(subtitle).not.toBeNull();
    expect(within(subtitle).getByTestId("client-contact")).toHaveTextContent(
      "info@acme.com · +27 11 123 4567"
    );
    // 2026-01-15T10:00Z is 15 Jan on the SA calendar (formatDateInSA — hydration-safe).
    expect(screen.getByTestId("client-context")).toHaveTextContent("Since 15 Jan 2026 · 3 matters");
  });

  it("omits the contact line without email and phone; singular matter count", () => {
    render(<ClientHeaderCard {...defaultProps} email={null} phone={null} linkedProjectCount={1} />);

    expect(screen.queryByTestId("client-contact")).not.toBeInTheDocument();
    expect(heroRoot().querySelector('[data-slot="detail-hero-subtitle"]')).toBeNull();
    expect(screen.getByTestId("client-context")).toHaveTextContent(/1 matter$/);
  });

  it("renders no identifier chip (clients have no number)", () => {
    render(<ClientHeaderCard {...defaultProps} />);
    expect(heroRoot().querySelector("code")).toBeNull();
  });

  it("renders no tile strip when no tiles are passed", () => {
    render(<ClientHeaderCard {...defaultProps} />);
    expect(tilesContainer()).toBeNull();
  });

  it("renders four tiles as compact StatCards, in order, without links", () => {
    render(<ClientHeaderCard {...defaultProps} tiles={TILES} />);

    const strip = tilesContainer() as HTMLElement;
    expect(strip).not.toBeNull();
    const groups = within(strip).getAllByRole("group");
    expect(groups).toHaveLength(4);
    expect(groups[0]).toHaveAccessibleName("Open matters");
    expect(groups[1]).toHaveAccessibleName("Trust balance");
    expect(groups[2]).toHaveAccessibleName("Outstanding");
    expect(groups[3]).toHaveAccessibleName("FICA");
    expect(groups[1]).toHaveTextContent("R 50 000,00");
    expect(within(strip).queryByRole("link")).not.toBeInTheDocument();
  });

  it("renders three tiles when Outstanding was omitted by its gate (member)", () => {
    render(
      <ClientHeaderCard {...defaultProps} tiles={TILES.filter((t) => t.id !== "outstanding")} />
    );

    const groups = within(tilesContainer() as HTMLElement).getAllByRole("group");
    expect(groups).toHaveLength(3);
    expect(screen.queryByText("Outstanding")).not.toBeInTheDocument();
  });

  it("renders an error tile as '—' with \"Couldn't load\"", () => {
    render(
      <ClientHeaderCard
        {...defaultProps}
        tiles={[{ id: "fica", label: "FICA", value: null, state: "error" }]}
      />
    );

    const group = within(tilesContainer() as HTMLElement).getByRole("group", { name: "FICA" });
    expect(within(group).getByRole("status")).toHaveTextContent("Couldn't load");
  });

  it("shows the currency-exclusion hint on the Outstanding tile", () => {
    render(
      <ClientHeaderCard
        {...defaultProps}
        tiles={[
          {
            id: "outstanding",
            label: "Outstanding",
            value: "R 8 625,00",
            hint: "Excludes 1 fee note in other currencies",
            state: "ready",
          },
        ]}
      />
    );

    const group = within(tilesContainer() as HTMLElement).getByRole("group", {
      name: "Outstanding",
    });
    expect(group).toHaveTextContent("R 8 625,00");
    expect(within(group).getByText("Excludes 1 fee note in other currencies")).toBeInTheDocument();
  });
});
