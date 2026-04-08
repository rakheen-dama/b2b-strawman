package io.b2mash.b2b.b2bstrawman.customer;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerServiceIntegrationTest {
  private static final String ORG_ID = "org_cust_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerRepository customerRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Customer Service Test Org", null);
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_csvc_owner",
                "csvc_owner@test.com",
                "CustSvc Owner",
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void createCustomerWithAllNewFieldsPersistsCorrectly() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Full Fields Corp",
                      "fullfields@test.com",
                      "+27-11-555-0001",
                      "FF-001",
                      "Test notes",
                      memberId,
                      null,
                      null,
                      CustomerType.INDIVIDUAL,
                      "REG-12345",
                      "123 Main Street",
                      "Suite 400",
                      "Johannesburg",
                      "Gauteng",
                      "2000",
                      "ZA",
                      "VAT-9876",
                      "Jane Smith",
                      "jane@fullfields.com",
                      "+27-11-555-0002",
                      "PTY_LTD",
                      LocalDate.of(2026, 2, 28));

              assertThat(customer.getId()).isNotNull();
              assertThat(customer.getRegistrationNumber()).isEqualTo("REG-12345");
              assertThat(customer.getAddressLine1()).isEqualTo("123 Main Street");
              assertThat(customer.getAddressLine2()).isEqualTo("Suite 400");
              assertThat(customer.getCity()).isEqualTo("Johannesburg");
              assertThat(customer.getStateProvince()).isEqualTo("Gauteng");
              assertThat(customer.getPostalCode()).isEqualTo("2000");
              assertThat(customer.getCountry()).isEqualTo("ZA");
              assertThat(customer.getTaxNumber()).isEqualTo("VAT-9876");
              assertThat(customer.getContactName()).isEqualTo("Jane Smith");
              assertThat(customer.getContactEmail()).isEqualTo("jane@fullfields.com");
              assertThat(customer.getContactPhone()).isEqualTo("+27-11-555-0002");
              assertThat(customer.getEntityType()).isEqualTo("PTY_LTD");
              assertThat(customer.getFinancialYearEnd()).isEqualTo(LocalDate.of(2026, 2, 28));

              // Verify persistence by re-fetching
              var fetched = customerService.getCustomer(customer.getId());
              assertThat(fetched.getRegistrationNumber()).isEqualTo("REG-12345");
              assertThat(fetched.getCity()).isEqualTo("Johannesburg");
              assertThat(fetched.getFinancialYearEnd()).isEqualTo(LocalDate.of(2026, 2, 28));
            });
  }

  @Test
  void createCustomerWithNoNewFieldsIsBackwardCompatible() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Basic Corp",
                      "basic@test.com",
                      "+27-11-555-0010",
                      "BC-001",
                      "Basic notes",
                      memberId);

              assertThat(customer.getId()).isNotNull();
              assertThat(customer.getName()).isEqualTo("Basic Corp");
              assertThat(customer.getRegistrationNumber()).isNull();
              assertThat(customer.getAddressLine1()).isNull();
              assertThat(customer.getAddressLine2()).isNull();
              assertThat(customer.getCity()).isNull();
              assertThat(customer.getStateProvince()).isNull();
              assertThat(customer.getPostalCode()).isNull();
              assertThat(customer.getCountry()).isNull();
              assertThat(customer.getTaxNumber()).isNull();
              assertThat(customer.getContactName()).isNull();
              assertThat(customer.getContactEmail()).isNull();
              assertThat(customer.getContactPhone()).isNull();
              assertThat(customer.getEntityType()).isNull();
              assertThat(customer.getFinancialYearEnd()).isNull();
            });
  }

  @Test
  void updateCustomerAddsAddressFields() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // Create a customer with no structural fields
              var customer =
                  customerService.createCustomer(
                      "Update Test Corp", "updatetest@test.com", null, null, null, memberId);

              assertThat(customer.getAddressLine1()).isNull();

              // Update with address fields
              var updated =
                  customerService.updateCustomer(
                      customer.getId(),
                      "Update Test Corp",
                      "updatetest@test.com",
                      null,
                      null,
                      null,
                      null,
                      null,
                      "REG-UPD",
                      "456 Oak Avenue",
                      "Floor 2",
                      "Cape Town",
                      "Western Cape",
                      "8001",
                      "ZA",
                      "TAX-UPD",
                      "Bob Jones",
                      "bob@update.com",
                      "+27-21-555-0001",
                      "CC",
                      LocalDate.of(2026, 6, 30));

              assertThat(updated.getRegistrationNumber()).isEqualTo("REG-UPD");
              assertThat(updated.getAddressLine1()).isEqualTo("456 Oak Avenue");
              assertThat(updated.getAddressLine2()).isEqualTo("Floor 2");
              assertThat(updated.getCity()).isEqualTo("Cape Town");
              assertThat(updated.getStateProvince()).isEqualTo("Western Cape");
              assertThat(updated.getPostalCode()).isEqualTo("8001");
              assertThat(updated.getCountry()).isEqualTo("ZA");
              assertThat(updated.getTaxNumber()).isEqualTo("TAX-UPD");
              assertThat(updated.getContactName()).isEqualTo("Bob Jones");
              assertThat(updated.getContactEmail()).isEqualTo("bob@update.com");
              assertThat(updated.getContactPhone()).isEqualTo("+27-21-555-0001");
              assertThat(updated.getEntityType()).isEqualTo("CC");
              assertThat(updated.getFinancialYearEnd()).isEqualTo(LocalDate.of(2026, 6, 30));

              // Verify persistence
              var fetched = customerService.getCustomer(customer.getId());
              assertThat(fetched.getAddressLine1()).isEqualTo("456 Oak Avenue");
              assertThat(fetched.getFinancialYearEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
            });
  }

  @Test
  void financialYearEndPersistsAsCorrectLocalDate() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var fyeDate = LocalDate.of(2027, 3, 31);
              var customer =
                  customerService.createCustomer(
                      "FYE Test Corp",
                      "fyetest@test.com",
                      null,
                      null,
                      null,
                      memberId,
                      null,
                      null,
                      CustomerType.INDIVIDUAL,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      fyeDate);

              assertThat(customer.getFinancialYearEnd()).isEqualTo(fyeDate);

              // Verify persistence (re-fetch from DB)
              var fetched = customerService.getCustomer(customer.getId());
              assertThat(fetched.getFinancialYearEnd()).isEqualTo(fyeDate);
              assertThat(fetched.getFinancialYearEnd().getYear()).isEqualTo(2027);
              assertThat(fetched.getFinancialYearEnd().getMonthValue()).isEqualTo(3);
              assertThat(fetched.getFinancialYearEnd().getDayOfMonth()).isEqualTo(31);
            });
  }
}
