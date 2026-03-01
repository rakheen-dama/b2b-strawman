import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import {
  InlineFieldEditor,
  type InlineFieldEditorField,
} from "@/components/prerequisite/inline-field-editor";

afterEach(() => {
  cleanup();
});

function makeField(
  overrides: Partial<InlineFieldEditorField> = {},
): InlineFieldEditorField {
  return {
    id: "field-1",
    name: "Test Field",
    slug: "test_field",
    fieldType: "TEXT",
    description: null,
    required: false,
    options: null,
    ...overrides,
  };
}

describe("InlineFieldEditor", () => {
  it("renders a text input for TEXT field type", () => {
    const onChange = vi.fn();
    render(
      <InlineFieldEditor
        fieldDefinition={makeField({ fieldType: "TEXT" })}
        value=""
        onChange={onChange}
      />,
    );

    const input = screen.getByRole("textbox");
    expect(input).toBeInTheDocument();
  });

  it("renders a date input for DATE field type", () => {
    const onChange = vi.fn();
    render(
      <InlineFieldEditor
        fieldDefinition={makeField({
          fieldType: "DATE",
          slug: "date_field",
        })}
        value=""
        onChange={onChange}
      />,
    );

    const input = document.getElementById("inline-date_field");
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute("type", "date");
  });

  it("calls onChange with new value when input changes", () => {
    const onChange = vi.fn();
    render(
      <InlineFieldEditor
        fieldDefinition={makeField({ fieldType: "TEXT" })}
        value=""
        onChange={onChange}
      />,
    );

    const input = screen.getByRole("textbox");
    fireEvent.change(input, { target: { value: "hello" } });

    expect(onChange).toHaveBeenCalledWith("hello");
  });
});
