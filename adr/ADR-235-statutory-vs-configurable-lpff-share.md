# ADR-235: Statutory vs Configurable LPFF Share for Section 86(4) Investments

**Status**: Accepted

**Context**:

The Legal Practice Act, 2014 defines two distinct mechanisms for investing trust money. Section 86(3) allows a firm to invest surplus trust funds of its own accord — the interest split between clients and the Legal Practitioners Fidelity Fund follows the general LPFF arrangement rate, which is published by the LPFF via circulars and can change over time. This rate is already stored in the `LpffRate` table (per trust account, with effective-from dates) and is configurable by the firm admin.

Section 86(4) allows a firm to invest on a specific client's instruction, in a separate account for that client. Section 86(5) prescribes that interest on these investments must be paid to the client, with **exactly 5% going to the LPFF**. This is a statutory rate — it is not the general LPFF arrangement rate, it is not negotiable, and it is not variable per trust account.

The question is how to represent this 5% rate in the system. The general LPFF arrangement rate already has a well-established storage pattern (the `LpffRate` table with effective-from dates and rate percentages). One approach would be to extend this pattern for the statutory rate. Another would be to treat it differently, reflecting its fundamentally different legal character.

**Options Considered**:

1. **Hardcode as a constant in the interest calculation service** — Define `STATUTORY_LPFF_SHARE_PERCENT = new BigDecimal("0.05")` in `InterestCalculationService` or a shared `TrustAccountingConstants` class. The interest calculation service checks the investment's `investment_basis` field: for `CLIENT_INSTRUCTION`, it uses the constant directly; for `FIRM_DISCRETION`, it reads from the `LpffRate` table as today.
   - Pros:
     - Accurately reflects the legal nature of the rate: it is a statutory prescription, not a firm-configurable policy. The code makes this distinction explicit — a developer reading the interest calculation logic sees a constant with a comment citing the section, not a database lookup.
     - Zero risk of accidental misconfiguration. An admin cannot change the 5% to 4% or 10%. An auditor reviewing the system can verify that the statutory rate is correct by reading one line of code.
     - Simplest implementation: no schema changes to the rate table, no special-case records, no migration to seed a "statutory" rate row.
     - The rate has not changed since the Legal Practice Act was enacted in 2014. Changing it would require a parliamentary process (amendment to primary legislation). The likelihood of a code change being needed in the next decade is negligible.
   - Cons:
     - If the statute ever changes the 5% rate, it requires a code change and redeployment. This cannot be updated by a firm admin or a platform operator without a software release.
     - Does not appear in the `LpffRate` table, so a report querying only that table for "all applicable rates" would not surface the statutory rate. The reporting layer must be aware that 86(4) investments use a different rate source.

2. **Store in the LpffRate table as a special "statutory" rate type** — Add a `rate_type` column to `lpff_rates` (values: `ARRANGEMENT` for the current rates, `STATUTORY` for the 86(5) rate). Seed a statutory rate row per trust account during provisioning. The interest service reads the statutory rate from the same table as the arrangement rate.
   - Pros:
     - All rates live in one table. A single query returns both the general arrangement rate and the statutory rate. Reports that list "all LPFF rates" get both automatically.
     - Rate changes (if they ever happen) can be applied without a code change — insert a new row with the new statutory rate and a future effective-from date.
     - Consistent data model: both rate types follow the same pattern (effective-from, percentage, linked to trust account).
   - Cons:
     - Creates the false impression that the statutory rate is configurable. The table already has admin CRUD endpoints — nothing prevents someone from editing or deleting the statutory rate row. This would create a compliance breach that is silent (no error, just wrong interest allocation) until an auditor catches it.
     - Requires seeding a statutory rate row for every trust account. If the seed fails or is missed, the interest calculation would fall back to the arrangement rate for 86(4) investments — silently wrong.
     - Overloads the `LpffRate` table with a fundamentally different kind of rate. The arrangement rate changes when the LPFF publishes a circular (a firm admin action). The statutory rate changes when Parliament amends the Act (a legislative event). These are not the same thing and should not be stored the same way.
     - Schema change (add `rate_type` column), migration to update existing rows, seeder update — more work for a rate that is already known and fixed.

3. **Application configuration property (application.yml)** — Store the 5% as `trust.statutory-lpff-share-percent: 0.05` in the application configuration. Injected into the interest service via `@Value`. Changeable per environment without code changes.
   - Pros:
     - No database changes. The rate is read from configuration at startup.
     - Can be overridden per environment (dev, staging, production) if needed for testing.
     - Follows Spring Boot conventions for externalized configuration.
   - Cons:
     - Same risk as Option 2: the value can be changed by anyone with access to the deployment configuration. A misconfigured staging environment that accidentally gets promoted to production would apply the wrong rate.
     - The rate is not per-tenant — it applies to all tenants equally (it is a national statute). Storing it in `application.yml` is appropriate for this, but it normalizes the idea that statutory rates are "configuration" rather than "law."
     - An auditor asking "where does the 5% come from?" gets pointed at a YAML file rather than a line of code with a statutory citation. The YAML file is less self-documenting.
     - No meaningful advantage over a constant: the rate is not expected to change, and if it does, a new deployment is needed regardless (to update tests, documentation, audit reports, and the rate itself).

**Decision**: Option 1 — Hardcode as a constant.

**Rationale**:

The 5% LPFF share for client-instructed investments (Section 86(5)) is a statutory prescription. It has the force of law. It is not a policy decision made by the firm, the LPFF, or the platform. It cannot be negotiated, overridden, or varied by trust account. Making it editable — whether via a database table (Option 2) or a configuration file (Option 3) — creates a surface for non-compliance that does not need to exist.

The risk calculus is asymmetric. The downside of hardcoding is that a future statutory change requires a code change. But a change to Section 86(5) would require amendment of the Legal Practice Act by Parliament — a process that takes years, involves public consultation, and would be known well in advance. The DocTeams development team would have ample time to update a constant. The downside of making the rate configurable is that a misconfiguration (accidental edit, failed seed, wrong YAML value) silently applies the wrong LPFF share. This would not be caught until an auditor reviews the interest allocations — potentially months later. The compliance exposure is material.

Option 2 also conflates two fundamentally different types of rates. The `LpffRate` table stores the general LPFF arrangement rate, which changes when the LPFF publishes a new circular (roughly annually). The statutory 5% changes when Parliament amends the Act (has not happened since 2014). Mixing these in one table obscures the distinction that the Legal Practice Act draws explicitly.

The constant is defined as:

```java
public static final BigDecimal STATUTORY_LPFF_SHARE_PERCENT = new BigDecimal("0.05"); // Section 86(5)
```

The comment cites the statutory section. The variable name includes "STATUTORY" to distinguish it from the configurable rate. The interest calculation service checks the investment's `investment_basis` field and branches accordingly — this conditional is the implementation of the statutory distinction, and it should be visible in the code, not hidden behind a database lookup.

**Consequences**:

- The interest calculation service contains a conditional: `CLIENT_INSTRUCTION` investments use `STATUTORY_LPFF_SHARE_PERCENT`; `FIRM_DISCRETION` investments use the `LpffRate` table. This conditional is the core of the Section 86 distinction and must be tested thoroughly.
- Reports that display LPFF rates must be aware of two sources: the `LpffRate` table for arrangement rates and the constant for the statutory rate. The investment register and interest allocation table should display "5% (statutory)" for 86(4) investments — not a blank or a database-looked-up value.
- If Parliament amends Section 86(5) to change the 5%, a code change is required. The team should monitor the Legal Practice Council's gazette for legislative amendments. In practice, this rate has been stable for over a decade.
- `InterestAllocation` records for 86(4) investments have `lpff_rate_id = NULL` and `statutory_rate_applied = true`, providing an auditable distinction from 86(3) allocations that reference a specific `LpffRate` row.
- Related: [ADR-234](ADR-234-interest-daily-balance-method.md) (interest calculation method)
