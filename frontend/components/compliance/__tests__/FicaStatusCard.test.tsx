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
          clientId: "11111111-1111-1111-1111-111111111111",
          status: "DONE",
          lastVerifiedAt: "2026-04-22T10:30:00Z",
          requestId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
          checklistInstanceId: null,
        }}
      />
    );

    expect(screen.getByTestId("fica-status-card")).toBeInTheDocument();
    expect(screen.getByTestId("fica-status-badge")).toHaveTextContent("Done");
    expect(screen.getByText(/Verified/)).toBeInTheDocument();
    expect(screen.getByTestId("fica-last-verified-at")).toBeInTheDocument();
    const link = screen.getByTestId("fica-request-link") as HTMLAnchorElement;
    expect(link.getAttribute("href")).toBe(
      "/org/legal-test/information-requests/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );
    expect(screen.queryByTestId("fica-checklist-link")).not.toBeInTheDocument();
  });

  it("links to the canonical /information-requests/{id} route (OBS-501)", () => {
    render(
      <FicaStatusCard
        slug="acme"
        ficaStatus={{
          clientId: "11111111-1111-1111-1111-111111111111",
          status: "IN_PROGRESS",
          lastVerifiedAt: null,
          requestId: "req-123",
          checklistInstanceId: null,
        }}
      />
    );
    const link = screen.getByTestId("fica-request-link") as HTMLAnchorElement;
    expect(link).toHaveAttribute("href", "/org/acme/information-requests/req-123");
    expect(link.getAttribute("href")).not.toMatch(/\/org\/[^/]+\/requests\//);
  });

  it("renders the In Progress badge + awaiting-review copy when FICA request exists but items are not all accepted", () => {
    render(
      <FicaStatusCard
        slug="legal-test"
        ficaStatus={{
          clientId: "11111111-1111-1111-1111-111111111111",
          status: "IN_PROGRESS",
          lastVerifiedAt: null,
          requestId: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
          checklistInstanceId: null,
        }}
      />
    );

    expect(screen.getByTestId("fica-status-badge")).toHaveTextContent("In Progress");
    expect(screen.getByText(/Awaiting client response/i)).toBeInTheDocument();
    expect(screen.queryByTestId("fica-last-verified-at")).not.toBeInTheDocument();
    expect(screen.getByTestId("fica-request-link")).toBeInTheDocument();
  });

  it("renders the Not Started badge + empty-state copy when no FICA onboarding exists", () => {
    render(
      <FicaStatusCard
        slug="legal-test"
        ficaStatus={{
          clientId: "11111111-1111-1111-1111-111111111111",
          status: "NOT_STARTED",
          lastVerifiedAt: null,
          requestId: null,
          checklistInstanceId: null,
        }}
      />
    );

    expect(screen.getByTestId("fica-status-badge")).toHaveTextContent("Not Started");
    expect(screen.getByText("No FICA onboarding started.")).toBeInTheDocument();
    expect(screen.queryByText(/No FICA onboarding request yet/i)).not.toBeInTheDocument();
    // Nothing to link to.
    expect(screen.queryByTestId("fica-request-link")).not.toBeInTheDocument();
    expect(screen.queryByTestId("fica-checklist-link")).not.toBeInTheDocument();
  });

  it("renders a soft 'Status unavailable' state when the upstream projection is null (fetch failed)", () => {
    render(<FicaStatusCard slug="legal-test" ficaStatus={null} />);

    expect(screen.getByTestId("fica-status-card")).toBeInTheDocument();
    expect(screen.getByText("Status unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("fica-status-badge")).not.toBeInTheDocument();
  });
});

describe("FicaStatusCard — checklist-first (Phase 88, ADR-348)", () => {
  afterEach(() => cleanup());

  const CLIENT = "11111111-1111-1111-1111-111111111111";
  const INSTANCE = "cccccccc-cccc-cccc-cccc-cccccccccccc";
  const REQUEST = "dddddddd-dddd-dddd-dddd-dddddddddddd";

  it("links a checklist-decided DONE status to the client's FICA onboarding tab and says Completed, not Verified", () => {
    render(
      <FicaStatusCard
        slug="legal-test"
        ficaStatus={{
          clientId: CLIENT,
          status: "DONE",
          lastVerifiedAt: "2026-10-23T08:00:00Z",
          requestId: null,
          checklistInstanceId: INSTANCE,
        }}
      />
    );

    expect(screen.getByTestId("fica-status-badge")).toHaveTextContent("Done");
    expect(screen.getByText(/Completed/)).toBeInTheDocument();
    expect(screen.queryByText(/Verified/)).not.toBeInTheDocument();
    expect(screen.getByTestId("fica-last-verified-at")).toBeInTheDocument();
    const link = screen.getByTestId("fica-checklist-link");
    expect(link).toHaveAttribute("href", `/org/legal-test/clients/${CLIENT}?tab=onboarding`);
    expect(link).toHaveTextContent("View checklist");
    expect(screen.queryByTestId("fica-request-link")).not.toBeInTheDocument();
  });

  it("links an open onboarding checklist to the FICA onboarding tab with checklist copy", () => {
    render(
      <FicaStatusCard
        slug="legal-test"
        ficaStatus={{
          clientId: CLIENT,
          status: "IN_PROGRESS",
          lastVerifiedAt: null,
          requestId: null,
          checklistInstanceId: INSTANCE,
        }}
      />
    );

    expect(screen.getByTestId("fica-status-badge")).toHaveTextContent("In Progress");
    expect(screen.getByText("Onboarding checklist in progress.")).toBeInTheDocument();
    expect(screen.queryByText(/Awaiting client response/i)).not.toBeInTheDocument();
    expect(screen.queryByTestId("fica-last-verified-at")).not.toBeInTheDocument();
    expect(screen.getByTestId("fica-checklist-link")).toHaveAttribute(
      "href",
      `/org/legal-test/clients/${CLIENT}?tab=onboarding`
    );
    expect(screen.queryByTestId("fica-request-link")).not.toBeInTheDocument();
  });

  it("prefers the checklist link when both ids are present", () => {
    render(
      <FicaStatusCard
        slug="legal-test"
        ficaStatus={{
          clientId: CLIENT,
          status: "DONE",
          lastVerifiedAt: "2026-10-23T08:00:00Z",
          requestId: REQUEST,
          checklistInstanceId: INSTANCE,
        }}
      />
    );

    expect(screen.getByTestId("fica-checklist-link")).toHaveAttribute(
      "href",
      `/org/legal-test/clients/${CLIENT}?tab=onboarding`
    );
    expect(screen.queryByTestId("fica-request-link")).not.toBeInTheDocument();
  });
});
