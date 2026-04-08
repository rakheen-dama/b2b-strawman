package io.b2mash.b2b.b2bstrawman.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerControllerIntegrationTest {
  private static final String ORG_ID = "org_cust_ctrl_new_fields";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Customer Controller New Fields Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_ccnf_owner", "ccnf_owner@test.com", "CCNF Owner", "owner");
  }

  // --- 459.10: Create with new fields ---

  @Test
  void createCustomerWithAllNewFieldsReturns201() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ccnf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Full Fields Ltd",
                      "email": "fullfields@ccnf.com",
                      "phone": "+27-11-555-0100",
                      "idNumber": "FF-100",
                      "notes": "Test with all fields",
                      "registrationNumber": "REG-CTRL-001",
                      "addressLine1": "789 Controller Street",
                      "addressLine2": "Unit 5",
                      "city": "Durban",
                      "stateProvince": "KwaZulu-Natal",
                      "postalCode": "4001",
                      "country": "ZA",
                      "taxNumber": "VAT-CTRL-001",
                      "contactName": "Alice Controller",
                      "contactEmail": "alice@ccnf.com",
                      "contactPhone": "+27-31-555-0200",
                      "entityType": "PTY_LTD",
                      "financialYearEnd": "2026-02-28"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Full Fields Ltd"))
        .andExpect(jsonPath("$.email").value("fullfields@ccnf.com"))
        .andExpect(jsonPath("$.registrationNumber").value("REG-CTRL-001"))
        .andExpect(jsonPath("$.addressLine1").value("789 Controller Street"))
        .andExpect(jsonPath("$.addressLine2").value("Unit 5"))
        .andExpect(jsonPath("$.city").value("Durban"))
        .andExpect(jsonPath("$.stateProvince").value("KwaZulu-Natal"))
        .andExpect(jsonPath("$.postalCode").value("4001"))
        .andExpect(jsonPath("$.country").value("ZA"))
        .andExpect(jsonPath("$.taxNumber").value("VAT-CTRL-001"))
        .andExpect(jsonPath("$.contactName").value("Alice Controller"))
        .andExpect(jsonPath("$.contactEmail").value("alice@ccnf.com"))
        .andExpect(jsonPath("$.contactPhone").value("+27-31-555-0200"))
        .andExpect(jsonPath("$.entityType").value("PTY_LTD"))
        .andExpect(jsonPath("$.financialYearEnd").value("2026-02-28"));
  }

  @Test
  void createCustomerWithOnlyCoreFieldsReturns201WithNullNewFields() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ccnf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Core Only Ltd",
                      "email": "coreonly@ccnf.com",
                      "phone": "+27-11-555-0300"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Core Only Ltd"))
        .andExpect(jsonPath("$.registrationNumber").doesNotExist())
        .andExpect(jsonPath("$.addressLine1").doesNotExist())
        .andExpect(jsonPath("$.addressLine2").doesNotExist())
        .andExpect(jsonPath("$.city").doesNotExist())
        .andExpect(jsonPath("$.stateProvince").doesNotExist())
        .andExpect(jsonPath("$.postalCode").doesNotExist())
        .andExpect(jsonPath("$.country").doesNotExist())
        .andExpect(jsonPath("$.taxNumber").doesNotExist())
        .andExpect(jsonPath("$.contactName").doesNotExist())
        .andExpect(jsonPath("$.contactEmail").doesNotExist())
        .andExpect(jsonPath("$.contactPhone").doesNotExist())
        .andExpect(jsonPath("$.entityType").doesNotExist())
        .andExpect(jsonPath("$.financialYearEnd").doesNotExist());
  }

  // --- 459.11: Update with new fields ---

  @Test
  void updateCustomerWithAddressFieldsReturns200() throws Exception {
    // Create a basic customer first
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ccnf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Update Target", "email": "updatetarget@ccnf.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(
            put("/api/customers/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ccnf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Update Target",
                      "email": "updatetarget@ccnf.com",
                      "registrationNumber": "REG-UPD-001",
                      "addressLine1": "100 Update Road",
                      "city": "Pretoria",
                      "stateProvince": "Gauteng",
                      "postalCode": "0001",
                      "country": "ZA",
                      "taxNumber": "VAT-UPD-001",
                      "contactName": "Bob Updater",
                      "contactEmail": "bob@update.com",
                      "contactPhone": "+27-12-555-0001",
                      "entityType": "SOLE_PROPRIETOR",
                      "financialYearEnd": "2027-06-30"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrationNumber").value("REG-UPD-001"))
        .andExpect(jsonPath("$.addressLine1").value("100 Update Road"))
        .andExpect(jsonPath("$.city").value("Pretoria"))
        .andExpect(jsonPath("$.stateProvince").value("Gauteng"))
        .andExpect(jsonPath("$.postalCode").value("0001"))
        .andExpect(jsonPath("$.country").value("ZA"))
        .andExpect(jsonPath("$.taxNumber").value("VAT-UPD-001"))
        .andExpect(jsonPath("$.contactName").value("Bob Updater"))
        .andExpect(jsonPath("$.contactEmail").value("bob@update.com"))
        .andExpect(jsonPath("$.contactPhone").value("+27-12-555-0001"))
        .andExpect(jsonPath("$.entityType").value("SOLE_PROPRIETOR"))
        .andExpect(jsonPath("$.financialYearEnd").value("2027-06-30"));
  }

  @Test
  void updateCustomerClearingFieldReturns200WithNull() throws Exception {
    // Create a customer with address fields set
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ccnf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Clear Target",
                          "email": "cleartarget@ccnf.com",
                          "city": "Johannesburg",
                          "contactName": "To Be Cleared"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.city").value("Johannesburg"))
            .andExpect(jsonPath("$.contactName").value("To Be Cleared"))
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    // Update, setting those fields to null (by omitting them)
    mockMvc
        .perform(
            put("/api/customers/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ccnf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Clear Target",
                      "email": "cleartarget@ccnf.com",
                      "city": null,
                      "contactName": null
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.city").doesNotExist())
        .andExpect(jsonPath("$.contactName").doesNotExist());
  }
}
