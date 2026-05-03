import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AssistantProvider } from "@/components/assistant/assistant-provider";

// Mock the chat hook before component import
const sendMessageMock = vi.fn();
const stopStreamingMock = vi.fn();
const confirmToolCallMock = vi.fn();
let chatMessages: Array<{
  id: string;
  role: "user" | "assistant";
  content: string;
}> = [];

vi.mock("@/hooks/use-assistant-chat", () => ({
  useAssistantChat: () => ({
    messages: chatMessages,
    isStreaming: false,
    tokenUsage: { input: 0, output: 0 },
    sendMessage: sendMessageMock,
    stopStreaming: stopStreamingMock,
    confirmToolCall: confirmToolCallMock,
  }),
}));

import {
  SpecialistPanel,
  buildHandOffSummary,
} from "@/components/assistant/specialist-panel";
import type { SessionHandle } from "@/lib/api/assistant-specialists";

const baseHandle: SessionHandle = {
  sessionId: "11111111-1111-1111-1111-111111111111",
  specialistId: "invoice-drafter",
  systemPromptHash: "deadbeef",
  toolIds: ["draft_invoice"],
  displayName: "Invoice Drafter",
  preSeededAssistantMessage: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  chatMessages = [];
});

afterEach(() => {
  cleanup();
  // Clean window state set by hand-off path
  if (typeof window !== "undefined") {
    delete (window as unknown as { __kaziHandOffSummary?: string }).__kaziHandOffSummary;
  }
});

describe("SpecialistPanel", () => {
  it("pre-seeds the chat stream with initialPrompt when opened", async () => {
    render(
      <AssistantProvider aiEnabled={true}>
        <SpecialistPanel
          open={true}
          onOpenChange={vi.fn()}
          sessionHandle={baseHandle}
          initialPrompt="Draft an invoice for project X"
          contextRef={{ entityType: "INVOICE", entityId: "i-1" }}
        />
      </AssistantProvider>
    );

    await waitFor(() =>
      expect(sendMessageMock).toHaveBeenCalledWith("Draft an invoice for project X")
    );
    // Header renders the specialist's display name
    expect(screen.getByText("Invoice Drafter")).toBeInTheDocument();
  });

  it("hand-off forwards a <=500-char transcript summary to the generalist via window bus", async () => {
    const user = userEvent.setup();
    chatMessages = [
      { id: "u1", role: "user", content: "Help me draft an invoice." },
      { id: "a1", role: "assistant", content: "Sure — what amount?" },
      { id: "u2", role: "user", content: "$500" },
    ];
    const onOpenChange = vi.fn();

    render(
      <AssistantProvider aiEnabled={true}>
        <SpecialistPanel
          open={true}
          onOpenChange={onOpenChange}
          sessionHandle={baseHandle}
          contextRef={{ entityType: "INVOICE", entityId: "i-1" }}
        />
      </AssistantProvider>
    );

    const handOff = screen.getByRole("button", { name: /hand off to generalist/i });
    await user.click(handOff);

    expect(onOpenChange).toHaveBeenCalledWith(false);
    const summary = (window as unknown as { __kaziHandOffSummary?: string })
      .__kaziHandOffSummary;
    expect(summary).toBeDefined();
    expect(summary!.length).toBeLessThanOrEqual(500);
    expect(summary).toContain("user: Help me draft an invoice.");
    expect(summary).toContain("assistant: Sure");
  });
});

describe("buildHandOffSummary", () => {
  it("truncates transcripts longer than 500 chars", () => {
    const longContent = "x".repeat(2000);
    const summary = buildHandOffSummary([
      { id: "1", role: "user", content: longContent },
    ]);
    expect(summary.length).toBe(500);
  });

  it("returns empty string for empty transcript", () => {
    expect(buildHandOffSummary([])).toBe("");
  });

  it("excludes tool_use / tool_result / error roles", () => {
    const summary = buildHandOffSummary([
      { id: "1", role: "user", content: "hi" },
      { id: "2", role: "tool_use", content: "{}" },
      { id: "3", role: "assistant", content: "hello" },
      { id: "4", role: "error", content: "boom" },
    ]);
    expect(summary).toBe("user: hi\nassistant: hello");
  });
});
