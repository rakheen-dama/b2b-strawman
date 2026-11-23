package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.create;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Phase 93 653A: a take-on batch lives in its tenant's schema only — every batch and source
 * endpoint answers 404 from another tenant, the other tenant's history does not list it, and the
 * archived file's S3 key carries the owning tenant's schema. (Extended by 655A, 657C, 658A.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnTenantIsolationIntegrationTest {

  private static final String ORG_A = "org_takeon_iso_a";
  private static final String ORG_B = "org_takeon_iso_b";
  private static final String OWNER_A = "user_toi_owner_a";
  private static final String OWNER_B = "user_toi_owner_b";
  private static final String CSV =
      "Client name,Email,Client type,CDD completed date,Beneficial ownership,Screening,Records\n"
          + "A,iso@example.test,Individual,01/02/2024,Done,Screened clear,Box\n";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private StorageService storageService;

  private String schemaA;
  private String schemaB;
  private UUID batchId;
  private UUID sourceId;

  @BeforeAll
  void setup() throws Exception {
    schemaA = provisioningService.provisionTenant(ORG_A, "Take-on Iso A", "legal-za").schemaName();
    schemaB = provisioningService.provisionTenant(ORG_B, "Take-on Iso B", "legal-za").schemaName();
    TestMemberHelper.syncMember(mockMvc, ORG_A, OWNER_A, "toi_a@test.com", "Iso A", "owner");
    TestMemberHelper.syncMember(mockMvc, ORG_B, OWNER_B, "toi_b@test.com", "Iso B", "owner");
    UUID[] ids = create(mockMvc, ownerA(), "CLIENTS", "iso.csv", TakeOnTestFiles.csv(CSV));
    batchId = ids[0];
    sourceId = ids[1];
  }

  @Test
  void everyBatchAndSourceEndpointIsNotFoundFromAnotherTenant() throws Exception {
    String batch = "/api/take-on/batches/" + batchId;
    String source = batch + "/sources/" + sourceId;
    List<AbstractMockHttpServletRequestBuilder<?>> requests =
        List.<AbstractMockHttpServletRequestBuilder<?>>of(
            get(batch),
            get(batch + "/rows"),
            get(source + "/preview"),
            get(source + "/file"),
            multipart(batch + "/sources")
                .file(new MockMultipartFile("file", "x.csv", "text/csv", CSV.getBytes()))
                .param("section", "MAIN"),
            delete(source),
            json(put(source + "/sheet"), "{\"sheetName\":\"CSV\",\"headerRow\":1}"),
            json(put(source + "/mapping"), "{\"targets\":{}}"),
            json(put(batch + "/position"), "{\"cutoverDate\":\"2026-10-31\"}"),
            post(batch + "/validate"),
            post(batch + "/cancel"));
    for (var request : requests) {
      mockMvc
          .perform(request.with(ownerB()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.title").value("Take-on batch not found"));
    }
    // still intact for its own tenant
    mockMvc
        .perform(get(batch).with(ownerA()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"));
  }

  @Test
  void anotherTenantsHistoryDoesNotListTheBatch() throws Exception {
    String json =
        mockMvc
            .perform(get("/api/take-on/batches?size=200").with(ownerB()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(json, "$.content[*].id");
    assertThat(ids).doesNotContain(batchId.toString());
  }

  @Test
  void theArchivedFileKeyCarriesTheOwningTenantsSchema() {
    assertThat(storageService.listKeys(TakeOnStorageKeys.batchPrefix(schemaA, batchId)))
        .singleElement()
        .satisfies(key -> assertThat(key).startsWith("take-on/" + schemaA + "/" + batchId + "/"));
    assertThat(storageService.listKeys(TakeOnStorageKeys.batchPrefix(schemaB, batchId))).isEmpty();
    assertThat(schemaA).isNotEqualTo(schemaB);
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder builder, String body) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private RequestPostProcessor ownerA() {
    return TestJwtFactory.ownerJwt(ORG_A, OWNER_A);
  }

  private RequestPostProcessor ownerB() {
    return TestJwtFactory.ownerJwt(ORG_B, OWNER_B);
  }
}
