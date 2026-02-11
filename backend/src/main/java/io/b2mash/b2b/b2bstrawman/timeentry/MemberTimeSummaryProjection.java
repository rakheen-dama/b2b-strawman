package io.b2mash.b2b.b2bstrawman.timeentry;

import java.util.UUID;

/** Spring Data projection interface for per-member time aggregation within a project. */
public interface MemberTimeSummaryProjection {

  UUID getMemberId();

  String getMemberName();

  Long getBillableMinutes();

  Long getNonBillableMinutes();

  Long getTotalMinutes();
}
