package io.b2mash.b2b.b2bstrawman.testutil;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared test utility for syncing members via the internal API. Replaces the private {@code
 * syncMember()} helper that was duplicated across 257+ test files.
 *
 * <p>All methods are static — pass the test's {@link MockMvc} instance as the first argument.
 */
public final class TestMemberHelper {

  public static final String API_KEY = "test-api-key";

  private TestMemberHelper() {}

  /**
   * Syncs a member via {@code POST /internal/members/sync} and returns the memberId.
   *
   * <p>Expects HTTP 201 Created (new member). For updating existing members, use {@link
   * #updateMember}.
   */
  public static String syncMember(
      MockMvc mockMvc, String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s",\
                        "name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  /**
   * Syncs (updates) an existing member. Expects HTTP 200 OK instead of 201 Created. Returns the
   * memberId.
   */
  public static String updateMember(
      MockMvc mockMvc, String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s",\
                        "name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  /**
   * Syncs a member without capturing the response. Use when the memberId is not needed (e.g.
   * background setup).
   */
  public static void syncMemberQuietly(
      MockMvc mockMvc, String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s",\
                    "name":"%s","avatarUrl":null,"orgRole":"%s"}
                    """
                        .formatted(orgId, clerkUserId, email, name, orgRole)))
        .andExpect(status().isCreated());
  }

  /** Syncs an owner with conventional test defaults. */
  public static String syncOwner(MockMvc mockMvc, String orgId, String subjectPrefix)
      throws Exception {
    return syncMember(
        mockMvc,
        orgId,
        "user_" + subjectPrefix + "_owner",
        subjectPrefix + "_owner@test.com",
        subjectPrefix.substring(0, 1).toUpperCase() + subjectPrefix.substring(1) + " Owner",
        "owner");
  }

  /** Syncs an admin with conventional test defaults. */
  public static String syncAdmin(MockMvc mockMvc, String orgId, String subjectPrefix)
      throws Exception {
    return syncMember(
        mockMvc,
        orgId,
        "user_" + subjectPrefix + "_admin",
        subjectPrefix + "_admin@test.com",
        subjectPrefix.substring(0, 1).toUpperCase() + subjectPrefix.substring(1) + " Admin",
        "admin");
  }

  /** Syncs a regular member with conventional test defaults. */
  public static String syncRegularMember(MockMvc mockMvc, String orgId, String subjectPrefix)
      throws Exception {
    return syncMember(
        mockMvc,
        orgId,
        "user_" + subjectPrefix + "_member",
        subjectPrefix + "_member@test.com",
        subjectPrefix.substring(0, 1).toUpperCase() + subjectPrefix.substring(1) + " Member",
        "member");
  }
}
