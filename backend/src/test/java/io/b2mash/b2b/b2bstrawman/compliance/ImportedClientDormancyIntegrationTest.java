package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 93 655A (task 655.1, R-9): the dormancy job and clients that have no activity rows.
 *
 * <p>Case 1 is the characterisation of unchanged {@code main}: a client persisted {@code ACTIVE}
 * through the 8-argument constructor with no time entries, tasks, documents, fee notes or comments
 * goes {@code DORMANT} at the first {@link CustomerLifecycleService#executeDormancyTransitions()}
 * (the job's own entry, as {@code DormancyScheduledJob} calls it) — the {@code GREATEST(...) IS
 * NULL} branch of {@code DORMANCY_SQL}. That is the hazard an imported client would hit at the
 * first 02:00 run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportedClientDormancyIntegrationTest {

  private static final String ORG_ID = "org_imported_client_dormancy_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerLifecycleService lifecycleService;

  private String schema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Imported Client Dormancy", "legal-za")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_icd_owner",
                "icd_owner@test.com",
                "Dormancy Owner",
                "owner"));
  }

  @Test
  void activeClientWithNoActivityRows_goesDormantAtTheFirstRun() {
    UUID clientId =
        inTenant(
            () ->
                customerRepository
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "No Activity Client", "no.activity@example.test", memberId))
                    .getId());

    inTenant(lifecycleService::executeDormancyTransitions);

    LifecycleStatus status =
        inTenant(() -> customerRepository.findById(clientId).orElseThrow().getLifecycleStatus());
    assertThat(status).isEqualTo(LifecycleStatus.DORMANT);
  }

  private <T> T inTenant(Callable<T> action) {
    return RequestScopes.callForTenant(schema, ORG_ID, action);
  }
}
