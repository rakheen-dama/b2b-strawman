package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import java.util.function.Supplier;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F12: contract parties are explicit matter fields and are never inferred from the firm ({@code
 * org.*}) or the firm's client ({@code customer.*}). Renders the shipped {@code legal-za} Offer to
 * Purchase, Deed of Transfer, Bond Cancellation Instruction and Letter of Demand through the
 * preview endpoint for a matter whose party fields are set, and asserts every party slot names the
 * field value — the Voetstoots clause names the SELLER as not liable and the PURCHASER as accepting
 * the property; the bond cancellation names the seller (the mortgagor), not the client; the Letter
 * of Demand is addressed to the opposing party, not to the client (the creditor).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractPartyBindingIntegrationTest {

  private static final String ORG_ID = "org_contract_party_binding_test";
  private static final String ORG_NAME = "Kruger Partners Inc";
  private static final String OWNER = "user_party_binding_owner";

  private static final String CLIENT = "Khumalo Property Holdings";
  private static final String SELLER = "Sipho Mokoena";
  private static final String SELLER_ID = "8001015009087";
  private static final String PURCHASER = "Thandi Nkosi";
  private static final String PURCHASER_ID = "9002020123083";
  private static final String OPPOSING_PARTY = "Pillay Hardware CC";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;
  private String memberId;
  private UUID clientId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, ORG_NAME, "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, OWNER, "party_owner@test.com", "Party Owner", "owner");
    clientId =
        inTenantTx(
            () ->
                customerRepository
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            CLIENT, "khumalo@test.com", UUID.fromString(memberId)))
                    .getId());
  }

  @Test
  void offerToPurchase_bindsSellerAndPurchaserFields_neverTheFirmOrClient() throws Exception {
    String matterId =
        createMatter(
            "CONVEYANCING",
            """
            {"seller_name":"%s","seller_id_number":"%s",\
            "purchaser_name":"%s","purchaser_id_number":"%s"}"""
                .formatted(SELLER, SELLER_ID, PURCHASER, PURCHASER_ID));

    String html = preview("offer-to-purchase", matterId);

    // Parties block + signature block
    assertThat(html).contains("Seller: </strong>" + SELLER);
    assertThat(html).contains("Seller ID / Passport: </strong>" + SELLER_ID);
    assertThat(html).contains("Purchaser: </strong>" + PURCHASER);
    assertThat(html).contains("Purchaser ID / Passport: </strong>" + PURCHASER_ID);
    assertThat(html).doesNotContain("Purchaser: </strong>" + CLIENT);

    // Voetstoots: the SELLER is not liable, the PURCHASER accepts the property (G88-F12 HIGH)
    String voetstoots = clauseBlock(html, "voetstoots");
    assertThat(voetstoots).contains("meaning that " + PURCHASER + " accepts the property");
    assertThat(voetstoots).contains("and " + SELLER + " shall not be liable");
    assertThat(voetstoots).contains("the rights of " + PURCHASER + " as a consumer");
    assertThat(voetstoots).doesNotContain(ORG_NAME).doesNotContain(CLIENT);

    // The other purchaser-side clauses name the purchaser field, never the client
    for (String slug :
        new String[] {"occupation-date", "suspensive-bond", "transfer-duty-liability"}) {
      String clause = clauseBlock(html, slug);
      assertThat(clause).as(slug).contains(PURCHASER).doesNotContain(CLIENT);
    }
  }

  @Test
  void deedOfTransfer_bindsTransferorAndTransfereeFields() throws Exception {
    String matterId =
        createMatter(
            "CONVEYANCING",
            """
            {"seller_name":"%s","seller_id_number":"%s",\
            "purchaser_name":"%s","purchaser_id_number":"%s"}"""
                .formatted(SELLER, SELLER_ID, PURCHASER, PURCHASER_ID));

    String html = preview("deed-of-transfer", matterId);

    assertThat(html).contains(SELLER).contains(SELLER_ID);
    assertThat(html).contains(PURCHASER).contains(PURCHASER_ID);
    assertThat(html).doesNotContain(CLIENT);
  }

  @Test
  void bondCancellationInstruction_namesTheSellerAsMortgagor_notTheClient() throws Exception {
    String matterId =
        createMatter(
            "CONVEYANCING",
            """
            {"seller_name":"%s","seller_id_number":"%s",\
            "purchaser_name":"%s","purchaser_id_number":"%s"}"""
                .formatted(SELLER, SELLER_ID, PURCHASER, PURCHASER_ID));

    String html = preview("bond-cancellation-instruction", matterId);

    assertThat(html).contains("Mortgagor (Seller)");
    assertThat(html).contains("Name: </strong>" + SELLER);
    assertThat(html).contains("ID / Passport Number: </strong>" + SELLER_ID);
    assertThat(html).doesNotContain(CLIENT).doesNotContain(PURCHASER);
    assertThat(html).doesNotContain("on behalf of our client");
  }

  @Test
  void letterOfDemand_isAddressedToTheOpposingParty_notTheClient() throws Exception {
    String matterId =
        createMatter("COLLECTIONS", "{\"opposing_party\":\"%s\"}".formatted(OPPOSING_PARTY));

    String html = preview("letter-of-demand", matterId);

    assertThat(html).contains("Dear " + OPPOSING_PARTY + ",");
    // the firm's client is the creditor ("our client"), never the addressee
    assertThat(html).doesNotContain("Dear " + CLIENT);
    assertThat(html).contains("you are indebted to our client");
  }

  // --- helpers ---

  private String createMatter(String workType, String customFieldsJson) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Party binding %s","clientId":"%s","workType":"%s",\
                        "customFields":%s}"""
                            .formatted(UUID.randomUUID(), clientId, workType, customFieldsJson)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  private String preview(String templateKey, String entityId) throws Exception {
    UUID templateId =
        inTenantTx(
            () ->
                documentTemplateRepository
                    .findPackSeeded("legal-za", templateKey)
                    .getFirst()
                    .getId());
    var result =
        mockMvc
            .perform(
                post("/api/templates/" + templateId + "/preview")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entityId\":\"%s\"}".formatted(entityId)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.html");
  }

  /** The rendered body of one clause block (up to the next clause block or the end). */
  private static String clauseBlock(String html, String slug) {
    String marker = "data-clause-slug=\"" + slug + "\"";
    int start = html.indexOf(marker);
    assertThat(start).as("clause block %s rendered", slug).isNotNegative();
    int next = html.indexOf("data-clause-slug=", start + marker.length());
    return next < 0 ? html.substring(start) : html.substring(start, next);
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
