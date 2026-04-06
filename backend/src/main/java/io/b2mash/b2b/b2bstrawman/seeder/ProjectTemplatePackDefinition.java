package io.b2mash.b2b.b2bstrawman.seeder;

import java.math.BigDecimal;
import java.util.List;

/** DTO record for deserializing project template pack JSON files from the classpath. */
public record ProjectTemplatePackDefinition(
    String packId, String verticalProfile, int version, List<TemplateEntry> templates) {
  public record TemplateEntry(
      String name,
      String namePattern,
      String description,
      boolean billableDefault,
      String matterType,
      List<TaskEntry> tasks) {}

  public record TaskEntry(
      String name,
      String description,
      String priority,
      String assigneeRole,
      boolean billable,
      BigDecimal estimatedHours) {}
}
