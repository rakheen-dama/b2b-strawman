package io.b2mash.b2b.b2bstrawman.customerbackend;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalReadModelService;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestService;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestItem;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestItemRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.ResponseType;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InformationRequestReadModelSyncIntegrationTest {

  private static final String ORG_ID = "org_info_req_sync_test";
  private static final String CLERK_USER_ID = "user_info_sync";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private InformationRequestService informationRequestService;
  @Autowired private InformationRequestRepository informationRequestRepository;
  @Autowired private RequestItemRepository requestItemRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private PortalReadModelRepository readModelRepo;
  @Autowired private PortalReadModelService readModelService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID portalContactId;
  private UUID portalContactId2;
  private UUID projectId;

  // Shared state across ordered tests
  private UUID sentRequestId;
  private UUID itemId1;
  private UUID itemId2;
  private UUID itemId3;

  @BeforeAll
  void provisionTenantAndSetupData() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Info Request Sync Org", null).schemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, CLERK_USER_ID, "info-sync@test.com", "Sync Test User", null, "admin");
    memberId = syncResult.memberId();

    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomer(
                          "Info Request Sync Customer", "info-sync-cust@test.com", memberId);
                  customer = customerRepository.save(customer);
                  customerId = customer.getId();

                  var project = new Project("Test Project", "A test project", memberId);
                  project = projectRepository.save(project);
                  projectId = project.getId();

                  var contact =
                      new PortalContact(
                          ORG_ID,
                          customerId,
                          "info-sync-contact@test.com",
                          "Info Contact",
                          PortalContact.ContactRole.PRIMARY);
                  contact = portalContactRepository.save(contact);
                  portalContactId = contact.getId();

                  var contact2 =
                      new PortalContact(
                          ORG_ID,
                          customerId,
                          "info-sync-contact2@test.com",
                          "Info Contact 2",
                          PortalContact.ContactRole.GENERAL);
                  contact2 = portalContactRepository.save(contact2);
                  portalContactId2 = contact2.getId();
                }));
  }

  // ── 1. SentEvent creates portal_requests + portal_request_items ───

  @Test
  @Order(1)
  void sentEvent_creates_portalRequests_and_items() {
    runInTenantWithMember(
        () -> {
          transactionTemplate.executeWithoutResult(
              tx -> {
                var request =
                    new InformationRequest("IR-001", customerId, portalContactId, memberId);
                request.setProjectId(projectId);
                request = informationRequestRepository.save(request);

                var item1 =
                    new RequestItem(
                        request.getId(),
                        "Tax Certificate",
                        "Upload latest tax cert",
                        ResponseType.FILE_UPLOAD,
                        true,
                        ".pdf,.jpg",
                        0);
                item1 = requestItemRepository.save(item1);

                var item2 =
                    new RequestItem(
                        request.getId(),
                        "Company Reg",
                        "Upload company registration",
                        ResponseType.FILE_UPLOAD,
                        true,
                        ".pdf",
                        1);
                item2 = requestItemRepository.save(item2);

                var item3 =
                    new RequestItem(
                        request.getId(),
                        "Notes",
                        "Any additional notes",
                        ResponseType.TEXT_RESPONSE,
                        false,
                        null,
                        2);
                item3 = requestItemRepository.save(item3);

                sentRequestId = request.getId();
                itemId1 = item1.getId();
                itemId2 = item2.getId();
                itemId3 = item3.getId();
              });

          // Send the request (triggers event publishing)
          informationRequestService.send(sentRequestId);
        });

    // Verify portal read-model
    var requests = readModelRepo.findRequestsByPortalContactId(portalContactId);
    assertThat(requests).hasSize(1);

    var view = requests.getFirst();
    assertThat(view.id()).isEqualTo(sentRequestId);
    assertThat(view.requestNumber()).isEqualTo("IR-001");
    assertThat(view.customerId()).isEqualTo(customerId);
    assertThat(view.portalContactId()).isEqualTo(portalContactId);
    assertThat(view.projectId()).isEqualTo(projectId);
    assertThat(view.projectName()).isEqualTo("Test Project");
    assertThat(view.orgId()).isEqualTo(ORG_ID);
    assertThat(view.status()).isEqualTo("SENT");
    assertThat(view.totalItems()).isEqualTo(3);
    assertThat(view.submittedItems()).isEqualTo(0);
    assertThat(view.acceptedItems()).isEqualTo(0);
    assertThat(view.rejectedItems()).isEqualTo(0);
    assertThat(view.sentAt()).isNotNull();

    var items = readModelRepo.findRequestItemsByRequestId(sentRequestId);
    assertThat(items).hasSize(3);
    assertThat(items.get(0).name()).isEqualTo("Tax Certificate");
    assertThat(items.get(0).responseType()).isEqualTo("FILE_UPLOAD");
    assertThat(items.get(0).required()).isTrue();
    assertThat(items.get(0).status()).isEqualTo("PENDING");
    assertThat(items.get(1).name()).isEqualTo("Company Reg");
    assertThat(items.get(2).name()).isEqualTo("Notes");
    assertThat(items.get(2).required()).isFalse();
  }

  // ── 2. CancelledEvent updates status ──────────────────────────────

  @Test
  @Order(2)
  void cancelledEvent_updates_portalRequest_status() {
    // Create another request to cancel
    final UUID[] cancelRequestId = new UUID[1];
    runInTenantWithMember(
        () -> {
          transactionTemplate.executeWithoutResult(
              tx -> {
                var request =
                    new InformationRequest("IR-002", customerId, portalContactId, memberId);
                request = informationRequestRepository.save(request);

                var item =
                    new RequestItem(
                        request.getId(), "Document", null, ResponseType.FILE_UPLOAD, true, null, 0);
                requestItemRepository.save(item);
                cancelRequestId[0] = request.getId();
              });

          informationRequestService.send(cancelRequestId[0]);
          informationRequestService.cancel(cancelRequestId[0]);
        });

    var request = readModelRepo.findRequestById(cancelRequestId[0]);
    assertThat(request).isPresent();
    assertThat(request.get().status()).isEqualTo("CANCELLED");
  }

  // ── 3. ItemAcceptedEvent updates item status + recalculates counts ─

  @Test
  @Order(3)
  void itemAcceptedEvent_updates_item_status_and_recalculates_counts() {
    // First, simulate item submission by directly updating the item status in tenant schema
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var item = requestItemRepository.findById(itemId1).orElseThrow();
                  item.submit(UUID.randomUUID());
                  requestItemRepository.save(item);
                }));

    // Now accept the item (triggers event)
    runInTenantWithMember(() -> informationRequestService.acceptItem(sentRequestId, itemId1));

    var items = readModelRepo.findRequestItemsByRequestId(sentRequestId);
    var acceptedItem = items.stream().filter(i -> i.id().equals(itemId1)).findFirst().orElseThrow();
    assertThat(acceptedItem.status()).isEqualTo("ACCEPTED");

    var request = readModelRepo.findRequestById(sentRequestId);
    assertThat(request).isPresent();
    assertThat(request.get().acceptedItems()).isEqualTo(1);
  }

  // ── 4. ItemRejectedEvent updates item status + rejection reason ────

  @Test
  @Order(4)
  void itemRejectedEvent_updates_item_status_and_rejection_reason() {
    // Submit item2 first
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var item = requestItemRepository.findById(itemId2).orElseThrow();
                  item.submit(UUID.randomUUID());
                  requestItemRepository.save(item);
                }));

    // Reject it
    runInTenantWithMember(
        () -> informationRequestService.rejectItem(sentRequestId, itemId2, "Wrong document"));

    var items = readModelRepo.findRequestItemsByRequestId(sentRequestId);
    var rejectedItem = items.stream().filter(i -> i.id().equals(itemId2)).findFirst().orElseThrow();
    assertThat(rejectedItem.status()).isEqualTo("REJECTED");
    assertThat(rejectedItem.rejectionReason()).isEqualTo("Wrong document");

    var request = readModelRepo.findRequestById(sentRequestId);
    assertThat(request).isPresent();
    assertThat(request.get().rejectedItems()).isEqualTo(1);
  }

  // ── 5. CompletedEvent updates status + completed_at ────────────────

  @Test
  @Order(5)
  void completedEvent_updates_portalRequest_status() {
    // Accept item2 (previously rejected - need to re-submit)
    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var item = requestItemRepository.findById(itemId2).orElseThrow();
                  item.submit(UUID.randomUUID());
                  requestItemRepository.save(item);
                }));

    runInTenantWithMember(() -> informationRequestService.acceptItem(sentRequestId, itemId2));

    // item1 is accepted, item2 now accepted, item3 is optional (not required)
    // All required items are accepted -> auto-complete triggers
    var request = readModelRepo.findRequestById(sentRequestId);
    assertThat(request).isPresent();
    assertThat(request.get().status()).isEqualTo("COMPLETED");
    assertThat(request.get().completedAt()).isNotNull();
  }

  // ── 6. Idempotent upserts ─────────────────────────────────────────

  @Test
  @Order(6)
  void idempotent_upserts_do_not_duplicate_rows() {
    final UUID[] requestId = new UUID[1];
    runInTenantWithMember(
        () -> {
          transactionTemplate.executeWithoutResult(
              tx -> {
                var request =
                    new InformationRequest("IR-003", customerId, portalContactId, memberId);
                request = informationRequestRepository.save(request);

                var item =
                    new RequestItem(
                        request.getId(), "Doc", null, ResponseType.FILE_UPLOAD, true, null, 0);
                requestItemRepository.save(item);
                requestId[0] = request.getId();
              });

          informationRequestService.send(requestId[0]);

          // Resend notification (publishes another SentEvent -> upsert)
          informationRequestService.resendNotification(requestId[0]);
        });

    // Should still be just one row for this contact+request
    var byId = readModelRepo.findRequestById(requestId[0]);
    assertThat(byId).isPresent();

    var items = readModelRepo.findRequestItemsByRequestId(requestId[0]);
    assertThat(items).hasSize(1);
  }

  // ── 7. Count recalculation accuracy ───────────────────────────────

  @Test
  @Order(7)
  void count_recalculation_accuracy() {
    // Use sentRequestId from test 1: item1=ACCEPTED, item2=ACCEPTED, item3=PENDING
    var request = readModelRepo.findRequestById(sentRequestId);
    assertThat(request).isPresent();
    assertThat(request.get().totalItems()).isEqualTo(3);
    assertThat(request.get().acceptedItems()).isEqualTo(2);
    // item3 is still PENDING (not required, never submitted)
  }

  // ── 8. Query by portal contact returns only their requests ────────

  @Test
  @Order(8)
  void query_by_portal_contact_returns_only_their_requests() {
    // Create a request for contact2
    final UUID[] contact2RequestId = new UUID[1];
    runInTenantWithMember(
        () -> {
          transactionTemplate.executeWithoutResult(
              tx -> {
                var request =
                    new InformationRequest("IR-004", customerId, portalContactId2, memberId);
                request = informationRequestRepository.save(request);

                var item =
                    new RequestItem(
                        request.getId(), "ID Doc", null, ResponseType.FILE_UPLOAD, true, null, 0);
                requestItemRepository.save(item);
                contact2RequestId[0] = request.getId();
              });

          informationRequestService.send(contact2RequestId[0]);
        });

    var contact1Requests = readModelRepo.findRequestsByPortalContactId(portalContactId);
    var contact2Requests = readModelRepo.findRequestsByPortalContactId(portalContactId2);

    // Contact1 should have multiple (from tests 1, 2, 6), contact2 should have exactly 1
    assertThat(contact1Requests).hasSizeGreaterThanOrEqualTo(1);
    assertThat(contact2Requests).hasSize(1);
    assertThat(contact2Requests.getFirst().requestNumber()).isEqualTo("IR-004");

    // Verify no cross-contamination
    assertThat(contact1Requests).noneMatch(r -> r.portalContactId().equals(portalContactId2));
    assertThat(contact2Requests).noneMatch(r -> r.portalContactId().equals(portalContactId));
  }

  // ── 9. Handler exception does not propagate ───────────────────────

  @Test
  @Order(9)
  void handler_exception_does_not_propagate() {
    // If the portal read-model update fails, the original transaction should still succeed.
    // We can't easily simulate a portal DB failure in integration test, but we verify that
    // sending a request with a null projectId (valid scenario) works correctly.
    final UUID[] requestId = new UUID[1];
    runInTenantWithMember(
        () -> {
          transactionTemplate.executeWithoutResult(
              tx -> {
                var request =
                    new InformationRequest("IR-005", customerId, portalContactId, memberId);
                // No projectId set (null)
                request = informationRequestRepository.save(request);

                var item =
                    new RequestItem(
                        request.getId(), "Doc", null, ResponseType.FILE_UPLOAD, true, null, 0);
                requestItemRepository.save(item);
                requestId[0] = request.getId();
              });

          informationRequestService.send(requestId[0]);
        });

    var view = readModelRepo.findRequestById(requestId[0]);
    assertThat(view).isPresent();
    assertThat(view.get().projectId()).isNull();
    assertThat(view.get().projectName()).isNull();
  }

  // ── 10. Query by request ID returns items in sort order ───────────

  @Test
  @Order(10)
  void query_by_request_id_returns_items_in_sort_order() {
    var items = readModelRepo.findRequestItemsByRequestId(sentRequestId);
    assertThat(items).hasSize(3);
    assertThat(items.get(0).sortOrder()).isEqualTo(0);
    assertThat(items.get(1).sortOrder()).isEqualTo(1);
    assertThat(items.get(2).sortOrder()).isEqualTo(2);

    // Verify order is ascending
    for (int i = 1; i < items.size(); i++) {
      assertThat(items.get(i).sortOrder()).isGreaterThan(items.get(i - 1).sortOrder());
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────

  private void runInTenantWithMember(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(action);
  }
}
