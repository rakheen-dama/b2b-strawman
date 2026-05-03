package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies V120 schema shape for the AI specialist invocations + LLM calls tables. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V120MigrationTest {
  private static final String ORG_ID = "org_v120_migration_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DataSource dataSource;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "V120 Migration Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_v120_owner", "v120_owner@test.com", "V120 Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void v120CreatesAiSpecialistInvocationsTableWithAllColumns() throws Exception {
    List<String[]> expectedColumns =
        List.of(
            new String[] {"specialist_id", "character varying"},
            new String[] {"invoked_by", "character varying"},
            new String[] {"actor_id", "uuid"},
            new String[] {"automation_action_execution_id", "uuid"},
            new String[] {"context_entity_type", "character varying"},
            new String[] {"context_entity_id", "uuid"},
            new String[] {"status", "character varying"},
            new String[] {"proposed_output", "jsonb"},
            new String[] {"applied_output", "jsonb"},
            new String[] {"created_at", "timestamp with time zone"},
            new String[] {"reviewed_at", "timestamp with time zone"},
            new String[] {"reviewed_by_id", "uuid"},
            new String[] {"reject_reason", "text"},
            new String[] {"error_message", "character varying"},
            new String[] {"prompt_version", "character varying"},
            new String[] {"version", "integer"});

    try (Connection conn = dataSource.getConnection()) {
      for (String[] spec : expectedColumns) {
        try (PreparedStatement ps =
            conn.prepareStatement(
                "SELECT data_type FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = 'ai_specialist_invocations' "
                    + "AND column_name = ?")) {
          ps.setString(1, tenantSchema);
          ps.setString(2, spec[0]);
          ResultSet rs = ps.executeQuery();
          assertThat(rs.next())
              .as("column ai_specialist_invocations." + spec[0] + " should exist")
              .isTrue();
          assertThat(rs.getString("data_type")).isEqualTo(spec[1]);
        }
      }
    }
  }

  @Test
  void v120CreatesAiLlmCallsTableWithAllColumns() throws Exception {
    List<String[]> expectedColumns =
        List.of(
            new String[] {"invocation_id", "uuid"},
            new String[] {"model", "character varying"},
            new String[] {"prompt_version", "character varying"},
            new String[] {"input_tokens", "integer"},
            new String[] {"output_tokens", "integer"},
            new String[] {"cache_read_input_tokens", "integer"},
            new String[] {"cache_creation_input_tokens", "integer"},
            new String[] {"request_id", "character varying"},
            new String[] {"stop_reason", "character varying"},
            new String[] {"latency_ms", "integer"},
            new String[] {"was_vision", "boolean"},
            new String[] {"created_at", "timestamp with time zone"});

    try (Connection conn = dataSource.getConnection()) {
      for (String[] spec : expectedColumns) {
        try (PreparedStatement ps =
            conn.prepareStatement(
                "SELECT data_type FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = 'ai_llm_calls' "
                    + "AND column_name = ?")) {
          ps.setString(1, tenantSchema);
          ps.setString(2, spec[0]);
          ResultSet rs = ps.executeQuery();
          assertThat(rs.next()).as("column ai_llm_calls." + spec[0] + " should exist").isTrue();
          assertThat(rs.getString("data_type")).isEqualTo(spec[1]);
        }
      }
    }
  }

  @Test
  void v120CreatesAllInvocationIndexes() throws Exception {
    List<String> expectedIndexes =
        List.of(
            "idx_invocation_status_created",
            "idx_invocation_context",
            "idx_invocation_action_execution",
            "idx_invocation_specialist_status",
            "idx_invocation_actor_created");

    try (Connection conn = dataSource.getConnection()) {
      for (String indexName : expectedIndexes) {
        try (PreparedStatement ps =
            conn.prepareStatement(
                "SELECT COUNT(*) FROM pg_indexes "
                    + "WHERE schemaname = ? AND tablename = 'ai_specialist_invocations' "
                    + "AND indexname = ?")) {
          ps.setString(1, tenantSchema);
          ps.setString(2, indexName);
          ResultSet rs = ps.executeQuery();
          assertThat(rs.next()).isTrue();
          assertThat(rs.getInt(1)).as("index " + indexName + " should exist").isEqualTo(1);
        }
      }
    }
  }

  @Test
  void v120AddsLastRunAtToAutomationRules() throws Exception {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT data_type FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = 'automation_rules' "
                    + "AND column_name = 'last_run_at'")) {
      ps.setString(1, tenantSchema);
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("data_type")).isEqualTo("timestamp with time zone");
    }
  }

  @Test
  void v120AiLlmCallsCascadeDeletesWhenInvocationDeleted() throws Exception {
    try (Connection conn = dataSource.getConnection();
        java.sql.Statement st = conn.createStatement()) {
      st.execute("SET search_path TO " + tenantSchema);
      String invId = java.util.UUID.randomUUID().toString();
      String callId = java.util.UUID.randomUUID().toString();
      String actorId = java.util.UUID.randomUUID().toString();
      String contextId = java.util.UUID.randomUUID().toString();
      try (PreparedStatement ins =
          conn.prepareStatement(
              "INSERT INTO ai_specialist_invocations "
                  + "(id, specialist_id, invoked_by, actor_id, context_entity_type, "
                  + "context_entity_id, status, created_at) "
                  + "VALUES (?::uuid, 'billing-za', 'MEMBER', ?::uuid, 'invoice', ?::uuid, "
                  + "'RUNNING', now())")) {
        ins.setString(1, invId);
        ins.setString(2, actorId);
        ins.setString(3, contextId);
        ins.executeUpdate();
      }
      try (PreparedStatement ins =
          conn.prepareStatement(
              "INSERT INTO ai_llm_calls "
                  + "(id, invocation_id, model, input_tokens, output_tokens, "
                  + "cache_read_input_tokens, cache_creation_input_tokens, was_vision, created_at) "
                  + "VALUES (?::uuid, ?::uuid, 'claude-sonnet-4-6', 100, 50, 0, 0, false, now())")) {
        ins.setString(1, callId);
        ins.setString(2, invId);
        ins.executeUpdate();
      }
      // Sanity: the call exists.
      try (PreparedStatement check =
          conn.prepareStatement("SELECT COUNT(*) FROM ai_llm_calls WHERE id = ?::uuid")) {
        check.setString(1, callId);
        ResultSet rs = check.executeQuery();
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(1);
      }
      // Delete parent — child should cascade.
      try (PreparedStatement del =
          conn.prepareStatement("DELETE FROM ai_specialist_invocations WHERE id = ?::uuid")) {
        del.setString(1, invId);
        del.executeUpdate();
      }
      try (PreparedStatement check =
          conn.prepareStatement("SELECT COUNT(*) FROM ai_llm_calls WHERE id = ?::uuid")) {
        check.setString(1, callId);
        ResultSet rs = check.executeQuery();
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isEqualTo(0);
      }
    }
  }
}
