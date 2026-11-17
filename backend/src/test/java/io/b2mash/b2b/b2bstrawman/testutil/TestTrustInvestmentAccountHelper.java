package io.b2mash.b2b.b2bstrawman.testutil;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Phase 92 648A (ADR-379): an investment is placed from a trust banking account into an {@code
 * INVESTMENT} trust account whose basis is the investment's. Trust tests that place investments
 * open the investment account here, through the API ({@code POST /api/trust-accounts}).
 */
public final class TestTrustInvestmentAccountHelper {

  private TestTrustInvestmentAccountHelper() {}

  /** Opens a non-primary INVESTMENT account of {@code basis} as {@code jwt}; returns its id. */
  public static UUID openInvestmentAccount(
      MockMvc mockMvc,
      RequestPostProcessor jwt,
      String accountName,
      String accountNumber,
      InvestmentBasis basis)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "%s",
                          "bankName": "Investec",
                          "branchCode": "580105",
                          "accountNumber": "%s",
                          "accountType": "INVESTMENT",
                          "investmentBasis": "%s",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2025-01-01"
                        }
                        """
                            .formatted(accountName, accountNumber, basis.name())))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(TestEntityHelper.extractId(result));
  }
}
