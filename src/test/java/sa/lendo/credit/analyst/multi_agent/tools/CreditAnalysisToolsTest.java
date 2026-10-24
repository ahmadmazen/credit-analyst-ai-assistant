package sa.lendo.credit.analyst.multi_agent.tools;

import org.junit.jupiter.api.Test;
import sa.lendo.credit.analyst.multi_agent.mock.ApplicantProfileRepository;

import static org.assertj.core.api.Assertions.assertThat;

class CreditAnalysisToolsTest {

    private final CreditAnalysisTools tools = new CreditAnalysisTools(new ApplicantProfileRepository());

    @Test
    void returnsApplicantSpecificCustomerData() {
        assertThat(tools.getCustomerData("APP-001")).contains("Sara");
        assertThat(tools.getCustomerData("APP-002")).contains("John Doe");
    }

    @Test
    void returnsApplicantSpecificCreditScore() {
        assertThat(tools.getCreditScore("APP-002")).contains("690");
        assertThat(tools.getCreditScore("APP-001")).doesNotContain("690");
    }

    @Test
    void returnsFinancialsAndApplicationDetails() {
        assertThat(tools.getFinancials("APP-002"))
                .contains("annualIncome").contains("totalExistingDebt");
        assertThat(tools.getApplicationDetails("APP-002"))
                .contains("27000").contains("29000");
    }

    @Test
    void unknownApplicantYieldsErrorJson() {
        assertThat(tools.getCustomerData("APP-999")).contains("error");
        assertThat(tools.getCreditScore("APP-999")).contains("error");
    }

    @Test
    void calculatesDtiRatio() {
        assertThat(tools.calculateDtiRatio(60000, 30000)).isEqualTo(50.0);
        assertThat(tools.calculateDtiRatio(0, 30000)).isEqualTo(Double.MAX_VALUE);
    }
}
