package io.b2mash.b2b.b2bstrawman.informationrequest;

import static io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestService.FICA_ONBOARDING_PACK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.compliance.BeneficialOwnershipStatus;
import io.b2mash.b2b.b2bstrawman.compliance.ClientFicaAttestation;
import io.b2mash.b2b.b2bstrawman.compliance.ClientFicaAttestationService;
import io.b2mash.b2b.b2bstrawman.compliance.FicaClientType;
import io.b2mash.b2b.b2bstrawman.compliance.FicaRiskRating;
import io.b2mash.b2b.b2bstrawman.compliance.FicaScreeningStatus;
import io.b2mash.b2b.b2bstrawman.compliance.SourceOfFundsStatus;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * The FICA status rule extracted from {@code InformationRequestService#getFicaStatus} (Phase 87,
 * Epic 614A; checklist-first since Phase 88, ADR-348). Part 1 pins the information-request rule
 * (the fallback, reached with no onboarding checklist instances); part 2 pins the checklist-first
 * branch; part 3 proves the batched {@code getFicaStatuses} and the single-client {@code
 * getFicaStatus} return identical results for the same fixtures (the single read delegates to the
 * batch today — this guards against a future split); part 4 pins the attestation branch (Phase 93
 * 655B, §17.3.4, ADR-388).
 */
class FicaStatusProjectorTest {

  private static final Instant T0 = Instant.parse("2026-09-01T08:00:00Z");

  /** The projector's {@code today} — a constant, never {@code LocalDate.now()} (P-15). */
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

  private final UUID ficaTemplate = UUID.randomUUID();
  private final UUID inactiveFicaTemplate = UUID.randomUUID();
  private final UUID otherTemplate = UUID.randomUUID();
  private final Set<UUID> ficaTemplates = Set.of(ficaTemplate, inactiveFicaTemplate);
  private final UUID client = UUID.randomUUID();

  /** Items by request id; the projector's lookup reads from here. */
  private final List<RequestItem> items = new ArrayList<>();

  private final Function<UUID, List<RequestItem>> lookup =
      id -> items.stream().filter(i -> i.getRequestId().equals(id)).toList();

  @Nested
  class Rule {

    @Test
    void noRequestsIsNotStarted() {
      assertProjection(
          FicaStatusProjector.project(List.of(), ficaTemplates, List.of(), lookup, null, TODAY),
          "NOT_STARTED",
          null);
    }

    @Test
    void noFicaTemplatesIsNotStarted() {
      var r = request(client, ficaTemplate, RequestStatus.DRAFT, T0, null);
      assertProjection(
          FicaStatusProjector.project(List.of(), Set.of(), List.of(r), lookup, null, TODAY),
          "NOT_STARTED",
          null);
    }

    @Test
    void nonFicaOrTemplatelessRequestsAreNotStarted() {
      var other = request(client, otherTemplate, RequestStatus.DRAFT, T0, null);
      var adHoc = request(client, null, RequestStatus.SENT, T0, null);
      assertProjection(
          FicaStatusProjector.project(
              List.of(), ficaTemplates, List.of(other, adHoc), lookup, null, TODAY),
          "NOT_STARTED",
          null);
    }

    @Test
    void onlyCancelledFicaRequestsAreNotStarted() {
      var cancelled = request(client, ficaTemplate, RequestStatus.CANCELLED, T0, null);
      assertProjection(
          FicaStatusProjector.project(
              List.of(), ficaTemplates, List.of(cancelled), lookup, null, TODAY),
          "NOT_STARTED",
          null);
    }

    @Test
    void liveRequestsAreInProgressWithTheNewestId() {
      var draft = request(client, ficaTemplate, RequestStatus.DRAFT, T0, null);
      var sent = request(client, ficaTemplate, RequestStatus.SENT, T0.plusSeconds(60), null);
      var inProgress =
          request(client, ficaTemplate, RequestStatus.IN_PROGRESS, T0.plusSeconds(30), null);
      assertProjection(
          FicaStatusProjector.project(
              List.of(), ficaTemplates, List.of(draft, sent, inProgress), lookup, null, TODAY),
          "IN_PROGRESS",
          sent.getId());
    }

    @Test
    void completedWithEveryItemAcceptedIsDone() {
      Instant completedAt = T0.plus(3, ChronoUnit.DAYS);
      var r = request(client, ficaTemplate, RequestStatus.COMPLETED, T0, completedAt);
      item(r, ItemStatus.ACCEPTED);
      item(r, ItemStatus.ACCEPTED);
      var p =
          FicaStatusProjector.project(List.of(), ficaTemplates, List.of(r), lookup, null, TODAY);
      assertThat(p.status()).isEqualTo("DONE");
      assertThat(p.requestId()).isEqualTo(r.getId());
      assertThat(p.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void completedWithAnItemNotAcceptedIsInProgress() {
      var submitted = request(client, ficaTemplate, RequestStatus.COMPLETED, T0, T0);
      item(submitted, ItemStatus.ACCEPTED);
      item(submitted, ItemStatus.SUBMITTED);
      assertProjection(
          FicaStatusProjector.project(
              List.of(), ficaTemplates, List.of(submitted), lookup, null, TODAY),
          "IN_PROGRESS",
          submitted.getId());

      var rejected = request(client, ficaTemplate, RequestStatus.COMPLETED, T0, T0);
      item(rejected, ItemStatus.REJECTED);
      assertProjection(
          FicaStatusProjector.project(
              List.of(), ficaTemplates, List.of(rejected), lookup, null, TODAY),
          "IN_PROGRESS",
          rejected.getId());
    }

    @Test
    void completedWithNoItemsIsInProgress() {
      var r = request(client, ficaTemplate, RequestStatus.COMPLETED, T0, T0);
      assertProjection(
          FicaStatusProjector.project(List.of(), ficaTemplates, List.of(r), lookup, null, TODAY),
          "IN_PROGRESS",
          r.getId());
    }

    @Test
    void olderCompletedAllAcceptedBeatsNewerLiveRequest() {
      var done = request(client, ficaTemplate, RequestStatus.COMPLETED, T0, T0.plusSeconds(5));
      item(done, ItemStatus.ACCEPTED);
      var newerLive = request(client, ficaTemplate, RequestStatus.SENT, T0.plusSeconds(3600), null);
      var p =
          FicaStatusProjector.project(
              List.of(), ficaTemplates, List.of(newerLive, done), lookup, null, TODAY);
      assertThat(p.status()).isEqualTo("DONE");
      assertThat(p.requestId()).isEqualTo(done.getId());
    }

    @Test
    void newestCompletedWinsEvenWhenAnOlderCompletedWasAllAccepted() {
      // Pins current behaviour: only the newest COMPLETED request is inspected.
      var olderDone = request(client, ficaTemplate, RequestStatus.COMPLETED, T0, T0);
      item(olderDone, ItemStatus.ACCEPTED);
      var newerIncomplete =
          request(client, ficaTemplate, RequestStatus.COMPLETED, T0.plusSeconds(3600), T0);
      item(newerIncomplete, ItemStatus.REJECTED);
      assertProjection(
          FicaStatusProjector.project(
              List.of(), ficaTemplates, List.of(olderDone, newerIncomplete), lookup, null, TODAY),
          "IN_PROGRESS",
          newerIncomplete.getId());
    }

    @Test
    void newerCancelledRequestIsIgnored() {
      var done = request(client, ficaTemplate, RequestStatus.COMPLETED, T0, T0.plusSeconds(5));
      item(done, ItemStatus.ACCEPTED);
      var cancelled =
          request(client, ficaTemplate, RequestStatus.CANCELLED, T0.plusSeconds(3600), null);
      var p =
          FicaStatusProjector.project(
              List.of(), ficaTemplates, List.of(cancelled, done), lookup, null, TODAY);
      assertThat(p.status()).isEqualTo("DONE");
      assertThat(p.requestId()).isEqualTo(done.getId());
    }

    @Test
    void inactiveFicaTemplatesStillCount() {
      var r = request(client, inactiveFicaTemplate, RequestStatus.SENT, T0, null);
      assertProjection(
          FicaStatusProjector.project(List.of(), ficaTemplates, List.of(r), lookup, null, TODAY),
          "IN_PROGRESS",
          r.getId());
    }

    @Test
    void equalCreatedAtIsBrokenDeterministicallyById() {
      var a = request(client, ficaTemplate, RequestStatus.SENT, T0, null);
      var b = request(client, ficaTemplate, RequestStatus.DRAFT, T0, null);
      UUID expected = a.getId().compareTo(b.getId()) < 0 ? a.getId() : b.getId();
      assertThat(
              FicaStatusProjector.project(
                      List.of(), ficaTemplates, List.of(a, b), lookup, null, TODAY)
                  .requestId())
          .isEqualTo(expected);
      assertThat(
              FicaStatusProjector.project(
                      List.of(), ficaTemplates, List.of(b, a), lookup, null, TODAY)
                  .requestId())
          .isEqualTo(expected);
    }
  }

  @Nested
  class ChecklistFirst {

    @Test
    void completedChecklistIsDoneWithItsCompletedAtAndId() {
      Instant completedAt = T0.plus(2, ChronoUnit.DAYS);
      var done = instance(client, "COMPLETED", T0, completedAt);
      // A live FICA request alone would read IN_PROGRESS — the checklist outranks it.
      var sent = request(client, ficaTemplate, RequestStatus.SENT, T0.plusSeconds(60), null);
      var p =
          FicaStatusProjector.project(
              List.of(done), ficaTemplates, List.of(sent), lookup, null, TODAY);
      assertThat(p.status()).isEqualTo("DONE");
      assertThat(p.completedAt()).isEqualTo(completedAt);
      assertThat(p.checklistInstanceId()).isEqualTo(done.getId());
      assertThat(p.requestId()).isNull();
    }

    @Test
    void openChecklistIsInProgressEvenWhenARequestIsDone() {
      var open = instance(client, "IN_PROGRESS", T0, null);
      var doneReq = request(client, ficaTemplate, RequestStatus.COMPLETED, T0, T0.plusSeconds(5));
      item(doneReq, ItemStatus.ACCEPTED);
      var p =
          FicaStatusProjector.project(
              List.of(open), ficaTemplates, List.of(doneReq), lookup, null, TODAY);
      // A2: the checklist branch outranks the information-request branch entirely.
      assertChecklistProjection(p, "IN_PROGRESS", open.getId());
    }

    @Test
    void inProgressOutranksAnOlderCompleted() {
      var completed = instance(client, "COMPLETED", T0, T0.plusSeconds(30));
      var newerOpen = instance(client, "IN_PROGRESS", T0.plusSeconds(3600), null);
      assertChecklistProjection(
          FicaStatusProjector.project(
              List.of(completed, newerOpen), ficaTemplates, List.of(), lookup, null, TODAY),
          "IN_PROGRESS",
          newerOpen.getId());

      // Reverse ages: the open one is older than the completed one — still IN_PROGRESS.
      var olderOpen = instance(client, "IN_PROGRESS", T0, null);
      var newerCompleted =
          instance(client, "COMPLETED", T0.plusSeconds(3600), T0.plusSeconds(7200));
      assertChecklistProjection(
          FicaStatusProjector.project(
              List.of(newerCompleted, olderOpen), ficaTemplates, List.of(), lookup, null, TODAY),
          "IN_PROGRESS",
          olderOpen.getId());
    }

    @Test
    void newestCompletedWins() {
      var older = instance(client, "COMPLETED", T0, T0.plusSeconds(10));
      var newer = instance(client, "COMPLETED", T0.plusSeconds(3600), T0.plusSeconds(3610));
      var p =
          FicaStatusProjector.project(
              List.of(older, newer), ficaTemplates, List.of(), lookup, null, TODAY);
      assertThat(p.status()).isEqualTo("DONE");
      assertThat(p.checklistInstanceId()).isEqualTo(newer.getId());
      assertThat(p.completedAt()).isEqualTo(T0.plusSeconds(3610));
      assertThat(p.requestId()).isNull();
    }

    @Test
    void cancelledInstancesAreIgnored() {
      var cancelled = instance(client, "CANCELLED", T0.plusSeconds(3600), null);
      Instant completedAt = T0.plusSeconds(5);
      var doneReq = request(client, ficaTemplate, RequestStatus.COMPLETED, T0, completedAt);
      item(doneReq, ItemStatus.ACCEPTED);
      // Falls back to the request rule: DONE via the request.
      var p =
          FicaStatusProjector.project(
              List.of(cancelled), ficaTemplates, List.of(doneReq), lookup, null, TODAY);
      assertThat(p.status()).isEqualTo("DONE");
      assertThat(p.requestId()).isEqualTo(doneReq.getId());
      assertThat(p.completedAt()).isEqualTo(completedAt);
      assertThat(p.checklistInstanceId()).isNull();

      // Only a CANCELLED instance and nothing else → NOT_STARTED.
      assertProjection(
          FicaStatusProjector.project(
              List.of(cancelled), ficaTemplates, List.of(), lookup, null, TODAY),
          "NOT_STARTED",
          null);
    }

    @Test
    void noInstancesFallsBackToTheRequestRule() {
      var sent = request(client, ficaTemplate, RequestStatus.SENT, T0, null);
      assertProjection(
          FicaStatusProjector.project(List.of(), ficaTemplates, List.of(sent), lookup, null, TODAY),
          "IN_PROGRESS",
          sent.getId());
    }
  }

  @Nested
  class BatchEqualsSingle {

    private final InformationRequestRepository requestRepository =
        mock(InformationRequestRepository.class);
    private final RequestItemRepository itemRepository = mock(RequestItemRepository.class);
    private final ChecklistInstanceRepository instanceRepository =
        mock(ChecklistInstanceRepository.class);
    private final ClientFicaAttestationService attestationService =
        mock(ClientFicaAttestationService.class);
    private final List<InformationRequest> requests = new ArrayList<>();
    private final List<ChecklistInstance> instances = new ArrayList<>();

    private final InformationRequestService service =
        new InformationRequestService(
            requestRepository,
            itemRepository,
            mock(RequestNumberService.class),
            mock(RequestTemplateRepository.class),
            mock(RequestTemplateItemRepository.class),
            mock(CustomerRepository.class),
            mock(ProjectRepository.class),
            mock(PortalContactRepository.class),
            mock(DocumentRepository.class),
            mock(ApplicationEventPublisher.class),
            mock(AuditService.class),
            mock(MemberRepository.class),
            instanceRepository,
            attestationService,
            Clock.fixed(T0, KaziTime.ZONE));

    /**
     * What the database returns: the clients' onboarding instances; the clients' requests on a
     * FICA-pack template ({@code templateIds}), minus CANCELLED; the items of the given requests.
     */
    private void stubRepositories(Set<UUID> templateIds) {
      when(instanceRepository.findOnboardingByCustomerIdIn(anyCollection()))
          .thenAnswer(
              inv -> {
                Collection<UUID> ids = inv.getArgument(0);
                return instances.stream().filter(i -> ids.contains(i.getCustomerId())).toList();
              });
      when(requestRepository.findFicaRequestsForClients(
              anyCollection(), eq(FICA_ONBOARDING_PACK_ID), eq(RequestStatus.CANCELLED)))
          .thenAnswer(
              inv -> {
                Collection<UUID> ids = inv.getArgument(0);
                return requests.stream()
                    .filter(r -> ids.contains(r.getCustomerId()))
                    .filter(r -> r.getRequestTemplateId() != null)
                    .filter(r -> templateIds.contains(r.getRequestTemplateId()))
                    .filter(r -> r.getStatus() != RequestStatus.CANCELLED)
                    .toList();
              });
      when(itemRepository.findByRequestIdIn(anyCollection()))
          .thenAnswer(
              inv -> {
                Collection<UUID> ids = inv.getArgument(0);
                return items.stream().filter(i -> ids.contains(i.getRequestId())).toList();
              });
    }

    private ChecklistInstance addInstance(ChecklistInstance i) {
      instances.add(i);
      return i;
    }

    private InformationRequest add(InformationRequest r) {
      requests.add(r);
      return r;
    }

    @Test
    void batchMatchesSingleClientForEveryFixture() {
      UUID notStarted = UUID.randomUUID();
      UUID nonFicaOnly = UUID.randomUUID();
      add(request(nonFicaOnly, otherTemplate, RequestStatus.SENT, T0, null));

      UUID cancelledOnly = UUID.randomUUID();
      add(request(cancelledOnly, ficaTemplate, RequestStatus.CANCELLED, T0, null));

      UUID inProgress = UUID.randomUUID();
      add(request(inProgress, ficaTemplate, RequestStatus.DRAFT, T0, null));
      add(request(inProgress, inactiveFicaTemplate, RequestStatus.SENT, T0.plusSeconds(60), null));

      UUID done = UUID.randomUUID();
      var doneReq =
          add(request(done, ficaTemplate, RequestStatus.COMPLETED, T0, T0.plusSeconds(9)));
      item(doneReq, ItemStatus.ACCEPTED);
      item(doneReq, ItemStatus.ACCEPTED);
      add(request(done, ficaTemplate, RequestStatus.CANCELLED, T0.plusSeconds(3600), null));

      UUID completedNotAccepted = UUID.randomUUID();
      var cna = add(request(completedNotAccepted, ficaTemplate, RequestStatus.COMPLETED, T0, T0));
      item(cna, ItemStatus.ACCEPTED);
      item(cna, ItemStatus.REJECTED);

      UUID mixed = UUID.randomUUID();
      var olderDone = add(request(mixed, ficaTemplate, RequestStatus.COMPLETED, T0, T0));
      item(olderDone, ItemStatus.ACCEPTED);
      var newerIncomplete =
          add(request(mixed, ficaTemplate, RequestStatus.COMPLETED, T0.plusSeconds(60), T0));
      item(newerIncomplete, ItemStatus.SUBMITTED);
      add(request(mixed, ficaTemplate, RequestStatus.SENT, T0.plusSeconds(120), null));

      // Checklist-first fixtures (ADR-348).
      UUID checklistDone = UUID.randomUUID();
      var checklistDoneInstance =
          addInstance(instance(checklistDone, "COMPLETED", T0, T0.plusSeconds(42)));

      UUID checklistOpenOverDoneRequest = UUID.randomUUID();
      var openInstance =
          addInstance(instance(checklistOpenOverDoneRequest, "IN_PROGRESS", T0, null));
      var acceptedReq =
          add(
              request(
                  checklistOpenOverDoneRequest,
                  ficaTemplate,
                  RequestStatus.COMPLETED,
                  T0,
                  T0.plusSeconds(9)));
      item(acceptedReq, ItemStatus.ACCEPTED);

      UUID cancelledChecklistDraftRequest = UUID.randomUUID();
      addInstance(instance(cancelledChecklistDraftRequest, "CANCELLED", T0, null));
      var draftReq =
          add(request(cancelledChecklistDraftRequest, ficaTemplate, RequestStatus.DRAFT, T0, null));

      stubRepositories(Set.of(ficaTemplate, inactiveFicaTemplate));
      var clients =
          List.of(
              notStarted,
              nonFicaOnly,
              cancelledOnly,
              inProgress,
              done,
              completedNotAccepted,
              mixed,
              checklistDone,
              checklistOpenOverDoneRequest,
              cancelledChecklistDraftRequest);
      var batch = service.getFicaStatuses(clients);

      assertThat(batch).containsOnlyKeys(clients);
      for (UUID c : clients) {
        assertThat(batch.get(c)).as("client %s", c).isEqualTo(service.getFicaStatus(c));
      }
      // Sanity: the fixtures cover every status.
      assertThat(batch.get(notStarted).status()).isEqualTo("NOT_STARTED");
      assertThat(batch.get(cancelledOnly).status()).isEqualTo("NOT_STARTED");
      assertThat(batch.get(inProgress).status()).isEqualTo("IN_PROGRESS");
      assertThat(batch.get(done).status()).isEqualTo("DONE");
      assertThat(batch.get(done).lastVerifiedAt()).isEqualTo(T0.plusSeconds(9));
      assertThat(batch.get(completedNotAccepted).status()).isEqualTo("IN_PROGRESS");
      assertThat(batch.get(mixed).requestId()).isEqualTo(newerIncomplete.getId());
      assertThat(batch.get(checklistDone).status()).isEqualTo("DONE");
      assertThat(batch.get(checklistDone).checklistInstanceId())
          .isEqualTo(checklistDoneInstance.getId());
      assertThat(batch.get(checklistDone).lastVerifiedAt()).isEqualTo(T0.plusSeconds(42));
      assertThat(batch.get(checklistOpenOverDoneRequest).status()).isEqualTo("IN_PROGRESS");
      assertThat(batch.get(checklistOpenOverDoneRequest).checklistInstanceId())
          .isEqualTo(openInstance.getId());
      assertThat(batch.get(checklistOpenOverDoneRequest).requestId()).isNull();
      assertThat(batch.get(cancelledChecklistDraftRequest).status()).isEqualTo("IN_PROGRESS");
      assertThat(batch.get(cancelledChecklistDraftRequest).requestId()).isEqualTo(draftReq.getId());
      assertThat(batch.get(cancelledChecklistDraftRequest).checklistInstanceId()).isNull();
    }

    @Test
    void batchMatchesSingleClientWhenNoFicaTemplateExists() {
      UUID c = UUID.randomUUID();
      add(request(c, ficaTemplate, RequestStatus.SENT, T0, null));
      stubRepositories(Set.of());

      var batch = service.getFicaStatuses(List.of(c));

      assertThat(batch.get(c)).isEqualTo(service.getFicaStatus(c));
      assertThat(batch.get(c).status()).isEqualTo("NOT_STARTED");
    }

    @Test
    void batchMatchesSingleClientWithAttestations() {
      UUID attested = UUID.randomUUID();
      UUID review = UUID.randomUUID();
      UUID checklistOverAttestation = UUID.randomUUID();
      var clean = cleanAttestation(attested);
      var unrated = attestation(review, FicaRiskRating.UNRATED, null);
      var checklistAttestation = cleanAttestation(checklistOverAttestation);
      var open = addInstance(instance(checklistOverAttestation, "IN_PROGRESS", T0, null));
      stubRepositories(Set.of(ficaTemplate));
      var active =
          Map.of(attested, clean, review, unrated, checklistOverAttestation, checklistAttestation);
      when(attestationService.activeFor(anyCollection()))
          .thenAnswer(
              inv -> {
                Collection<UUID> ids = inv.getArgument(0);
                var found = new HashMap<UUID, ClientFicaAttestation>(active);
                found.keySet().retainAll(ids);
                return found;
              });
      var clients = List.of(attested, review, checklistOverAttestation);

      var batch = service.getFicaStatuses(clients);

      for (UUID c : clients) {
        assertThat(batch.get(c)).as("client %s", c).isEqualTo(service.getFicaStatus(c));
      }
      assertThat(batch.get(attested).status()).isEqualTo("ATTESTED");
      assertThat(batch.get(attested).attestationId()).isEqualTo(clean.getId());
      assertThat(batch.get(attested).reviewReasons()).isEmpty();
      assertThat(batch.get(attested).lastVerifiedAt()).isNull();
      assertThat(batch.get(review).status()).isEqualTo("ATTESTED_REVIEW");
      assertThat(batch.get(review).reviewReasons()).containsExactly("UNRATED");
      assertThat(batch.get(checklistOverAttestation).status()).isEqualTo("IN_PROGRESS");
      assertThat(batch.get(checklistOverAttestation).checklistInstanceId()).isEqualTo(open.getId());
      assertThat(batch.get(checklistOverAttestation).attestationId()).isNull();
    }

    @Test
    void emptyInputIsEmptyMap() {
      assertThat(service.getFicaStatuses(List.of())).isEmpty();
    }
  }

  @Nested
  class Attestation {

    @Test
    void openChecklistBeatsAnAttestation() {
      var open = instance(client, "IN_PROGRESS", T0, null);
      var p =
          FicaStatusProjector.project(
              List.of(open), ficaTemplates, List.of(), lookup, cleanAttestation(client), TODAY);
      assertChecklistProjection(p, "IN_PROGRESS", open.getId());
    }

    @Test
    void completedChecklistBeatsAnAttestation() {
      // FD-29: Kazi evidence wins — a completed Kazi onboarding checklist supersedes it.
      var done = instance(client, "COMPLETED", T0, T0.plusSeconds(60));
      var p =
          FicaStatusProjector.project(
              List.of(done),
              ficaTemplates,
              List.of(),
              lookup,
              attestation(client, FicaRiskRating.UNRATED, null),
              TODAY);
      assertThat(p.status()).isEqualTo("DONE");
      assertThat(p.checklistInstanceId()).isEqualTo(done.getId());
      assertThat(p.attestationId()).isNull();
      assertThat(p.reviewReasons()).isEmpty();
    }

    @Test
    void liveFicaRequestBeatsAnAttestation() {
      var sent = request(client, ficaTemplate, RequestStatus.SENT, T0, null);
      assertProjection(
          FicaStatusProjector.project(
              List.of(), ficaTemplates, List.of(sent), lookup, cleanAttestation(client), TODAY),
          "IN_PROGRESS",
          sent.getId());
    }

    @Test
    void requestBranchNotStartedFallsThroughToTheAttestation() {
      // A cancelled FICA request leaves the request rule at NOT_STARTED — the attestation decides.
      var cancelled = request(client, ficaTemplate, RequestStatus.CANCELLED, T0, null);
      var a = cleanAttestation(client);
      var p =
          FicaStatusProjector.project(
              List.of(), ficaTemplates, List.of(cancelled), lookup, a, TODAY);
      assertThat(p.status()).isEqualTo("ATTESTED");
      assertThat(p.attestationId()).isEqualTo(a.getId());
    }

    @Test
    void cleanAttestationIsAttested() {
      var a = cleanAttestation(client);
      var p = FicaStatusProjector.project(List.of(), ficaTemplates, List.of(), lookup, a, TODAY);
      assertAttested(p, "ATTESTED", a);
      assertThat(p.reviewReasons()).isEmpty();
    }

    @Test
    void unratedIsReviewDue() {
      var a = attestation(client, FicaRiskRating.UNRATED, null);
      assertAttested(project(a), "ATTESTED_REVIEW", a);
      assertThat(project(a).reviewReasons()).containsExactly("UNRATED");
    }

    @Test
    void beneficialOwnershipOutstandingIsReviewDue() {
      var a =
          attestation(
              client,
              FicaRiskRating.LOW,
              TODAY.plusYears(1),
              BeneficialOwnershipStatus.OUTSTANDING,
              FicaScreeningStatus.SCREENED_CLEAR,
              SourceOfFundsStatus.NOT_REQUIRED);
      assertAttested(project(a), "ATTESTED_REVIEW", a);
      assertThat(project(a).reviewReasons()).containsExactly("BENEFICIAL_OWNERSHIP_OUTSTANDING");
    }

    @Test
    void sourceOfFundsOutstandingIsReviewDue() {
      var a =
          attestation(
              client,
              FicaRiskRating.MEDIUM,
              TODAY.plusYears(1),
              BeneficialOwnershipStatus.DONE,
              FicaScreeningStatus.SCREENED_CLEAR,
              SourceOfFundsStatus.OUTSTANDING);
      assertThat(project(a).status()).isEqualTo("ATTESTED_REVIEW");
      assertThat(project(a).reviewReasons()).containsExactly("SOURCE_OF_FUNDS_OUTSTANDING");
    }

    @Test
    void notScreenedIsReviewDue() {
      var a =
          attestation(
              client,
              FicaRiskRating.HIGH,
              TODAY.plusYears(1),
              BeneficialOwnershipStatus.NOT_APPLICABLE,
              FicaScreeningStatus.NOT_SCREENED,
              SourceOfFundsStatus.DONE);
      assertThat(project(a).status()).isEqualTo("ATTESTED_REVIEW");
      assertThat(project(a).reviewReasons()).containsExactly("NOT_SCREENED");
    }

    @Test
    void reviewDateOnOrBeforeTodayHasPassed() {
      var dueToday = attestation(client, FicaRiskRating.HIGH, TODAY);
      assertThat(project(dueToday).status()).isEqualTo("ATTESTED_REVIEW");
      assertThat(project(dueToday).reviewReasons()).containsExactly("REVIEW_DATE_PASSED");

      var overdue = attestation(client, FicaRiskRating.HIGH, TODAY.minusYears(2));
      assertThat(project(overdue).reviewReasons()).containsExactly("REVIEW_DATE_PASSED");

      var dueTomorrow = attestation(client, FicaRiskRating.HIGH, TODAY.plusDays(1));
      assertThat(project(dueTomorrow).status()).isEqualTo("ATTESTED");
      assertThat(project(dueTomorrow).reviewReasons()).isEmpty();
    }

    @Test
    void severalReasonsComeInDeclarationOrder() {
      var a =
          attestation(
              client,
              FicaRiskRating.UNRATED,
              null,
              BeneficialOwnershipStatus.OUTSTANDING,
              FicaScreeningStatus.NOT_SCREENED,
              SourceOfFundsStatus.OUTSTANDING);
      assertThat(project(a).reviewReasons())
          .containsExactly(
              "UNRATED",
              "BENEFICIAL_OWNERSHIP_OUTSTANDING",
              "SOURCE_OF_FUNDS_OUTSTANDING",
              "NOT_SCREENED");

      var allFive =
          attestation(
              client,
              FicaRiskRating.LOW,
              TODAY.minusDays(1),
              BeneficialOwnershipStatus.OUTSTANDING,
              FicaScreeningStatus.NOT_SCREENED,
              SourceOfFundsStatus.OUTSTANDING);
      assertThat(project(allFive).reviewReasons())
          .containsExactly(
              "BENEFICIAL_OWNERSHIP_OUTSTANDING",
              "SOURCE_OF_FUNDS_OUTSTANDING",
              "NOT_SCREENED",
              "REVIEW_DATE_PASSED");
      assertThat(FicaReviewReason.values())
          .extracting(Enum::name)
          .containsExactly(
              "UNRATED",
              "BENEFICIAL_OWNERSHIP_OUTSTANDING",
              "SOURCE_OF_FUNDS_OUTSTANDING",
              "NOT_SCREENED",
              "REVIEW_DATE_PASSED");
    }

    @Test
    void screeningMatchAloneIsNotAReviewReason() {
      // FD-30 names five reasons; a screening match is not one of them (F-655B-2, RG-3).
      var a =
          attestation(
              client,
              FicaRiskRating.LOW,
              TODAY.plusYears(1),
              BeneficialOwnershipStatus.DONE,
              FicaScreeningStatus.SCREENED_MATCH,
              SourceOfFundsStatus.NOT_REQUIRED);
      assertThat(project(a).status()).isEqualTo("ATTESTED");
    }

    @Test
    void supersededAttestationIsIgnored() {
      var a = cleanAttestation(client);
      a.supersede(T0);
      assertProjection(project(a), "NOT_STARTED", null);
    }

    @Test
    void todayIsRequired() {
      assertThatThrownBy(
              () ->
                  FicaStatusProjector.project(
                      List.of(), ficaTemplates, List.of(), lookup, null, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void outstandingAndClearPredicates() {
      assertThat(FicaStatusProjector.isOutstanding("NOT_STARTED")).isTrue();
      assertThat(FicaStatusProjector.isOutstanding("IN_PROGRESS")).isTrue();
      assertThat(FicaStatusProjector.isOutstanding("ATTESTED_REVIEW")).isTrue();
      assertThat(FicaStatusProjector.isOutstanding("DONE")).isFalse();
      assertThat(FicaStatusProjector.isOutstanding("ATTESTED")).isFalse();
      assertThat(FicaStatusProjector.isOutstanding(null)).isFalse();

      assertThat(FicaStatusProjector.isClear("DONE")).isTrue();
      assertThat(FicaStatusProjector.isClear("ATTESTED")).isTrue();
      assertThat(FicaStatusProjector.isClear("NOT_STARTED")).isFalse();
      assertThat(FicaStatusProjector.isClear("IN_PROGRESS")).isFalse();
      assertThat(FicaStatusProjector.isClear("ATTESTED_REVIEW")).isFalse();
      assertThat(FicaStatusProjector.isClear(null)).isFalse();
    }

    private FicaStatusProjector.Projection project(ClientFicaAttestation a) {
      return FicaStatusProjector.project(List.of(), ficaTemplates, List.of(), lookup, a, TODAY);
    }
  }

  // --- fixtures ---

  private InformationRequest request(
      UUID clientId,
      UUID templateId,
      RequestStatus status,
      Instant createdAt,
      Instant completedAt) {
    var r =
        new InformationRequest(
            "REQ-" + UUID.randomUUID(), clientId, UUID.randomUUID(), UUID.randomUUID());
    TestIds.withId(r, UUID.randomUUID());
    r.setRequestTemplateId(templateId);
    TestIds.withField(r, "status", status);
    TestIds.withField(r, "createdAt", createdAt);
    TestIds.withField(r, "completedAt", completedAt);
    return r;
  }

  private RequestItem item(InformationRequest request, ItemStatus status) {
    var item =
        new RequestItem(request.getId(), "ID copy", null, ResponseType.FILE_UPLOAD, true, null, 0);
    TestIds.withId(item, UUID.randomUUID());
    TestIds.withField(item, "status", status);
    items.add(item);
    return item;
  }

  private ChecklistInstance instance(
      UUID clientId, String status, Instant createdAt, Instant completedAt) {
    var i = new ChecklistInstance(UUID.randomUUID(), clientId, createdAt);
    TestIds.withId(i, UUID.randomUUID());
    TestIds.withField(i, "status", status);
    TestIds.withField(i, "createdAt", createdAt);
    TestIds.withField(i, "completedAt", completedAt);
    return i;
  }

  /** A clean, LOW-rated attestation due for review a year after {@link #TODAY}. */
  private static ClientFicaAttestation cleanAttestation(UUID clientId) {
    return attestation(clientId, FicaRiskRating.LOW, TODAY.plusYears(1));
  }

  private static ClientFicaAttestation attestation(
      UUID clientId, FicaRiskRating rating, LocalDate nextReview) {
    return attestation(
        clientId,
        rating,
        nextReview,
        BeneficialOwnershipStatus.DONE,
        FicaScreeningStatus.SCREENED_CLEAR,
        SourceOfFundsStatus.NOT_REQUIRED);
  }

  private static ClientFicaAttestation attestation(
      UUID clientId,
      FicaRiskRating rating,
      LocalDate nextReview,
      BeneficialOwnershipStatus bo,
      FicaScreeningStatus screening,
      SourceOfFundsStatus sof) {
    var a =
        new ClientFicaAttestation(
            clientId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            LocalDate.of(2024, 2, 1),
            null,
            rating,
            FicaClientType.NATURAL_PERSON,
            bo,
            screening,
            screening == FicaScreeningStatus.NOT_SCREENED ? null : LocalDate.of(2024, 2, 1),
            sof,
            "Archive box 12",
            "PREV-1",
            nextReview,
            UUID.randomUUID(),
            T0,
            T0);
    return TestIds.withId(a, UUID.randomUUID());
  }

  /** An attestation-branch outcome: the attestation id, no other id, no completedAt. */
  private static void assertAttested(
      FicaStatusProjector.Projection p, String status, ClientFicaAttestation a) {
    assertThat(p.status()).isEqualTo(status);
    assertThat(p.attestationId()).isEqualTo(a.getId());
    assertThat(p.requestId()).isNull();
    assertThat(p.checklistInstanceId()).isNull();
    assertThat(p.completedAt()).isNull();
  }

  /** A request-branch (or NOT_STARTED) outcome: no checklist instance id, no completedAt. */
  private static void assertProjection(
      FicaStatusProjector.Projection p, String status, UUID requestId) {
    assertThat(p.status()).isEqualTo(status);
    assertThat(p.requestId()).isEqualTo(requestId);
    assertThat(p.completedAt()).isNull();
    assertThat(p.checklistInstanceId()).isNull();
    assertThat(p.attestationId()).isNull();
    assertThat(p.reviewReasons()).isEmpty();
  }

  /** A checklist-branch non-DONE outcome: the instance id, no request id, no completedAt. */
  private static void assertChecklistProjection(
      FicaStatusProjector.Projection p, String status, UUID instanceId) {
    assertThat(p.status()).isEqualTo(status);
    assertThat(p.checklistInstanceId()).isEqualTo(instanceId);
    assertThat(p.requestId()).isNull();
    assertThat(p.completedAt()).isNull();
    assertThat(p.attestationId()).isNull();
    assertThat(p.reviewReasons()).isEmpty();
  }
}
