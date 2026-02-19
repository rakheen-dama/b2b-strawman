package io.b2mash.b2b.b2bstrawman.retention;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RetentionCheckResult {

  private final Instant checkedAt;
  private final Map<String, FlaggedRecords> flagged;

  public RetentionCheckResult() {
    this.checkedAt = Instant.now();
    this.flagged = new LinkedHashMap<>();
  }

  public void addFlagged(String recordType, String action, List<UUID> ids) {
    flagged.put(recordType, new FlaggedRecords(ids.size(), action, List.copyOf(ids)));
  }

  public Instant getCheckedAt() {
    return checkedAt;
  }

  public Map<String, FlaggedRecords> getFlagged() {
    return Collections.unmodifiableMap(flagged);
  }

  public int getTotalFlagged() {
    return flagged.values().stream().mapToInt(FlaggedRecords::count).sum();
  }

  public record FlaggedRecords(int count, String action, List<UUID> recordIds) {}
}
