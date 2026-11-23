import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import type { ReactNode } from "react";

vi.mock("server-only", () => ({}));

const actions = vi.hoisted(() => ({
  attest: vi.fn(),
  revokeSignOff: vi.fn(),
  commitBatch: vi.fn(),
  fetchTakeOnRows: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => actions);

import { AttestationStep } from "@/components/take-on/attestation-step";
import type { TakeOnBatch } from "@/lib/types";
import { ATTEST_TEXT, ATTESTATION, CLIENTS_CATALOGUE, clientsBatch } from "./fixtures";

function renderStep(batch: TakeOnBatch, onBatchChange = vi.fn(), onCommitted = vi.fn()) {
  const ui: ReactNode = (
    <AttestationStep
      batch={batch}
      dataset={CLIENTS_CATALOGUE}
      onBatchChange={onBatchChange}
      onCommitted={onCommitted}
    />
  );
  render(<SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>{ui}</SWRConfig>);
  return { onBatchChange, onCommitted };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("AttestationStep — attest", () => {
  it("shows the server's statement exactly and the batch defaults read-only", () => {
    renderStep(clientsBatch());
    expect(screen.getByTestId("take-on-attestation-statement").textContent).toBe(ATTEST_TEXT);
    expect(screen.getByTestId("take-on-attestation-default-cddRecordsLocation")).toHaveTextContent(
      "Archive room, box 12"
    );
    expect(screen.getByTestId("take-on-attestation-defaults")).toHaveTextContent(
      "CDD records location"
    );
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("keeps Attest disabled until the statement is accepted", async () => {
    const user = userEvent.setup();
    renderStep(clientsBatch());
    const attestButton = screen.getByTestId("take-on-attest");
    expect(attestButton).toBeDisabled();
    await user.click(screen.getByTestId("take-on-attestation-accept"));
    expect(attestButton).toBeEnabled();
    await user.click(screen.getByTestId("take-on-attestation-accept"));
    expect(attestButton).toBeDisabled();
    expect(actions.attest).not.toHaveBeenCalled();
  });

  it("sends the validation hash, the statement version and the defaults shown", async () => {
    const user = userEvent.setup();
    const attested = clientsBatch({
      signOffs: [ATTESTATION],
      allowedActions: [
        "REMOVE_SOURCE",
        "MAP",
        "REVALIDATE",
        "COMMIT",
        "REVOKE_ATTESTATION",
        "CANCEL",
      ],
    });
    actions.attest.mockResolvedValue({ success: true, data: attested });
    const { onBatchChange } = renderStep(clientsBatch());
    await user.click(screen.getByTestId("take-on-attestation-accept"));
    await user.click(screen.getByTestId("take-on-attest"));
    expect(actions.attest).toHaveBeenCalledWith("batch-655", {
      validationHash: "hash-v1",
      statementVersion: "FICA-ATTEST-v1",
      defaults: { cddRecordsLocation: "Archive room, box 12" },
    });
    await waitFor(() => expect(onBatchChange).toHaveBeenCalledWith(attested));
  });

  it("sends {} as the defaults when the mapping has none", async () => {
    const user = userEvent.setup();
    actions.attest.mockResolvedValue({ success: true, data: clientsBatch() });
    const batch = clientsBatch();
    batch.sources = [
      { ...batch.sources[0], mapping: { ...batch.sources[0].mapping!, defaults: {} } },
    ];
    renderStep(batch);
    expect(screen.getByTestId("take-on-attestation-defaults")).toHaveTextContent(
      "None. Every row gives its own CDD records location."
    );
    await user.click(screen.getByTestId("take-on-attestation-accept"));
    await user.click(screen.getByTestId("take-on-attest"));
    expect(actions.attest).toHaveBeenCalledWith(
      "batch-655",
      expect.objectContaining({ defaults: {} })
    );
  });

  it.each([
    "This take-on batch has already been attested. Withdraw the attestation to attest again.",
    "Re-validate and sign off again before committing.",
    "The batch defaults shown are not the ones the batch was validated with. Reload the take-on and attest again.",
  ])("shows a refusal verbatim: %s", async (detail) => {
    const user = userEvent.setup();
    actions.attest.mockResolvedValue({ success: false, error: detail, code: "X" });
    const { onBatchChange } = renderStep(clientsBatch());
    await user.click(screen.getByTestId("take-on-attestation-accept"));
    await user.click(screen.getByTestId("take-on-attest"));
    expect((await screen.findByTestId("take-on-attestation-error")).textContent).toBe(detail);
    expect(onBatchChange).not.toHaveBeenCalled();
  });

  it("shows the FICA columns the backend counted at validation, as labels in catalogue order, without reading the rows", () => {
    renderStep(
      clientsBatch({
        summary: {
          clients: 3,
          fica: {
            ficaClientType: { NATURAL_PERSON: 3 },
            riskRating: { UNRATED: 1, LOW: 2 },
            beneficialOwnershipStatus: { NOT_APPLICABLE: 3 },
            screeningStatus: { NOT_SCREENED: 1, SCREENED_CLEAR: 2 },
            sourceOfFundsStatus: {},
          },
        },
      })
    );
    expect(screen.getByTestId("take-on-fica-summary-riskRating")).toHaveTextContent(
      "Low 2 · Not risk-rated 1"
    );
    expect(screen.getByTestId("take-on-fica-summary-screeningStatus")).toHaveTextContent(
      "Screened — clear 2 · Not screened 1"
    );
    expect(screen.getByTestId("take-on-fica-summary-ficaClientType")).toHaveTextContent(
      "Natural person 3"
    );
    expect(screen.getByTestId("take-on-fica-summary-sourceOfFundsStatus")).toHaveTextContent("—");
    expect(screen.getByTestId("take-on-attestation-fica-summary")).toHaveTextContent(
      "of the 3 clients to be created"
    );
    expect(actions.fetchTakeOnRows).not.toHaveBeenCalled();
  });

  it("asks for a re-validation when the batch carries no FICA summary", () => {
    renderStep(clientsBatch({ summary: {} }));
    expect(screen.getByTestId("take-on-attestation-fica-summary-missing")).toHaveTextContent(
      "Validate the batch again to see the FICA columns summarised."
    );
    expect(screen.queryByTestId("take-on-fica-summary-riskRating")).not.toBeInTheDocument();
    expect(actions.fetchTakeOnRows).not.toHaveBeenCalled();
  });
});

describe("AttestationStep — live attestation", () => {
  const attested = clientsBatch({
    signOffs: [ATTESTATION],
    allowedActions: [
      "REMOVE_SOURCE",
      "MAP",
      "REVALIDATE",
      "COMMIT",
      "REVOKE_ATTESTATION",
      "CANCEL",
    ],
  });

  it("shows who attested and withdraws it with revokeSignOff", async () => {
    const user = userEvent.setup();
    actions.revokeSignOff.mockResolvedValue({ success: true, data: clientsBatch() });
    const { onBatchChange } = renderStep(attested);
    expect(screen.getByTestId("take-on-attestation-live")).toHaveTextContent(
      "Attested by Alice Owner (Owner)"
    );
    expect(screen.queryByTestId("take-on-attest")).not.toBeInTheDocument();
    expect(screen.getByTestId("take-on-commit")).toHaveTextContent("Commit take-on");
    await user.click(screen.getByTestId("take-on-attestation-withdraw"));
    expect(actions.revokeSignOff).toHaveBeenCalledWith("batch-655", "sign-off-1");
    await waitFor(() => expect(onBatchChange).toHaveBeenCalled());
  });

  it("offers no commit without the COMMIT action", () => {
    renderStep({ ...attested, allowedActions: ["REVOKE_ATTESTATION", "CANCEL"] });
    expect(screen.queryByTestId("take-on-commit")).not.toBeInTheDocument();
    expect(screen.getByTestId("take-on-attestation-withdraw")).toBeInTheDocument();
  });

  it("explains why it cannot attest a batch with no valid rows", () => {
    renderStep(
      clientsBatch({
        counts: { ...clientsBatch().counts, valid: 0, duplicate: 0 },
        allowedActions: ["REVALIDATE", "CANCEL"],
      })
    );
    expect(screen.getByTestId("take-on-attestation-unavailable")).toHaveTextContent(
      "This take-on batch has no valid rows to attest. Correct the file and validate it again."
    );
    expect(screen.queryByTestId("take-on-attest")).not.toBeInTheDocument();
  });

  it("shows why the last commit did not run", () => {
    const lastError =
      "The attestation for this take-on batch was withdrawn before the commit ran. Attest it again, then commit.";
    renderStep(clientsBatch({ lastError }));
    expect(screen.getByTestId("take-on-last-error").textContent).toBe(lastError);
  });
});
