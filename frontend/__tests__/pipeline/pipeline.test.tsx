import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";

vi.mock("server-only", () => ({}));

const mockRouterRefresh = vi.fn();
const mockRouterPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockRouterPush, refresh: mockRouterRefresh }),
  useSearchParams: () => new URLSearchParams(""),
}));

const mockIntakeDealAction = vi.fn();
const mockTransitionDealAction = vi.fn();
const mockCreateDealAction = vi.fn();
vi.mock("@/app/(app)/org/[slug]/pipeline/actions", () => ({
  intakeDealAction: (...args: unknown[]) => mockIntakeDealAction(...args),
  transitionDealAction: (...args: unknown[]) => mockTransitionDealAction(...args),
  createDealAction: (...args: unknown[]) => mockCreateDealAction(...args),
}));

import { DealCardView } from "@/components/pipeline/DealCard";
import { PipelineBoard } from "@/components/pipeline/PipelineBoard";
import { PipelineListView } from "@/components/pipeline/PipelineListView";
import { IntakeDialog } from "@/components/pipeline/IntakeDialog";
import { WinLoseDialog } from "@/components/pipeline/WinLoseDialog";
import type { DealResponse, StageDto } from "@/lib/api/crm";

afterEach(() => {
  cleanup();
  mockIntakeDealAction.mockReset();
  mockTransitionDealAction.mockReset();
  mockCreateDealAction.mockReset();
  mockRouterRefresh.mockReset();
});

const stages: StageDto[] = [
  {
    id: "s1",
    name: "Lead",
    position: 0,
    defaultProbabilityPct: 20,
    stageType: "OPEN",
    archived: false,
  },
  {
    id: "s2",
    name: "Qualified",
    position: 1,
    defaultProbabilityPct: 50,
    stageType: "OPEN",
    archived: false,
  },
  {
    id: "swon",
    name: "Won",
    position: 2,
    defaultProbabilityPct: 100,
    stageType: "WON",
    archived: false,
  },
  {
    id: "slost",
    name: "Lost",
    position: 3,
    defaultProbabilityPct: 0,
    stageType: "LOST",
    archived: false,
  },
];

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

const customerNames = { c1: "Acme Corp" };
const ownerNames = { o1: "Alice" };

describe("DealCardView", () => {
  it("renders customer, title, value and probability", () => {
    render(<DealCardView deal={makeDeal()} customerName="Acme Corp" ownerName="Alice" />);
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Acme website")).toBeInTheDocument();
    expect(screen.getByText(/20%/)).toBeInTheDocument();
    expect(screen.getByText("Alice")).toBeInTheDocument();
  });
});

describe("PipelineBoard", () => {
  it("renders ordered OPEN columns plus WON/LOST", () => {
    render(
      <PipelineBoard
        slug="acme"
        stages={stages}
        deals={[makeDeal()]}
        customerNames={customerNames}
        ownerNames={ownerNames}
        canManage
      />
    );
    expect(screen.getByText("Lead")).toBeInTheDocument();
    expect(screen.getByText("Qualified")).toBeInTheDocument();
    expect(screen.getByText("Won")).toBeInTheDocument();
    expect(screen.getByText("Lost")).toBeInTheDocument();
    // The deal card appears in its stage column.
    expect(screen.getByText("Acme website")).toBeInTheDocument();
  });
});

describe("PipelineListView", () => {
  it("renders deals in a table", () => {
    render(
      <PipelineListView
        deals={[makeDeal()]}
        customerNames={customerNames}
        ownerNames={ownerNames}
      />
    );
    expect(screen.getByText("Acme website")).toBeInTheDocument();
    expect(screen.getByText("DEAL-001")).toBeInTheDocument();
    expect(screen.getByText("OPEN")).toBeInTheDocument();
  });

  it("shows empty state when no deals", () => {
    render(<PipelineListView deals={[]} customerNames={{}} ownerNames={{}} />);
    expect(screen.getByText(/No deals match/i)).toBeInTheDocument();
  });
});

describe("IntakeDialog", () => {
  it("submits intake with an existing customer", async () => {
    mockIntakeDealAction.mockResolvedValue({ success: true });
    render(
      <IntakeDialog slug="acme" customers={[{ id: "c1", name: "Acme Corp" }]} stages={stages} />
    );

    fireEvent.click(screen.getByRole("button", { name: /New Enquiry/i }));
    // Default mode is "existing" — pick the customer + a title.
    fireEvent.change(screen.getByText("Choose a customer…").closest("select")!, {
      target: { value: "c1" },
    });
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "New deal" } });
    fireEvent.click(screen.getByRole("button", { name: /Create Enquiry/i }));

    await waitFor(() => {
      expect(mockIntakeDealAction).toHaveBeenCalledTimes(1);
    });
    const [, req] = mockIntakeDealAction.mock.calls[0];
    expect(req.customerId).toBe("c1");
    expect(req.customer).toBeUndefined();
    expect(req.title).toBe("New deal");
  });

  it("requires a title", async () => {
    render(
      <IntakeDialog slug="acme" customers={[{ id: "c1", name: "Acme Corp" }]} stages={stages} />
    );
    fireEvent.click(screen.getByRole("button", { name: /New Enquiry/i }));
    fireEvent.click(screen.getByRole("button", { name: /Create Enquiry/i }));
    await waitFor(() => {
      expect(screen.getByText("Title is required")).toBeInTheDocument();
    });
    expect(mockIntakeDealAction).not.toHaveBeenCalled();
  });
});

describe("WinLoseDialog", () => {
  it("requires a reason when marking as lost", async () => {
    render(
      <WinLoseDialog
        slug="acme"
        deal={makeDeal()}
        targetStage={stages[3]}
        open
        onOpenChange={() => {}}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: /Mark as Lost/i }));
    await waitFor(() => {
      expect(screen.getByText(/A reason is required/i)).toBeInTheDocument();
    });
    expect(mockTransitionDealAction).not.toHaveBeenCalled();
  });

  it("transitions to lost with a reason", async () => {
    mockTransitionDealAction.mockResolvedValue({ success: true });
    render(
      <WinLoseDialog
        slug="acme"
        deal={makeDeal()}
        targetStage={stages[3]}
        open
        onOpenChange={() => {}}
      />
    );
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Budget cut" } });
    fireEvent.click(screen.getByRole("button", { name: /Mark as Lost/i }));
    await waitFor(() => {
      expect(mockTransitionDealAction).toHaveBeenCalledWith("acme", "d1", {
        targetStageId: "slost",
        lostReason: "Budget cut",
      });
    });
  });

  it("surfaces a backend 400 and keeps the dialog open", async () => {
    mockTransitionDealAction.mockResolvedValue({
      success: false,
      status: 400,
      error: "A reason is required to mark a deal as lost.",
    });
    render(
      <WinLoseDialog
        slug="acme"
        deal={makeDeal()}
        targetStage={stages[3]}
        open
        onOpenChange={() => {}}
      />
    );
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "x" } });
    fireEvent.click(screen.getByRole("button", { name: /Mark as Lost/i }));
    await waitFor(() => {
      expect(screen.getByText("A reason is required to mark a deal as lost.")).toBeInTheDocument();
    });
  });

  it("transitions to won without a reason", async () => {
    mockTransitionDealAction.mockResolvedValue({ success: true });
    render(
      <WinLoseDialog
        slug="acme"
        deal={makeDeal()}
        targetStage={stages[2]}
        open
        onOpenChange={() => {}}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: /Mark as Won/i }));
    await waitFor(() => {
      expect(mockTransitionDealAction).toHaveBeenCalledWith("acme", "d1", {
        targetStageId: "swon",
        lostReason: undefined,
      });
    });
  });
});
