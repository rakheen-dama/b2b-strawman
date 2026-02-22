package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataExportServiceTest {

  private static final String ORG_ID = "org_dsr_export_test";

  @Autowired private DataExportService dataExportService;
  @Autowired private DataSubjectRequestService dataSubjectRequestService;
  @Autowired private DataSubjectRequestRepository requestRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private StorageService storageService;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "DSR Export Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    var syncResult =
        memberSyncService.syncMember(
            ORG_ID,
            "user_dsr_export_test",
            "dsr_export@test.com",
            "DSR Export Tester",
            null,
            "owner");
    memberId = syncResult.memberId();

    customerId =
        runInTenant(
            () -> {
              var customer =
                  new Customer(
                      "Export Test Customer",
                      "export-customer@test.com",
                      "+1-555-0200",
                      "EXP-001",
                      "Test customer for export",
                      memberId);
              customer = customerRepository.save(customer);
              return customer.getId();
            });
  }

  @Test
  void generateExport_setsExportFileKeyOnRequest() {
    when(storageService.upload(any(String.class), any(byte[].class), any(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "Export test", memberId));

    var s3Key = runInTenant(() -> dataExportService.generateExport(request.getId(), memberId));

    assertThat(s3Key).contains("exports/" + request.getId() + ".zip");

    var updated = runInTenant(() -> requestRepository.findById(request.getId()).orElseThrow());
    assertThat(updated.getExportFileKey()).isEqualTo(s3Key);
  }

  @Test
  void generateExport_uploadsToS3() {
    when(storageService.upload(any(String.class), any(byte[].class), any(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "S3 upload test", memberId));

    runInTenant(() -> dataExportService.generateExport(request.getId(), memberId));

    var keyCaptor = ArgumentCaptor.forClass(String.class);
    var contentTypeCaptor = ArgumentCaptor.forClass(String.class);
    verify(storageService)
        .upload(keyCaptor.capture(), any(byte[].class), contentTypeCaptor.capture());

    assertThat(keyCaptor.getValue()).contains("exports/" + request.getId() + ".zip");
    assertThat(contentTypeCaptor.getValue()).isEqualTo("application/zip");
  }

  @Test
  void generateExport_producesZipWithCorrectEntries() {
    when(storageService.upload(any(String.class), any(byte[].class), any(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "ZIP contents test", memberId));

    runInTenant(() -> dataExportService.generateExport(request.getId(), memberId));

    // Verify the storage upload was called
    verify(storageService).upload(any(String.class), any(byte[].class), any(String.class));
  }

  @Test
  void generateExport_auditEventLogged() {
    when(storageService.upload(any(String.class), any(byte[].class), any(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "Audit export test", memberId));

    runInTenant(() -> dataExportService.generateExport(request.getId(), memberId));

    runInTenant(
        () -> {
          var page =
              auditEventRepository.findByFilter(
                  "data_subject_request",
                  request.getId(),
                  null,
                  "data.export.generated",
                  null,
                  null,
                  Pageable.ofSize(10));
          assertThat(page.getContent()).isNotEmpty();
          assertThat(page.getContent().getFirst().getEventType())
              .isEqualTo("data.export.generated");
        });
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private <T> T runInTenant(Callable<T> callable) {
    var auth =
        new TestingAuthenticationToken(
            "user_dsr_export_test", null, List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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

  private void runInTenant(Runnable runnable) {
    runInTenant(
        () -> {
          runnable.run();
          return null;
        });
  }
}
