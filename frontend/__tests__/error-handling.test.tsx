import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ back: vi.fn() }),
}));

import { classifyError } from "@/lib/error-handler";
import { ErrorBoundary } from "@/components/error-boundary";

describe("classifyError", () => {
  afterEach(() => {
    cleanup();
  });

  it("classifies 400 as validation", () => {
    const result = classifyError({ status: 400 });
    expect(result.category).toBe("validation");
    expect(result.retryable).toBe(false);
    expect(result.messageCode).toBe("api.validation");
  });

  it("classifies 403 as forbidden", () => {
    const result = classifyError({ status: 403 });
    expect(result.category).toBe("forbidden");
    expect(result.retryable).toBe(false);
    expect(result.action).toBe("contactAdmin");
  });

  it("classifies 404 as notFound", () => {
    const result = classifyError({ status: 404 });
    expect(result.category).toBe("notFound");
    expect(result.retryable).toBe(false);
    expect(result.action).toBe("goBack");
  });

  it("classifies 409 as conflict", () => {
    const result = classifyError({ status: 409 });
    expect(result.category).toBe("conflict");
    expect(result.retryable).toBe(true);
    expect(result.action).toBe("refresh");
  });

  it("classifies 500 as serverError", () => {
    const result = classifyError({ status: 500 });
    expect(result.category).toBe("serverError");
    expect(result.retryable).toBe(true);
    expect(result.action).toBe("retry");
  });

  it("classifies plain Error as networkError", () => {
    const result = classifyError(new Error("connection failed"));
    expect(result.category).toBe("networkError");
    expect(result.retryable).toBe(true);
    expect(result.action).toBe("retry");
  });
});

describe("ErrorBoundary", () => {
  afterEach(() => {
    cleanup();
  });

  function ThrowingComponent(): React.ReactNode {
    throw new Error("Test render error");
  }

  it("catches render error and shows fallback", () => {
    // Suppress React's console.error for error boundary
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});

    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>
    );

    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
    expect(
      screen.getByText(
        "An unexpected error occurred while loading this page. Try refreshing, or go back and try again."
      )
    ).toBeInTheDocument();

    spy.mockRestore();
  });

  it("resets and re-renders children when Try again is clicked", async () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    const user = userEvent.setup();

    let shouldThrow = true;

    function ConditionalThrow(): React.ReactNode {
      if (shouldThrow) {
        throw new Error("Test render error");
      }
      return <div>Recovered content</div>;
    }

    render(
      <ErrorBoundary>
        <ConditionalThrow />
      </ErrorBoundary>
    );

    expect(screen.getByText("Something went wrong")).toBeInTheDocument();

    // Stop throwing before reset
    shouldThrow = false;

    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(screen.getByText("Recovered content")).toBeInTheDocument();

    spy.mockRestore();
  });
});
