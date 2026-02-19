import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FileText } from "lucide-react";
import { EmptyState } from "@/components/setup/empty-state";

describe("SetupEmptyState", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders icon, title, and description", () => {
    render(
      <EmptyState
        icon={FileText}
        title="No documents yet"
        description="Upload or generate your first document."
      />,
    );

    expect(screen.getByText("No documents yet")).toBeInTheDocument();
    expect(
      screen.getByText("Upload or generate your first document."),
    ).toBeInTheDocument();
  });

  it("renders Link button when actionHref and actionLabel provided", () => {
    render(
      <EmptyState
        icon={FileText}
        title="No documents"
        description="Get started."
        actionLabel="Upload Document"
        actionHref="/documents/upload"
      />,
    );

    const link = screen.getByRole("link", { name: "Upload Document" });
    expect(link).toHaveAttribute("href", "/documents/upload");
  });

  it("renders callback button when onAction and actionLabel provided", async () => {
    const handleAction = vi.fn();
    const user = userEvent.setup();

    render(
      <EmptyState
        icon={FileText}
        title="No documents"
        description="Get started."
        actionLabel="Create Document"
        onAction={handleAction}
      />,
    );

    const button = screen.getByRole("button", { name: "Create Document" });
    await user.click(button);
    expect(handleAction).toHaveBeenCalledTimes(1);
  });

  it("renders no action area when no actionLabel", () => {
    render(
      <EmptyState
        icon={FileText}
        title="No documents"
        description="Nothing here yet."
      />,
    );

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
