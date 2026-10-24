package sa.lendo.credit.analyst.multi_agent.service;

import org.junit.jupiter.api.Test;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisEvent;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisRun;
import sa.lendo.credit.analyst.multi_agent.events.AnalysisRunRegistry;
import sa.lendo.credit.analyst.multi_agent.mock.ApplicantProfileRepository;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisServiceTest {

    private final ApplicantProfileRepository repository = new ApplicantProfileRepository();
    private final AnalysisRunRegistry registry = new AnalysisRunRegistry();
    private static final Executor DIRECT = Runnable::run;
    private static final Executor NOOP = command -> { };

    private AnalysisService service(AgentFactory factory, Executor executor, int max) {
        return new AnalysisService(factory, repository, registry, executor, max);
    }

    @Test
    void rejectsUnknownApplicant() {
        AnalysisService service = service(p -> (a, b, c) -> "unused", DIRECT, 2);

        assertThatThrownBy(() -> service.startAnalysis("APP-999", "Car Loan"))
                .isInstanceOf(UnknownApplicantException.class);
    }

    @Test
    void rejectsUnsupportedProduct() {
        AnalysisService service = service(p -> (a, b, c) -> "unused", DIRECT, 2);

        assertThatThrownBy(() -> service.startAnalysis("APP-001", "Yacht Loan"))
                .isInstanceOf(InvalidProductException.class);
    }

    @Test
    void completedRunStoresDecisionAndPublishesLifecycleEvents() {
        AgentFactory factory = publisher -> (applicantId, productType, rules) -> {
            assertThat(applicantId).isEqualTo("APP-002");
            assertThat(productType).isEqualTo("Car Loan");
            assertThat(rules).contains("credit score"); // policy_rules.txt was loaded
            return "{\"decision\":\"REJECT\",\"summary\":\"LTV too high\",\"reasoningSteps\":[]}";
        };
        AnalysisService service = service(factory, DIRECT, 2);

        String runId = service.startAnalysis("APP-002", "Car Loan");

        AnalysisRun run = registry.find(runId).orElseThrow();
        assertThat(run.status()).isEqualTo(AnalysisRun.Status.COMPLETED);
        assertThat(run.decision().path("decision").asText()).isEqualTo("REJECT");
        List<String> types = run.publisher().events().stream().map(AnalysisEvent::type).toList();
        assertThat(types).containsExactly("run_started", "decision", "run_completed");
        assertThat(run.publisher().events().get(2).payload().path("status").asText())
                .isEqualTo("COMPLETED");
    }

    @Test
    void failedRunPublishesErrorAndMarksRunFailed() {
        AgentFactory factory = publisher -> (a, b, c) -> {
            throw new RuntimeException("claude CLI exited with code 1: not authenticated");
        };
        AnalysisService service = service(factory, DIRECT, 2);

        String runId = service.startAnalysis("APP-001", "Personal Loan");

        AnalysisRun run = registry.find(runId).orElseThrow();
        assertThat(run.status()).isEqualTo(AnalysisRun.Status.FAILED);
        List<String> types = run.publisher().events().stream().map(AnalysisEvent::type).toList();
        assertThat(types).containsExactly("run_started", "error", "run_completed");
        assertThat(run.publisher().events().get(1).payload().path("message").asText())
                .contains("not authenticated");
    }

    @Test
    void enforcesConcurrentRunLimit() {
        AnalysisService service = service(p -> (a, b, c) -> "x", NOOP, 1);

        service.startAnalysis("APP-001", "Car Loan"); // stays RUNNING (noop executor)

        assertThatThrownBy(() -> service.startAnalysis("APP-002", "Car Loan"))
                .isInstanceOf(TooManyRunsException.class);
    }

    @Test
    void slotIsFreedWhenRunFinishes() {
        AnalysisService service = service(p -> (a, b, c) -> "done", DIRECT, 1);

        service.startAnalysis("APP-001", "Car Loan");
        // completed synchronously, so a second run must be accepted
        String second = service.startAnalysis("APP-002", "Car Loan");

        assertThat(registry.find(second)).isPresent();
    }
}
