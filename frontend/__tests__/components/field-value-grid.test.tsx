import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { FieldValueGrid } from "@/components/setup/field-value-grid";
import type { FieldValueProps } from "@/components/setup/types";

function makeFields(
  overrides: Partial<FieldValueProps>[] = [],
): FieldValueProps[] {
  const defaults: FieldValueProps[] = [
    {
      name: "Company Reg",
      slug: "company-reg",
      value: "2026/123456/07",
      fieldType: "TEXT",
      required: true,
    },
    {
      name: "Tax Number",
      slug: "tax-number",
      value: null,
      fieldType: "TEXT",
      required: true,
    },
    {
      name: "Website",
      slug: "website",
      value: "https://example.com",
      fieldType: "URL",
      required: false,
    },
  ];
  return defaults.map((field, i) => ({ ...field, ...overrides[i] }));
}

describe("FieldValueGrid", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders field names and values", () => {
    render(<FieldValueGrid fields={makeFields()} />);

    expect(screen.getByText("Company Reg")).toBeInTheDocument();
    expect(screen.getByText("2026/123456/07")).toBeInTheDocument();
    expect(screen.getByText("Website")).toBeInTheDocument();
    expect(screen.getByText("https://example.com")).toBeInTheDocument();
  });

  it("shows 'Not set' for unfilled required fields", () => {
    render(<FieldValueGrid fields={makeFields()} />);

    expect(screen.getByText("Not set")).toBeInTheDocument();
  });

  it("renders edit link when editHref is provided", () => {
    render(<FieldValueGrid fields={makeFields()} editHref="/fields/edit" />);

    const editLink = screen.getByRole("link", { name: "Edit Fields" });
    expect(editLink).toHaveAttribute("href", "/fields/edit");
  });

  it("renders grouped fields under group headings", () => {
    const fields: FieldValueProps[] = [
      {
        name: "Company Reg",
        slug: "company-reg",
        value: "2026/123456/07",
        fieldType: "TEXT",
        required: true,
        groupId: "legal",
      },
      {
        name: "Tax Number",
        slug: "tax-number",
        value: "9876543210",
        fieldType: "TEXT",
        required: true,
        groupId: "legal",
      },
      {
        name: "Website",
        slug: "website",
        value: "https://example.com",
        fieldType: "URL",
        required: false,
      },
    ];

    render(
      <FieldValueGrid
        fields={fields}
        groups={[{ id: "legal", name: "Legal Info" }]}
      />,
    );

    expect(screen.getByText("Legal Info")).toBeInTheDocument();
    expect(screen.getByText("Other")).toBeInTheDocument();
    expect(screen.getByText("Company Reg")).toBeInTheDocument();
    expect(screen.getByText("Website")).toBeInTheDocument();
  });
});
