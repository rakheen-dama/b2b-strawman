package io.b2mash.b2b.b2bstrawman.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ADR-352 / G-B7: only {@code GET /api/portal/acceptance/{token}/pdf} may be framed, and only by
 * the portal origin. Headers are written by HeaderWriterFilter regardless of the response status,
 * so unauthenticated / unknown-token requests are enough — no tenant provisioning needed. Same
 * context-cache key as {@link SecurityIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FrameHeadersIntegrationTest {

  private static final String UNKNOWN_TOKEN = "frame-headers-unknown-token";

  @Autowired private MockMvc mockMvc;

  @Test
  void acceptancePdf_allowsPortalFraming_withoutXFrameOptions() throws Exception {
    mockMvc
        .perform(get("/api/portal/acceptance/" + UNKNOWN_TOKEN + "/pdf"))
        .andExpect(status().isNotFound())
        .andExpect(
            header().string("Content-Security-Policy", "frame-ancestors http://localhost:3002"))
        .andExpect(header().doesNotExist("X-Frame-Options"));
  }

  @Test
  void acceptancePageJson_keepsXFrameOptionsDeny() throws Exception {
    mockMvc
        .perform(get("/api/portal/acceptance/" + UNKNOWN_TOKEN))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().doesNotExist("Content-Security-Policy"));
  }

  @Test
  void staffApi_keepsXFrameOptionsDeny() throws Exception {
    mockMvc
        .perform(get("/api/matters"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().doesNotExist("Content-Security-Policy"));
  }

  @Test
  void portalChain_keepsXFrameOptionsDeny() throws Exception {
    mockMvc
        .perform(get("/portal/matters"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().doesNotExist("Content-Security-Policy"));
  }
}
