# 655A — found outside the slice's own scope

## F-655A-1 — dormancy check threw for every client with old activity (fixed in 655A, see why)

`CustomerLifecycleService.runDormancyCheck` cast the native query's `last_activity` to `java.sql.Timestamp`; Hibernate 7
returns `java.time.Instant`, so the check threw `ClassCastException` for the whole tenant as soon as one ACTIVE client's
latest activity row was older than the threshold. On main only clients with no activity at all could ever go dormant,
and the 02:00 job failed for any tenant holding an idle client with history. No test covered a non-null
`last_activity`.

Reproduced on the branch before the fix with a case that uses no take-on code (`ImportedClientDormancyIntegrationTest.
clientWithActivityOlderThanTheThreshold_goesDormant`, evidence in `characterisation.md` run 2).

Why it is fixed in 655A rather than re-specced: 655A makes the take-on instant dormancy activity (R-9), so every
imported client idle for the threshold takes exactly this path; the slice's own acceptance case (655.8: "`taken_on_at`
moved back 91 days and no activity → DORMANT") cannot pass without it. The fix is one helper (`toInstant`) in the method
655A already changes. Logged as a D-row. The orchestrator may split it out if preferred.

## F-655A-2 — the UI create path dedups email case-sensitively (not fixed — outside the slice)

Found while answering CodeRabbit CR-1/CR-5 on PR #1850 (D-129). `CustomerService.createCustomer` refuses a duplicate
with `existsByEmail` (case-sensitive) and `uq_customers_email` is case-sensitive, so the UI can already create
`Foo@x.test` beside `foo@x.test`; nothing anywhere enforces name + tax-number uniqueness. The take-on's own writers
cannot race each other (one COMMITTING batch per tenant, in-file repeats are ERROR rows, `uq_take_on_rows_created_key`
on the lower-cased key), but a client created in the UI in the same instant as a take-on row with a case-variant email
can still pass both checks. A case-folded unique rule on `customers.email` would need existing rows triaged first —
a product/data decision, not a 655A change.
