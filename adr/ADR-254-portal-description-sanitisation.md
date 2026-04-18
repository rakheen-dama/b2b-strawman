# ADR-254: Description Sanitisation for Portal-Visible Domain Text

**Status**: Accepted

**Context**:

Firm users routinely use free-text description fields (on trust transactions, time entries, deadline notes) as a scratch pad. Common conventions observed in production tenants: prefixing with `[internal]` to flag a note meant only for firm eyes, appending multi-paragraph musings, pasting in client correspondence fragments, or writing abbreviations that assume firm context. When Phase 68 starts piping those fields into portal-visible surfaces, raw pass-through is unsafe: a client seeing `[internal] client seems flaky, push deposit before month-end` on their trust transaction list is an embarrassment at best and a privileged-communication leak at worst.

We need a policy that is:

- **Low-friction for firms** — no per-tenant opt-in workflow; works out of the box.
- **Respects firm intent** — the `[internal]` prefix is already an existing convention in Legal-ZA and Consulting-ZA tenants; honour it.
- **Protects portal even when firms forget to self-censor** — no description longer than a tweet gets through; unknown contexts fall back to synthesised text.
- **Extensible** — the same rule applies to any new portal-visible free-text field.

The phase 68 requirements settled on two mechanical rules: strip anything tagged `[internal]` and truncate to 140 chars with a fallback. This ADR records why those rules and not others.

**Options Considered**:

1. **Strip-and-truncate pipeline with `[internal]` prefix detection and fallback** — Shared `DescriptionSanitiser` service; input → strip leading `[internal]` (case-insensitive, with optional whitespace) → truncate to 140 characters with ellipsis → substitute a synthesised fallback (e.g. `"DEPOSIT — TX-2026-0012"`) if empty after strip.
   - Pros:
     - Zero config for firms. Existing `[internal]` convention is honoured automatically.
     - Hard length cap prevents screen-hogging from runaway notes.
     - Fallback ensures no blank descriptions on the client side.
     - One central service; swap the implementation without touching sync services.
     - Applies uniformly to trust / retainer / deadline — no per-surface special casing.
   - Cons:
     - Doesn't catch non-`[internal]` firm slang or privileged content.
     - Firms that don't know about the `[internal]` convention may be surprised.
     - 140 chars is a judgment call; some legitimate descriptions will be truncated.

2. **Strict allowlist of visible-field content** — Hard-coded list of allowed phrases / structured types; anything else → fallback.
   - Pros:
     - Tightest possible safety.
   - Cons:
     - Unworkable: free-text is free-text; the allowlist would be infinite or useless.
     - Would require per-firm curation, creating support burden.
     - Destroys the utility of the description field for clients who benefit from real context.

3. **Firm opt-in per entity** — Each trust transaction, time entry, etc. has a `showDescriptionOnPortal` boolean; firm toggles per row.
   - Pros:
     - Firms have absolute control.
     - Supports cases where a firm wants to hide even non-`[internal]` descriptions.
   - Cons:
     - Adds friction to every firm-side write path.
     - Firms will forget to toggle; the default has to be either "show" (risky) or "hide" (defeats the point of portal visibility).
     - Per-row UI clutter.
     - Doesn't protect against runaway length.

**Decision**: Option 1 — strip-and-truncate pipeline with `[internal]` prefix detection and fallback.

**Rationale**:

**Honour existing conventions.** The `[internal]` prefix is an emergent practice in Legal-ZA tenants (confirmed by surveying the demo data + two production firms during Phase 60 rollout). Formalising it as a portal-sanitisation rule is low-friction — firms already use it.

**140-char cap matches portal use.** Portal list views show a single-line description column. Longer text breaks layout; truncating at 140 keeps the surface consistent with mobile expectations.

**Fallback is safer than blank.** An empty description field on the portal looks broken. Synthesising `"{transactionType} — {reference}"` is always meaningful and never leaks firm-internal content.

**Central service enables future evolution.** Starting with prefix-and-length rules is pragmatic; future extensions (stopword lists, ML-based redaction, per-tenant patterns) plug into the same service without touching call sites.

**Consequences**:

- `backend/.../portal/sanitisation/DescriptionSanitiser.java` is introduced as a `@Component`. Unit-tested directly.
- Every portal sync service that handles a free-text field injects `DescriptionSanitiser` and calls `sanitise(raw, fallback)` before writing.
- Three new field mappings use it: `portal_trust_transaction.description`, `portal_retainer_consumption_entry.description`, `portal_deadline_view.description_sanitised`.
- If a firm's description doesn't start with `[internal]` but still contains sensitive content, the sanitiser does not catch it. This is explicitly accepted — Phase 68 protects against the dominant leakage pattern, not against adversarial firm behaviour.
- Documentation in portal admin surface will mention `[internal]` convention so firms can opt into the existing behaviour.
- Future ADRs may extend the sanitiser (per-tenant patterns, stopwords) — backward compatibility guaranteed because all call sites go through `sanitise(raw, fallback)`.

**Related**:

- [ADR-253](ADR-253-portal-surfaces-as-read-model-extensions.md) — Sanitisation happens inside the sync pipeline, not at render time.
- [ADR-031](ADR-031-separate-portal-read-model-schema.md) — Because the portal schema stores sanitised text, no raw firm-internal text is ever on the portal data path.
