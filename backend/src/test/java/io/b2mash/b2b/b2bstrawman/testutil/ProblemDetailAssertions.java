package io.b2mash.b2b.b2bstrawman.testutil;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;

/** Shared assertions for verifying ProblemDetail (RFC 9457) responses. */
public final class ProblemDetailAssertions {

  private ProblemDetailAssertions() {}

  /** Assert a ProblemDetail response with given status and title. */
  public static void assertProblem(
      ResultActions result, HttpStatus expectedStatus, String expectedTitle) throws Exception {
    result
        .andExpect(status().is(expectedStatus.value()))
        .andExpect(jsonPath("$.status").value(expectedStatus.value()))
        .andExpect(jsonPath("$.title").value(expectedTitle))
        .andExpect(jsonPath("$.detail").exists());
  }

  /** Assert a ProblemDetail response with status, title, and detail containing a substring. */
  public static void assertProblemWithDetail(
      ResultActions result, HttpStatus expectedStatus, String expectedTitle, String detailContains)
      throws Exception {
    assertProblem(result, expectedStatus, expectedTitle);
    result.andExpect(jsonPath("$.detail").value(Matchers.containsString(detailContains)));
  }

  /** Assert a validation error response with field-level errors. */
  public static void assertValidationErrors(ResultActions result, String... expectedFields)
      throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.fieldErrors").isArray());
    for (String field : expectedFields) {
      result.andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')]").exists());
    }
  }
}
