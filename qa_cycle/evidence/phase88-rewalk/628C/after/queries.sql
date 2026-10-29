\echo -- A1 pack_install
SELECT pack_id, pack_type, pack_version, installed_at, item_count FROM tenant_7d218705360b.pack_install ORDER BY pack_type, pack_id;
\echo -- A1b org_settings pack status (versions only)
SELECT 'field' k, jsonb_path_query_array(field_pack_status::jsonb,'$[*].version') v, jsonb_path_query_array(field_pack_status::jsonb,'$[*].packId') p FROM tenant_7d218705360b.org_settings
UNION ALL SELECT 'template', jsonb_path_query_array(template_pack_status::jsonb,'$[*].version'), jsonb_path_query_array(template_pack_status::jsonb,'$[*].packId') FROM tenant_7d218705360b.org_settings
UNION ALL SELECT 'report', jsonb_path_query_array(report_pack_status::jsonb,'$[*].version'), jsonb_path_query_array(report_pack_status::jsonb,'$[*].packId') FROM tenant_7d218705360b.org_settings
UNION ALL SELECT 'clause', jsonb_path_query_array(clause_pack_status::jsonb,'$[*].version'), jsonb_path_query_array(clause_pack_status::jsonb,'$[*].packId') FROM tenant_7d218705360b.org_settings
UNION ALL SELECT 'automation', jsonb_path_query_array(automation_pack_status::jsonb,'$[*].version'), jsonb_path_query_array(automation_pack_status::jsonb,'$[*].packId') FROM tenant_7d218705360b.org_settings;
\echo -- A2 field_groups PROJECT
SELECT pack_id, slug, name, description, updated_at FROM tenant_7d218705360b.field_groups WHERE entity_type='PROJECT' ORDER BY pack_id, slug;
\echo -- A2b field_groups legacy-word hits (all entity types)
SELECT entity_type, pack_id, slug, name, description FROM tenant_7d218705360b.field_groups WHERE name ~* '(project|customer|invoice)' OR description ~* '(project|customer|invoice)';
\echo -- A3 field_definitions legacy hits + category
SELECT entity_type, pack_id, pack_field_key, name, description FROM tenant_7d218705360b.field_definitions WHERE name ~* '(project|customer|invoice)' OR description ~* '(project|customer|invoice)' OR options::text ~* '(project|customer|invoice)' OR (pack_id='common-project' AND pack_field_key='category');
\echo -- A4 report_definitions
SELECT slug, name, description, category FROM tenant_7d218705360b.report_definitions ORDER BY sort_order;
\echo -- A4b parameter_schema
SELECT slug, parameter_schema::text FROM tenant_7d218705360b.report_definitions ORDER BY sort_order;
\echo -- A4c column_definitions
SELECT slug, column_definitions::text FROM tenant_7d218705360b.report_definitions ORDER BY sort_order;
\echo -- A4d template_body capitalised legacy words in context (was 11 rows)
SELECT slug, m[1] AS context FROM tenant_7d218705360b.report_definitions, regexp_matches(template_body, '([^<>]{0,40}\m(?:Projects?|Customers?|Invoices?)\M[^<>]{0,30})', 'g') AS m;
\echo -- A5 request templates hits
SELECT pack_id, name FROM tenant_7d218705360b.request_templates WHERE name ~* '(project|customer|invoice)' OR description ~* '(project|customer|invoice)';
\echo -- A6 automation_rules
SELECT template_slug, name, description, updated_at FROM tenant_7d218705360b.automation_rules ORDER BY template_slug;
\echo -- A6b automation_actions configs for the two reconciled rules
SELECT r.template_slug, a.sort_order, a.action_config::text FROM tenant_7d218705360b.automation_actions a JOIN tenant_7d218705360b.automation_rules r ON r.id=a.rule_id WHERE r.template_slug IN ('overdue-invoice-reminder','budget-alert-escalation') ORDER BY 1,2;
\echo -- A7 document_templates common
SELECT pack_id, pack_template_key, name, description, encode(sha256(convert_to(content::text,'UTF8')),'hex') AS content_sha_text, updated_at FROM tenant_7d218705360b.document_templates WHERE pack_id='common' ORDER BY sort_order;
\echo -- A7b document_templates legacy name/desc hits
SELECT pack_id, name, description FROM tenant_7d218705360b.document_templates WHERE name ~* '(project|customer|invoice)' OR description ~* '(project|customer|invoice)';
\echo -- A7c common template content capitalised legacy words outside {{ }}
SELECT pack_template_key, m[1] FROM tenant_7d218705360b.document_templates, regexp_matches(content::text, '"text": "([^"]{0,40}\m(?:Projects?|Customers?|Invoices?)\M[^"]{0,30})"', 'g') AS m WHERE pack_id='common';
\echo -- A8 clauses legacy-word hits (body text)
SELECT pack_id, slug, title FROM tenant_7d218705360b.clauses WHERE body::text ~* '\m(project|customer|invoice)s?\M' ORDER BY pack_id, slug;
