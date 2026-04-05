package io.b2mash.b2b.b2bstrawman.testutil;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for integration tests that need the full Spring context with Testcontainers.
 * Eliminates the 5-annotation boilerplate and provides common autowired fields.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

  @Autowired protected MockMvc mockMvc;
  @Autowired protected TenantProvisioningService provisioningService;

  /**
   * Provisions a tenant schema and syncs an owner member. Returns the owner's memberId.
   *
   * @param orgId Clerk org ID (e.g., "org_test_xyz")
   * @param orgName Display name for the org
   * @param ownerClerkId Clerk user ID for the owner (e.g., "user_owner")
   * @param ownerEmail Email for the owner member
   * @param ownerName Display name for the owner member
   * @return the synced owner's memberId (UUID string)
   */
  protected String provisionTenantWithOwner(
      String orgId, String orgName, String ownerClerkId, String ownerEmail, String ownerName)
      throws Exception {
    provisioningService.provisionTenant(orgId, orgName, null);
    return TestMemberHelper.syncMember(
        mockMvc, orgId, ownerClerkId, ownerEmail, ownerName, "owner");
  }
}
