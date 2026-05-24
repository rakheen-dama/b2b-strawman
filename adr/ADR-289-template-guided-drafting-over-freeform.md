# ADR-289: Template-Guided Drafting Over Freeform Generation

**Status**: Accepted

**Context**:

Phase 74 introduces an AI drafting skill that helps attorneys create legal documents from matter and customer context. The firm's existing document template system (Phase 12/42) provides `DocumentTemplate` entities with defined variables, Tiptap or DOCX structures, associated clauses, and a generation pipeline (`DocumentGenerationService`). Attorneys currently fill template variables manually, copy-paste from prior matters, and write narrative sections from scratch.

The question is how much creative freedom the AI should have in generating document content. At one extreme, the AI fills pre-defined template variables and generates content for marked narrative sections -- guided by the template's structure. At the other extreme, the AI generates an entire document from scratch based on matter context and the firm's style, with no template constraint.

The Attorneys Act liability framework (ADR-281) means the attorney is personally responsible for all work product. AI-generated legal documents carry professional risk -- an incorrect clause, a missing statutory reference, or a hallucinated obligation could expose the firm to liability. The drafting approach must balance productivity with professional safety.

**Options Considered**:

1. **Template-guided -- AI fills variables and generates narrative sections within template structure (CHOSEN)** -- The attorney selects a template. The AI receives the template structure (variables, sections, clause slots) alongside matter/customer context. It fills variable values from available data, generates narrative content for marked sections, and recommends relevant clauses. The output respects the template's structure -- no content is generated outside the template's defined sections.
   - Pros:
     - **Structure provides guardrails.** The template defines what sections exist, what variables are needed, and where clauses slot in. The AI operates within these boundaries, reducing the risk of hallucinated sections, missing critical clauses, or structurally invalid documents.
     - **Leverages existing investment.** Firms have already created templates (Phase 12/42) that encode their practice knowledge -- engagement letters, sale agreements, trust deeds, pleadings. The AI amplifies this investment by automating the filling process, not replacing it.
     - **Confidence scoring per variable.** Each variable fill has a confidence level (`HIGH` = from data, `MEDIUM` = AI-inferred, `LOW`/`UNDETERMINED` = cannot determine). The attorney sees exactly which values the AI is confident about and which need verification. Freeform generation offers no such granularity.
     - **Reviewable delta.** The attorney can compare the AI's fills against the template's expected variables -- every field is accounted for. With freeform generation, the attorney must review the entire document with no structural reference point.
     - **Clause recommendation is additive.** The AI recommends clauses from the firm's clause library based on matter type and context. Clauses are toggleable -- the attorney adds or removes them. This is safer than having the AI generate clause text from scratch.
     - **Uses existing generation pipeline.** On gate approval, `DocumentGenerationService` processes the template with AI-provided values -- the same pipeline used for manual document generation. No new rendering infrastructure.
   - Cons:
     - **Requires templates to exist.** If the firm has no templates, the skill is useless. Mitigated: the skill is disabled with a tooltip "Create a document template first." Template creation is an existing capability.
     - **Template quality limits AI quality.** A poorly structured template (missing sections, unclear variable names) produces a poorly structured draft. The AI cannot compensate for template gaps.
     - **Less creative output.** The AI cannot suggest structural improvements to the document, add sections the template doesn't define, or propose alternative document structures. The output is bounded by the template.

2. **Freeform generation -- AI generates entire document from context** -- The attorney describes what they need ("draft an engagement letter for this matter"). The AI generates the complete document -- structure, content, clauses, formatting -- from matter context, customer data, and the firm's style notes.
   - Pros:
     - **No template dependency.** Works for any document type, even if the firm hasn't created a template for it.
     - **Creative flexibility.** The AI can adapt document structure to the specific matter -- a complex commercial transaction might need sections that a standard template doesn't include.
     - **Simpler UX.** No template selection step. "Draft a letter" and the AI produces one.
   - Cons:
     - **Hallucination risk.** Without structural guardrails, the AI may generate clauses that don't reflect SA law, invent obligations, or include provisions that contradict the firm's standard terms. Legal document hallucination is a professional liability issue, not just a UX issue.
     - **No reviewable delta.** The attorney must review the entire document with no structural reference. There is no "these are the variables I filled" -- everything is AI-generated. Review burden is higher, not lower.
     - **Bypasses firm knowledge.** The firm's templates encode years of practice knowledge -- standard clause language, preferred structures, house style. Freeform generation ignores this knowledge base and generates from general training data + style notes.
     - **Inconsistency across documents.** Two freeform-generated engagement letters for similar matters may have different structures, different clause sets, and different language. Templates ensure consistency.
     - **Does not use the generation pipeline.** The output is raw Tiptap content, not a template-applied document. No variable metadata, no clause associations, no template lineage. The document is an orphan in the template system.

3. **Hybrid -- both modes available, attorney chooses** -- Template-guided mode when a template is selected; freeform mode when the attorney opts for "draft from scratch."
   - Pros:
     - **Maximum flexibility.** Attorneys choose the mode that fits their need.
     - **Covers edge cases.** Ad-hoc documents (client letters, one-off agreements) where no template exists can use freeform mode.
   - Cons:
     - **Double implementation effort.** Two prompt designs, two output parsers, two gate executors, two frontend flows. The template-guided flow is already complex (variable editing, confidence badges, clause recommendations). Adding freeform doubles the surface area.
     - **UX confusion.** The attorney must understand the difference between the two modes and choose appropriately. If freeform produces lower-quality output (which it likely will without structural guardrails), the attorney's experience with "AI drafting" is degraded.
     - **Deferred value.** Freeform mode can be added in a future phase once template-guided mode is proven. Building both simultaneously dilutes focus.

**Decision**: Option 1 -- Template-guided drafting. The AI fills template variables from matter/customer context, generates narrative content for marked sections, and recommends clauses from the firm's library. All output respects the template's structure.

**Rationale**:

Template-guided drafting is the safer and more valuable approach for a legal practice management tool. The templates encode the firm's practice knowledge -- standard clause language, required sections, variable definitions. The AI amplifies this knowledge by automating the filling process, not by replacing the firm's document architecture with AI-generated alternatives. This is the same "embedded in the system of record" philosophy that makes Kazi's AI more valuable than bolt-on tools: the AI works with the firm's templates, not its own.

The confidence scoring system (`HIGH`/`MEDIUM`/`LOW`/`UNDETERMINED`) is only possible with template-guided drafting because each variable fill has a clear provenance: did it come from customer data (HIGH), AI inference (MEDIUM), or nowhere (UNDETERMINED)? Freeform generation produces a monolithic text blob with no granular confidence indicators. For the Attorneys Act liability framework, granular confidence is essential -- the attorney needs to know which parts of the document to scrutinise.

Freeform generation (Option 2) has legitimate use cases (ad-hoc client correspondence, one-off agreements), but these are better served by the existing Phase 52 chat assistant or by creating a lightweight template. The professional risk of hallucinated legal content in a freeform-generated document outweighs the convenience of skipping template selection. Hybrid mode (Option 3) is the natural evolution once template-guided mode is proven and trusted, but building both simultaneously doubles the implementation effort without doubling the value.

**Consequences**:

- Positive:
  - Leverages existing template infrastructure. On gate approval, the existing `DocumentGenerationService` creates the document -- no new rendering pipeline.
  - Confidence badges give the attorney precise visibility into AI certainty per variable. Variables marked `UNDETERMINED` are immediately flagged for manual input.
  - Document consistency: all drafts from the same template have the same structure, regardless of which AI model or prompt version produced the fills.
  - Clause recommendations are additive -- the attorney toggles clauses from the firm's own library, not AI-generated clause text.

- Negative:
  - Firms without templates cannot use the drafting skill. They must create at least one template first. Mitigated: template creation is an existing feature with its own UI.
  - The AI cannot suggest structural improvements or add sections the template doesn't define. If the template is incomplete, the draft is incomplete.
  - Freeform drafting use cases (ad-hoc letters, custom agreements) are not served. These require the Phase 52 chat assistant or a future freeform mode.

- Neutral:
  - The `DraftingOutput` record separates variable fills (structured, per-variable) from narrative sections (per-section text blocks). This separation supports the frontend's editable variable table and narrative preview panels.
  - Template variable metadata (from `DocumentTemplate.variables`) is included in the AI prompt so the AI understands what each variable represents. Poorly named variables (e.g., `var1`) produce lower-quality fills.
  - The drafting skill's `SkillContext.additionalContext` includes the template ID. The skill loads the template structure in `assembleUserPrompt()`.

- Related: [ADR-280](ADR-280-evolve-ai-provider-port-for-skills.md) (AiSkill interface -- drafting implements it), [ADR-281](ADR-281-execution-gate-pattern-attorney-liability.md) (execution gates -- draft creation requires attorney approval), [ADR-288](ADR-288-contract-review-document-as-report.md) (contract review also produces Document output via a different path), [ADR-292](ADR-292-ai-generated-document-provenance.md) (draft is tagged as AI-generated)
