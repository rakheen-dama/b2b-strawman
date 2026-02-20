import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AssigneeSelector } from "@/components/tasks/assignee-selector";

const MEMBERS = [
  { id: "m1", name: "Alice Smith", email: "alice@example.com" },
  { id: "m2", name: "Bob Jones", email: "bob@example.com" },
];

describe("AssigneeSelector", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows Unassigned when currentAssigneeId is null", () => {
    render(
      <AssigneeSelector
        members={MEMBERS}
        currentAssigneeId={null}
        onAssigneeChange={vi.fn()}
      />,
    );

    expect(screen.getByRole("combobox")).toHaveTextContent("Unassigned");
  });

  it("shows the selected member name when currentAssigneeId is set", () => {
    render(
      <AssigneeSelector
        members={MEMBERS}
        currentAssigneeId="m1"
        onAssigneeChange={vi.fn()}
      />,
    );

    expect(screen.getByRole("combobox")).toHaveTextContent("Alice Smith");
  });

  it("renders members in the dropdown when opened", async () => {
    const user = userEvent.setup();
    render(
      <AssigneeSelector
        members={MEMBERS}
        currentAssigneeId={null}
        onAssigneeChange={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("combobox"));

    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
  });

  it("calls onAssigneeChange with member id when a member is selected", async () => {
    const onAssigneeChange = vi.fn();
    const user = userEvent.setup();
    render(
      <AssigneeSelector
        members={MEMBERS}
        currentAssigneeId={null}
        onAssigneeChange={onAssigneeChange}
      />,
    );

    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByText("Alice Smith"));

    await waitFor(() => {
      expect(onAssigneeChange).toHaveBeenCalledWith("m1");
    });
  });

  it("calls onAssigneeChange with null when Unassigned is selected", async () => {
    const onAssigneeChange = vi.fn();
    const user = userEvent.setup();
    render(
      <AssigneeSelector
        members={MEMBERS}
        currentAssigneeId="m1"
        onAssigneeChange={onAssigneeChange}
      />,
    );

    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByText("Unassigned"));

    await waitFor(() => {
      expect(onAssigneeChange).toHaveBeenCalledWith(null);
    });
  });
});
