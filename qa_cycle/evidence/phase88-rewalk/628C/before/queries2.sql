\echo '-- Q10 report template_body capitalised legacy words in context'
SELECT slug, m[1] AS context FROM tenant_7d218705360b.report_definitions, regexp_matches(template_body, '([^<>]{0,40}\m(?:Projects?|Customers?|Invoices?)\M[^<>]{0,30})', 'g') AS m;
\echo '-- Q11 full parameter_schema (labels/options) of the 3 reports'
SELECT slug, jsonb_pretty(parameter_schema) FROM tenant_7d218705360b.report_definitions;
\echo '-- Q12 column labels with legacy words'
SELECT slug, c->>'key' AS key, c->>'label' AS label FROM tenant_7d218705360b.report_definitions, jsonb_array_elements(column_definitions->'columns') c WHERE c->>'label' ~* '(project|customer|invoice)';
\echo '-- Q13 checklist_template_items hits'
SELECT t.name, i.name, i.description FROM tenant_7d218705360b.checklist_template_items i JOIN tenant_7d218705360b.checklist_templates t ON t.id=i.template_id WHERE i.name ~* '(project|customer|invoice)' OR i.description ~* '(project|customer|invoice)';
\echo '-- Q14 template_tasks hits'
SELECT name, description FROM tenant_7d218705360b.template_tasks WHERE name ~* '(project|customer|invoice)' OR description ~* '(project|customer|invoice)';
\echo '-- Q15 field_groups all (entity_type + names)'
SELECT entity_type, pack_id, name, description FROM tenant_7d218705360b.field_groups ORDER BY entity_type, pack_id;
