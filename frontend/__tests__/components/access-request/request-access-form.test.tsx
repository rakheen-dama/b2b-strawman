import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RequestAccessForm } from "@/components/access-request/request-access-form";

const mockSubmitAccessRequest = vi.fn();

vi.mock("@/app/request-access/actions", () => ({
  submitAccessRequest: (...args: unknown[]) => mockSubmitAccessRequest(...args),
}));

describe("RequestAccessForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders all form fields", () => {
    render(<RequestAccessForm />);

    expect(screen.getByLabelText("Work Email")).toBeInTheDocument();
    expect(screen.getByLabelText("Full Name")).toBeInTheDocument();
    expect(screen.getByLabelText("Organisation Name")).toBeInTheDocument();
    expect(screen.getByText("Select a country")).toBeInTheDocument();
    expect(screen.getByText("Select an industry")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Request Access" })).toBeInTheDocument();
  });

  it("renders the card title and description", () => {
    render(<RequestAccessForm />);

    expect(
      screen.getByText("Fill in your details to request access to Kazi.")
    ).toBeInTheDocument();
    // "Request Access" appears as both heading and button
    expect(screen.getAllByText("Request Access")).toHaveLength(2);
  });

  it("shows blocked domain error for gmail", async () => {
    const user = userEvent.setup();
    render(<RequestAccessForm />);

    await user.type(screen.getByLabelText("Work Email"), "test@gmail.com");

    expect(screen.getByText("Please use a company email address.")).toBeInTheDocument();
  });

  it("shows blocked domain error for yahoo", async () => {
    const user = userEvent.setup();
    render(<RequestAccessForm />);

    await user.type(screen.getByLabelText("Work Email"), "test@yahoo.com");

    expect(screen.getByText("Please use a company email address.")).toBeInTheDocument();
  });

  it("shows blocked domain error for hotmail", async () => {
    const user = userEvent.setup();
    render(<RequestAccessForm />);

    await user.type(screen.getByLabelText("Work Email"), "user@hotmail.com");

    expect(screen.getByText("Please use a company email address.")).toBeInTheDocument();
  });

  it("does not show blocked domain error for company email", async () => {
    const user = userEvent.setup();
    render(<RequestAccessForm />);

    await user.type(screen.getByLabelText("Work Email"), "test@acme.com");

    expect(screen.queryByText("Please use a company email address.")).not.toBeInTheDocument();
  });

  it("clears blocked domain error when email is corrected", async () => {
    const user = userEvent.setup();
    render(<RequestAccessForm />);

    const emailInput = screen.getByLabelText("Work Email");
    await user.type(emailInput, "test@gmail.com");
    expect(screen.getByText("Please use a company email address.")).toBeInTheDocument();

    await user.clear(emailInput);
    await user.type(emailInput, "test@acme.com");
    expect(screen.queryByText("Please use a company email address.")).not.toBeInTheDocument();
  });

  it("sets aria-invalid on email input when domain is blocked", async () => {
    const user = userEvent.setup();
    render(<RequestAccessForm />);

    const emailInput = screen.getByLabelText("Work Email");
    await user.type(emailInput, "test@gmail.com");

    expect(emailInput).toHaveAttribute("aria-invalid", "true");
  });

  it("disables submit button when required fields are empty", () => {
    render(<RequestAccessForm />);

    const submitButton = screen.getByRole("button", { name: "Request Access" });
    expect(submitButton).toBeDisabled();
  });

  it("disables submit button when email is blocked domain", async () => {
    const user = userEvent.setup();
    render(<RequestAccessForm />);

    await user.type(screen.getByLabelText("Work Email"), "test@hotmail.com");
    await user.type(screen.getByLabelText("Full Name"), "Jane Smith");
    await user.type(screen.getByLabelText("Organisation Name"), "Acme Corp");

    const submitButton = screen.getByRole("button", { name: "Request Access" });
    expect(submitButton).toBeDisabled();
  });

  it("renders country and industry select comboboxes", () => {
    render(<RequestAccessForm />);

    const comboboxes = screen.getAllByRole("combobox");
    expect(comboboxes).toHaveLength(2);
  });
});
