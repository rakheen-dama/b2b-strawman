\echo -- A8b clause body text nodes containing legacy words (tokens {{..}} stripped)
SELECT c.pack_id, c.slug, substring(regexp_replace(t.txt, '\{\{[^}]*\}\}', '', 'g') from '(.{0,50}\m(?:[Pp]rojects?|[Cc]ustomers?|[Ii]nvoices?)\M.{0,40})') AS ctx
FROM tenant_7d218705360b.clauses c,
 LATERAL (SELECT string_agg(m[1], ' ') AS txt FROM regexp_matches(c.body::text, '"text": "([^"]*)"', 'g') m) t
WHERE regexp_replace(t.txt, '\{\{[^}]*\}\}', '', 'g') ~ '\m(?:[Pp]rojects?|[Cc]ustomers?|[Ii]nvoices?)\M'
ORDER BY 1,2;
\echo -- A8c clause body updated_at for the 3 reconciled clauses
SELECT pack_id, slug, updated_at FROM tenant_7d218705360b.clauses WHERE slug IN ('legal-za-fees-hourly','payment-terms','fee-schedule');
