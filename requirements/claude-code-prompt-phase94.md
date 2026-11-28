# Phase 94 — AI Egress Guard: Client Personal Information Leaves Kazi Only Masked, Tokenised, Lawful and On the Record

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` on 2026-11-28 (log: `.claude/ideas/phase94-ideation-2026-11-28.md`). The founder signs off the Legal basis; the release gates RG-1..RG-9 below need a practising attorney before release to a real firm. Building does not wait for them.
>
> **Why this phase, why now.** Kazi sends client personal information (PI) to Anthropic by two routes:
> - **(A)** seven in-product skills, using the firm's own key;
> - **(B)** the MCP server, which the firm's own Claude reads.
>
> Neither route minimises anything. Drafting sends the client's ID number, tax number, registration number and full address. Intake sends up to 500 other matters with their client names. MCP returns correspondence bodies verbatim. There is no record of what left, and nothing on the POPIA surfaces (DSAR, processing register, PAIA manual) mentions an AI provider or the US. The founder named three drivers:
> 1. **Real risk reduction**: less PI leaves at all.
> 2. **The design-partner objection** "does my client data go to Anthropic / the US?", which needs a credible, provable answer.
> 3. **Responsible client data handling** as a product value.
>
> This must be in place before more skills or MCP tools are added.
>
> **Founder decisions (2026-11-28):**
> 1. **Both routes, one policy, one egress ledger.** Route A and route B share the firm policy, the categories and the ledger. They differ only where noted below.
> 2. **Identifiers are masked by default on both routes.** This covers SA ID and passport numbers, company registration numbers, tax/VAT numbers, bank account numbers, email addresses, phone numbers and street addresses. A firm owner may relax a category, and the reason is recorded.
> 3. **Names are tokenised on route A** ("Client-A", "Party-B") and swapped back into the result before a human sees it. Kazi can do this because it holds every client, contact and adverse-party name in the tenant. **On route B names pass through**, because the lawyer's Claude must show them, and every name sent is recorded in the ledger.
> 4. **Special PI (s26) and children's PI (s34) are blocked by default.** A firm owner may allow a category, but only on a **structured legal basis** (see § Legal basis, Q1/Q3), never on owner say-so alone.
> 5. **Detection is deterministic, with no ML or NER.**
>    - **Sensitive-category flags on the matter**, which the attorney sets and the matter template prefills.
>    - **Minors** are detected from the date of birth encoded in SA ID numbers.
>    - **Identifiers** are found by pattern plus checksum.
>    - Keyword scanning is out.
> 6. **Images are treated as biometric (s26) and blocked by default.** The scout found that no skill sends images today: every `requiresVision()` returns false, and FICA sends a "[Image document: …]" placeholder plus extracted PDF text (`fica/FicaDocumentReader.java:163`). The rule therefore governs any vision call: blocked unless the owner unblocks BIOMETRIC on a recorded basis, then a per-run confirmation, then a ledger row. **What FICA does send today, the extracted ID-document text, gets identifier tokenisation**, plus a locally computed "ID number on document matches client record: yes/no" fact (see E2).
> 7. **The legal advisor's additions are all in scope:**
>    - a firm-level **s72 transfer-basis record**;
>    - a **"no AI" flag** on client and matter, which hard-blocks both routes;
>    - an **MCP commercial-plan attestation** (Claude Team, Enterprise or API; not consumer plans);
>    - **processing register and PAIA manual** entries naming Anthropic and the US;
>    - an optional **engagement-letter AI clause**;
>    - the **wording fixes** (never "anonymised"; rename the MCP "consent").
>
> **Scouting note.** Code trace on 2026-11-28 against `main` `6efca4504`. Paths are relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/`. **Re-read file:line at HEAD before relying on any of it.**
> - **Route A has one choke point: `integration/ai/skill/AiSkillExecutionService.executeSkill` (`:83-156`).**
>   - **Prompt assembly:** prompts are built in `AiExecutionPersistenceService.assemblePrompts` (`:108-112`) via `skill.assembleSystemPrompt(profile)` / `assembleUserPrompt(context)`.
>   - **The only Anthropic call sites** are `provider.completeWithVision` (`:122`) and `provider.complete` (`:133`), which go through `AnthropicAiProvider` (`:126`/`:132`) to `AnthropicApiClient` `/v1/messages` (`:102`, `:178`).
>   - **Order after the call:** cost is metered in `completeExecution` (`:124-164`), then `skill.createGates(...)` (`:140`) parses with `LlmJsonParser` and builds the `AiExecutionGate`s.
>   - **Collections** (`AiReminderComposer.java:76`, `CashDigestService.java:290`) also go through `executeSkill`. CashDigest creates no gate; its service parses the output (~`:315`).
>   - **Storage:** `AiExecution.outputContent` stores the raw model output (`execution/AiExecution.java:37`). The prompt is not stored, only `inputSummary`.
>   - **The only other Anthropic call** is `IntegrationService.testAiConnection → validateKey` (`:286`), which sends no client PI and is exempt. `LlmChatProvider.chat()` has no callers. `AiProvider.generateText/summarize/suggestCategories` have no callers.
> - **PI per skill (route A):**
>   - **drafting** (`DraftingSkill.java` ~`:95-250`): matter name, description and reference; client email, phone, **idNumber, registrationNumber, full address, taxNumber**; contact person.
>   - **intake** (`MatterIntakeSkill.java:97-216`): free-text description, client name, and **up to 500 recent matter names with their client names** (other clients' data).
>   - **contract review** (`:98-172`): full extracted contract text, plus matter name, description and work type.
>   - **fica** (`:100-185`): client name, checklist items, and the **extracted text of every uploaded document** (ID documents included).
>   - **compliance audit** (`:70-197`): mostly counts, plus flagged client and matter names.
>   - **collection reminder** (`:104-182`): client name, amounts, billing and chase history, advisor annotations.
>   - **cash digest** (`:62-81`): top-debtor names and amounts.
> - **Route B has no choke point.**
>   - **Shape:** 19 `@McpTool`s and 3 `@McpResource`s. Each one guards itself inline: `if (!enablement.effectiveState())`, `McpCapabilityGuard.gatedTool/gatedResource`, `McpToolAudit`. Spring AI 2.0.0-M6 has **no tool-call interceptor** (Phase 78, Epic 565 decision).
>   - **DTOs carrying PI:**
>     - `McpClientDto`: name, contact name/email/phone. It does not expose idNumber, taxNumber or address.
>     - `McpCorrespondenceDto`: subject, **`bodyText`, `bodyHtml`**, from/to/cc.
>     - `McpCorrespondenceListItem`.
>     - `McpMatterDto`: name, description, reference.
>     - `McpInvoiceDto`: client name, line descriptions.
>     - `McpUnbilledSummaryItem`.
>     - `McpActivityItem` / `McpAuditEventItem`: messages, actor names.
>     - `McpFirmProfileDto`.
>   - **Write tools** (`file_correspondence`, `attach_document`, `propose_task`) are inbound. Only their response payloads are egress.
> - **The existing MCP "consent"** is `mcp/consent/McpEgressConsent` (`mcp_egress_consents`, append-only, GRANTED/REVOKED, `consentVersion`). Its frontend copy is "Acknowledge POPIA responsibility", version `popia-egress-v1` (`frontend/lib/types/settings.ts:297`, `…/settings/connections/_tabs/mcp-connector-card.tsx:288-303`). **It is an owner acknowledgement, not data-subject consent.** Route A has no equivalent record.
> - **Where PI lives.**
>   - `customer/Customer.java`: name, email, phone, `idNumber` (`:40`), `registrationNumber` (`:83`), address (`:86-101`), `taxNumber` (`:104`), contact name/email/phone (`:107-113`), notes, customFields. **There is no date-of-birth field**, so age must be derived from the SA ID number.
>   - `portal/PortalContact` (displayName, email).
>   - `verticals/legal/conflictcheck/AdverseParty` (name, idNumber, registrationNumber, aliases, notes).
>   - Matter custom fields `opposing_party` / `opposing_attorney` (`field-packs/legal-za-project.json:39,47`).
> - **Matter type.**
>   - `Project.workType` (`project/Project.java:127`) is inherited from `ProjectTemplate.workType` (`projecttemplate/ProjectTemplate.java:67`).
>   - The legal-za template pack's matter types are COLLECTIONS, COMMERCIAL, CONVEYANCING, ESTATES and LITIGATION (`project-template-packs/legal-za.json` v4).
>   - There is no sensitivity field.
> - **POPIA surfaces** (`datarequest/`):
>   - `ProcessingActivity` has category, description, legalBasis, dataSubjects, retentionPeriod and recipients. It is seeded in Java (`ProcessingActivityService.seedJurisdictionDefaults` `:71`, 7 ZA rows, recipients "None" except SARS). **There is no AI row.**
>   - `PaiaManualContextBuilder:50-58` feeds `template-packs/compliance-za/paia-section-51-manual.json` (pack v1, Parts I-VI). **There is no transborder-flow content.**
>   - The DSAR export (`DataExportService:472-509`) has **no third-parties / recipients section**.
> - **AI settings.**
>   - `AiFirmProfile` holds practiceAreas, jurisdiction, riskCalibration, houseStyleNotes, ficaRequirements, feeEstimationNotes, preferredModel, monthlyBudgetCents, and so on.
>   - UI lives at `/org/[slug]/settings/connections?tab=ai` (`_tabs/ai.tsx`, `components/ai/ai-profile-form.tsx`), `?tab=mcp` (`_tabs/mcp.tsx`), and `settings/connections/ai/history/page.tsx`.
> - **Engagement letters:** `template-packs/legal-za/engagement-letter-{general,litigation,conveyancing}.json`, pack **v16**. There is also a clause library (`clause/`).
> - **Numbers.** The latest tenant migration is `V37` and global is `V7`, so the next free are **V38** and **V8**. Take the next free at build time. The latest ADR is **ADR-396** (`adr/`).

## System Context

Kazi is legal-only SA practice-management SaaS (Phase 86): Next.js 16 frontend and portal, Spring Boot 4 / Java 25, Keycloak, schema-per-tenant Postgres. The AI core has two parts:
- **Route A** (Phases 72/74/83): `AiProvider`/`AnthropicAiProvider` (BYOAK), `AiFirmProfile`, seven skills, `AiExecution` + `AiExecutionGate` (human approval before anything commits), and `AiCostService`.
- **Route B** (Phases 78/81/82): the MCP server with per-tool capability guards, the enablement + acknowledgement record, the `mcp.*` audit, and the `kazi-legal-za` skill pack in `../claude-for-legal-sa`.

Both were proven live against real Claude in June 2026. The POPIA tooling (Phases 50+/89) covers DSAR export and anonymisation, the processing register, the PAIA s51 manual and retention. Neither the AI core nor the POPIA tooling knows about the other.

## Objective

Every byte of client PI bound for an AI provider passes an **egress guard** that applies the firm's policy. The guard does one of three things:
- **minimises**: masks or tokenises identifiers, and tokenises names on route A;
- **blocks**: special PI, children's PI, images, "no AI" clients and matters, or a missing legal basis;
- **records**: what left, about whom, to which provider and country, by whom, why, and on what legal basis.

The firm can show this record in a DSAR answer, its processing register, its PAIA manual and its engagement letter.

**Exit bar.** Both items are observed on a fresh stack, not inferred (root CLAUDE.md §3):
1. **Route A.** A live Claude drafting run on a client with ID, tax and address fields. The **captured outbound request body** contains no raw identifier and no real client name, while the approved draft shows the real values.
2. **Route B.** An MCP session where a sensitive and a "no AI" matter are withheld and correspondence identifiers are masked. The client's DSAR export then lists every egress event about them.

## Legal basis (Fable legal advisor + Perplexity, 2026-11-28; founder sign-off)

Sources: POPIA (Act 4 of 2013) https://www.justice.gov.za/legislation/acts/2013-004.pdf; Information Regulator prior-authorisation guidance https://inforegulator.org.za/prior-authorisation/; LPC Code of Conduct; LSSA revised AI guidelines (De Rebus, July 2026); LPC ICT guide (March 2026). Confidence is per item.

- **Q1. Prior authorisation (s57(1)(d), s58).**
  - **When it applies:** it is required for special PI or children's PI transferred to a third country without adequate protection "as referred to in s72".
  - **Unsettled:** the Regulator has issued no adequacy determinations and no transborder guidance (the 2025 Annual Report says the guidance note was not yet approved). Whether a binding agreement (Anthropic's DPA) cures country inadequacy is unsettled. The conservative reading is that the US needs authorisation.
  - **Process:** authorisation is once-off (s57(4)). The Regulator has 4 weeks, plus up to 13 if it investigates, and silence is deemed approval (s58(3)-(7)).
  - **Product rule:** BLOCK is the default. To unblock, the owner records **either** "prior authorisation obtained/deemed" (reference + date) **or** "adequacy via binding agreement, legal basis attested". Never a reason alone.
  - *Confidence:* medium on the law, low on how the Regulator would read the adequacy wording.
- **Q2. s72 ground.**
  - **Grounds:** the primary ground is s72(1)(a), a binding agreement (the firm's Anthropic Commercial Terms + DPA: no training, onward-transfer terms). The second, belt-and-braces ground is s72(1)(b), consent via the engagement letter. Contract-necessity (c)/(d) is weak and (e) fails.
  - **Who holds it:** under BYOAK the **firm** (the responsible party) holds the basis. Kazi is the firm's operator (s20/s21).
  - **Consumer plans:** Claude Free/Pro/Max have no DPA and may train on inputs, so **s72(1)(a) fails for consumer-plan MCP use**.
  - *Confidence:* medium-high.
- **Q3. Authorisations (s27(1)(b), s33, s35(1)(b)).**
  - **What they cover:** necessity for "establishment, exercise or defence of a right or obligation in law" covers the firm's own processing, and its operator's.
  - **What they don't cover:** they lift only the s26/s34 prohibition. They do **not** satisfy s72 or s57(1)(d), and they do not fit non-contentious work.
  - **Product rule:** the unblock reason is a structured choice (data-subject consent / right in law / other + text), in addition to the Q1 basis.
  - *Confidence:* medium.
- **Q4. Confidentiality (LPC Code para 3.6, 57.2-57.3).**
  - **The duty:** it is separate from POPIA. There is no binding LPC AI rule. The LSSA guidelines say no general duty of proactive disclosure exists, **no consumer-tier tools for confidential information**, and **a client mandate excluding AI must be honoured**. Academic opinion is split (Van Eck 2025 TSAR 454 argues for prior consent).
  - **Tokenising names:** it reduces exposure, but matter facts can still identify the client.
  - **Product rule:** a "no AI" flag, plus an optional engagement-letter clause.
  - *Confidence:* medium.
- **Q5. Notice and DSAR.**
  - **What the statute requires:** s18(1)(g) notice of the transfer to a third country and its level of protection. s18(1)(h) notice of recipients. s23(1)(b) the identity or categories of third parties who have had access. PAIA s51 requires the manual to list planned transborder flows.
  - **Product rule:** the ledger captures what is needed to answer these.
  - *Confidence:* high.
- **Q6. Not de-identification.** Kazi holds the token map, so the payload is **pseudonymised PI**, not de-identified (s1, s6(1)(b)).
  - **Product rule:** masking is a s19 safeguard and s10 minimisation, **not** an exit from s72. Never say "anonymised" or "POPIA-exempt" in UI or marketing.
  - *Confidence:* high.

**Release gates (practising attorney sign-off before release to a real firm; build is not blocked):**
- **RG-1:** the s57(1)(d) reading, i.e. whether the DPA amounts to adequacy. The special/children's unblock flow ships disabled-by-config until this is cleared.
- **RG-2:** whether ID-document images and text are biometric (s26). The per-run staff confirmation is not data-subject consent.
- **RG-3:** the wording of the MCP consumer-plan attestation, and the block copy.
- **RG-4:** detection limits. ID-derived age misses passport holders and children named only in free text, and the matter flag misses special PI inside ordinary matters. The disclaimer copy needs sign-off, and BLOCK must never be described as a guarantee.
- **RG-5:** the free-text residual risk (pattern masking only). Explicit acceptance wording.
- **RG-6:** the renamed MCP acknowledgement copy.
- **RG-7:** the Kazi–firm operator terms (s20/s21) covering AI egress, token-map security and breach notification (s22). This is a contract, not code.
- **RG-8:** the engagement-letter AI clause and the privacy-notice text.
- **RG-9:** opposing and third parties appear in payloads but never get the firm's notice. Reliance on s72(1)(a) and the s18(4) exemptions.

**Could not verify (attorney to check):**
- the exact wording of the 2021/2024 prior-authorisation guidance notes on adequacy;
- whether a legal-practitioner POPIA code exists (s57(3));
- the 2025 Regulations amendments;
- s32 health-information supply limits;
- Anthropic's current default API retention (the firm's actual contract governs).

## Assumptions register (design so a different answer changes config or copy, not the data model)

- **A1.** The provider of record is **Anthropic PBC, United States**. The provider and country live on the transfer-basis record, not in code, so a later provider or region is a data change.
- **A2.** Unblocking a category and editing the transfer basis are **OWNER-only**. Editing the identifier policy and setting matter flags use existing capabilities (`AI_MANAGE` for policy; matter edit for the flags). Confirm at `/architecture`.
- **A3.** The legal-za matter template defaults are: LITIGATION prefilled with no special category (the attorney ticks); ESTATES with none; a family/children template, if one exists in the pack, prefills CHILD. The attorney can always edit. Confirm the list against the pack at build.
- **A4.** Ledger retention follows the firm's retention policy for audit events (Phase 50+). The ledger is never anonymised away by a DSAR deletion: the record of disclosure survives, keyed to the anonymised subject.
- **A5.** Firm staff (member) names in payloads are out of scope for tokenisation in v1. They are the firm's own employees, and processing them falls under the firm's employment basis. They are recorded as such.

## Constraints

- **One policy engine, two adapters.** A single `egress` package owns the categories, detectors, policy, legal-basis record and ledger. Route A plugs in at `AiSkillExecutionService.executeSkill`, after prompt assembly and before the provider call, then again after the call and **before** `createGates`/CashDigest parsing (rehydration). Route B plugs in where the MCP DTO is serialised (see ADR topic 3). **No tool or skill may bypass it.** Enforce this with an architecture test, e.g. "only `egress` may reference `AnthropicApiClient`", and "every MCP DTO string field carries a classification".
- **Route A tokenisation is reversible; route B masking is not.**
  - **Route A:** identifiers and names become stable per-call placeholders (`[CLIENT-A]`, `[ID-NUMBER-1]`). Claude can still write them into a draft, and Kazi rehydrates the parsed output (including Tiptap JSON) before the gate shows it. `AiExecution.outputContent` stores the **tokenised** output. Token-map storage (ephemeral vs encrypted-with-execution for retries/audit) is an ADR decision. If persisted, it uses `EncryptedDatabaseSecretStore`-grade encryption and follows execution retention.
  - **Route B:** identifiers are irreversibly masked (`****4821`). Names pass through.
- **The name dictionary comes from the tenant's own records.** It covers clients, client contact names, portal-contact display names, adverse parties and their aliases, and the `opposing_party`/`opposing_attorney` custom fields. Matching must be safe: longest-first, word-boundary, case-insensitive, and no tokenising of fragments shorter than a threshold. Names not in the dictionary (witnesses, people in documents) pass through. That is residual risk (RG-4/RG-5), shown in copy.
- **Identifier detection is deterministic.**
  - SA ID: 13 digits, Luhn-valid, plausible YYMMDD. Passport: format.
  - CIPC registration (`YYYY/NNNNNN/NN`), SARS tax/VAT (10 digits, starting 0-3/9 for income tax and 4 for VAT, with checksum where defined).
  - Bank account: digits in an account-labelled context, or a known field. Email, ZA phone (+27 / 0 prefixes), and street addresses via known fields only (no free-text address NER).
  - Known structured fields are classified by field, not by pattern.
- **Minors.** Age comes from the ID-number DOB at egress time. The century is resolved by "YY > current YY ⇒ 19YY". A subject under 18 is CHILD. This applies to clients and adverse parties alike.
- **Blocking semantics.**
  - **Route A:** a blocked execution makes **no provider call and no spend**. It is recorded as `BLOCKED`, with category, subject and reason, and the UI says why and what would unblock it.
  - **Route B:** blocked content is **withheld in place**, with a typed marker (`{"withheld":"SPECIAL_PI"}` / `"NO_AI"` / `"CHILD"`), so the lawyer's Claude knows something exists without seeing it. A "no AI" client is omitted from lists and returns the same non-leaking 404 as no-access for direct gets (the `McpToolErrors` convention).
- **Prerequisites to switch AI on.**
  - **Both routes:** a transfer-basis record must exist, holding the s72 ground(s), a commercial-plan + DPA attestation, the zero-data-retention status, and the provider and country.
  - **Route B also:** the attestation that staff use Claude **Team, Enterprise or API**, not consumer plans. Kazi cannot verify this technically, and the UI says so.
  - **Setup prompt:** a firm must complete the record before its first AI call; show a clear prompt, not a silent failure. (No live tenants — FD-1 ruled 2026-11-28: no migration handling for already-enabled firms.)
- **Wording.**
  - Rename the MCP "Acknowledge POPIA responsibility" acknowledgement and bump it to `popia-egress-v2`. Prior v1 rows stay, and the new copy (RG-6) states that this is the firm's acknowledgement, not client consent.
  - No "anonymised", "de-identified" or "POPIA-exempt" anywhere in AI or egress copy. Add a lint/test grep over `frontend/` and the skill-pack docs.
- **Audit and ledger hygiene.** The new `egress.*` audit family (policy changed, basis recorded, category unblocked, execution blocked) carries ids and categories only, with no PI in params (SAFE_TOKEN discipline). The **ledger** necessarily references data subjects by id. It must **never store the raw values sent**: only categories, counts and the masked/tokenised/sent disposition.
- **Performance.** The guard runs per skill call and per MCP response. Dictionary build is per tenant and cached with invalidation on client/party change. MCP list responses (up to the existing response ceiling) must not regress p95 materially; measure in E8.
- **Tenant isolation tests are mandatory.** The policy, basis, ledger and dictionary are invisible across tenants, and a dictionary must never include another tenant's names.
- **Test bar.** Full `./mvnw verify` clean (`backend/scripts/verify.sh`), plus `pnpm lint && pnpm build && pnpm test && pnpm format:check`, plus the legal Playwright suites (UI change). Pack bumps (legal-za v17, compliance-za v2) carry their pack-version tests. Reproduce-before-fix for any bug found.

---

## Epic specifications

### E1 — Egress foundation: categories, detectors, policy, transfer basis
- **`EgressCategory`:**
  - **Identifiers:** `NATIONAL_ID`, `PASSPORT`, `COMPANY_REG`, `TAX_NUMBER`, `BANK_ACCOUNT`, `EMAIL`, `PHONE`, `ADDRESS`.
  - **Names:** `PERSON_NAME` / `PARTY_NAME`.
  - **Free text:** `FREE_TEXT` (flag-only: "unstructured content sent").
  - **s26:** `SPECIAL_RELIGION`, `SPECIAL_RACE_ETHNIC`, `SPECIAL_TRADE_UNION`, `SPECIAL_POLITICAL`, `SPECIAL_HEALTH_SEX_LIFE`, `SPECIAL_BIOMETRIC`, `SPECIAL_CRIMINAL`.
  - **s34:** `CHILD`.
  - **Flag:** `NO_AI`.
- **Detectors** per § Constraints. Each is a pure function with an exhaustive unit-test corpus: valid and invalid IDs, Luhn edge cases, embedded-in-text cases, false-positive traps such as fee-note numbers and matter references.
- **`AiEgressPolicy`** (tenant, versioned). Each category is ALLOW / MASK / BLOCK, and the defaults come from founder decisions 2-6. Changing it emits `egress.policy.changed` with old and new values.
- **`AiCategoryUnblock`** (special/child/biometric). Fields:
  - category;
  - **basis**: `PRIOR_AUTHORISATION` + Regulator reference and date, or `ADEQUACY_BINDING_AGREEMENT` + attestation;
  - **reason type**: `DATA_SUBJECT_CONSENT` / `RIGHT_IN_LAW` / `OTHER` + text;
  - document reference, owner and timestamp;
  - append-only revoke.
  
  The flow sits behind a config flag that stays off until RG-1 clears.
- **`AiTransferBasis`** (tenant, append-only history). Fields:
  - provider legal entity and country (A1);
  - s72 grounds (multi-select: `BINDING_AGREEMENT`, `CONSENT`, `OTHER`);
  - commercial terms + DPA attested (yes, with date);
  - zero data retention (yes/no/unknown);
  - MCP commercial-plan attestation;
  - the attesting owner and timestamp.
- **Migration:** next free tenant (~V38). The `egress.*` audit family; catalogue-count tests bumped.

### E2 — Route A adapter: tokenise, block, call, rehydrate
- **Hook into `executeSkill`:**
  1. Build the `EgressContext`: the subjects resolved from the `SkillContext` (client, matter, adverse parties), their flags, and the policy.
  2. Run the **pre-call guard** over the assembled system and user prompts. It hard-blocks on NO_AI, unblocked-required categories, CHILD, or a missing transfer basis, and tokenises names and identifiers per policy.
  3. Call the provider.
  4. **Rehydrate after the call, before** `LlmJsonParser`/`createGates` and the CashDigest parse.
- **Rehydration** covers every string node of the parsed JSON, including Tiptap documents from drafting and contract review. An unknown placeholder echoed by Claude is left verbatim and flagged, never guessed.
- **Per-skill minimisation, beyond the generic guard:**
  - **Intake:** the 500-recent-matters list is tokenised, and capped or reduced to fields needed for conflict and template screening. `/architecture` decides; the founder's leaning is "names tokenised, keep the list".
  - **Drafting:** identifiers tokenised, so the draft carries placeholders that rehydrate to real values.
  - **FICA:** extracted document text is tokenised. Kazi **computes locally**, and passes as facts, whether the ID number found on the document passes Luhn and matches `Customer.idNumber`, and whether the DOB is consistent. Claude never needs the raw number.
  - **Collections and CashDigest:** names tokenised; amounts allowed (not PI on their own).
- **Vision:** any `completeWithVision` call is classified `SPECIAL_BIOMETRIC`. It is blocked by default; if unblocked, the UI requires a per-run confirmation ("this sends the image to Anthropic, United States").
- **Records:** `BLOCKED` is a new `AiExecution` status (or a sibling record, decided by ADR). No provider call, no cost. Tokenised `outputContent`.

### E3 — Route B adapter: classified DTOs, masking, withholding
- **Classification:** every MCP DTO field carries a classification (annotation or registry, decided by ADR). A whole-codebase test fails the build on any unclassified string field in `mcp/dto`. This is a `WireVocabularyContractTest`-style scanner, so remember Tier-2 test scoping.
- **One serialisation-level policy application** (for example a Jackson module or a single `McpEgressWriter` that every tool's `asResult` goes through). Tools do not hand-mask. It:
  - masks identifiers;
  - pattern-masks `FREE_TEXT` fields (correspondence `bodyText`/`bodyHtml` and subject, matter description, activity messages, invoice line descriptions);
  - withholds sensitive, CHILD and NO_AI content with typed markers;
  - omits NO_AI clients from lists.
- **Enablement:** `McpEnablementService.effectiveState()` additionally requires the transfer basis plus the MCP commercial-plan attestation. Acknowledgement copy v2 (RG-6). No live tenants (FD-1, ruled 2026-11-28): no grace-period or switch-off code.
- **Skill pack:** the `kazi-legal-za` skill pack (sibling repo) needs a doc note on markers, so skills don't misread a withheld block as "no data". Track it as a follow-up PR in `../claude-for-legal-sa`, not in this repo.

### E4 — Sensitive matters, minors and "no AI"
- **On `Project`:** `sensitiveCategories` (a set of s26 categories + CHILD) and `aiExcluded` (boolean + reason + who/when). **On `Customer`:** `aiExcluded` likewise.
- **On `ProjectTemplate`:** `defaultSensitiveCategories`, copied at instantiation. The legal-za template pack is bumped (A3).
- **Minors:** CHILD is derived from ID numbers (client, adverse parties linked to the matter) at egress time. It is not stored as a flag, so it stays correct as people age. It is shown on the matter as a derived badge.
- **Audit:** every flag change is audited (`egress.matter.flagged`, `egress.client.ai_excluded`).

### E5 — Egress ledger and DSAR
- **`AiEgressEvent`** (one per skill execution or MCP tool/resource call). Fields:
  - route (SKILL / MCP), skill id or tool name;
  - acting member;
  - matter (if any);
  - provider entity + country;
  - purpose (skill purpose / "MCP read");
  - disposition (SENT / BLOCKED);
  - a policy-version snapshot and a transfer-basis-version snapshot;
  - a free-text-sent flag;
  - timestamp.
- **`AiEgressSubject`** (one per data subject per event). Fields: subject type (CLIENT / ADVERSE_PARTY / CONTACT) and id; per-category disposition (SENT / MASKED / TOKENISED / WITHHELD). **Raw values are never stored.**
- **Written transactionally:** with the `AiExecution` on route A (respecting the Phase 72 split of the HTTP call from the DB transaction), and with the `mcp.tool.invoked` audit on route B.
- **DSAR export (`DataExportService`):** gains an **"AI providers and disclosures"** section. It lists each provider/country, and the events (date, purpose, categories, disposition) about the subject. This answers s23(1)(b).
- **Anonymisation (`DataAnonymizationService`):** it preserves ledger rows (A4) and rekeys them to the anonymised subject.

### E6 — POPIA documents and engagement letter
- **Processing register:**
  - When AI (either route) is first enabled, seed or upsert an **"AI-assisted legal services"** `ProcessingActivity`. It holds the data subjects, the categories actually allowed by policy, the legal basis (from `AiTransferBasis`), and recipients "Anthropic PBC (United States)".
  - It is kept in sync when the policy or basis changes. When AI is disabled, the row is marked inactive, not deleted.
- **PAIA manual:** `compliance-za` pack v1 → v2 adds a **transborder flows** part (s51(1)) fed from `AiTransferBasis` (`PaiaManualContextBuilder` gains the data).
- **Engagement letter:** `legal-za` pack v16 → v17 adds an optional **AI-use clause** (RG-8). It covers what is sent, to whom and where, the safeguards (masking/tokenising, no training under the commercial terms), and the client's right to exclude AI, which maps to `aiExcluded`. It is available to the three engagement letters via the clause library. **It doubles as s18 notice and s72(1)(b) consent.**
- **Privacy notice:** a privacy-notice paragraph template in the same pack.

### E7 — Frontend
- **Settings → Connections → new "AI data protection" tab** (or a section in `?tab=ai`; decide by placement ADR, with `?tab=mcp` linking to it). It holds:
  - the transfer-basis form (owner);
  - the category policy matrix;
  - the special/child/biometric unblock dialogs with the structured basis (disabled until RG-1);
  - the **egress log** (filter by client, matter, route, disposition, date; CSV export);
  - the detection-limits disclaimer (RG-4).
- **Matter:** a sensitive-categories control and an "Exclude from AI" toggle, with a derived "minor involved" badge. **Client:** an "Exclude from AI" toggle and an **"AI disclosures"** panel (that client's ledger).
- **Skill runs:** a pre-run notice when a run will be blocked (with what would unblock it). Result chips such as "3 identifiers tokenised · 2 names tokenised · sent to Anthropic (US)". The vision per-run confirmation.
- **MCP card:** the commercial-plan attestation step and the renamed acknowledgement (v2).
- **Setup prompt:** "record your AI transfer basis" wherever AI or MCP is switched on without one.

### E8 — QA capstone (observed end-to-end)
- **Fixture firm:**
  - a client with ID, tax, registration, address, email and phone;
  - a minor adverse party (an ID number with a DOB under 18);
  - a LITIGATION matter flagged `SPECIAL_HEALTH_SEX_LIFE`;
  - a client marked "no AI";
  - correspondence containing an ID number and a bank account in the body.
- **Observed on a fresh stack, live Claude key:**
  - **Route A outbound body captured** (test-profile request logging or a recording proxy; never logged in prod): no raw identifiers and no real names. The approved drafting gate shows the real values.
  - A FICA run reports the local ID-match fact.
  - Runs on the sensitive matter, the no-AI client and the minor-party matter are **BLOCKED with zero spend**, and the blocked reasons are shown.
  - Before the transfer basis exists, AI is refused with the banner.
  - **Route B** (MCP over the real transport, with the commercial-plan attestation): correspondence identifiers masked; the sensitive matter withheld with a marker; the no-AI client absent from `list_clients` and 404 on `get_client`.
  - The **egress log** shows every event above.
  - The client's **DSAR export** lists them. The processing register shows the AI row. The PAIA manual renders the transborder part. The engagement letter renders with the AI clause.
  - An architecture-test failure is demonstrated on a deliberately unclassified DTO field (then reverted).
  - MCP p95 is measured before and after on `list_clients`/`list_correspondence`.
- The three legal Playwright suites green. Gap report in the house format.

---

## Out of Scope

- **ML/NER PII detection** (Presidio and similar), **keyword scanning** for special PI, and address detection in free text.
- **Local OCR** of images, and scanning inside document attachments beyond FICA's existing extracted text.
- **Per-matter policy overrides** of the identifier matrix (only the sensitivity flags and "no AI" are per matter).
- **Self-hosted or SA-region models; provider switching.** A1 keeps it a data change later.
- **Negotiating or verifying Anthropic contract terms** (DPA, zero data retention). Kazi records the firm's attestation only.
- **Tokenising firm staff names** (A5).
- **Kazi–firm operator agreement text** (RG-7, a contract task).
- **Changes in `../claude-for-legal-sa`** beyond the marker doc note (a follow-up PR there).
- **POPIA s22 breach register** (still a candidate phase, from the Phase 93 ideation).

---

## ADR topics for `/architecture` (next free number at build; last on disk ADR-396)

1. **One egress guard, two adapters.** The package boundary; the route A hook points (pre-call / post-call-pre-parse); how route B gets a single choke point despite Spring AI M6 having no interceptor (serialisation-level vs `asResult` wrapper); the architecture tests that prevent bypass.
2. **Reversible tokenisation on route A.** Placeholder grammar, the dictionary sources and matching rules, rehydration over parsed JSON/Tiptap, unknown-placeholder handling, and token-map lifetime and encryption. `outputContent` stores tokenised output.
3. **MCP DTO classification.** Annotation vs registry; the free-text masking scope; the withheld-marker contract; NO_AI omission vs non-leaking 404; the enablement-gate change and grace for already-enabled tenants.
4. **Special PI, children and biometric.** Matter flags + template defaults; ID-derived CHILD; images as biometric; the structured unblock basis (Q1/Q3) behind the RG-1 flag; OWNER-only.
5. **Transfer basis record.** s72 grounds, commercial-plan/DPA/zero-data-retention attestations, provider + country as data (A1), versioning and snapshots on events, and the AI-on prerequisite for both routes.
6. **Egress ledger.** The event/subject model, the "no raw values" invariant, the transactional write per route, retention (A4), the DSAR section, and anonymisation preservation.
7. **POPIA document integration.** Processing-register sync, the PAIA transborder part, the engagement-letter clause as s18 notice + s72(1)(b) consent, the wording rules and their lint.

## Style and boundaries

- Follow `backend/CLAUDE.md` and `frontend/CLAUDE.md`. Backend code lives in a new horizontal `egress/` package (or `integration/ai/egress/`, decided by ADR 1). Route adapters live beside their route. POPIA document changes live in `datarequest/`.
- **Hard boundaries to verify at review:**
  - no route A provider call without the pre-call guard;
  - no MCP DTO serialised without policy application;
  - no raw PI in the ledger or in `egress.*` audit params;
  - a blocked execution has zero spend;
  - rehydration happens before any human-visible gate;
  - no "anonymised" wording;
  - tenant-isolation tests are present;
  - full `./mvnw verify` is clean.
- **Estimated shape:** 8 epics, about 16–20 slices. Slice by verify cost (BE+FE together per gap). E2 (rehydration across seven skills) and E3 (classifying about 15 DTOs) are the largest. Architect E1 and the two adapters first.

## Next step

1. `/architecture requirements/claude-code-prompt-phase94.md`, then `/breakdown 94`.
2. In parallel (non-code): put RG-1..RG-9 to a practising attorney (RG-1 and RG-2 first, since they decide whether the unblock flow and FICA vision can ever switch on). Draft the RG-7 operator terms.
3. Follow-up PR in `../claude-for-legal-sa`: document the withheld markers for `kazi-legal-za` skills.
