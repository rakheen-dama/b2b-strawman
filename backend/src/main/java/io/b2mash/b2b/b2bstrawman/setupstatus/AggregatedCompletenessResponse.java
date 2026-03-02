package io.b2mash.b2b.b2bstrawman.setupstatus;

import java.util.List;

public record AggregatedCompletenessResponse(
    List<MissingFieldSummary> topMissingFields, long incompleteCount, long totalCount) {}
