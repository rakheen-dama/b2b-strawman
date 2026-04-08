package io.b2mash.b2b.b2bstrawman.testutil;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared test utility for completing all checklist items for a customer. Completes items that don't
 * require documents and skips items that do.
 */
public final class TestChecklistHelper {

  private TestChecklistHelper() {}

  /**
   * Transitions a customer from ONBOARDING to ACTIVE. Completes any auto-instantiated checklist
   * items first. If no checklists exist (e.g., generic-onboarding with autoInstantiate=false), the
   * explicit ACTIVE transition handles it since the onboarding guard passes when no checklists are
   * pending.
   */
  public static void transitionToActive(
      MockMvc mockMvc, String customerId, JwtRequestPostProcessor jwt) throws Exception {
    completeChecklistItems(mockMvc, customerId, jwt);
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ACTIVE"}
                    """))
        .andExpect(status().isOk());
  }

  @SuppressWarnings("unchecked")
  public static void completeChecklistItems(
      MockMvc mockMvc, String customerId, JwtRequestPostProcessor jwt) throws Exception {
    var result =
        mockMvc
            .perform(get("/api/customers/" + customerId + "/checklists").with(jwt))
            .andExpect(status().isOk())
            .andReturn();
    String json = result.getResponse().getContentAsString();
    List<Map<String, Object>> instances = JsonPath.read(json, "$[*]");
    for (Map<String, Object> instance : instances) {
      List<Map<String, Object>> items = (List<Map<String, Object>>) instance.get("items");
      if (items == null) continue;
      for (Map<String, Object> item : items) {
        String itemId = (String) item.get("id");
        boolean requiresDocument = Boolean.TRUE.equals(item.get("requiresDocument"));
        if (requiresDocument) {
          mockMvc
              .perform(
                  put("/api/checklist-items/" + itemId + "/skip")
                      .with(jwt)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"reason\": \"skipped for test\"}"))
              .andExpect(status().isOk());
        } else {
          mockMvc
              .perform(
                  put("/api/checklist-items/" + itemId + "/complete")
                      .with(jwt)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"notes\": \"auto-completed for test\"}"))
              .andExpect(status().isOk());
        }
      }
    }
  }
}
