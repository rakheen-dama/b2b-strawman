package io.b2mash.b2b.b2bstrawman.compliance;

import java.util.Arrays;
import java.util.List;

/**
 * Filter parameters for querying compliance audit findings. All fields are optional -- null means
 * no filter on that dimension.
 */
public record FindingFilterParams(
    List<String> severities, List<String> categories, List<String> statuses) {

  /** Factory for constructing filter params from comma-separated request parameter strings. */
  public static FindingFilterParams fromRequestParams(
      String severity, String category, String status) {
    return new FindingFilterParams(split(severity), split(category), split(status));
  }

  private static List<String> split(String param) {
    return param == null || param.isBlank() ? null : Arrays.asList(param.split(","));
  }
}
