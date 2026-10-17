-- V142__drop_assistant.sql
-- Phase 86 (Epic 601B, ADR-331 / ADR-333): remove the Phase 45/52 in-app chat assistant and the
-- Phase 70 AI specialists — ai_specialist_invocations / ai_llm_calls, the INVOKE_AI_SPECIALIST
-- automation action, the three ai-specialist-* automation packs and the AI_ASSISTANT_USE
-- capability. The chat-provider package (LlmChatProvider*) is re-homed to integration/ai/provider
-- in the same PR and owns no schema. Throwaway migration — squashed into tenant V1 at zero tenants
-- (ADR-331); no production data exists.

-- 1. Enum-string row cleanup BEFORE the Java constant disappears (595B §4).
--    automation_actions.action_type is @Enumerated(STRING) ActionType (AutomationAction.java:30-32).
--    Rules seeded from automation-templates/ai-specialist-{common,legal-za,consulting-za}.json carry
--    a single INVOKE_AI_SPECIALIST action. Delete rules whose ONLY actions are specialist actions
--    (actions + executions cascade, V60:23,42); rules that also carry other actions keep them — the
--    engine tolerates action-less rules (H15: AutomationEventListener:180,
--    AutomationRuleService:331), so pre-existing action-less user rules are deliberately untouched.
--    action_executions.action_id is ON DELETE SET NULL (V60:57), so execution history survives.
DELETE FROM automation_rules r
 WHERE EXISTS (SELECT 1 FROM automation_actions a
                WHERE a.rule_id = r.id AND a.action_type = 'INVOKE_AI_SPECIALIST')
   AND NOT EXISTS (SELECT 1 FROM automation_actions a
                    WHERE a.rule_id = r.id AND a.action_type <> 'INVOKE_AI_SPECIALIST');
DELETE FROM automation_actions WHERE action_type = 'INVOKE_AI_SPECIALIST';

-- 2. The three ai-specialist-* pack JSONs leave the classpath in the same PR. Their pack_install
--    rows (V94; table is pack_install, singular) would otherwise be listed as installed by
--    PackCatalogService.listInstalled() via its "removed from classpath" fallback.
--    automation_rules.source_pack_install_id is ON DELETE SET NULL (V94:37-39).
DELETE FROM pack_install
 WHERE pack_id IN ('ai-specialist-common', 'ai-specialist-legal-za', 'ai-specialist-consulting-za');

-- 3. Defensive: AI_ASSISTANT_USE leaves the Capability enum (org_role_capabilities.capability is
--    @Enumerated(STRING), OrgRole.java:41-45). No migration seeds it (V122 seeds only
--    AI_MANAGE/AI_EXECUTE/AI_REVIEW); only role-editor grants on dev volumes can carry it.
DELETE FROM org_role_capabilities WHERE capability = 'AI_ASSISTANT_USE';

-- 4. Tables (V120; child first). ai_llm_calls.invocation_id -> ai_specialist_invocations is
--    ON DELETE CASCADE; ai_specialist_invocations.automation_action_execution_id ->
--    action_executions is outbound only. All idx_ai_llm_calls_* / idx_invocation_* indexes go with
--    the tables. automation_rules.last_run_at (also added by V120) is KEPT — the SCHEDULED trigger
--    uses it.
DROP TABLE IF EXISTS ai_llm_calls;
DROP TABLE IF EXISTS ai_specialist_invocations;
