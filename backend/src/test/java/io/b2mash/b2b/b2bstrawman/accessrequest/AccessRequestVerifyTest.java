package io.b2mash.b2b.b2bstrawman.accessrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessRequestVerifyTest {

  private static final String KNOWN_OTP = "123456";

  @Autowired private MockMvc mockMvc;

  @Autowired private AccessRequestRepository accessRequestRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private JavaMailSender javaMailSender;

  @BeforeEach
  void setUpMailMock() {
    when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
  }

  private AccessRequest createPendingVerificationRequest(String email) {
    var entity = new AccessRequest(email, "Test User", "Test Corp", "South Africa", "Accounting");
    entity.setOtpHash(passwordEncoder.encode(KNOWN_OTP));
    entity.setOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
    return accessRequestRepository.save(entity);
  }

  @Test
  void verifyOtp_validOtp_returns200() throws Exception {
    String email = "valid-otp@verify-test.com";
    createPendingVerificationRequest(email);

    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "otp": "%s"}
                    """
                        .formatted(email, KNOWN_OTP)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Email verified successfully"));
  }

  @Test
  void verifyOtp_invalidOtp_returns400() throws Exception {
    String email = "invalid-otp@verify-test.com";
    createPendingVerificationRequest(email);

    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "otp": "999999"}
                    """
                        .formatted(email)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid OTP"))
        .andExpect(jsonPath("$.detail").value("The verification code is incorrect"));
  }

  @Test
  void verifyOtp_expiredOtp_returns400() throws Exception {
    String email = "expired-otp@verify-test.com";
    var entity = createPendingVerificationRequest(email);

    // Manually expire the OTP
    entity.setOtpExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
    accessRequestRepository.save(entity);

    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "otp": "%s"}
                    """
                        .formatted(email, KNOWN_OTP)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("OTP expired"))
        .andExpect(jsonPath("$.detail").value("Please submit a new access request"));
  }

  @Test
  void verifyOtp_tooManyAttempts_returns429() throws Exception {
    String email = "too-many@verify-test.com";
    var entity = createPendingVerificationRequest(email);

    // Set attempts to max
    entity.setOtpAttempts(5);
    accessRequestRepository.save(entity);

    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "otp": "%s"}
                    """
                        .formatted(email, KNOWN_OTP)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.title").value("Too many attempts"))
        .andExpect(jsonPath("$.detail").value("Maximum verification attempts exceeded"));
  }

  @Test
  void verifyOtp_noMatchingRequest_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "unknown@nowhere.com", "otp": "123456"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Verification failed"));
  }

  @Test
  void verifyOtp_promotesToPending() throws Exception {
    String email = "promote@verify-test.com";
    createPendingVerificationRequest(email);

    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "otp": "%s"}
                    """
                        .formatted(email, KNOWN_OTP)))
        .andExpect(status().isOk());

    var entity =
        accessRequestRepository
            .findByEmailAndStatus(email, AccessRequestStatus.PENDING)
            .orElseThrow();

    assertThat(entity.getStatus()).isEqualTo(AccessRequestStatus.PENDING);
    assertThat(entity.getOtpVerifiedAt()).isNotNull();
    assertThat(entity.getOtpHash()).isNull();
  }

  @Test
  void verifyOtp_incrementsAttemptCount() throws Exception {
    String email = "attempts@verify-test.com";
    createPendingVerificationRequest(email);

    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "otp": "999999"}
                    """
                        .formatted(email)))
        .andExpect(status().isBadRequest());

    var entity =
        accessRequestRepository
            .findByEmailAndStatus(email, AccessRequestStatus.PENDING_VERIFICATION)
            .orElseThrow();

    assertThat(entity.getOtpAttempts()).isEqualTo(1);
  }
}
