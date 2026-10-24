package sa.lendo.credit.analyst.multi_agent.mock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicantProfileRepositoryTest {

    private final ApplicantProfileRepository repository = new ApplicantProfileRepository();

    @Test
    void providesFiveDistinctDemoScenarios() {
        assertThat(repository.findAll()).hasSize(5);
        assertThat(repository.findAll().stream().map(ApplicantProfile::id).distinct()).hasSize(5);
        assertThat(repository.findAll()).allSatisfy(p -> {
            assertThat(p.name()).isNotBlank();
            assertThat(p.description()).isNotBlank();
        });
    }

    @Test
    void findsKnownApplicantById() {
        assertThat(repository.findById("APP-002")).isPresent();
        assertThat(repository.findById("APP-002").get().creditScore()).isEqualTo(690);
    }

    @Test
    void returnsEmptyForUnknownApplicant() {
        assertThat(repository.findById("APP-999")).isEmpty();
    }

    @Test
    void keepsTheOriginalJohnDoeLtvFailureScenario() {
        ApplicantProfile johnDoe = repository.findById("APP-002").orElseThrow();
        // 27k / 29k = 93.1% LTV > the 90% auto-loan cap; score 690 < the 700 personal-loan floor
        assertThat(johnDoe.requestedLoanAmount()).isEqualTo(27000);
        assertThat(johnDoe.vehicleAssessedValue()).isEqualTo(29000);
        assertThat(johnDoe.creditScore()).isEqualTo(690);
    }
}
