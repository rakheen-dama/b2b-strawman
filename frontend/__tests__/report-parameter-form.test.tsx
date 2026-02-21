import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReportParameterForm } from "@/components/reports/report-parameter-form";
import type { ParameterSchema } from "@/lib/api/reports";

// Mock server-only (imported transitively via type imports)
vi.mock("server-only", () => ({}));

// Mock the entity options fetch action
const mockFetchEntityOptions = vi.fn();
vi.mock(
  "@/app/(app)/org/[slug]/reports/[reportSlug]/actions",
  () => ({
    fetchEntityOptionsAction: (...args: unknown[]) =>
      mockFetchEntityOptions(...args),
  }),
);

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

  beforeEach(() => {
    mockFetchEntityOptions.mockResolvedValue({
      data: [
        { id: "p-1", label: "Alpha Project" },
        { id: "p-2", label: "Beta Project" },
      ],
    });
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
    // Select trigger should be present (use name to avoid matching entity picker)
    expect(
      screen.getByRole("combobox", { name: /group by/i }),
    ).toBeInTheDocument();
  });

  it("renders an entity picker button for uuid params with entityType", () => {
    render(
      <ReportParameterForm
        schema={makeSchema()}
        onSubmit={vi.fn()}
        isLoading={false}
      />,
    );

    // Should render a picker button instead of a text input
    const pickerButton = document.getElementById("param-projectId");
    expect(pickerButton).not.toBeNull();
    expect(pickerButton!.tagName).toBe("BUTTON");
    expect(pickerButton!.textContent).toMatch(/select a project/i);
    // Should NOT have a text input with UUID placeholder
    expect(
      screen.queryByPlaceholderText(/project id \(uuid\)/i),
    ).not.toBeInTheDocument();
  });

  it("renders a text input for uuid params without entityType", () => {
    const schema = makeSchema({
      parameters: [
        {
          name: "someId",
          type: "uuid",
          label: "Some Entity",
          required: false,
        },
      ],
    });

    render(
      <ReportParameterForm
        schema={schema}
        onSubmit={vi.fn()}
        isLoading={false}
      />,
    );

    const uuidInput = screen.getByPlaceholderText(/entity id \(uuid\)/i);
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
    const trigger = screen.getByRole("combobox", { name: /group by/i });
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

    const pickerButton = document.getElementById("param-projectId");
    expect(pickerButton).toBeDisabled();

    const submitBtn = screen.getByRole("button", { name: /run report/i });
    expect(submitBtn).toBeDisabled();
  });

  it("renders different field types for mixed parameter schemas", () => {
    const mixedSchema = makeSchema({
      parameters: [
        {
          name: "startDate",
          type: "date",
          label: "Start Date",
          required: true,
        },
        {
          name: "customerId",
          type: "uuid",
          label: "Customer",
          required: false,
          entityType: "Customer",
        },
        {
          name: "rawId",
          type: "uuid",
          label: "Raw ID",
          required: false,
          // no entityType — should render text input
        },
        {
          name: "status",
          type: "enum",
          label: "Status",
          options: ["active", "archived"],
        },
      ],
    });

    render(
      <ReportParameterForm
        schema={mixedSchema}
        onSubmit={vi.fn()}
        isLoading={false}
      />,
    );

    // Date renders as date input
    expect(screen.getByLabelText(/start date/i)).toHaveAttribute("type", "date");

    // UUID with entityType renders as button (entity picker)
    const customerPicker = document.getElementById("param-customerId");
    expect(customerPicker).not.toBeNull();
    expect(customerPicker!.tagName).toBe("BUTTON");
    expect(customerPicker!.textContent).toMatch(/select a customer/i);

    // UUID without entityType renders as text input
    const rawInput = screen.getByPlaceholderText(/entity id \(uuid\)/i);
    expect(rawInput).toHaveAttribute("type", "text");

    // Enum renders as combobox (select)
    expect(
      screen.getByRole("combobox", { name: /status/i }),
    ).toBeInTheDocument();
  });
});
