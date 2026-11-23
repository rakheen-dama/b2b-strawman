package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnMappingRequest;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.GenericTakeOnDatasetHandler;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnFieldCatalogue;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnFieldCatalogue.FieldSpec;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Request checks of {@code PUT …/mapping} (§17.3.2): every target is a field of the source's
 * section, every header exists in the file and is used once, custom fields are active definitions
 * of the dataset's entity type, defaults name catalogue fields, and every required field is mapped
 * or defaulted. Refusals are 400s with X3 text. Parse options: only the date format and decimal
 * separator are taken from the request (the CSV delimiter and encoding applied when the file was
 * read and stay as recorded).
 */
@Component
public class TakeOnMappingValidator {

  static final int MAX_DEFAULT_LENGTH = 500;
  static final int MAX_DATE_FORMAT_LENGTH = 30;

  /** The mapping and parse options to store. */
  public record Checked(TakeOnColumnMapping mapping, TakeOnParseOptions parseOptions) {}

  private final TakeOnRowRepository rows;
  private final TakeOnSourceFiles sourceFiles;
  private final FieldDefinitionRepository fieldDefinitions;

  public TakeOnMappingValidator(
      TakeOnRowRepository rows,
      TakeOnSourceFiles sourceFiles,
      FieldDefinitionRepository fieldDefinitions) {
    this.rows = rows;
    this.sourceFiles = sourceFiles;
    this.fieldDefinitions = fieldDefinitions;
  }

  public Checked check(TakeOnBatch batch, TakeOnSource source, TakeOnMappingRequest request) {
    var mapping =
        new TakeOnColumnMapping(
            request.targets(), request.customFields(), request.parked(), request.defaults());
    Map<String, FieldSpec> fields =
        TakeOnFieldCatalogue.fields(batch.getDataset(), source.getSection()).stream()
            .collect(Collectors.toMap(FieldSpec::key, f -> f));
    Set<String> headers = headersOf(source);

    for (var key : mapping.targets().keySet()) {
      if (!fields.containsKey(key)) {
        throw refused(
            "There is no field \"%s\" in this file's part of the take-on.".formatted(key));
      }
    }
    var used = new HashSet<String>();
    for (String header : allHeaders(mapping)) {
      if (!headers.contains(header)) {
        throw refused("The file has no column \"%s\".".formatted(header));
      }
      if (!used.add(header)) {
        throw refused("The column \"%s\" is mapped more than once.".formatted(header));
      }
    }
    checkCustomFields(batch, mapping);
    for (var entry : mapping.defaults().entrySet()) {
      if (!fields.containsKey(entry.getKey())) {
        throw refused(
            "There is no field \"%s\" in this file's part of the take-on."
                .formatted(entry.getKey()));
      }
      if (entry.getValue().length() > MAX_DEFAULT_LENGTH) {
        throw refused("A default value is longer than 500 characters.");
      }
    }
    for (FieldSpec field : fields.values()) {
      boolean defaulted =
          mapping.defaults().containsKey(field.key())
              && !mapping.defaults().get(field.key()).isBlank();
      if (field.required() && !mapping.targets().containsKey(field.key()) && !defaulted) {
        throw refused("Map %s to a column, or give it a default value.".formatted(field.label()));
      }
    }
    return new Checked(mapping, parseOptions(source, request));
  }

  private void checkCustomFields(TakeOnBatch batch, TakeOnColumnMapping mapping) {
    if (mapping.customFields().isEmpty()) {
      return;
    }
    var entityType = GenericTakeOnDatasetHandler.customFieldEntityType(batch.getDataset());
    if (entityType.isEmpty()) {
      throw refused("A trust take-on has no custom fields to fill.");
    }
    Set<String> active =
        fieldDefinitions.findByEntityTypeAndActiveTrueOrderBySortOrder(entityType.get()).stream()
            .map(FieldDefinition::getSlug)
            .collect(Collectors.toSet());
    for (String slug : mapping.customFields().keySet()) {
      if (!active.contains(slug)) {
        throw refused("There is no active custom field \"%s\".".formatted(slug));
      }
    }
  }

  private static TakeOnParseOptions parseOptions(
      TakeOnSource source, TakeOnMappingRequest request) {
    var stored = TakeOnParseOptions.fromMap(source.getParseOptions());
    var given = request.parseOptions();
    if (given == null) {
      return stored;
    }
    String dateFormat = stored.dateFormat();
    if (given.dateFormat() != null && !given.dateFormat().isBlank()) {
      dateFormat = given.dateFormat().strip();
      if (dateFormat.length() > MAX_DATE_FORMAT_LENGTH) {
        throw refused("\"%s\" is not a date format.".formatted(dateFormat));
      }
      try {
        DateTimeFormatter.ofPattern(dateFormat, Locale.ENGLISH);
      } catch (IllegalArgumentException e) {
        throw refused("\"%s\" is not a date format.".formatted(dateFormat));
      }
    }
    var separator = stored.decimalSeparator();
    if (given.decimalSeparator() != null && !given.decimalSeparator().isBlank()) {
      try {
        separator =
            TakeOnParseOptions.DecimalSeparator.valueOf(
                given.decimalSeparator().strip().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw refused("The decimal separator must be a point or a comma.");
      }
    }
    return new TakeOnParseOptions(dateFormat, separator, stored.delimiter(), stored.encoding());
  }

  /** The file's headers: the key set of its first stored row, else a re-read of the file. */
  private Set<String> headersOf(TakeOnSource source) {
    return rows.findFirstBySourceIdOrderByRowNumberAsc(source.getId())
        .map(r -> (Set<String>) new HashSet<>(r.getRawValues().keySet()))
        .orElseGet(() -> new HashSet<>(sourceFiles.reread(source).headers()));
  }

  private static List<String> allHeaders(TakeOnColumnMapping mapping) {
    var all = new ArrayList<String>();
    all.addAll(mapping.targets().values());
    all.addAll(mapping.customFields().values());
    all.addAll(mapping.parked()); // a header parked twice is "mapped more than once"
    return all;
  }

  private static InvalidStateException refused(String detail) {
    return new InvalidStateException("Mapping not saved", detail);
  }
}
