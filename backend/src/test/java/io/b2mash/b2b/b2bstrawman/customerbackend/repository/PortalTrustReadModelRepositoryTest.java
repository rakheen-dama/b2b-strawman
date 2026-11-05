package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTrustBalanceView;
import java.math.BigDecimal;
import java.time.Instant;
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
    trustRepo.upsertBalance(clientA, matter, new BigDecimal("1500.00"), Instant.now());

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
    trustRepo.upsertBalance(clientA, matter, new BigDecimal("10.00"), Instant.now());

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
    trustRepo.upsertBalance(clientA, matter, new BigDecimal("250.00"), Instant.now());

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
    trustRepo.upsertBalance(clientA, matter, new BigDecimal("99.00"), Instant.now());

    assertThat(trustRepo.findBalancesByCustomer(clientA))
        .singleElement()
        .extracting(PortalTrustBalanceView::matterName)
        .isNull();
  }
}
