import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock server actions BEFORE importing the component — actions.ts is server-only.
const mockCreateProposal = vi.fn();

vi.mock("@/app/(app)/org/[slug]/proposals/actions", () => ({
  createProposalAction: (...args: unknown[]) => mockCreateProposal(...args),
}));

// next/navigation is imported by the dialog for useRouter — stub it.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

import { CreateProposalDialog } from "@/components/proposals/create-proposal-dialog";
import { Button } from "@/components/ui/button";

const CUSTOMERS = [
  { id: "11111111-1111-1111-1111-111111111111", name: "Dlamini & Co", email: "dlamini@test.local" },
  { id: "22222222-2222-2222-2222-222222222222", name: "Moroka Trust", email: "moroka@test.local" },
];

describe("CreateProposalDialog — matter-level CTA (GAP-L-48)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreateProposal.mockResolvedValue({
      success: true,
      data: { id: "proposal-1" },
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders a trigger that opens the dialog pre-seeded with HOURLY fee model and the matter's customer", async () => {
    const user = userEvent.setup();

    render(
      <CreateProposalDialog
        slug="legal-test"
        customers={[CUSTOMERS[0]]}
        defaultCustomerId={CUSTOMERS[0].id}
        defaultFeeModel="HOURLY"
      >
        <Button data-testid="open-matter-proposal">New Engagement Letter</Button>
      </CreateProposalDialog>
    );

    // Trigger exists and is rendered by the parent.
    const trigger = screen.getByTestId("open-matter-proposal");
    expect(trigger).toBeInTheDocument();

    await user.click(trigger);

    // Dialog opens with the matter's customer pre-selected (shown as the
    // combobox label), the combobox is disabled to prevent re-selection,
    // and the HOURLY-specific field ("Hourly Rate Note") is visible.
    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    const customerTrigger = screen.getByTestId("proposal-customer-trigger");
    expect(customerTrigger).toBeDisabled();
    // The selected customer name is rendered inside the combobox trigger.
    expect(within(customerTrigger).getByText("Dlamini & Co")).toBeInTheDocument();

    // HOURLY is pre-selected — its hourly-rate-note field must be visible
    // while the RETAINER / FIXED / CONTINGENCY fields must NOT be.
    expect(screen.getByText(/Hourly Rate Note/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Retainer Amount/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Fixed Fee Amount/i)).not.toBeInTheDocument();
  });

  it("keeps the existing org-level behaviour — RETAINER default + open combobox — when no defaults are passed", async () => {
    const user = userEvent.setup();

    render(
      <CreateProposalDialog slug="legal-test" customers={CUSTOMERS}>
        <Button data-testid="open-org-proposal">New Proposal</Button>
      </CreateProposalDialog>
    );

    await user.click(screen.getByTestId("open-org-proposal"));

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    const customerTrigger = screen.getByTestId("proposal-customer-trigger");
    // Org-level CTA: combobox is enabled so user can pick any customer.
    expect(customerTrigger).not.toBeDisabled();
    // RETAINER default: retainer-specific fields visible, hourly note not.
    expect(screen.getByLabelText(/Retainer Amount/i)).toBeInTheDocument();
  });
});
