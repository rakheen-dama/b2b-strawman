import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderToString } from "react-dom/server";

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
import { Button } from "@b2mash/ui/button";

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

describe("CreateProposalDialog — expiresAt timezone encoding (OBS-702)", () => {
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

  it("submits expiresAt as the LOCAL end-of-day of the user's picked date (does not cross the date line in zones east of UTC)", async () => {
    const user = userEvent.setup();

    render(
      <CreateProposalDialog
        slug="legal-test"
        customers={[CUSTOMERS[0]]}
        defaultCustomerId={CUSTOMERS[0].id}
        defaultFeeModel="HOURLY"
      >
        <Button data-testid="open-obs702-proposal">New Engagement Letter</Button>
      </CreateProposalDialog>
    );

    await user.click(screen.getByTestId("open-obs702-proposal"));
    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    // Fill required title.
    const titleInput = screen.getByLabelText(/Title/i);
    await user.type(titleInput, "Engagement letter");

    // Fill the optional expiry date with a known calendar date.
    const expiryInput = screen.getByLabelText(/Expiry Date/i);
    await user.type(expiryInput, "2026-05-12");

    // Submit.
    await user.click(screen.getByRole("button", { name: /Create Proposal/i }));

    await waitFor(() => {
      expect(mockCreateProposal).toHaveBeenCalled();
    });

    const [, payload] = mockCreateProposal.mock.calls[0] as [string, { expiresAt?: string }];

    expect(payload.expiresAt).toBeDefined();

    // The submitted instant must decode back to the user's picked calendar
    // date (May 12) when read in the SAME local zone the user picked it in.
    // This is the round-trip the proposal detail page relies on. Crucially,
    // the bug was the `T23:59:59Z` form which crossed the date line in any
    // zone east of UTC.
    const decoded = new Date(payload.expiresAt!);
    expect(decoded.getFullYear()).toBe(2026);
    expect(decoded.getMonth()).toBe(4); // May (0-indexed)
    expect(decoded.getDate()).toBe(12);

    // Belt-and-braces: the ISO string must NOT be the literal `T23:59:59Z`
    // forced-UTC form that produced OBS-702.
    expect(payload.expiresAt).not.toBe("2026-05-12T23:59:59Z");
  });
});

describe("CreateProposalDialog — SSR hydration contract (LZKC-002, supersedes OBS-704 v3)", () => {
  afterEach(() => {
    cleanup();
  });

  it("SSRs the bare trigger with no useId-derived radix ids (mount-gate)", () => {
    // LZKC-002 regression guard — this inverts the OBS-704 v3 contract that
    // previously lived here. OBS-704 v3 asserted Radix's aria-controls IS in
    // the SSR HTML, on the premise that React 19's useId() is SSR-stable.
    // That premise only holds for hydration-stable subtrees: useId values are
    // position-derived, and the org app-shell above /proposals is NOT
    // hydration-stable (next/dynamic ssr:false command palette, auth-gated
    // `return null` header controls). Under that shell the SSR-stamped
    // aria-controls id drifted on every fresh load — a guaranteed hydration
    // mismatch (qa_cycle/fix-specs/LZKC-002.md).
    //
    // The mount-gate contract: SSR (and the first client commit) emit the
    // bare consumer trigger — present (no blank-button flash; a `return null`
    // gate would strip it) and id-free (nothing for the client to
    // contradict). Radix wires the trigger with client-generated ids after
    // mount. The divergent-shell hydration guard lives in
    // __tests__/components/dialog-family-ssr.snapshot.test.tsx (LZKC-002).
    const ssrHtml = renderToString(
      <CreateProposalDialog slug="legal-test" customers={CUSTOMERS}>
        <Button data-testid="ssr-trigger">New Proposal</Button>
      </CreateProposalDialog>
    );

    // The trigger child must be in the SSR output…
    expect(ssrHtml).toContain('data-testid="ssr-trigger"');

    // …and no Radix useId-derived attribute may be stamped into it.
    expect(ssrHtml).not.toMatch(/aria-controls/);
    expect(ssrHtml).not.toMatch(/radix-/);
  });
});
