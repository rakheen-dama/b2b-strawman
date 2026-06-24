import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

const mockRouterRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRouterRefresh, push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(""),
}));

// Deal-detail proposal actions (server actions) — stub so the client component renders.
vi.mock("@/app/(app)/org/[slug]/pipeline/[id]/actions", () => ({
  createDealProposalAction: vi.fn(),
  linkDealProposalAction: vi.fn(),
}));

// IntakeDialog (used by CustomerDealsTab) reaches the pipeline intake action.
vi.mock("@/app/(app)/org/[slug]/pipeline/actions", () => ({
  intakeDealAction: vi.fn(),
}));

import { DealOverview } from "@/components/pipeline/DealOverview";
import { DealProposalsPanel } from "@/components/pipeline/DealProposalsPanel";
import { CustomerDealsTab } from "@/components/pipeline/CustomerDealsTab";
import type { DealResponse, LinkedProposalDto, StageDto } from "@/lib/api/crm";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function makeDeal(over: Partial<DealResponse> = {}): DealResponse {
  return {
    id: "d1",
    dealNumber: "DEAL-001",
    customerId: "c1",
    title: "Acme website",
    stageId: "s1",
    stageName: "Lead",
    status: "OPEN",
    valueAmount: 10000,
    valueCurrency: "ZAR",
    probabilityPct: null,
    effectiveProbabilityPct: 20,
    weightedValue: 2000,
    expectedCloseDate: "2026-07-01",
    ownerId: "o1",
    source: "Referral",
    wonAt: null,
    lostAt: null,
    lostReason: null,
    customFields: null,
    createdBy: "o1",
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-01T00:00:00Z",
    ...over,
  };
}

const stages: StageDto[] = [
  {
    id: "s1",
    name: "Lead",
    position: 0,
    defaultProbabilityPct: 20,
    stageType: "OPEN",
    archived: false,
  },
];

describe("DealOverview", () => {
  it("renders the overview fields and the customer link", () => {
    render(
      <DealOverview deal={makeDeal()} slug="acme" customerName="Acme Corp" ownerName="Alice" />
    );
    // Stage, probability, owner, source
    expect(screen.getByText("Lead")).toBeInTheDocument();
    expect(screen.getByText("20%")).toBeInTheDocument();
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Referral")).toBeInTheDocument();
    // Customer link points to the customer detail page
    const link = screen.getByRole("link", { name: /Acme Corp/i });
    expect(link).toHaveAttribute("href", "/org/acme/customers/c1");
  });

  it("shows the lost reason when the deal is lost", () => {
    render(
      <DealOverview
        deal={makeDeal({
          status: "LOST",
          lostAt: "2026-06-10T00:00:00Z",
          lostReason: "Budget cut",
        })}
        slug="acme"
        customerName="Acme Corp"
        ownerName={null}
      />
    );
    expect(screen.getByText("Budget cut")).toBeInTheDocument();
    expect(screen.getByTestId("deal-status-badge")).toHaveTextContent("Lost");
    expect(screen.getByText("Unassigned")).toBeInTheDocument();
  });
});

describe("DealProposalsPanel", () => {
  const proposals: LinkedProposalDto[] = [
    { id: "p1", proposalNumber: "PROP-001", status: "DRAFT", amount: 5000 },
    { id: "p2", proposalNumber: "PROP-002", status: "ACCEPTED", amount: null },
  ];

  it("renders proposals with the correct status chips", () => {
    render(
      <DealProposalsPanel slug="acme" dealId="d1" proposals={proposals} currency="ZAR" canManage />
    );
    expect(screen.getByText("PROP-001")).toBeInTheDocument();
    expect(screen.getByText("PROP-002")).toBeInTheDocument();
    // Status chip labels (mapped from STATUS_BADGE)
    expect(screen.getByText("Draft")).toBeInTheDocument();
    expect(screen.getByText("Accepted")).toBeInTheDocument();
  });

  it("shows an empty state when there are no proposals", () => {
    render(<DealProposalsPanel slug="acme" dealId="d1" proposals={[]} currency="ZAR" canManage />);
    expect(screen.getByText(/No proposals yet/i)).toBeInTheDocument();
  });
});

describe("CustomerDealsTab", () => {
  it("lists the customer's deals with status chips", () => {
    render(
      <CustomerDealsTab
        slug="acme"
        customerId="c1"
        customerName="Acme Corp"
        deals={[
          makeDeal(),
          makeDeal({ id: "d2", dealNumber: "DEAL-002", title: "Retainer", status: "WON" }),
        ]}
        stages={stages}
        canManage
      />
    );
    expect(screen.getByText("Acme website")).toBeInTheDocument();
    expect(screen.getByText("Retainer")).toBeInTheDocument();
    expect(screen.getByText("DEAL-001")).toBeInTheDocument();
    expect(screen.getByText("Won")).toBeInTheDocument();
    // Each deal links to its detail page
    const link = screen.getByRole("link", { name: "Acme website" });
    expect(link).toHaveAttribute("href", "/org/acme/pipeline/d1");
  });

  it("shows an empty state when the customer has no deals", () => {
    render(
      <CustomerDealsTab
        slug="acme"
        customerId="c1"
        customerName="Acme Corp"
        deals={[]}
        stages={stages}
        canManage={false}
      />
    );
    expect(screen.getByText(/No deals yet/i)).toBeInTheDocument();
  });
});
