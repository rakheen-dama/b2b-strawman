import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation — default: no unsubscribe query param
const mockPush = vi.fn();
const mockReplace = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  usePathname: () => "/settings/notifications",
  useSearchParams: () => mockSearchParams,
}));

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// Mock useAuth
vi.mock("@/hooks/use-auth", () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    jwt: "test-jwt",
    customer: {
      id: "cust-1",
      name: "Test Corp",
      email: "alice@test.com",
      orgId: "org_abc",
    },
    logout: vi.fn(),
  }),
}));

// Mock the notification-preferences API client
const mockGetPreferences = vi.fn();
const mockUpdatePreferences = vi.fn();
vi.mock("@/lib/api/notification-preferences", () => ({
  getPreferences: (...args: unknown[]) => mockGetPreferences(...args),
  updatePreferences: (...args: unknown[]) => mockUpdatePreferences(...args),
}));

import NotificationsPage from "@/app/(authenticated)/settings/notifications/page";

function defaultPrefs(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    digestEnabled: true,
    trustActivityEnabled: true,
    retainerUpdatesEnabled: true,
    deadlineRemindersEnabled: true,
    actionRequiredEnabled: true,
    firmDigestCadence: "WEEKLY" as const,
    ...overrides,
  };
}

describe("NotificationsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders toggle list reflecting server state", async () => {
    mockGetPreferences.mockResolvedValue(
      defaultPrefs({
        digestEnabled: true,
        trustActivityEnabled: false,
        retainerUpdatesEnabled: true,
        deadlineRemindersEnabled: false,
        actionRequiredEnabled: true,
      }),
    );

    render(<NotificationsPage />);

    const digest = await screen.findByRole("switch", { name: /Weekly digest/i });
    expect(digest).toBeChecked();

    const trust = screen.getByRole("switch", { name: /Trust activity/i });
    expect(trust).not.toBeChecked();

    const retainer = screen.getByRole("switch", {
      name: /Retainer updates/i,
    });
    expect(retainer).toBeChecked();

    const deadlines = screen.getByRole("switch", {
      name: /Deadline reminders/i,
    });
    expect(deadlines).not.toBeChecked();

    const action = screen.getByRole("switch", {
      name: /Action-required notifications/i,
    });
    expect(action).toBeChecked();

    expect(
      screen.getByText(/Your firm sends weekly digests/i),
    ).toBeInTheDocument();
  });

  it("Unsubscribe all sets every toggle to false and PUTs once", async () => {
    mockGetPreferences.mockResolvedValue(defaultPrefs());
    mockUpdatePreferences.mockResolvedValue(
      defaultPrefs({
        digestEnabled: false,
        trustActivityEnabled: false,
        retainerUpdatesEnabled: false,
        deadlineRemindersEnabled: false,
        actionRequiredEnabled: false,
      }),
    );

    const user = userEvent.setup();
    render(<NotificationsPage />);

    await screen.findByRole("switch", { name: /Weekly digest/i });

    await user.click(screen.getByRole("button", { name: /Unsubscribe all/i }));

    await waitFor(() => {
      expect(mockUpdatePreferences).toHaveBeenCalledTimes(1);
    });

    expect(mockUpdatePreferences).toHaveBeenCalledWith({
      digestEnabled: false,
      trustActivityEnabled: false,
      retainerUpdatesEnabled: false,
      deadlineRemindersEnabled: false,
      actionRequiredEnabled: false,
    });

    // Post-save success message confirms setPrefs + setSuccessMessage ran.
    await screen.findByText(
      /You have been unsubscribed from all notifications/i,
    );
  });

  it("?unsubscribe=1 auto-unsubscribes digest and shows confirmation banner", async () => {
    mockSearchParams = new URLSearchParams("unsubscribe=1");

    mockGetPreferences.mockResolvedValue(defaultPrefs());
    mockUpdatePreferences.mockResolvedValue(
      defaultPrefs({ digestEnabled: false }),
    );

    render(<NotificationsPage />);

    await waitFor(() => {
      expect(mockUpdatePreferences).toHaveBeenCalledTimes(1);
    });

    // Digest-only flip — other toggles preserved.
    expect(mockUpdatePreferences).toHaveBeenCalledWith({
      digestEnabled: false,
      trustActivityEnabled: true,
      retainerUpdatesEnabled: true,
      deadlineRemindersEnabled: true,
      actionRequiredEnabled: true,
    });

    const banner = await screen.findByRole("status");
    expect(
      within(banner).getByText(
        /You(?:'|’)ve been unsubscribed from weekly digests/i,
      ),
    ).toBeInTheDocument();
  });
});
