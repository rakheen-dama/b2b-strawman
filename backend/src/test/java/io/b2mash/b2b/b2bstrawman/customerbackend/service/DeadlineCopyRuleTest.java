package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.DeadlinePortalSyncService.DeadlineSource;
import io.b2mash.b2b.b2bstrawman.event.CourtDateCancelledEvent;
import io.b2mash.b2b.b2bstrawman.event.CourtDateScheduledEvent;
import io.b2mash.b2b.b2bstrawman.event.PrescriptionDeadlineClearedEvent;
import io.b2mash.b2b.b2bstrawman.event.PrescriptionDeadlineSetEvent;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTracker;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The sanitised-copy rule by construction (Phase 89 633B, ADR-363, B10): no firm-only court-date or
 * prescription field (judge / magistrate, outcome, description, notes, interruption data,
 * cause-of-action date) can reach the portal deadline row's {@code label}, {@code descriptionRaw}
 * or {@code sourceReference}. Pinned three ways — the event records' components, the builders'
 * signatures, and the bytecode of the portal layer — plus the expected copy. Plain JUnit, no Spring
 * context.
 */
class DeadlineCopyRuleTest {

  private static final List<String> BANNED_COMPONENTS =
      List.of(
          "judgeMagistrate",
          "judge",
          "outcome",
          "description",
          "notes",
          "interruptionReason",
          "interruptionDate",
          "causeOfActionDate");

  private static final UUID ID = UUID.randomUUID();
  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final UUID MATTER = UUID.randomUUID();
  private static final LocalDate DATE = LocalDate.of(2030, 3, 1);

  @Test
  void theFourEventRecordsCarryNoFirmOnlyComponent() {
    for (Class<?> cls :
        List.of(
            CourtDateScheduledEvent.class,
            CourtDateCancelledEvent.class,
            PrescriptionDeadlineSetEvent.class,
            PrescriptionDeadlineClearedEvent.class)) {
      List<String> names =
          Arrays.stream(cls.getRecordComponents()).map(RecordComponent::getName).toList();
      assertThat(names)
          .as(cls.getSimpleName())
          .isNotEmpty()
          .doesNotContainAnyElementsOf(BANNED_COMPONENTS);
    }
  }

  @Test
  void theBuildersTakeOnlyPortalSafeParameters() throws NoSuchMethodException {
    Method court =
        DeadlinePortalSyncService.class.getDeclaredMethod(
            "courtDateSource",
            UUID.class,
            UUID.class,
            UUID.class,
            String.class,
            LocalDate.class,
            String.class,
            String.class,
            String.class);
    Method prescription =
        DeadlinePortalSyncService.class.getDeclaredMethod(
            "prescriptionSource",
            UUID.class,
            UUID.class,
            UUID.class,
            String.class,
            LocalDate.class);

    assertThat(court.getReturnType()).isEqualTo(DeadlineSource.class);
    assertThat(prescription.getReturnType()).isEqualTo(DeadlineSource.class);
    assertThat(
            Arrays.stream(DeadlinePortalSyncService.class.getDeclaredMethods())
                .filter(
                    m ->
                        m.getName().equals("courtDateSource")
                            || m.getName().equals("prescriptionSource")))
        .as("exactly one overload of each builder")
        .hasSize(2);
  }

  @Test
  void thePortalLayerNeverReadsAFirmOnlyField() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.b2mash.b2b.b2bstrawman.customerbackend");

    noClasses()
        .should()
        .callMethod(CourtDate.class, "getJudgeMagistrate")
        .orShould()
        .callMethod(CourtDate.class, "getOutcome")
        .orShould()
        .callMethod(CourtDate.class, "getDescription")
        .orShould()
        .callMethod(PrescriptionTracker.class, "getNotes")
        .orShould()
        .callMethod(PrescriptionTracker.class, "getInterruptionReason")
        .orShould()
        .callMethod(PrescriptionTracker.class, "getInterruptionDate")
        .orShould()
        .callMethod(PrescriptionTracker.class, "getCauseOfActionDate")
        .because("the sanitised-copy rule (ADR-363, B10) keeps firm work product off the portal")
        .check(classes);
  }

  @Test
  void courtDateCopy() {
    DeadlineSource preTrial =
        DeadlinePortalSyncService.courtDateSource(
            ID, CUSTOMER, MATTER, "PRE_TRIAL", DATE, "Durban Magistrate", null, "SCHEDULED");
    assertThat(preTrial.label()).isEqualTo("Pre trial — Durban Magistrate");
    assertThat(preTrial.sourceReference()).isEqualTo("Pre trial — Durban Magistrate");
    assertThat(preTrial.overrideStatus()).isNull();
    assertThat(preTrial.descriptionRaw()).isNull();
    assertThat(preTrial.sourceEntity()).isEqualTo("COURT_DATE");
    assertThat(preTrial.deadlineType()).isEqualTo("COURT_DATE");
    assertThat(preTrial.dueDate()).isEqualTo(DATE);

    DeadlineSource heard =
        DeadlinePortalSyncService.courtDateSource(
            ID, CUSTOMER, MATTER, "HEARING", DATE, "Johannesburg High Court", "2026/1234", "HEARD");
    assertThat(heard.overrideStatus()).isEqualTo("COMPLETED");
    assertThat(heard.descriptionRaw()).isEqualTo("2026/1234");

    DeadlineSource blankCourt =
        DeadlinePortalSyncService.courtDateSource(
            ID, CUSTOMER, MATTER, "HEARING", DATE, "  ", null, "SCHEDULED");
    assertThat(blankCourt.label()).isEqualTo("Hearing");

    DeadlineSource longCourt =
        DeadlinePortalSyncService.courtDateSource(
            ID, CUSTOMER, MATTER, "HEARING", DATE, "x".repeat(200), null, "SCHEDULED");
    assertThat(longCourt.label()).hasSize(160).endsWith("…");

    assertThat(DeadlinePortalSyncService.humanise("CASE_MANAGEMENT")).isEqualTo("Case management");
    assertThat(DeadlinePortalSyncService.humanise("PRE_TRIAL")).isEqualTo("Pre trial");
    assertThat(DeadlinePortalSyncService.humanise(null)).isEmpty();
  }

  @Test
  void prescriptionCopy() {
    DeadlineSource mortgage =
        DeadlinePortalSyncService.prescriptionSource(ID, CUSTOMER, MATTER, "MORTGAGE_30Y", DATE);
    assertThat(mortgage.label()).isEqualTo("Prescription deadline");
    assertThat(mortgage.descriptionRaw()).isEqualTo("Mortgage 30 years");
    assertThat(mortgage.sourceEntity()).isEqualTo("PRESCRIPTION_TRACKER");
    assertThat(mortgage.deadlineType()).isEqualTo("PRESCRIPTION");
    assertThat(mortgage.overrideStatus()).isNull();

    assertThat(DeadlinePortalSyncService.humanisePrescriptionType("GENERAL_3Y"))
        .isEqualTo("General 3 years");
    assertThat(DeadlinePortalSyncService.humanisePrescriptionType("DEBT_6Y"))
        .isEqualTo("Debt 6 years");
    assertThat(DeadlinePortalSyncService.humanisePrescriptionType("CUSTOM")).isEqualTo("Custom");
    assertThat(DeadlinePortalSyncService.humanisePrescriptionType("SOMETHING_NEW"))
        .isEqualTo("Something new");
  }

  @Test
  void aBlankCourtReferenceFallsBackToHumanisedCopyNotTheRawEnum() {
    // D3: upsertFromDeadline passes "Court date" (not the raw "COURT_DATE") as the sanitiser
    // fallback type, so a court date without a reference never renders an enum on the portal.
    var deadlineRepo = mock(PortalDeadlineViewRepository.class);
    var service =
        new DeadlinePortalSyncService(
            deadlineRepo,
            mock(FieldDefinitionRepository.class),
            mock(CustomerProjectRepository.class),
            new PortalTrustDescriptionSanitiser(),
            mock(PlatformTransactionManager.class),
            Clock.fixed(Instant.parse("2030-01-15T08:00:00Z"), KaziTime.ZONE));

    service.upsertFromDeadline(
        DeadlinePortalSyncService.courtDateSource(
            ID, CUSTOMER, MATTER, "HEARING", DATE, "Johannesburg High Court", null, "SCHEDULED"));
    service.upsertFromDeadline(
        DeadlinePortalSyncService.prescriptionSource(ID, CUSTOMER, MATTER, null, DATE));

    var captor = ArgumentCaptor.forClass(PortalDeadlineView.class);
    verify(deadlineRepo, times(2)).upsert(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(PortalDeadlineView::descriptionSanitised)
        .containsExactly(
            "Court date — Hearing — Johannesburg High Court",
            "Prescription — Prescription deadline")
        .noneMatch(d -> d.contains("COURT_DATE") || d.contains("PRESCRIPTION"));
  }
}
