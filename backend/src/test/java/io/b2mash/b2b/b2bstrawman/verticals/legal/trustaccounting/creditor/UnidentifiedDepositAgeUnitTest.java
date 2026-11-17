package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustClientMatterRule;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * {@code ageDays} is never negative on the wire: a future-dated statement line can be recorded
 * today (647A), so an item received after today lists with age 0 and no one-year flag. A fixed
 * clock pins "today"; the integration test ({@link UnidentifiedDepositIntegrationTest}) covers the
 * past-dated ages through the API.
 */
class UnidentifiedDepositAgeUnitTest {

  private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

  @Test
  void anItemReceivedAfterToday_listsWithAgeZero_andNoOneYearFlag() {
    var account = UUID.randomUUID();
    var future = item(account, TODAY.plusDays(5));
    var sameDay = item(account, TODAY);
    var past = item(account, TODAY.minusDays(366));

    var deposits = mock(UnidentifiedDepositRepository.class);
    when(deposits.findByTrustAccountIdOrderByReceivedDateAscCreatedAtAscIdAsc(
            eq(account), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(past, sameDay, future)));
    var accounts = mock(TrustAccountRepository.class);
    when(accounts.existsById(account)).thenReturn(true);
    var service =
        new UnidentifiedDepositService(
            deposits,
            accounts,
            mock(TrustPostingService.class),
            mock(TrustTransactionRepository.class),
            mock(CustomerRepository.class),
            mock(ProjectRepository.class),
            mock(TrustClientMatterRule.class),
            mock(VerticalModuleGuard.class),
            mock(AuditService.class),
            mock(ApplicationEventPublisher.class),
            Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE));

    var content = service.list(account, null, PageRequest.of(0, 25)).getContent();

    assertThat(content)
        .extracting(
            UnidentifiedDepositService.UnidentifiedDepositResponse::receivedDate,
            UnidentifiedDepositService.UnidentifiedDepositResponse::ageDays,
            UnidentifiedDepositService.UnidentifiedDepositResponse::olderThanOneYear)
        .containsExactly(
            tuple(TODAY.minusDays(366), 366, true),
            tuple(TODAY, 0, false),
            tuple(TODAY.plusDays(5), 0, false));
  }

  private static UnidentifiedDeposit item(UUID account, LocalDate receivedDate) {
    return new UnidentifiedDeposit(
        account,
        UUID.randomUUID(),
        UUID.randomUUID(),
        receivedDate,
        "Statement credit",
        "REF",
        new BigDecimal("100.00"),
        null);
  }
}
