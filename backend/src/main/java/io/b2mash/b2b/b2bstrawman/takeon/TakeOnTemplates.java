package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnTemplateFile;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnFieldCatalogue;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnFieldCatalogue.FieldSpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CSV templates (§17.4.1): the section's field labels in catalogue order as the header row, then
 * one row of fictional data; UTF-8 with a BOM so Excel opens it cleanly. The preview's suggested
 * mapping matches these labels, so a filled-in template maps itself.
 */
public final class TakeOnTemplates {

  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  /** Fictional example values by field key (anything else falls back to its type's example). */
  private static final Map<String, String> EXAMPLES =
      Map.ofEntries(
          Map.entry("name", "Thandi Example"),
          Map.entry("email", "thandi.example@example.test"),
          Map.entry("clientEmail", "thandi.example@example.test"),
          Map.entry("leadMemberEmail", "attorney.example@example.test"),
          Map.entry("phone", "+27 11 555 0100"),
          Map.entry("idNumber", "0000000000000"),
          Map.entry("clientIdNumber", "0000000000000"),
          Map.entry("registrationNumber", "2020/000000/07"),
          Map.entry("taxNumber", "0000000000"),
          Map.entry("addressLine1", "1 Example Street"),
          Map.entry("addressLine2", "Example Park"),
          Map.entry("city", "Johannesburg"),
          Map.entry("postalCode", "2000"),
          Map.entry("country", "ZA"),
          Map.entry("cddRecordsLocation", "Previous system archive, box 12"),
          Map.entry("clientRef", "C-0001"),
          Map.entry("matterRef", "M-0001"),
          Map.entry("referenceNumber", "M-0001"),
          Map.entry("description", "Example description"),
          Map.entry("workType", "Litigation"),
          Map.entry("feeNoteNumber", "FN-0001"),
          Map.entry("currency", "ZAR"),
          Map.entry("bankReference", "DEP 12345"),
          Map.entry("investmentAccount", "Example Bank investment 0001"),
          Map.entry("institution", "Example Bank"),
          Map.entry("accountNumber", "0000000001"),
          Map.entry("instructionReference", "INSTR-0001"),
          Map.entry("reference", "CHQ 000123"),
          Map.entry(TakeOnFieldCatalogue.SOURCE_REF, "PREV-0001"));

  private TakeOnTemplates() {}

  public static TakeOnTemplateFile template(TakeOnDataset dataset, TakeOnSection section) {
    List<FieldSpec> fields = TakeOnFieldCatalogue.fields(dataset, section);
    String header = fields.stream().map(f -> csv(f.label())).collect(Collectors.joining(","));
    String example = fields.stream().map(f -> csv(example(f))).collect(Collectors.joining(","));
    byte[] body = (header + "\r\n" + example + "\r\n").getBytes(StandardCharsets.UTF_8);
    var out = new ByteArrayOutputStream(body.length + UTF8_BOM.length);
    out.writeBytes(UTF8_BOM);
    out.writeBytes(body);
    return new TakeOnTemplateFile(out.toByteArray(), fileName(dataset, section));
  }

  static String fileName(TakeOnDataset dataset, TakeOnSection section) {
    String name = "take-on-" + kebab(dataset.name());
    if (!dataset.singleSource()) {
      name += "-" + kebab(section.name());
    }
    return name + "-template.csv";
  }

  private static String example(FieldSpec field) {
    String known = EXAMPLES.get(field.key());
    if (known != null) {
      return known;
    }
    return switch (field.type()) {
      case TEXT -> "Example";
      case EMAIL -> "thandi.example@example.test";
      case DATE -> "31/10/2026";
      case AMOUNT -> "1234.56";
      case DECIMAL -> "7.25";
      case ENUM -> field.values().getFirst();
      case BOOLEAN -> "Yes";
    };
  }

  private static String kebab(String enumName) {
    return enumName.toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static String csv(String value) {
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
