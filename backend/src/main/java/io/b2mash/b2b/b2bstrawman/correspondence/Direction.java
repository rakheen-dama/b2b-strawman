package io.b2mash.b2b.b2bstrawman.correspondence;

/**
 * Direction of a filed correspondence. Only {@link #INBOUND} is written in v1; {@link #OUTBOUND} is
 * modelled now to avoid a future enum migration when a send path is added.
 */
public enum Direction {
  INBOUND,
  OUTBOUND
}
