import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const mockMetadata = {
  groups: [
    {
      label: "Project",
      prefix: "project",
      variables: [
        { key: "project.name", label: "Project Name", type: "string" },
        { key: "project.description", label: "Description", type: "string" },
      ],
    },
    {
      label: "Customer",
      prefix: "customer",
      variables: [
        { key: "customer.name", label: "Customer Name", type: "string" },
        { key: "customer.email", label: "Customer Email", type: "string" },
      ],
    },
  ],
  loopSources: [],
};

vi.mock("./actions", () => ({
  fetchVariableMetadata: vi.fn(() => Promise.resolve(mockMetadata)),
}));

vi.mock("@/components/editor/actions", () => ({
  fetchVariableMetadata: vi.fn(() => Promise.resolve(mockMetadata)),
}));

vi.mock("motion/react", () => ({
  motion: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    div: ({ children, ...props }: any) => {
      const { initial, animate, transition, ...rest } = props;
      void initial;
      void animate;
      void transition;
      return <div {...rest}>{children}</div>;
    },
  },
}));

import { VariablePicker } from "@/components/editor/VariablePicker";

describe("VariablePicker", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders variable groups when open", async () => {
    render(
      <VariablePicker
        entityType="PROJECT"
        onSelect={vi.fn()}
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Insert Variable")).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText("Project")).toBeInTheDocument();
      expect(screen.getByText("Customer")).toBeInTheDocument();
    });
  });

  it("filters variables with search input", async () => {
    const user = userEvent.setup();

    render(
      <VariablePicker
        entityType="PROJECT"
        onSelect={vi.fn()}
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("project.name")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText("Search variables...");
    await user.type(searchInput, "email");

    await waitFor(() => {
      expect(screen.getByText("customer.email")).toBeInTheDocument();
      expect(screen.queryByText("project.name")).not.toBeInTheDocument();
    });
  });

  it("calls onSelect when a variable is clicked", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();

    render(
      <VariablePicker
        entityType="PROJECT"
        onSelect={onSelect}
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("project.name")).toBeInTheDocument();
    });

    await user.click(screen.getByText("project.name"));

    expect(onSelect).toHaveBeenCalledWith("project.name");
  });
});
