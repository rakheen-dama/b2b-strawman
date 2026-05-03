package io.b2mash.b2b.b2bstrawman.datarequest;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Epic 505A — DSAR audit-trail folder integration tests. Verifies that {@link
 * DataExportService#exportCustomerData} produces an {@code audit-trail/} folder containing {@code
 * events.json}, {@code events.csv}, and {@code README.txt}; that the row counts agree; that
 * cross-customer events are excluded; and that the existing Phase 50 pack contents remain unchanged
 * (additive-only).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataExportServiceAuditTrailIntegrationTest {

  private static final String ORG_ID = "org_dsr_audit_trail_test";

  @Autowired private DataExportService dataExportService;
  @Autowired private AuditService auditService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private StorageService storageService;

  private String tenantSchema;
  private UUID memberId;
  private UUID subjectCustomerId;
  private UUID otherCustomerId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "DSR Audit Trail Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    var syncResult =
        memberSyncService.syncMember(
            ORG_ID,
            "user_dsr_audit_trail_test",
            "dsr_audit_trail@test.com",
            "DSR Audit Trail Tester",
            null,
            "owner");
    memberId = syncResult.memberId();

    subjectCustomerId =
        runInTenant(
            () -> {
              var c = createActiveCustomer("Subject Customer", "subject@test.com", memberId);
              return customerRepository.save(c).getId();
            });
    otherCustomerId =
        runInTenant(
            () -> {
              var c = createActiveCustomer("Other Customer", "other@test.com", memberId);
              return customerRepository.save(c).getId();
            });
  }

  @Test
  void exportCustomerData_includesAuditTrailFolder_withParsableContents() throws Exception {
    // Seed two audit events directly on the subject's customer entity. The DSAR audit-trail
    // streaming query picks these up via branch (a) entity_type='customer' AND entity_id=...
    runInTenant(
        () -> {
          auditService.log(
              AuditEventBuilder.builder()
                  .eventType("customer.updated")
                  .entityType("customer")
                  .entityId(subjectCustomerId)
                  .actorId(memberId)
                  .actorType("USER")
                  .source("API")
                  .details(Map.of("field", "name"))
                  .build());
          auditService.log(
              AuditEventBuilder.builder()
                  .eventType("customer.activated")
                  .entityType("customer")
                  .entityId(subjectCustomerId)
                  .actorId(memberId)
                  .actorType("USER")
                  .source("API")
                  .details(Map.of("note", "internal review complete"))
                  .build());
          return null;
        });

    var entries = exportAndReadZip(subjectCustomerId);
    String prefix = "customer-export-" + subjectCustomerId + "/";

    assertThat(entries).containsKey(prefix + "audit-trail/events.json");
    assertThat(entries).containsKey(prefix + "audit-trail/events.csv");
    assertThat(entries).containsKey(prefix + "audit-trail/README.txt");

    // events.json parses as a JSON array
    byte[] jsonBytes = entries.get(prefix + "audit-trail/events.json");
    List<Map<String, Object>> events =
        objectMapper.readValue(jsonBytes, new TypeReference<List<Map<String, Object>>>() {});
    assertThat(events).isNotEmpty();
    // The two seeded events must appear (other customer-export.generated audit events from the
    // pack-build flow may also be present — assertion is on inclusion, not exact count).
    assertThat(events.stream().map(e -> e.get("eventType")).toList())
        .contains("customer.updated", "customer.activated");

    // events.csv row count (excluding header) matches events.json length
    String csv = new String(entries.get(prefix + "audit-trail/events.csv"));
    long csvDataRows = csv.lines().count() - 1 /* header */;
    // CSV may have CRLF line endings; lines() splits on either, so this works for both.
    assertThat(csvDataRows).isEqualTo(events.size());

    // README mentions POPIA
    String readme = new String(entries.get(prefix + "audit-trail/README.txt"));
    assertThat(readme).contains("POPIA");
  }

  @Test
  void exportCustomerData_excludesEventsForUnrelatedCustomer() throws Exception {
    // Seed an event on the OTHER customer that must NOT appear in the subject's audit trail.
    String unrelatedMarker = "unrelated-event-marker-" + UUID.randomUUID();
    runInTenant(
        () -> {
          auditService.log(
              AuditEventBuilder.builder()
                  .eventType("customer.updated")
                  .entityType("customer")
                  .entityId(otherCustomerId)
                  .actorId(memberId)
                  .actorType("USER")
                  .source("API")
                  .details(Map.of("marker", unrelatedMarker))
                  .build());
          // Also seed one event on the subject so the trail is not empty
          auditService.log(
              AuditEventBuilder.builder()
                  .eventType("customer.updated")
                  .entityType("customer")
                  .entityId(subjectCustomerId)
                  .actorId(memberId)
                  .actorType("USER")
                  .source("API")
                  .details(Map.of("field", "phone"))
                  .build());
          return null;
        });

    var entries = exportAndReadZip(subjectCustomerId);
    String prefix = "customer-export-" + subjectCustomerId + "/";
    String json = new String(entries.get(prefix + "audit-trail/events.json"));
    String csv = new String(entries.get(prefix + "audit-trail/events.csv"));

    // The unrelated-customer event's marker must not appear anywhere in the subject's pack
    assertThat(json).doesNotContain(unrelatedMarker);
    assertThat(csv).doesNotContain(unrelatedMarker);
    // The other customer's UUID itself should also not show up as an entity_id row
    assertThat(json).doesNotContain(otherCustomerId.toString());
  }

  @Test
  void exportCustomerData_phase50PackContents_remainPresent() throws Exception {
    var entries = exportAndReadZip(subjectCustomerId);
    String prefix = "customer-export-" + subjectCustomerId + "/";

    // Phase 50 backwards-compatibility: every existing structured-pack file must still be present.
    assertThat(entries).containsKey(prefix + "customer.json");
    assertThat(entries).containsKey(prefix + "portal-contacts.json");
    assertThat(entries).containsKey(prefix + "time-entries.json");
    assertThat(entries).containsKey(prefix + "invoices.json");
    assertThat(entries).containsKey(prefix + "comments.json");
    assertThat(entries).containsKey(prefix + "custom-fields.json");
    assertThat(entries).containsKey(prefix + "audit-events.json");
    assertThat(entries).containsKey(prefix + "export-metadata.json");

    // And the Phase 505A audit-trail siblings must coexist.
    assertThat(entries).containsKey(prefix + "audit-trail/events.json");
    assertThat(entries).containsKey(prefix + "audit-trail/events.csv");
    assertThat(entries).containsKey(prefix + "audit-trail/README.txt");
  }

  // --- helpers ---

  private Map<String, byte[]> exportAndReadZip(UUID customerId) throws Exception {
    var zipCaptor = ArgumentCaptor.forClass(byte[].class);
    when(storageService.upload(any(), zipCaptor.capture(), any()))
        .thenAnswer(inv -> inv.getArgument(0));
    when(storageService.generateDownloadUrl(any(), any()))
        .thenReturn(
            new PresignedUrl("https://example.com/download", Instant.now().plusSeconds(3600)));

    var result = runInTenant(() -> dataExportService.exportCustomerData(customerId, memberId));
    assertThat(result).isNotNull();
    return readZipEntries(zipCaptor.getValue());
  }

  private Map<String, byte[]> readZipEntries(byte[] zipBytes) throws Exception {
    var result = new LinkedHashMap<String, byte[]>();
    try (var bais = new ByteArrayInputStream(zipBytes);
        var zis = new ZipInputStream(bais)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          result.put(entry.getName(), zis.readAllBytes());
        }
        zis.closeEntry();
      }
    }
    return result;
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private <T> T runInTenant(Callable<T> callable) {
    var auth =
        new TestingAuthenticationToken("user_dsr_audit_trail_test", null, Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      try {
                        return callable.call();
                      } catch (RuntimeException e) {
                        throw e;
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }));
  }
}
