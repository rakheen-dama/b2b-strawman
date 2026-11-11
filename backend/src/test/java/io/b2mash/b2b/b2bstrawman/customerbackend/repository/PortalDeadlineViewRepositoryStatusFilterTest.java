package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineStatus;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * G89-F71: the {@code status} filter of {@link PortalDeadlineViewRepository#findByCustomer} matches
 * the status <b>effective</b> on {@code today}, at its boundaries, not the value stored at sync
 * time. Every non-terminal row is stored with a deliberately stale status. The filter result for
 * each status must also equal what {@link PortalDeadlineStatus#effective} says in Java, so the SQL
 * and Java halves of the rule cannot drift. Portal schema only, random ids, fixed {@code today}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalDeadlineViewRepositoryStatusFilterTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 3, 16);

  /** A seeded row: its due date, the (stale) status stored on it, and its effective status. */
  private record Seed(LocalDate dueDate, String stored, String effective) {}

  @Autowired private PortalDeadlineViewRepository repo;

  private final UUID customerId = UUID.randomUUID();

  private final Map<UUID, Seed> seeds =
      Map.of(
          UUID.randomUUID(), new Seed(TODAY.minusDays(1), "UPCOMING", "OVERDUE"),
          UUID.randomUUID(), new Seed(TODAY, "UPCOMING", "DUE_SOON"),
          UUID.randomUUID(), new Seed(TODAY.plusDays(7), "UPCOMING", "DUE_SOON"),
          UUID.randomUUID(), new Seed(TODAY.plusDays(8), "DUE_SOON", "UPCOMING"),
          UUID.randomUUID(), new Seed(TODAY.plusDays(3), "COMPLETED", "COMPLETED"),
          UUID.randomUUID(), new Seed(TODAY.minusDays(5), "COMPLETED", "COMPLETED"),
          UUID.randomUUID(), new Seed(TODAY.plusDays(30), "CANCELLED", "CANCELLED"));

  @BeforeAll
  void seed() {
    seeds.forEach(
        (id, s) ->
            repo.upsert(
                new PortalDeadlineView(
                    id,
                    "COURT_DATE",
                    customerId,
                    UUID.randomUUID(),
                    "COURT_DATE",
                    "Hearing " + s.dueDate(),
                    s.dueDate(),
                    s.stored(),
                    null,
                    null)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"UPCOMING", "DUE_SOON", "OVERDUE", "COMPLETED", "CANCELLED"})
  void statusFilterMatchesTheEffectiveStatusOnToday(String filter) {
    List<UUID> expectedIds =
        seeds.entrySet().stream()
            .filter(e -> e.getValue().effective().equals(filter))
            .map(Map.Entry::getKey)
            .toList();

    List<PortalDeadlineView> rows = repo.findByCustomer(customerId, null, null, filter, TODAY);

    assertThat(rows)
        .extracting(PortalDeadlineView::id)
        .as("SQL filter %s on %s", filter, TODAY)
        .containsExactlyInAnyOrderElementsOf(expectedIds);
    // Same rule in Java: every row the SQL returned reads `filter` through effective().
    assertThat(rows)
        .allSatisfy(
            r ->
                assertThat(PortalDeadlineStatus.effective(r.status(), r.dueDate(), TODAY))
                    .as("Java effective status of row due %s", r.dueDate())
                    .isEqualTo(filter));
  }

  @Test
  void noFilterReturnsEveryRowWithItsStoredStatus() {
    // The repository returns the raw read-model row; readers map the status
    // (PortalDeadlineService).
    assertThat(repo.findByCustomer(customerId, null, null, null, TODAY))
        .hasSize(seeds.size())
        .allSatisfy(r -> assertThat(r.status()).isEqualTo(seeds.get(r.id()).stored()));
  }
}
