package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Matters take-on files for integration tests (Phase 93 656A): one header, one mapping, and a row
 * builder, so each test states only the values it is about. Fictional data only.
 */
public final class TakeOnMattersFiles {

  public static final String HEADER =
      "Matter name,Client email,Client ID,Client ID number,Matter number,Description,Due date,"
          + "Work type,Priority,Attorney email,Previous ID,File room\n";

  private static final Map<String, String> TARGETS =
      Map.ofEntries(
          Map.entry("name", "Matter name"),
          Map.entry("clientEmail", "Client email"),
          Map.entry("clientRef", "Client ID"),
          Map.entry("clientIdNumber", "Client ID number"),
          Map.entry("referenceNumber", "Matter number"),
          Map.entry("description", "Description"),
          Map.entry("dueDate", "Due date"),
          Map.entry("workType", "Work type"),
          Map.entry("priority", "Priority"),
          Map.entry("leadMemberEmail", "Attorney email"),
          Map.entry("sourceRef", "Previous ID"));

  private TakeOnMattersFiles() {}

  /** The mapping every matters test uses: all columns mapped, "File room" parked. */
  public static Map<String, Object> mapping() {
    var body = new HashMap<String, Object>();
    body.put("targets", TARGETS);
    body.put("parked", List.of("File room"));
    return body;
  }

  /** One matter row named {@code name}; give its client with one of the client setters. */
  public static Row row(String name) {
    return new Row(name);
  }

  /** A row under construction; {@link #toString()} renders it as one CSV line. */
  public static final class Row {
    private final String name;
    private String clientEmail = "";
    private String clientRef = "";
    private String clientIdNumber = "";
    private String number = "";
    private String description = "";
    private String dueDate = "";
    private String workType = "";
    private String priority = "";
    private String attorney = "";
    private String ref = "";
    private String fileRoom = "";

    private Row(String name) {
      this.name = name;
    }

    public Row clientEmail(String v) {
      this.clientEmail = v;
      return this;
    }

    public Row clientRef(String v) {
      this.clientRef = v;
      return this;
    }

    public Row clientIdNumber(String v) {
      this.clientIdNumber = v;
      return this;
    }

    /** The matter reference ("Matter number"). */
    public Row number(String v) {
      this.number = v;
      return this;
    }

    public Row description(String v) {
      this.description = v;
      return this;
    }

    public Row dueDate(String v) {
      this.dueDate = v;
      return this;
    }

    public Row workType(String v) {
      this.workType = v;
      return this;
    }

    public Row priority(String v) {
      this.priority = v;
      return this;
    }

    /** The responsible attorney's email. */
    public Row attorney(String v) {
      this.attorney = v;
      return this;
    }

    /** The matter's own previous-system ID. */
    public Row ref(String v) {
      this.ref = v;
      return this;
    }

    public Row fileRoom(String v) {
      this.fileRoom = v;
      return this;
    }

    @Override
    public String toString() {
      return Stream.of(
                  Objects.toString(name, ""),
                  clientEmail,
                  clientRef,
                  clientIdNumber,
                  number,
                  description,
                  dueDate,
                  workType,
                  priority,
                  attorney,
                  ref,
                  fileRoom)
              .map(TakeOnClientsFiles::field)
              .collect(Collectors.joining(","))
          + "\n";
    }
  }

  /** The file: the header and the rows. */
  public static String csv(Row... rows) {
    var sb = new StringBuilder(HEADER);
    for (Row r : rows) {
      sb.append(r);
    }
    return sb.toString();
  }
}
