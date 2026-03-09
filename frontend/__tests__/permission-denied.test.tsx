import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ back: vi.fn() }),
}));

import { PermissionDenied } from "@/components/permission-denied";
import { scrollToFirstError } from "@/lib/error-handler";

describe("PermissionDenied", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders ShieldOff icon and heading", () => {
    render(<PermissionDenied />);

    expect(
      screen.getByText("You don't have access to this feature"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Contact your organisation admin to update your role.",
      ),
    ).toBeInTheDocument();
  });

  it("renders dashboard link", () => {
    render(<PermissionDenied />);

    const link = screen.getByRole("link", { name: "Go to dashboard" });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute("href", "../dashboard");
  });

  it("renders custom feature name in message", () => {
    render(<PermissionDenied featureName="invoices" />);

    expect(
      screen.getByText("You don't have access to invoices"),
    ).toBeInTheDocument();
  });

  it("renders custom dashboard href", () => {
    render(<PermissionDenied dashboardHref="/org/test/dashboard" />);

    const link = screen.getByRole("link", { name: "Go to dashboard" });
    expect(link).toHaveAttribute("href", "/org/test/dashboard");
  });

  it("uses centred layout matching EmptyState visual language", () => {
    const { container } = render(<PermissionDenied />);

    const wrapper = container.firstElementChild;
    expect(wrapper).toHaveClass(
      "flex",
      "flex-col",
      "items-center",
      "py-24",
      "text-center",
      "gap-4",
    );
  });
});

describe("scrollToFirstError", () => {
  afterEach(() => {
    cleanup();
  });

  it("finds and focuses invalid field with aria-invalid", () => {
    render(
      <div>
        <input data-testid="valid-field" />
        <input data-testid="invalid-field" aria-invalid="true" />
      </div>,
    );

    const invalidField = screen.getByTestId("invalid-field");
    const scrollSpy = vi.fn();
    invalidField.scrollIntoView = scrollSpy;
    invalidField.focus = vi.fn();

    scrollToFirstError();

    expect(scrollSpy).toHaveBeenCalledWith({
      behavior: "smooth",
      block: "center",
    });
    expect(invalidField.focus).toHaveBeenCalled();
  });

  it("does nothing when no invalid fields exist", () => {
    render(
      <div>
        <input data-testid="valid-field" />
      </div>,
    );

    // Should not throw
    scrollToFirstError();
  });
});
