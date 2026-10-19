package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ADR-342 guard: audit {@code entityType}, EntityTag, SavedView and field-definition entity-type
 * strings are STORAGE KEYS. They stay {@code "project"}/{@code "customer"} (audit plane) and {@code
 * "PROJECT"}/{@code "CUSTOMER"} (tag/view/field-definition plane) under the renamed matter/client
 * wire (ADR-337). Renaming one side silently empties the activity tab, tag filters and saved views
 * (PR #1503) — this test turns that silent defect into a red build.
 *
 * <p>Plain JUnit source scan (no Spring context), mirroring {@code CollectionsCoreBoundaryTest}:
 * reads {@code src/main/java} as text. The literals are pinned where they actually live (the call
 * sites marked {@code // ADR-342: storage key, do not rename}); {@code EntityTagService} and {@code
 * ViewFilterHelper} are generic (callers pass the key) and {@code fielddefinition/} persists the
 * {@link EntityType} enum name, so those are covered by negative pins and the enum check. There is
 * deliberately NO global {@code "MATTER"} ban — {@code MatterClosureService} legitimately uses it
 * as a retention record type. A deliberate future rename must supersede ADR-342, ship the data
 * migration, and update this test consciously.
 */
class EntityTypeLiteralStabilityTest {

  private static final Path MAIN =
      Path.of("").toAbsolutePath().resolve("src/main/java/io/b2mash/b2b/b2bstrawman");

  private static final String WHY =
      "ADR-342: entity-type strings are storage keys (audit_events, entity_tags, saved_views,"
          + " field_definitions) and do NOT follow the matter/client wire rename (ADR-337) — ";

  private static final Pattern AUDIT_PROJECT =
      Pattern.compile("\\.entityType\\(\\s*\"project\"\\s*\\)");
  private static final Pattern AUDIT_CUSTOMER =
      Pattern.compile("\\.entityType\\(\\s*\"customer\"\\s*\\)");
  private static final Pattern AUDIT_RENAMED =
      Pattern.compile("\\.entityType\\(\\s*\"(matter|client)\"\\s*\\)");

  // ---- audit plane (lowercase) -------------------------------------------------

  @Test
  void auditPlaneStillEmitsProjectAndCustomer() {
    assertThat(read("project/ProjectService.java"))
        .as(WHY + "ProjectService must still emit audit entityType \"project\"")
        .containsPattern(AUDIT_PROJECT);
    assertThat(read("projecttemplate/ProjectTemplateService.java"))
        .as(WHY + "ProjectTemplateService must still emit audit entityType \"project\"")
        .containsPattern(AUDIT_PROJECT);
    assertThat(read("verticals/legal/closure/MatterClosureService.java"))
        .as(WHY + "MatterClosureService must still emit audit entityType \"project\"")
        .containsPattern(AUDIT_PROJECT);
    assertThat(read("customer/CustomerService.java"))
        .as(WHY + "CustomerService must still emit audit entityType \"customer\"")
        .containsPattern(AUDIT_CUSTOMER);

    List<String> renamed =
        javaSources(MAIN).stream()
            .filter(p -> AUDIT_RENAMED.matcher(read(p)).find())
            .map(p -> MAIN.relativize(p).toString())
            .toList();
    assertThat(renamed).as(WHY + "no audit entityType may be \"matter\"/\"client\"").isEmpty();
  }

  // ---- EntityTag / SavedView / custom-field-filter plane (UPPERCASE) ------------

  @Test
  void entityTagAndSavedViewPlaneStillKeyedProjectAndCustomer() {
    assertThat(read("project/ProjectController.java"))
        .as(WHY + "ProjectController tags are keyed \"PROJECT\"")
        .containsPattern("getEntityTags\\(\\s*\"PROJECT\"")
        .containsPattern("setEntityTags\\(\\s*\"PROJECT\"");
    assertThat(read("project/ProjectService.java"))
        .as(WHY + "ProjectService saved-view filter + tag batch are keyed \"PROJECT\"")
        .containsPattern("\"PROJECT\",\\s*\"projects\"")
        .containsPattern("getEntityTagsBatch\\(\\s*\"PROJECT\"");
    assertThat(read("projecttemplate/ProjectTemplateService.java"))
        .as(WHY + "template-instantiated matters are tagged \"PROJECT\"")
        .containsPattern("new EntityTag\\(\\s*\\w+,\\s*\"PROJECT\"");
    assertThat(read("customer/CustomerController.java"))
        .as(WHY + "CustomerController saved-view filter + tags are keyed \"CUSTOMER\"")
        .containsPattern("\"CUSTOMER\",\\s*\"customers\"")
        .containsPattern("getEntityTags\\(\\s*\"CUSTOMER\"")
        .containsPattern("setEntityTags\\(\\s*\"CUSTOMER\"");
    assertThat(read("view/CustomFieldFilterUtil.java"))
        .as(WHY + "promoted-slug map is keyed \"PROJECT\"/\"CUSTOMER\"")
        .containsPattern("\"PROJECT\",\\s*PROJECT_PROMOTED_SLUGS")
        .containsPattern("\"CUSTOMER\",\\s*CUSTOMER_PROMOTED_SLUGS");
    assertThat(read("template/ProjectContextBuilder.java"))
        .as(WHY + "matter document context reads tags keyed \"PROJECT\"")
        .containsPattern("buildTagsList\\(\\s*\"PROJECT\"");
    assertThat(read("template/CustomerContextBuilder.java"))
        .as(WHY + "client document context reads tags keyed \"CUSTOMER\"")
        .containsPattern("buildTagsList\\(\\s*\"CUSTOMER\"");
    assertThat(read("task/TaskController.java"))
        .as(WHY + "TaskController tags are keyed \"TASK\"")
        .containsPattern("getEntityTags\\(\\s*\"TASK\"");

    List<Path> plane = new ArrayList<>();
    Stream.of(
            "project/ProjectController.java",
            "project/ProjectService.java",
            "projecttemplate/ProjectTemplateService.java",
            "customer/CustomerController.java",
            "task/TaskController.java",
            "template/ProjectContextBuilder.java",
            "template/CustomerContextBuilder.java")
        .map(MAIN::resolve)
        .forEach(plane::add);
    plane.addAll(javaSources(MAIN.resolve("tag")));
    plane.addAll(javaSources(MAIN.resolve("view")));
    plane.addAll(javaSources(MAIN.resolve("fielddefinition")));
    assertThat(plane)
        .as("plane sources must exist (guard against moved packages)")
        .hasSizeGreaterThan(5);

    List<String> renamed =
        plane.stream()
            .filter(p -> read(p).contains("\"MATTER\"") || read(p).contains("\"CLIENT\""))
            .map(p -> MAIN.relativize(p).toString())
            .toList();
    assertThat(renamed)
        .as(WHY + "the tag/view/field-definition plane must never use \"MATTER\"/\"CLIENT\"")
        .isEmpty();
  }

  // ---- field-definition plane (enum persisted by name) --------------------------

  @Test
  void fieldDefinitionEntityTypeEnumKeepsProjectAndCustomer() {
    List<String> names = Arrays.stream(EntityType.values()).map(Enum::name).toList();
    assertThat(names)
        .as(WHY + "field_definitions.entity_type stores EntityType.name()")
        .contains("PROJECT", "CUSTOMER", "TASK", "INVOICE")
        .doesNotContain("MATTER", "CLIENT");
    assertThat(read("fielddefinition/FieldDefinition.java"))
        .as(WHY + "the enum name is the persisted key only while @Enumerated(EnumType.STRING)")
        .contains("@Enumerated(EnumType.STRING)");
  }

  // ---- helpers ------------------------------------------------------------------

  private static String read(String relative) {
    return read(MAIN.resolve(relative));
  }

  private static String read(Path p) {
    try {
      return Files.readString(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + p + " (moved? update ADR-342 guard)", e);
    }
  }

  private static List<Path> javaSources(Path dir) {
    try (Stream<Path> walk = Files.walk(dir)) {
      return walk.filter(p -> p.toString().endsWith(".java")).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
