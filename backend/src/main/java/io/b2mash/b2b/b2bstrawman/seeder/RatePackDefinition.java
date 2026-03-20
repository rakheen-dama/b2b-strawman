package io.b2mash.b2b.b2bstrawman.seeder;

import java.util.List;

/** DTO record for deserializing rate pack JSON files from the classpath. */
public record RatePackDefinition(
    String packId, String verticalProfile, int version, List<RateEntry> rates) {
  public record RateEntry(String description, double hourlyRate, String currency) {}
}
