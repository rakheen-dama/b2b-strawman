\echo '-- Q0 tenant schema'
SELECT external_org_id, schema_name, created_at FROM public.org_schema_mapping;
\echo '-- Q1 pack_install'
SELECT pack_id, pack_type, pack_version, pack_name, installed_at, item_count FROM tenant_7d218705360b.pack_install ORDER BY pack_type, pack_id;
\echo '-- Q1b org_settings pack status jsonb'
SELECT field_pack_status, template_pack_status, compliance_pack_status, report_pack_status, clause_pack_status, request_pack_status, automation_pack_status, project_template_pack_status FROM tenant_7d218705360b.org_settings;
\echo '-- Q2 field_groups name/description ~* project|customer|invoice'
SELECT entity_type, pack_id, slug, name, description FROM tenant_7d218705360b.field_groups WHERE name ~* '(project|customer|invoice)' OR description ~* '(project|customer|invoice)' ORDER BY pack_id, slug;
\echo '-- Q3 field_definitions name/description/options ~*'
SELECT entity_type, pack_id, pack_field_key, name, description, options::text FROM tenant_7d218705360b.field_definitions WHERE name ~* '(project|customer|invoice)' OR description ~* '(project|customer|invoice)' OR options::text ~* '(project|customer|invoice)' ORDER BY pack_id, pack_field_key;
\echo '-- Q4 report_definitions name/description/category/parameter_schema'
SELECT slug, name, description, category FROM tenant_7d218705360b.report_definitions ORDER BY sort_order;
\echo '-- Q4b report_definitions parameter_schema/column_definitions hits'
SELECT slug, parameter_schema::text AS params FROM tenant_7d218705360b.report_definitions WHERE parameter_schema::text ~* '(project|customer|invoice)';
SELECT slug, column_definitions::text AS cols FROM tenant_7d218705360b.report_definitions WHERE column_definitions::text ~* '(project|customer|invoice)';
\echo '-- Q4c report template_body title/heading hits (count of matches)'
SELECT slug, (SELECT count(*) FROM regexp_matches(template_body, '\m(Projects?|Customers?|Invoices?)\M', 'g')) AS capitalised_hits FROM tenant_7d218705360b.report_definitions;
\echo '-- Q5 request_templates'
SELECT pack_id, source, name, description FROM tenant_7d218705360b.request_templates ORDER BY pack_id, name;
\echo '-- Q5b request_template_items hits'
SELECT t.name AS template, i.name, i.description FROM tenant_7d218705360b.request_template_items i JOIN tenant_7d218705360b.request_templates t ON t.id=i.template_id WHERE i.name ~* '(project|customer|invoice)' OR i.description ~* '(project|customer|invoice)';
\echo '-- Q6 automation_rules'
SELECT source, template_slug, enabled, trigger_type, name, description FROM tenant_7d218705360b.automation_rules ORDER BY template_slug;
\echo '-- Q6b automation_actions config hits'
SELECT r.template_slug, a.* FROM tenant_7d218705360b.automation_actions a JOIN tenant_7d218705360b.automation_rules r ON r.id=a.rule_id WHERE a::text ~* '(project|customer|invoice)';
\echo '-- Q7 document_templates name/description hits'
SELECT pack_id, pack_template_key, category, primary_entity_type, name, description FROM tenant_7d218705360b.document_templates WHERE name ~* '(project|customer|invoice)' OR description ~* '(project|customer|invoice)' ORDER BY pack_id;
\echo '-- Q7b all document_templates names'
SELECT pack_id, name, description FROM tenant_7d218705360b.document_templates ORDER BY pack_id, sort_order;
\echo '-- Q8 checklist_templates hits'
SELECT pack_id, name, description FROM tenant_7d218705360b.checklist_templates WHERE name ~* '(project|customer|invoice)' OR description ~* '(project|customer|invoice)';
\echo '-- Q9 project_templates hits'
SELECT * FROM tenant_7d218705360b.project_templates WHERE project_templates::text ~* '(project|customer|invoice)' LIMIT 20;
