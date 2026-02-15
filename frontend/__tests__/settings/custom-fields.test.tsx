import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CustomFieldsContent } from "@/app/(app)/org/[slug]/settings/custom-fields/custom-fields-content";
import type {
  EntityType,
  FieldDefinitionResponse,
  FieldGroupResponse,
} from "@/lib/types";

const mockCreateFieldDefinition = vi.fn();
const mockUpdateFieldDefinition = vi.fn();
const mockDeleteFieldDefinition = vi.fn();
const mockCreateFieldGroup = vi.fn();
const mockUpdateFieldGroup = vi.fn();
const mockDeleteFieldGroup = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/custom-fields/actions",
  () => ({
    createFieldDefinitionAction: (...args: unknown[]) =>
      mockCreateFieldDefinition(...args),
    updateFieldDefinitionAction: (...args: unknown[]) =>
      mockUpdateFieldDefinition(...args),
    deleteFieldDefinitionAction: (...args: unknown[]) =>
      mockDeleteFieldDefinition(...args),
    createFieldGroupAction: (...args: unknown[]) =>
      mockCreateFieldGroup(...args),
    updateFieldGroupAction: (...args: unknown[]) =>
      mockUpdateFieldGroup(...args),
    deleteFieldGroupAction: (...args: unknown[]) =>
      mockDeleteFieldGroup(...args),
  }),
);

function makeFieldDefinitions(
  entityType: EntityType,
): FieldDefinitionResponse[] {
  if (entityType === "PROJECT") {
    return [
      {
        id: "fd-1",
        entityType: "PROJECT",
        name: "Budget Code",
        slug: "budget_code",
        fieldType: "TEXT",
        description: "Internal budget code",
        required: true,
        defaultValue: null,
        options: null,
        validation: { maxLength: 20 },
        sortOrder: 1,
        packId: null,
        packFieldKey: null,
        active: true,
        createdAt: "2025-01-15T10:00:00Z",
        updatedAt: "2025-01-15T10:00:00Z",
      },
      {
        id: "fd-2",
        entityType: "PROJECT",
        name: "Priority Level",
        slug: "priority_level",
        fieldType: "DROPDOWN",
        description: null,
        required: false,
        defaultValue: null,
        options: [
          { value: "low", label: "Low" },
          { value: "high", label: "High" },
        ],
        validation: null,
        sortOrder: 2,
        packId: "pack-1",
        packFieldKey: "priority_level",
        active: true,
        createdAt: "2025-01-15T10:00:00Z",
        updatedAt: "2025-01-15T10:00:00Z",
      },
    ];
  }
  return [];
}

function makeFieldGroups(entityType: EntityType): FieldGroupResponse[] {
  if (entityType === "PROJECT") {
    return [
      {
        id: "fg-1",
        entityType: "PROJECT",
        name: "Financial Info",
        slug: "financial_info",
        description: "Financial tracking fields",
        packId: null,
        sortOrder: 1,
        active: true,
        createdAt: "2025-01-15T10:00:00Z",
        updatedAt: "2025-01-15T10:00:00Z",
      },
    ];
  }
  return [];
}

function makeProps(canManage = true) {
  const fieldsByType: Record<EntityType, FieldDefinitionResponse[]> = {
    PROJECT: makeFieldDefinitions("PROJECT"),
    TASK: makeFieldDefinitions("TASK"),
    CUSTOMER: makeFieldDefinitions("CUSTOMER"),
  };
  const groupsByType: Record<EntityType, FieldGroupResponse[]> = {
    PROJECT: makeFieldGroups("PROJECT"),
    TASK: makeFieldGroups("TASK"),
    CUSTOMER: makeFieldGroups("CUSTOMER"),
  };
  return { slug: "acme", fieldsByType, groupsByType, canManage };
}

describe("CustomFieldsContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders tabs for 3 entity types", () => {
    render(<CustomFieldsContent {...makeProps()} />);

    expect(screen.getByRole("tab", { name: "Projects" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Tasks" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Customers" })).toBeInTheDocument();
  });

  it("displays field definitions in table", () => {
    render(<CustomFieldsContent {...makeProps()} />);

    expect(screen.getByText("Budget Code")).toBeInTheDocument();
    expect(screen.getByText("TEXT")).toBeInTheDocument();
    // "Required" appears as both column header and badge
    const requiredElements = screen.getAllByText("Required");
    expect(requiredElements.length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("Priority Level")).toBeInTheDocument();
    expect(screen.getByText("DROPDOWN")).toBeInTheDocument();
  });

  it("displays field groups as cards", () => {
    render(<CustomFieldsContent {...makeProps()} />);

    expect(screen.getByText("Financial Info")).toBeInTheDocument();
    expect(screen.getByText("Financial tracking fields")).toBeInTheDocument();
  });

  it("admin sees Add Field and Add Group buttons", () => {
    render(<CustomFieldsContent {...makeProps(true)} />);

    expect(
      screen.getByRole("button", { name: /Add Field/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Add Group/ }),
    ).toBeInTheDocument();
  });

  it("member does not see Add Field or Add Group buttons", () => {
    render(<CustomFieldsContent {...makeProps(false)} />);

    expect(screen.queryByRole("button", { name: /Add Field/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /Add Group/ })).toBeNull();
  });

  it("switches tabs and shows empty state", async () => {
    const user = userEvent.setup();
    render(<CustomFieldsContent {...makeProps()} />);

    // Click Tasks tab
    await user.click(screen.getByRole("tab", { name: "Tasks" }));

    expect(
      screen.getByText("No field definitions for tasks yet."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("No field groups for tasks yet."),
    ).toBeInTheDocument();
  });

  it("shows pack badge for pack-sourced fields", () => {
    render(<CustomFieldsContent {...makeProps()} />);

    // Priority Level has a packId
    const packBadges = screen.getAllByText("Pack");
    expect(packBadges.length).toBeGreaterThanOrEqual(1);
  });
});
