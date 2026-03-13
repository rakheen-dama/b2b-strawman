# Phase 45 Ideation — In-App AI Assistant (BYOAK)
**Date**: 2026-03-12

## Lighthouse Domain
Universal across all verticals. Every professional services firm has non-technical staff who underutilize complex software. The assistant is the accessibility layer for 44 phases of functionality — it turns the power gap into a differentiator.

## Decision Rationale
Founder's insight: "There are a lot of tools and functionality. An AI that knows how to use the system and be of assistance to non-technical users." This reframes AI from "bolted-on chatbot" (which founder rejected in Feb 2026) to "system expert" — a fundamentally different value proposition. The Feb drudgery features (time narrative polish, line item grouping) remain valid but are Layer 3.

### Three-Layer AI Roadmap
1. **Layer 1 (this phase)**: BYOAK infra + conversational assistant with tool execution
2. **Layer 2 (future)**: Document intelligence — template composition, document decomposition, data extraction from uploads
3. **Layer 3 (future)**: Drudgery removal — time narrative polish, smart line item grouping (may become assistant commands)

### Key Design Choices
1. **BYOAK for now** — tenant brings own API key. No platform cost. Premium tier with platform-managed key when subs/tiers are revisited.
2. **Claude first, abstraction always** — provider-agnostic interface, Claude adapter only in v1. Strict no lock-in.
3. **Reversible actions only** — create and update, never delete/send/email. "Can the user undo this with one click?" is the line.
4. **Confirmation before write** — every write action shows a preview card, user must explicitly confirm.
5. **Static system guide** — markdown doc describing app features, refreshable via dev skill. Not runtime-generated.
6. **No drafting** — founder noted drafting (composing documents in-place) is harder; deferred to Layer 2.
7. **Session-scoped** — no server-side chat history for v1.

## Founder Preferences (Confirmed)
- BYOAK with future premium tier
- Claude first but no vendor lock-in
- Actions yes, but only reversible ones
- Static system guide with dev skill for refresh (acknowledged as separate from this phase)
- Document intelligence is Layer 2, drudgery features Layer 3

## Phase Roadmap (Updated)
- Phase 43: UX Quality Pass (done)
- Phase 44: Navigation Zones, Command Palette & Settings (done)
- **Phase 45: In-App AI Assistant — BYOAK** (spec written)
- Phase 46 (candidate): Document Intelligence — AI template composition, data extraction
- Phase 47 (candidate): AI Drudgery Removal — time narrative polish, smart line item grouping

## Estimated Scope
~5 epics, ~12-15 slices. Backend-heavy (provider abstraction, tool layer, SSE orchestration, key management) with significant frontend (chat panel, message rendering, confirmation flow, streaming).
