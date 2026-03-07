package io.b2mash.b2b.b2bstrawman.accessrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
class AccessRequestPublicControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private AccessRequestRepository accessRequestRepository;

  @Test
  void submitRequest_validCompanyEmail_returns200() throws Exception {
    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "jane@acme-corp.com",
                      "fullName": "Jane Smith",
                      "organizationName": "Acme Corp",
                      "country": "South Africa",
                      "industry": "Accounting"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Verification code sent to jane@acme-corp.com"))
        .andExpect(jsonPath("$.expiresInMinutes").value(10));
  }

  @Test
  void submitRequest_blockedDomain_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "jane@gmail.com",
                      "fullName": "Jane Smith",
                      "organizationName": "Acme Corp",
                      "country": "South Africa",
                      "industry": "Accounting"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Blocked email domain"))
        .andExpect(jsonPath("$.detail").value("Please use a company email address"));
  }

  @Test
  void submitRequest_duplicatePending_returns409() throws Exception {
    // First request succeeds
    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "duplicate@testcorp.com",
                      "fullName": "Duplicate User",
                      "organizationName": "Test Corp",
                      "country": "South Africa",
                      "industry": "Legal"
                    }
                    """))
        .andExpect(status().isOk());

    // Second request with same email returns 409
    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "duplicate@testcorp.com",
                      "fullName": "Duplicate User",
                      "organizationName": "Test Corp",
                      "country": "South Africa",
                      "industry": "Legal"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Duplicate request"))
        .andExpect(jsonPath("$.detail").value("A request for this email is already pending"));
  }

  @Test
  void submitRequest_missingFields_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "jane@acme.com"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void submitRequest_createsEntityWithPendingVerification() throws Exception {
    String email = "entity-check@newcorp.com";

    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "fullName": "Entity Check",
                      "organizationName": "New Corp",
                      "country": "South Africa",
                      "industry": "Consulting"
                    }
                    """
                        .formatted(email)))
        .andExpect(status().isOk());

    var entity =
        accessRequestRepository
            .findByEmailAndStatus(email, AccessRequestStatus.PENDING_VERIFICATION)
            .orElseThrow();

    assertThat(entity.getEmail()).isEqualTo(email);
    assertThat(entity.getFullName()).isEqualTo("Entity Check");
    assertThat(entity.getOrganizationName()).isEqualTo("New Corp");
    assertThat(entity.getStatus()).isEqualTo(AccessRequestStatus.PENDING_VERIFICATION);
  }

  @Test
  void submitRequest_noAuthRequired() throws Exception {
    // No JWT, no auth header — should still succeed
    var result =
        mockMvc
            .perform(
                post("/api/access-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "noauth@securecorp.com",
                          "fullName": "No Auth User",
                          "organizationName": "Secure Corp",
                          "country": "Kenya",
                          "industry": "Finance"
                        }
                        """))
            .andReturn();

    int statusCode = result.getResponse().getStatus();
    assertThat(statusCode).isNotEqualTo(401);
    assertThat(statusCode).isNotEqualTo(403);
    assertThat(statusCode).isEqualTo(200);
  }

  @Test
  void submitRequest_otpHashStored() throws Exception {
    String email = "otphash@checkcorp.com";

    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "fullName": "OTP Hash Check",
                      "organizationName": "Check Corp",
                      "country": "South Africa",
                      "industry": "Engineering"
                    }
                    """
                        .formatted(email)))
        .andExpect(status().isOk());

    var entity =
        accessRequestRepository
            .findByEmailAndStatus(email, AccessRequestStatus.PENDING_VERIFICATION)
            .orElseThrow();

    assertThat(entity.getOtpHash()).isNotNull();
    assertThat(entity.getOtpHash()).startsWith("$2");
    assertThat(entity.getOtpExpiresAt()).isNotNull();
    assertThat(entity.getOtpExpiresAt()).isAfter(java.time.Instant.now());
  }

  @Test
  void submitRequest_previousRejectedEmail_allowsNewRequest() throws Exception {
    String email = "rejected@resubmit.com";

    // Create a rejected request directly in the DB
    var rejected =
        new AccessRequest(email, "Rejected User", "Resubmit Corp", "South Africa", "Legal");
    rejected.setStatus(AccessRequestStatus.REJECTED);
    accessRequestRepository.save(rejected);

    // New request with same email should succeed since previous was rejected
    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "fullName": "Rejected User",
                      "organizationName": "Resubmit Corp",
                      "country": "South Africa",
                      "industry": "Legal"
                    }
                    """
                        .formatted(email)))
        .andExpect(status().isOk());
  }
}
