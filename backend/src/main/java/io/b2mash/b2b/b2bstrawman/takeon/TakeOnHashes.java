package io.b2mash.b2b.b2bstrawman.takeon;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The validation hash (§17.3.2): SHA-256 over a canonical JSON of the batch inputs, each source's
 * file hash, sheet, header row, parse options and mapping, and every row's status and mapped
 * values. Every later attestation, sign-off and commit binds to it, so it must be deterministic:
 * map keys sorted, sources by section, rows by (section, row number), decimals as plain strings,
 * ids, dates and enums as strings. 64 lower-case hex characters ({@code validation_hash}).
 */
public final class TakeOnHashes {

  private static final JsonMapper CANONICAL =
      JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

  private TakeOnHashes() {}

  public static String of(
      TakeOnBatch batch, Collection<TakeOnSource> sources, Collection<TakeOnRow> rows) {
    var input = new HashMap<String, Object>();
    input.put("dataset", batch.getDataset().name());
    input.put("trustAccountId", str(batch.getTrustAccountId()));
    input.put("cutoverDate", str(batch.getCutoverDate()));

    List<Map<String, Object>> sourceList = new ArrayList<>();
    sources.stream()
        .sorted(Comparator.comparing(s -> s.getSection().name()))
        .forEach(
            s -> {
              var m = new HashMap<String, Object>();
              m.put("section", s.getSection().name());
              m.put("fileSha256", s.getFileSha256());
              m.put("sheetName", s.getSheetName());
              m.put("headerRow", s.getHeaderRow());
              m.put("parseOptions", canonical(s.getParseOptions()));
              m.put("columnMapping", canonical(s.getColumnMapping()));
              sourceList.add(m);
            });
    input.put("sources", sourceList);

    List<Map<String, Object>> rowList = new ArrayList<>();
    rows.stream()
        .sorted(
            Comparator.<TakeOnRow, String>comparing(r -> r.getSection().name())
                .thenComparingInt(TakeOnRow::getRowNumber))
        .forEach(
            r -> {
              var m = new HashMap<String, Object>();
              m.put("section", r.getSection().name());
              m.put("rowNumber", r.getRowNumber());
              m.put("status", r.getStatus().name());
              m.put("mappedValues", canonical(r.getMappedValues()));
              rowList.add(m);
            });
    input.put("rows", rowList);

    byte[] json = CANONICAL.writeValueAsString(input).getBytes(StandardCharsets.UTF_8);
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /** SHA-256 of a file's bytes, lower-case hex ({@code take_on_sources.file_sha256}). */
  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /** Values made representation-independent: decimals plain, numbers as decimals, ids as text. */
  private static Object canonical(Object value) {
    return switch (value) {
      case null -> null;
      case Map<?, ?> map -> {
        var copy = new LinkedHashMap<String, Object>();
        map.forEach((k, v) -> copy.put(String.valueOf(k), canonical(v)));
        yield copy;
      }
      case Collection<?> list -> list.stream().map(TakeOnHashes::canonical).toList();
      case BigDecimal b -> b.stripTrailingZeros().toPlainString();
      case Number n -> new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
      case UUID id -> id.toString();
      case Enum<?> e -> e.name();
      case TemporalAccessor t -> t.toString();
      default -> value;
    };
  }

  private static String str(Object value) {
    return value == null ? null : value.toString();
  }
}
