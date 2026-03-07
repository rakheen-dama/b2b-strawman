package io.b2mash.b2b.b2bstrawman.capacity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared math utilities for capacity and utilization calculations. */
final class CapacityMathUtil {

  private CapacityMathUtil() {}

  /** Calculates percentage = (numerator / denominator) * 100. Returns ZERO if denominator is 0. */
  static BigDecimal pct(BigDecimal numerator, BigDecimal denominator) {
    if (denominator.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
  }
}
