# Phase 52 Ideation — In-App AI Assistant (BYOAK)
**Date**: 2026-03-20

## Lighthouse Domain
Universal across all verticals. The assistant is the accessibility layer for 51 phases of functionality — it makes the product's depth a differentiator rather than a barrier for non-technical users at professional services firms.

## Decision Rationale
After completing Phase 51 (Accounting Practice Essentials), founder asked what's next. With 51 phases built and pilot 1-2 months away, the AI assistant was chosen over going deeper on accounting or starting legal vertical. Reasoning: horizontal play that makes the demo pop, spec was already done (Phase 45), and it bridges the gap between feature depth and user discovery.

### Why New Spec (Phase 52) Instead of Running Phase 45
Phase 45 was specced at Phase 44 — 7 phases of codebase changes since then made it stale:
- Migration V68 → now V82 (needs V83)
- RBAC changed from `@PreAuthorize("hasRole('ORG_OWNER')")` to `@RequiresCapability(...)` (Phase 46)
- Tool capability filtering used ad-hoc strings, needs real `CapabilityAuthorizationService`
- Nav/settings structure completely rewritten (Phase 44)
- New entities not covered (expenses, retainers, proposals, etc.)
Re-speccing was cleaner than patching.

### Key Design Changes from Phase 45
1. **SecretStore reuse** — AI API key uses existing `SecretStore`/`OrgSecret` instead of custom `AiKeyEncryptionService`. Same encryption, less code.
2. **PRO tier gating** — premium feature via plan enforcement, not just org toggle. Cross-vertical (not module-gated).
3. **Capability-based tool filtering** — uses real `CapabilityAuthorizationService`, not string matching.
4. **Integration settings reuse** — existing `IntegrationCard` for AI domain already scaffolded. Just needs model selector + test wiring.

## Founder Preferences (Confirmed)
- BYOAK model (tenant provides own Anthropic key)
- Use existing `SecretStore` for key storage
- Premium tier (PRO only), cross-vertical
- Core tool set to start, expandable later
- Everything else from Phase 45 ideation stands: reversible only, confirmation before write, ephemeral sessions, Claude-first with abstraction

## Phase Roadmap (Updated)
- Phase 51: Accounting Practice Essentials (nearly complete, 386B remaining)
- **Phase 52: In-App AI Assistant (BYOAK)** (spec written)
- Phase 53 (candidate): Advanced AI tools (expenses, retainers, proposals, document intelligence)
- Phase 54 (candidate): Legal trust accounting OR accounting Xero sync
