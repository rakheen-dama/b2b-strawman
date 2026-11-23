package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Clients take-on files for integration tests (Phase 93 655A): one header, one mapping, and a row
 * builder, so each test states only the values it is about. Fictional data only.
 */
public final class TakeOnClientsFiles {

  public static final String HEADER =
      "Client name,Email,Client type,CDD completed date,Beneficial ownership,Screening,"
          + "Screening date,Records,Rating,Tax number,ID number,Address,City,Country,Previous ID,"
          + "Branch\n";

  private static final Map<String, String> TARGETS =
      Map.ofEntries(
          Map.entry("name", "Client name"),
          Map.entry("email", "Email"),
          Map.entry("ficaClientType", "Client type"),
          Map.entry("cddCompletedDate", "CDD completed date"),
          Map.entry("beneficialOwnershipStatus", "Beneficial ownership"),
          Map.entry("screeningStatus", "Screening"),
          Map.entry("screeningDate", "Screening date"),
          Map.entry("cddRecordsLocation", "Records"),
          Map.entry("riskRating", "Rating"),
          Map.entry("taxNumber", "Tax number"),
          Map.entry("idNumber", "ID number"),
          Map.entry("addressLine1", "Address"),
          Map.entry("city", "City"),
          Map.entry("country", "Country"),
          Map.entry("sourceRef", "Previous ID"));

  private TakeOnClientsFiles() {}

  /** The mapping every clients test uses: all columns mapped, "Branch" parked. */
  public static Map<String, Object> mapping() {
    return mapping(Map.of());
  }

  /** The mapping with batch defaults (field key → value for blank cells). */
  public static Map<String, Object> mapping(Map<String, String> defaults) {
    var body = new HashMap<String, Object>();
    body.put("targets", TARGETS);
    body.put("parked", List.of("Branch"));
    if (!defaults.isEmpty()) {
      body.put("defaults", defaults);
    }
    return body;
  }

  /** One client row, with fictional defaults for every column not given. */
  public static Row row(String name, String email) {
    return new Row(name, email);
  }

  /** A row under construction; {@link #toString()} renders it as one CSV line. */
  public static final class Row {
    private final String name;
    private final String email;
    private String type = "Individual";
    private String cddDate = "01/02/2024";
    private String bo = "Not applicable";
    private String screening = "Screened clear";
    private String screeningDate = "01/02/2024";
    private String records = "Archive box 1";
    private String rating = "Low";
    private String tax = "";
    private String idNumber = "";
    private String address = "1 Main Road";
    private String city = "Johannesburg";
    private String country = "ZA";
    private String ref = "";
    private String branch = "";

    private Row(String name, String email) {
      this.name = name;
      this.email = email;
    }

    public Row type(String v) {
      this.type = v;
      return this;
    }

    public Row screening(String status, String date) {
      this.screening = status;
      this.screeningDate = date;
      return this;
    }

    public Row records(String v) {
      this.records = v;
      return this;
    }

    public Row rating(String v) {
      this.rating = v;
      return this;
    }

    public Row tax(String v) {
      this.tax = v;
      return this;
    }

    public Row idNumber(String v) {
      this.idNumber = v;
      return this;
    }

    public Row address(String line1, String city, String country) {
      this.address = line1;
      this.city = city;
      this.country = country;
      return this;
    }

    public Row ref(String v) {
      this.ref = v;
      return this;
    }

    public Row branch(String v) {
      this.branch = v;
      return this;
    }

    public Row cddDate(String v) {
      this.cddDate = v;
      return this;
    }

    @Override
    public String toString() {
      return Stream.of(
                  Objects.toString(name, ""),
                  Objects.toString(email, ""),
                  type,
                  cddDate,
                  bo,
                  screening,
                  screeningDate,
                  records,
                  rating,
                  tax,
                  idNumber,
                  address,
                  city,
                  country,
                  ref,
                  branch)
              .map(TakeOnClientsFiles::field)
              .collect(Collectors.joining(","))
          + "\n";
    }
  }

  /**
   * One CSV field: quoted when it holds a comma, a double quote or a line break, with embedded
   * quotes doubled (RFC 4180); an ordinary field is written as is.
   */
  static String field(String value) {
    return value.contains(",")
            || value.contains("\"")
            || value.contains("\n")
            || value.contains("\r")
        ? "\"" + value.replace("\"", "\"\"") + "\""
        : value;
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
