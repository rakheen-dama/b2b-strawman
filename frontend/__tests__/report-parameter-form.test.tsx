import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReportParameterForm } from "@/components/reports/report-parameter-form";
import type { ParameterSchema } from "@/lib/api/reports";

// Mock server-only (imported transitively via type imports)
vi.mock("server-only", () => ({}));

function makeSchema(
  overrides: Partial<ParameterSchema> = {},
): ParameterSchema {
  return {
    parameters: [
      {
        name: "dateFrom",
        type: "date",
        label: "Date From",
        required: true,
      },
      {
        name: "groupBy",
        type: "enum",
        label: "Group By",
        required: false,
        options: ["member", "project", "customer"],
        default: "member",
      },
      {
        name: "projectId",
        type: "uuid",
        label: "Project",
        required: false,
        entityType: "Project",
      },
    ],
    ...overrides,
  };
}

describe("ReportParameterForm", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders a date input for type='date' parameters", () => {
    render(
      <ReportParameterForm
        schema={makeSchema()}
        onSubmit={vi.fn()}
        isLoading={false}
      />,
    );

    const dateInput = screen.getByLabelText(/date from/i);
    expect(dateInput).toBeInTheDocument();
    expect(dateInput).toHaveAttribute("type", "date");
  });

  it("renders a select trigger for type='enum' parameters", () => {
    render(
      <ReportParameterForm
        schema={makeSchema()}
        onSubmit={vi.fn()}
        isLoading={false}
      />,
    );

    expect(screen.getByText(/group by/i)).toBeInTheDocument();
    // Select trigger should be present
    expect(screen.getByRole("combobox")).toBeInTheDocument();
  });

  it("renders a text input for type='uuid' parameters", () => {
    render(
      <ReportParameterForm
        schema={makeSchema()}
        onSubmit={vi.fn()}
        isLoading={false}
      />,
    );

    const uuidInput = screen.getByPlaceholderText(/project id \(uuid\)/i);
    expect(uuidInput).toBeInTheDocument();
    expect(uuidInput).toHaveAttribute("type", "text");
  });

  it("shows error when required field is empty on submit", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(
      <ReportParameterForm
        schema={makeSchema()}
        onSubmit={onSubmit}
        isLoading={false}
      />,
    );

    await user.click(screen.getByRole("button", { name: /run report/i }));

    expect(
      screen.getByText(/date from is required/i),
    ).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("calls onSubmit with correct parameter map on valid submission", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(
      <ReportParameterForm
        schema={makeSchema()}
        onSubmit={onSubmit}
        isLoading={false}
      />,
    );

    const dateInput = screen.getByLabelText(/date from/i);
    await user.type(dateInput, "2026-01-01");

    await user.click(screen.getByRole("button", { name: /run report/i }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          dateFrom: "2026-01-01",
          groupBy: "member",
          projectId: "",
        }),
      );
    });
  });

  it("pre-selects enum default value", () => {
    render(
      <ReportParameterForm
        schema={makeSchema()}
        onSubmit={vi.fn()}
        isLoading={false}
      />,
    );

    // The select trigger should show the default value
    const trigger = screen.getByRole("combobox");
    expect(trigger).toHaveTextContent("member");
  });

  it("disables inputs and button when isLoading is true", () => {
    render(
      <ReportParameterForm
        schema={makeSchema()}
        onSubmit={vi.fn()}
        isLoading={true}
      />,
    );

    const dateInput = screen.getByLabelText(/date from/i);
    expect(dateInput).toBeDisabled();

    const submitBtn = screen.getByRole("button", { name: /run report/i });
    expect(submitBtn).toBeDisabled();
  });
});
