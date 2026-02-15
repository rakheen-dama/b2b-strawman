import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CustomFieldBadges } from "@/components/field-definitions/CustomFieldBadges";
import type { FieldDefinitionResponse } from "@/lib/types";

function makeFieldDef(
  overrides: Partial<FieldDefinitionResponse>,
): FieldDefinitionResponse {
  return {
    id: "fd-1",
    entityType: "PROJECT",
    name: "Test",
    slug: "test",
    fieldType: "TEXT",
    description: null,
    required: false,
    defaultValue: null,
    options: null,
    validation: null,
    sortOrder: 0,
    packId: null,
    packFieldKey: null,
    active: true,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  };
}

const textField = makeFieldDef({ id: "fd-1", name: "Case Number", slug: "case_number" });
const boolField = makeFieldDef({ id: "fd-2", name: "Urgent", slug: "urgent", fieldType: "BOOLEAN" });
const dropdownField = makeFieldDef({
  id: "fd-3",
  name: "Court",
  slug: "court",
  fieldType: "DROPDOWN",
  options: [
    { value: "hc", label: "High Court" },
    { value: "mc", label: "Magistrate Court" },
  ],
});
const currencyField = makeFieldDef({
  id: "fd-4",
  name: "Budget",
  slug: "budget",
  fieldType: "CURRENCY",
});

const allFields = [textField, boolField, dropdownField, currencyField];

describe("CustomFieldBadges", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders text field as badge with name and value", () => {
    render(
      <CustomFieldBadges
        customFields={{ case_number: "2025/12345" }}
        fieldDefinitions={allFields}
      />,
    );

    expect(screen.getByText("Case Number:")).toBeInTheDocument();
    expect(screen.getByText("2025/12345")).toBeInTheDocument();
  });

  it("renders boolean field as Yes/No", () => {
    render(
      <CustomFieldBadges
        customFields={{ urgent: true }}
        fieldDefinitions={allFields}
      />,
    );

    expect(screen.getByText("Urgent:")).toBeInTheDocument();
    expect(screen.getByText("Yes")).toBeInTheDocument();
  });

  it("renders dropdown field with label instead of value", () => {
    render(
      <CustomFieldBadges
        customFields={{ court: "hc" }}
        fieldDefinitions={allFields}
      />,
    );

    expect(screen.getByText("Court:")).toBeInTheDocument();
    expect(screen.getByText("High Court")).toBeInTheDocument();
  });

  it("limits badges to maxFields", () => {
    render(
      <CustomFieldBadges
        customFields={{
          case_number: "2025/12345",
          urgent: true,
          court: "hc",
          budget: { amount: 1000, currency: "USD" },
        }}
        fieldDefinitions={allFields}
        maxFields={2}
      />,
    );

    const container = screen.getByTestId("custom-field-badges");
    // Should only show 2 badges
    const badges = container.querySelectorAll("[data-slot='badge']");
    expect(badges.length).toBe(2);
  });

  it("renders nothing when customFields is empty", () => {
    const { container } = render(
      <CustomFieldBadges
        customFields={{}}
        fieldDefinitions={allFields}
      />,
    );

    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when field definitions do not match", () => {
    const { container } = render(
      <CustomFieldBadges
        customFields={{ unknown_field: "value" }}
        fieldDefinitions={allFields}
      />,
    );

    expect(container.innerHTML).toBe("");
  });
});
