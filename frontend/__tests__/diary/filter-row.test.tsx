import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { DiaryFilterRow } from "@/components/diary/diary-filter-row";
import type { DiaryKind } from "@/app/(app)/org/[slug]/diary/diary-types";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const ALL_KINDS: DiaryKind[] = ["TASK_DUE", "MATTER_DUE", "COURT_DATE", "PRESCRIPTION"];
const MEMBERS = [
  { id: "fe-1", name: "Thandi Nkosi", email: "thandi@firm.test" },
  { id: "fe-2", name: "Pieter van Wyk", email: "pieter@firm.test" },
];
const MATTERS = [
  { id: "m-1", name: "Dlamini v Road Accident Fund", referenceNumber: "LIT-001" },
  { id: "m-2", name: "Zulu Estate", referenceNumber: null },
];

function filterProps(overrides: Partial<React.ComponentProps<typeof DiaryFilterRow>> = {}) {
  return {
    typesServed: ALL_KINDS,
    kinds: ALL_KINDS,
    onKindsChange: vi.fn(),
    feeEarner: "all",
    onFeeEarnerChange: vi.fn(),
    members: MEMBERS,
    showMine: true,
    matterId: null,
    onMatterChange: vi.fn(),
    matters: MATTERS,
    ...overrides,
  };
}

describe("DiaryFilterRow", () => {
  it("renders a chip per served kind in display order", () => {
    render(<DiaryFilterRow {...filterProps()} />);
    const group = screen.getByRole("group", { name: "Event kinds" });
    expect(
      within(group)
        .getAllByRole("button")
        .map((b) => b.textContent)
    ).toEqual(["Court dates", "Prescriptions", "Matter due dates", "Tasks"]);
  });

  it("hides court chips when the server did not serve them", () => {
    render(<DiaryFilterRow {...filterProps({ typesServed: ["TASK_DUE", "MATTER_DUE"] })} />);
    expect(screen.queryByRole("button", { name: "Court dates" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Prescriptions" })).toBeNull();
    expect(screen.getByRole("button", { name: "Tasks" })).toBeInTheDocument();
  });

  it("hides the chip group entirely when no kinds were served", () => {
    render(<DiaryFilterRow {...filterProps({ typesServed: [], kinds: [] })} />);
    expect(screen.queryByRole("group", { name: "Event kinds" })).toBeNull();
  });

  it("toggles a chip off via onKindsChange and reflects state with aria-pressed", async () => {
    const user = userEvent.setup();
    const props = filterProps({ kinds: ["COURT_DATE", "PRESCRIPTION", "TASK_DUE"] });
    render(<DiaryFilterRow {...props} />);
    expect(screen.getByRole("button", { name: "Court dates" })).toHaveAttribute(
      "aria-pressed",
      "true"
    );
    expect(screen.getByRole("button", { name: "Matter due dates" })).toHaveAttribute(
      "aria-pressed",
      "false"
    );
    await user.click(screen.getByRole("button", { name: "Court dates" }));
    expect(props.onKindsChange).toHaveBeenCalledWith(["PRESCRIPTION", "TASK_DUE"]);
    await user.click(screen.getByRole("button", { name: "Matter due dates" }));
    expect(props.onKindsChange).toHaveBeenLastCalledWith([
      "COURT_DATE",
      "PRESCRIPTION",
      "MATTER_DUE",
      "TASK_DUE",
    ]);
  });

  it("offers Mine in the fee-earner select only when the current member is known", async () => {
    const user = userEvent.setup();
    render(<DiaryFilterRow {...filterProps({ showMine: false })} />);
    await user.click(screen.getByRole("combobox", { name: "Fee earner" }));
    expect(await screen.findByRole("option", { name: "All fee earners" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Mine" })).toBeNull();
    expect(screen.getByRole("option", { name: "Pieter van Wyk" })).toBeInTheDocument();
  });

  it("selects a fee earner", async () => {
    const user = userEvent.setup();
    const props = filterProps();
    render(<DiaryFilterRow {...props} />);
    await user.click(screen.getByRole("combobox", { name: "Fee earner" }));
    expect(await screen.findByRole("option", { name: "Mine" })).toBeInTheDocument();
    await user.click(screen.getByRole("option", { name: "Thandi Nkosi" }));
    expect(props.onFeeEarnerChange).toHaveBeenCalledWith("fe-1");
  });

  it("selects a matter from the searchable combobox", async () => {
    const user = userEvent.setup();
    const props = filterProps();
    render(<DiaryFilterRow {...props} />);
    const trigger = screen.getByRole("combobox", { name: "Matter" });
    expect(trigger).toHaveTextContent("All matters");
    await user.click(trigger);
    await user.click(await screen.findByText("Dlamini v Road Accident Fund"));
    expect(props.onMatterChange).toHaveBeenCalledWith("m-1");
  });

  it("shows the selected matter name on the trigger", () => {
    render(<DiaryFilterRow {...filterProps({ matterId: "m-2" })} />);
    expect(screen.getByRole("combobox", { name: "Matter" })).toHaveTextContent("Zulu Estate");
  });
});
