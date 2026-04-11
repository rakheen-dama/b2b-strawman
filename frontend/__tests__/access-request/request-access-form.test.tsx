import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RequestAccessForm } from "@/components/access-request/request-access-form";

const mockSubmitAccessRequest = vi.fn();
const mockVerifyAccessRequestOtp = vi.fn();

vi.mock("@/app/request-access/actions", () => ({
  submitAccessRequest: (...args: unknown[]) => mockSubmitAccessRequest(...args),
  verifyAccessRequestOtp: (...args: unknown[]) => mockVerifyAccessRequestOtp(...args),
}));

/**
 * Fills text fields and submits the form. Skips Select interactions (Radix
 * Select dropdowns don't reliably render in happy-dom). The form handler
 * still fires; the server action mock controls the response.
 */
async function fillTextFieldsAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Work Email"), "jane@company.com");
  await user.type(screen.getByLabelText("Full Name"), "Jane Smith");
  await user.type(screen.getByLabelText("Organisation Name"), "Acme Corp");

  // The submit button is disabled because Select fields are empty (isFormValid
  // returns false). Use fireEvent.submit on the form to bypass the disabled
  // button and exercise the handleSubmit path directly.
  const form = screen.getByLabelText("Work Email").closest("form")!;
  fireEvent.submit(form);
}

describe("RequestAccessForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders Step 1 form fields", () => {
    render(<RequestAccessForm />);

    expect(screen.getByLabelText("Work Email")).toBeInTheDocument();
    expect(screen.getByLabelText("Full Name")).toBeInTheDocument();
    expect(screen.getByLabelText("Organisation Name")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Country" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Industry" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Request Access" })).toBeInTheDocument();
  });

  it("shows blocked domain error for gmail.com email", async () => {
    const user = userEvent.setup();
    render(<RequestAccessForm />);

    await user.type(screen.getByLabelText("Work Email"), "test@gmail.com");

    expect(screen.getByText("Please use a company email address.")).toBeInTheDocument();
  });

  it("shows OTP input after successful Step 1 submission", async () => {
    mockSubmitAccessRequest.mockResolvedValue({
      success: true,
      message: "Verification code sent.",
      expiresInMinutes: 10,
    });

    const user = userEvent.setup();
    render(<RequestAccessForm />);

    await fillTextFieldsAndSubmit(user);

    await waitFor(() => {
      expect(screen.getByLabelText("Verification Code")).toBeInTheDocument();
    });

    expect(screen.getByText("Check Your Email")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Verify" })).toBeDisabled();
  });

  it("shows success message after valid OTP verification", async () => {
    mockSubmitAccessRequest.mockResolvedValue({
      success: true,
      message: "Verification code sent.",
      expiresInMinutes: 10,
    });
    mockVerifyAccessRequestOtp.mockResolvedValue({
      success: true,
      message: "Email verified successfully",
    });

    const user = userEvent.setup();
    render(<RequestAccessForm />);

    await fillTextFieldsAndSubmit(user);

    await waitFor(() => {
      expect(screen.getByLabelText("Verification Code")).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText("Verification Code"), "123456");
    await user.click(screen.getByRole("button", { name: "Verify" }));

    await waitFor(() => {
      expect(screen.getByText("Request Submitted")).toBeInTheDocument();
    });

    expect(
      screen.getByText(/Your access request has been submitted for review/)
    ).toBeInTheDocument();
    expect(screen.getByText("Back to home")).toBeInTheDocument();
  });

  it("shows error message when OTP verification fails", async () => {
    mockSubmitAccessRequest.mockResolvedValue({
      success: true,
      message: "Verification code sent.",
      expiresInMinutes: 10,
    });
    mockVerifyAccessRequestOtp.mockResolvedValue({
      success: false,
      error: "The verification code is incorrect",
    });

    const user = userEvent.setup();
    render(<RequestAccessForm />);

    await fillTextFieldsAndSubmit(user);

    await waitFor(() => {
      expect(screen.getByLabelText("Verification Code")).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText("Verification Code"), "999999");
    await user.click(screen.getByRole("button", { name: "Verify" }));

    await waitFor(() => {
      expect(screen.getByText("The verification code is incorrect")).toBeInTheDocument();
    });
  });

  it("calls submitAccessRequest again when resend is clicked", async () => {
    mockSubmitAccessRequest.mockResolvedValue({
      success: true,
      message: "Verification code sent.",
      expiresInMinutes: 10,
    });

    const user = userEvent.setup();
    render(<RequestAccessForm />);

    await fillTextFieldsAndSubmit(user);

    await waitFor(() => {
      expect(screen.getByLabelText("Verification Code")).toBeInTheDocument();
    });

    expect(mockSubmitAccessRequest).toHaveBeenCalledTimes(1);

    // The "Resend code" link is visible below the timer
    const resendButton = screen.getByRole("button", { name: "Resend code" });
    await user.click(resendButton);

    await waitFor(() => {
      expect(mockSubmitAccessRequest).toHaveBeenCalledTimes(2);
    });
  });
});
