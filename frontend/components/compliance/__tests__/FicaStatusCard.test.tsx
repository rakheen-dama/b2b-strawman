import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import { FicaStatusCard } from "@/components/compliance/FicaStatusCard";

describe("FicaStatusCard (GAP-L-46)", () => {
  afterEach(() => cleanup());

  it("renders the Done badge, a verified timestamp, and a request link when every item is accepted", () => {
    render(
      <FicaStatusCard
        slug="legal-test"
        ficaStatus={{
          customerId: "11111111-1111-1111-1111-111111111111",
          status: "DONE",
          lastVerifiedAt: "2026-04-22T10:30:00Z",
          requestId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        }}
      />
    );

    expect(screen.getByTestId("fica-status-card")).toBeInTheDocument();
    expect(screen.getByTestId("fica-status-badge")).toHaveTextContent("Done");
    expect(screen.getByTestId("fica-last-verified-at")).toBeInTheDocument();
    const link = screen.getByTestId("fica-request-link") as HTMLAnchorElement;
    expect(link.getAttribute("href")).toBe(
      "/org/legal-test/requests/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );
  });

  it("renders the In Progress badge + awaiting-review copy when FICA request exists but items are not all accepted", () => {
    render(
      <FicaStatusCard
        slug="legal-test"
        ficaStatus={{
          customerId: "11111111-1111-1111-1111-111111111111",
          status: "IN_PROGRESS",
          lastVerifiedAt: null,
          requestId: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
        }}
      />
    );

    expect(screen.getByTestId("fica-status-badge")).toHaveTextContent("In Progress");
    expect(screen.getByText(/Awaiting client response/i)).toBeInTheDocument();
    expect(screen.queryByTestId("fica-last-verified-at")).not.toBeInTheDocument();
    expect(screen.getByTestId("fica-request-link")).toBeInTheDocument();
  });

  it("renders the Not Started badge + empty-state copy when no FICA request exists", () => {
    render(
      <FicaStatusCard
        slug="legal-test"
        ficaStatus={{
          customerId: "11111111-1111-1111-1111-111111111111",
          status: "NOT_STARTED",
          lastVerifiedAt: null,
          requestId: null,
        }}
      />
    );

    expect(screen.getByTestId("fica-status-badge")).toHaveTextContent("Not Started");
    expect(screen.getByText(/No FICA onboarding request yet/i)).toBeInTheDocument();
    // No request to link to.
    expect(screen.queryByTestId("fica-request-link")).not.toBeInTheDocument();
  });

  it("renders a soft 'Status unavailable' state when the upstream projection is null (fetch failed)", () => {
    render(<FicaStatusCard slug="legal-test" ficaStatus={null} />);

    expect(screen.getByTestId("fica-status-card")).toBeInTheDocument();
    expect(screen.getByText("Status unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("fica-status-badge")).not.toBeInTheDocument();
  });
});
