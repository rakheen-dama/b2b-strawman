package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

/** Direction of a sync entry — push to external or pull from external. */
public enum SyncDirection {
  /** Outbound: push Kazi data to the external accounting system. */
  PUSH,
  /** Inbound: pull data from the external accounting system into Kazi. */
  PULL
}
