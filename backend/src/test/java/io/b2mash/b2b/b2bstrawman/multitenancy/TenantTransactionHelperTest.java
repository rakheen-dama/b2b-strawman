package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * D4 regression: {@link TenantTransactionHelper} must bind {@code SHARD_ID} so a standalone caller
 * (no enclosing {@code runForTenantOnShard}) cannot silently route a secondary-shard tenant to the
 * primary database. Verifies the three binding paths: standalone defaults to primary, the 2-arg
 * variant inherits an enclosing shard, and the 3-arg variant binds the explicit shard. See
 * kazi-infra-review-scheduling-sharding.md finding D4.
 *
 * <p>Runs with sharding disabled (default), which is sufficient: the {@code SHARD_ID} ScopedValue
 * is bound regardless of {@code kazi.sharding.enabled} — only the {@code TenantIdentifierResolver}
 * gates on the flag — so the binding behaviour is observable here.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantTransactionHelperTest {

  private static final String ORG_ID = "org_tth_d4";

  private final TenantProvisioningService provisioningService;
  private final TenantTransactionHelper helper;

  private String schema;

  @Autowired
  TenantTransactionHelperTest(
      TenantProvisioningService provisioningService, TenantTransactionHelper helper) {
    this.provisioningService = provisioningService;
    this.helper = helper;
  }

  @BeforeAll
  void provisionTenant() {
    schema = provisioningService.provisionTenant(ORG_ID, "TTH D4 Org", null).schemaName();
  }

  @Test
  void standaloneCall_bindsPrimaryShard() {
    var seen = new AtomicReference<String>();
    helper.executeInTenantTransaction(schema, ORG_ID, t -> seen.set(boundShard()));
    assertThat(seen.get()).isEqualTo("primary");
  }

  @Test
  void twoArgCall_inheritsEnclosingShard() {
    var seen = new AtomicReference<String>();
    RequestScopes.runForTenantOnShard(
        schema,
        ORG_ID,
        "kazi_legal_1",
        () -> helper.executeInTenantTransaction(schema, ORG_ID, t -> seen.set(boundShard())));
    assertThat(seen.get()).isEqualTo("kazi_legal_1");
  }

  @Test
  void threeArgCall_bindsExplicitShard() {
    var seen = new AtomicReference<String>();
    helper.executeInTenantTransaction(schema, ORG_ID, "kazi_legal_2", t -> seen.set(boundShard()));
    assertThat(seen.get()).isEqualTo("kazi_legal_2");
  }

  private static String boundShard() {
    return RequestScopes.SHARD_ID.isBound() ? RequestScopes.SHARD_ID.get() : null;
  }
}
