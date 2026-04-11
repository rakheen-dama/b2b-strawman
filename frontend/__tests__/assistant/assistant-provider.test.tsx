import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AssistantProvider } from "@/components/assistant/assistant-provider";
import { AssistantTrigger } from "@/components/assistant/assistant-trigger";

// Mock the Sheet/Panel to avoid Radix complexity in unit tests
vi.mock("@/components/assistant/assistant-panel", () => ({
  AssistantPanel: () => <div data-testid="assistant-panel">Panel</div>,
}));

vi.mock("@/hooks/use-assistant-chat", () => ({
  useAssistantChat: () => ({
    messages: [],
    isStreaming: false,
    tokenUsage: { input: 0, output: 0 },
    sendMessage: vi.fn(),
    stopStreaming: vi.fn(),
    confirmToolCall: vi.fn(),
  }),
}));

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

describe("AssistantProvider + AssistantTrigger", () => {
  it("shows trigger when aiEnabled is true", () => {
    render(
      <AssistantProvider aiEnabled={true}>
        <AssistantTrigger />
      </AssistantProvider>
    );

    expect(screen.getByLabelText("Open assistant")).toBeInTheDocument();
  });

  it("hides trigger when aiEnabled is false", () => {
    render(
      <AssistantProvider aiEnabled={false}>
        <AssistantTrigger />
      </AssistantProvider>
    );

    expect(screen.queryByLabelText("Open assistant")).not.toBeInTheDocument();
  });

  it("clicking trigger toggles panel open (trigger disappears)", async () => {
    const user = userEvent.setup();

    render(
      <AssistantProvider aiEnabled={true}>
        <AssistantTrigger />
      </AssistantProvider>
    );

    const trigger = screen.getByLabelText("Open assistant");
    expect(trigger).toBeInTheDocument();

    await user.click(trigger);

    // Trigger hides when panel is open
    expect(screen.queryByLabelText("Open assistant")).not.toBeInTheDocument();
  });
});
