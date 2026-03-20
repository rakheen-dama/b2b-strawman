package io.b2mash.b2b.b2bstrawman.seeder;

import java.util.List;
import java.util.Map;

/** DTO record for deserializing schedule pack JSON files from the classpath. */
public record SchedulePackDefinition(
    String packId, String verticalProfile, int version, List<ScheduleEntry> schedules) {
  public record ScheduleEntry(
      String name,
      String projectTemplateName,
      String recurrence,
      String description,
      Map<String, Object> postCreateActions) {}
}
