import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NotificationPreferencesForm } from "@/components/notifications/notification-preferences-form";
import type { NotificationPreference } from "@/lib/actions/notifications";

const mockUpdateNotificationPreferences = vi.fn();

vi.mock("@/lib/actions/notifications", () => ({
  updateNotificationPreferences: (...args: unknown[]) => mockUpdateNotificationPreferences(...args),
}));

function makePreferences(): NotificationPreference[] {
  return [
    { notificationType: "TASK_ASSIGNED", inAppEnabled: true, emailEnabled: false },
    { notificationType: "TASK_CLAIMED", inAppEnabled: true, emailEnabled: false },
    { notificationType: "TASK_UPDATED", inAppEnabled: true, emailEnabled: false },
    { notificationType: "COMMENT_ADDED", inAppEnabled: true, emailEnabled: false },
    { notificationType: "DOCUMENT_SHARED", inAppEnabled: true, emailEnabled: false },
    { notificationType: "MEMBER_INVITED", inAppEnabled: true, emailEnabled: false },
    { notificationType: "DOCUMENT_GENERATED", inAppEnabled: true, emailEnabled: false },
    { notificationType: "BUDGET_ALERT", inAppEnabled: true, emailEnabled: false },
    { notificationType: "INVOICE_APPROVED", inAppEnabled: true, emailEnabled: false },
    { notificationType: "INVOICE_SENT", inAppEnabled: true, emailEnabled: false },
    { notificationType: "INVOICE_PAID", inAppEnabled: true, emailEnabled: false },
    { notificationType: "INVOICE_VOIDED", inAppEnabled: true, emailEnabled: false },
  ];
}

describe("NotificationPreferencesForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders all 12 notification types with human-readable labels and toggles", () => {
    render(<NotificationPreferencesForm initialPreferences={makePreferences()} />);

    expect(screen.getByText("Task Assigned")).toBeInTheDocument();
    expect(screen.getByText("Task Claimed")).toBeInTheDocument();
    expect(screen.getByText("Task Updated")).toBeInTheDocument();
    expect(screen.getByText("Comment Added")).toBeInTheDocument();
    expect(screen.getByText("Document Shared")).toBeInTheDocument();
    expect(screen.getByText("Member Invited")).toBeInTheDocument();
    expect(screen.getByText("Document Generated")).toBeInTheDocument();
    expect(screen.getByText("Fee estimate alert")).toBeInTheDocument();
    expect(screen.getByText("Fee note approved")).toBeInTheDocument();
    expect(screen.getByText("Fee note sent")).toBeInTheDocument();
    expect(screen.getByText("Fee note paid")).toBeInTheDocument();
    expect(screen.getByText("Fee note voided")).toBeInTheDocument();

    // Should have 12 in-app switches + 12 email switches = 24 switches total
    const switches = screen.getAllByRole("switch");
    expect(switches).toHaveLength(24);
  });

  it("displays category headers for grouped notification types", () => {
    render(<NotificationPreferencesForm initialPreferences={makePreferences()} />);

    expect(screen.getByText("Tasks")).toBeInTheDocument();
    expect(screen.getByText("Collaboration")).toBeInTheDocument();
    expect(screen.getByText("Billing")).toBeInTheDocument();
  });

  it("email toggles are not disabled", () => {
    render(<NotificationPreferencesForm initialPreferences={makePreferences()} />);

    const switches = screen.getAllByRole("switch");
    // All switches should be enabled (not disabled)
    switches.forEach((switchEl) => {
      expect(switchEl).not.toBeDisabled();
    });
  });

  it("toggling an in-app switch and saving updates preferences", async () => {
    const preferences = makePreferences();
    const updatedPreferences = preferences.map((p) =>
      p.notificationType === "TASK_ASSIGNED" ? { ...p, inAppEnabled: false } : p
    );

    mockUpdateNotificationPreferences.mockResolvedValue({
      preferences: updatedPreferences,
    });

    render(<NotificationPreferencesForm initialPreferences={preferences} />);

    const user = userEvent.setup();

    // Toggle off the first in-app switch (Task Assigned)
    const taskAssignedSwitch = screen.getByRole("switch", {
      name: "Task Assigned",
    });
    await user.click(taskAssignedSwitch);

    // Click save
    const saveButton = screen.getByRole("button", {
      name: /save preferences/i,
    });
    await user.click(saveButton);

    await waitFor(() => {
      expect(mockUpdateNotificationPreferences).toHaveBeenCalledWith(
        expect.arrayContaining([
          expect.objectContaining({
            notificationType: "TASK_ASSIGNED",
            inAppEnabled: false,
          }),
        ])
      );
    });

    // Success message appears
    await waitFor(() => {
      expect(screen.getByText("Preferences saved successfully.")).toBeInTheDocument();
    });
  });

  it("toggling an email switch and saving sends correct payload", async () => {
    const preferences = makePreferences();
    const updatedPreferences = preferences.map((p) =>
      p.notificationType === "COMMENT_ADDED" ? { ...p, emailEnabled: true } : p
    );

    mockUpdateNotificationPreferences.mockResolvedValue({
      preferences: updatedPreferences,
    });

    render(<NotificationPreferencesForm initialPreferences={preferences} />);

    const user = userEvent.setup();

    // Find the email switch for COMMENT_ADDED
    // Email switches have ids like email-COMMENT_ADDED
    const emailSwitch = document.getElementById("email-COMMENT_ADDED");
    expect(emailSwitch).toBeTruthy();
    await user.click(emailSwitch!);

    // Click save
    const saveButton = screen.getByRole("button", {
      name: /save preferences/i,
    });
    await user.click(saveButton);

    await waitFor(() => {
      expect(mockUpdateNotificationPreferences).toHaveBeenCalledWith(
        expect.arrayContaining([
          expect.objectContaining({
            notificationType: "COMMENT_ADDED",
            emailEnabled: true,
          }),
        ])
      );
    });
  });
});
