import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FolderOpen, Users, ClipboardList, Receipt } from "lucide-react";

vi.mock("server-only", () => ({}));

// Mock next/link as a simple anchor
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

import { EmptyState } from "@/components/empty-state";

describe("EmptyState", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders with onAction prop and calls handler on click", async () => {
    const user = userEvent.setup();
    const handleAction = vi.fn();

    render(
      <EmptyState
        icon={FolderOpen}
        title="No projects yet"
        description="Create your first project to get started."
        actionLabel="Create project"
        onAction={handleAction}
      />
    );

    expect(screen.getByText("No projects yet")).toBeInTheDocument();
    expect(screen.getByText("Create your first project to get started.")).toBeInTheDocument();

    const button = screen.getByRole("button", { name: "Create project" });
    await user.click(button);
    expect(handleAction).toHaveBeenCalledTimes(1);
  });

  it("renders secondaryLink with correct href and label", () => {
    render(
      <EmptyState
        icon={Users}
        title="No customers yet"
        description="Add your first customer."
        secondaryLink={{ label: "Learn more", href: "/help" }}
      />
    );

    const link = screen.getByText("Learn more");
    expect(link).toBeInTheDocument();
    expect(link.closest("a")).toHaveAttribute("href", "/help");
    expect(link).toHaveClass("text-teal-600");
  });

  it("applies max-w-md to description paragraph", () => {
    render(<EmptyState icon={FolderOpen} title="Test" description="A description" />);

    const description = screen.getByText("A description");
    expect(description).toHaveClass("max-w-md");
  });

  it("renders with actionLabel + actionHref as a link (backwards compatible)", () => {
    render(
      <EmptyState
        icon={ClipboardList}
        title="No tasks"
        description="No tasks assigned."
        actionLabel="Browse projects"
        actionHref="/projects"
      />
    );

    const link = screen.getByText("Browse projects");
    expect(link.closest("a")).toHaveAttribute("href", "/projects");
  });

  it("renders project-like empty state with action node", () => {
    render(
      <EmptyState
        icon={FolderOpen}
        title="No projects yet"
        description="Projects organise your work, documents, and time tracking. Create your first project to get started."
        action={<button type="button">New Project</button>}
      />
    );

    expect(screen.getByText("No projects yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Projects organise your work, documents, and time tracking. Create your first project to get started."
      )
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New Project" })).toBeInTheDocument();
  });

  it("renders customer-like empty state with catalog strings", () => {
    render(
      <EmptyState
        icon={Users}
        title="No customers yet"
        description="Customers represent the organisations you work with. Add your first customer to start managing relationships."
      />
    );

    expect(screen.getByText("No customers yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Customers represent the organisations you work with. Add your first customer to start managing relationships."
      )
    ).toBeInTheDocument();
  });

  it("renders admin description for projects when user is admin", () => {
    // Simulates admin role: uses the admin description from the catalog
    render(
      <EmptyState
        icon={FolderOpen}
        title="No projects yet"
        description="Projects organise your work, documents, and time tracking. Create your first project to get started."
        action={<button type="button">Create project</button>}
      />
    );

    expect(
      screen.getByText(
        "Projects organise your work, documents, and time tracking. Create your first project to get started."
      )
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create project" })).toBeInTheDocument();
  });

  it("renders member description for projects when user is member", () => {
    // Simulates member role: uses the member-specific description from the catalog
    render(
      <EmptyState
        icon={FolderOpen}
        title="No projects yet"
        description={`You\u2019re not on any projects yet.`}
        action={<button type="button">Create project</button>}
      />
    );

    expect(screen.getByText(/not on any projects yet/)).toBeInTheDocument();
  });

  it("renders member description for customers when user is member", () => {
    // Simulates member role: uses the member-specific description from the catalog
    render(
      <EmptyState
        icon={Users}
        title="No customers yet"
        description="No customers have been added yet."
      />
    );

    expect(screen.getByText("No customers have been added yet.")).toBeInTheDocument();
  });

  it("prefers onAction over actionHref when both provided", async () => {
    const user = userEvent.setup();
    const handleAction = vi.fn();

    render(
      <EmptyState
        icon={Receipt}
        title="Test"
        description="Test desc"
        actionLabel="Click me"
        actionHref="/fallback"
        onAction={handleAction}
      />
    );

    // Should render as a button (onAction), not a link
    const button = screen.getByRole("button", { name: "Click me" });
    expect(button).toBeInTheDocument();
    await user.click(button);
    expect(handleAction).toHaveBeenCalledTimes(1);

    // The link should NOT be rendered
    const links = screen.queryAllByRole("link");
    const fallbackLink = links.find((l) => l.getAttribute("href") === "/fallback");
    expect(fallbackLink).toBeUndefined();
  });

  it("default variant (explicit) keeps the 48px icon tile, h2 title, max-w-md and actions", () => {
    const { container } = render(
      <EmptyState
        variant="default"
        icon={FolderOpen}
        title="No matters yet"
        description="Open your first matter."
        actionLabel="New matter"
        actionHref="/matters/new"
      />
    );

    const root = container.querySelector('[data-slot="empty-state"]');
    expect(root).toHaveAttribute("data-variant", "default");
    expect(container.querySelector('[data-slot="empty-state-icon"]')).toHaveClass("size-12");
    expect(container.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByRole("heading", { level: 2, name: "No matters yet" })).toBeInTheDocument();
    expect(screen.getByText("Open your first matter.")).toHaveClass("max-w-md");
    expect(screen.getByRole("link", { name: "New matter" })).toHaveAttribute(
      "href",
      "/matters/new"
    );
  });

  it("compact variant keeps the h2 title, description and onAction button", async () => {
    const user = userEvent.setup();
    const handleAction = vi.fn();
    const { container } = render(
      <EmptyState
        variant="compact"
        icon={Receipt}
        title="No fee notes in this period"
        description="Change the range to see more."
        actionLabel="Reset range"
        onAction={handleAction}
      />
    );

    expect(container.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "compact"
    );
    expect(container.querySelector('[data-slot="empty-state-icon"]')).toHaveClass("size-10");
    expect(
      screen.getByRole("heading", { level: 2, name: "No fee notes in this period" })
    ).toBeInTheDocument();
    expect(screen.getByText("Change the range to see more.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Reset range" }));
    expect(handleAction).toHaveBeenCalledTimes(1);
  });

  it("inline variant renders one line with no heading, keeping actions and links", () => {
    const { container } = render(
      <EmptyState
        variant="inline"
        icon={ClipboardList}
        title="No tasks"
        description="Nothing assigned to this matter."
        action={<button type="button">Add task</button>}
        secondaryLink={{ label: "Task help", href: "https://docs.example.com/tasks" }}
      />
    );

    expect(container.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "inline"
    );
    expect(screen.queryByRole("heading")).toBeNull();
    expect(screen.getByText("No tasks")).toBeInTheDocument();
    expect(screen.getByText("Nothing assigned to this matter.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add task" })).toBeInTheDocument();
    const help = screen.getByRole("link", { name: "Task help" });
    expect(help).toHaveAttribute("target", "_blank");
    expect(help).toHaveAttribute("rel", "noopener noreferrer");
    expect(help).toHaveClass("text-teal-600");
  });
});
