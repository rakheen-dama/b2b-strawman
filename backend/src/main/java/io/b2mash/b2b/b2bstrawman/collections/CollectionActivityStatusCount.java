package io.b2mash.b2b.b2bstrawman.collections;

/**
 * Spring Data projection for a reminder-activity count grouped by status (Phase 83, 593A). Feeds
 * the cash digest's "reminder activity summary" — how many chase actions landed in each status over
 * the trailing window.
 */
public interface CollectionActivityStatusCount {

  CollectionActivityStatus getStatus();

  long getCount();
}
