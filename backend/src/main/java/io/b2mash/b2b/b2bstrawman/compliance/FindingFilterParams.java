package io.b2mash.b2b.b2bstrawman.compliance;

import java.util.List;

/**
 * Filter parameters for querying compliance audit findings. All fields are optional -- null means
 * no filter on that dimension.
 */
public record FindingFilterParams(
    List<String> severities, List<String> categories, List<String> statuses) {}
