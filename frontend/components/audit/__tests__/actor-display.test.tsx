import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import { ActorDisplay, FORMER_MEMBER_PREFIX } from "../actor-display";

afterEach(() => {
  cleanup();
});

describe("<ActorDisplay>", () => {
  it("renders the actor display name", () => {
    render(
      <ActorDisplay
        actorDisplayName="Alice Smith"
        actorId="user-1"
        actorType="USER"
        source="API"
        ipAddress="10.0.0.1"
      />
    );
    const node = screen.getByTestId("actor-display");
    expect(node).toHaveTextContent("Alice Smith");
    expect(node.className).not.toContain("line-through");
  });

  it("strikes through display names that start with the former-member prefix", () => {
    render(
      <ActorDisplay
        actorDisplayName={`${FORMER_MEMBER_PREFIX} (deleted)`}
        actorId="user-2"
        actorType="USER"
        source={null}
        ipAddress={null}
      />
    );
    const node = screen.getByTestId("actor-display");
    expect(node.className).toContain("line-through");
  });

  it("only renders tooltip rows for non-null fields (smoke check on dom presence)", () => {
    // Tooltip content is portalled and only shown on hover; we instead
    // assert the trigger renders and our component branches on null inputs
    // without throwing. The visible name should still be present.
    render(
      <ActorDisplay
        actorDisplayName="System"
        actorId={null}
        actorType={null}
        source={null}
        ipAddress={null}
      />
    );
    expect(screen.getByTestId("actor-display")).toHaveTextContent("System");
  });
});
