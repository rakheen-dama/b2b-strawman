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
 * - `CreateProposalDialog` — children-API (Dialog, LZKC-002 mount-gate
 *   sentinel — see the LZKC-002 describe block below).
 *
 * To extend coverage to a new dialog: add a mock for any server-action
 * imports it has, build a fixture with its minimum-required props, and
 * add a `it("renders ... in SSR", ...)` block calling
 * `renderDialogToSsr(...)` + `toMatchSnapshot()`.
 *
 * LZKC-002 addendum: isolated `renderToString` snapshots are structurally
 * blind to useId drift — in isolation the server and client trees trivially
 * match, so OBS-704 v3's "React 19 useId is SSR-stable" premise holds and
 * the aria-controls contract passes. In the real app the org shell above
 * the page is NOT hydration-stable (next/dynamic ssr:false command palette,
 * auth-gated header controls that `return null` until loaded), which shifts
 * useId values between the Fizz pass and client hydration and made every
 * SSR-stamped radix id on /proposals a guaranteed mismatch. The LZKC-002
 * block below renders the dialog inside a representative divergent shell so
 * this class is actually caught.
 */
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup } from "@testing-library/react";
import { renderToString } from "react-dom/server";
import { hydrateRoot, type Root } from "react-dom/client";
import { act } from "react";

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

import { Button } from "@b2mash/ui/button";
import { EditCustomerDialog } from "@/components/customers/edit-customer-dialog";
import { DeleteCommentDialog } from "@/components/comments/delete-comment-dialog";
import { LogExpenseDialog } from "@/components/expenses/log-expense-dialog";
import { CreateProposalDialog } from "@/components/proposals/create-proposal-dialog";
import type { Customer } from "@/lib/types";

// Minimal customer fixture — the SSR pass renders the trigger only; the
// form (which actually reads these fields) lives inside `DialogContent`
// and is unmounted while the dialog is closed. `buildDefaults` walks
// every field, so each must be a defined string / null-tolerant value.
// Strictly typed against `Customer` (no double-cast) so shape drift in
// the type breaks this fixture loud at compile time.
const STUB_CUSTOMER: Customer = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Test Customer",
  email: "test@example.com",
  status: "ACTIVE",
  createdBy: "test-user",
  createdByName: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
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
};

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

  it("CreateProposalDialog (children-API, LZKC-002 mount-gate sentinel) SSRs the bare trigger, id-free", () => {
    const html = renderDialogToSsr(
      <CreateProposalDialog slug="legal-test" customers={PROPOSAL_CUSTOMERS}>
        <Button data-testid="ssr-proposal-trigger">New Proposal</Button>
      </CreateProposalDialog>
    );

    // LZKC-002 contract (inverts the OBS-704 v3 sentinel): the trigger must
    // still be in the SSR output (no blank-button flash — a `return null`
    // gate would collapse this to ""), but Radix must NOT stamp any
    // useId-derived attribute into it. The org shell above /proposals is not
    // hydration-stable, so any SSR-stamped radix id is a guaranteed
    // hydration mismatch on fresh loads.
    expect(html).not.toBe("");
    expect(html).toContain('data-testid="ssr-proposal-trigger"');
    expect(html).not.toMatch(/aria-controls/);
    expect(html).not.toMatch(/radix-/);
    expect(html).toMatchSnapshot();
  });
});

/**
 * LZKC-002 — hydration stability under a client-divergent app shell.
 *
 * `AuthGatedControls` mirrors `frontend/components/auth-header-controls.tsx`
 * (`if (!isLoaded) return null`): the server renders `null`, the client's
 * first render materialises the subtree. That single structural divergence
 * above the page shifts React's position-derived `useId` values for
 * everything after it — verified empirically: hydrating a probe under this
 * shell yields `_r_0_` on the client where SSR emitted `_R_2_`, exactly the
 * `aria-controls="radix-_R_…"` drift QA captured on /proposals
 * (.playwright-mcp/console-2026-07-06T08-58-30-443Z.log).
 */
describe("LZKC-002 — CreateProposalDialog under a client-divergent shell", () => {
  afterEach(() => cleanup());

  function AuthGatedControls({ loaded }: { loaded: boolean }) {
    // Mirrors auth-header-controls.tsx: nothing until the auth client loads.
    if (!loaded) return null;
    return <span data-testid="header-controls">controls</span>;
  }

  function RepresentativeShell({
    loaded,
    children,
  }: {
    loaded: boolean;
    children: React.ReactNode;
  }) {
    return (
      <div className="flex min-h-screen">
        <header>
          <AuthGatedControls loaded={loaded} />
        </header>
        <main>{children}</main>
      </div>
    );
  }

  function dialogUnderShell(loaded: boolean) {
    return (
      <RepresentativeShell loaded={loaded}>
        <CreateProposalDialog slug="legal-test" customers={PROPOSAL_CUSTOMERS}>
          <Button data-testid="shell-proposal-trigger">New Proposal</Button>
        </CreateProposalDialog>
      </RepresentativeShell>
    );
  }

  it("SSR output inside the shell carries no useId-derived radix ids", () => {
    // Raw renderToString (no id normalisation) — we assert absence, and a
    // normaliser would mask exactly the ids we're checking for.
    const html = renderToString(dialogUnderShell(false));

    expect(html).toContain('data-testid="shell-proposal-trigger"');
    // Pre-LZKC-002 this matched aria-controls="radix-_R_…" — the id the
    // client contradicts whenever the shell diverges.
    expect(html).not.toMatch(/aria-controls/);
    expect(html).not.toMatch(/radix-/);
  });

  it("hydrating with a diverged shell produces no dialog id mismatch and wires the trigger post-mount", async () => {
    // Server pass: auth not loaded (header renders null) — what Fizz emits.
    const serverHtml = renderToString(dialogUnderShell(false));

    // LZKC-002 contract: SSR must not stamp any id the client can
    // contradict. (Pre-fix: serverHtml contained aria-controls="radix-_R_…".)
    const ssrAriaControls = /aria-controls="([^"]+)"/.exec(serverHtml)?.[1] ?? null;
    expect(ssrAriaControls).toBeNull();

    // Client pass: auth loaded synchronously — the header materialises a
    // node the server never rendered. React discards the SSR DOM and
    // regenerates on the client (recoverable error).
    const container = document.createElement("div");
    document.body.appendChild(container);
    // Self-generated renderToString output — not untrusted input.
    container.innerHTML = serverHtml;

    const recoverableErrors: string[] = [];
    let root: Root | undefined;
    await act(async () => {
      root = hydrateRoot(container, dialogUnderShell(true), {
        onRecoverableError: (err) => {
          recoverableErrors.push(String(err instanceof Error ? err.message : err));
        },
      });
    });

    // The shell's own divergence still recovers (that root fix is
    // out-of-scope — see LZKC-002 fix spec §3), but no error may implicate
    // the dialog's ids anymore.
    expect(recoverableErrors.join("\n")).not.toMatch(/aria-controls/);

    // Post-mount the gate flips and Radix wires the trigger for real —
    // client-generated ids only, never SSR-stamped ones.
    const trigger = container.querySelector('[data-testid="shell-proposal-trigger"]');
    expect(trigger).not.toBeNull();
    expect(trigger!.getAttribute("aria-haspopup")).toBe("dialog");
    expect(trigger!.getAttribute("aria-controls")).toMatch(/^radix-/);

    await act(async () => {
      root?.unmount();
    });
    container.remove();
  });
});
