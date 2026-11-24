import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import type { ReactNode } from "react";

vi.mock("server-only", () => ({}));

// Every take-on action: the panel may call only the signer's three; the rest are MANAGE_TAKE_ON.
const actions = vi.hoisted(() => ({
  getSignOffReview: vi.fn(),
  signOff: vi.fn(),
  withdrawMySignOff: vi.fn(),
  getSignOffQueue: vi.fn(),
  fetchTakeOnBatch: vi.fn(),
  fetchTakeOnRows: vi.fn(),
  validateBatch: vi.fn(),
  savePosition: vi.fn(),
  commitBatch: vi.fn(),
  revokeSignOff: vi.fn(),
  cancelBatch: vi.fn(),
  attest: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => actions);

import { SignOffPanel } from "@/components/take-on/sign-off-panel";
import { SIGN_OFF_TEXT, TRUST_SIGN_OFF, signOffReview, signedReview } from "./fixtures";

const MANAGE_TAKE_ON_ACTIONS = [
  "fetchTakeOnBatch",
  "fetchTakeOnRows",
  "validateBatch",
  "savePosition",
  "commitBatch",
  "revokeSignOff",
  "cancelBatch",
  "attest",
] as const;

const withSWR = (ui: ReactNode) => (
  <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0, shouldRetryOnError: false }}>
    {ui}
  </SWRConfig>
);

function renderPanel(onChange = vi.fn()) {
  render(withSWR(<SignOffPanel batchId="batch-657b" onChange={onChange} />));
  return onChange;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("SignOffPanel (657C)", () => {
  it("shows what is signed: account, cutover date, position, control totals, rows and the declaration verbatim", async () => {
    actions.getSignOffReview.mockResolvedValue({ success: true, data: signOffReview() });
    renderPanel();

    const panel = await screen.findByTestId("takeon-signoff-panel");
    expect(actions.getSignOffReview).toHaveBeenCalledWith("batch-657b", 0, 50);
    expect(screen.getByTestId("takeon-signoff-account")).toHaveTextContent("Trust Account G");
    expect(screen.getByTestId("takeon-signoff-cutover")).toHaveTextContent("31 October 2026");
    expect(screen.getByTestId("takeon-signoff-prepared-by")).toHaveTextContent("Bob Admin");
    expect(screen.getByTestId("takeon-signoff-position-bankBalance")).toHaveTextContent(
      /418\s?500,00/
    );
    // The five X3 indicator rows, each agreeing.
    for (const id of ["eq1", "items", "eq2", "investments", "deficit"]) {
      expect(screen.getByTestId(`take-on-equation-${id}`)).toHaveAttribute("data-holds", "true");
    }
    const row = screen.getByTestId("takeon-signoff-row-CLIENT_LEDGER-2");
    expect(row).toHaveTextContent("Client and matter balances");
    expect(row).toHaveTextContent("client.a@example.com · M-A1");
    expect(row).toHaveTextContent(/300\s?000,00/);
    expect(row).not.toHaveTextContent("4b1d7c3e");
    expect(screen.getByTestId("takeon-signoff-declaration").textContent).toBe(SIGN_OFF_TEXT);
    expect(screen.getByTestId("takeon-signoff-declaration-version")).toHaveTextContent(
      "TAKEON-SIGNOFF-v1"
    );
    expect(within(panel).getByTestId("takeon-signoff-flags")).toHaveTextContent(
      "Unidentified deposit older than one year"
    );
  });

  it("keeps Sign off disabled until the declaration is accepted, then signs on the reviewed hash — and calls no MANAGE_TAKE_ON action", async () => {
    const user = userEvent.setup();
    actions.getSignOffReview.mockResolvedValue({ success: true, data: signOffReview() });
    const signed = signedReview();
    actions.signOff.mockResolvedValue({ success: true, data: signed });
    const onChange = renderPanel();

    const submit = await screen.findByTestId("takeon-signoff-submit");
    expect(submit).toBeDisabled();
    await user.click(screen.getByTestId("takeon-signoff-accept"));
    expect(submit).toBeEnabled();
    await user.click(submit);

    expect(actions.signOff).toHaveBeenCalledWith("batch-657b", {
      validationHash: "hash-trust-v1",
      statementVersion: "TAKEON-SIGNOFF-v1",
      declarationAccepted: true,
    });
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(signed));
    expect(await screen.findByTestId("takeon-signoff-live")).toHaveTextContent(
      "Signed off by you (Owner)"
    );
    expect(screen.getByTestId("takeon-signoff-withdraw")).toHaveTextContent("Withdraw my sign-off");
    expect(screen.queryByTestId("takeon-signoff-submit")).not.toBeInTheDocument();
    for (const name of MANAGE_TAKE_ON_ACTIONS) {
      expect(actions[name]).not.toHaveBeenCalled();
    }
  });

  it.each([
    "A client named in this trust take-on was anonymised after it was validated. Validate it again, then sign it off.",
    "This trust take-on has already been signed off. Withdraw the sign-off to sign again.",
    "Re-validate and sign off again before committing.",
  ])("shows a refusal verbatim and stays unsigned: %s", async (detail) => {
    const user = userEvent.setup();
    actions.getSignOffReview.mockResolvedValue({ success: true, data: signOffReview() });
    actions.signOff.mockResolvedValue({ success: false, error: detail, code: "X" });
    const onChange = renderPanel();

    await user.click(await screen.findByTestId("takeon-signoff-accept"));
    await user.click(screen.getByTestId("takeon-signoff-submit"));
    expect((await screen.findByTestId("takeon-signoff-error")).textContent).toBe(detail);
    expect(screen.queryByTestId("takeon-signoff-live")).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("lets the signer withdraw their own sign-off", async () => {
    const user = userEvent.setup();
    actions.getSignOffReview.mockResolvedValue({ success: true, data: signedReview() });
    const unsigned = signOffReview();
    actions.withdrawMySignOff.mockResolvedValue({ success: true, data: unsigned });
    const onChange = renderPanel();

    await user.click(await screen.findByTestId("takeon-signoff-withdraw"));
    expect(actions.withdrawMySignOff).toHaveBeenCalledWith("batch-657b");
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(unsigned));
    expect(await screen.findByTestId("takeon-signoff-submit")).toBeDisabled();
    expect(actions.revokeSignOff).not.toHaveBeenCalled();
  });

  it("shows another member's live sign-off without a withdraw control", async () => {
    actions.getSignOffReview.mockResolvedValue({
      success: true,
      data: signOffReview({
        liveSignOff: { ...TRUST_SIGN_OFF, memberName: "Carol Partner", roleName: "Partner" },
        allowedActions: [],
      }),
    });
    renderPanel();

    expect(await screen.findByTestId("takeon-signoff-live")).toHaveTextContent(
      "Signed off by Carol Partner (Partner)"
    );
    expect(screen.queryByTestId("takeon-signoff-withdraw")).not.toBeInTheDocument();
    expect(screen.queryByTestId("takeon-signoff-accept")).not.toBeInTheDocument();
  });

  it.each([
    ["COMMITTING", "This trust take-on is being committed."],
    ["COMPLETED", "This trust take-on has been committed."],
  ] as const)(
    "says where a %s batch is instead of why it cannot be signed",
    async (status, text) => {
      actions.getSignOffReview.mockResolvedValue({
        success: true,
        data: signOffReview({ status, allowedActions: [] }),
      });
      renderPanel();
      expect((await screen.findByTestId("takeon-signoff-unavailable")).textContent).toBe(text);
      expect(screen.queryByTestId("takeon-signoff-submit")).not.toBeInTheDocument();
    }
  );

  it("offers no sign-off when the review allows none, and shows a failed read verbatim", async () => {
    actions.getSignOffReview.mockResolvedValueOnce({
      success: true,
      data: signOffReview({ allowedActions: [] }),
    });
    renderPanel();
    expect((await screen.findByTestId("takeon-signoff-unavailable")).textContent).toMatch(
      /^This trust take-on cannot be signed off now\./
    );
    expect(screen.queryByTestId("takeon-signoff-submit")).not.toBeInTheDocument();
    cleanup();

    actions.getSignOffReview.mockResolvedValueOnce({ success: false, error: "Access denied" });
    renderPanel();
    expect((await screen.findByTestId("takeon-signoff-load-error")).textContent).toBe(
      "Access denied"
    );
  });
});
