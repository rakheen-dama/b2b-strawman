/**
 * SSR snapshot harness for the dialog family — audit-03 fix-spec rec #4.
 *
 * Each test renders a dialog at its closed-by-default state through
 * `react-dom/server`, normalises Radix's auto-generated IDs, and
 * captures the resulting HTML as a vitest snapshot. Any structural
 * change to a dialog's SSR shape — including the mount-gate regression
 * class that PR #1262 fixed for `CreateProposalDialog` — produces a
 * loud diff in PR review.
 *
 * The harness covers four representative dialogs across both shapes:
 * - `EditCustomerDialog` — dialog-owns-button (Dialog, PR #1242).
 * - `DeleteCommentDialog` — dialog-owns-button (AlertDialog, PR #1263).
 * - `LogExpenseDialog` — children-API (Dialog, audit-03 left-as-is).
 * - `CreateProposalDialog` — children-API (Dialog, OBS-704 v3 sentinel).
 *
 * To extend coverage to a new dialog: add a mock for any server-action
 * imports it has, build a fixture with its minimum-required props, and
 * add a `it("renders ... in SSR", ...)` block calling
 * `renderDialogToSsr(...)` + `toMatchSnapshot()`.
 */
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

import { renderDialogToSsr } from "../lib/ssr-snapshot";

// --- Server-action + next/navigation mocks (must be hoisted before the
// component imports below). Each dialog imports its own action module
// server-side; we stub them so the module graph loads in vitest.
vi.mock("@/app/(app)/org/[slug]/customers/actions", () => ({
  updateCustomer: vi.fn(),
}));
vi.mock("@/lib/actions/comments", () => ({
  deleteComment: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/projects/[id]/expense-actions", () => ({
  createExpense: vi.fn(),
  updateExpense: vi.fn(),
  deleteExpense: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/projects/[id]/actions", () => ({
  initiateUpload: vi.fn(),
  confirmUpload: vi.fn(),
  cancelUpload: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/proposals/actions", () => ({
  createProposalAction: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

import { Button } from "@/components/ui/button";
import { EditCustomerDialog } from "@/components/customers/edit-customer-dialog";
import { DeleteCommentDialog } from "@/components/comments/delete-comment-dialog";
import { LogExpenseDialog } from "@/components/expenses/log-expense-dialog";
import { CreateProposalDialog } from "@/components/proposals/create-proposal-dialog";
import type { Customer } from "@/lib/types";

// Minimal customer fixture — the SSR pass renders the trigger only; the
// form (which actually reads these fields) lives inside `DialogContent`
// and is unmounted while the dialog is closed. `buildDefaults` walks
// every field, so each must be a defined string / null-tolerant value.
const STUB_CUSTOMER: Customer = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Test Customer",
  email: "test@example.com",
  customerType: "INDIVIDUAL",
  phone: null,
  idNumber: null,
  notes: null,
  addressLine1: null,
  addressLine2: null,
  city: null,
  stateProvince: null,
  postalCode: null,
  country: null,
  taxNumber: null,
  contactName: null,
  contactEmail: null,
  contactPhone: null,
  registrationNumber: null,
  entityType: null,
  financialYearEnd: null,
} as unknown as Customer;

const PROPOSAL_CUSTOMERS = [
  { id: "22222222-2222-2222-2222-222222222222", name: "Dlamini & Co", email: "dlamini@test.local" },
];

describe("Dialog family — SSR snapshot harness (audit-03 rec #4)", () => {
  afterEach(() => cleanup());

  it("EditCustomerDialog (dialog-owns-button) renders its trigger in SSR", () => {
    const html = renderDialogToSsr(
      <EditCustomerDialog customer={STUB_CUSTOMER} slug="legal-test" triggerLabel="Edit" />
    );

    // Contract: SSR output is non-empty and contains the rendered trigger
    // Button. A mount-gate regression would collapse this to "".
    expect(html).not.toBe("");
    expect(html).toContain("Edit");

    // Structural snapshot — any change to the SSR shape fails the test.
    expect(html).toMatchSnapshot();
  });

  it("DeleteCommentDialog (dialog-owns-button, AlertDialog) renders its trigger in SSR", () => {
    const html = renderDialogToSsr(
      <DeleteCommentDialog
        commentId="c-1"
        orgSlug="legal-test"
        projectId="p-1"
        triggerLabel="Delete"
      />
    );

    expect(html).not.toBe("");
    expect(html).toContain("Delete");
    expect(html).toMatchSnapshot();
  });

  it("LogExpenseDialog (children-API) renders Radix wrapper + trigger child in SSR", () => {
    const html = renderDialogToSsr(
      <LogExpenseDialog slug="legal-test" projectId="p-1" tasks={[]}>
        <Button data-testid="ssr-log-expense-trigger">Log expense</Button>
      </LogExpenseDialog>
    );

    expect(html).not.toBe("");
    // Children-API contract: the consumer-supplied trigger must be in the
    // SSR output, AND Radix's auto-injected `aria-controls` must appear on
    // the trigger element (this is the load-bearing OBS-704 v3 contract —
    // a mount-gate would strip both).
    expect(html).toContain('data-testid="ssr-log-expense-trigger"');
    expect(html).toMatch(/aria-controls="radix-[^"]+"/);
    expect(html).toMatchSnapshot();
  });

  it("CreateProposalDialog (children-API, OBS-704 v3 sentinel) renders Radix wrapper + trigger in SSR", () => {
    const html = renderDialogToSsr(
      <CreateProposalDialog slug="legal-test" customers={PROPOSAL_CUSTOMERS}>
        <Button data-testid="ssr-proposal-trigger">New Proposal</Button>
      </CreateProposalDialog>
    );

    expect(html).not.toBe("");
    expect(html).toContain('data-testid="ssr-proposal-trigger"');
    expect(html).toMatch(/aria-controls="radix-[^"]+"/);
    expect(html).toMatchSnapshot();
  });
});
