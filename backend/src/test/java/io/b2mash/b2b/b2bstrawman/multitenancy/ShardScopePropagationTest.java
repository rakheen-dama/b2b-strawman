package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.servlet.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration tests verifying that SHARD_ID is correctly propagated through the HTTP request path
 * (TenantFilter), the scheduled job path (TenantScopedRunner), and the programmatic
 * runForTenantOnShard API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// @TestPropertySource is required here: application-test.yml disables sharding globally, but this
// test class needs the ShardAwareConnectionProvider and ShardRegistry beans active. This genuinely
// varies per test class per the anti-pattern policy in backend/CLAUDE.md.
@TestPropertySource(properties = "kazi.sharding.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShardScopePropagationTest {

  private static final String ORG_ID = "org_shard_scope_test";

  private final MockMvc mockMvc;
  private final TenantProvisioningService provisioningService;
  private final TenantFilter tenantFilter;
  private final TenantScopedRunner tenantScopedRunner;
  private final WebApplicationContext wac;

  @Autowired
  ShardScopePropagationTest(
      MockMvc mockMvc,
      TenantProvisioningService provisioningService,
      TenantFilter tenantFilter,
      TenantScopedRunner tenantScopedRunner,
      WebApplicationContext wac) {
    this.mockMvc = mockMvc;
    this.provisioningService = provisioningService;
    this.tenantFilter = tenantFilter;
    this.tenantScopedRunner = tenantScopedRunner;
    this.wac = wac;
  }

  @BeforeAll
  void provisionTenant() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Shard Scope Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_shard_scope_owner", "shard_scope@test.com", "Shard Owner", "owner");
  }

  @Test
  void runForTenantOnShard_bindsShardId() {
    AtomicReference<String> captured = new AtomicReference<>();
    RequestScopes.runForTenantOnShard(
        "tenant_a1b2c3d4e5f6",
        "org_test",
        "primary",
        () -> captured.set(RequestScopes.SHARD_ID.get()));

    assertThat(captured.get()).isEqualTo("primary");
  }

  @Test
  void tenantFilter_resolvesAndBindsShardId() throws Exception {
    var jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_shard_scope_owner");

    // Evict cache to force a fresh DB lookup
    tenantFilter.evictSchema(ORG_ID);

    // Capture SHARD_ID from within the request scope using a filter that runs
    // inside TenantFilter's ScopedValue carrier.
    AtomicReference<String> capturedShardId = new AtomicReference<>();
    Filter shardCapture =
        (request, response, chain) -> {
          if (RequestScopes.SHARD_ID.isBound()) {
            capturedShardId.set(RequestScopes.SHARD_ID.get());
          }
          chain.doFilter(request, response);
        };

    MockMvc capturingMvc =
        MockMvcBuilders.webAppContextSetup(wac)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .addFilter(shardCapture, "/*")
            .build();

    capturingMvc.perform(get("/api/projects").with(jwt)).andExpect(status().isOk());

    assertThat(capturedShardId.get())
        .as("SHARD_ID should be bound to 'primary' during the HTTP request")
        .isEqualTo("primary");
  }

  @Test
  void tenantFilter_cacheHitIncludesShardId() throws Exception {
    var jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_shard_scope_owner");

    // Evict and make first request to populate cache
    tenantFilter.evictSchema(ORG_ID);

    AtomicReference<String> capturedShardId = new AtomicReference<>();
    Filter shardCapture =
        (request, response, chain) -> {
          if (RequestScopes.SHARD_ID.isBound()) {
            capturedShardId.set(RequestScopes.SHARD_ID.get());
          }
          chain.doFilter(request, response);
        };

    MockMvc capturingMvc =
        MockMvcBuilders.webAppContextSetup(wac)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .addFilter(shardCapture, "/*")
            .build();

    // First request populates the cache
    capturingMvc.perform(get("/api/projects").with(jwt)).andExpect(status().isOk());
    assertThat(capturedShardId.get())
        .as("SHARD_ID should be bound on first (cache-miss) request")
        .isEqualTo("primary");

    // Reset capture and make second request — should hit cache
    capturedShardId.set(null);
    capturingMvc.perform(get("/api/projects").with(jwt)).andExpect(status().isOk());

    assertThat(capturedShardId.get())
        .as("SHARD_ID should still be bound on cache-hit request")
        .isEqualTo("primary");
  }

  @Test
  void tenantScopedRunner_bindsShardIdPerIteration() {
    List<String> capturedShardIds = new ArrayList<>();

    tenantScopedRunner.forEachTenant(
        (tenantId, orgId) -> {
          assertThat(RequestScopes.SHARD_ID.isBound())
              .as("SHARD_ID should be bound for tenant=%s org=%s", tenantId, orgId)
              .isTrue();
          capturedShardIds.add(RequestScopes.SHARD_ID.get());
        });

    // At least one tenant was provisioned in @BeforeAll
    assertThat(capturedShardIds).isNotEmpty();
    // All provisioned tenants default to "primary" shard
    assertThat(capturedShardIds).allMatch("primary"::equals);
  }

  @Test
  void nestedRunForTenantOnShard_correctlyRebindsShardId() {
    AtomicReference<String> outerShard = new AtomicReference<>();
    AtomicReference<String> innerShard = new AtomicReference<>();
    AtomicReference<String> outerShardAfterNesting = new AtomicReference<>();

    RequestScopes.runForTenantOnShard(
        "tenant_a1b2c3d4e5f6",
        "org_outer",
        "primary",
        () -> {
          outerShard.set(RequestScopes.SHARD_ID.get());

          RequestScopes.runForTenantOnShard(
              "tenant_112233445566",
              "org_inner",
              "kazi_legal_1",
              () -> innerShard.set(RequestScopes.SHARD_ID.get()));

          outerShardAfterNesting.set(RequestScopes.SHARD_ID.get());
        });

    assertThat(outerShard.get()).isEqualTo("primary");
    assertThat(innerShard.get()).isEqualTo("kazi_legal_1");
    assertThat(outerShardAfterNesting.get())
        .as("Outer shard should be restored after nested call exits")
        .isEqualTo("primary");
  }
}
