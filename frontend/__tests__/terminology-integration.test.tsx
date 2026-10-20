import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { vi } from "vitest";

// --- Mocks ---

vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/projects"),
}));

import { usePathname } from "next/navigation";
import { TerminologyProvider, useTerminology } from "@/lib/terminology";
import { Breadcrumbs } from "@/components/breadcrumbs";
import { TerminologyHeading } from "@/components/terminology-heading";
import { TerminologyText } from "@/components/terminology-text";

const mockUsePathname = vi.mocked(usePathname);

afterEach(() => {
  cleanup();
});

// ---- Test 2: Breadcrumbs ----

describe("364B: terminology in breadcrumbs", () => {
  it("renders Matters instead of Projects in breadcrumb for /matters route", () => {
    mockUsePathname.mockReturnValue("/org/test-org/matters");

    render(
      <TerminologyProvider verticalProfile="legal-za">
        <Breadcrumbs slug="test-org" />
      </TerminologyProvider>
    );

    expect(screen.getByText("Matters")).toBeInTheDocument();
    expect(screen.queryByText("Projects")).not.toBeInTheDocument();
  });

  it("renders Clients instead of Customers in breadcrumb for /clients route", () => {
    mockUsePathname.mockReturnValue("/org/test-org/clients");

    render(
      <TerminologyProvider verticalProfile="legal-za">
        <Breadcrumbs slug="test-org" />
      </TerminologyProvider>
    );

    expect(screen.getByText("Clients")).toBeInTheDocument();
    expect(screen.queryByText("Customers")).not.toBeInTheDocument();
  });
});

// ---- Test 3: Page heading via TerminologyHeading ----

describe("364B: terminology in page heading component", () => {
  it("renders Matters instead of Projects for term=Projects with legal-za profile", () => {
    render(
      <TerminologyProvider verticalProfile="legal-za">
        <h1>
          <TerminologyHeading term="Projects" />
        </h1>
      </TerminologyProvider>
    );

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Matters");
  });
});

// ---- Test 4: TerminologyHeading with count prop ----

describe("364B: terminology heading with count", () => {
  it("renders '3 matters' for count=3 with legal-za profile", () => {
    render(
      <TerminologyProvider verticalProfile="legal-za">
        <TerminologyHeading count={3} term="projects" singularTerm="project" />
      </TerminologyProvider>
    );

    expect(screen.getByText("3 matters")).toBeInTheDocument();
  });

  it("renders '1 matter' for count=1 with legal-za profile", () => {
    render(
      <TerminologyProvider verticalProfile="legal-za">
        <TerminologyHeading count={1} term="projects" singularTerm="project" />
      </TerminologyProvider>
    );

    expect(screen.getByText("1 matter")).toBeInTheDocument();
  });

  it("renders '3 projects' for count=3 with null profile", () => {
    render(
      <TerminologyProvider verticalProfile={null}>
        <TerminologyHeading count={3} term="projects" singularTerm="project" />
      </TerminologyProvider>
    );

    expect(screen.getByText("3 projects")).toBeInTheDocument();
  });
});

// ---- Test 5: TerminologyText with template placeholders ----

describe("364B: terminology text template replacement", () => {
  it("replaces {proposals} in template with 'engagement letters' for legal-za", () => {
    render(
      <TerminologyProvider verticalProfile="legal-za">
        <TerminologyText template="No {proposals} yet" />
      </TerminologyProvider>
    );

    expect(screen.getByText("No engagement letters yet")).toBeInTheDocument();
  });

  it("replaces {a proposal} with an article-adjusted 'an engagement letter' for legal-za", () => {
    // LZKC-003: copy that needs an indefinite article uses the {a term} token so the article
    // tracks the substituted noun ("a proposal" -> "an engagement letter").
    render(
      <TerminologyProvider verticalProfile="legal-za">
        <TerminologyText template="Create {a proposal} to get started." />
      </TerminologyProvider>
    );

    expect(screen.getByText("Create an engagement letter to get started.")).toBeInTheDocument();
  });

  it("passes through template unchanged with null profile", () => {
    render(
      <TerminologyProvider verticalProfile={null}>
        <TerminologyText template="No {proposals} yet" />
      </TerminologyProvider>
    );

    expect(screen.getByText("No proposals yet")).toBeInTheDocument();
  });
});

// ---- 372.6: Legal terminology overrides ----

describe("372B: legal-za terminology overrides", () => {
  it("t('Project') returns 'Matter' for legal-za profile", () => {
    function TestConsumer() {
      const { t } = useTerminology();
      return <span data-testid="result">{t("Project")}</span>;
    }

    render(
      <TerminologyProvider verticalProfile="legal-za">
        <TestConsumer />
      </TerminologyProvider>
    );

    expect(screen.getByTestId("result").textContent).toBe("Matter");
  });

  it("t('Customer') returns 'Client' for legal-za profile", () => {
    function TestConsumer() {
      const { t } = useTerminology();
      return <span data-testid="result">{t("Customer")}</span>;
    }

    render(
      <TerminologyProvider verticalProfile="legal-za">
        <TestConsumer />
      </TerminologyProvider>
    );

    expect(screen.getByTestId("result").textContent).toBe("Client");
  });

  it("t('Document') passes through unchanged for legal-za profile (mapping removed)", () => {
    function TestConsumer() {
      const { t } = useTerminology();
      return <span data-testid="result">{t("Document")}</span>;
    }

    render(
      <TerminologyProvider verticalProfile="legal-za">
        <TestConsumer />
      </TerminologyProvider>
    );

    expect(screen.getByTestId("result").textContent).toBe("Document");
  });
});

// ---- 465.3: Updated legal-za terminology integration tests ----

describe("465: legal-za terminology fixes", () => {
  it("t('Invoice') returns 'Fee Note' for legal-za profile", () => {
    function TestConsumer() {
      const { t } = useTerminology();
      return <span data-testid="result">{t("Invoice")}</span>;
    }

    render(
      <TerminologyProvider verticalProfile="legal-za">
        <TestConsumer />
      </TerminologyProvider>
    );

    expect(screen.getByTestId("result").textContent).toBe("Fee Note");
  });

  it("t('Task') returns 'Action Item' for legal-za profile", () => {
    function TestConsumer() {
      const { t } = useTerminology();
      return <span data-testid="result">{t("Task")}</span>;
    }

    render(
      <TerminologyProvider verticalProfile="legal-za">
        <TestConsumer />
      </TerminologyProvider>
    );

    expect(screen.getByTestId("result").textContent).toBe("Action Item");
  });
});
