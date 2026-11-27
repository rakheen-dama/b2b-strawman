import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { DataRequestsSection } from "@/components/compliance/DataRequestsSection";
import {
  UrgentDataRequestsTable,
  type UrgentDataRequestRow,
} from "@/components/compliance/UrgentDataRequestsTable";
import type { DataRequestResponse } from "@/lib/types";

// The table is the kit DataTable, which calls useRouter().
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

function request(id: string, clientName: string, deadline: string): DataRequestResponse {
  return {
    id,
    clientId: `client-${id}`,
    clientName,
    requestType: "ACCESS",
    status: "RECEIVED",
    description: "Access request",
    rejectionReason: null,
    deadline,
    requestedAt: "2026-11-01T08:00:00Z",
    requestedBy: "u1",
    requestedByName: "Alice",
    completedAt: null,
    completedBy: null,
    completedByName: null,
    hasExport: false,
    notes: null,
    createdAt: "2026-11-01T08:00:00Z",
  };
}

function row(id: string, daysUntil: number): UrgentDataRequestRow {
  return {
    id,
    clientName: `Client ${id}`,
    requestType: "ACCESS",
    deadline: "2026-12-01",
    daysUntil,
    status: "RECEIVED",
  };
}

/** A local "YYYY-MM-DD" date `offsetDays` from today. */
function localDate(offsetDays: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mm}-${dd}`;
}

describe("DataRequestsSection", () => {
  afterEach(() => cleanup());

  it("shows a plain line, no table and only the section heading when there are no open requests", () => {
    render(<DataRequestsSection openCount={0} urgentRequests={[]} orgSlug="test-org" />);
    const empty = screen.getByText("No open data requests");
    expect(empty.tagName).toBe("P");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getAllByRole("heading").map((h) => h.textContent)).toEqual(["Data Requests"]);
    expect(screen.queryByRole("link", { name: "View All Requests" })).not.toBeInTheDocument();
  });

  it("renders the Client, Type, Deadline and Status columns and a link to all requests", () => {
    render(
      <DataRequestsSection
        openCount={1}
        urgentRequests={[request("r1", "Mokoena Holdings", localDate(10))]}
        orgSlug="test-org"
      />
    );
    expect(screen.getAllByRole("columnheader").map((h) => h.textContent)).toEqual([
      "Client",
      "Type",
      "Deadline",
      "Status",
    ]);
    const cells = within(screen.getAllByRole("row")[1]).getAllByRole("cell");
    expect(cells[0]).toHaveTextContent("Mokoena Holdings");
    expect(cells[1]).toHaveTextContent("ACCESS");
    expect(cells[3]).toHaveTextContent("RECEIVED");
    expect(screen.getByText("1 open request")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View All Requests" })).toHaveAttribute(
      "href",
      "/org/test-org/compliance/requests"
    );
  });

  it("counts a past deadline as overdue in red and a future one as remaining", () => {
    render(
      <DataRequestsSection
        openCount={2}
        urgentRequests={[
          request("r1", "Overdue Client", localDate(-5)),
          request("r2", "Future Client", localDate(20)),
        ]}
        orgSlug="test-org"
      />
    );
    const overdue = screen.getByText(/^\d+d overdue$/);
    expect(overdue).toHaveClass("text-red-600");
    expect(screen.getByText(/^\d+d remaining$/)).toHaveClass("text-emerald-600");
    // Rows keep the server's order.
    const rows = within(screen.getByRole("table")).getAllByRole("row").slice(1);
    expect(rows.map((r) => within(r).getAllByRole("cell")[0].textContent)).toEqual([
      "Overdue Client",
      "Future Client",
    ]);
  });
});

describe("DataRequestsSection countdown on the South African calendar (F-669A-A-4)", () => {
  // Today in South Africa is Fri 27 Nov 2026 at each of these instants.
  const instants = [
    ["00:30 SAST", "2026-11-26T22:30:00Z"],
    ["11:40 SAST", "2026-11-27T09:40:00Z"],
    ["23:30 SAST", "2026-11-27T21:30:00Z"],
  ] as const;
  // The server renders in UTC; a browser may run in any zone. The count must not depend on it.
  const zones = ["UTC", "Africa/Johannesburg", "America/New_York", "Pacific/Kiritimati"];
  const originalTz = process.env.TZ;

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    process.env.TZ = originalTz;
  });

  describe.each(zones)("runtime zone %s", (zone) => {
    it.each(instants)(
      "at %s, yesterday is 1d overdue, today is due today, tomorrow is 1d remaining",
      (_label, instant) => {
        process.env.TZ = zone;
        vi.useFakeTimers({ toFake: ["Date"] });
        vi.setSystemTime(new Date(instant));
        render(
          <DataRequestsSection
            openCount={3}
            urgentRequests={[
              request("y", "Yesterday Client", "2026-11-26"),
              request("t", "Today Client", "2026-11-27"),
              request("m", "Tomorrow Client", "2026-11-28"),
            ]}
            orgSlug="test-org"
          />
        );
        const countdowns = within(screen.getByRole("table"))
          .getAllByRole("row")
          .slice(1)
          .map((r) => [
            within(r).getAllByRole("cell")[0].textContent,
            within(r).getByText(/overdue|Due today|remaining/).textContent,
          ]);
        expect(countdowns).toEqual([
          ["Yesterday Client", "1d overdue"],
          ["Today Client", "Due today"],
          ["Tomorrow Client", "1d remaining"],
        ]);
      }
    );
  });
});

describe("UrgentDataRequestsTable", () => {
  afterEach(() => cleanup());

  it("renders the countdown from the server-computed day count", () => {
    render(<UrgentDataRequestsTable rows={[row("a", -2), row("b", 0), row("c", 5)]} />);
    expect(screen.getByText("2d overdue")).toHaveClass("text-red-600");
    expect(screen.getByText("Due today")).toHaveClass("text-red-600");
    expect(screen.getByText("5d remaining")).toHaveClass("text-amber-600");
  });
});
