package io.b2mash.b2b.b2bstrawman.timeentry;

/** Spring Data projection interface for trend aggregation (hours grouped by period). */
public interface TrendProjection {

  String getPeriod();

  Long getTotalMinutes();
}
