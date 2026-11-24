import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import { ClientFicaAttestationCard } from "@/components/compliance/ClientFicaAttestationCard";
import { formatDateInSA } from "@/lib/format";
import type { ClientFicaAttestation, FicaStatus } from "@/lib/types/fica";

// Phase 93 655B (§17.3.5, ADR-388): the take-on FICA attestation card on client detail.

const CLIENT = "11111111-1111-1111-1111-111111111111";
const ATTESTATION_ID = "22222222-2222-2222-2222-222222222222";

const ATTESTATION: ClientFicaAttestation = {
  id: ATTESTATION_ID,
  clientId: CLIENT,
  status: "ACTIVE",
  takeOnBatchId: "33333333-3333-3333-3333-333333333333",
  cddCompletedDate: "2024-03-15",
  lastReviewDate: "2025-02-01",
  riskRating: "HIGH",
  ficaClientType: "CLOSE_CORPORATION",
  beneficialOwnershipStatus: "NOT_APPLICABLE",
  screeningStatus: "SCREENED_CLEAR",
  screeningDate: "2025-01-20",
  sourceOfFundsStatus: "NOT_REQUIRED",
  cddRecordsLocation: "Archive box 12, Pretoria office",
  sourceSystemRef: "LS-4471",
  nextReviewDate: "2026-02-01",
  attestedBy: "44444444-4444-4444-4444-444444444444",
  attestedByName: "Thandi Mokoena",
  attestedByRole: "Owner",
  statementVersion: "FICA-ATTEST-v1",
  attestedAt: "2026-11-20T09:15:00Z",
};

function status(overrides: Partial<FicaStatus> = {}): FicaStatus {
  return {
    clientId: CLIENT,
    status: "ATTESTED",
    lastVerifiedAt: null,
    requestId: null,
    checklistInstanceId: null,
    reviewReasons: [],
    attestationId: ATTESTATION_ID,
    ...overrides,
  };
}

const field = (key: string) => screen.getByTestId(`fica-attestation-field-${key}`);

describe("ClientFicaAttestationCard (655B)", () => {
  afterEach(() => cleanup());

  it("renders every field with its label, never the raw enum", () => {
    render(<ClientFicaAttestationCard attestation={ATTESTATION} ficaStatus={status()} />);

    expect(screen.getByTestId("client-fica-attestation-card")).toHaveTextContent(
      "FICA attestation"
    );
    expect(screen.getByTestId("fica-attestation-status")).toHaveTextContent(
      "Verified in prior system (attested)"
    );
    expect(field("client-type")).toHaveTextContent("Close corporation");
    expect(field("risk-rating")).toHaveTextContent("High");
    expect(field("cdd-completed")).toHaveTextContent(formatDateInSA("2024-03-15"));
    expect(field("last-review")).toHaveTextContent(formatDateInSA("2025-02-01"));
    expect(field("next-review")).toHaveTextContent(formatDateInSA("2026-02-01"));
    expect(field("beneficial-ownership")).toHaveTextContent("Not applicable");
    expect(field("screening")).toHaveTextContent(
      `Screened — clear · ${formatDateInSA("2025-01-20")}`
    );
    expect(field("source-of-funds")).toHaveTextContent("Not required");
    expect(field("records-location")).toHaveTextContent("Archive box 12, Pretoria office");
    expect(field("source-system-ref")).toHaveTextContent("LS-4471");
    expect(field("statement-version")).toHaveTextContent("FICA-ATTEST-v1");
    expect(screen.queryByTestId("fica-attestation-review-reasons")).not.toBeInTheDocument();

    const text = screen.getByTestId("client-fica-attestation-card").textContent ?? "";
    expect(text).not.toMatch(
      /CLOSE_CORPORATION|NOT_APPLICABLE|SCREENED_CLEAR|NOT_REQUIRED|HIGH|ATTESTED/
    );
    expect(text).toContain("CDD was done in the previous system.");
  });

  it("says who attested and when, on the SA calendar", () => {
    render(<ClientFicaAttestationCard attestation={ATTESTATION} ficaStatus={status()} />);

    expect(field("attested-by")).toHaveTextContent("Thandi Mokoena (Owner)");
    expect(field("attested-on")).toHaveTextContent(formatDateInSA("2026-11-20T09:15:00Z"));
  });

  it("unrated: 'Not risk-rated', next review '—', and the reasons with the review-due badge", () => {
    render(
      <ClientFicaAttestationCard
        attestation={{
          ...ATTESTATION,
          riskRating: "UNRATED",
          nextReviewDate: null,
          lastReviewDate: null,
          screeningStatus: "NOT_SCREENED",
          screeningDate: null,
          sourceSystemRef: null,
        }}
        ficaStatus={status({
          status: "ATTESTED_REVIEW",
          reviewReasons: ["UNRATED", "NOT_SCREENED"],
        })}
      />
    );

    expect(screen.getByTestId("fica-attestation-status")).toHaveTextContent(
      "Review due (attested)"
    );
    expect(field("risk-rating")).toHaveTextContent("Not risk-rated");
    expect(field("next-review")).toHaveTextContent(/^Next review—$/);
    expect(field("last-review")).toHaveTextContent(/^Last review—$/);
    expect(field("screening")).toHaveTextContent(/^ScreeningNot screened$/);
    expect(field("source-system-ref")).toHaveTextContent(/^Previous system ID—$/);
    const reasons = Array.from(
      screen.getByTestId("fica-attestation-review-reasons").querySelectorAll("li"),
      (li) => li.textContent
    );
    expect(reasons).toEqual(["Not risk-rated", "Not screened"]);
  });

  it("no badge or reasons once Kazi evidence decides the status (FD-29), or the status read failed", () => {
    const { unmount } = render(
      <ClientFicaAttestationCard
        attestation={ATTESTATION}
        ficaStatus={status({
          status: "IN_PROGRESS",
          checklistInstanceId: "55555555-5555-5555-5555-555555555555",
          attestationId: null,
        })}
      />
    );
    expect(screen.getByTestId("client-fica-attestation-card")).toBeInTheDocument();
    expect(screen.queryByTestId("fica-attestation-status")).not.toBeInTheDocument();
    expect(field("records-location")).toHaveTextContent("Archive box 12, Pretoria office");
    unmount();

    render(<ClientFicaAttestationCard attestation={ATTESTATION} ficaStatus={null} />);
    expect(screen.queryByTestId("fica-attestation-status")).not.toBeInTheDocument();
    expect(screen.queryByTestId("fica-attestation-review-reasons")).not.toBeInTheDocument();
  });
});
