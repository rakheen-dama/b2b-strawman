package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 92 slice 646A (task 646.2, ADR-378): the database's {@code chk_trust_txn_type} lists
 * exactly the registry's types — no more (a type the code cannot post) and no fewer (a type the
 * database would refuse). Read from {@code pg_constraint} scoped to a freshly provisioned tenant
 * schema, so it checks what the tenant migrations actually build. ({@code @AutoConfigureMockMvc}
 * only to share the common test context.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustTransactionTypeSchemaTest {

  private static final String ORG_ID = "org_trust_txn_type_schema";

  /**
   * A quoted SQL literal in the constraint definition, e.g. {@code 'DEPOSIT'::character varying}.
   */
  private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Txn Type Schema Org", null).schemaName();
  }

  @Test
  void chkTrustTxnType_listsExactlyTheRegistryTypes() {
    String definition =
        jdbcTemplate.queryForObject(
            """
            SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c
              JOIN pg_namespace n ON n.oid = c.connamespace
             WHERE c.conname = 'chk_trust_txn_type' AND n.nspname = ?
            """,
            String.class,
            tenantSchema);
    assertThat(definition).as("chk_trust_txn_type in %s", tenantSchema).isNotNull();

    Set<String> constrained = new LinkedHashSet<>();
    var matcher = QUOTED.matcher(definition);
    while (matcher.find()) {
      constrained.add(matcher.group(1));
    }
    Set<String> registry =
        Arrays.stream(TrustTransactionType.values()).map(Enum::name).collect(Collectors.toSet());

    assertThat(constrained)
        .as("chk_trust_txn_type (%s) vs TrustTransactionType.values()", definition)
        .containsExactlyInAnyOrderElementsOf(registry);
  }
}
