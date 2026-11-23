package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnColumnMapping;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSection;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSource;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnFieldCatalogue.FieldSpec;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnValueParser;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnValueParser.Parsed;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The generic checks of §17.3.2, the only handler in 653A (each dataset slice adds its own): Excel
 * error cells, required fields (or their batch defaults), dates by the source's {@code dateFormat},
 * amounts by its {@code decimalSeparator}, enum names and aliases, lengths, custom fields through
 * {@link CustomFieldValidator}, parked columns, and in-file duplicates of the lookup-free dedup
 * keys (C-25). Never blocks (hard blocks are 657B's) and never writes outside the take-on rows.
 */
@Component
public class GenericTakeOnDatasetHandler implements TakeOnDatasetHandler {

  public static final String CELL_ERROR = "CELL_ERROR";
  public static final String REQUIRED = "REQUIRED";
  public static final String CUSTOM_FIELD_INVALID = "CUSTOM_FIELD_INVALID";
  public static final String DECIMAL_INVALID = "DECIMAL_INVALID";
  public static final String DUPLICATE_IN_FILE = "DUPLICATE_IN_FILE";
  public static final String PARKED_KEY = "takeon_parked";
  public static final String CUSTOM_FIELDS_KEY = "customFields";

  private static final Pattern DECIMAL = Pattern.compile("^-?\\d{1,9}(\\.\\d{1,6})?$");
  private static final int MAX_DECIMAL_LENGTH = 40;

  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final CustomFieldValidator customFieldValidator;

  public GenericTakeOnDatasetHandler(
      FieldDefinitionRepository fieldDefinitionRepository,
      CustomFieldValidator customFieldValidator) {
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.customFieldValidator = customFieldValidator;
  }

  /** The custom-field entity type of a dataset; trust rows take no custom fields. */
  public static Optional<EntityType> customFieldEntityType(TakeOnDataset dataset) {
    return switch (dataset) {
      case CLIENTS -> Optional.of(EntityType.CUSTOMER);
      case MATTERS -> Optional.of(EntityType.PROJECT);
      case DEBTOR_BALANCES -> Optional.of(EntityType.INVOICE);
      case TRUST_POSITION -> Optional.empty();
    };
  }

  @Override
  public TakeOnDataset dataset() {
    return null;
  }

  @Override
  public List<FieldSpec> fields(TakeOnDataset dataset, TakeOnSection section) {
    return TakeOnFieldCatalogue.fields(dataset, section);
  }

  @Override
  public ValidationContext loadContext(TakeOnBatch batch, List<TakeOnSource> sources) {
    var mappings = new HashMap<UUID, ValidationContext.SourceMapping>();
    for (TakeOnSource source : sources) {
      var mapping = TakeOnColumnMapping.fromMap(source.getColumnMapping());
      if (mapping == null) {
        throw new IllegalStateException("Map every file's columns before validating.");
      }
      mappings.put(
          source.getId(),
          new ValidationContext.SourceMapping(
              source,
              fields(batch.getDataset(), source.getSection()),
              mapping.targets(),
              mapping.customFields(),
              mapping.parked(),
              mapping.defaults(),
              TakeOnParseOptions.fromMap(source.getParseOptions())));
    }
    var entityType = customFieldEntityType(batch.getDataset()).orElse(null);
    var definitions = new HashMap<String, FieldDefinition>();
    if (entityType != null) {
      for (var d :
          fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(entityType)) {
        definitions.putIfAbsent(d.getSlug(), d);
      }
    }
    return new ValidationContext(batch, mappings, entityType, definitions);
  }

  @Override
  public void mapAndValidate(TakeOnRow row, ValidationContext context) {
    var source = context.source(row.getSourceId());
    boolean xlsx = source.source().getFileFormat() == TakeOnSource.FileFormat.XLSX;
    Map<String, Object> raw = row.getRawValues();
    var errors = new ArrayList<Map<String, Object>>();

    // 1. Excel error cells (C-8): any column, kept stable across re-validation.
    for (var cell : raw.entrySet()) {
      TakeOnValueParser.excelErrorCode(text(cell.getValue()))
          .ifPresent(
              code ->
                  errors.add(
                      message(
                          CELL_ERROR,
                          cell.getKey(),
                          "This cell holds an error (%s). Correct it in Excel and upload again."
                              .formatted(code))));
    }

    // 2. Catalogue fields: mapped cell, else the batch default, else blank.
    var mapped = new LinkedHashMap<String, Object>();
    for (FieldSpec field : source.fields()) {
      String header = source.targets().get(field.key());
      String value = header == null ? null : text(raw.get(header));
      if (value != null && TakeOnValueParser.excelErrorCode(value).isPresent()) {
        continue; // already an error
      }
      if (value == null || value.isBlank()) {
        value = source.defaults().get(field.key());
      }
      if (value == null || value.isBlank()) {
        if (field.required()) {
          errors.add(message(REQUIRED, field.key(), requiredMessage(field, xlsx)));
        }
        continue;
      }
      Parsed<?> parsed = parse(field, value, source.options());
      if (!parsed.ok()) {
        errors.add(message(parsed.errorCode(), field.key(), parsed.message()));
      } else if (parsed.value() != null) {
        mapped.put(field.key(), normalise(parsed.value()));
      }
    }

    // 3. Custom fields, typed to the definition and checked by the existing validator.
    var custom = new LinkedHashMap<String, Object>();
    for (var entry : source.customFields().entrySet()) {
      String value = text(raw.get(entry.getValue()));
      var definition = context.customFieldDefinitions().get(entry.getKey());
      if (value == null || value.isBlank() || definition == null) {
        continue;
      }
      if (TakeOnValueParser.excelErrorCode(value).isPresent()) {
        continue;
      }
      Parsed<Object> converted = convertCustom(definition, value, source.options());
      if (!converted.ok()) {
        errors.add(message(CUSTOM_FIELD_INVALID, entry.getKey(), converted.message()));
        continue;
      }
      String refusal = customFieldValidator.checkValue(definition, converted.value());
      if (refusal != null) {
        errors.add(
            message(
                CUSTOM_FIELD_INVALID,
                entry.getKey(),
                "%s: %s".formatted(definition.getName(), refusal)));
      } else {
        custom.put(entry.getKey(), converted.value());
      }
    }
    if (!custom.isEmpty()) {
      mapped.put(CUSTOM_FIELDS_KEY, custom);
    }

    // 4. Parked columns: kept as a note on the record (customFields.takeon_parked at commit).
    var parked = new LinkedHashMap<String, Object>();
    for (String header : source.parked()) {
      String value = text(raw.get(header));
      if (value != null && !value.isBlank()) {
        parked.put(header, value);
      }
    }
    if (!parked.isEmpty()) {
      mapped.put(PARKED_KEY, parked);
    }

    // 5. Source ref and the lookup-free dedup key (C-25).
    Object ref = mapped.get(TakeOnFieldCatalogue.SOURCE_REF);
    String sourceRef = ref instanceof String s && !s.isBlank() ? s : "row:" + row.getRowNumber();
    String dedupField = dedupField(row.getDataset());
    String dedupKey = null;
    if (dedupField != null && mapped.get(dedupField) instanceof String keyValue) {
      dedupKey = dedupPrefix(row.getDataset()) + keyValue.toLowerCase(Locale.ROOT);
    }

    row.applyValidation(mapped, sourceRef, dedupKey, errors, List.of(), false, null);
    if (dedupKey != null) {
      context.recordDedupKey(dedupKey, row, dedupField);
    }
  }

  @Override
  public void validateBatchLevel(TakeOnBatch batch, ValidationContext context) {
    for (var duplicate : context.inFileDuplicates()) {
      duplicate
          .row()
          .addError(
              DUPLICATE_IN_FILE,
              duplicate.field(),
              "Duplicate of row %d in this file".formatted(duplicate.firstRowNumber()));
    }
  }

  @Override
  public Map<String, Object> summary(ValidationContext context) {
    return Map.of();
  }

  // --- helpers ---

  static String requiredMessage(FieldSpec field, boolean xlsx) {
    return xlsx
        ? "%s is required. If this cell holds a formula, open the file in Excel, save it, and upload it again."
            .formatted(field.label())
        : "%s is required.".formatted(field.label());
  }

  private static Parsed<?> parse(FieldSpec field, String value, TakeOnParseOptions options) {
    return switch (field.type()) {
      case TEXT ->
          TakeOnValueParser.text(
              value, field.maxLength() == null ? Integer.MAX_VALUE : field.maxLength());
      case EMAIL -> email(value, field.maxLength());
      case DATE -> TakeOnValueParser.date(value, options.dateFormat());
      case AMOUNT -> TakeOnValueParser.amount(value, options.decimalSeparator());
      case DECIMAL -> decimal(value, options.decimalSeparator());
      case ENUM -> TakeOnValueParser.enumValue(value, field.values(), field.aliases());
      case BOOLEAN -> TakeOnValueParser.bool(value);
    };
  }

  private static Parsed<String> email(String value, Integer maxLength) {
    var parsed = TakeOnValueParser.email(value);
    if (parsed.ok()
        && parsed.value() != null
        && maxLength != null
        && parsed.value().length() > maxLength) {
      return Parsed.error(
          TakeOnValueParser.TEXT_TOO_LONG,
          "This value is longer than %d characters.".formatted(maxLength));
    }
    return parsed;
  }

  /** A plain decimal (e.g. an interest rate): no grouping, an optional trailing {@code %}. */
  static Parsed<BigDecimal> decimal(String raw, TakeOnParseOptions.DecimalSeparator separator) {
    if (raw == null || raw.isBlank()) {
      return Parsed.of(null);
    }
    if (raw.length() > MAX_DECIMAL_LENGTH) {
      return Parsed.error(DECIMAL_INVALID, "This value is too long to be a number.");
    }
    String s = raw.replace(' ', ' ').strip();
    if (s.endsWith("%")) {
      s = s.substring(0, s.length() - 1);
    }
    s = s.replace(" ", "");
    if (separator == TakeOnParseOptions.DecimalSeparator.COMMA) {
      if (s.indexOf('.') >= 0) {
        return Parsed.error(DECIMAL_INVALID, "\"%s\" is not a number.".formatted(raw.strip()));
      }
      s = s.replace(',', '.');
    }
    if (!DECIMAL.matcher(s).matches()) {
      return Parsed.error(DECIMAL_INVALID, "\"%s\" is not a number.".formatted(raw.strip()));
    }
    return Parsed.of(new BigDecimal(s));
  }

  /** A cell typed to the custom field's own type (as {@code CustomFieldValidator} expects it). */
  private static Parsed<Object> convertCustom(
      FieldDefinition definition, String value, TakeOnParseOptions options) {
    Parsed<?> parsed =
        switch (definition.getFieldType()) {
          case NUMBER -> decimal(value, options.decimalSeparator());
          case DATE -> TakeOnValueParser.date(value, options.dateFormat());
          case BOOLEAN -> TakeOnValueParser.bool(value);
          case CURRENCY -> {
            var amount = TakeOnValueParser.amount(value, options.decimalSeparator());
            yield amount.ok()
                ? Parsed.of(Map.of("amount", amount.value(), "currency", "ZAR"))
                : amount;
          }
          case TEXT, DROPDOWN, URL, EMAIL, PHONE -> Parsed.of(value.strip());
        };
    if (!parsed.ok()) {
      return Parsed.error(parsed.errorCode(), parsed.message());
    }
    Object v = parsed.value() instanceof LocalDate d ? d.toString() : parsed.value();
    return Parsed.of(v);
  }

  private static Object normalise(Object value) {
    return switch (value) {
      case LocalDate d -> d.toString();
      case BigDecimal b -> b.toPlainString();
      default -> value;
    };
  }

  private static String dedupField(TakeOnDataset dataset) {
    return switch (dataset) {
      case CLIENTS -> "email";
      case MATTERS -> "referenceNumber";
      case DEBTOR_BALANCES -> "feeNoteNumber";
      case TRUST_POSITION -> null; // needs resolved client/matter/account ids — 657B
    };
  }

  private static String dedupPrefix(TakeOnDataset dataset) {
    return switch (dataset) {
      case CLIENTS -> "email:";
      case MATTERS -> "ref:";
      case DEBTOR_BALANCES -> "number:";
      case TRUST_POSITION -> "";
    };
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  private static Map<String, Object> message(String code, String field, String message) {
    var m = new LinkedHashMap<String, Object>();
    m.put("code", code);
    m.put("field", field);
    m.put("message", message);
    return m;
  }
}
