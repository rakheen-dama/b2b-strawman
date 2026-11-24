import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

const actions = vi.hoisted(() => ({ commitBatch: vi.fn() }));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => actions);

import {
  CommitTakeOnDialog,
  commitBody,
  commitTrustSentence,
} from "@/components/take-on/commit-dialog";
import { ATTESTATION, TRUST_SIGN_OFF, clientsBatch, trustBatch } from "./fixtures";

const ATTESTED = clientsBatch({
  signOffs: [ATTESTATION],
  allowedActions: ["REVALIDATE", "COMMIT", "REVOKE_ATTESTATION", "CANCEL"],
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("CommitTakeOnDialog", () => {
  it("asks with the X3 title, body and button, then commits with the batch hash", async () => {
    const user = userEvent.setup();
    const committing = { ...ATTESTED, status: "COMMITTING" as const, allowedActions: [] };
    actions.commitBatch.mockResolvedValue({ success: true, data: committing });
    const onCommitted = vi.fn();
    render(<CommitTakeOnDialog batch={ATTESTED} onCommitted={onCommitted} />);

    await user.click(screen.getByTestId("take-on-commit"));
    expect(await screen.findByRole("alertdialog")).toBeInTheDocument();
    expect(screen.getByText("Commit clients take-on?")).toBeInTheDocument();
    expect(screen.getByTestId("take-on-commit-body").textContent).toBe(
      "8 records will be created, and 2 duplicates skipped. This cannot be undone."
    );
    expect(screen.getByTestId("take-on-commit-confirm")).toHaveTextContent("Commit take-on");
    expect(actions.commitBatch).not.toHaveBeenCalled();

    await user.click(screen.getByTestId("take-on-commit-confirm"));
    expect(actions.commitBatch).toHaveBeenCalledWith("batch-655", "hash-v1");
    await waitFor(() => expect(onCommitted).toHaveBeenCalledWith(committing));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
  });

  it.each([
    "Take-on commits run in the background, and the background worker is switched off on this server.",
    "Another take-on batch is committing. Wait for it to finish.",
    "This take-on batch is already committing. Wait for it to finish.",
    "Attest the FICA records of this batch before committing it.",
  ])("keeps the dialog open and shows the refusal verbatim: %s", async (detail) => {
    const user = userEvent.setup();
    actions.commitBatch.mockResolvedValue({ success: false, error: detail, code: "X" });
    const onCommitted = vi.fn();
    render(<CommitTakeOnDialog batch={ATTESTED} onCommitted={onCommitted} />);
    await user.click(screen.getByTestId("take-on-commit"));
    await user.click(await screen.findByTestId("take-on-commit-confirm"));
    expect((await screen.findByTestId("take-on-commit-error")).textContent).toBe(detail);
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(onCommitted).not.toHaveBeenCalled();
  });

  it("leaves out the duplicates clause when there are none, and says record for one", () => {
    const counts = { ...ATTESTED.counts, valid: 1, duplicate: 0 };
    expect(commitBody({ ...ATTESTED, counts })).toBe(
      "1 record will be created. This cannot be undone."
    );
    expect(commitBody({ ...ATTESTED, counts: { ...counts, valid: 4, duplicate: 1 } })).toBe(
      "3 records will be created, and 1 duplicate skipped. This cannot be undone."
    );
  });

  it("adds the X3 trust sentence for a trust take-on, naming the account and the cutover date (657C)", async () => {
    const user = userEvent.setup();
    const signed = trustBatch({
      signOffs: [TRUST_SIGN_OFF],
      validationHash: "hash-trust-v1",
      allowedActions: ["REVALIDATE", "COMMIT", "REVOKE_SIGN_OFF", "CANCEL"],
    });
    render(<CommitTakeOnDialog batch={signed} onCommitted={vi.fn()} />);

    await user.click(screen.getByTestId("take-on-commit"));
    expect(await screen.findByText("Commit trust opening position take-on?")).toBeInTheDocument();
    expect(screen.getByTestId("take-on-commit-trust").textContent).toBe(
      "The opening position of Trust Account G at 31 October 2026 will be posted and the account will not accept entries dated on or before that date."
    );
    expect(commitTrustSentence(ATTESTED)).toBeNull();
  });

  it("shows no trust sentence on a clients commit", async () => {
    const user = userEvent.setup();
    render(<CommitTakeOnDialog batch={ATTESTED} onCommitted={vi.fn()} />);
    await user.click(screen.getByTestId("take-on-commit"));
    await screen.findByRole("alertdialog");
    expect(screen.queryByTestId("take-on-commit-trust")).not.toBeInTheDocument();
  });
});
