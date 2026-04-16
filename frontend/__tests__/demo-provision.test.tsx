import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DemoProvisionForm } from "@/app/(app)/platform-admin/demo/demo-provision-form";

const mockProvisionDemo = vi.fn();

vi.mock("server-only", () => ({}));

vi.mock("@/app/(app)/platform-admin/demo/actions", () => ({
  provisionDemo: (...args: unknown[]) => mockProvisionDemo(...args),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

describe("DemoProvisionForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => cleanup());

  it("renders all form fields", () => {
    render(<DemoProvisionForm />);

    expect(screen.getByLabelText("Organization Name")).toBeInTheDocument();
    expect(screen.getByLabelText("Admin Email")).toBeInTheDocument();
    expect(screen.getByText("Generic")).toBeInTheDocument();
    expect(screen.getByText("Accounting")).toBeInTheDocument();
    expect(screen.getByText("Legal")).toBeInTheDocument();
    expect(screen.getByText("Seed Demo Data")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create Demo Tenant" })).toBeInTheDocument();
  });

  it("submits with valid data and calls action", async () => {
    const user = userEvent.setup();
    mockProvisionDemo.mockResolvedValue({
      success: true,
      data: {
        organizationId: "org-demo-123",
        organizationSlug: "demo-accounting-firm",
        organizationName: "Demo Accounting Firm",
        verticalProfile: "accounting-za",
        loginUrl: "https://app.example.com/sign-in",
        demoDataSeeded: true,
        adminNote: "Demo tenant created",
      },
    });

    render(<DemoProvisionForm />);

    await user.type(screen.getByLabelText("Organization Name"), "Demo Accounting Firm");
    await user.click(screen.getByRole("radio", { name: /Accounting/ }));
    await user.type(screen.getByLabelText("Admin Email"), "admin@example.com");

    await user.click(screen.getByRole("button", { name: "Create Demo Tenant" }));

    await waitFor(() => {
      expect(mockProvisionDemo).toHaveBeenCalledWith({
        organizationName: "Demo Accounting Firm",
        verticalProfile: "accounting-za",
        adminEmail: "admin@example.com",
        seedDemoData: true,
      });
    });
  });

  it("shows error for invalid email and does not call action", async () => {
    const user = userEvent.setup();

    const { container } = render(<DemoProvisionForm />);

    await user.type(screen.getByLabelText("Organization Name"), "Test Org");
    await user.type(screen.getByLabelText("Admin Email"), "not-an-email");

    // Submit the form directly to ensure form submission triggers
    const form = container.querySelector("form")!;
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await waitFor(() => {
      expect(screen.getByText("Invalid email address")).toBeInTheDocument();
    });

    expect(mockProvisionDemo).not.toHaveBeenCalled();
  });

  it("shows success state with org details and login URL", async () => {
    const user = userEvent.setup();
    mockProvisionDemo.mockResolvedValue({
      success: true,
      data: {
        organizationId: "org-demo-456",
        organizationSlug: "demo-legal-firm",
        organizationName: "Demo Legal Firm",
        verticalProfile: "legal-za",
        loginUrl: "https://app.example.com/sign-in",
        demoDataSeeded: false,
        adminNote: "Demo tenant created by admin",
      },
    });

    render(<DemoProvisionForm />);

    await user.type(screen.getByLabelText("Organization Name"), "Demo Legal Firm");
    await user.click(screen.getByRole("radio", { name: /Legal/ }));
    await user.type(screen.getByLabelText("Admin Email"), "legal@example.com");
    // Toggle seed data off
    await user.click(screen.getByRole("switch"));

    await user.click(screen.getByRole("button", { name: "Create Demo Tenant" }));

    await waitFor(() => {
      expect(screen.getByText("Demo Legal Firm")).toBeInTheDocument();
    });

    expect(screen.getByText("legal-za")).toBeInTheDocument();
    expect(screen.getByText("https://app.example.com/sign-in")).toBeInTheDocument();
    expect(screen.getByText("No")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create Another" })).toBeInTheDocument();
  });

  it("selects vertical profile radio and submits correct value", async () => {
    const user = userEvent.setup();
    mockProvisionDemo.mockResolvedValue({
      success: true,
      data: {
        organizationId: "org-demo-789",
        organizationSlug: "demo-accounting",
        organizationName: "Accounting Demo",
        verticalProfile: "accounting-za",
        loginUrl: "https://app.example.com/sign-in",
        demoDataSeeded: true,
        adminNote: "Created",
      },
    });

    render(<DemoProvisionForm />);

    // Default is consulting-generic — click Accounting radio
    const accountingRadio = screen.getByRole("radio", { name: /Accounting/ });
    await user.click(accountingRadio);
    expect(accountingRadio).toBeChecked();

    await user.type(screen.getByLabelText("Organization Name"), "Accounting Demo");
    await user.type(screen.getByLabelText("Admin Email"), "test@example.com");

    await user.click(screen.getByRole("button", { name: "Create Demo Tenant" }));

    await waitFor(() => {
      expect(mockProvisionDemo).toHaveBeenCalledWith(
        expect.objectContaining({
          verticalProfile: "accounting-za",
        })
      );
    });
  });
});
