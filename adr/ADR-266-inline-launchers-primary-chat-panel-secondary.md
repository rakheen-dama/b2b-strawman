# ADR-266: Inline Launchers Are Primary; Chat Panel Is Secondary Generalist Fallback

**Status**: Accepted

**Context**:

Phase 52 shipped Kazi's first AI surface — a slide-out chat panel anchored to the top bar and accessible via `⌘K`. The panel is generic: any question about any entity, any tool the user has capability for, all in one conversational thread. Phase 52 hit its goal of "AI exists in the product," but observation of demo runs and founder feedback (2026-04-22 ideation) revealed a discoverability and adoption gap: the AI lives in a panel that the user has to remember exists, then opens, then must formulate a prompt for. The drudgery the AI is meant to eliminate (polishing time entries, extracting fields from intake docs, summarising matter activity) is at the entity page, not at the chat panel.

Phase 70 must decide where its three specialists (Billing, Intake, Inbox) appear in the UI. Three coherent options exist: keep them in the existing chat panel as "modes," replace the chat panel with inline buttons, or add inline buttons as the primary surface and retain the chat panel as a generalist fallback.

**Options Considered**:

1. **Specialists live inside the existing chat panel as switchable modes.** A dropdown at the top of the panel selects "Generalist / Billing / Intake / Inbox"; the panel re-themes and reseeds the system prompt accordingly.
   - Pros:
     - Single chat surface — no new component tree; fewer places to maintain.
     - Power users who already use the chat panel pick up specialists without learning a new surface.
   - Cons:
     - Discoverability is unchanged from Phase 52. The user still has to (a) know the AI exists, (b) open the panel, (c) pick a specialist mode, (d) formulate a prompt. The inline placement that the founder identified as the drudgery-removing innovation is absent.
     - Context binding is awkward — switching to "Billing mode" while the user is on a customer page leaves the specialist no entity to operate on. Specialists need a `contextRef` to be useful; a mode dropdown does not naturally carry one.
     - The specialist effectively competes with the generalist for the same screen real-estate. Users cannot have both open.

2. **Inline launchers replace the chat panel entirely.** Specialists live as buttons on relevant pages; the Phase 52 chat panel is removed.
   - Pros:
     - The drudgery surface is solved cleanly — every specialist appears where its work lives.
     - One surface metaphor; no overlap.
   - Cons:
     - The generalist use case ("how do I do X in Kazi?", "where is the billing run page?", "find the matter for Mrs Nkosi") has no home. The user with an open question that doesn't match a specialist's scope is stuck.
     - Phase 52 just shipped. Removing it after 8 weeks is a regression of investment and a confusing message to early adopters who built habits.
     - Tickets like "I had a quick question about my data" route to no surface, and the user falls back to support email.

3. **Inline launchers primary, chat panel secondary generalist fallback (CHOSEN).** Specialists are buttons on entity pages; the Phase 52 chat panel stays accessible via the top bar / `⌘K` for generalist questions. A specialist panel may, in v1+, suggest "let me hand you over to the generalist" for out-of-scope asks; the generalist may suggest a specialist hand-off for in-scope asks.
   - Pros:
     - The drudgery is at the work. A paralegal on an invoice draft sees "Polish with AI" and clicks once — no panel, no prompt-writing, just a focused tool with a pre-seeded session.
     - The generalist still exists for genuinely unstructured questions ("what does the audit log do?", "show me my profitability for Q1"). The user has a sane fallback.
     - The two surfaces compose: a specialist that hits a question outside its scope can point at the generalist; the generalist that detects a specialist-shaped task can suggest a hand-off.
     - Capability + plan gating works the same on both surfaces — `<CapabilityGate>` + `<PlanGate>` wrap the launcher button, and the `AssistantTrigger` is already gated.
   - Cons:
     - Two surface metaphors to maintain. Documentation and onboarding must explain both.
     - Slight UX overlap on entity pages where a specialist exists — both the inline button and the top-bar trigger are visible. Acceptable: they have different framings ("focused, pre-seeded, one task" vs "generalist, open prompt").

**Decision**: Option 3 — inline launchers primary, chat panel secondary. The Phase 52 chat panel is retained unchanged. Specialists are launched from page-specific buttons via a new `<SpecialistLauncherButton>` component that opens a docked specialist panel pre-seeded with `contextRef` + `initialPrompt`.

**Rationale**:

The founder's 2026-04-22 call was explicit: "drudgery removal only works when the AI is at the point of work, not at a `⌘K` shortcut." Specialist-on-entity-page is the unit of value; the chat panel is the unit of "general help when there's nothing better." Conflating them (Option 1) keeps the discoverability gap that Phase 70 is meant to close. Removing the generalist (Option 2) leaves users with open-ended questions stranded.

The two-surface design also matches the natural divergence of the two flows. A specialist invocation has a `contextRef` (the invoice, the customer, the matter), an `initialPrompt`, and a bounded scope; it ends after one proposed output. A generalist chat is a multi-turn open conversation with no entity binding. Different shapes, different surfaces. Forcing them into one surface (a "mode" dropdown) creates the worst of both — a specialist with no automatic context binding, and a generalist that has to be reset between modes.

The composability matters in v1+: the requirements doc notes that specialists "may hand off to generalist" via a link at the bottom of the panel. That hand-off only makes sense if the generalist still exists. Option 3 preserves that path. The reverse hand-off (generalist → specialist) is non-mandatory in v1 but the architecture allows it.

**Consequences**:

- Positive:
  - Three specialist launchers live on the pages where their work happens: invoice draft, customer create dialog, matter activity tab. Plus secondary surfaces (unbilled-time dialog, info-request review, customer detail prereq prompt) listed in the requirements.
  - The Phase 52 chat panel is unchanged. No regression. No removed habits.
  - Frontend reuse is high — `<SpecialistPanel>` wraps the existing `<AssistantPanel>` chat tree; the streaming, tool-card, and confirmation-flow components are shared.
  - The hand-off link at the bottom of the specialist panel is a low-cost UX affordance for out-of-scope questions; it preserves the user's task continuity.

- Negative:
  - Two AI surfaces in the product. Onboarding tour and docs must explain both. Acceptable: the specialist surfaces are entity-page-local and self-explanatory ("Polish with AI" on an invoice is intuitive); the generalist surface is the one that already exists.
  - Capability gating must be applied at both surfaces. The `<SpecialistLauncherButton>` wraps in `<CapabilityGate capability="AI_ASSISTANT_USE">` and `<PlanGate plan="PRO">`; the existing `AssistantTrigger` already does the equivalent. Two gates to keep in sync, not one.

- Neutral:
  - Placement decisions per specialist (which surfaces, what `ctaLabel`) are listed in `LauncherContext` records on each `Specialist` (per [ADR-265](ADR-265-specialist-as-prompt-tools-launcher-metadata.md)). The frontend reads launcher metadata from `GET /api/assistant/specialists` and renders only the buttons whose `surface` matches the current page.
  - The two-surface model leaves room for future specialists to opt out of the generalist (e.g. a Compliance specialist that genuinely should never be reached except via its launchers). The `automationCapable` and `launchers` fields on the record encode that.

- Related: [ADR-265](ADR-265-specialist-as-prompt-tools-launcher-metadata.md) (specialist registry shape), [ADR-200](ADR-200-llm-chat-provider-interface.md) (Phase 52 chat infrastructure that the panel reuses), [ADR-203](ADR-203-completable-future-confirmation.md) (write-tool confirmation flow that the specialist panel inherits), [ADR-267](ADR-267-human-approval-default-direct-mode-exception.md) (the approval discipline applied to specialist outputs).
