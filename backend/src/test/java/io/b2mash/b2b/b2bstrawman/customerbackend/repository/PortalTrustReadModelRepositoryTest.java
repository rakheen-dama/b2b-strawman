package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTrustBalanceView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * G88-F31 DTO half (Phase 89 633B, §14.2.6): the portal trust balance reads carry the client's
 * portal matter name. {@code portal.portal_projects} is keyed on {@code (id, customer_id)}, so the
 * join must match the customer too (D6) — otherwise a multi-client matter duplicates every balance
 * row and one client could see another client's project row. Portal schema only, random ids.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalTrustReadModelRepositoryTest {

  private static final String ORG_ID = "org_portal_trust_repo_test";

  @Autowired private PortalTrustReadModelRepository trustRepo;
  @Autowired private PortalReadModelRepository readModelRepo;

  @Test
  void summaryCarriesTheClientsMatterName() {
    UUID clientA = UUID.randomUUID();
    UUID matter = UUID.randomUUID();
    readModelRepo.upsertPortalProject(
        matter, clientA, ORG_ID, "Ndlovu v RAF", "ACTIVE", null, Instant.now());
    trustRepo.upsertBalance(
        clientA, matter, new BigDecimal("1500.00"), BigDecimal.ZERO, Instant.now());

    assertThat(trustRepo.findBalancesByCustomer(clientA))
        .singleElement()
        .satisfies(
            b -> {
              assertThat(b.matterId()).isEqualTo(matter);
              assertThat(b.matterName()).isEqualTo("Ndlovu v RAF");
              assertThat(b.currentBalance()).isEqualByComparingTo("1500.00");
            });
    assertThat(trustRepo.findBalance(clientA, matter))
        .get()
        .extracting(PortalTrustBalanceView::matterName)
        .isEqualTo("Ndlovu v RAF");
  }

  @Test
  void noPortalProjectRowMeansNullName() {
    UUID clientA = UUID.randomUUID();
    UUID matter = UUID.randomUUID();
    trustRepo.upsertBalance(
        clientA, matter, new BigDecimal("10.00"), BigDecimal.ZERO, Instant.now());

    assertThat(trustRepo.findBalancesByCustomer(clientA))
        .singleElement()
        .extracting(PortalTrustBalanceView::matterName)
        .isNull();
    assertThat(trustRepo.findBalance(clientA, matter))
        .get()
        .extracting(PortalTrustBalanceView::matterName)
        .isNull();
  }

  @Test
  void aMultiClientMatterDoesNotDuplicateTheBalance() {
    UUID clientA = UUID.randomUUID();
    UUID clientB = UUID.randomUUID();
    UUID matter = UUID.randomUUID();
    readModelRepo.upsertPortalProject(
        matter, clientA, ORG_ID, "Mokoena Estate", "ACTIVE", null, Instant.now());
    readModelRepo.upsertPortalProject(
        matter, clientB, ORG_ID, "Mokoena Estate", "ACTIVE", null, Instant.now());
    trustRepo.upsertBalance(
        clientA, matter, new BigDecimal("250.00"), BigDecimal.ZERO, Instant.now());

    assertThat(trustRepo.findBalancesByCustomer(clientA))
        .singleElement()
        .extracting(PortalTrustBalanceView::matterName)
        .isEqualTo("Mokoena Estate");
    assertThat(trustRepo.findBalancesByCustomer(clientB)).isEmpty();
  }

  @Test
  void nameIsNotTakenFromAnotherClientsProjectRow() {
    UUID clientA = UUID.randomUUID();
    UUID clientB = UUID.randomUUID();
    UUID matter = UUID.randomUUID();
    readModelRepo.upsertPortalProject(
        matter, clientB, ORG_ID, "Only B's Matter Row", "ACTIVE", null, Instant.now());
    trustRepo.upsertBalance(
        clientA, matter, new BigDecimal("99.00"), BigDecimal.ZERO, Instant.now());

    assertThat(trustRepo.findBalancesByCustomer(clientA))
        .singleElement()
        .extracting(PortalTrustBalanceView::matterName)
        .isNull();
  }

  // Phase 92 648D (§16.6.3): the invested balance and each row's ledger round-trip through every
  // read the portal uses, and an upsert rewrites both.
  @Test
  void investedBalanceAndLedgerRoundTripThroughEveryRead() {
    UUID client = UUID.randomUUID();
    UUID matter = UUID.randomUUID();
    trustRepo.upsertBalance(
        client, matter, new BigDecimal("2500.00"), new BigDecimal("7595.00"), Instant.now());
    UUID interest = UUID.randomUUID();
    upsertRow(interest, client, matter, "INV_INTEREST", "CLIENT_INVESTMENT", "95.00", "7595.00");
    UUID deposit = UUID.randomUUID();
    upsertRow(deposit, client, matter, "DEPOSIT", "CLIENT", "10000.00", "10000.00");

    assertThat(trustRepo.findBalancesByCustomer(client))
        .singleElement()
        .satisfies(
            b -> {
              assertThat(b.currentBalance()).isEqualByComparingTo("2500.00");
              assertThat(b.investedBalance()).isEqualByComparingTo("7595.00");
            });
    assertThat(trustRepo.findBalance(client, matter).orElseThrow().investedBalance())
        .isEqualByComparingTo("7595.00");
    assertThat(trustRepo.findTransactions(client, matter, null, null, 50, 0))
        .extracting(t -> t.id() + "|" + t.transactionType() + "|" + t.ledger())
        .containsExactlyInAnyOrder(
            interest + "|INV_INTEREST|CLIENT_INVESTMENT", deposit + "|DEPOSIT|CLIENT");
    assertThat(trustRepo.findRecentTransactionsByCustomer(client, 10))
        .extracting(t -> t.id() + "|" + t.ledger())
        .containsExactlyInAnyOrder(interest + "|CLIENT_INVESTMENT", deposit + "|CLIENT");

    // A later replay rewrites both on conflict
    trustRepo.upsertBalance(
        client, matter, new BigDecimal("10095.00"), BigDecimal.ZERO, Instant.now());
    upsertRow(deposit, client, matter, "DEPOSIT", "CLIENT_INVESTMENT", "10000.00", "10000.00");
    assertThat(trustRepo.findBalance(client, matter).orElseThrow().investedBalance())
        .isEqualByComparingTo("0");
    assertThat(trustRepo.findTransactions(client, matter, null, null, 50, 0))
        .filteredOn(t -> t.id().equals(deposit))
        .singleElement()
        .satisfies(t -> assertThat(t.ledger()).isEqualTo("CLIENT_INVESTMENT"));
  }

  private void upsertRow(
      UUID id,
      UUID client,
      UUID matter,
      String type,
      String ledger,
      String amount,
      String runningBalance) {
    trustRepo.upsertTransaction(
        id,
        client,
        matter,
        type,
        ledger,
        new BigDecimal(amount),
        new BigDecimal(runningBalance),
        LocalDate.of(2026, 11, 8),
        Instant.now(),
        Instant.now(),
        type + " row",
        "REF-" + type);
  }
}
