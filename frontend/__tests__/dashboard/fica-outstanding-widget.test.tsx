import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { FicaOutstandingWidget } from "@/components/dashboard/fica-outstanding-widget";
import type { FicaOutstandingResponse } from "@/lib/dashboard-types";

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const DATA: FicaOutstandingResponse = {
  outstandingCount: 7,
  totalActiveClients: 24,
  limit: 5,
  clients: [
    {
      clientId: "c1",
      clientName: "Dlamini Holdings",
      lifecycleStatus: "ONBOARDING",
      ficaStatus: "NOT_STARTED",
      requestId: null,
      openedAt: "2026-09-01T08:00:00Z",
      daysOutstanding: 50,
    },
    {
      clientId: "c2",
      clientName: "Mokoena Trust",
      lifecycleStatus: "ACTIVE",
      ficaStatus: "IN_PROGRESS",
      requestId: "r2",
      openedAt: "2026-10-20T08:00:00Z",
      daysOutstanding: 1,
    },
  ],
};

describe("FicaOutstandingWidget", () => {
  afterEach(() => cleanup());

  it("headline outstanding of active clients, client rows with status and days", () => {
    render(<FicaOutstandingWidget slug="acme" data={DATA} />);

    expect(screen.getByTestId("fica-outstanding-headline")).toHaveTextContent("7");
    expect(screen.getByText(/of 24 active clients without completed FICA/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Dlamini Holdings/ })).toHaveAttribute(
      "href",
      "/org/acme/clients/c1"
    );
    expect(screen.getByText("Not started")).toBeInTheDocument();
    expect(screen.getByText("In progress")).toBeInTheDocument();
    expect(screen.getByText("50 days")).toBeInTheDocument();
    expect(screen.getByText("1 day")).toBeInTheDocument();
  });

  it("header links the FICA outstanding client view and compliance; footer links incomplete profiles", () => {
    render(<FicaOutstandingWidget slug="acme" data={DATA} />);

    // 615D (D-4): the Clients system view landed
    expect(screen.getByRole("link", { name: "View all" })).toHaveAttribute(
      "href",
      "/org/acme/clients?view=system:fica-outstanding"
    );

    expect(screen.getByRole("link", { name: "Open compliance" })).toHaveAttribute(
      "href",
      "/org/acme/compliance"
    );
    expect(screen.getByRole("link", { name: "Incomplete profiles" })).toHaveAttribute(
      "href",
      "/org/acme/clients?showIncomplete=true"
    );
    expect(screen.queryByRole("link", { name: /Information requests/ })).not.toBeInTheDocument();
  });

  it("nothing outstanding: says so", () => {
    render(
      <FicaOutstandingWidget slug="acme" data={{ ...DATA, outstandingCount: 0, clients: [] }} />
    );
    expect(screen.getByText("Every active client has completed FICA.")).toBeInTheDocument();
  });

  it("error: quiet error line, footer link kept", () => {
    render(<FicaOutstandingWidget slug="acme" data={null} />);
    expect(screen.getByText(/could not be loaded/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Incomplete profiles" })).toBeInTheDocument();
  });
});
