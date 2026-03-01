import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { IntakeFieldsSection } from "@/components/customers/intake-fields-section";
import type { IntakeFieldGroup } from "@/components/prerequisite/types";

afterEach(() => {
  cleanup();
});

function makeField(
  overrides: Partial<IntakeFieldGroup["fields"][number]> = {},
): IntakeFieldGroup["fields"][number] {
  return {
    id: "field-1",
    name: "Test Field",
    slug: "test_field",
    fieldType: "TEXT",
    required: false,
    description: null,
    options: null,
    defaultValue: null,
    requiredForContexts: [],
    visibilityCondition: null,
    ...overrides,
  };
}

function makeGroup(
  overrides: Partial<IntakeFieldGroup> = {},
): IntakeFieldGroup {
  return {
    id: "group-1",
    name: "Billing Information",
    slug: "billing_info",
    fields: [makeField({ id: "f1", name: "VAT Number", slug: "vat_number", required: true })],
    ...overrides,
  };
}

describe("IntakeFieldsSection", () => {
  it("renders group headers and required fields", () => {
    const onChange = vi.fn();
    render(
      <IntakeFieldsSection
        groups={[makeGroup()]}
        values={{}}
        onChange={onChange}
      />,
    );

    expect(screen.getByText("Billing Information")).toBeInTheDocument();
    // Group with required fields starts expanded, so the field label is visible
    expect(screen.getByText("VAT Number")).toBeInTheDocument();
  });

  it("separates required and optional fields with collapsible section", () => {
    const onChange = vi.fn();
    const group = makeGroup({
      fields: [
        makeField({ id: "f1", name: "Tax ID", slug: "tax_id", required: true }),
        makeField({ id: "f2", name: "Notes", slug: "notes", required: false }),
      ],
    });

    render(
      <IntakeFieldsSection
        groups={[group]}
        values={{}}
        onChange={onChange}
      />,
    );

    // Required field visible immediately (group starts expanded with required fields)
    expect(screen.getByText("Tax ID")).toBeInTheDocument();

    // Optional field hidden behind "Additional Information" toggle
    expect(screen.queryByText("Notes")).not.toBeInTheDocument();
    expect(screen.getByText("Additional Information (1)")).toBeInTheDocument();

    // Click to expand optional fields
    fireEvent.click(screen.getByText("Additional Information (1)"));
    expect(screen.getByText("Notes")).toBeInTheDocument();
  });

  it("hides fields whose visibility condition evaluates to false", () => {
    const onChange = vi.fn();
    const group = makeGroup({
      fields: [
        makeField({
          id: "f1",
          name: "Entity Type",
          slug: "entity_type",
          required: true,
          fieldType: "DROPDOWN",
          options: [
            { value: "company", label: "Company" },
            { value: "individual", label: "Individual" },
          ],
        }),
        makeField({
          id: "f2",
          name: "Company Reg",
          slug: "company_reg",
          required: true,
          visibilityCondition: {
            dependsOnSlug: "entity_type",
            operator: "eq",
            value: "company",
          },
        }),
      ],
    });

    // entity_type = "individual" → company_reg should be hidden
    render(
      <IntakeFieldsSection
        groups={[group]}
        values={{ entity_type: "individual" }}
        onChange={onChange}
      />,
    );

    expect(screen.getByText("Entity Type")).toBeInTheDocument();
    expect(screen.queryByText("Company Reg")).not.toBeInTheDocument();
  });

  it("shows fields when neq visibility condition is satisfied", () => {
    const onChange = vi.fn();
    const group = makeGroup({
      fields: [
        makeField({
          id: "f1",
          name: "Entity Type",
          slug: "entity_type",
          required: true,
          fieldType: "DROPDOWN",
          options: [
            { value: "company", label: "Company" },
            { value: "individual", label: "Individual" },
          ],
        }),
        makeField({
          id: "f2",
          name: "Personal ID",
          slug: "personal_id",
          required: true,
          visibilityCondition: {
            dependsOnSlug: "entity_type",
            operator: "neq",
            value: "company",
          },
        }),
      ],
    });

    // entity_type = "individual" (neq "company") → personal_id should be visible
    render(
      <IntakeFieldsSection
        groups={[group]}
        values={{ entity_type: "individual" }}
        onChange={onChange}
      />,
    );

    expect(screen.getByText("Personal ID")).toBeInTheDocument();
  });

  it("hides fields when neq visibility condition is not satisfied", () => {
    const onChange = vi.fn();
    const group = makeGroup({
      fields: [
        makeField({
          id: "f1",
          name: "Entity Type",
          slug: "entity_type",
          required: true,
          fieldType: "DROPDOWN",
          options: [
            { value: "company", label: "Company" },
            { value: "individual", label: "Individual" },
          ],
        }),
        makeField({
          id: "f2",
          name: "Personal ID",
          slug: "personal_id",
          required: true,
          visibilityCondition: {
            dependsOnSlug: "entity_type",
            operator: "neq",
            value: "company",
          },
        }),
      ],
    });

    // entity_type = "company" (neq "company" is false) → personal_id should be hidden
    render(
      <IntakeFieldsSection
        groups={[group]}
        values={{ entity_type: "company" }}
        onChange={onChange}
      />,
    );

    expect(screen.queryByText("Personal ID")).not.toBeInTheDocument();
  });

  it("shows fields when in visibility condition is satisfied", () => {
    const onChange = vi.fn();
    const group = makeGroup({
      fields: [
        makeField({
          id: "f1",
          name: "Country",
          slug: "country",
          required: true,
          fieldType: "DROPDOWN",
          options: [
            { value: "ZA", label: "South Africa" },
            { value: "US", label: "United States" },
            { value: "UK", label: "United Kingdom" },
          ],
        }),
        makeField({
          id: "f2",
          name: "State/Province",
          slug: "state_province",
          required: true,
          visibilityCondition: {
            dependsOnSlug: "country",
            operator: "in",
            value: ["ZA", "US"],
          },
        }),
      ],
    });

    // country = "ZA" (in ["ZA", "US"]) → state_province should be visible
    render(
      <IntakeFieldsSection
        groups={[group]}
        values={{ country: "ZA" }}
        onChange={onChange}
      />,
    );

    expect(screen.getByText("State/Province")).toBeInTheDocument();
  });

  it("hides fields when in visibility condition is not satisfied", () => {
    const onChange = vi.fn();
    const group = makeGroup({
      fields: [
        makeField({
          id: "f1",
          name: "Country",
          slug: "country",
          required: true,
          fieldType: "DROPDOWN",
          options: [
            { value: "ZA", label: "South Africa" },
            { value: "US", label: "United States" },
            { value: "UK", label: "United Kingdom" },
          ],
        }),
        makeField({
          id: "f2",
          name: "State/Province",
          slug: "state_province",
          required: true,
          visibilityCondition: {
            dependsOnSlug: "country",
            operator: "in",
            value: ["ZA", "US"],
          },
        }),
      ],
    });

    // country = "UK" (not in ["ZA", "US"]) → state_province should be hidden
    render(
      <IntakeFieldsSection
        groups={[group]}
        values={{ country: "UK" }}
        onChange={onChange}
      />,
    );

    expect(screen.queryByText("State/Province")).not.toBeInTheDocument();
  });

  it("calls onChange with field slug and new value", () => {
    const onChange = vi.fn();
    const group = makeGroup({
      fields: [
        makeField({ id: "f1", name: "VAT Number", slug: "vat_number", required: true, fieldType: "TEXT" }),
      ],
    });

    render(
      <IntakeFieldsSection
        groups={[group]}
        values={{}}
        onChange={onChange}
      />,
    );

    const input = screen.getByRole("textbox");
    fireEvent.change(input, { target: { value: "GB123456789" } });
    expect(onChange).toHaveBeenCalledWith("vat_number", "GB123456789");
  });
});
