import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TerminologyProvider } from "@/lib/terminology";
import { TerminologyText } from "@/components/terminology-text";

afterEach(() => {
  cleanup();
});

function withProfile(profile: string | null, ui: React.ReactElement) {
  return render(<TerminologyProvider verticalProfile={profile}>{ui}</TerminologyProvider>);
}

// ---- LZKC-003: indefinite-article token in TerminologyText ----

describe("LZKC-003: TerminologyText {a term} article token", () => {
  it("renders 'an engagement letter' for legal-za (vowel-initial substitution)", () => {
    withProfile(
      "legal-za",
      <TerminologyText template="Create {a proposal} for a client engagement." />
    );
    expect(screen.getByText("Create an engagement letter for a client engagement.")).toBeTruthy();
  });

  it("renders 'a proposal' with no profile", () => {
    withProfile(null, <TerminologyText template="Create {a proposal} for a client engagement." />);
    expect(screen.getByText("Create a proposal for a client engagement.")).toBeTruthy();
  });

  it("keeps 'a' for consonant-initial substitutions (legal-za project -> matter)", () => {
    withProfile("legal-za", <TerminologyText template="Add {a project} first." />);
    expect(screen.getByText("Add a matter first.")).toBeTruthy();
  });

  it("still substitutes plain tokens without an article", () => {
    withProfile("legal-za", <TerminologyText template="No {proposals} yet" />);
    expect(screen.getByText("No engagement letters yet")).toBeTruthy();
  });
});
