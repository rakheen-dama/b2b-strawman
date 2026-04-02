import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SubscribeButton } from "./subscribe-button";

const mockSubscribe = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/billing/actions", () => ({
  subscribe: (...args: unknown[]) => mockSubscribe(...args),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/settings/billing",
}));

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

describe("SubscribeButton", () => {
  it("calls subscribe action on button click and creates form", async () => {
    const mockFormSubmit = vi.fn();
    // Mock document.createElement to intercept form creation
    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, "createElement").mockImplementation((tag: string) => {
      const el = originalCreateElement(tag);
      if (tag === "form") {
        el.submit = mockFormSubmit;
      }
      return el;
    });

    mockSubscribe.mockResolvedValue({
      paymentUrl: "https://sandbox.payfast.co.za/eng/process",
      formFields: {
        merchant_id: "10000100",
        merchant_key: "46f0cd694581a",
        amount: "499.00",
      },
    });

    const user = userEvent.setup();
    render(<SubscribeButton />);

    await user.click(screen.getByRole("button", { name: /subscribe/i }));

    await waitFor(() => {
      expect(mockSubscribe).toHaveBeenCalledOnce();
      expect(mockFormSubmit).toHaveBeenCalledOnce();
    });

    // Verify hidden inputs were added to the form
    const form = document.querySelector("form");
    expect(form).toBeTruthy();
    expect(form?.querySelector("input[name='merchant_id']")).toBeTruthy();
    expect(form?.querySelector("input[name='amount']")).toBeTruthy();

    vi.restoreAllMocks();
  });

  it("shows loading state during async call", async () => {
    mockSubscribe.mockImplementation(
      () =>
        new Promise((resolve) =>
          setTimeout(
            () =>
              resolve({
                paymentUrl: "https://sandbox.payfast.co.za/eng/process",
                formFields: {},
              }),
            500
          )
        )
    );

    const user = userEvent.setup();
    render(<SubscribeButton />);

    await user.click(screen.getByRole("button", { name: /subscribe/i }));

    expect(screen.getByText("Subscribing...")).toBeInTheDocument();
    expect(screen.getByRole("button")).toBeDisabled();
  });

  it("shows error message when subscribe fails", async () => {
    mockSubscribe.mockRejectedValue(new Error("Payment service unavailable"));

    const user = userEvent.setup();
    render(<SubscribeButton />);

    await user.click(screen.getByRole("button", { name: /subscribe/i }));

    await waitFor(() => {
      expect(
        screen.getByText("Payment service unavailable")
      ).toBeInTheDocument();
    });
  });
});
