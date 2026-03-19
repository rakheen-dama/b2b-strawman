import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { OrgProfileProvider } from "@/lib/org-profile";
import { TerminologyProvider } from "@/lib/terminology";
import { ModuleGate } from "@/components/module-gate";
import { TrustBalanceCard } from "@/components/customers/trust-balance-card";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";
import { Scale, Gavel } from "lucide-react";

afterEach(() => {
  cleanup();
});

// -- Helper: renders content inside OrgProfileProvider with given modules --
function renderWithModules(
  enabledModules: string[],
  ui: React.ReactNode,
) {
  return render(
    <OrgProfileProvider
      verticalProfile="legal-za"
      enabledModules={enabledModules}
      terminologyNamespace="en-ZA-legal"
    >
      {ui}
    </OrgProfileProvider>,
  );
}

describe("Stub pages — trust accounting", () => {
  it("renders Coming Soon badge and description when trust_accounting module is enabled", () => {
    renderWithModules(["trust_accounting"], (
      <ModuleGate module="trust_accounting">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-3">
              <Scale className="size-5 text-slate-400" />
              <CardTitle>Trust Accounting</CardTitle>
              <Badge variant="neutral">Coming Soon</Badge>
            </div>
            <CardDescription>
              Manage client trust accounts, track deposits and withdrawals, and
              generate trust accounting reports in compliance with LSSA requirements.
            </CardDescription>
          </CardHeader>
        </Card>
      </ModuleGate>
    ));

    expect(screen.getByText("Coming Soon")).toBeInTheDocument();
    expect(screen.getByText("Trust Accounting")).toBeInTheDocument();
    expect(screen.getByText(/Manage client trust accounts/)).toBeInTheDocument();
  });
});

describe("Stub pages — court calendar", () => {
  it("renders Coming Soon badge and description when court_calendar module is enabled", () => {
    renderWithModules(["court_calendar"], (
      <ModuleGate module="court_calendar">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-3">
              <Gavel className="size-5 text-slate-400" />
              <CardTitle>Court Calendar</CardTitle>
              <Badge variant="neutral">Coming Soon</Badge>
            </div>
            <CardDescription>
              Track court dates, filing deadlines, and hearing schedules.
            </CardDescription>
          </CardHeader>
        </Card>
      </ModuleGate>
    ));

    expect(screen.getByText("Coming Soon")).toBeInTheDocument();
    expect(screen.getByText("Court Calendar")).toBeInTheDocument();
    expect(screen.getByText(/Track court dates/)).toBeInTheDocument();
  });
});

describe("Conditional trust balance card", () => {
  it("renders trust balance card when trust_accounting module is enabled", () => {
    renderWithModules(["trust_accounting"], <TrustBalanceCard />);

    expect(screen.getByText("Trust Balance")).toBeInTheDocument();
    expect(screen.getByText("Coming Soon")).toBeInTheDocument();
    expect(screen.getByText(/Trust Accounting module will display/)).toBeInTheDocument();
  });

  it("does NOT render trust balance card when trust_accounting module is disabled", () => {
    renderWithModules([], <TrustBalanceCard />);

    expect(screen.queryByText("Trust Balance")).not.toBeInTheDocument();
    expect(screen.queryByText(/Trust Accounting module will display/)).not.toBeInTheDocument();
  });
});

describe("Conditional conflict check section", () => {
  it("renders conflict check section when conflict_check module is enabled", () => {
    renderWithModules(["conflict_check"], (
      <ModuleGate module="conflict_check">
        <div>
          <p>Conflict Check</p>
          <Badge variant="neutral">Coming Soon</Badge>
          <p>Run a conflict of interest check before creating this matter.</p>
          <button disabled>Run Conflict Check</button>
        </div>
      </ModuleGate>
    ));

    expect(screen.getByText("Conflict Check")).toBeInTheDocument();
    expect(screen.getByText("Coming Soon")).toBeInTheDocument();
    expect(screen.getByText(/Run a conflict of interest check/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run Conflict Check" })).toBeDisabled();
  });

  it("does NOT render conflict check section when conflict_check module is disabled", () => {
    renderWithModules([], (
      <ModuleGate module="conflict_check">
        <div>
          <p>Conflict Check</p>
          <button disabled>Run Conflict Check</button>
        </div>
      </ModuleGate>
    ));

    expect(screen.queryByText("Conflict Check")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Run Conflict Check" })).not.toBeInTheDocument();
  });
});

// ---- 372.8: Trust accounting stub page with legal terminology ----

describe("372B: trust accounting stub page with legal-za terminology", () => {
  it("shows 'client' terminology in trust accounting description when legal-za profile is active", () => {
    // The TrustBalanceCard description contains "client" — verify legal-za terminology
    // (both TerminologyProvider and OrgProfileProvider set for legal-za context)
    render(
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["trust_accounting"]}
        terminologyNamespace="en-ZA-legal"
      >
        <TerminologyProvider verticalProfile="legal-za">
          <TrustBalanceCard />
        </TerminologyProvider>
      </OrgProfileProvider>,
    );

    // The TrustBalanceCard description text contains "client" (lowercase)
    // This verifies the card uses terminology consistent with legal-za
    expect(screen.getByText(/client/i)).toBeInTheDocument();
    expect(screen.getByText("Trust Balance")).toBeInTheDocument();
  });
});
