package io.b2mash.b2b.b2bstrawman.testutil;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared test utility for creating domain entities via the API. Replaces private {@code
 * createProject()}, {@code createCustomer()}, {@code createTask()}, and {@code createTag()} helpers
 * that were duplicated across 40+ test files.
 *
 * <p>All methods are static — pass the test's {@link MockMvc} and JWT processor as arguments.
 * Entity IDs are extracted from the response body ({@code $.id}) or from the Location header.
 */
public final class TestEntityHelper {

  private TestEntityHelper() {}

  // ── Projects ──────────────────────────────────────────────────────────────

  /** Creates a project and returns its ID. */
  public static String createProject(
      MockMvc mockMvc, JwtRequestPostProcessor jwt, String name, String description)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"%s"}
                        """
                            .formatted(name, description)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractId(result);
  }

  /** Creates a project with a default description. */
  public static String createProject(MockMvc mockMvc, JwtRequestPostProcessor jwt, String name)
      throws Exception {
    return createProject(mockMvc, jwt, name, "Test project");
  }

  // ── Customers ─────────────────────────────────────────────────────────────

  /** Creates a customer and returns its ID. */
  public static String createCustomer(
      MockMvc mockMvc, JwtRequestPostProcessor jwt, String name, String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","email":"%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractId(result);
  }

  // ── Tasks ─────────────────────────────────────────────────────────────────

  /** Creates a task in a project and returns its ID. */
  public static String createTask(
      MockMvc mockMvc, JwtRequestPostProcessor jwt, String projectId, String title, String priority)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title":"%s","priority":"%s"}
                        """
                            .formatted(title, priority)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractId(result);
  }

  /** Creates a task with default MEDIUM priority. */
  public static String createTask(
      MockMvc mockMvc, JwtRequestPostProcessor jwt, String projectId, String title)
      throws Exception {
    return createTask(mockMvc, jwt, projectId, title, "MEDIUM");
  }

  // ── Tags ──────────────────────────────────────────────────────────────────

  /** Creates a tag and returns its ID. */
  public static String createTag(
      MockMvc mockMvc, JwtRequestPostProcessor jwt, String name, String color) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/tags")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","color":"%s"}
                        """
                            .formatted(name, color)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractId(result);
  }

  // ── ID Extraction ─────────────────────────────────────────────────────────

  /**
   * Extracts an entity ID from the response. Tries the JSON body first ({@code $.id}), falls back
   * to the Location header.
   */
  public static String extractId(MvcResult result) throws Exception {
    String body = result.getResponse().getContentAsString();
    if (body != null && !body.isBlank()) {
      try {
        return JsonPath.read(body, "$.id").toString();
      } catch (com.jayway.jsonpath.PathNotFoundException e) {
        // fall through to Location header
      }
    }
    return extractIdFromLocation(result);
  }

  /**
   * Extracts an entity ID from the Location header. Replaces the private {@code
   * extractIdFromLocation()} helper that was duplicated across 46 test files.
   */
  public static String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    if (location == null) {
      throw new IllegalStateException("No Location header in response");
    }
    return location.substring(location.lastIndexOf('/') + 1);
  }
}
