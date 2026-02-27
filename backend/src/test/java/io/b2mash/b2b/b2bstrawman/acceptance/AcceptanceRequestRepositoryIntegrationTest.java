package io.b2mash.b2b.b2bstrawman.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcceptanceRequestRepositoryIntegrationTest {

  private static final String ORG_ID = "org_acceptance_repo_test";
  private static final UUID DOC_ID = UUID.randomUUID();
  private static final UUID CONTACT_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private AcceptanceRequestRepository repository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Acceptance Repo Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> transactionTemplate.executeWithoutResult(tx -> action.run()));
  }

  private AcceptanceRequest buildRequest(String token) {
    return new AcceptanceRequest(
        DOC_ID, CONTACT_ID, CUSTOMER_ID, token, Instant.now().plus(7, ChronoUnit.DAYS), MEMBER_ID);
  }

  private AcceptanceRequest buildRequest(
      String token, UUID docId, UUID contactId, UUID customerId) {
    return new AcceptanceRequest(
        docId, contactId, customerId, token, Instant.now().plus(7, ChronoUnit.DAYS), MEMBER_ID);
  }

  @Test
  void save_and_findById() {
    runInTenant(
        () -> {
          var request = buildRequest("save-test-token");
          var saved = repository.save(request);
          assertThat(saved.getId()).isNotNull();

          var found = repository.findById(saved.getId());
          assertThat(found).isPresent();
          assertThat(found.get().getGeneratedDocumentId()).isEqualTo(DOC_ID);
          assertThat(found.get().getPortalContactId()).isEqualTo(CONTACT_ID);
          assertThat(found.get().getCustomerId()).isEqualTo(CUSTOMER_ID);
          assertThat(found.get().getRequestToken()).isEqualTo("save-test-token");
          assertThat(found.get().getStatus()).isEqualTo(AcceptanceStatus.PENDING);
          assertThat(found.get().getReminderCount()).isZero();
          assertThat(found.get().getCreatedAt()).isNotNull();
          assertThat(found.get().getUpdatedAt()).isNotNull();
        });
  }

  @Test
  void findByRequestToken_returns_request() {
    runInTenant(
        () -> {
          var docId = UUID.randomUUID();
          var contactId = UUID.randomUUID();
          repository.save(buildRequest("unique-token-lookup", docId, contactId, CUSTOMER_ID));

          var found = repository.findByRequestToken("unique-token-lookup");
          assertThat(found).isPresent();
          assertThat(found.get().getRequestToken()).isEqualTo("unique-token-lookup");

          var notFound = repository.findByRequestToken("nonexistent-token");
          assertThat(notFound).isEmpty();
        });
  }

  @Test
  void findByGeneratedDocumentIdOrderByCreatedAtDesc_returns_list() {
    runInTenant(
        () -> {
          var docId = UUID.randomUUID();
          // Create two requests for the same document (different contacts)
          var r1 = buildRequest("doc-list-token-1", docId, UUID.randomUUID(), CUSTOMER_ID);
          var r2 = buildRequest("doc-list-token-2", docId, UUID.randomUUID(), CUSTOMER_ID);
          repository.save(r1);
          repository.save(r2);

          var results = repository.findByGeneratedDocumentIdOrderByCreatedAtDesc(docId);
          assertThat(results).hasSize(2);
          // Verify descending order by createdAt
          assertThat(results.get(0).getCreatedAt()).isAfterOrEqualTo(results.get(1).getCreatedAt());
        });
  }

  @Test
  void findByCustomerIdAndStatusIn_filters_correctly() {
    runInTenant(
        () -> {
          var customerId = UUID.randomUUID();
          var pending =
              buildRequest("cust-filter-1", UUID.randomUUID(), UUID.randomUUID(), customerId);
          var sent =
              buildRequest("cust-filter-2", UUID.randomUUID(), UUID.randomUUID(), customerId);
          sent.markSent();
          var accepted =
              buildRequest("cust-filter-3", UUID.randomUUID(), UUID.randomUUID(), customerId);
          accepted.markSent();
          accepted.markAccepted("Name", "127.0.0.1", "UA");

          repository.save(pending);
          repository.save(sent);
          repository.save(accepted);

          // Only PENDING and SENT
          var active =
              repository.findByCustomerIdAndStatusInOrderByCreatedAtDesc(
                  customerId, List.of(AcceptanceStatus.PENDING, AcceptanceStatus.SENT));
          assertThat(active).hasSize(2);
          assertThat(active)
              .allMatch(
                  r ->
                      r.getStatus() == AcceptanceStatus.PENDING
                          || r.getStatus() == AcceptanceStatus.SENT);

          // Only ACCEPTED
          var terminal =
              repository.findByCustomerIdAndStatusInOrderByCreatedAtDesc(
                  customerId, List.of(AcceptanceStatus.ACCEPTED));
          assertThat(terminal).hasSize(1);
          assertThat(terminal.get(0).getStatus()).isEqualTo(AcceptanceStatus.ACCEPTED);
        });
  }

  @Test
  void findByStatusInAndExpiresAtBefore_returns_expired() {
    runInTenant(
        () -> {
          var pastExpiry = Instant.now().minus(1, ChronoUnit.DAYS);
          var futureExpiry = Instant.now().plus(7, ChronoUnit.DAYS);

          var expired =
              new AcceptanceRequest(
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  CUSTOMER_ID,
                  "expiry-test-1",
                  pastExpiry,
                  MEMBER_ID);
          var notExpired =
              new AcceptanceRequest(
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  CUSTOMER_ID,
                  "expiry-test-2",
                  futureExpiry,
                  MEMBER_ID);

          repository.save(expired);
          repository.save(notExpired);

          var results =
              repository.findByStatusInAndExpiresAtBefore(
                  List.of(AcceptanceStatus.PENDING, AcceptanceStatus.SENT, AcceptanceStatus.VIEWED),
                  Instant.now());
          assertThat(results).anyMatch(r -> r.getRequestToken().equals("expiry-test-1"));
          assertThat(results).noneMatch(r -> r.getRequestToken().equals("expiry-test-2"));
        });
  }

  @Test
  void findByGeneratedDocumentIdAndPortalContactIdAndStatusIn_finds_active() {
    runInTenant(
        () -> {
          var docId = UUID.randomUUID();
          var contactId = UUID.randomUUID();
          var request = buildRequest("active-unique-lookup", docId, contactId, CUSTOMER_ID);
          repository.save(request);

          var found =
              repository.findByGeneratedDocumentIdAndPortalContactIdAndStatusIn(
                  docId,
                  contactId,
                  List.of(
                      AcceptanceStatus.PENDING, AcceptanceStatus.SENT, AcceptanceStatus.VIEWED));
          assertThat(found).isPresent();
          assertThat(found.get().getRequestToken()).isEqualTo("active-unique-lookup");

          // Different contact should not find it
          var notFound =
              repository.findByGeneratedDocumentIdAndPortalContactIdAndStatusIn(
                  docId,
                  UUID.randomUUID(),
                  List.of(
                      AcceptanceStatus.PENDING, AcceptanceStatus.SENT, AcceptanceStatus.VIEWED));
          assertThat(notFound).isEmpty();
        });
  }

  @Test
  void unique_token_constraint_enforced() {
    // First transaction: save with a unique token
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx ->
                        repository.save(
                            buildRequest(
                                "duplicate-token-test",
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                CUSTOMER_ID))));

    // Second transaction: attempt duplicate token -- should fail
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                    .where(RequestScopes.ORG_ID, ORG_ID)
                    .run(
                        () ->
                            transactionTemplate.executeWithoutResult(
                                tx -> {
                                  repository.save(
                                      buildRequest(
                                          "duplicate-token-test",
                                          UUID.randomUUID(),
                                          UUID.randomUUID(),
                                          CUSTOMER_ID));
                                  repository.flush();
                                })))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void active_unique_constraint_allows_terminal_duplicates() {
    var docId = UUID.randomUUID();
    var contactId = UUID.randomUUID();

    // First transaction: save an active request, then revoke it
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var request = buildRequest("terminal-dup-1", docId, contactId, CUSTOMER_ID);
                      request.markRevoked(MEMBER_ID);
                      repository.save(request);
                    }));

    // Second transaction: create a new PENDING request for the same doc-contact pair
    // This should succeed because the previous request is in terminal status (REVOKED)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var newRequest =
                          buildRequest("terminal-dup-2", docId, contactId, CUSTOMER_ID);
                      var saved = repository.save(newRequest);
                      repository.flush();
                      assertThat(saved.getId()).isNotNull();
                      assertThat(saved.getStatus()).isEqualTo(AcceptanceStatus.PENDING);
                    }));
  }
}
