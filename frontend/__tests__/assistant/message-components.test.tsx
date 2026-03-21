import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { UserMessage } from "@/components/assistant/user-message";
import { AssistantMessage } from "@/components/assistant/assistant-message";
import { ToolUseCard } from "@/components/assistant/tool-use-card";
import { ConfirmationCard } from "@/components/assistant/confirmation-card";
import { TokenUsageBadge } from "@/components/assistant/token-usage-badge";
import { EmptyState } from "@/components/assistant/empty-state";

// react-markdown is ESM-only; mock it to avoid import issues in happy-dom
vi.mock("react-markdown", () => ({
  default: ({ children }: { children: string }) => <div>{children}</div>,
}));

afterEach(() => {
  cleanup();
});

describe("UserMessage", () => {
  it("renders user text right-aligned", () => {
    const message = { content: "Hello assistant" };
    const { container } = render(<UserMessage message={message} />);
    expect(screen.getByText("Hello assistant")).toBeInTheDocument();
    // Right-aligned: has ml-auto class
    const bubble = container.firstChild as HTMLElement;
    expect(bubble.className).toContain("ml-auto");
  });
});

describe("AssistantMessage", () => {
  it("renders markdown content", () => {
    const message = { content: "## Title\n- item 1\n- item 2" };
    render(<AssistantMessage message={message} />);
    // With mock, content is rendered as plain text inside a div
    expect(screen.getByText(/Title/)).toBeInTheDocument();
    expect(screen.getByText(/item 1/)).toBeInTheDocument();
  });
});

describe("ToolUseCard", () => {
  it("shows loading state with spinner text", () => {
    const message = {
      toolName: "list_projects",
      content: "{}",
      toolCallId: "tc_001",
    };
    render(<ToolUseCard message={message} isLoading={true} />);
    expect(screen.getByText(/Looking up/i)).toBeInTheDocument();
    expect(screen.getByText("list_projects")).toBeInTheDocument();
  });

  it("shows expand/collapse when result is available", () => {
    const message = {
      toolName: "list_projects",
      content: '{"status":"ACTIVE"}',
      toolCallId: "tc_001",
    };
    render(<ToolUseCard message={message} isLoading={false} />);
    expect(screen.getByText(/Looked up/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /expand result/i }),
    ).toBeInTheDocument();
  });
});

describe("ConfirmationCard", () => {
  it("renders action title and data preview", () => {
    const message = {
      toolCallId: "tc_002",
      toolName: "create_project",
      content: '{"name":"Alpha Project","customerId":"123"}',
    };
    const onConfirm = vi.fn();
    render(<ConfirmationCard message={message} onConfirm={onConfirm} />);
    expect(screen.getByText("Create Project")).toBeInTheDocument();
    expect(screen.getByText("name")).toBeInTheDocument();
    expect(screen.getByText("Alpha Project")).toBeInTheDocument();
  });

  it("Confirm button calls onConfirm with approved=true", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn().mockResolvedValueOnce(undefined);
    const message = {
      toolCallId: "tc_003",
      toolName: "create_project",
      content: '{"name":"Beta"}',
    };
    render(<ConfirmationCard message={message} onConfirm={onConfirm} />);
    await user.click(screen.getByRole("button", { name: /confirm/i }));
    expect(onConfirm).toHaveBeenCalledWith("tc_003", true);
  });

  it("Cancel button calls onConfirm with approved=false", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn().mockResolvedValueOnce(undefined);
    const message = {
      toolCallId: "tc_004",
      toolName: "update_task",
      content: '{"taskId":"456","status":"DONE"}',
    };
    render(<ConfirmationCard message={message} onConfirm={onConfirm} />);
    await user.click(screen.getByRole("button", { name: /cancel/i }));
    expect(onConfirm).toHaveBeenCalledWith("tc_004", false);
  });
});

describe("TokenUsageBadge", () => {
  it('formats "~1.2K tokens" for 1234 total tokens', () => {
    render(<TokenUsageBadge inputTokens={834} outputTokens={400} />);
    expect(screen.getByText(/~1\.2K tokens/i)).toBeInTheDocument();
  });
});

describe("EmptyState", () => {
  it("admin variant shows settings link", () => {
    render(<EmptyState orgRole="owner" slug="my-org" />);
    expect(screen.getByText(/AI not configured/i)).toBeInTheDocument();
    const link = screen.getByRole("link", {
      name: /Settings.*Integrations/i,
    });
    expect(link).toHaveAttribute("href", "/org/my-org/settings/integrations");
  });
});
