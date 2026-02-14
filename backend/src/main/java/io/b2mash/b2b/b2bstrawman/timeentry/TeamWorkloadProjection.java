package io.b2mash.b2b.b2bstrawman.timeentry;

import java.util.UUID;

/** Projection for team workload native SQL query, returning flat rows per member per project. */
public interface TeamWorkloadProjection {

  UUID getMemberId();

  String getMemberName();

  UUID getProjectId();

  String getProjectName();

  Long getTotalMinutes();

  Long getBillableMinutes();
}
